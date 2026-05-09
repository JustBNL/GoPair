<template>
  <div 
    :class="[
      'message-bubble',
      { 'own-message': message.isOwn }
    ]"
  >
    <!-- 用户头像 -->
    <div class="message-avatar" style="cursor: pointer" @click="!message.isOwn && emit('viewProfile', message.senderId)">
      <UserAvatar
        :user-id="message.senderId"
        :nickname="message.senderNickname"
        :avatar="message.senderAvatar"
        :size="32"
        :clickable="false"
        class="avatar-img"
      />
    </div>

    <!-- 消息内容区域 -->
    <div class="message-content">
      <!-- 发送者信息（仅他人消息显示昵称，不显示时间） -->
      <div v-if="!message.isOwn && showSenderInfo" class="sender-info">
        <span
          class="sender-name"
          :style="{ cursor: 'pointer' }"
          @click="emit('viewProfile', message.senderId)"
        >{{ message.senderNickname }}</span>
      </div>

      <!-- 回复消息 -->
      <div v-if="message.replyToId" class="reply-message">
        <div class="reply-content">
          <span class="reply-sender">{{ message.replyToSender }}</span>
          <span class="reply-text">{{ message.replyToContent }}</span>
        </div>
      </div>

      <!-- 消息主体 -->
      <div
        :class="[
          'message-body',
          `message-type-${message.messageType}`,
          { 'has-reply': message.replyToId },
          { 'message-recalled': message.isRecalled }
        ]"
      >
        <!-- 已撤回消息 -->
        <div v-if="message.isRecalled" class="recalled-placeholder">
          <span class="recalled-text">消息已撤回</span>
        </div>

        <!-- 文本消息 -->
        <div v-else-if="message.messageType === MessageType.TEXT" class="text-message" v-html="renderEmojiContent(message.content || '')">
        </div>

        <!-- 图片消息 -->
        <ImageMessageBubble
          v-else-if="message.messageType === MessageType.IMAGE"
          :file-url="message.fileUrl"
          :file-name="message.fileName"
          :content="message.content"
        />

        <!-- 文件消息 -->
        <div v-else-if="message.messageType === MessageType.FILE" class="file-message">
          <div class="file-info">
            <div class="file-icon">
              <file-outlined />
            </div>
            <div class="file-details">
              <div class="file-name">{{ message.fileName }}</div>
              <div class="file-size">{{ message.fileSizeFormatted }}</div>
            </div>
            <div class="file-actions">
            <a-button
              type="link"
              size="small"
              aria-label="下载文件"
              @click="downloadFile"
            >
              <download-outlined />
              下载
            </a-button>
            </div>
          </div>
        </div>

        <!-- 语音消息 -->
        <div v-else-if="message.messageType === MessageType.VOICE" class="voice-message">
          <div class="voice-player">
            <a-button
              :type="isPlaying ? 'primary' : 'default'"
              shape="circle"
              size="small"
              @click="togglePlay"
            >
              <play-circle-outlined v-if="!isPlaying" />
              <pause-circle-outlined v-else />
            </a-button>
            <div class="voice-duration">{{ formatDuration(voiceDuration) }}</div>
          </div>
        </div>

      </div>

      <!-- 统一的消息时间与状态（右下角显示时间） -->
      <div class="message-meta">
        <span class="message-time">{{ formatTime(message.createTime) }}</span>
        <check-outlined v-if="message.isOwn" class="message-status" />
      </div>
    </div>

    <!-- 消息操作菜单 - 移动端始终显示，桌面端hover显示，已撤回消息不显示 -->
    <div
      v-if="showActions && !message.isRecalled"
      class="message-actions"
      role="toolbar"
      aria-label="消息操作"
      :class="{ 'always-visible': isMobile }"
    >
      <a-dropdown :trigger="['click']">
        <a-button type="text" size="small" aria-label="更多操作" @keydown.enter.prevent="onReply">
          <more-outlined />
        </a-button>
        <template #overlay>
          <a-menu role="menu">
            <a-menu-item key="reply" role="menuitem" @click="onReply">
              <message-outlined />
              回复
            </a-menu-item>
            <a-menu-item key="copy" role="menuitem" @click="onCopy">
              <copy-outlined />
              复制
            </a-menu-item>
            <a-menu-item v-if="message.isOwn" key="recall" role="menuitem" @click="onRecall">
              <rollback-outlined />
              撤回
            </a-menu-item>
            <a-menu-item v-if="message.isOwn" key="delete" danger role="menuitem" @click="onDelete">
              <delete-outlined />
              删除
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import {
  FileOutlined,
  DownloadOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  CheckOutlined,
  MoreOutlined,
  MessageOutlined,
  CopyOutlined,
  RollbackOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import { MessageType, type MessageVO } from '@/types/api'
import { formatTime } from '@/utils/format'
import { renderEmojiContent } from '@/utils/emoji'
import UserAvatar from '@/components/UserAvatar.vue'
import ImageMessageBubble from './ImageMessageBubble.vue'

interface Props {
  message: MessageVO
  showSenderInfo?: boolean
  showActions?: boolean
}

interface Emits {
  (e: 'reply', message: MessageVO): void
  (e: 'delete', messageId: number): void
  (e: 'recall', messageId: number): void
  (e: 'viewProfile', senderId: number): void
}

const props = withDefaults(defineProps<Props>(), {
  showSenderInfo: true,
  showActions: true
})

const emit = defineEmits<Emits>()

// 移动端检测（复用 RoomCard 同逻辑：768px 断点）
const isMobile = ref(false)

function updateMobileState() {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  updateMobileState()
  window.addEventListener('resize', updateMobileState, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('resize', updateMobileState)
})

// 语音播放状态
const isPlaying = ref(false)
const voiceDuration = ref(0)
let audioElement: HTMLAudioElement | null = null

/**
 * 格式化语音时长
 */
const formatDuration = (seconds: number) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

// formatTime 来自 @/utils/format（已在本文件顶部导入）

/**
 * 切换语音播放
 */
const togglePlay = async () => {
  if (!audioElement) {
    audioElement = new Audio(props.message.fileUrl)
    audioElement.addEventListener('loadedmetadata', () => {
      voiceDuration.value = Math.floor(audioElement!.duration || 0)
    })
    audioElement.addEventListener('ended', () => {
      isPlaying.value = false
      audioElement!.currentTime = 0
    })
  }

  if (isPlaying.value) {
    audioElement.pause()
    isPlaying.value = false
  } else {
    try {
      await audioElement.play()
      isPlaying.value = true
    } catch (error) {
      isPlaying.value = false
    }
  }
}

/**
 * 下载文件
 */
const downloadFile = async () => {
  try {
    const blob = await FileAPI.downloadFile(props.message.messageId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = props.message.fileName || 'download'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (error) {
    antMessage.error('下载失败')
  }
}

/**
 * 回复消息
 */
const onReply = () => {
  emit('reply', props.message)
}

/**
 * 复制消息
 */
const onCopy = async () => {
  try {
    await navigator.clipboard.writeText(props.message.content || '')
    antMessage.success('已复制到剪贴板')
  } catch (error) {
    antMessage.error('复制失败')
  }
}

/**
 * 撤回消息
 */
const onRecall = () => {
  emit('recall', props.message.messageId)
}

/**
 * 删除消息
 */
const onDelete = () => {
  emit('delete', props.message.messageId)
}
</script>

<style scoped lang="scss">
.message-bubble {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
  gap: 8px;

  &.own-message {
    flex-direction: row-reverse;

    .message-content {
      align-items: flex-end;
    }

    .message-body {
      background-color: var(--bubble-own-bg);
      color: var(--bubble-own-text);
    }
  }
}

.message-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
}

.message-content {
  display: flex;
  flex-direction: column;
  max-width: 60%;
  min-width: 80px;
}

.sender-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--text-muted);

  .sender-name {
    font-weight: 500;
    color: var(--text-secondary);
  }
}

.reply-message {
  margin-bottom: 4px;

  .reply-content {
    padding: 6px 8px;
    background-color: var(--bubble-reply-bg);
    border-left: 3px solid var(--bubble-reply-border);
    border-radius: 4px;
    font-size: 12px;
    color: var(--text-muted);

    .reply-sender {
      font-weight: 500;
      margin-right: 4px;
    }

    .reply-text {
      display: block;
      margin-top: 2px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 200px;
    }
  }
}

.message-body {
  padding: 8px 12px;
  background-color: var(--bubble-other-bg);
  color: var(--bubble-other-text);
  border-radius: 8px;
  position: relative;
  word-wrap: break-word;

  &.has-reply {
    border-top-left-radius: 4px;
  }

  &.message-recalled {
    background-color: transparent;
    color: var(--text-muted);
    font-style: italic;
    padding: 4px 8px;

    .recalled-placeholder {
      .recalled-text {
        font-size: 12px;
        color: var(--text-muted);
      }
    }
  }

  &.message-type-1 {
    // 文本消息
    .text-message {
      line-height: 1.4;

      :deep(img) {
        width: 1.2em;
        height: 1.2em;
        vertical-align: text-bottom;
        object-fit: contain;
        display: inline-block;
      }
    }
  }

  &.message-type-3 {
    // 文件消息
    padding: 8px;

    .file-message {
      .file-info {
        display: flex;
        align-items: center;
        gap: 8px;

        .file-icon {
          font-size: 24px;
          color: var(--color-info);
        }

        .file-details {
          flex: 1;

          .file-name {
            font-weight: 500;
            margin-bottom: 2px;
            word-break: break-all;
          }

          .file-size {
            font-size: 12px;
            color: var(--text-muted);
          }
        }

        .file-actions {
          flex-shrink: 0;
        }
      }
    }
  }

  &.message-type-4 {
    // 语音消息
    .voice-message {
      .voice-player {
        display: flex;
        align-items: center;
        gap: 8px;

        .voice-duration {
          font-size: 12px;
          color: var(--text-muted);
        }
      }
    }
  }
}

.message-meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);

  .message-status {
    color: var(--color-success);
  }
}

.message-actions {
  opacity: 0;
  transition: opacity 0.2s;

  &.always-visible {
    opacity: 1;
  }
}

.message-bubble:hover .message-actions {
  opacity: 1;
}

@media (max-width: 768px) {
  .message-actions {
    opacity: 1;
  }
}

@media (max-width: 768px) {
  .message-content {
    max-width: 80%;
  }

  .message-body {
  }
}
</style>
