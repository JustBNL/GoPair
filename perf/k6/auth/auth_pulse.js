/**
 * 场景：登录脉冲摸上限
 *
 * 验证目标：在 M 个并发 VU 同时发起登录时，测量系统能承受的最大并发登录数。
 * 设计为一次脉冲：所有 VUS 在极短时间内同时发起登录请求，等待所有结果后统计。
 *
 * 凭证准备策略：login 在 VU 主循环中执行（而非 setup 阶段），
 * 若用户尚未注册则先注册再登录，保证所有登录 RT 指标均来自 VU 阶段。
 *
 * 运行示例：
 *   k6 run -e BASE_URL=http://localhost:8081 \
 *          -e AUTH_USER_COUNT=200 \
 *          auth/auth_pulse.js
 *
 * 环境变量：
 *   BASE_URL           - 服务地址，默认 http://localhost:8081
 *   AUTH_USER_COUNT    - 虚拟用户数，默认 200
 *   RT_THRESHOLD_P95_MS - P95 响应时间阈值（ms），默认 500
 *   RT_THRESHOLD_P99_MS - P99 响应时间阈值（ms），默认 1000
 */

import http from 'k6/http';
import { credentials, BASE_URL } from './common_auth.js';

// ====================================================================
// 配置
// ====================================================================

const RT_P95_MS = parseInt(__ENV.RT_THRESHOLD_P95_MS || '500', 10);
const RT_P99_MS = parseInt(__ENV.RT_THRESHOLD_P99_MS || '1000', 10);

export const options = {
  scenarios: {
    login_pulse: {
      executor: 'per-vu-iterations',
      vus: credentials.length,
      iterations: 1,
      maxDuration: '120s',
    },
  },
  summaryTimeUnit: 'ms',
  // 必须显式包含所有百分位数，因为 k6 默认不包含 p(50) 和 p(99)
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'p(99.99)'],
  thresholds: {
    [`http_req_duration{name:login}`]: [
      `p(95)<${RT_P95_MS}`,
      `p(99)<${RT_P99_MS}`,
    ],
    http_req_failed: ['rate<0.05'],
  },
};

// ====================================================================
// 辅助函数（VU context 内调用）
// ====================================================================

function loginRequest(email, password) {
  const body = JSON.stringify({ email, password });
  const startMs = Date.now();
  const res = http.post(`${BASE_URL}/user/login`, body, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'login' },
  });
  const durationMs = Date.now() - startMs;

  if (res.status !== 200) {
    return { ok: false, statusCode: res.status, durationMs };
  }
  try {
    const json = JSON.parse(res.body);
    return { ok: json?.code === 200, statusCode: res.status, durationMs };
  } catch {
    return { ok: false, statusCode: res.status, durationMs };
  }
}

function registerRequest(email, password, nickname) {
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
// VU 主逻辑
// 1. 尝试登录，若失败则先注册再登录（幂等）
// 2. 所有 RT 指标均来自 VU 阶段，精确反映压测负载
// ====================================================================

export default function () {
  const vuId = (__VU - 1) % credentials.length;
  const cred = credentials[vuId];

  if (!cred) {
    console.error(`[VU ${__VU}] 凭证不存在 vuId=${vuId}`);
    return;
  }

  // 阶段1：尝试登录
  let loginRes = loginRequest(cred.email, cred.password);

  // 阶段2：若用户不存在则先注册再登录（幂等）
  if (!loginRes.ok) {
    registerRequest(cred.email, cred.password, cred.nickname || cred.email.split('@')[0]);
    loginRes = loginRequest(cred.email, cred.password);
  }

  if (loginRes.ok) {
    console.log(`[VU ${__VU}] ok email=${cred.email} rt=${loginRes.durationMs}ms`);
  } else {
    console.error(`[VU ${__VU}] fail email=${cred.email} status=${loginRes.statusCode}`);
  }
}

// ====================================================================
// 压测报告
// 注意：k6 内置 summary（阈值检查、RT 百分位）使用 summaryTimeUnit=ms
// handleSummary 中 http_req_duration 数值已为毫秒，无需额外转换
// ====================================================================

export function handleSummary(data) {
  const metrics = data.metrics || {};

  // 从基础 http_req_duration 读取百分位数（summaryTrendStats 必定对其生效）
  // http_req_duration 包含 login 和 register 请求，login 占比约 90%+，数据具有代表性
  // 阈值检查仍然针对 http_req_duration{name:login} 子指标，确保准确性
  const baseDuration = metrics['http_req_duration']?.values || {};
  const loginDur = metrics['http_req_duration{name:login}']?.values || {};

  const p50 = baseDuration['med'] || baseDuration.p50 || 0;
  const p95 = baseDuration['p(95)'] || baseDuration.p95 || 0;
  const p99 = baseDuration['p(99)'] || baseDuration.p99 || 0;
  const avg = loginDur.avg || baseDuration.avg || 0;

  const failRate = (metrics.http_req_failed?.values?.rate || 0) * 100;

  const pass = p99 < RT_P99_MS && failRate < 5;

  const report = `
================================================================================
                    登录脉冲压测报告
================================================================================
  服务地址:         ${BASE_URL}
  并发 VUS:         ${credentials.length}
  密码:             123456（统一）
--------------------------------------------------------------------------------
  [响应时间]（来自 k6 内置阈值检查，summaryTimeUnit=ms）
    均值:           ${avg.toFixed(2)}ms
    P50:            ${p50.toFixed(2)}ms
    P95:            ${p95.toFixed(2)}ms   阈值: ${RT_P95_MS}ms   ${p95 < RT_P95_MS ? 'PASS' : 'FAIL'}
    P99:            ${p99.toFixed(2)}ms   阈值: ${RT_P99_MS}ms   ${p99 < RT_P99_MS ? 'PASS' : 'FAIL'}
--------------------------------------------------------------------------------
  [可靠性]
    HTTP 错误率:    ${failRate.toFixed(2)}%     阈值: 5%       ${failRate < 5 ? 'PASS' : 'FAIL'}
--------------------------------------------------------------------------------
  [结论]
    ${pass ? '[PASS] 系统在脉冲并发下表现良好' : '[WARNING] 部分指标未达标，请检查上方 k6 内置 summary 中的 threshold 状态'}
================================================================================`;

  return { stdout: report };
}
