<template>
  <a-modal
    :open="visible"
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
            <div class="blocked-hint" v-if="blockedHintText">
              <WarningOutlined class="blocked-hint-icon" />
              <span>{{ blockedHintText }}</span>
            </div>
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

      <!-- 房间密码输入（有密码时显示） -->
      <a-form-item
        v-if="isRoomJoinable && roomPreview.passwordMode && roomPreview.passwordMode !== 0"
        name="password"
        :label="roomPreview.passwordMode === 2 ? '动态令牌' : '房间密码'"
      >
        <a-input-password
          v-model:value="formData.password"
          :placeholder="roomPreview.passwordMode === 2 ? '请输入6位动态令牌' : '请输入房间密码'"
          size="large"
          :maxlength="roomPreview.passwordMode === 2 ? 6 : 20"
        />
        <div class="input-hint password-hint">
          <span v-if="roomPreview.passwordMode === 1">此房间设有固定密码，请向房主获取</span>
          <span v-else>此房间使用动态令牌，每5分钟更新，请向房主获取当前令牌</span>
        </div>
      </a-form-item>
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

interface Props { visible: boolean }
const props = defineProps<Props>()

interface Emits { 'update:visible': [visible: boolean]; success: [room: RoomInfo] }
const emit = defineEmits<Emits>()

const roomStore = useRoomStore()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()

const formData = reactive<JoinRoomFormData>({ roomCode: '', password: '' })

const roomPreview = ref<RoomInfo | null>(null)
const searchLoading = ref(false)
const showNotFound = ref(false)

let searchTimer: number | null = null

const formRules = {
  roomCode: [
    { required: true, message: '请输入房间码' },
    { min: 4, max: 20, message: '房间码长度为4-20个字符' },
    { pattern: /^[A-Za-z0-9]+$/, message: '房间码只能包含字母和数字' }
  ],
  password: [
    { max: 20, message: '密码不能超过20个字符' }
  ]
}

const previewStatusColor = computed(() => {
  if (!roomPreview.value) return 'default'
  const s = roomPreview.value.status
  if (s === ROOM_STATUS.CLOSED) return 'red'
  if (s === ROOM_STATUS.EXPIRED) return 'orange'
  if (s === ROOM_STATUS.ARCHIVED) return 'default'
  if (s === ROOM_STATUS.DISABLED) return 'red'
  if (isRoomFull.value) return 'orange'
  if (isRoomExpiringSoon.value) return 'orange'
  return 'green'
})

const previewStatusText = computed(() => {
  if (!roomPreview.value) return ''
  const s = roomPreview.value.status
  if (s === ROOM_STATUS.CLOSED) return '已关闭'
  if (s === ROOM_STATUS.EXPIRED) return '已过期（只读）'
  if (s === ROOM_STATUS.ARCHIVED) return '已归档'
  if (s === ROOM_STATUS.DISABLED) return '已禁用'
  if (isRoomFull.value) return '房间已满'
  if (isRoomExpiringSoon.value) return '即将过期'
  return '可加入'
})

const blockedHintText = computed(() => {
  if (!roomPreview.value || isRoomJoinable.value) return ''
  const s = roomPreview.value.status
  if (s === ROOM_STATUS.CLOSED) return '该房间已关闭，无法加入'
  if (s === ROOM_STATUS.EXPIRED) return '该房间已过期，无法加入或操作'
  if (s === ROOM_STATUS.ARCHIVED) return '该房间已归档，无法加入'
  if (s === ROOM_STATUS.DISABLED) return '该房间已被管理员禁用，无法操作'
  if (isRoomFull.value) return '该房间已达人数上限，请联系房主扩容'
  return ''
})

const isRoomJoinable = computed(() => {
  if (!roomPreview.value) return false
  return roomPreview.value.status === ROOM_STATUS.ACTIVE && !isRoomExpired.value && !isRoomFull.value && !isRoomDisabled.value
})

const isRoomExpired = computed(() => {
  if (!roomPreview.value) return false
  return dayjs(roomPreview.value.expireTime).isBefore(dayjs())
})

const isRoomFull = computed(() => {
  if (!roomPreview.value) return false
  return roomPreview.value.currentMembers >= roomPreview.value.maxMembers
})

const isRoomDisabled = computed(() => {
  if (!roomPreview.value) return false
  return roomPreview.value.status === ROOM_STATUS.DISABLED
})

const isRoomExpiringSoon = computed(() => {
  if (!roomPreview.value) return false
  const expireTime = dayjs(roomPreview.value.expireTime)
  const now = dayjs()
  return expireTime.diff(now, 'hour') <= 1 && expireTime.isAfter(now)
})

watch(() => props.visible, (newVal) => { if (newVal) resetForm() })

function handleRoomCodeInput() {
  if (searchTimer) { clearTimeout(searchTimer) }
  roomPreview.value = null
  showNotFound.value = false
  if (formData.roomCode.length >= 4) {
    searchTimer = setTimeout(() => { searchRoom() }, 500)
  }
}

function handlePaste(_event: ClipboardEvent) {
  setTimeout(() => { handleRoomCodeInput() }, 10)
}

async function searchRoom() {
  if (!formData.roomCode.trim()) return
  searchLoading.value = true
  showNotFound.value = false
  try {
    const room = await roomStore.getRoomByCode(formData.roomCode.trim())
    if (room) {
      roomPreview.value = room
    }
  } catch (error) {
    roomPreview.value = null
    showNotFound.value = true
  } finally {
    searchLoading.value = false
  }
}

async function handleSubmit(_values: JoinRoomFormData) {
  if (!roomPreview.value) { message.warning('请先输入有效的房间码'); return }

  if (!isRoomJoinable.value) { message.warning('该房间当前无法加入'); return }
  // 保存预览，用于成功后 emit
  const targetRoom = roomPreview.value
  const currentRoomCode = formData.roomCode.trim()
  try {
    // 异步加入：请求受理 token 并轮询结果
    const token = await roomStore.requestJoinRoomAsync({
      roomCode: currentRoomCode,
      password: formData.password?.trim() || undefined
    })
    if (!token) return
    // 退避轮询（最多30次，约6~7s）
    for (let i = 0; i < 30; i++) {
      const status = await roomStore.prefetchAfterJoin(token, currentRoomCode)
      if (status === 'JOINED') {
        // queryJoinResult 内部已调用 fetchUserRooms，从刷新后列表找房间
        const joinedRoom = roomStore.roomList.find(r => r.roomCode === currentRoomCode) ?? targetRoom
        resetForm()
        emit('update:visible', false)
        emit('success', joinedRoom)
        return
      }
      if (status === 'FAILED') {
        return
      }
      await new Promise(r => setTimeout(r, 200 * Math.pow(2, i / 3)))
    }
    // 超时未得到明确结果
    message.info('加入处理中，请稍后在"我的房间"查看')
    resetForm()
    emit('update:visible', false)
  } catch (error: any) {
    const errorMessage = error?.message || '加入房间失败，请重试'
    message.error(errorMessage)
  }
}

function handleSubmitFailed(_errorInfo: any) {
  message.warning('请检查表单信息')
}

function handleCancel() {
  emit('update:visible', false)
}

function resetForm() {
  formData.roomCode = ''
  formData.password = ''
  roomPreview.value = null
  searchLoading.value = false
  showNotFound.value = false
  if (searchTimer) { clearTimeout(searchTimer); searchTimer = null }
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
  background: var(--brand-primary);
  color: var(--text-on-primary);
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
  color: var(--text-primary);
}

:deep(.join-form .ant-input),
:deep(.join-form .ant-select-selector) {
  border-radius: 8px;
}

:deep(.join-form .ant-input:focus),
:deep(.join-form .ant-input-focused) {
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 2px var(--brand-primary-light);
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
  color: var(--text-muted);
}

/* ==================== 房间预览 ==================== */

.room-preview {
  background: var(--surface-bg);
  border: 1px solid var(--border-default);
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
  color: var(--text-primary);
}

.preview-icon {
  font-size: 16px;
  color: var(--brand-primary);
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
  color: var(--text-primary);
}

.room-description {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--text-secondary);
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
  color: var(--text-secondary);
}

.meta-icon {
  font-size: 14px;
  color: var(--text-muted);
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
  background: var(--brand-accent-light);
  border: 1px solid rgba(var(--brand-accent-rgb), 0.2);
  border-radius: 8px;
  margin: 16px 0;
  font-size: 14px;
  color: var(--brand-accent);
}

.room-not-found {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(var(--color-error-rgb), 0.06);
  border: 1px solid rgba(var(--color-error-rgb), 0.2);
  border-radius: 8px;
  margin: 16px 0;
  font-size: 14px;
  color: var(--color-error);
}

.warning-icon {
  font-size: 16px;
}

.blocked-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 10px;
  background: rgba(var(--color-error-rgb), 0.06);
  border: 1px solid rgba(var(--color-error-rgb), 0.2);
  border-radius: 6px;
  font-size: 12px;
  color: var(--color-error);
  line-height: 1.4;
}

.blocked-hint-icon {
  font-size: 12px;
  margin-top: 1px;
  flex-shrink: 0;
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
  border-color: var(--border-default);
  color: var(--text-secondary);
}

.cancel-btn:hover {
  border-color: var(--text-muted);
  color: var(--text-primary);
}

.submit-btn {
  flex: 2;
  height: 44px;
  border-radius: 8px;
  font-weight: 500;
  background: var(--brand-primary);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.submit-btn:hover:not(:disabled) {
  background: var(--brand-primary-hover);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(var(--brand-primary-rgb), 0.4);
}

.submit-btn:disabled {
  background: var(--border-light);
  color: var(--text-muted);
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