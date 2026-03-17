<template>
  <div class="message-input">
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
    <div class="input-toolbar">
      <!-- 表情按钮 -->
      <a-tooltip title="表情">
        <a-button type="text" size="small">
          <smile-outlined />
        </a-button>
      </a-tooltip>

      <!-- 文件上传 -->
      <a-tooltip title="发送文件">
        <a-upload
          :show-upload-list="false"
          :before-upload="handleFileUpload"
          accept="*/*"
        >
          <a-button type="text" size="small">
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
        >
          <a-button type="text" size="small">
            <picture-outlined />
          </a-button>
        </a-upload>
      </a-tooltip>

      <!-- 语音录制 -->
      <a-tooltip title="语音消息">
        <a-button 
          type="text" 
          size="small"
          :class="{ 'recording': isRecording }"
          @mousedown="startRecording"
          @mouseup="stopRecording"
          @mouseleave="stopRecording"
        >
          <audio-outlined />
        </a-button>
      </a-tooltip>
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
      centered
    >
      <div class="recording-modal">
        <div class="recording-animation">
          <div class="pulse-circle"></div>
          <audio-outlined class="audio-icon" />
        </div>
        <p class="recording-tip">松开发送，按住说话</p>
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

// 语音录制
const isRecording = ref(false)
const showRecordingModal = ref(false)
const recordingDuration = ref(0)
const recordingTimer = ref<number | null>(null)
const mediaRecorder = ref<MediaRecorder | null>(null)
const audioChunks = ref<Blob[]>([])

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
      // Enter 发送
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
 * 发送文本消息
 */
const sendMessage = async () => {
  if (!canSend.value) return

  const content = inputText.value.trim()
  if (!content) return

  try {
    sending.value = true

    emit('send-message', {
      content,
      messageType: MessageType.TEXT,
      replyToId: props.replyMessage?.messageId
    })

    // 清空输入
    inputText.value = ''
    
    // 取消回复
    if (props.replyMessage) {
      cancelReply()
    }

    // 重新聚焦输入框
    await nextTick()
    textareaRef.value?.focus()

  } catch (error) {
    antMessage.error('发送失败')
  } finally {
    sending.value = false
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
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    
    mediaRecorder.value = new MediaRecorder(stream)
    audioChunks.value = []
    recordingDuration.value = 0
    
    mediaRecorder.value.ondataavailable = (event) => {
      audioChunks.value.push(event.data)
    }
    
    mediaRecorder.value.onstop = () => {
      const audioBlob = new Blob(audioChunks.value, { type: 'audio/wav' })
      
      // TODO: 上传语音文件
      const fileUrl = URL.createObjectURL(audioBlob)
      
      emit('send-message', {
        messageType: MessageType.VOICE,
        fileUrl,
        fileName: `voice_${Date.now()}.wav`,
        fileSize: audioBlob.size,
        replyToId: props.replyMessage?.messageId
      })

      // 取消回复
      if (props.replyMessage) {
        cancelReply()
      }
      
      // 停止所有轨道
      stream.getTracks().forEach(track => track.stop())
    }
    
    mediaRecorder.value.start()
    isRecording.value = true
    showRecordingModal.value = true
    
    // 开始计时
    recordingTimer.value = window.setInterval(() => {
      recordingDuration.value++
    }, 1000)
    
  } catch (error) {
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
  background: white;
  border-top: 1px solid #f0f0f0;
  padding: 16px;
}

.reply-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  margin-bottom: 12px;
  background-color: #f5f5f5;
  border-left: 3px solid #1890ff;
  border-radius: 4px;

  .reply-info {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;

    .reply-label {
      color: #1890ff;
      font-weight: 500;
    }

    .reply-target {
      color: #595959;
      font-weight: 500;
    }

    .reply-content {
      color: #8c8c8c;
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
  gap: 8px;
  margin-bottom: 12px;
  padding: 0 4px;

  .recording {
    color: #ff4d4f;
    animation: pulse 1s infinite;
  }
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
    color: #8c8c8c;
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
      border: 2px solid #ff4d4f;
      border-radius: 50%;
      animation: pulse-ring 2s infinite;
    }

    .audio-icon {
      font-size: 32px;
      color: #ff4d4f;
      z-index: 1;
    }
  }

  .recording-tip {
    margin: 16px 0 8px;
    color: #595959;
  }

  .recording-duration {
    font-size: 18px;
    font-weight: 500;
    color: #ff4d4f;
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
</style>
