/**
 * 场景4：容量边界（熔断降级测试）
 *
 * 验证目标：房间满员后新请求收到 FAILED，同时已加入成员能正常离开。
 *
 * 运行示例：
 *   k6 run -e BASE_URL=http://localhost:8081 \
 *          -e ROOM_CODE=54540797 \
 *          -e CAPACITY=10 \
 *          -e VUS=50 \
 *          -e DURATION=60s \
 *          scenarios/boundary.js
 *
 * 环境变量：
 *   BASE_URL  - 服务地址，默认 http://localhost:8081
 *   ROOM_CODE - 房间码，默认 54540797（setup 自动创建）
 *   CAPACITY  - 房间容量，默认 10
 *   VUS       - 虚拟用户数（应远大于 CAPACITY），默认 30
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  BASE_URL,
  ROOM_CODE,
  CAPACITY,
  userData,
  pollJoinResult,
  makeHttpParams,
  requestJoinAsync,
  createTestRoom,
} from '../common.js';

function randomIntBetween(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

const VUS = parseInt(__ENV.VUS || '30', 10);
const DURATION = __ENV.DURATION || '60s';

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
    http_req_failed: ['rate<0.3'],
  },
};

// ====================================================================
// setup：自动创建测试房间（幂等：已存在则复用）
// ====================================================================

export function setup() {
  const roomCode = ROOM_CODE;
  const maxMembers = CAPACITY;

  const { roomId, roomCode: actualRoomCode } = createTestRoom(BASE_URL, roomCode, maxMembers);

  if (!roomId) {
    throw new Error(`[setup] 无法获取有效 roomId，boundary 测试无法进行`);
  }

  console.log(`[setup] boundary 场景就绪 roomCode=${actualRoomCode}(${roomCode}) roomId=${roomId} capacity=${maxMembers}`);

  return { roomId, roomCode: actualRoomCode, capacity: maxMembers };
}

export default function (data) {
  const roomCode = data.roomCode;

  const vuId = (__VU - 1) % userData.length;
  const params = makeHttpParams(vuId);
  const user = userData[vuId];

  const joinRes = requestJoinAsync(params, roomCode);

  if (joinRes.fastReject) {
    if (joinRes.rejectReason === 'room_full') {
      console.log(`[VU ${__VU}] ROOM_FULL user=${user.userId}`);
    } else {
      console.log(`[VU ${__VU}] ALREADY_JOINED user=${user.userId}`);
    }
    sleep(randomIntBetween(1, 3));
    return;
  }

  if (!joinRes.accepted || !joinRes.joinToken) {
    console.warn(`[VU ${__VU}] join/async failed status=${joinRes.statusCode}`);
    sleep(randomIntBetween(1, 3));
    return;
  }

  const pollResult = pollJoinResult(joinRes.joinToken, params);

  if (pollResult.status === 'JOINED') {
    const roomId = pollResult.roomId;
    sleep(randomIntBetween(3, 8));
    const leaveRes = http.post(`${BASE_URL}/room/${roomId}/leave`, null, {
      ...params,
      tags: { name: 'room_leave' },
    });
    check(leaveRes, {
      'leave ok': (r) => r.status === 200,
    });
  } else if (pollResult.status === 'FAILED') {
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

export function handleSummary(data) {
  const metrics = data.metrics || {};
  const total = metrics.http_reqs?.values?.count || 0;

  let summary = '\n========== CAPACITY BOUNDARY SUMMARY ==========\n';
  summary += `Total requests : ${total}\n`;
  summary += `Target capacity: ${CAPACITY}\n`;
  summary += `VUS            : ${VUS}\n`;
  summary += '\nNotes:\n';
  summary += '  - Check console output for FAILED occurrences\n';
  summary += '  - FAILED should appear after JOINED reaches capacity\n';
  summary += '  - Run analyze.js for exact JOINED/FAILED counts\n';

  return { stdout: summary };
}
