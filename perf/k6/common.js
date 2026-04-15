import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

// ====================================================================
// 共享配置
// ====================================================================

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
export const ROOM_CODE = __ENV.ROOM_CODE || '54273898';
export const ROOM_ID = __ENV.ROOM_ID || '18';
export const CAPACITY = parseInt(__ENV.CAPACITY || '10', 10);
export const VUS = parseInt(__ENV.VUS || '50', 10);
export const DURATION = __ENV.DURATION || '60s';

// ====================================================================
// 用户数据（复用现有 CSV，无需重新生成）
// ====================================================================

export const userData = new SharedArray('user_info', () => {
  const raw = open('../user_info.txt');
  const lines = raw.split('\n').filter(l => l.trim());
  return lines.map(line => {
    const [token, userId, nickname] = line.split(',');
    return { token: token.trim(), userId: userId.trim(), nickname: nickname.trim() };
  });
});

// ====================================================================
// 轮询策略：自适应退避
// ====================================================================

/**
 * 自适应退避轮询 join/result，直到状态不再是 PENDING。
 * 初始延迟 50ms，每次×1.5，上限 1000ms，最多轮询 20 次。
 *
 * @param {string} joinToken - join/async 返回的令牌
 * @param {object} httpParams - k6 HTTP 请求参数（包含 headers）
 * @returns {{ status: string, roomId: number|null, pollCount: number, totalMs: number }}
 */
export function pollJoinResult(joinToken, httpParams) {
  let delay = 0.05;
  let pollCount = 0;
  let status = 'PENDING';
  let roomId = null;
  const startMs = Date.now();

  while (status === 'PENDING') {
    if (pollCount >= 20) {
      status = 'TIMEOUT';
      break;
    }

    const res = http.get(
      `${BASE_URL}/room/join/result?token=${encodeURIComponent(joinToken)}`,
      { ...httpParams, tags: { name: 'poll_result' } }
    );

    pollCount++;

    if (res.status !== 200) {
      status = 'ERROR';
      break;
    }

    const body = JSON.parse(res.body);
    const data = body?.data;
    status = data?.status || 'PENDING';
    if (status === 'JOINED' || status === 'FAILED') {
      roomId = data?.roomId || null;
    }

    if (status === 'PENDING') {
      sleep(delay);
      delay = Math.min(delay * 1.5, 1.0);
    }
  }

  return { status, roomId, pollCount, totalMs: Date.now() - startMs };
}

// ====================================================================
// HTTP 客户端封装
// ====================================================================

/**
 * 根据 userIndex 构造当前 VU 的 HTTP 请求头。
 * userIndex 由调用方根据 __VU % userData.length 计算传入。
 */
export function makeHttpParams(userIndex) {
  const user = userData[userIndex % userData.length];
  return {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${user.token}`,
      'X-User-Id': user.userId,
      'X-Nickname': user.nickname,
    },
  };
}

// ====================================================================
// 统一的 join/async 请求封装
// ====================================================================

/**
 * 发起 join/async 请求，返回 { joinToken, accepted }。
 * accepted=false 表示请求被快速拒绝（ALREADY_JOINED / FULL 等），无需轮询。
 */
export function requestJoinAsync(httpParams) {
  const body = JSON.stringify({ roomCode: ROOM_CODE });
  const res = http.post(`${BASE_URL}/room/join/async`, body, {
    ...httpParams,
    tags: { name: 'join_async' },
  });

  if (res.status !== 200) {
    return { accepted: false, joinToken: null, fastReject: true, statusCode: res.status };
  }

  const json = JSON.parse(res.body);
  const data = json?.data;
  const message = data?.message || json?.msg || '';
  const code = json?.code || 0;

  if (message === '已在房间' || message === '已有加入请求正在处理中，请稍候') {
    // 快速拒绝，无需轮询
    return { accepted: false, joinToken: null, fastReject: true, statusCode: 200, message };
  }

  if (code === 20202 || message === '房间已满') {
    return { accepted: false, joinToken: null, fastReject: true, statusCode: 200, message };
  }

  const joinToken = data?.joinToken;
  return { accepted: true, joinToken, fastReject: false, statusCode: 200 };
}
