import http from 'k6/http';
import { check, sleep } from 'k6';
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
    const parts = line.split(',');
    // 格式：token,userId,nickname,email,password（5字段）
    return {
      token: parts[0]?.trim() || '',
      userId: parts[1]?.trim() || '',
      nickname: parts[2]?.trim() || '',
      email: parts[3]?.trim() || '',
      password: parts[4]?.trim() || '',
    };
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

  // 持续轮询直到获得确定结果：JOINED / FAILED / ERROR / TIMEOUT
  // PENDING 和 PROCESSING 都是中间状态，需要继续轮询
  while (status === 'PENDING' || status === 'PROCESSING') {
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
      break;
    }

    // 中间状态：sleep 后继续轮询
    sleep(delay);
    delay = Math.min(delay * 1.5, 1.0);
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
 *
 * @param {object} httpParams - HTTP 请求参数（包含 headers）
 * @param {string} [roomCode] - 可选，优先使用此 roomCode；否则从 ROOM_CODE_OVERRIDE 或 ROOM_CODE 常量读取。
 */
export function requestJoinAsync(httpParams, roomCode) {
  const code = roomCode || __ENV.ROOM_CODE_OVERRIDE || ROOM_CODE;
  const body = JSON.stringify({ roomCode: code });
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
  const respCode = json?.code || 0;

  if (message === '已在房间' || message === '已有加入请求正在处理中，请稍候') {
    // 快速拒绝，无需轮询
    return { accepted: false, joinToken: null, fastReject: true, statusCode: 200, message };
  }

  if (respCode === 20202 || message === '房间已满') {
    return { accepted: false, joinToken: null, fastReject: true, statusCode: 200, message };
  }

  const joinToken = data?.joinToken;

  // data 为 null 或 joinToken 缺失时，视为快速拒绝（已在房间或其他异常状态）
  if (!joinToken) {
    return { accepted: false, joinToken: null, fastReject: true, statusCode: 200, message: message || 'no_join_token' };
  }

  return { accepted: true, joinToken, fastReject: false, statusCode: 200 };
}

// ====================================================================
// 消息发送封装
// ====================================================================

/**
 * 向消息服务发送消息，返回发送结果与耗时。
 *
 * @param {number} userIndex - 用户在 userData 数组中的索引
 * @param {number} roomId - 目标房间 ID
 * @param {number} messageType - 消息类型（1=文本, 2=图片, 3=文件, 4=语音）
 * @param {string} content - 消息内容
 * @returns {{ success: boolean, statusCode: number, messageId: string|null, durationMs: number }}
 */
export function sendMessage(userIndex, roomId, messageType, content) {
  const user = userData[userIndex % userData.length];
  const msgSvcUrl = __ENV.MESSAGE_SERVICE_URL || 'http://localhost:8081';
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${user.token}`,
      'X-User-Id': user.userId,
      'X-Nickname': user.nickname,
    },
    tags: { name: 'message_send' },
  };

  const body = JSON.stringify({
    roomId: roomId,
    messageType: messageType,
    content: content,
  });

  const startMs = Date.now();
  const res = http.post(`${msgSvcUrl}/message/send`, body, params);
  const durationMs = Date.now() - startMs;

  if (res.status !== 200) {
    return { success: false, statusCode: res.status, messageId: null, durationMs };
  }

  try {
    const json = JSON.parse(res.body);
    // HTTP 200 不代表业务成功，需检查 success 字段
    const ok = json?.success === true;
    const messageId = ok ? (json?.data?.messageId || null) : null;
    return { success: ok, statusCode: res.status, messageId, durationMs, code: json?.code, msg: json?.msg };
  } catch {
    return { success: false, statusCode: res.status, messageId: null, durationMs };
  }
}

// ====================================================================
// 消息计数封装
// ====================================================================

/**
 * 查询指定房间的消息总数（用于 teardown 阶段可靠性验证）。
 *
 * @param {number} userIndex - 用户在 userData 数组中的索引
 * @param {number} roomId - 目标房间 ID
 * @returns {{ count: number|null, statusCode: number }}
 */
export function countRoomMessages(userIndex, roomId) {
  const user = userData[userIndex % userData.length];
  const msgSvcUrl = __ENV.MESSAGE_SERVICE_URL || 'http://localhost:8081';
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${user.token}`,
      'X-User-Id': user.userId,
      'X-Nickname': user.nickname,
    },
    tags: { name: 'count_messages' },
  };

  const res = http.get(`${msgSvcUrl}/message/room/${roomId}/messages?pageNum=1&pageSize=1`, params);

  if (res.status !== 200) {
    return { count: null, statusCode: res.status };
  }

  try {
    const json = JSON.parse(res.body);
    const count = json?.data?.total ?? null;
    return { count, statusCode: res.status };
  } catch {
    return { count: null, statusCode: res.status };
  }
}

// ====================================================================
// 房间创建封装（幂等：先查后建）
// ====================================================================

/**
 * 创建测试房间。
 * 注意：后端自动生成 roomCode，客户端无需也不应传递 roomCode。
 * 每次调用都会创建新房间（带时间戳确保唯一性）。
 *
 * @param {string} baseUrl - Room Service 地址
 * @param {string} roomCode - 房间码（仅用于日志标识，不传递给后端）
 * @param {number} maxMembers - 房间最大成员数
 * @returns {{ roomId: number|null, roomCode: string }}
 */
export function createTestRoom(baseUrl, roomCode, maxMembers) {
  const user = userData[0];
  const createParams = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${user.token}`,
      'X-User-Id': user.userId,
      'X-Nickname': user.nickname,
    },
    tags: { name: 'create_room' },
  };

  const body = JSON.stringify({
    roomName: `perf-room-${roomCode}-${Date.now()}`,
    maxMembers: maxMembers,
  });

  const createRes = http.post(`${baseUrl}/room`, body, createParams);

  if (createRes.status !== 200) {
    console.error(`[createTestRoom] 创建房间失败: ${createRes.status} ${createRes.body}`);
    return { roomId: null, roomCode };
  }

  try {
    const json = JSON.parse(createRes.body);
    const roomId = json?.data?.roomId;
    const actualRoomCode = json?.data?.roomCode;
    if (!roomId) {
      console.error(`[createTestRoom] 创建房间返回数据异常: ${createRes.body}`);
      return { roomId: null, roomCode };
    }
    console.log(`[createTestRoom] 创建房间成功 roomCode=${actualRoomCode} roomId=${roomId}`);
    return { roomId, roomCode: actualRoomCode };
  } catch {
    console.error(`[createTestRoom] 解析创建响应失败: ${createRes.body}`);
    return { roomId: null, roomCode };
  }
}
