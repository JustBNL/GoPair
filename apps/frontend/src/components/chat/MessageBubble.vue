<template>
  <div 
    :class="[
      'message-bubble',
      { 'own-message': message.isOwn }
    ]"
  >
    <!-- 用户头像和信息 -->
    <div v-if="!message.isOwn" class="message-avatar">
      <a-avatar 
        :src="message.senderAvatar" 
        :size="32"
      >
        {{ message.senderNickname?.charAt(0) }}
      </a-avatar>
    </div>

    <!-- 消息内容区域 -->
    <div class="message-content">
      <!-- 发送者信息 -->
      <div v-if="!message.isOwn && showSenderInfo" class="sender-info">
        <span class="sender-name">{{ message.senderNickname }}</span>
        <span class="message-time">{{ formatTime(message.createTime) }}</span>
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
          { 'has-reply': message.replyToId }
        ]"
      >
        <!-- 文本消息 -->
        <div v-if="message.messageType === MessageType.TEXT" class="text-message">
          {{ message.content }}
        </div>

        <!-- 图片消息 -->
        <div v-else-if="message.messageType === MessageType.IMAGE" class="image-message">
          <a-image
            :src="message.fileUrl"
            :alt="message.fileName"
            :preview="true"
            class="message-image"
          />
          <div v-if="message.fileName" class="image-caption">
            {{ message.fileName }}
          </div>
        </div>

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

      <!-- 消息状态和时间（自己的消息） -->
      <div v-if="message.isOwn" class="message-meta">
        <span class="message-time">{{ formatTime(message.createTime) }}</span>
        <check-outlined class="message-status" />
      </div>
    </div>

    <!-- 消息操作菜单 -->
    <div v-if="showActions" class="message-actions">
      <a-dropdown :trigger="['click']">
        <a-button type="text" size="small">
          <more-outlined />
        </a-button>
        <template #overlay>
          <a-menu>
            <a-menu-item key="reply" @click="onReply">
              <message-outlined />
              回复
            </a-menu-item>
            <a-menu-item key="copy" @click="onCopy">
              <copy-outlined />
              复制
            </a-menu-item>
            <a-menu-item v-if="message.isOwn" key="recall" @click="onRecall">
              <rollback-outlined />
              撤回
            </a-menu-item>
            <a-menu-item v-if="message.isOwn" key="delete" danger @click="onDelete">
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
import { ref, computed } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import dayjs from 'dayjs'
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
import { FileAPI } from '@/api/file'

interface Props {
  message: MessageVO
  showSenderInfo?: boolean
  showActions?: boolean
}

interface Emits {
  (e: 'reply', message: MessageVO): void
  (e: 'delete', messageId: number): void
  (e: 'recall', messageId: number): void
}

const props = withDefaults(defineProps<Props>(), {
  showSenderInfo: true,
  showActions: true
})

const emit = defineEmits<Emits>()

// 语音播放状态
const isPlaying = ref(false)
const voiceDuration = ref(0)

/**
 * 格式化时间
 */
const formatTime = (timeStr: string) => {
  return dayjs(timeStr).format('HH:mm')
}

/**
 * 格式化语音时长
 */
const formatDuration = (seconds: number) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
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
 * 切换语音播放
 */
const togglePlay = () => {
  isPlaying.value = !isPlaying.value
  // TODO: 实现语音播放逻辑
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
      background-color: #1890ff;
      color: white;
    }
  }
}

.message-avatar {
  flex-shrink: 0;
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
  color: #8c8c8c;

  .sender-name {
    font-weight: 500;
    color: #595959;
  }
}

.reply-message {
  margin-bottom: 4px;

  .reply-content {
    padding: 6px 8px;
    background-color: #f5f5f5;
    border-left: 3px solid #1890ff;
    border-radius: 4px;
    font-size: 12px;
    color: #8c8c8c;

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
  background-color: #f5f5f5;
  border-radius: 8px;
  position: relative;
  word-wrap: break-word;

  &.has-reply {
    border-top-left-radius: 4px;
  }

  &.message-type-1 {
    // 文本消息
    .text-message {
      line-height: 1.4;
    }
  }

  &.message-type-2 {
    // 图片消息
    padding: 4px;
    
    .image-message {
      .message-image {
        max-width: 200px;
        max-height: 200px;
        border-radius: 4px;
      }

      .image-caption {
        padding: 4px 8px;
        font-size: 12px;
        color: #8c8c8c;
        text-align: center;
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
          color: #1890ff;
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
            color: #8c8c8c;
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
          color: #8c8c8c;
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
  color: #8c8c8c;

  .message-status {
    color: #52c41a;
  }
}

.message-actions {
  opacity: 0;
  transition: opacity 0.2s;
}

.message-bubble:hover .message-actions {
  opacity: 1;
}

@media (max-width: 768px) {
  .message-content {
    max-width: 80%;
  }

  .message-body {
    &.message-type-2 {
      .image-message {
        .message-image {
          max-width: 150px;
          max-height: 150px;
        }
      }
    }
  }
}
</style> 