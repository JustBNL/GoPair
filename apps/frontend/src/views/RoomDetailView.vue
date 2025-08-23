<template>
  <div class="room-detail-view">
    <!-- 加载状态 -->
    <div class="loading-container" v-if="loading">
      <a-spin size="large" />
      <p>加载房间信息中...</p>
    </div>

    <!-- 房间详情内容 -->
    <div class="room-detail-content" v-else-if="currentRoom">
      <!-- 房间头部 -->
      <div class="room-header">
        <div class="header-left">
          <a-button @click="goBack" class="back-btn">
            <ArrowLeftOutlined />
            返回
          </a-button>
          <div class="room-title">
            <h1>{{ currentRoom.roomName }}</h1>
            <p v-if="currentRoom.description">{{ currentRoom.description }}</p>
          </div>
        </div>
        
        <div class="header-right">
          <a-tag :color="statusColor" class="room-status">
            {{ statusText }}
          </a-tag>
          <a-button type="primary" @click="copyRoomCode">
            <CopyOutlined />
            复制房间码
          </a-button>
        </div>
      </div>

      <!-- 房间信息卡片 -->
      <div class="room-info-cards">
        <div class="info-card">
          <div class="card-header">
            <TeamOutlined class="card-icon" />
            <h3>成员信息</h3>
          </div>
          <div class="card-content">
            <div class="member-count">
              {{ currentRoom.currentMembers }} / {{ currentRoom.maxMembers }} 人
            </div>
          </div>
        </div>

        <div class="info-card">
          <div class="card-header">
            <ClockCircleOutlined class="card-icon" />
            <h3>有效期</h3>
          </div>
          <div class="card-content">
            <div class="expire-time">
              {{ expireText }}
            </div>
          </div>
        </div>

        <div class="info-card">
          <div class="card-header">
            <KeyOutlined class="card-icon" />
            <h3>房间码</h3>
          </div>
          <div class="card-content">
            <div class="room-code" @click="copyRoomCode">
              {{ currentRoom.roomCode }}
              <CopyOutlined class="copy-icon" />
            </div>
          </div>
        </div>
      </div>

      <!-- 功能扩展区域 -->
      <div class="feature-placeholder">
        <div class="placeholder-content">
          <div class="placeholder-icon">🚧</div>
          <h3>功能开发中</h3>
          <p>房间协作功能正在开发中，敬请期待...</p>
        </div>
      </div>
    </div>

    <!-- 房间不存在 -->
    <div class="room-not-found" v-else>
      <div class="not-found-content">
        <div class="not-found-icon">🏠</div>
        <h3>房间不存在</h3>
        <p>该房间可能已被关闭或不存在</p>
        <a-button type="primary" @click="goBack">
          返回房间列表
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import {
  ArrowLeftOutlined,
  TeamOutlined,
  ClockCircleOutlined,
  KeyOutlined,
  CopyOutlined
} from '@ant-design/icons-vue'
import { useRoomStore } from '@/stores/room'
import { ROOM_STATUS } from '@/types/room'

// 扩展dayjs
dayjs.extend(relativeTime)

// ==================== 组件状态 ====================

const route = useRoute()
const router = useRouter()
const roomStore = useRoomStore()

const loading = ref(true)

// ==================== 计算属性 ====================

const currentRoom = computed(() => roomStore.currentRoom)

// 房间状态
const statusColor = computed(() => {
  if (!currentRoom.value) return 'default'
  
  if (currentRoom.value.status === ROOM_STATUS.CLOSED) return 'red'
  if (isExpired.value) return 'red'
  if (isExpiringSoon.value) return 'orange'
  return 'green'
})

const statusText = computed(() => {
  if (!currentRoom.value) return ''
  
  if (currentRoom.value.status === ROOM_STATUS.CLOSED) return '已关闭'
  if (isExpired.value) return '已过期'
  if (isExpiringSoon.value) return '即将过期'
  return '活跃中'
})

// 是否已过期
const isExpired = computed(() => {
  if (!currentRoom.value) return false
  return dayjs(currentRoom.value.expireTime).isBefore(dayjs())
})

// 是否即将过期（1小时内）
const isExpiringSoon = computed(() => {
  if (!currentRoom.value) return false
  const expireTime = dayjs(currentRoom.value.expireTime)
  const now = dayjs()
  return expireTime.diff(now, 'hour') <= 1 && expireTime.isAfter(now)
})

// 过期时间文本
const expireText = computed(() => {
  if (!currentRoom.value) return ''
  
  const expireTime = dayjs(currentRoom.value.expireTime)
  const now = dayjs()
  
  if (expireTime.isBefore(now)) {
    return '已过期'
  }
  
  return expireTime.fromNow()
})

// ==================== 事件处理 ====================

/**
 * 返回上一页
 */
function goBack() {
  router.push('/rooms')
}

/**
 * 复制房间码
 */
async function copyRoomCode() {
  if (!currentRoom.value) return
  
  try {
    await navigator.clipboard.writeText(currentRoom.value.roomCode)
    message.success('房间码已复制到剪贴板')
  } catch (error) {
    // 降级方案
    const textArea = document.createElement('textarea')
    textArea.value = currentRoom.value.roomCode
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

// ==================== 生命周期 ====================

onMounted(async () => {
  const roomId = Number(route.params.roomId)
  
  if (!roomId) {
    router.push('/rooms')
    return
  }
  
  try {
    // 如果当前没有房间信息或房间ID不匹配，尝试刷新
    if (!currentRoom.value || currentRoom.value.roomId !== roomId) {
      await roomStore.refreshRoom(roomId)
    }
    
    // 如果仍然没有房间信息，说明房间不存在
    if (!currentRoom.value) {
      // 这里可以尝试从房间列表中查找
      await roomStore.fetchUserRooms()
      const room = roomStore.roomList.find(r => r.roomId === roomId)
      if (room) {
        roomStore.setCurrentRoom(room)
      }
    }
  } catch (error) {
    console.error('加载房间信息失败:', error)
    message.error('加载房间信息失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* ==================== 页面布局 ==================== */

.room-detail-view {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

/* ==================== 加载状态 ==================== */

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
  color: white;
  gap: 16px;
}

.loading-container p {
  font-size: 16px;
  margin: 0;
}

/* ==================== 房间详情内容 ==================== */

.room-detail-content {
  max-width: 1200px;
  margin: 0 auto;
}

/* ==================== 房间头部 ==================== */

.room-header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.header-left {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  flex: 1;
}

.back-btn {
  flex-shrink: 0;
  border-radius: 8px;
  height: 40px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.room-title h1 {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 600;
  color: #1a202c;
}

.room-title p {
  margin: 0;
  font-size: 16px;
  color: #6b7280;
  line-height: 1.5;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.room-status {
  font-size: 14px;
  border-radius: 6px;
  margin: 0;
}

/* ==================== 信息卡片 ==================== */

.room-info-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.info-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  transition: transform 0.2s ease;
}

.info-card:hover {
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.card-icon {
  font-size: 20px;
  color: #667eea;
}

.card-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1a202c;
}

.card-content {
  padding-left: 32px;
}

.member-count,
.expire-time {
  font-size: 24px;
  font-weight: 600;
  color: #374151;
}

.room-code {
  font-family: 'Courier New', monospace;
  font-size: 20px;
  font-weight: 600;
  color: #667eea;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f3f4f6;
  border-radius: 8px;
  transition: background-color 0.2s ease;
  width: fit-content;
}

.room-code:hover {
  background: #e5e7eb;
}

.copy-icon {
  font-size: 16px;
  opacity: 0.7;
}

/* ==================== 功能占位区域 ==================== */

.feature-placeholder {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 60px 24px;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.placeholder-content {
  max-width: 400px;
  margin: 0 auto;
}

.placeholder-icon {
  font-size: 64px;
  margin-bottom: 20px;
}

.placeholder-content h3 {
  margin: 0 0 12px;
  font-size: 24px;
  color: #374151;
}

.placeholder-content p {
  margin: 0;
  font-size: 16px;
  color: #6b7280;
  line-height: 1.5;
}

/* ==================== 房间不存在 ==================== */

.room-not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
}

.not-found-content {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 60px 40px;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  max-width: 400px;
}

.not-found-icon {
  font-size: 64px;
  margin-bottom: 20px;
}

.not-found-content h3 {
  margin: 0 0 12px;
  font-size: 24px;
  color: #374151;
}

.not-found-content p {
  margin: 0 0 24px;
  font-size: 16px;
  color: #6b7280;
  line-height: 1.5;
}

/* ==================== 响应式设计 ==================== */

@media (max-width: 768px) {
  .room-detail-view {
    padding: 16px;
  }
  
  .room-header {
    flex-direction: column;
    gap: 16px;
    padding: 20px;
  }
  
  .header-left {
    flex-direction: column;
    gap: 12px;
    width: 100%;
  }
  
  .header-right {
    justify-content: space-between;
    width: 100%;
  }
  
  .room-title h1 {
    font-size: 24px;
  }
  
  .room-info-cards {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  
  .info-card {
    padding: 20px;
  }
  
  .member-count,
  .expire-time {
    font-size: 20px;
  }
  
  .room-code {
    font-size: 16px;
  }
  
  .feature-placeholder {
    padding: 40px 20px;
  }
  
  .placeholder-icon,
  .not-found-icon {
    font-size: 48px;
  }
  
  .not-found-content {
    padding: 40px 20px;
    margin: 0 16px;
  }
}
</style> 