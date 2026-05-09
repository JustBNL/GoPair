<template>
  <a-dropdown v-model:open="dropdownOpen" :trigger="['click']" placement="bottomRight">
    <div class="friends-trigger" @click="dropdownOpen = !dropdownOpen">
      <div class="friends-btn">
        <ContactsOutlined class="friends-icon" />
        <span v-if="chatStore.incomingCount + chatStore.unreadMessageCount > 0" class="friends-badge">
          {{ (chatStore.incomingCount + chatStore.unreadMessageCount) > 99 ? '99+' : (chatStore.incomingCount + chatStore.unreadMessageCount) }}
        </span>
      </div>
    </div>

    <template #overlay>
      <div class="friends-dropdown-panel">
        <!-- 标题栏 + Tab 切换 -->
        <div class="panel-header">
          <div class="tab-group">
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'friends' }"
              @click="switchTab('friends')"
            >
              好友
            </button>
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'requests' }"
              @click="switchTab('requests')"
            >
              好友申请
              <span
                v-if="chatStore.incomingCount > 0"
                class="tab-badge"
              >
                {{ chatStore.incomingCount > 99 ? '99+' : chatStore.incomingCount }}
              </span>
            </button>
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'search' }"
              @click="switchTab('search')"
            >
              添加朋友
            </button>
          </div>
        </div>

        <!-- ===== Tab: 好友列表 ===== -->
        <template v-if="activeTab === 'friends'">
          <!-- 搜索 -->
          <div class="search-area">
            <a-input
              v-model:value="friendSearchKeyword"
              placeholder="搜索好友"
              allow-clear
              @keyup.enter="handleFriendSearch"
            >
              <template #suffix>
                <a-button
                  type="text"
                  size="small"
                  class="search-btn"
                  :loading="chatStore.friendSearchLoading"
                  @click="handleFriendSearch"
                >
                  <SearchOutlined />
                </a-button>
              </template>
            </a-input>
          </div>

          <!-- 好友列表 -->
          <div class="friends-list" v-if="displayedFriends.length > 0">
            <div
              v-for="friend in displayedFriends"
              :key="friend.friendId"
              class="friend-item"
              @click="handleSelectFriend(friend)"
            >
              <UserAvatar
                :user-id="friend.friendId"
                :nickname="friend.nickname"
                :avatar="friend.avatar"
                :size="40"
              />
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
          <div v-else-if="!chatStore.friendsLoading && !chatStore.friendSearchLoading" class="empty-state">
            <p class="empty-text">{{ friendSearchKeyword ? '没有找到好友' : '暂无好友' }}</p>
            <p v-if="!friendSearchKeyword" class="empty-hint">在房间里添加好友开始聊天吧</p>
          </div>

          <!-- 加载中 -->
          <div v-if="chatStore.friendsLoading || chatStore.friendSearchLoading" class="loading-state">
            <a-spin size="small" />
            <span>加载中...</span>
          </div>
        </template>

        <!-- ===== Tab: 好友申请 ===== -->
        <template v-else-if="activeTab === 'requests'">
          <!-- 收到的申请 -->
          <div class="requests-section">
            <div class="section-label">收到的申请</div>
            <div v-if="requestsTabLoading" class="loading-state">
              <a-spin size="small" />
              <span>加载中...</span>
            </div>
            <div v-else-if="pendingIncoming.length > 0" class="requests-list">
              <div
                v-for="req in pendingIncoming"
                :key="req.requestId"
                class="request-item"
              >
                <UserAvatar
                  :user-id="req.fromUserId"
                  :nickname="req.fromNickname"
                  :avatar="req.fromAvatar"
                  :size="40"
                />
                <div class="request-info">
                  <div class="request-name">{{ req.fromNickname }}</div>
                  <div v-if="req.message" class="request-message">{{ req.message }}</div>
                  <div class="request-time">{{ formatTime(req.createdAt) }}</div>
                </div>
                <div class="request-actions">
                  <a-button
                    type="primary"
                    size="small"
                    :loading="acceptingId === req.requestId"
                    :disabled="acceptingId !== null || rejectingId !== null"
                    @click="handleAccept(req.requestId)"
                  >
                    同意
                  </a-button>
                  <a-button
                    size="small"
                    :loading="rejectingId === req.requestId"
                    :disabled="acceptingId !== null || rejectingId !== null"
                    @click="handleReject(req.requestId)"
                  >
                    拒绝
                  </a-button>
                </div>
              </div>
            </div>
            <div v-else class="empty-state small">
              <p class="empty-text">暂无收到的申请</p>
            </div>
          </div>

          <!-- 发出的申请 -->
          <div class="requests-section">
            <div class="section-label">发出的申请</div>
            <div v-if="requestsTabLoading" class="loading-state">
              <a-spin size="small" />
              <span>加载中...</span>
            </div>
            <div v-else-if="outgoingRequests.length > 0" class="requests-list">
              <div
                v-for="req in outgoingRequests"
                :key="req.requestId"
                class="request-item outgoing"
              >
                <UserAvatar
                  :user-id="req.toUserId"
                  :nickname="req.toNickname || '用户'"
                  :size="40"
                />
                <div class="request-info">
                  <div class="request-name">{{ req.toNickname || '用户' }}</div>
                  <div v-if="req.message" class="request-message">{{ req.message }}</div>
                  <div class="request-time">{{ formatTime(req.createdAt) }}</div>
                </div>
                <div class="request-status">
                  <a-tag :color="statusTagColor(req.status)">
                    {{ statusLabel(req.status) }}
                  </a-tag>
                </div>
              </div>
            </div>
            <div v-else class="empty-state small">
              <p class="empty-text">暂无发出的申请</p>
            </div>
          </div>
        </template>

        <!-- ===== Tab: 添加朋友 ===== -->
        <template v-else-if="activeTab === 'search'">
          <!-- 搜索输入 -->
          <div class="search-area">
            <a-input
              v-model:value="searchKeyword"
              placeholder="搜索昵称或邮箱"
              allow-clear
              @keyup.enter="handleSearchInput"
            >
              <template #suffix>
                <a-button
                  type="text"
                  size="small"
                  class="search-btn"
                  :loading="chatStore.searchLoading"
                  @click="handleSearchInput"
                >
                  <SearchOutlined />
                </a-button>
              </template>
            </a-input>
          </div>

          <!-- 搜索异常 -->
          <div
            v-if="chatStore.searchError && !chatStore.searchLoading"
            class="error-state"
          >
            <p class="error-text">{{ chatStore.searchError }}</p>
          </div>

          <!-- 搜索结果列表 -->
          <div
            v-else-if="searchKeyword.trim() && chatStore.searchResults.length > 0"
            class="friends-list"
          >
            <div
              v-for="user in chatStore.searchResults"
              :key="user.userId"
              class="friend-item search-result-item"
            >
              <UserAvatar
                :user-id="user.userId"
                :nickname="user.nickname"
                :avatar="user.avatar"
                :size="40"
              />
              <div class="friend-info">
                <div class="friend-name">{{ user.nickname }}</div>
                <div class="friend-preview email-preview">{{ user.email || '无邮箱' }}</div>
              </div>
              <!-- 操作按钮 -->
              <div class="user-action">
                <template v-if="user.friendStatus?.isFriend">
                  <a-button size="small" disabled class="action-btn disabled">已是好友</a-button>
                </template>
                <template v-else-if="user.friendStatus?.isRequestSent">
                  <a-button size="small" disabled class="action-btn disabled">已发送</a-button>
                </template>
                <template v-else-if="user.friendStatus?.isRequestReceived">
                  <a-button
                    type="primary"
                    size="small"
                    class="action-btn"
                    @click.stop="switchToRequests"
                  >
                    回复
                  </a-button>
                </template>
                <template v-else>
                  <a-button
                    type="primary"
                    size="small"
                    class="action-btn primary"
                    :loading="addingUserId === user.userId"
                    @click.stop="handleAddFriend(user.userId)"
                  >
                    加好友
                  </a-button>
                </template>
              </div>
            </div>
          </div>

          <!-- 搜索无结果 -->
          <div
            v-else-if="searchKeyword.trim() && !chatStore.searchLoading && chatStore.searchResults.length === 0"
            class="empty-state"
          >
            <p class="empty-text">未找到相关用户</p>
            <p class="empty-hint">换个关键词试试吧</p>
          </div>

          <!-- 未输入时 -->
          <div
            v-else-if="!searchKeyword.trim()"
            class="empty-state"
          >
            <p class="empty-text">搜索用户</p>
            <p class="empty-hint">输入昵称或邮箱搜索</p>
          </div>

          <!-- 加载中 -->
          <div v-if="chatStore.searchLoading" class="loading-state">
            <a-spin size="small" />
            <span>搜索中...</span>
          </div>
        </template>
      </div>
    </template>
  </a-dropdown>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  ContactsOutlined,
  SearchOutlined
} from '@ant-design/icons-vue'
import { useChatStore } from '@/stores/chat'
import type { FriendVO } from '@/types/chat'
import UserAvatar from '@/components/UserAvatar.vue'

const emit = defineEmits<{
  (e: 'openChat', friendId: number): void
}>()

const chatStore = useChatStore()

const dropdownOpen = ref(false)
const activeTab = ref<'friends' | 'requests' | 'search'>('friends')

// 好友 Tab 搜索
const friendSearchKeyword = ref('')

// 添加朋友 Tab 搜索
const searchKeyword = ref('')
const addingUserId = ref<number | null>(null)

// 操作级 loading
const acceptingId = ref<number | null>(null)
const rejectingId = ref<number | null>(null)
const requestsTabLoading = ref(false)

// 防抖定时器
let searchTimer: ReturnType<typeof setTimeout> | null = null

const displayedFriends = computed(() => {
  if (friendSearchKeyword.value.trim()) {
    return chatStore.friendSearchResults
  }
  return chatStore.friends
})

const pendingIncoming = computed(() =>
  chatStore.incomingRequests.filter(r => r.status === 'pending')
)

const outgoingRequests = computed(() => chatStore.outgoingRequests)

async function switchTab(tab: 'friends' | 'requests' | 'search') {
  activeTab.value = tab
  if (tab === 'friends') {
    searchKeyword.value = ''
    chatStore.clearSearchResults()
  } else if (tab === 'search') {
    friendSearchKeyword.value = ''
    chatStore.clearFriendSearchResults()
  } else if (tab === 'requests') {
    requestsTabLoading.value = true
    await Promise.all([
      chatStore.fetchIncomingRequests(),
      chatStore.fetchOutgoingRequests()
    ])
    requestsTabLoading.value = false
  }
}

function switchToRequests() {
  activeTab.value = 'requests'
}

function handleFriendSearch() {
  const kw = friendSearchKeyword.value.trim()
  if (!kw) {
    chatStore.clearFriendSearchResults()
    return
  }
  chatStore.fetchFriendSearchResults(kw)
}

function handleSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  const kw = searchKeyword.value.trim()
  if (!kw) {
    chatStore.clearSearchResults()
    return
  }
  searchTimer = setTimeout(() => {
    chatStore.fetchSearchResults(kw)
  }, 300)
}

function handleSelectFriend(friend: FriendVO) {
  dropdownOpen.value = false
  emit('openChat', friend.friendId)
}

async function handleAddFriend(userId: number) {
  addingUserId.value = userId
  try {
    await chatStore.sendRequest(userId)
    message.success('申请已发送')
    if (searchKeyword.value.trim()) {
      await chatStore.fetchSearchResults(searchKeyword.value.trim())
    }
    await chatStore.fetchOutgoingRequests()
  } catch {
    // 错误已在 API 层处理
  } finally {
    addingUserId.value = null
  }
}

async function handleAccept(requestId: number) {
  acceptingId.value = requestId
  try {
    await chatStore.acceptRequest(requestId)
    message.success('已同意好友申请')
  } catch {
    // 错误已在 API 层处理
  } finally {
    acceptingId.value = null
  }
}

async function handleReject(requestId: number) {
  rejectingId.value = requestId
  try {
    await chatStore.rejectRequest(requestId)
    message.success('已拒绝好友申请')
  } catch {
    // 错误已在 API 层处理
  } finally {
    rejectingId.value = null
  }
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    pending: '待回复',
    accepted: '已同意',
    rejected: '已拒绝'
  }
  return map[status] || status
}

function statusTagColor(status: string): string {
  const map: Record<string, string> = {
    pending: 'orange',
    accepted: 'green',
    rejected: 'default'
  }
  return map[status] || 'default'
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

// 关闭下拉时清理状态
watch(dropdownOpen, (open) => {
  if (!open) {
    friendSearchKeyword.value = ''
    searchKeyword.value = ''
    chatStore.clearSearchResults()
    chatStore.clearFriendSearchResults()
  } else {
    chatStore.clearUnread()
    if (chatStore.friends.length === 0) {
      Promise.all([
        chatStore.fetchFriends(),
        chatStore.fetchIncomingRequests(),
        chatStore.fetchOutgoingRequests()
      ])
    }
  }
})
</script>

<style scoped>
.friends-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  cursor: pointer;
  color: var(--text-secondary);
  transition: color 0.2s, border-color 0.2s, background-color 0.2s;
}

.friends-trigger:hover {
  color: var(--brand-primary);
  border-color: var(--brand-primary);
  background: var(--brand-primary-light);
}

.friends-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
}

.friends-icon {
  font-size: 18px;
  color: inherit;
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

.friends-dropdown-panel {
  width: 320px;
  max-height: 520px;
  background: var(--surface-bg, #fff);
  border-radius: 12px;
  box-shadow: 0 6px 24px rgba(0,0,0,0.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  padding: 14px 16px 10px;
  border-bottom: 1px solid var(--border-light, #f0f0f0);
}

.tab-group {
  display: flex;
  background: var(--border-light, #f0f0f0);
  border-radius: 8px;
  padding: 2px;
  gap: 2px;
}

.tab-btn {
  flex: 1;
  padding: 4px 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary, #666);
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.tab-btn:hover {
  color: var(--text-primary, #1a1a1a);
}

.tab-btn.active {
  background: var(--surface-bg, #fff);
  color: var(--brand-primary);
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}

.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
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

.search-area {
  padding: 10px 12px;
}

.search-icon {
  color: var(--text-muted, #999);
  font-size: 14px;
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

.search-result-item {
  cursor: default;
}

.search-result-item:hover {
  background: transparent;
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

.email-preview {
  font-size: 11px;
  color: var(--text-muted, #999);
}

.friend-time {
  font-size: 11px;
  color: var(--text-muted, #999);
  flex-shrink: 0;
}

.user-action {
  flex-shrink: 0;
}

.action-btn {
  min-width: 60px;
  font-size: 12px;
}

.action-btn.primary {
  background: var(--brand-primary);
  border-color: var(--brand-primary);
}

.action-btn.disabled {
  background: var(--border-light, #f0f0f0);
  color: var(--text-muted, #999);
  border-color: transparent;
}

.empty-state {
  padding: 32px 16px;
  text-align: center;
}

.empty-state.small {
  padding: 20px 16px;
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

.error-state {
  padding: 32px 16px;
  text-align: center;
}

.error-text {
  font-size: 14px;
  color: var(--color-error, #e74c3c);
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

.requests-section {
  padding: 8px 0;
  border-bottom: 1px solid var(--border-light, #f0f0f0);
}

.requests-section:last-child {
  border-bottom: none;
}

.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary, #666);
  padding: 4px 16px 8px;
}

.requests-list {
  display: flex;
  flex-direction: column;
}

.request-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 16px;
  transition: background 0.15s;
}

.request-item:hover {
  background: var(--border-light, #f5f5f5);
}

.request-item.outgoing {
  opacity: 0.85;
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
  flex-shrink: 0;
}

.request-status {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
</style>
