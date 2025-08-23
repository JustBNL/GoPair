<template>
  <a-modal
    :visible="visible"
    :title="null"
    :footer="null"
    :maskClosable="false"
    :width="480"
    centered
    @cancel="handleCancel"
    class="join-room-modal"
  >
    <!-- 模态框头部 -->
    <div class="modal-header">
      <div class="header-icon">
        <TeamOutlined />
      </div>
      <h2 class="modal-title">加入房间</h2>
      <p class="modal-subtitle">输入房间码加入协作房间</p>
    </div>

    <!-- 加入房间表单 -->
    <a-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      layout="vertical"
      @finish="handleSubmit"
      @finishFailed="handleSubmitFailed"
      class="join-form"
    >
      <!-- 房间码输入 -->
      <a-form-item name="roomCode" label="房间码">
        <a-input
          v-model:value="formData.roomCode"
          placeholder="请输入8位房间码"
          size="large"
          :maxlength="20"
          :prefix="h(KeyOutlined)"
          @input="handleRoomCodeInput"
          @paste="handlePaste"
          class="room-code-input"
        />
        <div class="input-hint">
          <InfoCircleOutlined />
          <span>房间码通常为8位字符，由房主创建时生成</span>
        </div>
      </a-form-item>

      <!-- 房间信息预览 -->
      <div class="room-preview" v-if="roomPreview">
        <div class="preview-header">
          <HomeOutlined class="preview-icon" />
          <span>房间预览</span>
        </div>
        
        <div class="preview-content">
          <div class="room-info">
            <h4 class="room-name">{{ roomPreview.roomName }}</h4>
            <p class="room-description" v-if="roomPreview.description">
              {{ roomPreview.description }}
            </p>
            <div class="room-meta">
              <div class="meta-item">
                <TeamOutlined class="meta-icon" />
                <span>{{ roomPreview.currentMembers }}/{{ roomPreview.maxMembers }} 成员</span>
              </div>
              <div class="meta-item">
                <UserOutlined class="meta-icon" />
                <span>房主：{{ roomPreview.ownerNickname || '未知' }}</span>
              </div>
            </div>
          </div>
          
          <div class="room-status">
            <a-tag :color="previewStatusColor" class="status-tag">
              {{ previewStatusText }}
            </a-tag>
          </div>
        </div>
      </div>

      <!-- 查找房间加载状态 -->
      <div class="search-loading" v-if="searchLoading">
        <a-spin size="small" />
        <span>正在查找房间...</span>
      </div>

      <!-- 房间不存在提示 -->
      <div class="room-not-found" v-if="showNotFound">
        <WarningOutlined class="warning-icon" />
        <span>房间不存在或已过期</span>
      </div>

      <!-- 显示名称输入 -->
      <a-form-item 
        name="displayName" 
        label="房间内显示名称"
        v-if="roomPreview"
      >
        <a-input
          v-model:value="formData.displayName"
          placeholder="请输入您在房间内的显示名称"
          size="large"
          :maxlength="20"
          showCount
          :prefix="h(UserOutlined)"
        />
        <div class="input-hint">
          <span>其他成员将看到此名称，可与您的账户昵称不同</span>
        </div>
      </a-form-item>

      <!-- 操作按钮 -->
      <div class="form-actions">
        <a-button 
          size="large" 
          @click="handleCancel"
          class="cancel-btn"
        >
          取消
        </a-button>
        <a-button
          type="primary"
          size="large"
          html-type="submit"
          :loading="roomStore.joinLoading"
          :disabled="!roomPreview || !isRoomJoinable"
          class="submit-btn"
        >
          <LoginOutlined v-if="!roomStore.joinLoading" />
          {{ roomStore.joinLoading ? '加入中...' : '加入房间' }}
        </a-button>
      </div>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, h } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  TeamOutlined,
  KeyOutlined,
  InfoCircleOutlined,
  HomeOutlined,
  UserOutlined,
  WarningOutlined,
  LoginOutlined
} from '@ant-design/icons-vue'
import { useRoomStore } from '@/stores/room'
import { useAuthStore } from '@/stores/auth'
import type { JoinRoomFormData, RoomInfo } from '@/types/room'
import { ROOM_STATUS } from '@/types/room'

// ==================== 组件属性 ====================

interface Props {
  visible: boolean
}

const props = defineProps<Props>()

// ==================== 组件事件 ====================

interface Emits {
  'update:visible': [visible: boolean]
  success: [room: RoomInfo]
}

const emit = defineEmits<Emits>()

// ==================== 组件状态 ====================

const roomStore = useRoomStore()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<JoinRoomFormData>({
  roomCode: '',
  displayName: ''
})

// 房间预览数据
const roomPreview = ref<RoomInfo | null>(null)
const searchLoading = ref(false)
const showNotFound = ref(false)

// 防抖定时器
let searchTimer: number | null = null

// 表单验证规则
const formRules = {
  roomCode: [
    { required: true, message: '请输入房间码' },
    { min: 4, max: 20, message: '房间码长度为4-20个字符' },
    { pattern: /^[A-Za-z0-9]+$/, message: '房间码只能包含字母和数字' }
  ],
  displayName: [
    { required: true, message: '请输入显示名称' },
    { min: 1, max: 20, message: '显示名称长度为1-20个字符' },
    { pattern: /^[^<>'"&]*$/, message: '显示名称不能包含特殊字符' }
  ]
}

// ==================== 计算属性 ====================

// 房间状态
const previewStatusColor = computed(() => {
  if (!roomPreview.value) return 'default'
  
  if (roomPreview.value.status === ROOM_STATUS.CLOSED) return 'red'
  if (isRoomExpired.value) return 'red'
  if (isRoomFull.value) return 'orange'
  if (isRoomExpiringSoon.value) return 'orange'
  return 'green'
})

const previewStatusText = computed(() => {
  if (!roomPreview.value) return ''
  
  if (roomPreview.value.status === ROOM_STATUS.CLOSED) return '已关闭'
  if (isRoomExpired.value) return '已过期'
  if (isRoomFull.value) return '房间已满'
  if (isRoomExpiringSoon.value) return '即将过期'
  return '可加入'
})

// 房间是否可加入
const isRoomJoinable = computed(() => {
  if (!roomPreview.value) return false
  
  return roomPreview.value.status === ROOM_STATUS.ACTIVE &&
         !isRoomExpired.value &&
         !isRoomFull.value
})

// 房间是否已过期
const isRoomExpired = computed(() => {
  if (!roomPreview.value) return false
  return dayjs(roomPreview.value.expireTime).isBefore(dayjs())
})

// 房间是否已满
const isRoomFull = computed(() => {
  if (!roomPreview.value) return false
  return roomPreview.value.currentMembers >= roomPreview.value.maxMembers
})

// 房间是否即将过期（1小时内）
const isRoomExpiringSoon = computed(() => {
  if (!roomPreview.value) return false
  const expireTime = dayjs(roomPreview.value.expireTime)
  const now = dayjs()
  return expireTime.diff(now, 'hour') <= 1 && expireTime.isAfter(now)
})

// ==================== 监听器 ====================

// 监听可见性变化，重置表单
watch(() => props.visible, (newVal) => {
  if (newVal) {
    resetForm()
  }
})

// ==================== 事件处理 ====================

/**
 * 房间码输入处理
 */
function handleRoomCodeInput() {
  // 清除之前的搜索
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  
  // 重置状态
  roomPreview.value = null
  showNotFound.value = false
  
  // 如果房间码长度足够，开始搜索
  if (formData.roomCode.length >= 4) {
    searchTimer = setTimeout(() => {
      searchRoom()
    }, 500) // 500ms 防抖
  }
}

/**
 * 粘贴处理
 */
function handlePaste(event: ClipboardEvent) {
  // 延迟处理，确保粘贴内容已被设置
  setTimeout(() => {
    handleRoomCodeInput()
  }, 10)
}

/**
 * 搜索房间
 */
async function searchRoom() {
  if (!formData.roomCode.trim()) return
  
  searchLoading.value = true
  showNotFound.value = false
  
  try {
    const room = await roomStore.getRoomByCode(formData.roomCode.trim())
    if (room) {
      roomPreview.value = room
      
      // 自动填充显示名称（如果未填写）
      if (!formData.displayName.trim()) {
        formData.displayName = authStore.user?.nickname || ''
      }
    }
  } catch (error) {
    console.warn('查找房间失败:', error)
    roomPreview.value = null
    showNotFound.value = true
  } finally {
    searchLoading.value = false
  }
}

/**
 * 提交表单
 */
async function handleSubmit(values: JoinRoomFormData) {
  if (!roomPreview.value) {
    message.warning('请先输入有效的房间码')
    return
  }
  
  if (!isRoomJoinable.value) {
    message.warning('该房间当前无法加入')
    return
  }
  
  try {
    const room = await roomStore.joinRoom({
      roomCode: values.roomCode.trim(),
      displayName: values.displayName.trim()
    })
    
    if (room) {
      emit('success', room)
      resetForm()
      message.success('成功加入房间！')
    }
  } catch (error: any) {
    console.error('加入房间失败:', error)
    
    // 根据错误类型显示不同的提示
    const errorMessage = error?.message || '加入房间失败，请重试'
    message.error(errorMessage)
  }
}

/**
 * 提交失败处理
 */
function handleSubmitFailed(errorInfo: any) {
  console.warn('表单验证失败:', errorInfo)
  message.warning('请检查表单信息')
}

/**
 * 取消操作
 */
function handleCancel() {
  emit('update:visible', false)
}

/**
 * 重置表单
 */
function resetForm() {
  formData.roomCode = ''
  formData.displayName = ''
  roomPreview.value = null
  searchLoading.value = false
  showNotFound.value = false
  
  // 清除搜索定时器
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  
  // 清除表单验证状态
  formRef.value?.clearValidate()
}
</script>

<style scoped>
/* ==================== 模态框样式 ==================== */

:deep(.join-room-modal .ant-modal-content) {
  border-radius: 16px;
  overflow: hidden;
}

:deep(.join-room-modal .ant-modal-body) {
  padding: 0;
}

/* ==================== 模态框头部 ==================== */

.modal-header {
  text-align: center;
  padding: 32px 32px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.header-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.9;
}

.modal-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px;
}

.modal-subtitle {
  font-size: 14px;
  margin: 0;
  opacity: 0.8;
}

/* ==================== 表单样式 ==================== */

.join-form {
  padding: 32px;
}

:deep(.join-form .ant-form-item-label > label) {
  font-weight: 500;
  color: #1a202c;
}

:deep(.join-form .ant-input),
:deep(.join-form .ant-select-selector) {
  border-radius: 8px;
}

:deep(.join-form .ant-input:focus),
:deep(.join-form .ant-input-focused) {
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

/* ==================== 房间码输入 ==================== */

.room-code-input {
  font-family: 'Courier New', monospace;
  font-size: 16px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.input-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: #6b7280;
}

/* ==================== 房间预览 ==================== */

.room-preview {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px;
  margin: 16px 0;
}

.preview-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  font-weight: 500;
  color: #374151;
}

.preview-icon {
  font-size: 16px;
  color: #667eea;
}

.preview-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.room-info {
  flex: 1;
}

.room-name {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: #1a202c;
}

.room-description {
  margin: 0 0 12px;
  font-size: 14px;
  color: #6b7280;
  line-height: 1.4;
}

.room-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #6b7280;
}

.meta-icon {
  font-size: 14px;
  color: #9ca3af;
}

.room-status {
  flex-shrink: 0;
}

.status-tag {
  font-size: 12px;
  border-radius: 6px;
  margin: 0;
}

/* ==================== 搜索状态 ==================== */

.search-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #f0f9ff;
  border: 1px solid #e0f2fe;
  border-radius: 8px;
  margin: 16px 0;
  font-size: 14px;
  color: #0369a1;
}

.room-not-found {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  margin: 16px 0;
  font-size: 14px;
  color: #dc2626;
}

.warning-icon {
  font-size: 16px;
}

/* ==================== 操作按钮 ==================== */

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 32px;
}

.cancel-btn {
  flex: 1;
  height: 44px;
  border-radius: 8px;
  font-weight: 500;
  border-color: #d1d5db;
  color: #6b7280;
}

.cancel-btn:hover {
  border-color: #9ca3af;
  color: #374151;
}

.submit-btn {
  flex: 2;
  height: 44px;
  border-radius: 8px;
  font-weight: 500;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.submit-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #5a67d8 0%, #6b46c1 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.submit-btn:disabled {
  background: #e5e7eb;
  color: #9ca3af;
  transform: none;
  box-shadow: none;
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
}

/* ==================== 响应式设计 ==================== */

@media (max-width: 576px) {
  :deep(.join-room-modal) {
    margin: 16px;
    max-width: calc(100vw - 32px);
  }
  
  .modal-header {
    padding: 24px 20px 20px;
  }
  
  .header-icon {
    font-size: 40px;
    margin-bottom: 12px;
  }
  
  .modal-title {
    font-size: 20px;
  }
  
  .modal-subtitle {
    font-size: 13px;
  }
  
  .join-form {
    padding: 24px 20px;
  }
  
  .room-preview {
    padding: 16px;
  }
  
  .preview-content {
    flex-direction: column;
    gap: 12px;
  }
  
  .room-status {
    align-self: flex-start;
  }
  
  .form-actions {
    flex-direction: column;
    gap: 8px;
  }
  
  .cancel-btn,
  .submit-btn {
    flex: none;
  }
}
</style> 