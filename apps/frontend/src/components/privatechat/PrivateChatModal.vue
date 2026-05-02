<template>
  <a-modal
    :open="visible"
    :title="null"
    :footer="null"
    :width="720"
    :bodyStyle="{ padding: 0, height: '520px', display: 'flex' }"
    :maskClosable="true"
    @cancel="handleClose"
    class="private-chat-modal"
  >
    <template #closeIcon>
      <CloseOutlined />
    </template>

    <div class="chat-layout">
      <!-- 左侧：好友信息 -->
      <div class="chat-sidebar">
        <div class="sidebar-header">
          <a-avatar :size="56" :src="friendAvatar">
            <template v-if="!friendAvatar">
              {{ nicknameInitial }}
            </template>
          </a-avatar>
          <div class="sidebar-user-info">
            <h4 class="sidebar-nickname">{{ friendNickname || '加载中...' }}</h4>
          </div>
        </div>
        <a-divider style="margin: 12px 0" />
        <div class="sidebar-actions">
          <a-popconfirm
            title="确定删除该好友？"
            ok-text="确定"
            cancel-text="取消"
            @confirm="handleDeleteFriend"
          >
            <a-button type="text" danger size="small">
              删除好友
            </a-button>
          </a-popconfirm>
        </div>
      </div>

      <!-- 右侧：聊天区域 -->
      <div class="chat-main">
        <!-- 消息列表 -->
        <div class="message-list" ref="messageListRef">
          <div v-if="chatStore.messagesLoading" class="loading-messages">
            <a-spin />
          </div>
          <div v-else-if="chatStore.currentMessages.length === 0" class="empty-messages">
            <p>暂无聊天记录，开始对话吧</p>
          </div>
          <template v-else>
            <div
              v-for="msg in chatStore.currentMessages"
              :key="msg.messageId"
              class="message-item"
              :class="{ own: msg.isOwn }"
            >
              <!-- 发送者头像（非自己的消息显示） -->
              <a-avatar v-if="!msg.isOwn" :size="32" :src="msg.senderAvatar" class="msg-avatar">
                <template v-if="!msg.senderAvatar">
                  {{ (msg.senderNickname || 'U').charAt(0).toUpperCase() }}
                </template>
              </a-avatar>

              <div class="message-content-wrapper">
                <div class="message-bubble">
                  <!-- 文本消息 -->
                  <span v-if="msg.messageType === PrivateMessageType.TEXT">{{ msg.content }}</span>

                  <!-- 图片消息 -->
                  <div v-else-if="msg.messageType === PrivateMessageType.IMAGE" class="image-message">
                    <img
                      :src="msg.fileUrl"
                      :alt="msg.fileName"
                      class="image-preview"
                      @click="previewImage(msg.fileUrl!)"
                    />
                  </div>

                  <!-- 文件消息 -->
                  <div v-else-if="msg.messageType === PrivateMessageType.FILE" class="file-message">
                    <FileOutlined class="file-icon" />
                    <div class="file-info">
                      <span class="file-name">{{ msg.fileName }}</span>
                      <span class="file-size">{{ formatFileSize(msg.fileSize) }}</span>
                    </div>
                    <a :href="msg.fileUrl" target="_blank" class="file-download">
                      <DownloadOutlined />
                    </a>
                  </div>

                  <!-- 撤回消息 -->
                  <div v-if="msg.isRecalled" class="recalled-tip">
                    消息已被撤回
                  </div>
                </div>
                <div class="message-time">
                  {{ formatTime(msg.createTime) }}
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <!-- 文件上传按钮 -->
          <div class="input-tools">
            <a-upload
              :beforeUpload="handleBeforeUpload"
              :showUploadList="false"
              accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.rar"
            >
              <a-tooltip title="发送文件">
                <PaperClipOutlined class="tool-icon" />
              </a-tooltip>
            </a-upload>
            <a-upload
              :beforeUpload="handleBeforeUploadImage"
              :showUploadList="false"
              accept="image/*"
            >
              <a-tooltip title="发送图片">
                <PictureOutlined class="tool-icon" />
              </a-tooltip>
            </a-upload>
          </div>

          <!-- 文本输入框 -->
          <a-textarea
            v-model:value="inputText"
            :autoSize="{ minRows: 1, maxRows: 3 }"
            :maxlength="2000"
            placeholder="输入消息..."
            @pressEnter="handleSendText"
            class="input-textarea"
          />

          <!-- 发送按钮 -->
          <a-button
            type="primary"
            :disabled="!inputText.trim()"
            @click="handleSendText"
            class="send-btn"
          >
            <template #icon>
              <SendOutlined />
            </template>
            发送
          </a-button>
        </div>

        <!-- 上传进度 -->
        <div v-if="uploading" class="upload-progress">
          <a-spin size="small" />
          <span>上传中...</span>
        </div>
      </div>
    </div>
  </a-modal>

  <!-- 图片预览 -->
  <a-image-viewer
    v-if="previewVisible"
    :visible="previewVisible"
    :src="previewImageUrl"
    @close="previewVisible = false"
  />
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import {
  CloseOutlined,
  FileOutlined,
  DownloadOutlined,
  PaperClipOutlined,
  PictureOutlined,
  SendOutlined
} from '@ant-design/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { FileAPI } from '@/api/file'
import { PrivateMessageType } from '@/types/chat'
import type { PrivateMessageVO, ConversationVO, FriendVO } from '@/types/chat'

const props = defineProps<{
  visible: boolean
  friendId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'refreshFriends'): void
}>()

const chatStore = useChatStore()
const authStore = useAuthStore()

const inputText = ref('')
const messageListRef = ref<HTMLElement | null>(null)
const uploading = ref(false)
const previewVisible = ref(false)
const previewImageUrl = ref('')

const friendInfo = computed((): FriendVO | ConversationVO | undefined =>
  chatStore.friends.find(f => f.friendId === props.friendId) ||
  chatStore.conversations.find(c => c.friendId === props.friendId)
)

const friendNickname = computed(() => {
  const info = friendInfo.value
  if (!info) return ''
  return (info as FriendVO).nickname || (info as ConversationVO).friendNickname || ''
})

const friendAvatar = computed(() => {
  const info = friendInfo.value
  if (!info) return ''
  return (info as FriendVO).avatar || (info as ConversationVO).friendAvatar || ''
})

const nicknameInitial = computed(() =>
  (friendNickname.value || 'U').charAt(0).toUpperCase()
)

watch(
  () => props.visible,
  async (val) => {
    if (val && props.friendId) {
      await chatStore.openChat(props.friendId)
      scrollToBottom()
    }
  }
)

watch(
  () => chatStore.currentMessages.length,
  () => {
    nextTick(scrollToBottom)
  }
)

async function handleSendText(e: KeyboardEvent | MouseEvent) {
  if (!inputText.value.trim() || !props.friendId) return

  // 阻止换行
  if (e instanceof KeyboardEvent && e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
  }

  try {
    await chatStore.sendMessage({
      receiverId: props.friendId,
      messageType: PrivateMessageType.TEXT,
      content: inputText.value.trim()
    })
    inputText.value = ''
    scrollToBottom()
  } catch {
    // 错误已在 API 层处理
  }
}

async function handleBeforeUpload(file: File) {
  await uploadFile(file, PrivateMessageType.FILE)
  return false
}

async function handleBeforeUploadImage(file: File) {
  await uploadFile(file, PrivateMessageType.IMAGE)
  return false
}

async function uploadFile(file: File, messageType: PrivateMessageType) {
  if (!props.friendId) return

  uploading.value = true
  try {
    const res = await FileAPI.upload(file)
    if (res.code === 200 && res.data) {
      await chatStore.sendMessage({
        receiverId: props.friendId,
        messageType,
        fileUrl: res.data.downloadUrl,
        fileName: file.name,
        fileSize: file.size,
        content: ''
      })
      scrollToBottom()
    } else {
      message.error(res.msg || '文件上传失败')
    }
  } catch {
    message.error('文件上传失败')
  } finally {
    uploading.value = false
  }
}

function previewImage(url: string) {
  previewImageUrl.value = url
  previewVisible.value = true
}

async function handleDeleteFriend() {
  if (!props.friendId) return
  try {
    await chatStore.removeFriend(props.friendId)
    message.success('已删除好友')
    emit('refreshFriends')
    handleClose()
  } catch {
    // 错误已在 API 层处理
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

function handleClose() {
  chatStore.closeCurrentChat()
  emit('update:visible', false)
}

function formatTime(timeStr: string): string {
  if (!timeStr) return ''
  try {
    const d = new Date(timeStr)
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

function formatFileSize(bytes?: number): string {
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
</script>

<style scoped>
.private-chat-modal :deep(.ant-modal-content) {
  border-radius: 12px;
  overflow: hidden;
}

.private-chat-modal :deep(.ant-modal-close) {
  top: 12px;
  right: 12px;
}

.chat-layout {
  display: flex;
  height: 100%;
  width: 100%;
}

/* 左侧边栏 */
.chat-sidebar {
  width: 160px;
  flex-shrink: 0;
  padding: 20px 16px;
  border-right: 1px solid var(--border-light, #e8e8e8);
  background: var(--surface-bg, #f9fafb);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 8px;
}

.sidebar-user-info {
  width: 100%;
}

.sidebar-nickname {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, #1a1a1a);
  margin: 0 0 4px;
  word-break: break-word;
}

.sidebar-email {
  font-size: 11px;
  color: var(--text-muted, #999);
  margin: 0;
  word-break: break-all;
}

.sidebar-actions {
  margin-top: auto;
}

/* 右侧聊天区 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* 消息列表 */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.loading-messages,
.empty-messages {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted, #999);
  font-size: 13px;
}

.message-item {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  max-width: 75%;
}

.message-item.own {
  flex-direction: row-reverse;
  margin-left: auto;
}

.msg-avatar {
  flex-shrink: 0;
}

.message-content-wrapper {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.message-item.own .message-content-wrapper {
  align-items: flex-end;
}

.message-bubble {
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  background: var(--surface-bg, #f0f0f0);
  color: var(--text-primary, #1a1a1a);
  word-break: break-word;
  max-width: 100%;
  overflow-wrap: break-word;
}

.message-item.own .message-bubble {
  background: var(--brand-primary, #5B87BD);
  color: #fff;
}

.message-time {
  font-size: 10px;
  color: var(--text-muted, #999);
}

.image-message .image-preview {
  max-width: 240px;
  max-height: 180px;
  border-radius: 6px;
  cursor: pointer;
  object-fit: cover;
}

.file-message {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 120px;
}

.file-icon {
  font-size: 20px;
  color: var(--brand-primary, #5B87BD);
  flex-shrink: 0;
}

.file-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.file-name {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
}

.file-size {
  font-size: 11px;
  opacity: 0.7;
}

.file-download {
  font-size: 16px;
  padding: 4px;
  flex-shrink: 0;
}

.recalled-tip {
  font-size: 12px;
  font-style: italic;
  opacity: 0.5;
}

/* 输入区 */
.input-area {
  padding: 12px 16px;
  border-top: 1px solid var(--border-light, #e8e8e8);
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.input-tools {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  padding-bottom: 2px;
}

.tool-icon {
  font-size: 18px;
  color: var(--text-muted, #999);
  cursor: pointer;
  padding: 4px;
  transition: color 0.2s;
}

.tool-icon:hover {
  color: var(--brand-primary, #5B87BD);
}

.input-textarea {
  flex: 1;
  resize: none;
  border-radius: 8px;
}

.send-btn {
  flex-shrink: 0;
  height: 36px;
}

.upload-progress {
  padding: 6px 16px;
  font-size: 12px;
  color: var(--text-muted, #999);
  display: flex;
  align-items: center;
  gap: 6px;
  border-top: 1px solid var(--border-light, #e8e8e8);
}
</style>
