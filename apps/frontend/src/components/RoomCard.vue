<template>
  <div class="room-card">
    <!-- 房间信息区域 -->
    <div class="room-info">
      <div class="room-header">
        <h3 class="room-name">{{ room.roomName }}</h3>
        <div class="room-status">
          <a-tag :color="statusColor" class="status-tag">
            {{ statusText }}
          </a-tag>
        </div>
      </div>
      
      <p class="room-description" v-if="room.description">
        {{ room.description }}
      </p>
      
      <div class="room-meta">
        <div class="meta-item">
          <TeamOutlined class="meta-icon" />
          <span>{{ room.currentMembers }}/{{ room.maxMembers }}</span>
        </div>
        
        <div class="meta-item">
          <ClockCircleOutlined class="meta-icon" />
          <span>{{ expireText }}</span>
        </div>
        
        <div class="meta-item">
          <KeyOutlined class="meta-icon" />
          <span class="room-code" @click="copyRoomCode">
            {{ room.roomCode }}
            <CopyOutlined class="copy-icon" />
          </span>
        </div>
      </div>
      
      <div class="room-owner" v-if="room.ownerNickname">
        <UserOutlined class="owner-icon" />
        <span>房主：{{ room.ownerNickname }}</span>
        <a-tag v-if="isOwner" color="gold" size="small">我的房间</a-tag>
        <a-tag v-else-if="room.relationshipType" :color="relationshipColor" size="small">
          {{ relationshipText }}
        </a-tag>
      </div>
    </div>
    
    <!-- 操作按钮区域 -->
    <div class="room-actions">
      <a-button 
        type="primary" 
        @click="handleEnter"
        class="action-btn enter-btn"
      >
        <LoginOutlined />
        进入房间
      </a-button>
      
      <a-dropdown :trigger="['click']" placement="bottomRight">
        <a-button class="action-btn more-btn">
          <MoreOutlined />
        </a-button>
        
        <template #overlay>
          <a-menu>
            <a-menu-item key="refresh" @click="handleRefresh">
              <ReloadOutlined />
              刷新信息
            </a-menu-item>
            
            <a-menu-item key="copy" @click="copyRoomCode">
              <CopyOutlined />
              复制房间码
            </a-menu-item>
            
            <a-menu-divider v-if="isOwner" />
            
            <a-menu-item key="close" @click="handleClose" v-if="isOwner" class="danger-item">
              <CloseOutlined />
              关闭房间
            </a-menu-item>
            
            <a-menu-item key="leave" @click="handleLeave" v-if="!isOwner" class="danger-item">
              <LogoutOutlined />
              离开房间
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import {
  TeamOutlined,
  ClockCircleOutlined,
  KeyOutlined,
  UserOutlined,
  LoginOutlined,
  MoreOutlined,
  ReloadOutlined,
  CopyOutlined,
  CloseOutlined,
  LogoutOutlined
} from '@ant-design/icons-vue'
import type { RoomInfo } from '@/types/room'
import { ROOM_STATUS } from '@/types/room'
import { useAuthStore } from '@/stores/auth'

// 扩展dayjs
dayjs.extend(relativeTime)

// ==================== 组件属性 ====================

interface Props {
  room: RoomInfo
}

const props = defineProps<Props>()

// ==================== 组件事件 ====================

interface Emits {
  enter: [room: RoomInfo]
  leave: [room: RoomInfo]
  close: [room: RoomInfo]
  refresh: [room: RoomInfo]
}

const emit = defineEmits<Emits>()

// ==================== 组件状态 ====================

const authStore = useAuthStore()

// ==================== 计算属性 ====================

// 是否为房主
const isOwner = computed(() => {
  return props.room.ownerId === authStore.user?.userId
})

// 关系类型显示文本
const relationshipText = computed(() => {
  if (props.room.relationshipType === 'created') return '我的房间'
  if (props.room.relationshipType === 'joined') return '已加入'
  return '房间成员'
})

// 关系类型徽章颜色
const relationshipColor = computed(() => {
  if (props.room.relationshipType === 'created') return 'gold'
  return 'blue'
})

// 房间状态
const statusColor = computed(() => {
  if (props.room.status === ROOM_STATUS.CLOSED) return 'red'
  if (isExpiringSoon.value) return 'orange'
  return 'green'
})

const statusText = computed(() => {
  if (props.room.status === ROOM_STATUS.CLOSED) return '已关闭'
  if (isExpiringSoon.value) return '即将过期'
  return '活跃'
})

// 是否即将过期（1小时内）
const isExpiringSoon = computed(() => {
  const expireTime = dayjs(props.room.expireTime)
  const now = dayjs()
  return expireTime.diff(now, 'hour') <= 1 && expireTime.isAfter(now)
})

// 过期时间文本
const expireText = computed(() => {
  const expireTime = dayjs(props.room.expireTime)
  const now = dayjs()
  
  if (expireTime.isBefore(now)) {
    return '已过期'
  }
  
  return expireTime.fromNow()
})

// ==================== 事件处理 ====================

/**
 * 进入房间
 */
function handleEnter() {
  if (props.room.status === ROOM_STATUS.CLOSED) {
    message.warning('房间已关闭，无法进入')
    return
  }
  
  if (dayjs(props.room.expireTime).isBefore(dayjs())) {
    message.warning('房间已过期，无法进入')
    return
  }
  
  emit('enter', props.room)
}

/**
 * 离开房间
 */
function handleLeave() {
  emit('leave', props.room)
}

/**
 * 关闭房间
 */
function handleClose() {
  emit('close', props.room)
}

/**
 * 刷新房间信息
 */
function handleRefresh() {
  emit('refresh', props.room)
}

/**
 * 复制房间码
 */
async function copyRoomCode() {
  try {
    await navigator.clipboard.writeText(props.room.roomCode)
    message.success('房间码已复制到剪贴板')
  } catch (error) {
    // 降级方案
    const textArea = document.createElement('textarea')
    textArea.value = props.room.roomCode
    document.body.appendChild(textArea)
    textArea.select()
    
    try {
      document.execCommand('copy')
      message.success('房间码已复制到剪贴板')
    } catch (err) {
      message.error('复制失败，请手动复制房间码')
    }
    
    document.body.removeChild(textArea)
  }
}
</script>

<style scoped>
/* ==================== 卡片容器 ==================== */

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

/* ==================== 房间信息区域 ==================== */

.room-info {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
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
  color: #1a202c;
  line-height: 1.3;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room-status {
  flex-shrink: 0;
}

.status-tag {
  font-size: 12px;
  border-radius: 6px;
  margin: 0;
}

.room-description {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ==================== 房间元信息 ==================== */

.room-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6b7280;
}

.meta-icon {
  font-size: 14px;
  color: #9ca3af;
  flex-shrink: 0;
}

.room-code {
  font-family: 'Courier New', monospace;
  font-weight: 500;
  color: #667eea;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  border-radius: 4px;
  transition: background-color 0.2s ease;
}

.room-code:hover {
  background-color: #f3f4f6;
}

.copy-icon {
  font-size: 12px;
  opacity: 0.7;
}

.room-owner {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6b7280;
  margin-top: auto;
}

.owner-icon {
  font-size: 14px;
  color: #9ca3af;
}

/* ==================== 操作按钮区域 ==================== */

.room-actions {
  padding: 16px 20px;
  background: #fafafa;
  border-top: 1px solid #e8e8e8;
  display: flex;
  gap: 8px;
}

.action-btn {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.enter-btn {
  flex: 1;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  color: white;
}

.enter-btn:hover {
  background: linear-gradient(135deg, #5a67d8 0%, #6b46c1 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.more-btn {
  width: 40px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-color: #d1d5db;
  color: #6b7280;
}

.more-btn:hover {
  border-color: #667eea;
  color: #667eea;
  background: rgba(102, 126, 234, 0.05);
}

/* ==================== 下拉菜单样式 ==================== */

:deep(.ant-dropdown-menu) {
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

:deep(.ant-dropdown-menu-item) {
  padding: 8px 16px;
  font-size: 14px;
}

:deep(.danger-item) {
  color: #dc2626 !important;
}

:deep(.danger-item:hover) {
  background-color: #fef2f2 !important;
}

/* ==================== 响应式设计 ==================== */

@media (max-width: 480px) {
  .room-card {
    height: auto;
    min-height: 260px;
  }
  
  .room-info {
    padding: 16px;
    gap: 10px;
  }
  
  .room-name {
    font-size: 16px;
  }
  
  .room-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .room-meta {
    gap: 6px;
  }
  
  .meta-item {
    font-size: 12px;
  }
  
  .room-actions {
    padding: 12px 16px;
    gap: 6px;
  }
  
  .enter-btn {
    font-size: 14px;
  }
  
  .more-btn {
    width: 36px;
  }
}
</style> 