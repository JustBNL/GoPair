<template>
  <!-- 悬浮触发按钮 -->
  <div class="ai-fab" @click="open = true" title="AI 助手">
    <span class="ai-fab-icon">✦</span>
    <span class="ai-fab-label">AI</span>
  </div>

  <!-- 聊天抽屉 -->
  <a-drawer
    v-model:open="open"
    :placement="isMobile ? 'bottom' : 'right'"
    :width="isMobile ? '100%' : 420"
    :height="isMobile ? '85vh' : undefined"
    :closable="false"
    class="ai-drawer"
    :body-style="{ padding: 0, display: 'flex', flexDirection: 'column', height: '100%' }"
    :header-style="{ display: 'none' }"
  >
    <!-- ARIA: 语义化抽屉角色，提供屏幕阅读器支持 -->
    <div class="ai-panel" ref="panelEl" role="dialog" aria-modal="true" aria-label="聊天室 AI 助手聊天面板">
      <!-- 头部 -->
      <div class="ai-header">
        <div class="ai-header-left">
          <div class="ai-avatar">✦</div>
          <div>
            <div class="ai-title">AI 助手</div>
            <div class="ai-subtitle">GLM-4-Flash · 聊天室智能助手</div>
          </div>
        </div>
        <div class="ai-header-actions">
          <button class="icon-btn" aria-label="清空对话" title="清空对话" @click="clearHistory">
            <DeleteOutlined />
          </button>
          <button class="icon-btn" aria-label="关闭" title="关闭" @click="open = false">
            <CloseOutlined />
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="ai-messages" ref="messagesEl">
        <!-- 欢迎语 -->
        <div v-if="messages.length === 0" class="ai-welcome">
          <div class="welcome-icon">✦</div>
          <h3>你好！我是 聊天室 AI 助手</h3>
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
import { ref, nextTick, watch, onMounted, onUnmounted } from 'vue'
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
const panelEl = ref<HTMLElement | null>(null)

/** 移动端判断（屏幕宽度 < 768px） */
const isMobile = ref(false)
let previousActiveElement: HTMLElement | null = null

function updateMobileState() {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  updateMobileState()
  window.addEventListener('resize', updateMobileState)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateMobileState)
})

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
  if (val) {
    previousActiveElement = document.activeElement as HTMLElement
    scrollToBottom()
    nextTick(() => {
      inputEl.value?.focus()
    })
  } else {
    nextTick(() => {
      previousActiveElement?.focus()
    })
  }
})
</script>


<style scoped>
/* 移动端 bottom drawer 底部圆角（ant-design-vue 原生不支持 border-radius，需穿透覆盖） */
@media (max-width: 768px) {
  :global(.ant-drawer.ant-drawer-open.bottom .ant-drawer-content) {
    border-radius: 16px 16px 0 0;
  }
  :global(.ant-drawer.ant-drawer-open.bottom .ant-drawer-content-wrapper) {
    border-radius: 16px 16px 0 0;
  }
}
.ai-fab {
  position: fixed;
  bottom: 40px;
  right: 40px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--ai-surface);
  box-shadow: 0 4px 24px var(--ai-shadow), 0 0 0 1px var(--ai-border);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  z-index: 999;
  transition: box-shadow 0.2s ease;
  user-select: none;
}
.ai-fab:hover {
  box-shadow: 0 8px 32px var(--ai-shadow-strong), 0 0 0 1px var(--ai-border-strong);
}
.ai-fab:active { transform: scale(0.96); }
.ai-fab-icon { font-size: 20px; color: var(--ai-accent); line-height: 1; }
.ai-fab-label { font-size: 10px; font-weight: 700; color: var(--ai-accent-60); letter-spacing: 0.05em; line-height: 1; }
.ai-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--ai-bg);
  font-family: PingFang SC, Microsoft YaHei, sans-serif;
}

@media (max-width: 768px) {
  .ai-panel {
    height: 85vh;
  }
}
.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  background: linear-gradient(135deg, var(--ai-surface) 0%, var(--ai-bg) 100%);
  border-bottom: 1px solid var(--ai-border);
  flex-shrink: 0;
}
.ai-header-left { display: flex; align-items: center; gap: 12px; }
.ai-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--ai-surface-raised);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--ai-text-primary);
  flex-shrink: 0;
  box-shadow: 0 0 12px var(--ai-shadow);
}
.ai-title { font-size: 16px; font-weight: 700; color: var(--ai-text-primary); line-height: 1.2; }
.ai-subtitle { font-size: 11px; color: var(--ai-accent-60); margin-top: 2px; line-height: 1; }
.ai-header-actions { display: flex; gap: 8px; }
.icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--ai-border);
  background: var(--ai-accent-10);
  color: var(--ai-accent-60);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: background 0.15s, border-color 0.15s;
}
.icon-btn:hover {
  background: var(--ai-accent-20);
  color: var(--ai-accent);
  border-color: var(--ai-border-strong);
}
.ai-messages { flex: 1; overflow-y: auto; padding: 20px 16px; display: flex; flex-direction: column; gap: 16px; scroll-behavior: smooth; }
.ai-messages::-webkit-scrollbar { width: 4px; }
.ai-messages::-webkit-scrollbar-track { background: transparent; }
.ai-messages::-webkit-scrollbar-thumb { background: var(--ai-accent-20); border-radius: 2px; }
.ai-welcome { text-align: center; padding: 40px 16px 16px; color: var(--ai-text-secondary); }
.welcome-icon { font-size: 52px; margin-bottom: 16px; display: block; animation: pulse-glow 2.5s ease-in-out infinite; }
@keyframes pulse-glow { 0%, 100% { filter: drop-shadow(0 0 12px var(--ai-shadow)); } 50% { filter: drop-shadow(0 0 24px var(--ai-shadow-strong)); } }
.ai-welcome h3 { font-size: 18px; color: var(--ai-text-primary); margin: 0 0 10px; font-weight: 600; }
.ai-welcome p { font-size: 13px; line-height: 1.7; margin: 0 0 24px; color: var(--ai-text-muted); }
.quick-questions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.quick-btn {
  padding: 6px 14px;
  border-radius: 20px;
  border: 1px solid var(--ai-border);
  background: var(--ai-accent-10);
  color: var(--ai-accent);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  line-height: 1.5;
}
.quick-btn:hover {
  background: var(--ai-accent-15);
  border-color: var(--ai-border-strong);
}
.msg-row { display: flex; align-items: flex-end; gap: 8px; animation: bubbleIn 0.2s ease; }
.msg-row--user { flex-direction: row-reverse; }
@keyframes bubbleIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.msg-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--ai-surface-raised);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--ai-text-primary);
  flex-shrink: 0;
  margin-bottom: 2px;
}
.msg-bubble { max-width: 78%; padding: 10px 14px; border-radius: 16px; font-size: 14px; line-height: 1.65; word-break: break-word; }
.bubble--ai {
  background: var(--ai-surface-raised);
  color: var(--ai-text-primary);
  border: 1px solid var(--ai-border);
  border-bottom-left-radius: 4px;
}
.bubble--user {
  background: var(--ai-accent);
  color: var(--ai-text-primary);
  border-bottom-right-radius: 4px;
}
.bubble--error {
  background: var(--ai-danger-bg);
  border: 1px solid var(--ai-danger-border);
  color: var(--ai-danger);
}
.typing-dots { display: inline-flex; gap: 4px; align-items: center; padding: 2px 0; }
.typing-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ai-accent);
  animation: dot-bounce 1.2s ease-in-out infinite;
}
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce { 0%, 80%, 100% { transform: scale(0.7); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }
.ai-input-area { padding: 16px; background: var(--ai-bg); border-top: 1px solid var(--ai-border); flex-shrink: 0; }
.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: var(--ai-surface);
  border: 1px solid var(--ai-border);
  border-radius: 14px;
  padding: 10px 12px;
  transition: border-color 0.2s;
}
.input-wrapper:focus-within { border-color: var(--ai-accent-60); box-shadow: 0 0 0 3px var(--ai-accent-10); }
.ai-textarea {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  color: var(--ai-text-primary);
  font-size: 14px;
  line-height: 1.6;
  min-height: 22px;
  max-height: 120px;
  overflow-y: auto;
  font-family: inherit;
}
.ai-textarea::placeholder { color: var(--ai-text-subtle); }
.ai-textarea:disabled { opacity: 0.5; cursor: not-allowed; }
.send-btn {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: none;
  background: var(--ai-surface-raised);
  color: var(--ai-text-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
  transition: box-shadow 0.15s;
}
.send-btn:hover:not(:disabled) { box-shadow: 0 4px 12px var(--ai-shadow); }
.send-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.send-btn--stop { background: var(--ai-danger); }
.send-btn--stop:hover:not(:disabled) { box-shadow: 0 4px 12px rgba(var(--color-error-rgb), 0.4); }

@media (prefers-reduced-motion: reduce) {
  .ai-fab { transition: none; }
  .welcome-icon { animation: none; }
  .quick-btn { transition: none; }
  .quick-btn:hover { transform: none; }
  .msg-row { animation: none; }
  .typing-dots span { animation: none; }
  .send-btn { transition: none; }
  .send-btn:hover:not(:disabled) { transform: none; }
}
</style>
