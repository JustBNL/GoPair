<template>
  <div class="rooms-view">
    <!-- 页面头部 -->
    <div class="rooms-header">
      <div class="header-content">
        <div class="brand-section">
          <div class="logo-icon">🎮</div>
          <h1 class="page-title">房间管理</h1>
        </div>
        <div class="user-section">
          <span class="welcome-text">欢迎，{{ authStore.currentNickname }}</span>
          <a-button type="text" @click="handleLogout" class="logout-btn">
            <LogoutOutlined />
            退出登录
          </a-button>
        </div>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="rooms-content">
      <!-- 操作区域 -->
      <div class="rooms-actions">
        <div class="actions-container">
          <a-button 
            type="primary" 
            size="large" 
            @click="showCreateModal"
            :loading="roomStore.createLoading"
            class="action-btn create-btn"
          >
            <PlusOutlined />
            创建房间
          </a-button>
          
          <a-button 
            size="large" 
            @click="showJoinModal"
            :loading="roomStore.joinLoading"
            class="action-btn join-btn"
          >
            <TeamOutlined />
            加入房间
          </a-button>
        </div>
      </div>

      <!-- 房间列表区域 -->
      <div class="rooms-list-section">
        <div class="list-header">
          <h2>我的房间</h2>
          <a-button 
            type="text" 
            @click="refreshRooms"
            :loading="roomStore.loading"
            class="refresh-btn"
          >
            <ReloadOutlined />
            刷新
          </a-button>
        </div>

        <!-- 房间列表 -->
        <div class="rooms-list" v-if="roomStore.hasRooms">
          <RoomCard 
            v-for="room in roomStore.roomList" 
            :key="room.roomId" 
            :room="room"
            @enter="handleEnterRoom"
            @leave="handleLeaveRoom"
            @close="handleCloseRoom"
          />
        </div>

        <!-- 空状态 -->
        <div class="empty-state" v-else-if="!roomStore.loading">
          <div class="empty-icon">🏠</div>
          <h3>暂无房间</h3>
          <p>创建一个新房间或加入已有房间开始协作吧！</p>
          <div class="empty-actions">
            <a-button type="primary" @click="showCreateModal">
              <PlusOutlined />
              创建第一个房间
            </a-button>
          </div>
        </div>

        <!-- 加载状态 -->
        <div class="loading-state" v-if="roomStore.loading">
          <a-spin size="large" />
          <p>加载中...</p>
        </div>
      </div>
    </div>

    <!-- 创建房间模态框 -->
    <CreateRoomModal 
      v-model:visible="createModalVisible" 
      @success="handleCreateSuccess" 
    />
    
    <!-- 加入房间模态框 -->
    <JoinRoomModal 
      v-model:visible="joinModalVisible" 
      @success="handleJoinSuccess" 
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { 
  PlusOutlined, 
  TeamOutlined, 
  LogoutOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'
import type { RoomInfo } from '@/types/room'
import CreateRoomModal from '@/components/CreateRoomModal.vue'
import JoinRoomModal from '@/components/JoinRoomModal.vue'
import RoomCard from '@/components/RoomCard.vue'

// ==================== 组件状态 ====================

const router = useRouter()
const authStore = useAuthStore()
const roomStore = useRoomStore()

// 模态框状态
const createModalVisible = ref(false)
const joinModalVisible = ref(false)

// ==================== 事件处理 ====================

/**
 * 显示创建房间模态框
 */
function showCreateModal() {
  createModalVisible.value = true
}

/**
 * 显示加入房间模态框
 */
function showJoinModal() {
  joinModalVisible.value = true
}

/**
 * 创建房间成功处理
 */
function handleCreateSuccess(room: RoomInfo) {
  createModalVisible.value = false
  message.success(`房间 "${room.roomName}" 创建成功！`)
  // 可以选择直接进入房间或留在列表页
}

/**
 * 加入房间成功处理
 */
function handleJoinSuccess(room: RoomInfo) {
  joinModalVisible.value = false
  message.success(`成功加入房间 "${room.roomName}"！`)
}

/**
 * 进入房间
 */
function handleEnterRoom(room: RoomInfo) {
  roomStore.setCurrentRoom(room)
  router.push(`/rooms/${room.roomId}`)
}

/**
 * 离开房间
 */
async function handleLeaveRoom(room: RoomInfo) {
  Modal.confirm({
    title: '确认离开房间',
    content: `确定要离开房间 "${room.roomName}" 吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await roomStore.leaveRoom(room.roomId)
      } catch (error) {
        message.error('离开房间失败，请重试')
      }
    }
  })
}

/**
 * 关闭房间（仅房主）
 */
async function handleCloseRoom(room: RoomInfo) {
  Modal.confirm({
    title: '确认关闭房间',
    content: `确定要关闭房间 "${room.roomName}" 吗？此操作不可撤销！`,
    okText: '确认关闭',
    cancelText: '取消',
    okType: 'danger',
    onOk: async () => {
      try {
        await roomStore.closeRoom(room.roomId)
      } catch (error) {
        message.error('关闭房间失败，请重试')
      }
    }
  })
}

/**
 * 刷新房间列表
 */
async function refreshRooms() {
  try {
    await roomStore.fetchUserRooms()
  } catch (error) {
    message.error('刷新失败，请重试')
  }
}

/**
 * 退出登录
 */
function handleLogout() {
  Modal.confirm({
    title: '确认退出',
    content: '确定要退出登录吗？',
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      authStore.logout()
      roomStore.clearRoomData()
      router.push('/login')
    }
  })
}

// ==================== 生命周期 ====================

onMounted(async () => {
  // 页面加载时获取房间列表
  await roomStore.fetchUserRooms()
})
</script>

<style scoped>
/* ==================== 页面布局 ==================== */

.rooms-view {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
}

/* ==================== 页面头部 ==================== */

.rooms-header {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  padding: 20px 0;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.brand-section {
  display: flex;
  align-items: center;
  color: white;
}

.logo-icon {
  font-size: 32px;
  margin-right: 12px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
  background: linear-gradient(45deg, #fff, #e2e8f0);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.user-section {
  display: flex;
  align-items: center;
  gap: 16px;
}

.welcome-text {
  color: rgba(255, 255, 255, 0.9);
  font-size: 16px;
}

.logout-btn {
  color: rgba(255, 255, 255, 0.8) !important;
  border: 1px solid rgba(255, 255, 255, 0.3) !important;
}

.logout-btn:hover {
  color: white !important;
  background: rgba(255, 255, 255, 0.1) !important;
  border-color: rgba(255, 255, 255, 0.5) !important;
}

/* ==================== 主要内容区域 ==================== */

.rooms-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
}

/* ==================== 操作区域 ==================== */

.rooms-actions {
  text-align: center;
  margin-bottom: 40px;
}

.actions-container {
  display: inline-flex;
  gap: 24px;
  padding: 32px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.action-btn {
  min-width: 160px;
  height: 56px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s ease;
}

.create-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

.create-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}

.join-btn {
  border: 2px solid #667eea;
  color: #667eea;
}

.join-btn:hover {
  background: #667eea;
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.3);
}

/* ==================== 房间列表区域 ==================== */

.rooms-list-section {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.list-header h2 {
  margin: 0;
  font-size: 20px;
  color: #1a202c;
}

.refresh-btn {
  color: #667eea;
}

.refresh-btn:hover {
  color: #764ba2;
  background: rgba(102, 126, 234, 0.1);
}

/* ==================== 房间列表 ==================== */

.rooms-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

/* ==================== 空状态 ==================== */

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #6b7280;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state h3 {
  font-size: 20px;
  margin-bottom: 8px;
  color: #374151;
}

.empty-state p {
  font-size: 16px;
  margin-bottom: 24px;
  line-height: 1.5;
}

.empty-actions {
  display: flex;
  justify-content: center;
}

/* ==================== 加载状态 ==================== */

.loading-state {
  text-align: center;
  padding: 60px 20px;
  color: #6b7280;
}

.loading-state p {
  margin-top: 16px;
  font-size: 16px;
}

/* ==================== 响应式设计 ==================== */

@media (max-width: 768px) {
  .header-content {
    padding: 0 16px;
  }
  
  .user-section {
    flex-direction: column;
    gap: 8px;
  }
  
  .welcome-text {
    font-size: 14px;
  }
  
  .rooms-content {
    padding: 20px 16px;
  }
  
  .actions-container {
    flex-direction: column;
    gap: 16px;
    padding: 24px;
  }
  
  .action-btn {
    width: 100%;
    min-width: auto;
  }
  
  .rooms-list-section {
    padding: 20px;
  }
  
  .list-header {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  
  .rooms-list {
    grid-template-columns: 1fr;
  }
}
</style> 