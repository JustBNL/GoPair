<template>
  <!-- 悬浮触发按钮 -->
  <div class="ai-fab" @click="open = true" title="AI 助手">
    <span class="ai-fab-icon">✦</span>
    <span class="ai-fab-label">AI</span>
  </div>

  <!-- 聊天抽屉 -->
  <a-drawer
    v-model:open="open"
    placement="right"
    :width="420"
    :closable="false"
    class="ai-drawer"
    :body-style="{ padding: 0, display: 'flex', flexDirection: 'column', height: '100%' }"
    :header-style="{ display: 'none' }"
  >
    <div class="ai-panel">
      <!-- 头部 -->
      <div class="ai-header">
        <div class="ai-header-left">
          <div class="ai-avatar">✦</div>
          <div>
            <div class="ai-title">AI 助手</div>
            <div class="ai-subtitle">GLM-4-Flash · GoPair 智能助手</div>
          </div>
        </div>
        <div class="ai-header-actions">
          <button class="icon-btn" title="清空对话" @click="clearHistory">
            <DeleteOutlined />
          </button>
          <button class="icon-btn" title="关闭" @click="open = false">
            <CloseOutlined />
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="ai-messages" ref="messagesEl">
        <!-- 欢迎语 -->
        <div v-if="messages.length === 0" class="ai-welcome">
          <div class="welcome-icon">✦</div>
          <h3>你好！我是 GoPair AI 助手</h3>
          <p>我可以帮你解答关于协作、房间管理、功能使用等问题，也可以陪你聊聊天。</p>
          <div class="quick-questions">
            <button
              v-for="q in quickQuestions"
              :key="q"
              class="quick-btn"
              @click="sendQuickQuestion(q)"
            >{{ q }}</button>
          </div>
        </div>

        <!-- 消息气泡 -->
        <div
          v-for="msg in messages"
          :key="msg.id"
          :class="['msg-row', msg.role === 'user' ? 'msg-row--user' : 'msg-row--ai']"
        >
          <div v-if="msg.role === 'assistant'" class="msg-avatar">✦</div>
          <div :class="['msg-bubble', msg.role === 'user' ? 'bubble--user' : 'bubble--ai', { 'bubble--error': msg.error }]">
            <!-- AI 加载中动画 -->
            <span v-if="msg.loading && !msg.content" class="typing-dots">
              <span></span><span></span><span></span>
            </span>
            <!-- 消息内容 -->
            <span v-else class="bubble-text" v-html="renderContent(msg.content)"></span>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="ai-input-area">
        <div class="input-wrapper">
          <textarea
            ref="inputEl"
            v-model="inputText"
            class="ai-textarea"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行..."
            :disabled="isStreaming"
            rows="1"
            @keydown.enter.exact.prevent="handleEnterKey"
            @compositionstart="isComposing = true"
            @compositionend="isComposing = false"
            @input="autoResize"
          />
          <button
            class="send-btn"
            :class="{ 'send-btn--stop': isStreaming }"
            :disabled="!inputText.trim() && !isStreaming"
            @click="isStreaming ? stopStreaming() : handleSend()"
          >
            <span v-if="isStreaming">⏹</span>
            <SendOutlined v-else />
          </button>
        </div>
      </div>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { DeleteOutlined, CloseOutlined, SendOutlined } from '@ant-design/icons-vue'
import { streamChat } from '@/api/ai'
import type { AiMessage, GlmChatMessage } from '@/types/ai'

// ==================== 状态 ====================

const open = ref(false)
const inputText = ref('')
const isStreaming = ref(false)
const isComposing = ref(false)
const messages = ref<AiMessage[]>([])
const messagesEl = ref<HTMLElement | null>(null)
const inputEl = ref<HTMLTextAreaElement | null>(null)

let abortController: AbortController | null = null

const STORAGE_KEY = 'gopair_ai_chat_history'

const quickQuestions = [
  '如何创建一个新房间？',
  '忘记密码怎么办？',
  '如何邀请朋友加入房间？',
  '语音通话怎么使用？',
  '如何上传和分享文件？',
  '房主有哪些管理权限？',
  '消息记录会保存吗？',
  '什么是动态令牌模式？'
]

// ==================== 初始化：恢复历史 ====================

try {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved) {
    const parsed = JSON.parse(saved) as AiMessage[]
    messages.value = parsed.filter(m => !m.loading)
  }
} catch {
  // 忽略
}

// ==================== 方法 ====================

function renderContent(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
}

async function scrollToBottom() {
  await nextTick()
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}

function autoResize() {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

function saveHistory() {
  try {
    const toSave = messages.value.filter(m => !m.loading).slice(-100)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(toSave))
  } catch {
    // 忽略
  }
}

function clearHistory() {
  if (isStreaming.value) stopStreaming()
  messages.value = []
  localStorage.removeItem(STORAGE_KEY)
}

function stopStreaming() {
  abortController?.abort()
  abortController = null
  isStreaming.value = false
  const last = messages.value[messages.value.length - 1]
  if (last?.loading) {
    last.loading = false
    if (!last.content) last.content = '（已停止生成）'
  }
  saveHistory()
}

function sendQuickQuestion(q: string) {
  inputText.value = q
  handleSend()
}

function handleEnterKey() {
  if (isComposing.value) return
  handleSend()
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  inputText.value = ''
  await nextTick()
  if (inputEl.value) inputEl.value.style.height = 'auto'

  const userMsg: AiMessage = {
    id: `${Date.now()}-u`,
    role: 'user',
    content: text,
    timestamp: Date.now()
  }
  messages.value.push(userMsg)
  await scrollToBottom()

  const aiMsg: AiMessage = {
    id: `${Date.now()}-a`,
    role: 'assistant',
    content: '',
    timestamp: Date.now(),
    loading: true
  }
  messages.value.push(aiMsg)
  await scrollToBottom()

  isStreaming.value = true

  const history: GlmChatMessage[] = messages.value
    .filter(m => !m.loading && !m.error)
    .map(m => ({ role: m.role, content: m.content }))

  abortController = streamChat(
    history,
    (chunk) => {
      aiMsg.content += chunk
      scrollToBottom()
    },
    () => {
      aiMsg.loading = false
      isStreaming.value = false
      abortController = null
      saveHistory()
      scrollToBottom()
    },
    (err) => {
      aiMsg.loading = false
      aiMsg.error = true
      aiMsg.content = `请求失败：${err.message}`
      isStreaming.value = false
      abortController = null
      saveHistory()
    }
  )
}

watch(open, (val) => {
  if (val) scrollToBottom()
})
</script>


<style scoped>
.ai-fab {
  position: fixed;
  bottom: 40px;
  right: 40px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  box-shadow: 0 4px 24px rgba(99, 102, 241, 0.5), 0 0 0 1px rgba(99, 102, 241, 0.3);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  z-index: 999;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  user-select: none;
}
.ai-fab:hover { transform: scale(1.1) translateY(-2px); box-shadow: 0 8px 32px rgba(99,102,241,0.7); }
.ai-fab:active { transform: scale(0.96); }
.ai-fab-icon { font-size: 20px; color: #a78bfa; line-height: 1; }
.ai-fab-label { font-size: 10px; font-weight: 700; color: rgba(167,139,250,0.8); letter-spacing: 0.05em; line-height: 1; }
.ai-panel { display: flex; flex-direction: column; height: 100vh; background: #0d0d1a; font-family: PingFang SC, Microsoft YaHei, sans-serif; }
.ai-header { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px; background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); border-bottom: 1px solid rgba(99,102,241,0.2); flex-shrink: 0; }
.ai-header-left { display: flex; align-items: center; gap: 12px; }
.ai-avatar { width: 40px; height: 40px; border-radius: 50%; background: linear-gradient(135deg, #6366f1, #8b5cf6); display: flex; align-items: center; justify-content: center; font-size: 18px; color: white; flex-shrink: 0; box-shadow: 0 0 12px rgba(99,102,241,0.5); }
.ai-title { font-size: 16px; font-weight: 700; color: #f1f5f9; line-height: 1.2; }
.ai-subtitle { font-size: 11px; color: rgba(167,139,250,0.7); margin-top: 2px; line-height: 1; }
.ai-header-actions { display: flex; gap: 8px; }
.icon-btn { width: 32px; height: 32px; border-radius: 8px; border: 1px solid rgba(99,102,241,0.2); background: rgba(99,102,241,0.1); color: rgba(167,139,250,0.8); cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 14px; transition: all 0.15s; }
.icon-btn:hover { background: rgba(99,102,241,0.25); color: #a78bfa; border-color: rgba(99,102,241,0.5); }
.ai-messages { flex: 1; overflow-y: auto; padding: 20px 16px; display: flex; flex-direction: column; gap: 16px; scroll-behavior: smooth; }
.ai-messages::-webkit-scrollbar { width: 4px; }
.ai-messages::-webkit-scrollbar-track { background: transparent; }
.ai-messages::-webkit-scrollbar-thumb { background: rgba(99,102,241,0.3); border-radius: 2px; }
.ai-welcome { text-align: center; padding: 40px 16px 16px; color: #94a3b8; }
.welcome-icon { font-size: 52px; margin-bottom: 16px; display: block; animation: pulse-glow 2.5s ease-in-out infinite; }
@keyframes pulse-glow { 0%, 100% { filter: drop-shadow(0 0 12px rgba(99,102,241,0.5)); } 50% { filter: drop-shadow(0 0 24px rgba(139,92,246,0.9)); } }
.ai-welcome h3 { font-size: 18px; color: #e2e8f0; margin: 0 0 10px; font-weight: 600; }
.ai-welcome p { font-size: 13px; line-height: 1.7; margin: 0 0 24px; color: #64748b; }
.quick-questions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.quick-btn { padding: 6px 14px; border-radius: 20px; border: 1px solid rgba(99,102,241,0.3); background: rgba(99,102,241,0.08); color: #a78bfa; font-size: 12px; cursor: pointer; transition: all 0.15s; line-height: 1.5; }
.quick-btn:hover { background: rgba(99,102,241,0.2); border-color: rgba(99,102,241,0.6); color: #c4b5fd; transform: translateY(-1px); }
.msg-row { display: flex; align-items: flex-end; gap: 8px; animation: bubbleIn 0.2s ease; }
.msg-row--user { flex-direction: row-reverse; }
@keyframes bubbleIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.msg-avatar { width: 28px; height: 28px; border-radius: 50%; background: linear-gradient(135deg, #6366f1, #8b5cf6); display: flex; align-items: center; justify-content: center; font-size: 12px; color: white; flex-shrink: 0; margin-bottom: 2px; }
.msg-bubble { max-width: 78%; padding: 10px 14px; border-radius: 16px; font-size: 14px; line-height: 1.65; word-break: break-word; }
.bubble--ai { background: #1e1e35; color: #e2e8f0; border: 1px solid rgba(99,102,241,0.15); border-bottom-left-radius: 4px; }
.bubble--user { background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #ffffff; border-bottom-right-radius: 4px; }
.bubble--error { background: rgba(239,68,68,0.12); border: 1px solid rgba(239,68,68,0.3); color: #fca5a5; }
.typing-dots { display: inline-flex; gap: 4px; align-items: center; padding: 2px 0; }
.typing-dots span { width: 6px; height: 6px; border-radius: 50%; background: #6366f1; animation: dot-bounce 1.2s ease-in-out infinite; }
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce { 0%, 80%, 100% { transform: scale(0.7); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }
.ai-input-area { padding: 16px; background: #0d0d1a; border-top: 1px solid rgba(99,102,241,0.15); flex-shrink: 0; }
.input-wrapper { display: flex; align-items: flex-end; gap: 10px; background: #1a1a2e; border: 1px solid rgba(99,102,241,0.2); border-radius: 14px; padding: 10px 12px; transition: border-color 0.2s; }
.input-wrapper:focus-within { border-color: rgba(99,102,241,0.6); box-shadow: 0 0 0 3px rgba(99,102,241,0.1); }
.ai-textarea { flex: 1; background: transparent; border: none; outline: none; resize: none; color: #e2e8f0; font-size: 14px; line-height: 1.6; min-height: 22px; max-height: 120px; overflow-y: auto; font-family: inherit; }
.ai-textarea::placeholder { color: #475569; }
.ai-textarea:disabled { opacity: 0.5; cursor: not-allowed; }
.send-btn { width: 34px; height: 34px; border-radius: 10px; border: none; background: linear-gradient(135deg, #6366f1, #8b5cf6); color: white; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 14px; flex-shrink: 0; transition: all 0.15s; }
.send-btn:hover:not(:disabled) { transform: scale(1.08); box-shadow: 0 4px 12px rgba(99,102,241,0.5); }
.send-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.send-btn--stop { background: linear-gradient(135deg, #ef4444, #dc2626); }
.send-btn--stop:hover:not(:disabled) { box-shadow: 0 4px 12px rgba(239,68,68,0.5); }
</style> 
