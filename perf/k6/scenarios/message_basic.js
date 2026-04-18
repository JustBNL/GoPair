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
 *   MESSAGE_SERVICE_URL  - Message Service 地址，默认 http://localhost:8082
 *   ROOM_CODE            - 房间码（用于入房）
 *   ROOM_ID              - 共享房间 ID（setup 阶段自动创建）
 *   VUS                  - 虚拟用户数，默认 50
 *   DURATION             - 压测持续时间，默认 60s
 *   MODE                 - 场景模式，默认 group_chat
 *   MSG_INTERVAL_MS      - 发送间隔（ms），仅 high_freq/reliability 模式使用
 */

import http from 'k6/http';
import { sleep } from 'k6';
import {
  BASE_URL,
  VUS,
  DURATION,
  userData,
  makeHttpParams,
  sendMessage,
  countRoomMessages,
  requestJoinAsync,
  pollJoinResult,
} from '../common.js';

// ====================================================================
// 场景配置（从环境变量读取）
// ====================================================================

const MODE = __ENV.MODE || 'group_chat';
const ROOM_CODE = __ENV.ROOM_CODE || '54273898';
const ROOM_ID = parseInt(__ENV.ROOM_ID || '0', 10);

const SCENARIO_CONFIG = {
  high_freq: { description: '单用户高频发送（摸上限）' },
  group_chat: { description: '群聊并发（模拟真实）' },
  reliability: { description: '可靠性验证（零丢失零重复）' },
};

// ====================================================================
// VU 内序号计数器（每个 VU 独立递增）
// ====================================================================

// 用于 teardown 阶段与实际发送数对比
let vuSentCount = 0;

// ====================================================================
// K6 执行配置
// ====================================================================

// 三个模式对应的发送间隔（ms）
const INTERVAL_MAP = {
  high_freq: 100,
  group_chat: 300,
  reliability: 200,
};
const INTERVAL_MS = parseInt(__ENV.MSG_INTERVAL_MS || String(INTERVAL_MAP[MODE] || 300), 10);

export const options = {
  scenarios: {
    message_storm: {
      executor: 'shared-iterations',
      vus: Math.min(VUS, userData.length),
      iterations: Math.min(VUS, userData.length) * 50,
      maxDuration: `${parseInt(DURATION) + 30}s`,
    },
  },
  summaryTimeUnit: 'ms',
  // 必须显式包含所有百分位数，因为 k6 默认不包含 p(50) 和 p(99)
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

  // 读取第一个用户作为 admin 创建房间
  const adminUser = userData[0];
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${adminUser.token}`,
      'X-User-Id': adminUser.userId,
      'X-Nickname': adminUser.nickname,
    },
  };

  const body = JSON.stringify({
    roomCode: ROOM_CODE,
    roomName: `perf-msg-${Date.now()}`,
    maxMembers: 1000,
  });

  const res = http.post(`${BASE_URL}/room`, body, params);

  if (res.status !== 200) {
    throw new Error(`[setup] 创建房间失败: ${res.status} ${res.body}`);
  }

  const json = JSON.parse(res.body);
  const roomId = json?.data?.roomId;

  if (!roomId) {
    throw new Error(`[setup] 创建房间返回数据异常: ${res.body}`);
  }

  console.log(`[setup] 创建共享房间成功 roomId=${roomId}`);
  return { roomId };
}

// ====================================================================
// VU 主循环：入房 → 持续发消息直到时间耗尽
// ====================================================================

export default function (data) {
  const roomId = data.roomId;
  const vuId = __VU - 1;
  const userIndex = vuId % userData.length;
  let localSeq = 0; // VU 内序号，每次发送递增

  // Step 1：入房（幂等：已在房间会快速拒绝）
  const params = makeHttpParams(userIndex);
  const joinResult = requestJoinAsync(params);

  if (joinResult.fastReject) {
    // 已入房或容量满，跳过轮询
  } else {
    pollJoinResult(joinResult.joinToken, params);
  }

  // Step 2：按时间持续发消息（带序号，用于可靠性验证）
  const durationSec = parseInt(DURATION, 10);
  const deadline = Date.now() + durationSec * 1000;
  let successCount = 0;
  let failCount = 0;

  while (Date.now() < deadline) {
    // 消息内容嵌入序号：perf-test-{vuId}-{seq}-{timestamp}
    // teardown 阶段通过解析序号检测乱序和重复
    const content = `perf-test-${vuId}-${localSeq}-${Date.now()}`;
    const result = sendMessage(userIndex, roomId, 1, content);

    if (result.success) {
      successCount++;
      vuSentCount++;
    } else {
      failCount++;
    }

    localSeq++;

    // 控制发送间隔
    sleep(INTERVAL_MS / 1000);
  }
}

// ====================================================================
// teardown：可靠性验证（零丢失、零重复、零乱序）
// ====================================================================

export function teardown(data) {
  const roomId = data.roomId;
  if (!roomId) return;

  const MESSAGE_SERVICE_URL = __ENV.MESSAGE_SERVICE_URL || 'http://localhost:8082';

  // 1. 查询 MySQL 中该房间的消息总数
  const countResult = countRoomMessages(0, roomId);
  const dbCount = countResult.count;

  // 2. 查询所有测试消息（content 以 perf-test- 开头），用于乱序和重复检测
  //    由于分页限制，这里取前 1000 条进行分析
  const allMessages = [];
  let page = 1;
  const pageSize = 100;

  while (allMessages.length < 1000) {
    const res = http.get(
      `${MESSAGE_SERVICE_URL}/message/room/${roomId}/messages?pageNum=${page}&pageSize=${pageSize}`,
      {
        headers: {
          'Authorization': `Bearer ${userData[0].token}`,
          'X-User-Id': userData[0].userId,
        },
        tags: { name: 'teardown_query' },
      }
    );
    if (res.status !== 200) break;

    try {
      const json = JSON.parse(res.body);
      const records = json?.data?.records || [];
      if (records.length === 0) break;
      // 只保留压测消息
      records.forEach(msg => {
        if (msg.content && msg.content.startsWith('perf-test-')) {
          allMessages.push(msg);
        }
      });
      if (records.length < pageSize) break;
      page++;
    } catch {
      break;
    }
  }

  // 3. 乱序检测：按 senderId 分组，对每组内按 createTime 升序，检测倒序
  const senderGroups = {};
  allMessages.forEach(msg => {
    const sid = msg.senderId;
    if (!senderGroups[sid]) senderGroups[sid] = [];
    senderGroups[sid].push(msg);
  });

  let reorderCount = 0;
  const reorderDetails = [];

  Object.entries(senderGroups).forEach(([senderId, messages]) => {
    // 按 createTime 升序
    messages.sort((a, b) => new Date(a.createTime) - new Date(b.createTime));

    // 检测时间倒序：后发消息的 createTime 不应早于先发消息
    for (let i = 1; i < messages.length; i++) {
      const prev = new Date(messages[i - 1].createTime).getTime();
      const curr = new Date(messages[i].createTime).getTime();
      if (curr < prev) {
        reorderCount++;
        if (reorderDetails.length < 5) {
          reorderDetails.push({
            senderId,
            messageId1: messages[i - 1].messageId,
            messageId2: messages[i].messageId,
            time1: messages[i - 1].createTime,
            time2: messages[i].createTime,
          });
        }
      }
    }
  });

  // 4. 重复检测：解析 perf-test-{vuId}-{seq}-{ts}，相同 vuId+seq 应只有一条
  const seqMap = {};
  let duplicateCount = 0;
  const duplicateDetails = [];

  allMessages.forEach(msg => {
    const parts = msg.content.split('-');
    if (parts.length >= 3 && parts[0] === 'perf' && parts[1] === 'test') {
      const vuId = parts[2];
      const seq = parts[3];
      const key = `${vuId}-${seq}`;
      if (seqMap[key]) {
        duplicateCount++;
        if (duplicateDetails.length < 3) {
          duplicateDetails.push({
            vuId,
            seq,
            messageId1: seqMap[key].messageId,
            messageId2: msg.messageId,
          });
        }
      } else {
        seqMap[key] = { messageId: msg.messageId };
      }
    }
  });

  // 5. 输出详细日志供捕获
  console.log(`[teardown] roomId=${roomId}`);
  console.log(`[teardown] vuSentCount=${vuSentCount} dbCount=${dbCount}`);
  console.log(`[teardown] reorderCount=${reorderCount} duplicateCount=${duplicateCount}`);

  if (reorderDetails.length > 0) {
    console.log(`[teardown] 乱序示例: ${JSON.stringify(reorderDetails)}`);
  }
  if (duplicateDetails.length > 0) {
    console.log(`[teardown] 重复示例: ${JSON.stringify(duplicateDetails)}`);
  }

  // 将结果挂到全局对象，供 handleSummary 读取
  const resultObj = {
    vuSentCount,
    dbCount,
    reorderCount,
    duplicateCount,
    messageSampleCount: allMessages.length,
  };

  if (typeof globalThis !== 'undefined') {
    globalThis.__perfResult = resultObj;
  } else if (typeof global !== 'undefined') {
    global.__perfResult = resultObj;
  }
}

// ====================================================================
// 压测结束后的汇总分析（标准化报告格式）
// ====================================================================

export function handleSummary(data) {
  const metrics = data.metrics || {};

  // 提取关键性能指标
  const totalRequests = metrics.http_reqs?.values?.count || 0;
  const failRate = (metrics.http_req_failed?.values?.rate || 0) * 100;
  const reqDuration = metrics.http_req_duration?.values || {};
  const messageSendDuration =
    metrics['http_req_duration{name:message_send}']?.values || reqDuration;

  const p50 = messageSendDuration['med'] || messageSendDuration.p50 || 0;
  const p95 = messageSendDuration['p(95)'] || messageSendDuration.p95 || 0;
  const p99 = messageSendDuration['p(99)'] || messageSendDuration.p99 || 0;
  const avg = messageSendDuration.avg || 0;

  // 从 teardown 阶段获取可靠性数据
  const perfResult = (typeof globalThis !== 'undefined' ? globalThis.__perfResult : null)
    ?? (typeof global !== 'undefined' ? global.__perfResult : null);

  const vuSentCount = perfResult?.vuSentCount ?? null;
  const dbCount = perfResult?.dbCount ?? null;
  const reorderCount = perfResult?.reorderCount ?? 0;
  const duplicateCount = perfResult?.duplicateCount ?? 0;

  // 可靠性计算
  const expectedMessages = vuSentCount !== null ? vuSentCount : 0;
  const lossCount = (dbCount !== null && expectedMessages > 0)
    ? expectedMessages - dbCount : null;
  const lossRate = lossCount !== null
    ? (lossCount / expectedMessages * 100).toFixed(2) : 'N/A';

  const durationSec = parseFloat(data.state?.testRunDuration || DURATION);
  const tps = durationSec > 0 ? (totalRequests / durationSec).toFixed(2) : 'N/A';

  // PASS/FAIL 判定
  const lossPass = lossCount === null || lossCount === 0;
  const reorderPass = reorderCount === 0;
  const duplicatePass = duplicateCount === 0;

  // 构建标准化报告
  let report = `
╔══════════════════════════════════════════════════════════════════╗
║               消息发送高频压测报告                              ║
║               场景: ${MODE} (${SCENARIO_CONFIG[MODE]?.description || ''})
╠══════════════════════════════════════════════════════════════════╣
║  [性能指标]                                                   ║
║    TPS:           ${tps}/s                                      ║
║    RT 均值:       ${avg.toFixed(2)}ms                               ║
║    RT P50:        ${p50.toFixed(2)}ms                               ║
║    RT P95:        ${p95.toFixed(2)}ms                               ║
║    RT P99:        ${p99.toFixed(2)}ms                               ║
║    HTTP 错误率:   ${failRate.toFixed(2)}%                             ║
╠══════════════════════════════════════════════════════════════════╣
║  [可靠性验证]                                                 ║
║    期望消息:     ${expectedMessages}                               ║
║    实际消息:     ${dbCount !== null ? dbCount : 'N/A (查询失败)'}
║    丢失数:       ${lossCount !== null ? lossCount : 'N/A'} (${lossRate}%)
║    丢失率:       [${lossPass ? 'PASS' : 'FAIL'}]                                  ║
║    乱序事件:     ${reorderCount}                                     ║
║    乱序检测:     [${reorderPass ? 'PASS' : 'FAIL'}]                                  ║
║    重复消息:     ${duplicateCount}                                     ║
║    重复检测:     [${duplicatePass ? 'PASS' : 'FAIL'}]                                  ║
╠══════════════════════════════════════════════════════════════════╣
║  [关键结论]                                                   ║`;

  if (p99 < 200 && lossPass && reorderPass && duplicatePass) {
    report += `
║    系统在 ${VUS} VUS 下表现良好。                               ║
║    P99 响应时间 ${p99.toFixed(0)}ms，低于 200ms 阈值。           ║
║    零丢失、零乱序、零重复，可靠性验证通过。                   ║`;
  } else if (p99 >= 200) {
    report += `
║    [WARNING] P99 响应时间 ${p99.toFixed(0)}ms 超过 200ms。      ║
║    用户感知可能出现卡顿，建议优化链路。                       ║`;
  } else {
    report += `
║    [WARNING] 可靠性验证存在异常，请检查日志。                 ║`;
  }

  report += `
╚══════════════════════════════════════════════════════════════════╝`;

  return { stdout: report };
}
