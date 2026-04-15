<template>
  <div class="rooms-view">
    <!-- 无障碍跳转链接：绝对定位跳到根元素顶部，视觉上不受影响 -->
    <a href="#rooms-main" class="skip-link">跳转到房间列表</a>

    <!-- 页面头部 -->
    <div class="rooms-header">
      <div class="header-content">
        <div class="brand-section">
          <div class="logo-icon">🎮</div>
          <h1 class="page-title">房间管理</h1>
        </div>
        <div class="user-section">
          <div class="user-avatar-btn" @click="profileVisible = true" title="编辑个人资料">
            <img v-if="authStore.user?.avatar" :src="authStore.user.avatar" class="user-avatar user-avatar-img" alt="avatar" />
            <div v-else class="user-avatar">{{ nicknameInitial }}</div>
            <span class="welcome-text">{{ authStore.currentNickname }}</span>
          </div>
          <a-button type="text" @click="handleLogout" class="logout-btn">
            <LogoutOutlined />
            退出登录
          </a-button>
        </div>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="rooms-content" id="rooms-main" role="main">
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
        <div class="loading-state" v-else-if="roomStore.loading">
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

    <!-- 个人资料模态框 -->
    <UserProfileModal v-model:visible="profileVisible" />

    <!-- AI 聊天助手 -->
    <AiChatDrawer />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
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
import UserProfileModal from '@/components/UserProfileModal.vue'
import AiChatDrawer from '@/components/ai/AiChatDrawer.vue'

// ==================== 组件状态 ====================

const router = useRouter()
const authStore = useAuthStore()
const roomStore = useRoomStore()

// 模态框状态
const createModalVisible = ref(false)
const joinModalVisible = ref(false)
const profileVisible = ref(false)

const nicknameInitial = computed(() => {
  const name = authStore.currentNickname
  return name.charAt(0).toUpperCase()
})

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
 * 先导航离开当前页面，再清理数据，避免组件在卸载过程中响应式崩溃
 */
function handleLogout() {
  Modal.confirm({
    title: '确认退出',
    content: '确定要退出登录吗？',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      await authStore.logout()
      await router.push('/login')
      roomStore.clearRoomData()
    }
  })
}

// ==================== 生命周期 ====================

onMounted(async () => {
  await roomStore.fetchUserRooms()
})
</script>

<style scoped>
/* ==================== 无障碍跳转链接 ==================== */
.skip-link {
  position: absolute;
  top: -100%;
  left: 16px;
  z-index: 9999;
  padding: 8px 16px;
  background: var(--brand-primary);
  color: var(--text-on-primary);
  border-radius: 0 0 8px 8px;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: top 0.2s;
}
.skip-link:focus {
  top: 0;
}

/* ==================== 页面布局 ==================== */

.rooms-view {
  position: relative;
  min-height: 100vh;
  background: var(--brand-primary);
  position: relative;
}

/* ==================== 页面头部 ==================== */

.rooms-header {
  background: var(--overlay-white-10);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--overlay-white-20);
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
  color: var(--text-on-primary);
}

.logo-icon {
  font-size: 32px;
  margin-right: 12px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
  color: var(--text-on-primary);
}

.user-section {
  display: flex;
  align-items: center;
  gap: 16px;
}

.welcome-text {
  color: var(--text-on-primary-muted);
  font-size: 16px;
}

.logout-btn {
  color: var(--text-on-primary-muted) !important;
  border: 1px solid var(--overlay-white-30) !important;
}

.logout-btn:hover {
  color: var(--text-on-primary) !important;
  background: var(--overlay-white-10) !important;
  border-color: var(--overlay-white-20) !important;
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
  background: var(--overlay-white-95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--overlay-white-20);
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
  background: var(--brand-primary);
  border: none;
}

.create-btn:hover {
  box-shadow: 0 8px 25px rgba(var(--brand-primary-rgb), 0.3);
}

.join-btn {
  border: 2px solid var(--brand-primary);
  color: var(--brand-primary);
}

.join-btn:hover {
  background: var(--brand-primary);
  color: var(--text-on-primary);
  box-shadow: 0 8px 25px rgba(var(--brand-primary-rgb), 0.3);
}

/* ==================== 房间列表区域 ==================== */

.rooms-list-section {
  background: var(--overlay-white-95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 32px;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--overlay-white-20);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
}

.list-header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.refresh-btn {
  color: var(--brand-primary);
}

.refresh-btn:hover {
  color: var(--brand-primary);
  background: var(--brand-primary-light);
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
  color: var(--text-secondary);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state h3 {
  font-size: 20px;
  margin-bottom: 8px;
  color: var(--text-primary);
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
  color: var(--text-secondary);
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

/* ==================== 用户头像按钮 ==================== */

.user-avatar-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 30px;
  transition: background 0.2s;

  &:hover {
    background: var(--overlay-white-10);
  }
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--surface-card);
  color: var(--brand-primary);
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--overlay-white-40);
  flex-shrink: 0;
}

.user-avatar-img {
  background: none;
  object-fit: cover;
  display: block;
}

/* ==================== 尊重减少动画偏好 ==================== */

@media (prefers-reduced-motion: reduce) {
  .action-btn {
    transition: none;
  }

  .create-btn:hover,
  .join-btn:hover {
    transform: none;
  }

  .rooms-header,
  .actions-container,
  .rooms-list-section,
  .user-avatar-btn {
    backdrop-filter: none;
  }
}
</style>
