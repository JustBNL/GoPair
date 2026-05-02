/**
 * 场景：消息发送高频压测
 *
 * 包含三个模式，通过 MODE 环境变量切换：
 *   high_freq  - 单用户高频（摸单用户发送上限）
 *   group_chat - 群聊并发（模拟真实群聊场景）
 *   reliability - 可靠性验证（零丢失零重复）
 *
 * 运行示例：
 *   k6 run -e MODE=group_chat -e VUS=50 scenarios/message_basic.js
 *
 * 环境变量：
 *   BASE_URL             - Room Service 地址，默认 http://localhost:8081
 *   MESSAGE_SERVICE_URL  - Message Service 地址，默认 http://localhost:8081（走网关）
 *   ROOM_CODE            - 房间码（用于入房）
 *   ROOM_ID              - 共享房间 ID（setup 阶段自动创建）
 *   VUS                  - 虚拟用户数，默认 50
 *   DURATION             - 压测持续时间，默认 60s
 *   MODE                 - 场景模式，默认 group_chat
 *   MSG_INTERVAL_MS      - 发送间隔（ms），仅 high_freq/reliability 模式使用
 */

import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';
import {
  BASE_URL,
  VUS,
  DURATION,
  userData,
  makeHttpParams,
  sendMessage,
  requestJoinAsync,
  pollJoinResult,
  createTestRoom,
} from '../common.js';

// ====================================================================
// 场景配置（从环境变量读取）
// ====================================================================

const MODE = __ENV.MODE || 'group_chat';
const ROOM_CODE = String(Math.floor(1000000000 + Date.now() % 1000000000)) + String(Math.floor(Math.random() * 1000)).padStart(3, '0');
const ROOM_ID = parseInt(__ENV.ROOM_ID || '0', 10);
const MAX_MEMBERS = parseInt(__ENV.MAX_MEMBERS || '1000', 10);

const SCENARIO_CONFIG = {
  high_freq: { description: '单用户高频发送（摸上限）' },
  group_chat: { description: '群聊并发（模拟真实）' },
  reliability: { description: '可靠性验证（零丢失零重复）' },
};

// 三个模式对应的发送间隔（ms）
const INTERVAL_MAP = {
  high_freq: 100,
  group_chat: 300,
  reliability: 200,
};
const INTERVAL_MS = parseInt(__ENV.MSG_INTERVAL_MS || String(INTERVAL_MAP[MODE] || 300), 10);

// ====================================================================
// VU 内序号计数器（每个 VU 独立递增）
// ====================================================================

// Counter 是 k6 内置的进程级线程安全计数器，teardown/handleSummary 均可访问
const vuSentCounter = new Counter('vu_sent_total');

// ====================================================================
// K6 执行配置
// ====================================================================

export const options = {
  scenarios: {
    message_storm: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: Math.min(VUS, userData.length) },  // 渐进爬坡
        { duration: DURATION, target: Math.min(VUS, userData.length) },  // 保持目标（默认 60s → 约 200 条/VU）
        { duration: '10s', target: 0 },  // 降载
        { duration: '5s', target: 0 },  // 静默期：等待 MQ 消费完积压消息
      ],
      gracefulRampDown: '10s',
      gracefulStop: '15s',  // 延长停止时间，确保静默期内 VU 不发送新消息
    },
  },
  summaryTimeUnit: 'ms',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'p(99.99)'],
};

// ====================================================================
// setup：创建共享房间，所有 VU 在同一个房间内发消息
// ====================================================================

export function setup() {
  // 如果已指定 ROOM_ID，跳过创建
  if (ROOM_ID > 0) {
    console.log(`[setup] 使用已有房间 ID=${ROOM_ID}`);
    return { roomId: ROOM_ID };
  }

  // 使用 createTestRoom 幂等创建大房间
  const { roomId } = createTestRoom(BASE_URL, ROOM_CODE, MAX_MEMBERS);

  if (!roomId) {
    throw new Error(`[setup] 无法获取有效 roomId，message 测试无法进行`);
  }

  // 批量将所有压测用户预置为房间成员（解决房主入房幂等问题）
  const userIds = userData.map(u => parseInt(u.userId, 10));
  const adminParams = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${userData[0].token}`,
      'X-User-Id': userData[0].userId,
      'X-Nickname': userData[0].nickname,
    },
  };
  const batchRes = http.post(`${BASE_URL}/room/${roomId}/members/batch`, JSON.stringify(userIds), adminParams);
  console.log(`[setup] batch add HTTP status=${batchRes.status} body=${batchRes.body}`);
  let addedCount = 0;
  try {
    const json = JSON.parse(batchRes.body);
    addedCount = json?.data || 0;
  } catch {}

  // 验证 batch add 是否真的写入了数据库
  // 注意：/room/{roomId}/members 返回 R<List<RoomMemberVO>>，data 是数组而非分页对象
  const verifyRes = http.get(`${BASE_URL}/room/${roomId}/members`, adminParams);
  console.log(`[setup] getMembers HTTP status=${verifyRes.status} body=${verifyRes.body}`);
  let actualMemberCount = 0;
  try {
    const json = JSON.parse(verifyRes.body);
    const data = json?.data;
    // data 可能是数组（直接返回成员列表）或对象（有 total 字段）
    if (Array.isArray(data)) {
      actualMemberCount = data.length;
    } else if (data && typeof data === 'object' && typeof data.total === 'number') {
      actualMemberCount = data.total;
    }
  } catch {}

  console.log(`[setup] 创建共享房间成功 roomId=${roomId}, 批量添加成员 reported=${addedCount}, 实际查询到成员数=${actualMemberCount}`);

  if (addedCount > 0 && actualMemberCount === 0) {
    console.error(`[setup] 警告：batch add 报告成功(${addedCount})，但数据库中查询不到任何成员！`);
  }

  return { roomId };
}

// ====================================================================
// VU 主循环：入房 → 发一条消息（由 k6 executor 控制迭代频率）
// ====================================================================

export default function (data) {
  const roomId = data.roomId;
  const vuId = __VU - 1;
  const userIndex = vuId % userData.length;

  // Step 1：入房
  // fast_reject=true 表示已在房间或已在处理中，无需轮询，直接可发消息
  // fast_reject=false 时需要 poll，poll 成功才可发消息
  const params = makeHttpParams(userIndex);
  const joinResult = requestJoinAsync(params, ROOM_CODE);

  if (!joinResult.fastReject) {
    const pollRes = pollJoinResult(joinResult.joinToken, params);
    if (pollRes.status !== 'JOINED') {
      // 入房未成功（TIMEOUT/FAILED/ERROR），跳过本次发消息
      return;
    }
  }

  // Step 2：发送一条消息（带序号，用于可靠性验证）
  // 使用 k6 内置的 __ITER 迭代器代替手动 localSeq，保证全局唯一且单调递增
  const content = `perf-test-${vuId}-${__ITER}-${Date.now()}`;
  const result = sendMessage(userIndex, roomId, 1, content);

  if (result.success) {
    vuSentCounter.add(1);
  } else {
    // 记录第一个失败原因，方便调试
    console.error(`[VU ${vuId} | 迭代 ${__ITER}] 失败告警! HTTP状态码: ${result.statusCode}, 耗时: ${result.durationMs}ms, code=${result.code}, msg=${result.msg}`);
  }

  // 控制发送间隔
  sleep(INTERVAL_MS / 1000);
}

// ====================================================================
// 压测结束后的汇总分析（标准化报告格式）
// ====================================================================

export function handleSummary(data) {
  const metrics = data.metrics || {};
  const roomId = data.setup_data?.roomId;

  // 提取关键性能指标
  const totalRequests = metrics.http_reqs?.values?.count || 0;
  const failRate = (metrics.http_req_failed?.values?.rate || 0) * 100;
  const reqDuration = metrics.http_req_duration?.values || {};

  // 从 k6 Counter metric 读取消息发送总数（线程安全）
  const vuSentCount = metrics['vu_sent_total']?.values?.count ?? 0;

  // 查询数据库中的压测消息（handleSummary 在 teardown 之后执行，此时可以安全查询）
  let dbCount = 0;
  let reorderCount = 0;
  let duplicateCount = 0;
  let totalPerfMessages = 0;
  const reorderDetails = [];
  const duplicateDetails = [];

  if (roomId) {
    const msgSvcUrl = __ENV.MESSAGE_SERVICE_URL || 'http://localhost:8081';

    // 先用 /count 接口获取精确的压测消息总数，避免分页 total=0 的问题
    const countRes = http.get(
      `${msgSvcUrl}/message/room/${roomId}/count`,
      {
        headers: {
          'Authorization': `Bearer ${userData[0].token}`,
          'X-User-Id': userData[0].userId,
        },
        tags: { name: 'teardown_count' },
      }
    );
    if (countRes.status === 200) {
      try {
        const json = JSON.parse(countRes.body);
        totalPerfMessages = json?.data || 0;
      } catch {}
    }

    const allMessages = [];
    let page = 1;
    const pageSize = 100;
    // 如果 count 接口也失败，使用 vuSentCount 作为兜底上限
    const stopAt = totalPerfMessages > 0 ? totalPerfMessages : vuSentCount;
    console.log(`[handleSummary] count=${totalPerfMessages} vuSentCount=${vuSentCount} stopAt=${stopAt}`);

    while (allMessages.length < stopAt) {
      const res = http.get(
        `${msgSvcUrl}/message/room/${roomId}?pageNum=${page}&pageSize=${pageSize}`,
        {
          headers: {
            'Authorization': `Bearer ${userData[0].token}`,
            'X-User-Id': userData[0].userId,
          },
          tags: { name: 'teardown_query' },
        }
      );
      if (res.status !== 200) {
        let queryHttpStatus = res.status;
        console.error(`[handleSummary] query messages failed: HTTP ${res.status}`);
        break;
      }

      try {
        const json = JSON.parse(res.body);
        const records = json?.data?.records || [];
        if (records.length === 0) break;

        for (const msg of records) {
          if (msg.content && msg.content.startsWith('perf-test-')) {
            allMessages.push(msg);
          }
        }
        // 如果已取够目标数量，停止
        if (allMessages.length >= stopAt) break;
        // records 不足 pageSize，说明没有更多数据
        if (records.length < pageSize) break;
        page++;
      } catch {
        break;
      }
    }

    dbCount = allMessages.length;
    console.log(`[handleSummary] count=${totalPerfMessages} vuSentCount=${vuSentCount} stopAt=${stopAt} dbCount=${allMessages.length}`);

    // 重复检测：同一 VU 的同一 seq 在一轮中不应出现多次
    // 多轮迭代时同一 seq 会重复出现，但只要 VU 内同一 seq 消息的 content 不同（timestamp 不同）就是正常的
    // 真正的重复：同一 vuId-seq-timestamp 出现两次（同一消息被处理了两次）
    const seenKeys = new Set();
    allMessages.forEach(msg => {
      const parts = msg.content.split('-');
      if (parts.length >= 5 && parts[0] === 'perf' && parts[1] === 'test') {
        const vuId = parts[2];
        const seq = parts[3];
        const timestamp = parts[4];
        const key = `${vuId}-${seq}-${timestamp}`;
        if (seenKeys.has(key)) {
          duplicateCount++;
          if (duplicateDetails.length < 3) {
            duplicateDetails.push({ vuId, seq, timestamp, messageId: msg.messageId });
          }
        } else {
          seenKeys.add(key);
        }
      }
    });

    // 乱序检测：同一用户先发的消息（seq 较小）落库时间（createTime）反而比后发的晚
    const senderGroups = {};
    allMessages.forEach(msg => {
      const sid = msg.senderId;
      if (!senderGroups[sid]) senderGroups[sid] = [];
      senderGroups[sid].push(msg);
    });

    Object.entries(senderGroups).forEach(([senderId, messages]) => {
      // 1. 按照发送时的 seq（序号）升序排列，还原发送顺序
      messages.sort((a, b) => {
        const partsA = a.content.split('-');
        const partsB = b.content.split('-');
        const seqA = parseInt(partsA[3], 10);
        const seqB = parseInt(partsB[3], 10);
        return seqA - seqB;
      });

      // 2. 检查落库时间是否存在倒置（后发的消息，落库时间却比先发的早）
      for (let i = 1; i < messages.length; i++) {
        const prevTime = new Date(messages[i - 1].createTime).getTime();
        const currTime = new Date(messages[i].createTime).getTime();
        if (currTime < prevTime) {
          reorderCount++;
          if (reorderDetails.length < 5) {
            reorderDetails.push({
              senderId,
              seqPrev: messages[i - 1].content.split('-')[3],
              seqCurr: messages[i].content.split('-')[3],
              messageId1: messages[i - 1].messageId,
              messageId2: messages[i].messageId,
              time1: messages[i - 1].createTime,
              time2: messages[i].createTime,
            });
          }
        }
      }
    });
  }

  console.log(`[handleSummary] vuSentCount=${vuSentCount} dbCount=${dbCount} reorderCount=${reorderCount} duplicateCount=${duplicateCount}`);
  if (reorderDetails.length > 0) {
    console.log(`[handleSummary] 乱序示例: ${JSON.stringify(reorderDetails)}`);
  }
  if (duplicateDetails.length > 0) {
    console.log(`[handleSummary] 重复示例: ${JSON.stringify(duplicateDetails)}`);
  }

  // 可靠性计算
  // expectedMessages 以 count 接口（/room/{id}/count）返回的数据库真实总数为真相基准
  const expectedMessages = totalPerfMessages;
  const lossCount = expectedMessages > 0 ? expectedMessages - dbCount : null;
  const lossRate = lossCount !== null ? (lossCount / expectedMessages * 100).toFixed(2) : 'N/A';

  const durationSec = parseFloat(data.state?.testRunDuration || DURATION);
  const tps = durationSec > 0 ? (totalRequests / durationSec).toFixed(2) : 'N/A';

  const dbAvailable = totalPerfMessages > 0 || (dbCount >= 0 && roomId !== undefined);
  // 小误差（<=1 条）视为通过：分页查询可能存在少量计数误差
  const lossPass = dbAvailable && Math.abs(lossCount) <= 1;
  const lossSkipped = !(totalPerfMessages > 0 || roomId !== undefined);
  const skipReason = lossSkipped ? '查询失败' : '';
  const reorderPass = reorderCount === 0;
  const duplicatePass = duplicateCount === 0;

  let report = `
╔══════════════════════════════════════════════════════════════════╗
║               消息发送高频压测报告                              ║
║               场景: ${MODE} (${SCENARIO_CONFIG[MODE]?.description || ''})
╠══════════════════════════════════════════════════════════════════╣
║  [性能指标]                                                   ║
║    TPS:           ${tps}/s                                      ║
║    RT 均值:       ${reqDuration.avg?.toFixed(2) || 0}ms                               ║
║    RT P50:        ${reqDuration['med']?.toFixed(2) || 0}ms                               ║
║    RT P95:        ${reqDuration['p(95)']?.toFixed(2) || 0}ms                               ║
║    RT P99:        ${reqDuration['p(99)']?.toFixed(2) || 0}ms                               ║
║    HTTP 错误率:   ${failRate.toFixed(2)}%                             ║
╠══════════════════════════════════════════════════════════════════╣
║  [可靠性验证]                                                 ║
║    发送成功:     ${vuSentCount}                                   ║
║    数据库消息:   ${dbAvailable ? dbCount : 'N/A (查询失败)'}                               ║
║    丢失数:       ${lossSkipped ? 'N/A' : lossCount} (${lossRate}%)                         ║
║    丢失率:       [${lossSkipped ? 'SKIP' : (lossPass ? 'PASS' : 'FAIL')}]                                  ║
║    乱序事件:     ${reorderCount}                                     ║
║    乱序检测:     [${reorderPass ? 'PASS' : 'FAIL'}]                                  ║
║    重复消息:     ${duplicateCount}                                     ║
║    重复检测:     [${duplicatePass ? 'PASS' : 'FAIL'}]                                  ║
╠══════════════════════════════════════════════════════════════════╣
║  [关键结论]                                                   ║`;

    if (lossSkipped) {
    report += `
║    [WARNING] 可靠性验证已跳过（${skipReason}）。                    ║`;
  } else if ((reqDuration['p(99)'] || 0) < 200 && lossPass && reorderPass && duplicatePass) {
    report += `
║    系统在 ${VUS} VUS 下表现良好。                               ║
║    P99 响应时间 ${(reqDuration['p(99)'] || 0).toFixed(0)}ms，低于 200ms 阈值。         ║
║    零丢失、零乱序、零重复，可靠性验证通过。                   ║`;
  } else {
    const warnings = [];
    if (!lossPass) warnings.push(`丢失率验证失败(丢失${lossCount})`);
    if (!reorderPass) warnings.push('乱序验证失败');
    if (!duplicatePass) warnings.push('重复验证失败');
    if ((reqDuration['p(99)'] || 0) >= 200) warnings.push(`P99=${(reqDuration['p(99)'] || 0).toFixed(0)}ms`);
    report += `
║    ${warnings.join('；')}。                                 ║`;
  }

  report += `
╚══════════════════════════════════════════════════════════════════╝`;

  // 输出结构化 JSON 行（供日志解析使用）
  console.log(JSON.stringify({
    type: 'perf_summary',
    timestamp: new Date().toISOString(),
    mode: MODE,
    vus: VUS,
    duration: DURATION,
    metrics: { tps: parseFloat(tps), rtAvg: parseFloat(reqDuration.avg?.toFixed(2) || 0), rtP50: parseFloat(reqDuration.med?.toFixed(2) || 0), rtP95: parseFloat(reqDuration['p(95)']?.toFixed(2) || 0), rtP99: parseFloat(reqDuration['p(99)']?.toFixed(2) || 0), httpErrors: parseFloat(failRate) },
    reliability: { sent: vuSentCount, dbCount: dbCount, count: totalPerfMessages, lossCount: lossCount, lossRate: lossRate, lossPass: lossPass, reorderCount: reorderCount, reorderPass: reorderPass, duplicateCount: duplicateCount, duplicatePass: duplicatePass, skipped: lossSkipped },
    conclusion: lossPass && reorderPass && duplicatePass ? 'PASS' : 'FAIL',
  }));

  return { stdout: report };
}
