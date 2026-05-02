/**
 * 容量验证结果分析脚本
 *
 * 用法：
 *   node analyze.js <json_result.json> <CAPACITY> [VUS] [JOINED] [FAST_REJECT] [FAILED] [TIMEOUT] [ERROR]
 *
 * 示例：
 *   k6 run --out json=results.json -e CAPACITY=10 -e VUS=100 scenarios/capacity.js
 *   node analyze.js results.json 10 100
 *
 * CAPACITY  从命令行第2个参数读取（默认为10）
 * VUS       从命令行第3个参数读取（默认为100）
 * 状态计数  从命令行第4-8个参数读取
 *            arg[4] = JOINED（成功入房）
 *            arg[5] = FAST_REJECT（快速拒绝）
 *            arg[6] = FAILED（入房失败）
 *            arg[7] = TIMEOUT（轮询超时）
 *            arg[8] = ERROR（HTTP错误）
 *            状态计数可省略，脚本会从 JSON 文件中尝试解析（可靠性低）
 */

const fs = require('fs');

function parseResults(jsonPath) {
  const raw = fs.readFileSync(jsonPath, 'utf8');
  const lines = raw.trim().split('\n');

  let joinedCount = 0;
  let failedCount = 0;
  let timeoutCount = 0;
  let errorCount = 0;
  let fastRejectCount = 0;
  let httpErrorCount = 0;

  for (const line of lines) {
    try {
      const entry = JSON.parse(line);

      // k6 JSON 输出中 console.log 不写入文件，只解析指标数据
      // 尝试从指标数据中推断错误情况
      if (entry.type === 'Point' || entry.type === 'Metric') {
        const data = entry.data;
        if (!data || !data.tags) continue;

        const tags = data.tags || {};
        const value = data.value;

        // 从 HTTP 状态码判断
        if (tags.status) {
          const status = parseInt(tags.status, 10);
          if (status >= 400) {
            httpErrorCount++;
          }
        }
      }
    } catch (e) {
      // 跳过无法解析的行
    }
  }

  return { joinedCount, failedCount, timeoutCount, errorCount, fastRejectCount, httpErrorCount };
}

function analyze(args) {
  const jsonPath = args[2];
  const capacity = parseInt(args[3] || '10', 10);
  const vus = parseInt(args[4] || '100', 10);

  // 优先使用命令行参数中的状态计数（从终端日志中手动提取或从 k6 --log-output 捕获）
  let joinedCount = args[5] !== undefined ? parseInt(args[5], 10) : 0;
  let fastRejectCount = args[6] !== undefined ? parseInt(args[6], 10) : 0;
  let failedCount = args[7] !== undefined ? parseInt(args[7], 10) : 0;
  let timeoutCount = args[8] !== undefined ? parseInt(args[8], 10) : 0;
  let errorCount = args[9] !== undefined ? parseInt(args[9], 10) : 0;

  // 如果命令行未提供计数，尝试从 JSON 文件解析（可靠性低）
  if (args[5] === undefined) {
    console.log('[info] 未提供状态计数参数，从 JSON 文件解析（精度有限）');
    const parsed = parseResults(jsonPath);
    // JSON 文件不包含 console 输出，仅用 HTTP 错误数作为参考
    errorCount = parsed.httpErrorCount;
    console.log(`[info] HTTP 错误数（仅供参考）: ${errorCount}`);
    console.log('[info] 建议：从终端日志中提取 JOINED/FAST_REJECT 等计数后重新运行');
    console.log('[info]   node analyze.js results.json 10 100 9 10 0 0 1');
    console.log('');
  }

  console.log('========== 容量验证结果分析 ==========\n');
  console.log(`服务地址:       http://localhost:8081`);
  console.log(`并发 VUS:       ${vus}`);
  console.log(`预期容量:       ${capacity}`);
  console.log('');
  console.log(`JOINED（成功入房）:   ${joinedCount}`);
  console.log(`FAST_REJECT（快速拒绝）: ${fastRejectCount}`);
  console.log(`FAILED（入房失败）:   ${failedCount}`);
  console.log(`TIMEOUT（轮询超时）:  ${timeoutCount}`);
  console.log(`ERROR（HTTP 错误）:   ${errorCount}`);
  console.log('');
  console.log(`总处理请求:     ${joinedCount + fastRejectCount + failedCount + timeoutCount + errorCount}`);

  // 超卖检测
  const oversold = joinedCount > capacity;
  const passed = !oversold;
  console.log(`\n超卖检测: ${passed ? '✓ 通过' : '✗ 失败'} (JOINED=${joinedCount}, CAPACITY=${capacity})`);

  if (passed) {
    console.log('\n[PASS] 防超卖验证通过');
    process.exit(0);
  } else {
    console.log(`\n[FAIL] 防超卖验证失败：JOINED(${joinedCount}) > CAPACITY(${capacity})`);
    process.exit(1);
  }
}

const args = process.argv;
if (args.length < 3) {
  console.error('用法: node analyze.js <k6-json-output.json> [CAPACITY] [VUS] [JOINED] [FAST_REJECT] [FAILED] [TIMEOUT] [ERROR]');
  console.error('');
  console.error('示例：');
  console.error('  # 仅传入 JSON 和容量（准确性有限）');
  console.error('  node analyze.js results.json 10');
  console.error('');
  console.error('  # 传入完整参数（推荐，从终端日志提取）');
  console.error('  node analyze.js results.json 10 100 9 10 0 0 1');
  console.error('');
  console.error('参数说明：');
  console.error('  arg[2] JSON 结果文件路径（必填）');
  console.error('  arg[3] 预期容量（默认 10）');
  console.error('  arg[4] VUS 数（默认 100）');
  console.error('  arg[5] JOINED 数量（成功入房）');
  console.error('  arg[6] FAST_REJECT 数量（快速拒绝）');
  console.error('  arg[7] FAILED 数量（入房失败）');
  console.error('  arg[8] TIMEOUT 数量（轮询超时）');
  console.error('  arg[9] ERROR 数量（HTTP 错误）');
  process.exit(1);
}

analyze(args);
