<template>
  <a-dropdown :trigger="['click']" placement="bottomRight">
    <div class="friends-trigger" @click.prevent>
      <div class="friends-btn">
        <ContactsOutlined class="friends-icon" />
        <span v-if="chatStore.incomingCount > 0" class="friends-badge">
          {{ chatStore.incomingCount > 99 ? '99+' : chatStore.incomingCount }}
        </span>
      </div>
      <span class="friends-label">好友</span>
    </div>

    <template #overlay>
      <div class="friends-dropdown-panel">
        <!-- 标题栏 -->
        <div class="panel-header">
          <span class="panel-title">好友列表</span>
          <span v-if="chatStore.incomingCount > 0" class="request-count">
            {{ chatStore.incomingCount }} 条待处理
          </span>
        </div>

        <!-- 搜索 -->
        <div class="search-area">
          <a-input
            v-model:value="searchKeyword"
            placeholder="搜索好友"
            allow-clear
            @change="handleSearch"
          >
            <template #prefix>
              <SearchOutlined class="search-icon" />
            </template>
          </a-input>
        </div>

        <!-- 待处理申请入口 -->
        <div
          v-if="chatStore.incomingCount > 0"
          class="pending-requests"
          @click="showRequestDrawer = true"
        >
          <BellOutlined class="pending-icon" />
          <span>{{ chatStore.incomingCount }} 条好友申请待处理</span>
          <RightOutlined class="arrow-icon" />
        </div>

        <!-- 好友列表 -->
        <div class="friends-list" v-if="filteredFriends.length > 0">
          <div
            v-for="friend in filteredFriends"
            :key="friend.friendId"
            class="friend-item"
            @click="handleSelectFriend(friend)"
          >
            <a-avatar :size="40" :src="friend.avatar">
              <template v-if="!friend.avatar">
                {{ getInitial(friend.nickname) }}
              </template>
            </a-avatar>
            <div class="friend-info">
              <div class="friend-name">{{ friend.nickname }}</div>
              <div v-if="friend.lastMessageContent" class="friend-preview">
                {{ friend.lastMessageContent }}
              </div>
              <div v-else class="friend-preview empty">暂无聊天记录</div>
            </div>
            <div v-if="friend.lastMessageTime" class="friend-time">
              {{ formatTime(friend.lastMessageTime) }}
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else-if="!chatStore.friendsLoading" class="empty-state">
          <p class="empty-text">{{ searchKeyword ? '没有找到好友' : '暂无好友' }}</p>
          <p v-if="!searchKeyword" class="empty-hint">在房间里添加好友开始聊天吧</p>
        </div>

        <!-- 加载中 -->
        <div v-if="chatStore.friendsLoading" class="loading-state">
          <a-spin size="small" />
          <span>加载中...</span>
        </div>
      </div>
    </template>
  </a-dropdown>

  <!-- 好友申请抽屉 -->
  <a-drawer
    v-model:open="showRequestDrawer"
    title="好友申请"
    placement="right"
    :width="360"
  >
    <div v-if="pendingIncoming.length === 0" class="no-requests">
      <p>暂无待处理的好友申请</p>
    </div>
    <div v-else class="request-list">
      <div
        v-for="req in pendingIncoming"
        :key="req.requestId"
        class="request-item"
      >
        <a-avatar :size="40" :src="req.fromAvatar">
          <template v-if="!req.fromAvatar">
            {{ getInitial(req.fromNickname) }}
          </template>
        </a-avatar>
        <div class="request-info">
          <div class="request-name">{{ req.fromNickname }}</div>
          <div v-if="req.message" class="request-message">{{ req.message }}</div>
          <div class="request-time">{{ formatTime(req.createdAt) }}</div>
        </div>
        <div class="request-actions">
          <a-button type="primary" size="small" @click="handleAccept(req.requestId)">
            同意
          </a-button>
          <a-button size="small" @click="handleReject(req.requestId)">
            拒绝
          </a-button>
        </div>
      </div>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  ContactsOutlined,
  SearchOutlined,
  BellOutlined,
  RightOutlined
} from '@ant-design/icons-vue'
import { useChatStore } from '@/stores/chat'
import type { FriendVO, FriendRequestVO } from '@/types/chat'

const emit = defineEmits<{
  (e: 'openChat', friendId: number): void
}>()

const chatStore = useChatStore()

const searchKeyword = ref('')
const showRequestDrawer = ref(false)

const filteredFriends = computed(() => {
  if (!searchKeyword.value.trim()) {
    return chatStore.friends
  }
  const kw = searchKeyword.value.toLowerCase()
  return chatStore.friends.filter(f =>
    f.nickname.toLowerCase().includes(kw)
  )
})

const pendingIncoming = computed(() =>
  chatStore.incomingRequests.filter(r => r.status === 'pending')
)

function handleSearch() {
  // 搜索由 computed 处理，无需额外逻辑
}

function handleSelectFriend(friend: FriendVO) {
  emit('openChat', friend.friendId)
}

async function handleAccept(requestId: number) {
  try {
    await chatStore.acceptRequest(requestId)
    message.success('已同意好友申请')
  } catch {
    // 错误已在 API 层处理
  }
}

async function handleReject(requestId: number) {
  try {
    await chatStore.rejectRequest(requestId)
    message.success('已拒绝好友申请')
  } catch {
    // 错误已在 API 层处理
  }
}

function getInitial(name: string): string {
  return (name || 'U').charAt(0).toUpperCase()
}

function formatTime(timeStr: string): string {
  if (!timeStr) return ''
  try {
    const d = new Date(timeStr)
    const now = new Date()
    const diff = now.getTime() - d.getTime()
    const oneDay = 86400000
    if (diff < oneDay) {
      return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    } else if (diff < 7 * oneDay) {
      const days = Math.floor(diff / oneDay)
      return `${days}天前`
    } else {
      return `${d.getMonth() + 1}/${d.getDate()}`
    }
  } catch {
    return ''
  }
}
</script>

<style scoped>
.friends-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 8px;
  transition: background 0.2s;
  user-select: none;
}

.friends-trigger:hover {
  background: var(--border-light, rgba(255,255,255,0.1));
}

.friends-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--surface-bg, rgba(255,255,255,0.08));
}

.friends-icon {
  font-size: 18px;
  color: var(--text-secondary, #a0a0a0);
}

.friends-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  border-radius: 8px;
  background: var(--color-error, #e74c3c);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  padding: 0 4px;
}

.friends-label {
  font-size: 10px;
  color: var(--text-muted, #888);
}

.friends-dropdown-panel {
  width: 320px;
  max-height: 480px;
  background: var(--surface-bg, #fff);
  border-radius: 12px;
  box-shadow: 0 6px 24px rgba(0,0,0,0.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
  border-bottom: 1px solid var(--border-light, #f0f0f0);
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #1a1a1a);
}

.request-count {
  font-size: 12px;
  color: var(--brand-accent, #E07850);
  cursor: pointer;
}

.search-area {
  padding: 10px 12px;
}

.search-icon {
  color: var(--text-muted, #999);
  font-size: 14px;
}

.pending-requests {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 12px 8px;
  padding: 10px 12px;
  background: rgba(224, 120, 80, 0.08);
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--brand-accent, #E07850);
  transition: background 0.2s;
}

.pending-requests:hover {
  background: rgba(224, 120, 80, 0.15);
}

.pending-icon {
  font-size: 16px;
}

.arrow-icon {
  margin-left: auto;
  font-size: 12px;
}

.friends-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.friend-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.15s;
}

.friend-item:hover {
  background: var(--border-light, #f5f5f5);
}

.friend-info {
  flex: 1;
  min-width: 0;
}

.friend-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary, #1a1a1a);
  margin-bottom: 2px;
}

.friend-preview {
  font-size: 12px;
  color: var(--text-muted, #999);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.friend-preview.empty {
  font-style: italic;
}

.friend-time {
  font-size: 11px;
  color: var(--text-muted, #999);
  flex-shrink: 0;
}

.empty-state {
  padding: 32px 16px;
  text-align: center;
}

.empty-text {
  font-size: 14px;
  color: var(--text-secondary, #666);
  margin: 0 0 6px;
}

.empty-hint {
  font-size: 12px;
  color: var(--text-muted, #999);
  margin: 0;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  color: var(--text-muted, #999);
  font-size: 13px;
}

.no-requests {
  text-align: center;
  padding: 40px 16px;
  color: var(--text-muted, #999);
}

.request-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.request-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  background: var(--surface-bg, #f9f9f9);
  border-radius: 8px;
}

.request-info {
  flex: 1;
  min-width: 0;
}

.request-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary, #1a1a1a);
  margin-bottom: 2px;
}

.request-message {
  font-size: 12px;
  color: var(--text-secondary, #666);
  margin-bottom: 4px;
}

.request-time {
  font-size: 11px;
  color: var(--text-muted, #999);
}

.request-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
