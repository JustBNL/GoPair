/**
 * 场景2：持续吞吐（循环进出）
 *
 * 验证目标：测量 RT（P50/P99）、TPS 及系统稳定性。
 * 每个 VU 持续执行：join → poll → 停留 → leave → 等待 → 循环。
 *
 * 运行示例：
 *   k6 run -e BASE_URL=http://localhost:8081 \
 *          -e ROOM_CODE=54273898 \
 *          -e VUS=50 \
 *          -e DURATION=60s \
 *          scenarios/throughput.js
 *
 * 环境变量：
 *   BASE_URL    - 服务地址，默认 http://localhost:8081
 *   ROOM_CODE   - 房间码，默认 54273898（setup 自动创建）
 *   VUS         - 虚拟用户数，默认 50
 *   DURATION    - 压测持续时间，默认 60s
 *   MIN_STAY_MS - 最小停留时间(ms)，默认 2000
 *   MAX_STAY_MS - 最大停留时间(ms)，默认 5000
 *   MIN_WAIT_MS - 离开后最小等待(ms)，默认 1000
 *   MAX_WAIT_MS - 离开后最大等待(ms)，默认 2000
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  BASE_URL,
  ROOM_CODE,
  userData,
  pollJoinResult,
  makeHttpParams,
  requestJoinAsync,
  createTestRoom,
} from '../common.js';

function randomIntBetween(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

const VUS = parseInt(__ENV.VUS || '50', 10);
const DURATION = __ENV.DURATION || '60s';
const MIN_STAY_MS = parseInt(__ENV.MIN_STAY_MS || '2000', 10);
const MAX_STAY_MS = parseInt(__ENV.MAX_STAY_MS || '5000', 10);
const MIN_WAIT_MS = parseInt(__ENV.MIN_WAIT_MS || '1000', 10);
const MAX_WAIT_MS = parseInt(__ENV.MAX_WAIT_MS || '2000', 10);

// ====================================================================
// setup：自动创建测试房间（幂等：已存在则复用）
// ====================================================================

export function setup() {
  const roomCode = ROOM_CODE;
  const { roomId, roomCode: actualRoomCode } = createTestRoom(BASE_URL, roomCode, userData.length);

  if (!roomId) {
    throw new Error(`[setup] 无法获取有效 roomId，throughput 测试无法进行`);
  }

  console.log(`[setup] throughput 场景就绪 roomCode=${actualRoomCode}(${roomCode}) roomId=${roomId}`);

  return { roomId, roomCode: actualRoomCode };
}

// ====================================================================
// 持续吞吐测试：ramp-up 后保持目标 VUS
// ====================================================================

export const options = {
  scenarios: {
    throughput_sustained: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: VUS },       // 10s 线性爬坡到目标 VUS
        { duration: DURATION, target: VUS },    // 保持目标 VUS
        { duration: '10s', target: 0 },         // 10s 线性降载
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // join/async 的 P99 响应时间不超过 500ms
    'http_req_duration{name:join_async}': ['p(99)<500'],
    // poll/result 的 P99 不超过 200ms
    'http_req_duration{name:poll_result}': ['p(99)<200'],
    // HTTP 错误率不超过 5%
    http_req_failed: ['rate<0.05'],
  },
};

// ====================================================================
// 每个 VU 的主逻辑：持续循环 join → stay → leave
// ====================================================================

export default function (data) {
  const roomCode = data.roomCode;

  const vuId = (__VU - 1) % userData.length;
  const params = makeHttpParams(vuId);
  const user = userData[vuId];

  // 阶段1：异步加入（直接传入 roomCode）
  const joinRes = requestJoinAsync(params, roomCode);
  if (joinRes.fastReject) {
    // 已在房间，等待后重试
    sleep(randomIntBetween(MIN_WAIT_MS / 1000, MAX_WAIT_MS / 1000));
    return;
  }
  if (!joinRes.accepted || !joinRes.joinToken) {
    console.error(`[VU ${__VU}] join/async failed`);
    sleep(randomIntBetween(MIN_WAIT_MS / 1000, MAX_WAIT_MS / 1000));
    return;
  }

  // 阶段2：轮询等待结果
  const pollResult = pollJoinResult(joinRes.joinToken, params);

  if (pollResult.status === 'TIMEOUT') {
    console.warn(`[VU ${__VU}] poll timeout`);
    sleep(1);
    return;
  }

  if (pollResult.status === 'FAILED') {
    // 入房失败，等待后重试（可能是容量满后被拒绝）
    sleep(randomIntBetween(MIN_WAIT_MS / 1000, MAX_WAIT_MS / 1000));
    return;
  }

  if (pollResult.status !== 'JOINED') {
    console.warn(`[VU ${__VU}] unexpected status: ${pollResult.status}`);
    sleep(1);
    return;
  }

  // 阶段3：停留（随机时长）
  const roomId = pollResult.roomId;
  const stayMs = randomIntBetween(MIN_STAY_MS, MAX_STAY_MS);
  sleep(stayMs / 1000);

  // 阶段4：离开房间
  const leaveRes = http.post(
    `${BASE_URL}/room/${roomId}/leave`,
    null,
    { ...params, tags: { name: 'room_leave' } }
  );

  check(leaveRes, {
    'leave returns 200': (r) => r.status === 200,
  });

  // 阶段5：离开后等待（让房间有喘息空间）
  const waitMs = randomIntBetween(MIN_WAIT_MS, MAX_WAIT_MS);
  sleep(waitMs / 1000);
}
