/**
 * 场景3：容量边界（熔断降级测试）
 *
 * 验证目标：在持续吞吐中观察容量满后的降级行为。
 * 核心观察：当 JOINED 人数达到容量后，新请求应得到 FULL 响应，
 *          且已加入的用户仍能正常离开。
 *
 * 运行示例：
 *   k6 run -e BASE_URL=http://localhost:8081 \
 *          -e ROOM_CODE=54273898 \
 *          -e CAPACITY=10 \
 *          -e VUS=30 \
 *          -e DURATION=60s \
 *          scenarios/boundary.js
 *
 * 环境变量：
 *   BASE_URL  - 服务地址，默认 http://localhost:8081
 *   ROOM_CODE - 房间码，默认 54273898
 *   CAPACITY  - 房间容量，默认 10
 *   VUS       - 虚拟用户数，默认 30
 *   DURATION  - 压测持续时间，默认 60s
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'k6/math';
import {
  BASE_URL,
  ROOM_CODE,
  CAPACITY,
  userData,
  pollJoinResult,
  makeHttpParams,
  requestJoinAsync,
} from '../common.js';

const VUS = parseInt(__ENV.VUS || '30', 10);
const DURATION = __ENV.DURATION || '60s';

// ====================================================================
// 全局状态（跨 VU 共享）：用于记录 JOINED 人数（近似）
// 注意：这里只是近似统计，用于启发式观察，不做精确断言
// ====================================================================

// ====================================================================
// 持续吞吐 + 边界观察
// ====================================================================

export const options = {
  scenarios: {
    boundary_observation: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: VUS },
        { duration: DURATION, target: VUS },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // 允许一定的 HTTP 错误率（因为容量满后的 FULL 是预期的）
    http_req_failed: ['rate<0.3'],
  },
};

export default function () {
  const vuId = (__VU - 1) % userData.length;
  const params = makeHttpParams(vuId);
  const user = userData[vuId];

  // 尝试加入
  const joinRes = requestJoinAsync(params);

  if (joinRes.fastReject) {
    // 已快速拒绝（ALREADY_JOINED / ALREADY_PROCESSING），记录但不退出
    console.log(`[VU ${__VU}] fast_reject user=${user.userId}`);
    sleep(randomIntBetween(1, 3));
    return;
  }

  if (!joinRes.accepted || !joinRes.joinToken) {
    // join/async 失败
    console.warn(`[VU ${__VU}] join/async failed status=${joinRes.statusCode}`);
    sleep(randomIntBetween(1, 3));
    return;
  }

  // 轮询
  const pollResult = pollJoinResult(joinRes.joinToken, params);

  if (pollResult.status === 'JOINED') {
    const roomId = pollResult.roomId;

    // 入房成功，短暂停留后离开
    sleep(randomIntBetween(3, 8));

    const leaveRes = http.post(`${BASE_URL}/room/${roomId}/leave`, null, {
      ...params,
      tags: { name: 'room_leave' },
    });
    check(leaveRes, {
      'leave ok': (r) => r.status === 200,
    });
  } else if (pollResult.status === 'FAILED') {
    // 入房失败（容量满后被拒绝）
    console.log(`[VU ${__VU}] FAILED user=${user.userId} polls=${pollResult.pollCount} e2e=${pollResult.totalMs}ms`);
    sleep(randomIntBetween(2, 5));
  } else if (pollResult.status === 'TIMEOUT') {
    console.warn(`[VU ${__VU}] TIMEOUT user=${user.userId}`);
    sleep(randomIntBetween(1, 3));
  } else {
    console.warn(`[VU ${__VU}] unknown status=${pollResult.status}`);
    sleep(randomIntBetween(1, 3));
  }
}

// ====================================================================
// 汇总观察（启发式）
// ====================================================================

export function handleSummary(data) {
  const metrics = data.metrics || {};
  const total = metrics.http_reqs?.values?.count || 0;

  let summary = '\n========== 容量边界观察汇总 ==========\n';
  summary += `总请求数:   ${total}\n`;
  summary += `目标容量:   ${CAPACITY}\n`;
  summary += `压测 VUS:   ${VUS}\n`;
  summary += '\n重要提示：\n`;
  summary += '  - 请查看上方 console.log 输出，观察 FAILED 出现时机\n';
  summary += '  - FAILED 应在 JOINED 达到容量后陆续出现\n';
  summary += '  - 若 FAILED 过早或过晚，说明防超卖逻辑可能有问题\n';
  summary += '  - 运行 analyze.js 脚本可获得精确的 JOINED/FAILED 统计\n';

  return { stdout: summary };
}
