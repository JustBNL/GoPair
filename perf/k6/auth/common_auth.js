/**
 * Auth 场景共享模块
 *
 * 职责：管理登录压测所需的凭证生命周期。
 * setup 阶段幂等注册测试用户并获取完整凭证，输出数据供 VU 主循环复用。
 *
 * 使用方式：
 *   import { credentials, loginUser, BASE_URL } from './auth/common_auth.js';
 *
 * 环境变量：
 *   BASE_URL         - 服务地址，默认 http://localhost:8081
 *   AUTH_USER_COUNT  - 测试用户数量，默认 200
 *   AUTH_PASSWORD    - 测试用户密码，默认 123456
 *   AUTH_EMAIL_DOMAIN - 邮箱域名，默认 example.com（跳过验证码）
 */

import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

// ====================================================================
// 共享配置（模块顶层，init context）
// ====================================================================

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const USER_COUNT = parseInt(__ENV.AUTH_USER_COUNT || '200', 10);
const PASSWORD = __ENV.AUTH_PASSWORD || '123456';
const EMAIL_DOMAIN = __ENV.AUTH_EMAIL_DOMAIN || 'example.com';

// SharedArray 必须在模块顶层（init context）定义，不能放在函数内部
export const credentials = new SharedArray('auth_credentials', () => {
  const creds = [];
  for (let i = 1; i <= USER_COUNT; i++) {
    const num = String(i).padStart(4, '0');
    creds.push({
      email: `perfauth${num}@${EMAIL_DOMAIN}`,
      password: PASSWORD,
      token: null,
      userId: null,
      nickname: null,
    });
  }
  return creds;
});

// ====================================================================
// 辅助函数（可在 VU context 调用）
// ====================================================================

/**
 * 发送登录请求，返回完整登录响应。
 *
 * @param {string} email
 * @param {string} password
 * @returns {{ ok: boolean, token: string|null, userId: number|null, nickname: string|null, statusCode: number, durationMs: number }}
 */
export function loginUser(email, password) {
  const body = JSON.stringify({ email, password });
  const startMs = Date.now();
  const res = http.post(`${BASE_URL}/user/login`, body, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'login' },
  });
  const durationMs = Date.now() - startMs;

  if (res.status !== 200) {
    return { ok: false, token: null, userId: null, nickname: null, statusCode: res.status, durationMs };
  }

  try {
    const json = JSON.parse(res.body);
    const data = json?.data;
    return {
      ok: true,
      token: data?.token || null,
      userId: data?.userId || null,
      nickname: data?.nickname || null,
      statusCode: res.status,
      durationMs,
    };
  } catch {
    return { ok: false, token: null, userId: null, nickname: null, statusCode: res.status, durationMs };
  }
}

/**
 * 发送注册请求（幂等：已存在用户返回 200 但 code 非 200，跳过即可）。
 *
 * @param {string} email
 * @param {string} password
 * @param {string} nickname
 * @returns {{ ok: boolean, statusCode: number }}
 */
function registerUser(email, password, nickname) {
  const body = JSON.stringify({ email, password, nickname });
  const res = http.post(`${BASE_URL}/user/register`, body, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'register' },
  });
  if (res.status === 200) {
    try {
      const json = JSON.parse(res.body);
      return { ok: json?.code === 200, statusCode: res.status };
    } catch {
      return { ok: false, statusCode: res.status };
    }
  }
  return { ok: false, statusCode: res.status };
}

// ====================================================================
// setup：幂等注册 + 登录获取完整凭证
// ====================================================================

export function setup() {
  console.log(`[setup] 开始准备 ${USER_COUNT} 个测试用户凭证...`);

  const results = [];

  for (let i = 0; i < credentials.length; i++) {
    const cred = credentials[i];

    // 1. 注册（幂等：已存在则跳过）
    const regRes = registerUser(cred.email, cred.password, `perfauth${String(i + 1).padStart(4, '0')}`);
    if (!regRes.ok) {
      console.warn(`[setup] 注册失败 ${cred.email} status=${regRes.statusCode}`);
    }

    // 2. 登录获取 token
    const loginRes = loginUser(cred.email, cred.password);

    if (loginRes.ok) {
      // 写入 SharedArray（全局可见，setup 完成后 VU 可直接读取）
      credentials[i].token = loginRes.token;
      credentials[i].userId = loginRes.userId;
      credentials[i].nickname = loginRes.nickname;
      results.push({ ok: true, email: cred.email });
    } else {
      console.error(`[setup] 登录失败 ${cred.email} status=${loginRes.statusCode}`);
      results.push({ ok: false, email: cred.email });
    }

    if ((i + 1) % 50 === 0) {
      console.log(`[setup] 进度 ${i + 1}/${USER_COUNT}`);
    }
  }

  const successCount = results.filter(r => r.ok).length;
  console.log(`[setup] 完成：成功 ${successCount}/${USER_COUNT}`);

  return { userCount: USER_COUNT, successCount };
}
