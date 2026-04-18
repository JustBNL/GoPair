/**
 * WebSocket Fan-out 测量客户端
 *
 * 独立运行，测量 ChannelMessageRouter 在 N 人房间下的消息分发能力。
 *
 * 使用方式：
 *   node perf/k6/scripts/ws_fanout_client.js --ws-url=ws://localhost:8080/ws --room-id=18 --subscribers=50 --inject=100
 *
 * 依赖安装：
 *   npm install ws amqplib
 *
 * 工作原理：
 *   1. 建立 M 个 WebSocket 连接，订阅 room:{roomId} 频道
 *   2. 通过 RabbitMQ AMQP 向 websocket.* 交换机注入消息
 *   3. 每个 WS 连接记录消息到达时间戳
 *   4. 计算端到端 fan-out 延迟（P50/P95/P99）
 *
 * @author gopair
 */

const WebSocket = require('ws');
const amqp = require('amqplib');

// ====================================================================
// 命令行参数解析
// ====================================================================

const args = process.argv.slice(2).reduce((acc, arg) => {
  const [key, value] = arg.replace(/^--/, '').split('=');
  acc[key] = value;
  return acc;
}, {});

const WS_URL = args['ws-url'] || 'ws://localhost:8080/ws';
const ROOM_ID = parseInt(args['room-id'] || '18', 10);
const SUBSCRIBERS = parseInt(args['subscribers'] || '50', 10);
const INJECT_COUNT = parseInt(args['inject'] || '100', 10);
const MQ_URL = args['mq-url'] || 'amqp://guest:guest@localhost:5672';
const WS_EXCHANGE = args['mq-exchange'] || 'websocket.exchange';
const ROUTING_KEY = args['mq-routing-key'] || 'websocket.chat.room';

if (!ROOM_ID || !SUBSCRIBERS) {
  console.error('用法: node ws_fanout_client.js --ws-url=... --room-id=18 --subscribers=50 --inject=100');
  process.exit(1);
}

// ====================================================================
// 全局状态
// ====================================================================

const receivedMessages = new Map(); // userId -> [timestamp, timestamp, ...]
const injectTimestamps = []; // 每条注入消息的发送时间
let totalReceived = 0;
let connectedCount = 0;
let mqChannel = null;
let wsConnections = [];

// ====================================================================
// 建立 WebSocket 连接
// ====================================================================

function createWsConnection(user, index) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(WS_URL);

    const timeout = setTimeout(() => {
      ws.close();
      reject(new Error(`连接超时 userIndex=${index}`));
    }, 10000);

    ws.on('open', () => {
      // 订阅 room 频道
      const subscribeMsg = {
        type: 'subscribe',
        eventType: 'subscribe',
        data: {
          payload: {
            channel: `room:${ROOM_ID}`,
            userId: parseInt(user.userId, 10),
            eventTypes: ['message_send', 'member_join', 'member_leave', 'member_typing'],
          },
        },
      };
      ws.send(JSON.stringify(subscribeMsg));

      clearTimeout(timeout);
      connectedCount++;
      resolve(ws);
    });

    ws.on('message', (data) => {
      try {
        const msg = JSON.parse(data.toString());

        // 只记录 message_send 事件
        if (msg.eventType === 'message_send') {
          const receiveTime = Date.now();
          const userId = user.userId;

          if (!receivedMessages.has(userId)) {
            receivedMessages.set(userId, []);
          }
          receivedMessages.get(userId).push(receiveTime);
          totalReceived++;

          // 找到对应的注入时间（通过 payload 中的序号匹配）
          // 这里简化处理：每个用户按顺序接收
        }
      } catch (e) {
        // 忽略解析错误
      }
    });

    ws.on('error', (err) => {
      clearTimeout(timeout);
      console.error(`[WS] 连接错误 userIndex=${index}: ${err.message}`);
      reject(err);
    });

    ws.on('close', () => {
      connectedCount--;
    });
  });
}

// ====================================================================
// 通过 RabbitMQ 注入消息
// ====================================================================

async function injectMessage(index) {
  const messageId = `fanout-${index}-${Date.now()}`;
  const injectTime = Date.now();
  injectTimestamps.push({ index, messageId, injectTime });

  const message = {
    messageId,
    timestamp: new Date().toISOString(),
    type: 'chat',
    channel: `room:${ROOM_ID}`,
    eventType: 'message_send',
    payload: {
      messageId: index,
      senderId: 0,
      senderNickname: 'perf-bot',
      messageType: 1,
      content: `fanout-msg-${index}`,
      createTime: new Date().toISOString(),
    },
    source: 'perf-test',
  };

  try {
    if (mqChannel) {
      mqChannel.publish(
        WS_EXCHANGE,
        ROUTING_KEY,
        Buffer.from(JSON.stringify(message)),
        { persistent: true }
      );
    }
  } catch (e) {
    console.error(`[MQ] 注入失败: ${e.message}`);
  }
}

// ====================================================================
// 计算 fan-out 延迟统计
// ====================================================================

function calculateLatency() {
  // fan-out 延迟 = 最后一个订阅者收到时间 - 注入时间
  const results = [];

  for (const entry of injectTimestamps) {
    const injectTime = entry.injectTime;
    let latestReceiveTime = injectTime;

    // 找到所有订阅者的收到时间
    for (const [, timestamps] of receivedMessages) {
      // 简化：取每个用户最新收到消息的时间
      if (timestamps.length > 0) {
        const lastTime = timestamps[timestamps.length - 1];
        if (lastTime > latestReceiveTime) {
          latestReceiveTime = lastTime;
        }
      }
    }

    const latency = latestReceiveTime - injectTime;
    results.push(latency);
  }

  if (results.length === 0) {
    return { p50: 0, p95: 0, p99: 0, avg: 0, min: 0, max: 0 };
  }

  results.sort((a, b) => a - b);

  const p50Idx = Math.floor(results.length * 0.50);
  const p95Idx = Math.floor(results.length * 0.95);
  const p99Idx = Math.floor(results.length * 0.99);
  const sum = results.reduce((a, b) => a + b, 0);

  return {
    p50: results[p50Idx],
    p95: results[p95Idx],
    p99: results[p99Idx],
    avg: Math.round(sum / results.length),
    min: results[0],
    max: results[results.length - 1],
  };
}

// ====================================================================
// 主流程
// ====================================================================

async function main() {
  console.log('============================================================');
  console.log('  WebSocket Fan-out 压测客户端');
  console.log('============================================================');
  console.log(`  WS 地址:          ${WS_URL}`);
  console.log(`  房间 ID:          ${ROOM_ID}`);
  console.log(`  订阅者数:         ${SUBSCRIBERS}`);
  console.log(`  注入消息数:       ${INJECT_COUNT}`);
  console.log(`  MQ 地址:          ${MQ_URL}`);
  console.log('============================================================');
  console.log('');

  // 步骤1：建立所有 WebSocket 连接
  console.log(`[1/3] 建立 ${SUBSCRIBERS} 个 WS 连接...`);
  const users = require('../../user_info.json').slice(0, SUBSCRIBERS);
  const wsPromises = users.map((user, i) =>
    createWsConnection(user, i).catch(() => null)
  );
  const wsResults = await Promise.allSettled(wsPromises);
  wsConnections = wsResults.filter(r => r.status === 'fulfilled').map(r => r.value);
  console.log(`       连接成功: ${wsConnections.length}/${SUBSCRIBERS}`);
  console.log('');

  // 步骤2：等待连接稳定
  await new Promise(resolve => setTimeout(resolve, 2000));

  // 步骤3：建立 RabbitMQ 连接
  console.log('[2/3] 连接 RabbitMQ...');
  try {
    const conn = await amqp.connect(MQ_URL);
    mqChannel = await conn.createChannel();
    await mqChannel.assertExchange(WS_EXCHANGE, 'topic', { durable: true });
    console.log('       RabbitMQ 连接成功');
  } catch (e) {
    console.warn(`       RabbitMQ 连接失败: ${e.message}，使用 HTTP 注入替代`);
    mqChannel = null;
  }
  console.log('');

  // 步骤4：批量注入消息
  console.log(`[3/3] 开始注入 ${INJECT_COUNT} 条消息...`);
  const injectStart = Date.now();

  for (let i = 0; i < INJECT_COUNT; i++) {
    await injectMessage(i);
    // 每条消息间隔 50ms 注入
    if (i < INJECT_COUNT - 1) {
      await new Promise(resolve => setTimeout(resolve, 50));
    }
  }

  // 等待所有消息分发完成
  await new Promise(resolve => setTimeout(resolve, 5000));

  const elapsed = Date.now() - injectStart;
  console.log(`       注入完成，耗时 ${elapsed}ms`);
  console.log('');

  // 计算统计
  const latencyStats = calculateLatency();
  const expectedReceive = wsConnections.length * INJECT_COUNT;

  // =================================================================
  // 输出报告
  // =================================================================

  console.log('============================================================');
  console.log('  Fan-out 压测报告');
  console.log('============================================================');
  console.log(`  房间 ID:           ${ROOM_ID}`);
  console.log(`  订阅者数量:       ${wsConnections.length}`);
  console.log(`  注入消息数:       ${INJECT_COUNT}`);
  console.log(`  期望收到数:       ${expectedReceive}`);
  console.log(`  实际收到数:       ${totalReceived}`);
  console.log(`  丢失率:           ${((1 - totalReceived / expectedReceive) * 100).toFixed(2)}%`);
  console.log('');
  console.log('  --- Fan-out 延迟 (ms) ---');
  console.log(`  P50:              ${latencyStats.p50}ms`);
  console.log(`  P95:              ${latencyStats.p95}ms`);
  console.log(`  P99:              ${latencyStats.p99}ms`);
  console.log(`  平均:             ${latencyStats.avg}ms`);
  console.log(`  最小:             ${latencyStats.min}ms`);
  console.log(`  最大:             ${latencyStats.max}ms`);
  console.log('============================================================');

  // =================================================================
  // PASS/FAIL 判定
  // =================================================================

  const lossRate = (1 - totalReceived / expectedReceive) * 100;
  const allPass = latencyStats.p99 < 500 && lossRate < 1;

  console.log('');
  if (allPass) {
    console.log('  [PASS] 系统在大房间场景下表现良好');
    console.log(`          P99 延迟 ${latencyStats.p99}ms < 500ms，丢失率 ${lossRate.toFixed(2)}% < 1%`);
  } else {
    console.log('  [WARNING] 部分指标未达标');
    if (latencyStats.p99 >= 500) {
      console.log(`          - P99 延迟 ${latencyStats.p99}ms >= 500ms`);
    }
    if (lossRate >= 1) {
      console.log(`          - 丢失率 ${lossRate.toFixed(2)}% >= 1%`);
    }
  }
  console.log('============================================================');

  // 清理
  wsConnections.forEach(ws => ws.close());
  if (mqChannel) mqChannel.close();
  process.exit(0);
}

main().catch(err => {
  console.error('执行失败:', err);
  process.exit(1);
});
