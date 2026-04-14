/**
 * 场景1：容量验证（防超卖脉冲测试）
 *
 * 验证目标：在 M >> N 的并发请求下，最终 JOINED 人数严格等于房间容量。
 * 设计为一次脉冲：所有 VUS 在极短时间内同时发起 join，等待所有结果后统计。
 *
 * 运行示例：
 *   k6 run -e BASE_URL=http://localhost:8081 \
 *          -e ROOM_CODE=54273898 \
 *          -e CAPACITY=10 \
 *          scenarios/capacity.js
 *
 * 环境变量：
 *   BASE_URL  - 服务地址，默认 http://localhost:8081
 *   ROOM_CODE - 房间码，默认 54273898
 *   CAPACITY  - 房间容量，默认 10
 *   VUS       - 虚拟用户数（应远大于 CAPACITY），默认 100
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import {
  BASE_URL,
  ROOM_CODE,
  CAPACITY,
  VUS,
  userData,
  pollJoinResult,
  makeHttpParams,
  requestJoinAsync,
} from '../common.js';

// ====================================================================
// 脉冲测试选项：per-vu-iterations 让每个 VU 执行一次迭代后结束
// ====================================================================

export const options = {
  scenarios: {
    capacity_pulse: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '120s',
    },
  },
  summaryTimeUnit: 'ms',
};

// ====================================================================
// 每个 VU 的主逻辑：发起 join，等待结果，记录状态
// ====================================================================

export default function () {
  // 按 VU 编号取用户数据（循环复用，避免 userData 不足）
  const vuId = (__VU - 1) % userData.length;
  const params = makeHttpParams(vuId);
  const user = userData[vuId];

  // 1. 发起 join/async
  const joinRes = requestJoinAsync(params);
  const joinStart = Date.now();

  if (joinRes.fastReject) {
    // 快速拒绝：已在房间 / 正在处理中
    // 记录为特殊状态，不参与 JOINED 统计
    console.log(`[VU ${__VU}] fast_reject message="${joinRes.message}" user=${user.userId}`);
    return;
  }

  if (!joinRes.accepted || !joinRes.joinToken) {
    console.error(`[VU ${__VU}] join/async unexpected response: ${joinRes.statusCode}`);
    return;
  }

  // 2. 轮询等待结果
  const pollResult = pollJoinResult(joinRes.joinToken, params);
  const endToEndMs = Date.now() - joinStart;

  // 3. 实时断言：JOINED 必须有 roomId
  check(pollResult, {
    'join result has roomId when JOINED': (r) =>
      r.status !== 'JOINED' || r.roomId !== null,
    'poll completed': (r) => r.status !== 'TIMEOUT',
  });

  // 4. 打印结果（handleSummary 无法精确到每个 VU，这里打印供调试）
  const tag = pollResult.status === 'JOINED' ? 'JOINED' : pollResult.status;
  console.log(`[VU ${__VU}] status=${tag} roomId=${pollResult.roomId} polls=${pollResult.pollCount} e2e=${endToEndMs}ms user=${user.userId}`);
}

// ====================================================================
// 压测结束后的汇总分析
// ====================================================================

export function handleSummary(data) {
  const metrics = data.metrics || {};
  const totalRequests = metrics.http_reqs?.values?.count || 0;
  const failRate = metrics.http_req_failed?.values?.rate || 0;

  let summary = '\n========== 容量验证汇总 ==========\n';
  summary += `总请求数: ${totalRequests}\n`;
  summary += `HTTP错误率: ${(failRate * 100).toFixed(2)}%\n`;
  summary += `预期容量: ${CAPACITY}\n`;
  summary += `压测 VUS: ${VUS}\n`;
  summary += '\n精确统计：请将上方 console.log 输出中的 status 数量手动统计\n';
  summary += '或运行：grep -c "status=JOINED" <日志文件>\n';

  return { stdout: summary };
}
