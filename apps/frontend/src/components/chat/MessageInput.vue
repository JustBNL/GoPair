<template>
  <div class="message-input">
    <!-- Emoji 选择器（工具栏上方） -->
    <div v-if="emojiPickerVisible" class="emoji-picker-inline">
      <emoji-bar @send-emoji="handleSendEmoji" />
    </div>

    <!-- 回复预览 -->
    <div v-if="replyMessage" class="reply-preview">
      <div class="reply-info">
        <span class="reply-label">回复</span>
        <span class="reply-target">{{ replyMessage.senderNickname }}</span>
        <span class="reply-content">{{ replyMessage.content }}</span>
      </div>
      <a-button
        type="text"
        size="small"
        @click="cancelReply"
      >
        <close-outlined />
      </a-button>
    </div>

    <!-- 工具栏 -->
    <div class="input-toolbar" :class="{ 'toolbar-disabled': sending || props.disabled || isRecording }">
      <!-- 表情按钮 -->
      <a-tooltip title="表情">
        <a-button type="text" size="small" :disabled="sending || props.disabled || isRecording" aria-label="表情" @click="emojiPickerVisible = !emojiPickerVisible">
          <smile-outlined />
        </a-button>
      </a-tooltip>

      <!-- 文件上传 -->
      <a-tooltip title="发送文件">
        <a-upload
          :show-upload-list="false"
          :before-upload="handleFileUpload"
          accept="*/*"
          :disabled="sending || props.disabled || isRecording"
        >
          <a-button type="text" size="small" :disabled="sending || props.disabled || isRecording" aria-label="发送文件">
            <paper-clip-outlined />
          </a-button>
        </a-upload>
      </a-tooltip>

      <!-- 图片上传 -->
      <a-tooltip title="发送图片">
        <a-upload
          :show-upload-list="false"
          :before-upload="handleImageUpload"
          accept="image/*"
          :disabled="sending || props.disabled || isRecording"
        >
          <a-button type="text" size="small" :disabled="sending || props.disabled || isRecording" aria-label="发送图片">
            <picture-outlined />
          </a-button>
        </a-upload>
      </a-tooltip>

      <!-- 语音录制 -->
      <a-button
        type="text"
        size="small"
        :disabled="sending || props.disabled || isRecording"
        :class="{ 'recording': isRecording }"
        @click="startRecording"
        @touchstart.prevent.passive="startRecording"
        aria-label="语音录制"
        title="语音消息"
      >
        <audio-outlined />
      </a-button>
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
      <a-textarea
        ref="textareaRef"
        v-model:value="inputText"
        :placeholder="placeholder"
        :auto-size="{ minRows: 1, maxRows: 5 }"
        :disabled="sending"
        @keydown="handleKeyDown"
        @paste="handlePaste"
      />
      
      <!-- 发送按钮 -->
      <a-button
        type="primary"
        :loading="sending"
        :disabled="!canSend"
        @click="sendMessage"
        class="send-button"
      >
        <send-outlined />
      </a-button>
    </div>

    <!-- 上传进度 -->
    <div v-if="uploadProgress > 0 && uploadProgress < 100" class="upload-progress">
      <a-progress 
        :percent="uploadProgress" 
        size="small"
        :show-info="false"
      />
      <span class="upload-text">上传中...</span>
    </div>

    <!-- 语音录制提示 -->
    <a-modal
      v-model:open="showRecordingModal"
      title="录制语音"
      :footer="null"
      :closable="false"
      :mask-closable="true"
      centered
      :body-style="{ padding: '24px', cursor: 'pointer' }"
      @cancel="stopRecording"
    >
      <div class="recording-modal" @click="stopRecording">
        <div class="recording-animation">
          <div class="pulse-circle"></div>
          <audio-outlined class="audio-icon" />
        </div>
        <p class="recording-tip">点击任意区域结束录制</p>
        <p class="recording-duration">{{ formatRecordingDuration }}</p>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import {
  CloseOutlined,
  SmileOutlined,
  PaperClipOutlined,
  PictureOutlined,
  AudioOutlined,
  SendOutlined
} from '@ant-design/icons-vue'
import { MessageType, type MessageVO } from '@/types/api'
import EmojiBar from './EmojiBar.vue'

interface Props {
  roomId: number
  placeholder?: string
  disabled?: boolean
  replyMessage?: MessageVO | null
}

interface Emits {
  (e: 'send-message', data: {
    content?: string
    messageType: MessageType
    fileUrl?: string
    fileName?: string
    fileSize?: number
    replyToId?: number
  }): void
  (e: 'cancel-reply'): void
  (e: 'upload-progress', progress: number): void
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '输入消息...',
  disabled: false,
  replyMessage: null
})

const emit = defineEmits<Emits>()

// 输入状态
const inputText = ref('')
const sending = ref(false)
const uploadProgress = ref(0)
const emojiPickerVisible = ref(false)

// 语音录制
const isRecording = ref(false)
const showRecordingModal = ref(false)
const recordingDuration = ref(0)
const recordingTimer = ref<number | null>(null)
const mediaRecorder = ref<MediaRecorder | null>(null)
const audioChunks = ref<Blob[]>([])
const recordingStartTime = ref(0)

// 组件引用
const textareaRef = ref()

/**
 * 是否可以发送
 */
const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !sending.value && !props.disabled
})

/**
 * 格式化录制时长
 */
const formatRecordingDuration = computed(() => {
  const mins = Math.floor(recordingDuration.value / 60)
  const secs = recordingDuration.value % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
})

/**
 * 处理键盘事件
 */
const handleKeyDown = (event: KeyboardEvent) => {
  if (event.key === 'Enter') {
    if (event.shiftKey) {
      // Shift + Enter 换行
      return
    } else {
      // Enter 发送：统一走 sendMessage，与按钮点击行为一致
      event.preventDefault()
      sendMessage()
    }
  }
}

/**
 * 处理粘贴事件
 */
const handlePaste = async (event: ClipboardEvent) => {
  const items = event.clipboardData?.items
  if (!items) return

  for (const item of items) {
    if (item.type.startsWith('image/')) {
      event.preventDefault()
      const file = item.getAsFile()
      if (file) {
        await handleImageUpload(file)
      }
      break
    }
  }
}

/**
 * 统一的消息发送入口（供回车键和按钮点击共用）
 * 始终从原生 DOM 同步读取输入值，保证时序一致性
 */
const sendMessage = () => {
  if (sending.value) return

  const nativeEl =
    textareaRef.value?.$el?.querySelector('textarea') ??
    textareaRef.value?.resizableTextArea?.textArea
  const rawText = nativeEl?.value?.trim() ?? ''

  if (!rawText) return

  sending.value = true

  // 发送后立即清空输入框
  inputText.value = ''
  if (nativeEl) nativeEl.value = ''

  const replyToId = props.replyMessage?.messageId
  if (props.replyMessage) {
    cancelReply()
  }

  try {
    emit('send-message', {
      content: rawText,
      messageType: MessageType.TEXT,
      replyToId
    })
  } catch (error) {
    antMessage.error('发送失败')
  } finally {
    sending.value = false
    nextTick(() => textareaRef.value?.focus())
  }
}

/**
 * 处理文件上传
 */
const handleFileUpload = async (file: File) => {
  if (file.size > 100 * 1024 * 1024) { // 100MB
    antMessage.error('文件大小不能超过100MB')
    return false
  }

  try {
    uploadProgress.value = 0
    sending.value = true
    
    // 上传文件到文件服务，获取持久 URL
    const { FileAPI } = await import('@/api/file')
    const response = await FileAPI.uploadFile(
      {
        roomId: props.roomId,
        file
      },
      (progressEvent) => {
        const progress = Math.round((progressEvent.loaded / (progressEvent.total || 1)) * 100)
        uploadProgress.value = progress
        emit('upload-progress', uploadProgress.value)
      }
    )
    
    const fileVO = response.data
    
    // 发送消息，使用服务器返回的持久 URL
    emit('send-message', {
      messageType: MessageType.FILE,
      fileUrl: fileVO.downloadUrl,
      fileName: file.name,
      fileSize: file.size,
      replyToId: props.replyMessage?.messageId
    })

    uploadProgress.value = 100
    setTimeout(() => {
      uploadProgress.value = 0
    }, 1000)

    // 取消回复
    if (props.replyMessage) {
      cancelReply()
    }

  } catch (error: any) {
    antMessage.error(error.response?.data?.msg || '文件上传失败')
    uploadProgress.value = 0
  } finally {
    sending.value = false
  }

  return false // 阻止默认上传
}

/**
 * 处理图片上传
 */
const handleImageUpload = async (file: File) => {
  if (!file.type.startsWith('image/')) {
    antMessage.error('请选择图片文件')
    return false
  }

  if (file.size > 10 * 1024 * 1024) { // 10MB
    antMessage.error('图片大小不能超过10MB')
    return false
  }

  try {
    uploadProgress.value = 0
    sending.value = true
    
    // 上传图片到文件服务，获取持久 URL
    const { FileAPI } = await import('@/api/file')
    const response = await FileAPI.uploadFile(
      {
        roomId: props.roomId,
        file
      },
      (progressEvent) => {
        const progress = Math.round((progressEvent.loaded / (progressEvent.total || 1)) * 100)
        uploadProgress.value = progress
        emit('upload-progress', uploadProgress.value)
      }
    )
    
    const fileVO = response.data
    
    // 发送消息：fileUrl 存缩略图 URL 供聊天界面显示，content 存原图 URL 供点击预览
    emit('send-message', {
      messageType: MessageType.IMAGE,
      fileUrl: fileVO.previewUrl,
      content: fileVO.downloadUrl,
      fileName: file.name,
      fileSize: file.size,
      replyToId: props.replyMessage?.messageId
    })

    uploadProgress.value = 100
    setTimeout(() => {
      uploadProgress.value = 0
    }, 1000)

    // 取消回复
    if (props.replyMessage) {
      cancelReply()
    }

  } catch (error: any) {
    antMessage.error(error.response?.data?.msg || '图片上传失败')
    uploadProgress.value = 0
  } finally {
    sending.value = false
  }

  return false // 阻止默认上传
}

/**
 * 开始录制语音
 */
const startRecording = async () => {
  let stream: MediaStream | null = null

  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true })

    mediaRecorder.value = new MediaRecorder(stream)
    audioChunks.value = []
    recordingDuration.value = 0
    recordingStartTime.value = Date.now()

    mediaRecorder.value.ondataavailable = (event) => {
      audioChunks.value.push(event.data)
    }

    mediaRecorder.value.onstop = async () => {
      // 检查最小录制时长，防误触
      const elapsed = Date.now() - recordingStartTime.value
      if (elapsed < 300) {
        if (stream) {
          stream.getTracks().forEach(track => track.stop())
        }
        mediaRecorder.value = null
        audioChunks.value = []
        isRecording.value = false
        showRecordingModal.value = false
        if (recordingTimer.value) {
          clearInterval(recordingTimer.value)
          recordingTimer.value = null
        }
        antMessage.info('说话时间太短')
        return
      }

      // 确保清理 stream tracks
      if (stream) {
        stream.getTracks().forEach(track => track.stop())
        stream = null
      }

      const audioBlob = new Blob(audioChunks.value, { type: 'audio/webm' })

      // 上传语音文件到文件服务
      try {
        sending.value = true
        const { FileAPI } = await import('@/api/file')
        const file = new File([audioBlob], `voice_${Date.now()}.webm`, { type: 'audio/webm' })
        const response = await FileAPI.uploadFile(
          { roomId: props.roomId, file },
          (progressEvent) => {
            const progress = Math.round((progressEvent.loaded / (progressEvent.total || 1)) * 100)
            uploadProgress.value = progress
            emit('upload-progress', uploadProgress.value)
          }
        )
        const fileVO = response.data
        uploadProgress.value = 100
        setTimeout(() => { uploadProgress.value = 0 }, 1000)

        emit('send-message', {
          messageType: MessageType.VOICE,
          fileUrl: fileVO.previewUrl,
          content: fileVO.downloadUrl,
          fileName: file.name,
          fileSize: audioBlob.size,
          replyToId: props.replyMessage?.messageId
        })

        if (props.replyMessage) cancelReply()
      } catch (error: any) {
        antMessage.error(error.response?.data?.msg || '语音上传失败')
        uploadProgress.value = 0
      } finally {
        sending.value = false
        mediaRecorder.value = null
        audioChunks.value = []
      }
    }

    mediaRecorder.value.start()
    isRecording.value = true
    showRecordingModal.value = true

    // 开始计时
    recordingTimer.value = window.setInterval(() => {
      recordingDuration.value++
    }, 1000)

  } catch (error) {
    if (stream) {
      stream.getTracks().forEach(track => track.stop())
    }
    antMessage.error('无法访问麦克风')
  }
}

/**
 * 停止录制语音
 */
const stopRecording = () => {
  if (mediaRecorder.value && isRecording.value) {
    mediaRecorder.value.stop()
    isRecording.value = false
    showRecordingModal.value = false

    if (recordingTimer.value) {
      clearInterval(recordingTimer.value)
      recordingTimer.value = null
    }
  }
}

/**
 * 取消回复
 */
const cancelReply = () => {
  emit('cancel-reply')
}

/**
 * 处理 Emoji 发送（来自 EmojiBar 弹窗）
 */
const handleSendEmoji = (emoji: string) => {
  emojiPickerVisible.value = false
  emit('send-message', {
    content: emoji,
    messageType: 5 // Emoji 互动消息类型
  })
}

// 监听回复消息变化，自动聚焦输入框
watch(() => props.replyMessage, (newVal) => {
  if (newVal) {
    nextTick(() => {
      textareaRef.value?.focus()
    })
  }
})
</script>

<style scoped lang="scss">
.message-input {
  background: var(--surface-card);
  border-top: 1px solid var(--border-light);
  padding: 16px;
}

.reply-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  margin-bottom: 12px;
  background-color: var(--bubble-reply-bg);
  border-left: 3px solid var(--bubble-reply-border);
  border-radius: 4px;

  .reply-info {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;

    .reply-label {
      color: var(--color-info);
      font-weight: 500;
    }

    .reply-target {
      color: var(--text-secondary);
      font-weight: 500;
    }

    .reply-content {
      color: var(--text-muted);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 200px;
    }
  }
}

.input-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  padding: 0 4px;

  :deep(.ant-btn-text) {
    width: 32px;
    height: 32px;
    border-radius: 6px;
    color: var(--text-secondary);
    transition: all 0.2s;

    &:hover:not(:disabled) {
      background: var(--brand-accent-light);
      color: var(--brand-accent);
    }

    &:active:not(:disabled) {
      background: var(--brand-accent-medium);
      color: var(--brand-accent-hover);
    }

    &:disabled {
      color: var(--border-default);
      cursor: not-allowed;
    }
  }

  .recording {
    color: var(--color-error);
    animation: pulse 1s infinite;
  }
}

.toolbar-disabled {
  opacity: 0.5;
  pointer-events: none;
}

.input-area {
  display: flex;
  align-items: flex-end;
  gap: 8px;

  :deep(.ant-input) {
    border-radius: 20px;
    padding: 8px 16px;
    resize: none;
  }

  .send-button {
    flex-shrink: 0;
    border-radius: 50%;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0;
  }
}

.upload-progress {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;

  .upload-text {
    font-size: 12px;
    color: var(--text-muted);
  }
}

.recording-modal {
  text-align: center;
  padding: 20px;

  .recording-animation {
    position: relative;
    margin: 20px auto;
    width: 80px;
    height: 80px;
    display: flex;
    align-items: center;
    justify-content: center;

    .pulse-circle {
      position: absolute;
      width: 100%;
      height: 100%;
      border: 2px solid var(--color-error);
      border-radius: 50%;
      animation: pulse-ring 2s infinite;
    }

    .audio-icon {
      font-size: 32px;
      color: var(--color-error);
      z-index: 1;
    }
  }

  .recording-tip {
    margin: 16px 0 8px;
    color: var(--text-secondary);
  }

  .recording-duration {
    font-size: 18px;
    font-weight: 500;
    color: var(--color-error);
    margin: 0;
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

@media (prefers-reduced-motion: reduce) {
  .recording { animation: none; }
}

@keyframes pulse-ring {
  0% {
    transform: scale(0.8);
    opacity: 1;
  }
  100% {
    transform: scale(1.2);
    opacity: 0;
  }
}

@media (max-width: 768px) {
  .message-input {
    padding: 12px;
  }

  .input-toolbar {
    gap: 4px;
  }

  .reply-preview {
    .reply-info {
      .reply-content {
        max-width: 150px;
      }
    }
  }
}

.emoji-picker-inline {
  padding: 4px 0;
  border-bottom: 1px solid var(--border-light);
}
</style>
