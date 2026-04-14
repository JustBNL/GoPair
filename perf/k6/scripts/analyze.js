/**
 * 容量验证结果分析脚本
 *
 * 用法：
 *   # 1. 先用 JSON 模式运行 k6
 *   k6 run --out json=results.json -e CAPACITY=10 -e VUS=100 scenarios/capacity.js
 *
 *   # 2. 再用 node 分析结果
 *   node scripts/analyze.js results.json
 *
 * 输出：JOINED / FAILED / FULL / TIMEOUT / ERROR 各状态的精确数量，
 *       以及超卖检测结果（JOINED > CAPACITY 则失败）。
 */

const fs = require('fs');

function analyzeResults(jsonPath) {
  const raw = fs.readFileSync(jsonPath, 'utf8');
  const lines = raw.trim().split('\n');

  let joinedCount = 0;
  let failedCount = 0;
  let timeoutCount = 0;
  let errorCount = 0;
  let fastRejectCount = 0;
  let unknownCount = 0;

  for (const line of lines) {
    try {
      const entry = JSON.parse(line);

      // k6 会输出各种指标行，我们只关心含有 status= 的 console 输出
      if (entry.type === 'point' && entry.metric === 'data_received') continue;
      if (entry.type !== 'point') continue;

      const metric = entry.metric || '';
      const value = entry.value || '';
      const tags = entry.tags || {};

      // 解析 console.log 输出行
      // 格式：[VU N] status=JOINED roomId=123 ...
      // 格式：[VU N] fast_reject message="..."
      const data = entry.data || {};
      if (typeof data === 'string' && data.includes('status=')) {
        if (data.includes('status=JOINED')) joinedCount++;
        else if (data.includes('status=FAILED')) failedCount++;
        else if (data.includes('status=TIMEOUT')) timeoutCount++;
        else if (data.includes('status=ERROR')) errorCount++;
        else if (data.includes('fast_reject')) fastRejectCount++;
        else unknownCount++;
      }
    } catch (e) {
      // 跳过无法解析的行
    }
  }

  // 也尝试从文件名解析（如果运行时有 --tag）
  console.log('========== 容量验证结果分析 ==========\n');
  console.log(`总 JOINED（成功入房）: ${joinedCount}`);
  console.log(`总 FAILED（入房失败）: ${failedCount}`);
  console.log(`总 TIMEOUT（轮询超时）: ${timeoutCount}`);
  console.log(`总 ERROR（HTTP 错误）: ${errorCount}`);
  console.log(`总 fast_reject（快速拒绝）: ${fastRejectCount}`);
  if (unknownCount > 0) console.log(`未知状态行数: ${unknownCount}`);
  console.log(`\n总处理请求: ${joinedCount + failedCount + timeoutCount + errorCount + fastRejectCount + unknownCount}`);

  // 从命令行参数读取 CAPACITY
  const capacity = parseInt(process.argv[2] || '10', 10);
  console.log(`\n预期容量: ${capacity}`);
  console.log(`超卖检测: ${joinedCount > capacity ? '❌ 超卖！JOINED > CAPACITY' : '✓ 通过'} (JOINED=${joinedCount})`);

  // 失败退出码
  if (joinedCount > capacity) {
    console.log('\n[FAIL] 防超卖验证失败');
    process.exit(1);
  } else {
    console.log('\n[PASS] 防超卖验证通过');
    process.exit(0);
  }
}

const resultFile = process.argv[2];
if (!resultFile) {
  console.error('用法: node analyze.js <k6-json-output.json> [CAPACITY]');
  console.error('示例: node analyze.js results.json 10');
  process.exit(1);
}

analyzeResults(resultFile);
