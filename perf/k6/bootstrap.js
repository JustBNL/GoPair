/**
 * 引导脚本：批量创建测试用户并写入 user_info.txt
 *
 * 功能：
 * - 读取 user_info.txt 已有行，提取已存在的 email 集合（幂等跳过）
 * - 循环注册 + 登录，获取 token/userId/nickname
 * - 追加写入 user_info.txt，每行：token,userId,nickname,email,password
 *
 * 运行示例：
 *   k6 run bootstrap.js
 *   k6 run bootstrap.js -e USER_COUNT=20 -e BASE_URL=http://localhost:8081
 *
 * 环境变量：
 *   USER_COUNT       - 创建用户数量，默认 10
 *   BASE_URL        - 服务地址，默认 http://localhost:8081
 *   PASSWORD        - 测试用户密码，默认 123456
 *   EMAIL_DOMAIN    - 邮箱域名，默认 example.com
 */

import http from 'k6/http';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const USER_COUNT = parseInt(__ENV.USER_COUNT || '10', 10);
const PASSWORD = __ENV.PASSWORD || '123456';
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || 'example.com';
const OUTPUT_FILE = '../user_info.txt';

export const options = {
  scenarios: {
    bootstrap: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '300s',
    },
  },
};

function buildEmail(i) {
  const num = String(i).padStart(4, '0');
  return `perfauth${num}@${EMAIL_DOMAIN}`;
}

function buildNickname(i) {
  const num = String(i).padStart(4, '0');
  return `perfauth${num}`;
}

function register(email, password, nickname) {
  const body = JSON.stringify({ email, password, nickname });
  const res = http.post(`${BASE_URL}/user/register`, body, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'register' },
  });
  // 200 + code=200 表示注册成功
  if (res.status === 200) {
    try {
      const json = JSON.parse(res.body);
      const ok = json?.code === 200;
      // code != 200 表示用户已存在或其他业务错误
      return { ok, statusCode: res.status, alreadyExists: !ok };
    } catch {
      return { ok: false, statusCode: res.status, alreadyExists: false };
    }
  }
  // 409 Conflict 表示用户已存在
  return { ok: false, statusCode: res.status, alreadyExists: res.status === 409 };
}

function login(email, password) {
  const body = JSON.stringify({ email, password });
  const res = http.post(`${BASE_URL}/user/login`, body, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'login' },
  });
  if (res.status !== 200) {
    return { ok: false, token: null, userId: null, nickname: null, statusCode: res.status };
  }
  try {
    const json = JSON.parse(res.body);
    const data = json?.data;
    return {
      ok: json?.code === 200,
      token: data?.token || null,
      userId: data?.userId || null,
      nickname: data?.nickname || null,
      statusCode: res.status,
    };
  } catch {
    return { ok: false, token: null, userId: null, nickname: null, statusCode: res.status };
  }
}

function parseUserInfoLine(line) {
  const parts = line.split(',');
  if (parts.length >= 5) {
    return { token: parts[0], userId: parts[1], nickname: parts[2], email: parts[3], password: parts[4] };
  }
  if (parts.length >= 3) {
    // 旧格式 3 字段：token,userId,nickname
    return { token: parts[0], userId: parts[1], nickname: parts[2] };
  }
  return null;
}

export default function () {
  console.log(`[bootstrap] 开始引导 ${USER_COUNT} 个测试用户...`);
  console.log(`[bootstrap] 服务地址: ${BASE_URL}`);

  // 读取已有用户，构建 email -> line 映射
  const existingMap = {};
  try {
    const raw = open(OUTPUT_FILE, 'r');
    raw.split('\n').filter(l => l.trim()).forEach(line => {
      const parsed = parseUserInfoLine(line);
      if (parsed?.email) {
        existingMap[parsed.email] = line.trim();
      }
    });
    console.log(`[bootstrap] 已有用户 ${Object.keys(existingMap).length} 个`);
  } catch (_) {
    console.log(`[bootstrap] user_info.txt 不存在，将创建新文件`);
  }

  let skipCount = 0;
  let successCount = 0;
  let failCount = 0;

  for (let i = 1; i <= USER_COUNT; i++) {
    const email = buildEmail(i);
    const nickname = buildNickname(i);

    if (existingMap[email]) {
      console.log(`[bootstrap] 跳过（已存在）: ${email}`);
      skipCount++;
      continue;
    }

    // 1. 注册（幂等：已存在则跳过）
    const regRes = register(email, PASSWORD, nickname);
    if (regRes.alreadyExists) {
      console.log(`[bootstrap] 注册跳过（已存在）: ${email}`);
      skipCount++;
    } else if (!regRes.ok) {
      console.warn(`[bootstrap] 注册失败: ${email} status=${regRes.statusCode}`);
      failCount++;
      continue;
    }

    // 2. 登录获取完整凭证
    const loginRes = login(email, PASSWORD);
    if (!loginRes.ok) {
      console.error(`[bootstrap] 登录失败: ${email} status=${loginRes.statusCode}`);
      failCount++;
      continue;
    }

    const newLine = `${loginRes.token},${loginRes.userId},${loginRes.nickname},${email},${PASSWORD}`;
    existingMap[email] = newLine;
    successCount++;

    console.log(`[bootstrap] 创建成功: ${email} userId=${loginRes.userId}`);

    if (i % 10 === 0) {
      console.log(`[bootstrap] 进度 ${i}/${USER_COUNT}，成功 ${successCount}，跳过 ${skipCount}，失败 ${failCount}`);
    }
  }

  console.log(`\n[bootstrap] 完成：成功 ${successCount}，跳过 ${skipCount}，失败 ${failCount}`);

  // 输出完整文件内容，供重定向到 user_info.txt
  const allLines = Object.values(existingMap);
  console.log('\n[bootstrap] === USER_INFO_START ===');
  for (const line of allLines) {
    console.log(line);
  }
  console.log('[bootstrap] === USER_INFO_END ===');
  console.log('[bootstrap] 提示：请使用 run_bootstrap.ps1 脚本执行，脚本会自动将新 token 写入 user_info.txt');
}
