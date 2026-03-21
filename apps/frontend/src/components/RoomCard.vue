<template>
  <div class="room-card">
    <div class="room-info">
      <div class="room-header">
        <h3 class="room-name">{{ props.room.roomName }}</h3>
        <div class="room-status">
          <a-tag :color="statusColor" class="status-tag">{{ statusText }}</a-tag>
        </div>
      </div>
      <p class="room-description" v-if="props.room.description">{{ props.room.description }}</p>
      <div class="room-meta">
        <div class="meta-item">
          <TeamOutlined class="meta-icon" />
          <span>{{ props.room.currentMembers }}/{{ props.room.maxMembers }}</span>
        </div>
        <div class="meta-item">
          <ClockCircleOutlined class="meta-icon" />
          <span>{{ expireText }}</span>
        </div>
        <div class="meta-item">
          <KeyOutlined class="meta-icon" />
          <span class="room-code" @click="copyRoomCode">
            {{ props.room.roomCode }}
            <CopyOutlined class="copy-icon" />
          </span>
          <span v-if="showPasswordArea" class="password-area">
            <LockOutlined class="meta-icon password-icon" />
            <span v-if="!passwordHidden" class="password-value">
              {{ currentPasswordDisplay }}
              <span v-if="props.room.passwordMode === 2 && remainingSeconds > 0" class="totp-timer">({{ remainingSeconds }}s)</span>
            </span>
            <span v-else class="password-value hidden">••••••</span>
            <span v-if="isOwner" class="password-toggle" @click.stop="togglePasswordVisibility">
              <EyeOutlined v-if="passwordHidden" />
              <EyeInvisibleOutlined v-else />
            </span>
          </span>
        </div>
      </div>
      <div class="room-owner" v-if="props.room.ownerNickname">
        <UserOutlined class="owner-icon" />
        <span>房主：{{ props.room.ownerNickname }}</span>
        <a-tag v-if="isOwner" color="gold" size="small">我的房间</a-tag>
        <a-tag v-else-if="props.room.relationshipType" :color="relationshipColor" size="small">{{ relationshipText }}</a-tag>
      </div>
    </div>
    <div class="room-actions">
      <a-button type="primary" @click="handleEnter" class="action-btn enter-btn">
        <LoginOutlined />
        进入房间
      </a-button>
      <a-dropdown :trigger="['click']" placement="bottomRight">
        <a-button class="action-btn more-btn"><MoreOutlined /></a-button>
        <template #overlay>
          <a-menu>
            <a-menu-item key="refresh" @click="handleRefresh"><ReloadOutlined /> 刷新信息</a-menu-item>
            <a-menu-item key="copy" @click="copyRoomCode"><CopyOutlined /> 复制房间码</a-menu-item>
            <a-menu-divider v-if="isOwner" />
            <a-menu-item key="password" @click="showPasswordModal" v-if="isOwner"><LockOutlined /> 设置密码</a-menu-item>
            <a-menu-item key="close" @click="handleClose" v-if="isOwner" class="danger-item"><CloseOutlined /> 关闭房间</a-menu-item>
            <a-menu-item key="leave" @click="handleLeave" v-if="!isOwner" class="danger-item"><LogoutOutlined /> 离开房间</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </div>
  <a-modal v-model:open="passwordModalVisible" title="设置房间密码" :footer="null" :width="420" centered @cancel="resetPasswordForm">
    <div class="password-modal-content">
      <a-form layout="vertical">
        <a-form-item label="密码模式">
          <a-radio-group v-model:value="passwordForm.mode" class="password-mode-group">
            <a-radio-button :value="0">不设置</a-radio-button>
            <a-radio-button :value="1">固定密码</a-radio-button>
            <a-radio-button :value="2">动态令牌</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="passwordForm.mode === 1" label="密码">
          <a-input-password v-model:value="passwordForm.rawPassword" placeholder="请输入房间密码（4-20位）" :maxlength="20" />
        </a-form-item>
        <a-form-item v-if="passwordForm.mode === 2">
          <a-alert type="info" message="动态令牌将在保存后自动生成，每5分钟更新一次" show-icon />
        </a-form-item>
        <a-form-item v-if="passwordForm.mode !== 0" label="展示密码给成员">
          <a-switch :checked="passwordForm.visible === 1" @change="(val) => passwordForm.visible = val ? 1 : 0" checked-children="展示" un-checked-children="隐藏" />
        </a-form-item>
        <div class="password-modal-actions">
          <a-button @click="resetPasswordForm">取消</a-button>
          <a-button type="primary" :loading="passwordSaving" @click="savePassword">保存</a-button>
        </div>
      </a-form>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import {
  TeamOutlined, ClockCircleOutlined, KeyOutlined, UserOutlined,
  LoginOutlined, MoreOutlined, ReloadOutlined, CopyOutlined,
  CloseOutlined, LogoutOutlined, LockOutlined, EyeOutlined, EyeInvisibleOutlined
} from '@ant-design/icons-vue'
import type { RoomInfo } from '@/types/room'
import { ROOM_STATUS } from '@/types/room'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'

dayjs.extend(relativeTime)

interface Props { room: RoomInfo }
const props = defineProps<Props>()

interface Emits {
  enter: [room: RoomInfo]
  leave: [room: RoomInfo]
  close: [room: RoomInfo]
  refresh: [room: RoomInfo]
}
const emit = defineEmits<Emits>()

const authStore = useAuthStore()
const roomStore = useRoomStore()

const passwordHidden = ref(true)
const currentPasswordDisplay = ref('')
const remainingSeconds = ref(0)
const passwordModalVisible = ref(false)
const passwordSaving = ref(false)
const passwordForm = reactive({ mode: 0, rawPassword: '', visible: 1 })
let totpTimer: ReturnType<typeof setInterval> | null = null

const isOwner = computed(() => props.room.ownerId === authStore.user?.userId)

const showPasswordArea = computed(() => {
  if (!props.room.passwordMode || props.room.passwordMode === 0) return false
  if (isOwner.value) return true
  return props.room.passwordVisible === 1
})

async function loadCurrentPassword() {
  if (!isOwner.value || !props.room.passwordMode || props.room.passwordMode === 0) return
  const data = await roomStore.getRoomCurrentPassword(props.room.roomId)
  if (data) {
    currentPasswordDisplay.value = data.currentPassword || ''
    remainingSeconds.value = data.remainingSeconds || 0
  }
}

function startTotpTimer() {
  if (totpTimer) clearInterval(totpTimer)
  if (!isOwner.value || props.room.passwordMode !== 2) return
  loadCurrentPassword()
  totpTimer = setInterval(() => {
    if (remainingSeconds.value > 0) remainingSeconds.value--
    if (remainingSeconds.value <= 0) loadCurrentPassword()
  }, 1000)
}

function stopTotpTimer() {
  if (totpTimer) { clearInterval(totpTimer); totpTimer = null }
}

onMounted(() => {
  if (isOwner.value && props.room.passwordMode === 2) startTotpTimer()
  else if (isOwner.value && props.room.passwordMode === 1) loadCurrentPassword()
})

onUnmounted(() => stopTotpTimer())

function togglePasswordVisibility() {
  passwordHidden.value = !passwordHidden.value
  if (!passwordHidden.value && !currentPasswordDisplay.value) loadCurrentPassword()
}

function showPasswordModal() {
  passwordForm.mode = props.room.passwordMode || 0
  passwordForm.rawPassword = ''
  passwordForm.visible = props.room.passwordVisible ?? 1
  passwordModalVisible.value = true
}

function resetPasswordForm() {
  passwordModalVisible.value = false
  passwordForm.mode = 0
  passwordForm.rawPassword = ''
  passwordForm.visible = 1
}

async function savePassword() {
  if (passwordForm.mode === 1 && !passwordForm.rawPassword.trim()) {
    message.warning('请输入固定密码')
    return
  }
  passwordSaving.value = true
  try {
    await roomStore.updateRoomPassword(props.room.roomId, {
      mode: passwordForm.mode,
      rawPassword: passwordForm.mode === 1 ? passwordForm.rawPassword.trim() : undefined,
      visible: passwordForm.visible
    })
    message.success('密码设置成功')
    resetPasswordForm()
    currentPasswordDisplay.value = ''
    stopTotpTimer()
    if (passwordForm.mode === 2) startTotpTimer()
    else if (passwordForm.mode === 1) loadCurrentPassword()
  } catch (e: any) {
    message.error(e?.message || '设置失败，请重试')
  } finally {
    passwordSaving.value = false
  }
}

const relationshipText = computed(() => {
  if (props.room.relationshipType === 'created') return '我的房间'
  if (props.room.relationshipType === 'joined') return '已加入'
  return '房间成员'
})
const relationshipColor = computed(() => props.room.relationshipType === 'created' ? 'gold' : 'blue')
const isExpiringSoon = computed(() => {
  const e = dayjs(props.room.expireTime)
  return e.diff(dayjs(), 'hour') <= 1 && e.isAfter(dayjs())
})
const statusColor = computed(() => props.room.status === ROOM_STATUS.CLOSED ? 'red' : isExpiringSoon.value ? 'orange' : 'green')
const statusText = computed(() => props.room.status === ROOM_STATUS.CLOSED ? '已关闭' : isExpiringSoon.value ? '即将过期' : '活跃')
const expireText = computed(() => {
  const e = dayjs(props.room.expireTime)
  return e.isBefore(dayjs()) ? '已过期' : e.fromNow()
})

function handleEnter() {
  if (props.room.status === ROOM_STATUS.CLOSED) { message.warning('房间已关闭，无法进入'); return }
  if (dayjs(props.room.expireTime).isBefore(dayjs())) { message.warning('房间已过期，无法进入'); return }
  emit('enter', props.room)
}
function handleLeave() { emit('leave', props.room) }
function handleClose() { emit('close', props.room) }
function handleRefresh() { emit('refresh', props.room) }

async function copyRoomCode() {
  try {
    await navigator.clipboard.writeText(props.room.roomCode)
    message.success('房间码已复制到剪贴板')
  } catch {
    const t = document.createElement('textarea')
    t.value = props.room.roomCode
    document.body.appendChild(t)
    t.select()
    try { document.execCommand('copy'); message.success('房间码已复制到剪贴板') }
    catch { message.error('复制失败，请手动复制房间码') }
    document.body.removeChild(t)
  }
}
</script>

<style scoped>
.room-card {
  background: white;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  transition: all 0.3s ease;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 280px;
}
.room-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  transform: translateY(-2px);
  border-color: #667eea;
}
.room-info {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.room-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.room-name { margin: 0; font-size: 18px; font-weight: 600; color: #1a202c; line-height: 1.3; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.room-status { flex-shrink: 0; }
.status-tag { font-size: 12px; border-radius: 6px; margin: 0; }
.room-description { margin: 0; font-size: 14px; color: #6b7280; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; text-overflow: ellipsis; }
.room-meta { display: flex; flex-direction: column; gap: 8px; }
.meta-item { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #6b7280; }
.meta-icon { font-size: 14px; color: #9ca3af; flex-shrink: 0; }
.room-code { font-family: 'Courier New', monospace; font-weight: 500; color: #667eea; cursor: pointer; display: flex; align-items: center; gap: 4px; padding: 2px 6px; border-radius: 4px; transition: background-color 0.2s ease; }
.room-code:hover { background-color: #f3f4f6; }
.copy-icon { font-size: 12px; opacity: 0.7; }
.room-owner { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #6b7280; margin-top: auto; }
.owner-icon { font-size: 14px; color: #9ca3af; }
.room-actions { padding: 16px 20px; background: #fafafa; border-top: 1px solid #e8e8e8; display: flex; gap: 8px; }
.action-btn { border-radius: 8px; font-weight: 500; transition: all 0.2s ease; }
.enter-btn { flex: 1; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border: none; color: white; }
.enter-btn:hover { background: linear-gradient(135deg, #5a67d8 0%, #6b46c1 100%); transform: translateY(-1px); box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3); }
.more-btn { width: 40px; padding: 0; display: flex; align-items: center; justify-content: center; border-color: #d1d5db; color: #6b7280; }
.more-btn:hover { border-color: #667eea; color: #667eea; background: rgba(102, 126, 234, 0.05); }
.password-area { display: inline-flex; align-items: center; gap: 4px; margin-left: 8px; padding: 2px 8px; background: rgba(102, 126, 234, 0.08); border-radius: 4px; border: 1px solid rgba(102, 126, 234, 0.2); }
.password-icon { font-size: 12px; color: #667eea; }
.password-value { font-family: 'Courier New', monospace; font-size: 13px; font-weight: 600; color: #667eea; letter-spacing: 1px; }
.password-value.hidden { color: #9ca3af; letter-spacing: 2px; }
.totp-timer { font-size: 11px; color: #f59e0b; margin-left: 2px; font-weight: 400; }
.password-toggle { cursor: pointer; color: #9ca3af; transition: color 0.2s; font-size: 13px; }
.password-toggle:hover { color: #667eea; }
.password-modal-content { padding: 8px 0; }
.password-mode-group { display: flex; width: 100%; }
.password-modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; padding-top: 16px; border-top: 1px solid #f0f0f0; }
:deep(.ant-dropdown-menu) { border-radius: 8px; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12); }
:deep(.ant-dropdown-menu-item) { padding: 8px 16px; font-size: 14px; }
:deep(.danger-item) { color: #dc2626 !important; }
:deep(.danger-item:hover) { background-color: #fef2f2 !important; }
@media (max-width: 480px) {
  .room-card { height: auto; min-height: 260px; }
  .room-info { padding: 16px; gap: 10px; }
  .room-name { font-size: 16px; }
  .room-header { flex-direction: column; align-items: flex-start; gap: 8px; }
  .room-meta { gap: 6px; }
  .meta-item { font-size: 12px; }
  .room-actions { padding: 12px 16px; gap: 6px; }
  .enter-btn { font-size: 14px; }
  .more-btn { width: 36px; }
}
</style>
