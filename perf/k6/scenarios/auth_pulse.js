/**
 * 场景：登录脉冲摸上限
 *
 * 验证目标：在 M 个并发 VU 同时发起登录时，测量系统能承受的最大并发登录数。
 * 设计为一次脉冲：所有 VUS 在极短时间内同时发起登录请求，等待所有结果后统计。
 *
 * 凭证来源：从 user_info.txt 读取 email + password
 *
 * 运行示例：
 *   k6 run -e BASE_URL=http://localhost:8081 scenarios/auth_pulse.js
 *
 * 环境变量：
 *   BASE_URL             - 服务地址，默认 http://localhost:8081
 *   RT_THRESHOLD_P95_MS - P95 响应时间阈值（ms），默认 500
 *   RT_THRESHOLD_P99_MS - P99 响应时间阈值（ms），默认 1000
 */

import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { BASE_URL } from '../common.js';

export const BASE_URL_AUTH = __ENV.BASE_URL || BASE_URL;

const RT_P95_MS = parseInt(__ENV.RT_THRESHOLD_P95_MS || '500', 10);
const RT_P99_MS = parseInt(__ENV.RT_THRESHOLD_P99_MS || '1000', 10);

const authUserData = new SharedArray('auth_user_info', () => {
  const raw = open('../../user_info.txt');
  const lines = raw.split('\n').filter(l => l.trim());
  return lines.map(line => {
    const parts = line.split(',');
    // 格式：token,userId,nickname,email,password（5字段）
    return {
      email: parts[3]?.trim() || '',
      password: parts[4]?.trim() || '',
    };
  });
});

export const options = {
  scenarios: {
    login_pulse: {
      executor: 'per-vu-iterations',
      vus: authUserData.length,
      iterations: 1,
      maxDuration: '120s',
    },
  },
  summaryTimeUnit: 'ms',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'p(99.99)'],
  thresholds: {
    ['http_req_duration{name:login}']: [
      `p(95)<${RT_P95_MS}`,
      `p(99)<${RT_P99_MS}`,
    ],
    http_req_failed: ['rate<0.05'],
  },
};

function loginRequest(email, password) {
  const body = JSON.stringify({ email, password });
  const startMs = Date.now();
  const res = http.post(`${BASE_URL_AUTH}/user/login`, body, {
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

export default function () {
  const vuId = (__VU - 1) % authUserData.length;
  const cred = authUserData[vuId];

  if (!cred || !cred.email) {
    console.error(`[VU ${__VU}] 凭证不存在 vuId=${vuId}`);
    return;
  }

  const loginRes = loginRequest(cred.email, cred.password);

  if (loginRes.ok) {
    console.log(`[VU ${__VU}] ok email=${cred.email} rt=${loginRes.durationMs}ms`);
  } else {
    console.error(`[VU ${__VU}] fail email=${cred.email} status=${loginRes.statusCode}`);
  }
}

export function handleSummary(data) {
  const metrics = data.metrics || {};

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
  服务地址:         ${BASE_URL_AUTH}
  并发 VUS:         ${authUserData.length}
  凭证来源:         user_info.txt
--------------------------------------------------------------------------------
  [响应时间]
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
