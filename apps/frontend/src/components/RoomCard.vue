<template>
  <div class="room-card">
    <div class="room-info">
      <div class="room-header">
        <h3 class="room-name">{{ props.room.roomName }}</h3>
        <div class="room-status">
          <a-tag :color="statusColor" class="status-tag">{{ statusText }}</a-tag>
        </div>
      </div>
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
          <button type="button" class="room-code" @click="copyRoomCode" :aria-label="`复制房间码 ${props.room.roomCode}`">
            {{ props.room.roomCode }}
            <CopyOutlined class="copy-icon" />
          </button>
          <span v-if="showPasswordArea" class="password-area">
            <LockOutlined class="meta-icon password-icon" />
            <span v-if="!passwordHidden" class="password-value">
              {{ currentPassword }}
                <span v-if="!passwordHidden && props.room.passwordMode === 2 && remainingSeconds > 0" class="totp-timer">({{ remainingSeconds }}s)</span>
            </span>
            <span v-else class="password-value hidden">••••••</span>
            <span v-if="isOwner" class="password-toggle" @click.stop="togglePasswordVisibility" :aria-label="passwordHidden ? '显示密码' : '隐藏密码'">
              <EyeOutlined v-if="passwordHidden" />
              <EyeInvisibleOutlined v-else />
            </span>
          </span>
        </div>
      </div>
      <p class="room-description" v-if="props.room.description">{{ props.room.description }}</p>
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
            <a-menu-item key="renew" @click="handleRenew" v-if="isOwner && props.room.status === ROOM_STATUS.EXPIRED" class="renew-item"><ReloadOutlined /> 续期房间</a-menu-item>
            <a-menu-item key="reopen" @click="handleReopen" v-if="isOwner && props.room.status === ROOM_STATUS.CLOSED && !props.room.closedTime" class="reopen-item"><ReloadOutlined /> 重新开启</a-menu-item>
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
import { useRoomPassword } from '@/composables/useRoomPassword'

dayjs.extend(relativeTime)

interface Props { room: RoomInfo }
const props = defineProps<Props>()

interface Emits {
  enter: [room: RoomInfo]
  leave: [room: RoomInfo]
  close: [room: RoomInfo]
  refresh: [room: RoomInfo]
  renew: [room: RoomInfo]
  reopen: [room: RoomInfo]
}
const emit = defineEmits<Emits>()

const authStore = useAuthStore()
const roomStore = useRoomStore()

const passwordModalVisible = ref(false)
const passwordSaving = ref(false)
const passwordForm = reactive({ mode: 0, rawPassword: '', visible: 1 })

const isOwner = computed(() => props.room.ownerId === authStore.user?.userId)

const showPasswordArea = computed(() => {
  return !!props.room.passwordMode && props.room.passwordMode !== 0
})

const {
  passwordHidden,
  currentPassword,
  remainingSeconds,
  initPasswordState,
  resetPasswordState,
  togglePasswordVisibility,
  loadCurrentPassword,
} = useRoomPassword({
  roomId: () => props.room.roomId,
  passwordMode: () => props.room.passwordMode,
  passwordVisible: () => props.room.passwordVisible,
  isOwner: () => isOwner.value,
  showPasswordArea: () => showPasswordArea.value,
  loadPasswordApi: (roomId: number) =>
    roomStore.getRoomCurrentPassword(roomId).then(r => r ?? null),
})

onMounted(() => {
  initPasswordState()
})

onUnmounted(() => {
  resetPasswordState()
})

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
    resetPasswordState()
    if (passwordForm.mode === 2) {
      initPasswordState()
    } else if (passwordForm.mode === 1) {
      loadCurrentPassword()
    }
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
const statusColor = computed(() => {
  const s = props.room.status
  if (s === ROOM_STATUS.CLOSED) return 'red'
  if (s === ROOM_STATUS.EXPIRED) return 'orange'
  if (s === ROOM_STATUS.ARCHIVED) return 'default'
  return isExpiringSoon.value ? 'orange' : 'green'
})
const statusText = computed(() => {
  const s = props.room.status
  if (s === ROOM_STATUS.CLOSED) return '已关闭'
  if (s === ROOM_STATUS.EXPIRED) return '已过期'
  if (s === ROOM_STATUS.ARCHIVED) return '已归档'
  return isExpiringSoon.value ? '即将过期' : '活跃'
})
const expireText = computed(() => {
  const e = dayjs(props.room.expireTime)
  return e.isBefore(dayjs()) ? '已过期' : e.fromNow()
})

function handleEnter() {
  if (props.room.status === ROOM_STATUS.ARCHIVED) { message.warning('房间已归档，无法进入'); return }
  // CLOSED 状态：非房主禁止进入，房主允许进入（可重新开启）
  if (props.room.status === ROOM_STATUS.CLOSED && !isOwner.value) {
    message.warning('房间已关闭，无法进入')
    return
  }
  emit('enter', props.room)
}
function handleLeave() { emit('leave', props.room) }
function handleClose() { emit('close', props.room) }
function handleRefresh() { emit('refresh', props.room) }
function handleRenew() { emit('renew', props.room) }
function handleReopen() { emit('reopen', props.room) }

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
/* ==================== 卡片主体 ==================== */
.room-card {
  background: var(--surface-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-default);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  /* 移除了固定 height:280px — 内容决定高度，避免文本溢出 */
  min-height: 260px;
}
.room-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--brand-primary);
}

/* ==================== 信息区域 ==================== */
.room-info {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.room-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.room-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.3;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.room-status { flex-shrink: 0; }
.status-tag { font-size: 12px; border-radius: var(--radius-sm); margin: 0; }
.room-description {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.room-meta { display: flex; flex-direction: column; gap: 8px; }
.meta-item { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-secondary); }
.meta-icon { font-size: 14px; color: var(--text-muted); flex-shrink: 0; }

/* 房间码（触控目标 >= 44px） */
.room-code {
  font-family: 'Courier New', monospace;
  font-weight: 500;
  color: var(--brand-primary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px; /* 扩大触控区域 */
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  outline: none;
  transition: background-color 0.15s ease, color 0.15s ease;
  min-height: 44px; /* 触控最小高度 */
  line-height: 1;
}
.room-code:hover {
  background-color: rgba(var(--brand-primary-rgb), 0.08);
  color: var(--brand-primary-hover);
}
.room-code:active {
  background-color: rgba(var(--brand-primary-rgb), 0.14);
}
.copy-icon { font-size: 12px; opacity: 0.7; color: var(--brand-primary); }

.room-owner {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: auto;
  flex-wrap: wrap;
}
.owner-icon { font-size: 14px; color: var(--text-muted); }

/* ==================== 操作区 ==================== */
.room-actions {
  padding: 16px 20px;
  background: var(--surface-bg);
  border-top: 1px solid var(--border-light);
  display: flex;
  gap: 8px;
}
.action-btn { border-radius: var(--radius-md); font-weight: 500; }
.enter-btn {
  flex: 1;
  background: var(--brand-primary);
  border: none;
  color: var(--text-on-primary);
  min-height: 44px;
}
.enter-btn:hover {
  background: var(--brand-primary-hover);
  box-shadow: 0 4px 12px rgba(var(--brand-primary-rgb), 0.35);
}
.more-btn {
  width: 44px; /* 触控目标 */
  height: 44px; /* 触控目标 */
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-color: var(--border-default);
  color: var(--text-secondary);
  flex-shrink: 0;
}
.more-btn:hover {
  border-color: var(--brand-primary);
  color: var(--brand-primary);
  background: var(--brand-primary-light);
}

/* ==================== 密码区 ==================== */
.password-area {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
  padding: 4px 8px;
  background: var(--brand-primary-light);
  border-radius: var(--radius-sm);
  border: 1px solid rgba(var(--brand-primary-rgb), 0.15);
}
.password-icon { font-size: 12px; color: var(--brand-primary); }
.password-value {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  font-weight: 600;
  color: var(--brand-primary);
  letter-spacing: 1px;
}
.password-value.hidden { color: var(--text-muted); letter-spacing: 2px; }
.totp-timer { font-size: 11px; color: var(--color-warning); margin-left: 2px; font-weight: 400; }

/* 密码切换 — 改为真实 button，48px 触控目标 */
.password-toggle {
  cursor: pointer;
  color: var(--text-muted);
  transition: color 0.15s;
  font-size: 13px;
  background: none;
  border: none;
  padding: 6px; /* 扩大触控区至 44px+ */
  margin: -6px; /* 补偿 padding，保持视觉位置 */
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  min-height: 44px;
  line-height: 1;
}
.password-toggle:hover { color: var(--brand-primary); }

/* ==================== 密码弹窗 ==================== */
.password-modal-content { padding: 8px 0; }
.password-mode-group { display: flex; width: 100%; }
.password-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

/* ==================== ant-design 覆盖 ==================== */
:deep(.ant-dropdown-menu) {
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
}
:deep(.ant-dropdown-menu-item) {
  padding: 10px 16px; /* 触控友好行高 */
  font-size: 14px;
  min-height: 44px; /* 触控目标 */
  display: flex;
  align-items: center;
}
:deep(.danger-item) { color: var(--color-error) !important; }
:deep(.danger-item:hover) { background-color: var(--danger-hover-bg) !important; }

/* ==================== 响应式断点 ==================== */

/* 手机竖屏 320px–479px */
@media (max-width: 479px) {
  .room-info { padding: 14px; gap: 8px; }
  .room-name { font-size: 15px; }
  .room-header { flex-direction: column; align-items: flex-start; gap: 6px; }
  .room-description { -webkit-line-clamp: 1; } /* 更少空间只显示一行 */
  .meta-item { font-size: 12px; }
  .room-owner { font-size: 12px; }
  .room-actions { padding: 10px 14px; }
}

/* 手机横屏 / 超小平板 480px–639px */
@media (min-width: 480px) and (max-width: 639px) {
  .room-info { padding: 16px; gap: 10px; }
  .room-name { font-size: 16px; }
  .room-header { flex-wrap: wrap; }
}

/* 平板竖屏 640px–1023px（主要移动端目标） */
@media (min-width: 640px) and (max-width: 1023px) {
  .room-card {
    /* 平板上允许更宽的卡片，适当限制宽度避免拉得太长 */
    max-width: 100%;
  }
}

/* 减少动画偏好 */
@media (prefers-reduced-motion: reduce) {
  .room-card,
  .action-btn,
  .room-code,
  .password-toggle {
    transition: none;
  }
}
</style>
