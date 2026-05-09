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
        <div class="sidebar-header" @click="openProfile">
          <div class="avatar-wrapper clickable">
            <a-image
              v-if="friendAvatar"
              :src="friendAvatar"
              :preview="{
                src: friendAvatarOriginalUrl || friendAvatar
              }"
              :preview-mask="false"
              :width="56"
              :height="56"
              class="profile-avatar-image"
              @click.stop
            />
            <a-avatar v-else :size="56" @click.stop>{{ nicknameInitial }}</a-avatar>
          </div>
          <div class="sidebar-user-info">
            <h4 class="sidebar-nickname">{{ friendNickname || '加载中...' }}</h4>
            <p class="sidebar-email">{{ friendEmail || '暂无邮箱' }}</p>
            <p class="sidebar-added-time">{{ formatAddedTime(friendCreatedAt) }}</p>
            <a-button
              v-if="friendAvatar"
              type="link"
              size="small"
              class="download-avatar-btn"
              @click.stop="handleDownloadAvatar"
            >
              <DownloadOutlined /> 下载头像
            </a-button>
          </div>
        </div>
        <a-divider style="margin: 8px 0" />
        <div class="sidebar-actions">
          <a-popconfirm
            title="确定删除该好友？"
            ok-text="确定"
            cancel-text="取消"
            @confirm="handleDeleteFriend"
          >
            <a-button class="logout-btn">
              删除好友
            </a-button>
          </a-popconfirm>
        </div>
      </div>

      <!-- 右侧：聊天区域 -->
      <div class="chat-main">
        <!-- 消息列表 -->
        <div class="message-list" ref="messageListRef" @scroll="handleScroll">
          <!-- 顶部加载更多提示 -->
          <div v-if="chatStore.hasMoreHistory && chatStore.currentMessages.length > 0" class="load-more-hint">
            <a-spin v-if="isLoadingMore" size="small" />
            <span v-else>上拉加载更多</span>
          </div>
          <div v-if="chatStore.messagesLoading && chatStore.currentMessages.length === 0" class="loading-messages">
            <a-spin />
          </div>
          <template v-else>
            <div
              v-for="msg in chatStore.currentMessages"
              :key="msg.messageId"
              class="message-item"
              :class="{ own: msg.isOwn }"
            >
              <!-- 发送者头像（非自己的消息显示） -->
              <UserAvatar
                v-if="!msg.isOwn"
                :user-id="msg.senderId"
                :nickname="msg.senderNickname || undefined"
                :avatar="msg.senderAvatar || undefined"
                :size="32"
                class="msg-avatar"
              />

              <div class="message-content-wrapper">
                <div class="message-bubble">
                  <!-- 文本消息 -->
                  <span v-if="msg.messageType === PrivateMessageType.TEXT">{{ msg.content }}</span>

                  <!-- 图片消息 -->
                  <div v-else-if="msg.messageType === PrivateMessageType.IMAGE" class="image-message">
                    <ImageMessageBubble
                      :file-url="msg.fileUrl"
                      :file-name="msg.fileName"
                      :content="msg.content"
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

  <!-- 用户资料弹窗 -->
  <MemberProfileModal
    v-model:visible="profileVisible"
    :member-id="friendId"
    @refresh-friends="() => emit('refreshFriends')"
  />
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import {
  CloseOutlined,
  DownloadOutlined,
  FileOutlined,
  PaperClipOutlined,
  PictureOutlined,
  SendOutlined
} from '@ant-design/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { FileAPI } from '@/api/file'
import { PrivateMessageType } from '@/types/chat'
import type { PrivateMessageVO, ConversationVO, FriendVO } from '@/types/chat'
import MemberProfileModal from '@/components/MemberProfileModal.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import ImageMessageBubble from '@/components/chat/ImageMessageBubble.vue'

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
const isLoadingMore = ref(false)

const profileVisible = ref(false)

const friendFromFriends = computed((): FriendVO | undefined =>
  chatStore.friends.find(f => f.friendId === props.friendId)
)

const friendFromConversations = computed((): ConversationVO | undefined =>
  chatStore.conversations.find(c => c.friendId === props.friendId)
)

const friendNickname = computed(() => {
  return friendFromFriends.value?.nickname
    || friendFromConversations.value?.friendNickname
    || ''
})

const friendAvatar = computed(() => {
  return friendFromFriends.value?.avatar
    || friendFromConversations.value?.friendAvatar
    || ''
})

const friendEmail = computed(() => {
  return friendFromFriends.value?.email || ''
})

const friendAvatarOriginalUrl = computed(() => {
  return friendFromFriends.value?.avatarOriginalUrl || ''
})

const friendCreatedAt = computed(() => {
  return friendFromFriends.value?.createdAt || ''
})

const nicknameInitial = computed(() => {
  const name = friendNickname.value
  if (!name) return '?'
  return name.trim().slice(0, 2).toUpperCase()
})

watch(
  () => props.visible,
  async (val) => {
    if (val && props.friendId) {
      // 惰性加载：首次打开时确保好友列表和会话列表已加载
      if (chatStore.friends.length === 0) {
        chatStore.fetchFriends()
      }
      if (chatStore.conversations.length === 0) {
        chatStore.fetchConversations()
      }
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
        fileUrl: res.data.previewUrl,  // 缩略图 URL 供聊天界面显示
        content: res.data.downloadUrl,  // 原图 URL 供点击预览
        fileName: file.name,
        fileSize: file.size
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

// 上拉加载更多历史消息（滚动位置保持）
async function handleScroll() {
  const el = messageListRef.value
  if (!el) return

  if (el.scrollTop <= 10 && !chatStore.messagesLoading && chatStore.hasMoreHistory) {
    isLoadingMore.value = true

    // 保存当前滚动状态以便加载后恢复
    const savedScrollHeight = el.scrollHeight
    const savedScrollTop = el.scrollTop

    const oldestId = chatStore.currentMessages[0]?.messageId
    if (oldestId) {
      await chatStore.fetchMessages(chatStore.currentConversationId!, oldestId)
      nextTick(() => {
        if (messageListRef.value) {
          const newScrollHeight = messageListRef.value.scrollHeight
          messageListRef.value.scrollTop = savedScrollTop + (newScrollHeight - savedScrollHeight)
        }
      })
    }
    isLoadingMore.value = false
  }
}

function handleClose() {
  chatStore.closeCurrentChat()
  emit('update:visible', false)
}

function openProfile() {
  if (props.friendId) {
    profileVisible.value = true
  }
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

function formatAddedTime(timeStr: string): string {
  if (!timeStr) return ''
  try {
    const d = new Date(timeStr.replace(' ', 'T'))
    return `添加于 ${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
  } catch {
    return ''
  }
}

async function handleDownloadAvatar() {
  if (!props.friendId) return
  try {
    const url = await FileAPI.downloadUserAvatar(props.friendId)
    window.open(url, '_blank')
  } catch {
    message.error('下载头像失败')
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

.private-chat-modal :deep(.ant-divider) {
  border-color: var(--border-default, #E2E8F0);
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
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
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
  line-height: 1.6;
  color: var(--text-muted, #999);
  margin-bottom: 2px;
  word-break: break-all;
}

.sidebar-added-time {
  font-size: 11px;
  line-height: 1.6;
  color: var(--text-muted, #999);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-actions {
  margin-top: auto;
  padding-top: 12px;
  display: flex;
  justify-content: center;
}

.avatar-wrapper.clickable {
  cursor: pointer;
}

.avatar-wrapper.clickable :deep(.ant-image) img,
.avatar-wrapper.clickable :deep(.ant-avatar) {
  border-radius: 50%;
  border: 3px solid var(--brand-primary, #5B87BD);
}

.download-avatar-btn {
  font-size: 12px;
  padding: 2px 4px;
  height: auto;
  color: var(--text-muted, #999);
}

.download-avatar-btn:hover {
  color: var(--brand-primary, #5B87BD);
}

.logout-btn {
  color: var(--color-error) !important;
  border: 1px solid var(--color-error) !important;
  width: 100%;
}

.logout-btn:hover {
  background: var(--color-error) !important;
  color: var(--text-on-primary) !important;
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

/* 加载更多提示 */
.load-more-hint {
  text-align: center;
  padding: 8px;
  font-size: 12px;
  color: var(--text-muted, #999);
  flex-shrink: 0;
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
  background: var(--bubble-other-bg, #EFF2F7);
  color: var(--bubble-other-text, #1E2432);
  word-break: break-word;
  max-width: 100%;
  overflow-wrap: break-word;
}

.message-item.own .message-bubble {
  background: var(--bubble-own-bg, #5B87BD);
  color: var(--bubble-own-text, #FFFFFF);
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
