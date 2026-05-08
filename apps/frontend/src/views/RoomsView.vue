<template>
  <div class="rooms-view">
    <!-- 无障碍跳转链接：绝对定位跳到根元素顶部，视觉上不受影响 -->
    <a href="#rooms-main" class="skip-link">跳转到房间列表</a>

    <!-- 页面头部 -->
    <div class="rooms-header">
      <div class="header-content">
        <div class="brand-section">
          <BrandLogo class="logo-icon" :size="32" aria-hidden="true" />
          <h1 class="page-title">主页</h1>
        </div>
        <div class="user-section">
          <div class="user-avatar-btn" @click="profileVisible = true" title="编辑个人资料">
            <img v-if="authStore.user?.avatar" :src="authStore.user.avatar" class="user-avatar user-avatar-img" alt="avatar" />
            <div v-else class="user-avatar">{{ nicknameInitial }}</div>
            <span class="welcome-text">{{ authStore.currentNickname }}</span>
          </div>
          <FriendsDropdown @open-chat="handleOpenPrivateChat" />
          <ThemeToggle />
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
          <div class="list-header-right">
            <a-radio-group
              v-model:value="roomFilter"
              option-type="button"
              button-style="solid"
              :options="filterOptions"
              @change="handleFilterChange"
            />
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
        </div>

        <!-- 房间列表 -->
        <div class="rooms-list" v-if="filteredRoomList.length > 0">
          <RoomCard
            v-for="room in filteredRoomList"
            :key="room.roomId"
            :room="room"
            @enter="handleEnterRoom"
            @leave="handleLeaveRoom"
            @close="handleCloseRoom"
            @renew="handleRenewRoom"
            @reopen="handleReopenRoom"
          />
        </div>

        <!-- 分页组件：总数超过单页数量才显示 -->
        <div class="rooms-pagination-wrapper" v-if="roomStore.pagination.total > roomStore.pagination.pageSize">
          <a-pagination
            :current="roomStore.pagination.current"
            :page-size="roomStore.pagination.pageSize"
            :total="roomStore.pagination.total"
            :show-quick-jumper="Math.ceil(roomStore.pagination.total / roomStore.pagination.pageSize) > 7"
            @change="handlePageChange"
          />
        </div>

        <!-- 空状态：v-show 避免渲染时序导致房间卡片和空状态短暂同时显示 -->
        <div class="empty-state" v-show="filteredRoomList.length === 0 && !roomStore.loading">
          <div class="empty-icon">{{ emptyIcon }}</div>
          <h3>{{ emptyTitle }}</h3>
          <p>{{ emptyDescription }}</p>
          <div class="empty-actions" v-if="roomFilter === 'all'">
            <a-button type="primary" @click="showCreateModal">
              <PlusOutlined />
              创建第一个房间
            </a-button>
          </div>
        </div>

        <!-- 加载状态：v-show 避免与空状态短暂重叠 -->
        <div class="loading-state" v-show="roomStore.loading">
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

    <!-- 续期房间模态框 -->
    <RenewRoomModal
      v-model:visible="renewModalVisible"
      :room="renewTargetRoom"
      @success="handleRenewSuccess"
    />

    <ReopenRoomModal
      v-model:visible="reopenModalVisible"
      :room="reopenTargetRoom"
      @success="handleReopenSuccess"
    />

    <!-- 个人资料模态框 -->
    <UserProfileModal v-model:visible="profileVisible" />

    <!-- AI 聊天助手 -->
    <AiChatDrawer />

    <!-- 私聊模态框 -->
    <PrivateChatModal
      v-model:visible="privateChatVisible"
      :friend-id="privateChatFriendId"
      @refresh-friends="handleRefreshFriends"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  PlusOutlined,
  TeamOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'
import { useChatStore } from '@/stores/chat'
import { usePrivateChatWebSocket } from '@/composables/usePrivateChatWebSocket'
import type { RoomInfo } from '@/types/room'
import CreateRoomModal from '@/components/CreateRoomModal.vue'
import JoinRoomModal from '@/components/JoinRoomModal.vue'
import RenewRoomModal from '@/components/RenewRoomModal.vue'
import ReopenRoomModal from '@/components/ReopenRoomModal.vue'
import RoomCard from '@/components/RoomCard.vue'
import UserProfileModal from '@/components/UserProfileModal.vue'
import FriendsDropdown from '@/components/privatechat/FriendsDropdown.vue'
import PrivateChatModal from '@/components/privatechat/PrivateChatModal.vue'
import AiChatDrawer from '@/components/ai/AiChatDrawer.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'

// ==================== 组件状态 ====================

const router = useRouter()
const authStore = useAuthStore()
const roomStore = useRoomStore()
const chatStore = useChatStore()
const { connect: connectPrivateChat } = usePrivateChatWebSocket()

// 模态框状态
const createModalVisible = ref(false)
const joinModalVisible = ref(false)
const profileVisible = ref(false)
const privateChatVisible = ref(false)
const privateChatFriendId = ref<number | null>(null)
const renewModalVisible = ref(false)
const renewTargetRoom = ref<RoomInfo | null>(null)

const reopenModalVisible = ref(false)
const reopenTargetRoom = ref<RoomInfo | null>(null)

// 房间筛选状态：'all' | 'created' | 'joined'
const roomFilter = ref<'all' | 'created' | 'joined'>('all')

// 筛选选项配置
const filterOptions = [
  { label: '全部', value: 'all' },
  { label: '我创建的', value: 'created' },
  { label: '我加入的', value: 'joined' }
]

const nicknameInitial = computed(() => {
  const name = authStore.currentNickname
  return name.charAt(0).toUpperCase()
})

// 根据筛选条件过滤房间列表
const filteredRoomList = computed(() => {
  if (roomFilter.value === 'all') return roomStore.roomList
  return roomStore.roomList.filter(r => r.relationshipType === roomFilter.value)
})

// 空状态图标
const emptyIcon = computed(() => {
  if (roomFilter.value === 'created') return '🏗️'
  if (roomFilter.value === 'joined') return '🚪'
  return '🏠'
})

// 空状态标题
const emptyTitle = computed(() => {
  if (roomFilter.value === 'created') return '暂无创建的房间'
  if (roomFilter.value === 'joined') return '暂无加入的房间'
  return '暂无房间'
})

// 空状态描述
const emptyDescription = computed(() => {
  if (roomFilter.value === 'created') return '创建一个新房间开始协作吧！'
  if (roomFilter.value === 'joined') return '加入一个已有房间开始协作吧！'
  return '创建一个新房间或加入已有房间开始协作吧！'
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
 * 续期房间（仅房主）
 */
function handleRenewRoom(room: RoomInfo) {
  renewTargetRoom.value = room
  renewModalVisible.value = true
}

/**
 * 续期成功
 */
function handleRenewSuccess(room: RoomInfo) {
  renewModalVisible.value = false
  renewTargetRoom.value = null
  message.success(`房间 "${room.roomName}" 续期成功！`)
}

/**
 * 打开重新开启弹窗
 */
function handleReopenRoom(room: RoomInfo) {
  reopenTargetRoom.value = room
  reopenModalVisible.value = true
}

/**
 * 重新开启成功
 */
function handleReopenSuccess(room: RoomInfo) {
  reopenModalVisible.value = false
  reopenTargetRoom.value = null
  message.success(`房间 "${room.roomName}" 已重新开启！`)
}

function handleOpenPrivateChat(friendId: number) {
  privateChatFriendId.value = friendId
  privateChatVisible.value = true
}

function handleRefreshFriends() {
  chatStore.fetchFriends()
  chatStore.fetchIncomingRequests()
  chatStore.fetchConversations()
}

/**
 * 刷新房间列表（保留当前页和筛选条件）
 */
async function refreshRooms() {
  try {
    await roomStore.fetchUserRooms({
      pageNum: roomStore.pagination.current,
      pageSize: roomStore.pagination.pageSize
    })
  } catch (error) {
    message.error('刷新失败，请重试')
  }
}

/**
 * 筛选条件变化：重置到第1页并重新加载
 */
async function handleFilterChange() {
  roomStore.pagination.current = 1
  try {
    await roomStore.fetchUserRooms({
      pageNum: 1,
      pageSize: roomStore.pagination.pageSize
    })
  } catch (error) {
    message.error('加载失败，请重试')
  }
}

/**
 * 翻页
 */
async function handlePageChange(page: number) {
  roomStore.pagination.current = page
  try {
    await roomStore.fetchUserRooms({
      pageNum: page,
      pageSize: roomStore.pagination.pageSize
    })
  } catch (error) {
    message.error('加载失败，请重试')
  }
}

// ==================== 生命周期 ====================

onMounted(async () => {
  await roomStore.fetchUserRooms()
  if (authStore.user) {
    chatStore.initChat()
    connectPrivateChat()
  }
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
  background-image: url('/bg-main.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

/* 背景遮罩：统一使用 --bg-overlay，浅色白色遮罩保留背景图可见，深色深色遮罩实现暗色沉浸 */
.rooms-view::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--bg-overlay);
  z-index: 0;
}

/* ==================== 页面头部 ==================== */

.rooms-header {
  background: var(--header-bg);
  backdrop-filter: blur(20px);
  border-bottom: none;
  padding: 20px 0;
  position: relative;
  z-index: 1;
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
  color: var(--text-on-header);
}

.logo-icon {
  margin-right: 12px;
  flex-shrink: 0;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
  color: var(--text-on-header);
}

.user-section {
  display: flex;
  align-items: center;
  gap: 16px;
}

.welcome-text {
  color: var(--text-on-header-muted);
  font-size: 16px;
}

/* ==================== 主要内容区域 ==================== */

.rooms-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
  position: relative;
  z-index: 1;
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
  background: var(--surface-card);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--border-default);
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
  background: var(--surface-card);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 32px;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--border-default);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
  gap: 16px;
}

.list-header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
  flex-shrink: 0;
}

.list-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
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

/* ==================== 分页组件 ==================== */

.rooms-pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
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
  color: var(--text-on-header);

  &:hover {
    background: var(--brand-primary-light);
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
  border: 2px solid var(--border-default);
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
