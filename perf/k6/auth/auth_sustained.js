/**
 * 场景：持续登录吞吐
 *
 * 验证目标：在长时间高负载下，测量登录接口的持续 TPS 与 RT 稳定性。
 * 每个 VU 持续执行：登录 → 停留 → 等待 → 重新登录。
 *
 * 运行示例：
 *   k6 run -e BASE_URL=http://localhost:8081 \
 *          -e AUTH_USER_COUNT=200 \
 *          -e VUS=100 \
 *          -e DURATION=60s \
 *          auth/auth_sustained.js
 *
 * 环境变量：
 *   BASE_URL           - 服务地址，默认 http://localhost:8081
 *   AUTH_USER_COUNT    - 凭证总数（应等于 setup 注册数），默认 200
 *   VUS                - 虚拟用户数，默认 50
 *   DURATION           - 压测持续时间，默认 60s
 *   MIN_STAY_MS        - 最小停留时间（ms），默认 2000
 *   MAX_STAY_MS        - 最大停留时间（ms），默认 5000
 *   MIN_WAIT_MS        - 重新登录前等待（ms），默认 1000
 *   MAX_WAIT_MS        - 重新登录前等待（ms），默认 2000
 *   RT_THRESHOLD_P95_MS - P95 响应时间阈值（ms），默认 500
 *   RT_THRESHOLD_P99_MS - P99 响应时间阈值（ms），默认 1000
 */

import { sleep } from 'k6';
import { credentials, loginUser, BASE_URL } from './common_auth.js';

// ====================================================================
// 配置
// ====================================================================

const VUS = parseInt(__ENV.VUS || '50', 10);
const DURATION = __ENV.DURATION || '60s';
const MIN_STAY_MS = parseInt(__ENV.MIN_STAY_MS || '2000', 10);
const MAX_STAY_MS = parseInt(__ENV.MAX_STAY_MS || '5000', 10);
const MIN_WAIT_MS = parseInt(__ENV.MIN_WAIT_MS || '1000', 10);
const MAX_WAIT_MS = parseInt(__ENV.MAX_WAIT_MS || '2000', 10);
const RT_P95_MS = parseInt(__ENV.RT_THRESHOLD_P95_MS || '500', 10);
const RT_P99_MS = parseInt(__ENV.RT_THRESHOLD_P99_MS || '1000', 10);

function randomIntBetween(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export const options = {
  scenarios: {
    login_sustained: {
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
// VU 主循环：登录 → 停留 → 等待 → 重新登录
// ====================================================================

export default function () {
  const vuId = (__VU - 1) % credentials.length;
  const cred = credentials[vuId];

  if (!cred) {
    console.error(`[VU ${__VU}] 凭证不存在 vuId=${vuId}`);
    return;
  }

  // 阶段1：登录
  const loginRes = loginUser(cred.email, cred.password);

  if (!loginRes.ok) {
    console.warn(`[VU ${__VU}] 登录失败 email=${cred.email} status=${loginRes.statusCode}`);
    sleep(randomIntBetween(MIN_WAIT_MS, MAX_WAIT_MS) / 1000);
    return;
  }

  // 更新凭证中的 token（下次循环复用）
  // 注意：SharedArray 在 VU context 下只读，直接跳过写入
  // 每次迭代均使用固定密码重新登录，token 无需缓存复用

  // 阶段2：停留
  const stayMs = randomIntBetween(MIN_STAY_MS, MAX_STAY_MS);
  sleep(stayMs / 1000);

  // 阶段3：等待后重新登录
  const waitMs = randomIntBetween(MIN_WAIT_MS, MAX_WAIT_MS);
  sleep(waitMs / 1000);
}

// ====================================================================
// 压测报告
// ====================================================================

export function handleSummary(data) {
  const metrics = data.metrics || {};

  // 从基础 http_req_duration 读取百分位数（summaryTrendStats 必定对其生效）
  // http_req_duration 包含 login 和 register 请求，login 占绝大多数，数据具有代表性
  // 阈值检查仍然针对 http_req_duration{name:login} 子指标，确保准确性
  const baseDuration = metrics['http_req_duration']?.values || {};
  const loginDur = metrics['http_req_duration{name:login}']?.values || {};
  const totalReqs = metrics.http_reqs?.values?.count || 0;

  const p50 = baseDuration['med'] || baseDuration.p50 || 0;
  const p95 = baseDuration['p(95)'] || baseDuration.p95 || 0;
  const p99 = baseDuration['p(99)'] || baseDuration.p99 || 0;
  const avg = loginDur.avg || baseDuration.avg || 0;
  const failRate = (metrics.http_req_failed?.values?.rate || 0) * 100;

  const durationSec = parseFloat(data.state?.testRunDurationMs || '0') / 1000;
  const tps = durationSec > 0 ? (totalReqs / durationSec).toFixed(2) : 'N/A';

  const pass = p99 < RT_P99_MS && failRate < 5;

  const report = `
================================================================================
                    持续登录吞吐压测报告
================================================================================
  服务地址:         ${BASE_URL}
  目标 VUS:         ${VUS}
  压测时长:         ${DURATION}
  停留时间:         ${MIN_STAY_MS}ms ~ ${MAX_STAY_MS}ms
  等待时间:         ${MIN_WAIT_MS}ms ~ ${MAX_WAIT_MS}ms
--------------------------------------------------------------------------------
  [吞吐量]
    总登录请求:      ${totalReqs}
    TPS:            ${tps}/s
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
    ${pass ? '[PASS] 系统在持续高负载下表现稳定' : '[WARNING] 部分指标未达标，请检查日志'}
================================================================================`;

  return { stdout: report };
}
