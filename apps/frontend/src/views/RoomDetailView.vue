<template>
  <div class="room-detail-view">
    <!-- 无障碍跳转链接：绝对定位跳到根元素顶部，视觉上不受影响 -->
    <a href="#room-detail-main" class="skip-link">跳转到房间内容</a>

    <!-- 加载状态 -->
    <div class="loading-container" v-if="loading">
      <a-spin size="large" />
      <p>加载房间信息中...</p>
    </div>

    <!-- 房间详情内容 -->
    <div class="room-detail-content" v-else-if="currentRoom" id="room-detail-main" role="main" aria-live="polite">
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
          <a-button v-if="isOwner && currentRoom.status === ROOM_STATUS.EXPIRED" type="primary" @click="showRenewModal = true">
            <ReloadOutlined />
            续期房间
          </a-button>
          <a-button v-if="isOwner && currentRoom.status === ROOM_STATUS.CLOSED && !currentRoom.closedTime" type="primary" @click="showReopenModal = true">
            <ReloadOutlined />
            重新开启
          </a-button>
          <a-button @click="copyRoomCode" aria-label="复制房间码">
            <CopyOutlined />
            复制房间码
          </a-button>
          <FriendsDropdown @open-chat="handleOpenPrivateChat" />
          <ThemeToggle />
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
            <!-- 成员信息加载状态 -->
            <div v-if="serviceStates.members.loading" class="card-loading">
              <a-spin size="small" />
              <span>加载中...</span>
            </div>
            
            <!-- 成员信息加载失败 -->
            <div v-else-if="serviceStates.members.error" class="card-error">
              <span class="error-text">{{ serviceStates.members.error }}</span>
              <a-button type="link" size="small" @click="retryService('members')">
                重试
              </a-button>
            </div>
            
            <!-- 正常的成员信息 -->
            <div v-else class="member-count">
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

        <!-- 密码卡片（有密码时显示） -->
        <div class="info-card password-card" v-if="showPasswordArea">
          <div class="card-header">
            <LockOutlined class="card-icon" />
            <h3>房间密码</h3>
            <a-tooltip v-if="isOwner" title="修改密码设置">
              <EditOutlined class="edit-password-btn" @click.stop="passwordDrawerVisible = true" />
            </a-tooltip>
          </div>
          <div class="card-content password-card-content">
            <div class="password-display">
              <span v-if="!passwordHidden" class="password-value">
                {{ currentPassword || '••••••' }}
                <span v-if="currentRoom.passwordMode === 2 && remainingSeconds > 0" class="totp-timer">({{ remainingSeconds }}s)</span>
              </span>
              <span v-else class="password-value hidden">••••••</span>
              <span v-if="isOwner || currentRoom.passwordVisible === 1" class="password-toggle" @click.stop="togglePasswordVisibility" :title="passwordHidden ? '查看密码' : '隐藏密码'">
                <EyeOutlined v-if="passwordHidden" />
                <EyeInvisibleOutlined v-else />
              </span>
              <a-tooltip v-if="isOwner" :title="currentRoom.passwordVisible === 1 ? '成员不可查看' : '成员可查看'">
                <span class="member-visibility-toggle" @click.stop="togglePasswordVisible">
                  <TeamOutlined :style="{ color: currentRoom.passwordVisible === 1 ? 'var(--brand-primary)' : 'var(--text-muted)' }" />
                </span>
              </a-tooltip>
            </div>
          </div>
        </div>

        <!-- 新增通话状态卡片 -->
        <div class="info-card" :class="{ 'active-call': callState === 'active' || callState === 'in-call' }">
          <div class="card-header">
            <PhoneOutlined class="card-icon" />
            <h3>语音状态</h3>
          </div>
          <div class="card-content">
            <div class="call-status">
              <span :class="['status-indicator', callState]"></span>
              {{ callStateText }}
            </div>
          </div>
        </div>
      </div>

      <!-- 通讯功能区域 -->
      <div class="communication-section">
        <!-- 聊天标签页 -->
        <div class="section-header">
          <a-tabs
            v-model:activeKey="activeTab"
            type="card"
            class="communication-tabs"
            :tab-bar-style="{ background: 'var(--surface-bg)', margin: 0 }"
          >
            <a-tab-pane key="chat" class="tab-pane">
              <template #tab>
                <span class="tab-title">
                  <MessageOutlined />
                  聊天
                  <a-badge v-if="unreadCount > 0" :count="unreadCount" class="tab-badge" />
                </span>
              </template>
            </a-tab-pane>

            <!-- 文件标签页 -->
            <a-tab-pane key="files" class="tab-pane">
              <template #tab>
                <span class="tab-title">
                  <FolderOutlined />
                  文件
                  <a-badge v-if="!serviceStates.files.available" status="error" class="tab-badge" />
                </span>
              </template>
            </a-tab-pane>

            <!-- 语音通话标签页 -->
            <a-tab-pane key="voice" class="tab-pane">
              <template #tab>
                <span class="tab-title">
                  <PhoneOutlined />
                  语音
                  <a-badge v-if="callState === 'in-call'" text="通话中" status="processing" class="tab-badge" />
                  <a-badge v-else-if="callState === 'active'" text="进行中" status="warning" class="tab-badge" />
                </span>
              </template>
            </a-tab-pane>

            <!-- 成员标签页 -->
            <a-tab-pane key="members" class="tab-pane">
              <template #tab>
                <span class="tab-title">
                  <TeamOutlined />
                  成员 ({{ !serviceStates.members.available ? '?' : currentRoom.currentMembers }})
                  <a-badge v-if="!serviceStates.members.available" status="error" class="tab-badge" />
                </span>
              </template>
            </a-tab-pane>
          </a-tabs>
        </div>

        <!-- 内容区域 -->
        <div class="section-body">
          <!-- 聊天面板 -->
          <div v-show="activeTab === 'chat'" class="message-list-wrapper">
            <div v-if="serviceStates.messages.loading" class="service-loading">
              <a-spin size="large" />
              <p>加载消息中...</p>
            </div>
            <div v-else-if="serviceStates.messages.error" class="service-error">
              <a-result status="warning" :title="serviceStates.messages.error">
                <template #extra>
                  <a-button type="primary" @click="retryService('messages')">
                    重试
                  </a-button>
                </template>
              </a-result>
            </div>
            <div v-else class="message-list-container">
              <div v-if="!messages || messages.length === 0" class="empty-messages">
                <a-empty description="暂无消息，开始聊天吧！" />
              </div>
              <div v-else class="message-items">
                <message-bubble
                  v-for="message in messages"
                  v-memo="[message.messageId, message.content, message.messageType, message.isOwn, message.senderNickname]"
                  :key="message.messageId"
                  :message="message"
                  :show-sender-info="true"
                  @reply="handleReply"
                  @delete="(id) => handleDeleteMessage(messages.find(m => m.messageId === id)!)"
                  @recall="(id) => handleRecallMessage(messages.find(m => m.messageId === id)!)"
                  @view-profile="openMemberProfile"
                />
              </div>
            </div>
          </div>

          <!-- 文件面板 -->
          <div v-show="activeTab === 'files'" class="files-container">
            <div v-if="serviceStates.files.loading" class="service-loading">
              <a-spin size="large" />
              <p>加载文件信息中...</p>
            </div>
            <div v-else-if="serviceStates.files.error" class="service-error">
              <a-result status="warning" :title="serviceStates.files.error">
                <template #extra>
                  <a-button type="primary" @click="retryService('files')">
                    重试
                  </a-button>
                </template>
              </a-result>
            </div>
            <div v-else class="file-list-section">
              <file-list
                :room-id="currentRoom.roomId"
                :refresh="fileListRefresh"
                @file-selected="handleFileSelected"
                @file-deleted="handleFileDeleted"
              />
            </div>
          </div>

          <!-- 语音面板 -->
          <div v-show="activeTab === 'voice'" class="voice-container">
            <voice-call-panel
              :room-id="currentRoom.roomId"
              :current-user-id="currentUser?.userId || 0"
              :call-state="callState"
              :is-owner="voiceIsOwner"
              :current-call="currentCall"
              :loading="voiceLoading"
              :action-loading="actionLoading"
              :is-muted="isMuted"
              :is-speaker-off="isSpeakerOff"
              :member-nicknames="memberNicknameMap"
              @open="handleOpen"
              @join="handleJoin"
              @leave="handleLeave"
              @end="handleEnd"
              @toggle-mute="handleToggleMute"
              @toggle-speaker="handleToggleSpeaker"
            />
          </div>

          <!-- 成员面板 -->
          <div v-show="activeTab === 'members'" class="members-container">
            <div v-if="serviceStates.members.loading" class="service-loading">
              <a-spin size="large" />
              <p>加载成员信息中...</p>
            </div>
            <div v-else-if="serviceStates.members.error" class="service-error">
              <a-result status="warning" :title="serviceStates.members.error">
                <template #extra>
                  <a-button type="primary" @click="retryService('members')">
                    重试
                  </a-button>
                </template>
              </a-result>
            </div>
            <div v-else class="members-list-wrapper">
              <div class="members-header">
                <h4>房间成员</h4>
                <a-button type="text" size="small" aria-label="刷新成员列表" @click="refreshMembers">
                  <ReloadOutlined />
                  刷新
                </a-button>
              </div>
              <div class="members-list">
                <div
                  v-for="member in roomMembers"
                  :key="member.userId"
                  class="member-item"
                >
                  <div class="member-avatar" style="cursor: pointer" @click="openMemberProfile(member.userId)">
                    <UserAvatar
                      :user-id="member.userId"
                      :nickname="member.nickname"
                      :avatar="member.avatar"
                      :size="40"
                    />
                    <div v-if="memberPresence(member) === 'online'" class="online-indicator"></div>
                  </div>
                  <div class="member-info">
                    <div class="member-name">
                      {{ member.nickname }}
                      <a-tag v-if="member.isOwner" color="gold" size="small">
                        房主
                      </a-tag>
                      <a-tag v-if="member.userId === currentUser?.userId" color="blue" size="small">
                        我
                      </a-tag>
                    </div>
                    <div class="member-meta">
                      <span class="join-time">{{ formatTime(member.joinTime) }}</span>
                      <span :class="['status', memberPresence(member)]">
                        {{ getStatusText(memberPresence(member)) }}
                      </span>
                    </div>
                  </div>
                  <div class="member-actions">
                    <a-dropdown v-if="isOwner && member.userId !== currentUser?.userId">
                      <a-button type="text" size="small" aria-label="成员操作">
                        <MoreOutlined />
                      </a-button>
                      <template #overlay>
                        <a-menu>
                          <a-menu-item key="kick" @click="kickMember(member)">
                            <UserDeleteOutlined />
                            移出房间
                          </a-menu-item>
                        </a-menu>
                      </template>
                    </a-dropdown>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="section-footer">
          <message-input
            v-show="activeTab === 'chat'"
            v-if="currentRoom"
            :room-id="currentRoom.roomId"
            :reply-message="replyMessage"
          :disabled="serviceStates.messages.error !== null || !canWrite"
            @send-message="handleSendMessage"
            @cancel-reply="() => (replyMessage = null)"
            @upload-progress="handleUploadProgress"
          />
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

    <!-- 全局加载提示 -->
    <div v-if="globalLoading" class="global-loading">
      <a-spin />
    </div>

    <!-- 全屏 Emoji 漂浮层 -->
    <emoji-overlay
      :particles="emojiParticles"
      @particle-done="removeParticle"
    />

    <!-- 私聊模态框 -->
    <PrivateChatModal
      v-model:visible="privateChatVisible"
      :friend-id="privateChatFriendId"
      @refresh-friends="handleRefreshFriends"
    />

    <!-- 用户资料弹窗 -->
    <MemberProfileModal
      v-model:visible="memberProfileVisible"
      :member-id="memberProfileUserId"
      @open-chat="handleOpenPrivateChat"
      @refresh-friends="handleRefreshFriends"
    />

    <!-- 房间密码设置抽屉（仅房主可见，由卡片内按钮触发） -->
    <RoomPasswordDrawer
      v-if="currentRoom"
      v-model:visible="passwordDrawerVisible"
      :room-id="currentRoom.roomId"
      :room-name="currentRoom.roomName"
      :current-password-mode="currentRoom.passwordMode ?? 0"
      :current-password-visible="currentRoom.passwordVisible ?? 0"
      @success="handlePasswordUpdateSuccess"
    />

    <!-- 续期房间弹窗（仅房主 + EXPIRED 状态可见） -->
    <a-modal
      v-model:open="showRenewModal"
      title="续期房间"
      :confirm-loading="renewLoading"
      :width="400"
      centered
      @ok="handleRenewConfirm"
      @cancel="showRenewModal = false"
      ok-text="确认续期"
      cancel-text="取消"
    >
      <div class="renew-modal-content" v-if="currentRoom">
        <p class="renew-tip">续期后房间将恢复正常使用。选择续期时长：</p>
        <div class="renew-room-name">
          <span class="label">房间名称：</span>
          <span class="value">{{ currentRoom.roomName }}</span>
        </div>
        <div class="renew-hours-selector">
          <a-radio-group v-model:value="renewHours">
            <a-radio-button v-for="opt in renewHoursOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-radio-button>
          </a-radio-group>
        </div>
      </div>
    </a-modal>

    <!-- 重新开启房间弹窗（仅房主 + CLOSED 状态 + closedTime=null 可见） -->
    <a-modal
      v-model:open="showReopenModal"
      title="重新开启房间"
      :confirm-loading="reopenLoading"
      :width="400"
      centered
      @ok="handleReopenConfirm"
      @cancel="showReopenModal = false"
      ok-text="确认开启"
      cancel-text="取消"
    >
      <div class="renew-modal-content" v-if="currentRoom">
        <p class="renew-tip">重新开启后房间将恢复正常使用。选择过期时长：</p>
        <div class="renew-room-name">
          <span class="label">房间名称：</span>
          <span class="value">{{ currentRoom.roomName }}</span>
        </div>
        <div class="renew-hours-selector">
          <a-radio-group v-model:value="reopenHours">
            <a-radio-button v-for="opt in renewHoursOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-radio-button>
          </a-radio-group>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message as antMessage, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  ArrowLeftOutlined,
  CopyOutlined,
  ClockCircleOutlined,
  KeyOutlined,
  MessageOutlined,
  FolderOutlined,
  PhoneOutlined,
  ReloadOutlined,
  MoreOutlined,
  UserDeleteOutlined,
  LockOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
  TeamOutlined,
  EditOutlined
} from '@ant-design/icons-vue'

// API和工具导入
import { getRoomMembers, updateRoomPassword, updatePasswordVisibility, getRoomCurrentPassword, RoomAPI } from '@/api/room'
import { MessageAPI } from '@/api/message'
import { FileAPI } from '@/api/file'
import { VoiceAPI } from '@/api/voice'
import { useRoomWebSocket } from '@/composables/useRoomWebSocket'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'
import { useChatStore } from '@/stores/chat'
import type { RoomInfo, RoomMember } from '@/types/room'
import { ROOM_STATUS } from '@/types/room'
import {
  memberNameInitial,
  memberPresence,
  normalizeRoomMembersList
} from '@/utils/roomMemberDisplay'
import type { MessageVO, FileVO, MessageQueryDto } from '@/types/api'

// 组件导入
import MessageBubble from '@/components/chat/MessageBubble.vue'
import EmojiOverlay from '@/components/chat/EmojiOverlay.vue'
import type { EmojiParticle } from '@/types/api'
import MessageInput from '@/components/chat/MessageInput.vue'
import FileList from '@/components/file/FileList.vue'
import VoiceCallPanel from '@/components/voice/VoiceCallPanel.vue'
import FriendsDropdown from '@/components/privatechat/FriendsDropdown.vue'
import PrivateChatModal from '@/components/privatechat/PrivateChatModal.vue'
import MemberProfileModal from '@/components/MemberProfileModal.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import RoomPasswordDrawer from '@/components/RoomPasswordDrawer.vue'
import { useVoiceCall } from '@/composables/useVoiceCall'
import { useRoomPassword } from '@/composables/useRoomPassword'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()

// 基础状态
const loading = ref(true)
const globalLoading = ref(false)
const currentRoom = ref<RoomInfo | null>(null)
const activeTab = ref('chat')

// 私聊状态
const privateChatVisible = ref(false)
const privateChatFriendId = ref<number | null>(null)

// 用户资料弹窗状态
const memberProfileVisible = ref(false)
const memberProfileUserId = ref<number | null>(null)

// 续期状态
const showRenewModal = ref(false)
const renewHours = ref(24)
const renewHoursOptions = [
  { value: 1, label: '1小时' },
  { value: 24, label: '1天' },
  { value: 72, label: '3天' },
  { value: 168, label: '7天' }
]
const renewLoading = ref(false)

// 重新开启状态
const showReopenModal = ref(false)
const reopenHours = ref(24)
const reopenLoading = ref(false)
const roomId = computed(() => currentRoom.value?.roomId || 0)
const { 
  roomState,
  isRoomConnected,
  connectToRoom,
  sendRoomMessage,
  replaceMessages 
} = useRoomWebSocket(roomId, {
  onMessage: (message: any) => {
    const normalized = { ...message }
    if (!normalized.createTime) {
      normalized.createTime = message.timestamp || new Date().toISOString()
    }
    // 页面不再push，由房间层维护messages
    if (activeTab.value !== 'chat') {
      unreadCount.value++
    }
    scrollToBottom()
  },
  onMessageDelete: (messageId: number) => {
    // 房间层已处理删除，这里无需重复修改数据
  },
  onFileUpload: () => {
    fileCount.value++
    fileListRefresh.value = !fileListRefresh.value
  },
  onFileDelete: () => {
    fileCount.value = Math.max(0, fileCount.value - 1)
    fileListRefresh.value = !fileListRefresh.value
  },
  onMemberJoin: () => {
    if (currentRoom.value) {
      currentRoom.value = { ...currentRoom.value, currentMembers: currentRoom.value.currentMembers + 1 }
    }
    loadRoomMembers()
  },
  onMemberLeave: () => {
    if (currentRoom.value) {
      currentRoom.value = { ...currentRoom.value, currentMembers: Math.max(0, currentRoom.value.currentMembers - 1) }
    }
    loadRoomMembers()
  },
  onCallStart: (callId: number, initiatorId: number) => {
    notifyCallStart(callId)
  },
  onCallEnd: (callId: number) => {
    notifyCallEnd(callId)
  },
  onSignaling: (data: any) => {
    handleSignaling(data)
  },
  onVoiceRosterUpdate: (callId: number) => {
    handleRosterUpdate(callId)
  },
  onEmojiReceived: (emoji: string, senderNickname: string) => {
    spawnEmojiParticle(emoji, senderNickname)
  },
  onRoomRenewed: (data: { roomId: number; expireTime: string; status: number }) => {
    if (currentRoom.value?.roomId === data.roomId) {
      currentRoom.value = {
        ...currentRoom.value,
        status: data.status,
        expireTime: data.expireTime
      }
      if (data.status === ROOM_STATUS.ACTIVE) {
        antMessage.success('房间已续期')
      }
    }
  },
  onRoomReopened: (data: { roomId: number; expireTime: string; status: number }) => {
    if (currentRoom.value?.roomId === data.roomId) {
      currentRoom.value = {
        ...currentRoom.value,
        status: data.status,
        expireTime: data.expireTime
      }
      if (data.status === ROOM_STATUS.ACTIVE) {
        antMessage.success('房间已重新开启')
      }
    }
  }
})

// 用户信息
const currentUser = computed(() => authStore.user)
const isOwner = computed(() =>
  currentRoom.value && currentUser.value &&
  currentRoom.value.ownerId === currentUser.value.userId
)

// 聊天相关状态
const messages = computed(() => roomState.value.messages as MessageVO[])  // 单一数据源
const unreadCount = ref(0)
const replyMessage = ref<MessageVO | null>(null)
const newMessage = ref('')

// 文件相关状态
const fileCount = ref(0)
const fileListRefresh = ref(false)

// 密码区域是否显示
const showPasswordArea = computed(() => {
  if (!currentRoom.value) return false
  const mode = currentRoom.value.passwordMode
  return !!mode && mode !== 0
})

const {
  passwordHidden,
  currentPassword,
  remainingSeconds,
  resetPasswordState,
  togglePasswordVisibility,
} = useRoomPassword({
  roomId: () => currentRoom.value!.roomId,
  passwordMode: () => currentRoom.value?.passwordMode,
  passwordVisible: () => currentRoom.value?.passwordVisible,
  isOwner: () => !!isOwner.value,
  showPasswordArea: () => showPasswordArea.value,
  loadPasswordApi: (roomId: number) =>
    getRoomCurrentPassword(roomId).then(r => r?.data ?? null),
})

/**
 * 房主切换密码是否对成员可见（调后端接口）
 */
const togglePasswordVisible = async () => {
  if (!currentRoom.value || !isOwner.value) return
  const newVisible = currentRoom.value.passwordVisible === 1 ? 0 : 1
  try {
    await updatePasswordVisibility(currentRoom.value.roomId, newVisible)
    currentRoom.value = { ...currentRoom.value, passwordVisible: newVisible }
    antMessage.success(newVisible === 1 ? '已允许成员查看密码' : '已禁止成员查看密码')
  } catch (e: any) {
    antMessage.error(e?.response?.data?.msg || '操作失败')
  }
}

/** 密码设置抽屉状态 */
const passwordDrawerVisible = ref(false)

/** 密码设置抽屉成功回调：刷新房间信息 */
async function handlePasswordUpdateSuccess() {
  await loadRoomInfo()
  resetPasswordState()
}

// Emoji 漂浮动画状态
const emojiParticles = ref<EmojiParticle[]>([])
const MAX_PARTICLES = 15

// 语音通话状态 - 通过 useVoiceCall composable 管理
const voiceRoomId = computed(() => currentRoom.value?.roomId ?? 0)
const voiceCurrentUserId = computed(() => currentUser.value?.userId ?? 0)
const voiceIsOwner = computed(() => !!(
  currentRoom.value && currentUser.value &&
  currentRoom.value.ownerId === currentUser.value.userId
))

const {
  callState,
  currentCall,
  loading: voiceLoading,
  actionLoading,
  isMuted,
  isSpeakerOff,
  handleOpen,
  handleJoin,
  handleLeave,
  handleEnd,
  handleToggleMute,
  handleToggleSpeaker,
  notifyCallStart,
  notifyCallEnd,
  handleSignaling,
  handleRosterUpdate,
  handleLeaveBeforeUnmount
} = useVoiceCall(voiceRoomId, voiceCurrentUserId, voiceIsOwner)

// 成员相关状态
const roomMembers = ref<RoomMember[]>([])

// userId -> nickname 映射，供语音面板解析参与者昵称
const memberNicknameMap = computed<Record<number, string>>(() => {
  const map: Record<number, string> = {}
  for (const m of roomMembers.value) {
    if (m.userId && m.nickname) map[m.userId] = m.nickname
  }
  return map
})

// 微服务状态管理
interface ServiceState {
  loading: boolean
  error: string | null
  retryCount: number
  available: boolean  // 服务可用性状态
  lastChecked?: number  // 最后检查时间
}

const serviceStates = ref({
  messages: { loading: false, error: null, retryCount: 0, available: true } as ServiceState,
  files: { loading: false, error: null, retryCount: 0, available: true } as ServiceState,
  members: { loading: false, error: null, retryCount: 0, available: true } as ServiceState
})

/**
 * 安全的服务调用包装器
 */
const safeServiceCall = async <T>(
  serviceName: keyof typeof serviceStates.value,
  serviceCall: () => Promise<T>,
  fallbackValue?: T
): Promise<T> => {
  const state = serviceStates.value[serviceName]
  
  try {
    state.loading = true
    state.error = null
    
    const result = await serviceCall()
    
    // 成功时更新状态
    state.available = true
    state.retryCount = 0
    state.lastChecked = Date.now()
    
    return result
  } catch (error: any) {
    // 失败时更新状态
    state.available = false
    state.retryCount++
    state.lastChecked = Date.now()
    
    // 根据错误类型设置不同的错误消息
    if (error.response?.status === 503) {
      state.error = `${serviceName}服务暂时不可用`
    } else if (error.response?.status === 404) {
      state.error = `${serviceName}服务未找到`
    } else {
      state.error = `${serviceName}服务调用失败`
    }
    
    // 返回默认值或重新抛出错误
    if (fallbackValue !== undefined) {
      return fallbackValue
    }
    
    throw error
  } finally {
    state.loading = false
  }
}

/**
 * 计算属性
 */
const statusColor = computed(() => {
  if (!currentRoom.value) return 'default'
  const s = currentRoom.value.status
  if (s === ROOM_STATUS.CLOSED) return 'red'
  if (s === ROOM_STATUS.EXPIRED) return 'orange'
  if (s === ROOM_STATUS.ARCHIVED) return 'default'
  if (s === ROOM_STATUS.DISABLED) return 'error'
  return 'success'
})

const statusText = computed(() => {
  if (!currentRoom.value) return '未知'
  const s = currentRoom.value.status
  if (s === ROOM_STATUS.ACTIVE) return '活跃'
  if (s === ROOM_STATUS.CLOSED) return '已关闭'
  if (s === ROOM_STATUS.EXPIRED) return '已过期（只读）'
  if (s === ROOM_STATUS.ARCHIVED) return '已归档'
  if (s === ROOM_STATUS.DISABLED) return '已禁用'
  return '未知'
})

const expireText = computed(() => {
  if (!currentRoom.value) return '未知'
  
  const expireTime = dayjs(currentRoom.value.expireTime)
  const now = dayjs()
  
  if (expireTime.isBefore(now)) {
    return '已过期'
  }
  
  const diff = expireTime.diff(now, 'hour')
  if (diff < 24) {
    return `${diff} 小时后过期`
  } else {
    const days = Math.ceil(diff / 24)
    return `${days} 天后过期`
  }
})

const callStateText = computed(() => {
  const stateMap: Record<string, string> = {
    locked: '未开启',
    idle: '空闲',
    active: '通话进行中',
    'in-call': '通话中'
  }
  return stateMap[callState.value] ?? callState.value
})

/** 非 ACTIVE 状态禁止写操作（已过期/已关闭/已归档均只读） */
const canWrite = computed(() => currentRoom.value?.status === ROOM_STATUS.ACTIVE)

/**
 * 加载房间信息
 */
const loadRoomInfo = async () => {
  try {
    loading.value = true
    const roomId = route.params.roomId as string
    
    if (!roomId) {
      throw new Error('房间ID不能为空')
    }

    // 将字符串roomId转换为数字
    const roomIdNum = parseInt(roomId, 10)
    if (isNaN(roomIdNum)) {
      throw new Error('无效的房间ID')
    }

    // 先尝试从store中获取房间信息
    const roomStore = useRoomStore()
    let roomInfo = roomStore.currentRoom
    
    // 如果store中没有房间信息或房间ID不匹配，则重新获取
    if (!roomInfo || roomInfo.roomId !== roomIdNum) {
      // 由于后端暂时只提供根据房间码查询的接口，
      // 我们需要先从房间列表中找到对应的房间信息
      if (roomStore.roomList.length === 0) {
        // 如果房间列表为空，先加载房间列表
        await roomStore.fetchUserRooms()
      }
      
      // 从房间列表中查找对应的房间
      roomInfo = roomStore.roomList.find(room => room.roomId === roomIdNum) || null
      
      if (!roomInfo) {
        throw new Error('房间不存在或您没有权限访问')
      }
      
      roomStore.setCurrentRoom(roomInfo)
    }
    
    currentRoom.value = roomInfo
    
    // 独立加载相关数据，实现故障隔离
    // 每个服务调用都有独立的错误处理，确保单个服务故障不影响其他服务
    loadMessages().catch(error => {
      serviceStates.value.messages.error = '消息服务暂时不可用'
    })
    
    loadFileCount().catch(error => {
      serviceStates.value.files.error = '文件服务暂时不可用'
    })
    
    loadRoomMembers().catch(error => {
      serviceStates.value.members.error = '成员服务暂时不可用'
    })
    
  } catch (error: any) {
    console.error('加载房间信息失败:', error)
    antMessage.error(error.response?.data?.msg || error.message || '加载房间信息失败')
    currentRoom.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 加载消息列表
 */
const loadMessages = async () => {
  if (!currentRoom.value) return
  
  serviceStates.value.messages.loading = true
  serviceStates.value.messages.error = null
  
  try {
    // 使用“最新N条后再升序”的接口，后端已保证顺序；前端仍做兜底排序
    const response = await MessageAPI.getLatestMessages(currentRoom.value.roomId, 50)
    const messagesData = response.data || []

    // 兜底：按 createTime 升序
    const sorted = [...messagesData].sort((a: any, b: any) => {
      const ta = new Date(a.createTime as any).getTime()
      const tb = new Date(b.createTime as any).getTime()
      return ta - tb
    })

    // 为消息数据添加 isOwn 字段
    const messagesWithOwnership = sorted.map((message: any) => ({
      ...message,
      isOwn: message.senderId === currentUser.value?.userId
    }))

    // 以房间层为单一数据源
    // 过滤掉 EMOJI 消息，不在聊天气泡中展示
    const filteredMessages = messagesWithOwnership.filter((m: any) => m.messageType !== 5)
    replaceMessages(filteredMessages)
    serviceStates.value.messages.retryCount = 0

    // 初次加载滚动到底部
    scrollToBottom()
  } catch (error: any) {
    console.error('加载消息失败:', error)
    // 确保消息状态重置为空数组而不是undefined
    replaceMessages([])
    serviceStates.value.messages.error = '消息服务暂时不可用，请稍后重试'
    serviceStates.value.messages.retryCount++
    throw error  // 重新抛出错误，用于上层catch处理
  } finally {
    serviceStates.value.messages.loading = false
  }
}

/**
 * 加载文件数量
 */
const loadFileCount = async () => {
  if (!currentRoom.value) return
  
  serviceStates.value.files.loading = true
  serviceStates.value.files.error = null
  
  try {
    const response = await FileAPI.getRoomFileStats(currentRoom.value.roomId)
    fileCount.value = response.data.fileCount || 0
    serviceStates.value.files.retryCount = 0
  } catch (error: any) {
    console.error('加载文件统计失败:', error)
    serviceStates.value.files.error = '文件服务暂时不可用，文件功能已禁用'
    serviceStates.value.files.retryCount++
    // 服务不可用时保持现有的fileCount值，避免显示错误数据
    throw error  // 重新抛出错误，用于上层catch处理
  } finally {
    serviceStates.value.files.loading = false
  }
}

/**
 * 加载房间成员
 */
const loadRoomMembers = async () => {
  if (!currentRoom.value) return
  
  serviceStates.value.members.loading = true
  serviceStates.value.members.error = null
  
  try {
    const response = await getRoomMembers(currentRoom.value.roomId)
    roomMembers.value = normalizeRoomMembersList(response.data)
    serviceStates.value.members.retryCount = 0
  } catch (error: any) {
    console.error('加载房间成员失败:', error)
    // 确保成员状态重置为空数组
    roomMembers.value = []
    serviceStates.value.members.error = '成员服务暂时不可用，成员信息无法显示'
    serviceStates.value.members.retryCount++
    throw error  // 重新抛出错误，用于上层catch处理
  } finally {
    serviceStates.value.members.loading = false
  }
}

/**
 * 刷新成员列表
 */
const refreshMembers = () => {
  loadRoomMembers()
}

/**
 * 重试加载指定服务
 */
const retryService = (serviceName: 'messages' | 'files' | 'members') => {
  switch (serviceName) {
    case 'messages':
      loadMessages()
      break
    case 'files':
      loadFileCount()
      break
    case 'members':
      loadRoomMembers()
      break
  }
}

/**
 * 初始化房间订阅（新架构 - 已通过Composable自动处理）
 */
const initRoomSubscription = async () => {
  if (!currentRoom.value || !currentUser.value) return
}

/**
 * 处理消息发送（新架构）
 */
const handleSendMessage = async (messageData: any) => {
  if (!canWrite.value) {
    antMessage.warning('房间已过期或已关闭，无法发送消息')
    return
  }
  try {
    if (!currentRoom.value) {
      throw new Error('房间信息不存在')
    }
    const payload = {
      roomId: currentRoom.value.roomId,
      ...messageData
    }
    await MessageAPI.sendMessage(payload)
  } catch (error: any) {
    antMessage.error(error.response?.data?.msg || '发送消息失败')
  }
}

/**
 * 处理消息回复
 */
const handleReply = (message: MessageVO) => {
  replyMessage.value = message
  activeTab.value = 'chat'
}

/**
 * 处理消息删除
 */
const handleDeleteMessage = async (message: MessageVO) => {
  try {
    await MessageAPI.deleteMessage(message.messageId)
    // 删除事件会通过WebSocket推送
  } catch (error: any) {
    antMessage.error(error.response?.data?.msg || '删除消息失败')
  }
}

/**
 * 处理消息撤回
 */
const handleRecallMessage = async (message: MessageVO) => {
  try {
    await MessageAPI.recallMessage(message.messageId)
    antMessage.success('消息已撤回')
  } catch (error: any) {
    antMessage.error(error.response?.data?.msg || '撤回消息失败')
  }
}

/**
 * 处理文件上传成功
 */
const handleFileUploadSuccess = (file: FileVO) => {
  fileCount.value++
  fileListRefresh.value = !fileListRefresh.value
  antMessage.success(`${file.fileName} 上传成功`)
}

/**
 * 处理文件上传失败
 */
const handleFileUploadError = (error: string) => {
  antMessage.error(error)
}

/**
 * 处理文件上传进度
 */
const handleFileUploadProgress = (progress: any) => {
  // 可以在这里显示全局上传进度
}

/**
 * 处理文件上传进度（消息输入）
 */
const handleUploadProgress = (progress: any) => {
  globalLoading.value = progress.percent < 100
}

/**
 * 处理文件选择
 */
const handleFileSelected = (file: FileVO) => {
  // 可以在这里处理文件选择事件
}

/**
 * 处理文件删除
 */
const handleFileDeleted = (fileId: number) => {
  fileCount.value = Math.max(0, fileCount.value - 1)
}

// 通话状态变化现在由 useVoiceCall composable 直接管理

/**
 * 踢出成员
 */
const kickMember = async (member: RoomMember) => {
  Modal.confirm({
    title: '确认操作',
    content: `确定要将 ${member.nickname} 移出房间吗？`,
    okText: '确定移出',
    okType: 'danger',
    onOk: async () => {
      try {
        if (!currentRoom.value) return
        await RoomAPI.kickMember(currentRoom.value.roomId, member.userId)
        antMessage.success('成员已移出')
        await loadRoomMembers()
      } catch (error: any) {
        antMessage.error(error.response?.data?.msg || '操作失败')
      }
    }
  })
}

/**
 * 复制房间码
 */
const copyRoomCode = async () => {
  if (!currentRoom.value) return
  
  try {
    await navigator.clipboard.writeText(currentRoom.value.roomCode)
    antMessage.success('房间码已复制到剪贴板')
  } catch (error) {
    antMessage.error('复制失败')
  }
}

async function handleRenewConfirm() {
  if (!currentRoom.value) return
  renewLoading.value = true
  try {
    await roomStore.renewRoom(currentRoom.value.roomId, renewHours.value)
    currentRoom.value = { ...currentRoom.value, status: ROOM_STATUS.ACTIVE }
    showRenewModal.value = false
    antMessage.success('续期成功，房间已恢复正常')
  } catch (e: any) {
    antMessage.error(e?.response?.data?.msg || e?.message || '续期失败，请重试')
  } finally {
    renewLoading.value = false
  }
}

async function handleReopenConfirm() {
  if (!currentRoom.value) return
  reopenLoading.value = true
  try {
    await roomStore.reopenRoom(currentRoom.value.roomId, reopenHours.value)
    currentRoom.value = { ...currentRoom.value, status: ROOM_STATUS.ACTIVE }
    showReopenModal.value = false
    antMessage.success('房间已重新开启')
  } catch (e: any) {
    antMessage.error(e?.response?.data?.msg || e?.message || '重新开启失败，请重试')
  } finally {
    reopenLoading.value = false
  }
}

/**
 * 返回上一页
 */
/**
 * 返回大厅：若正在通话则先退出通话，再导航
 */
const goBack = async () => {
  await handleLeaveBeforeUnmount()
  router.push('/rooms')
}

function handleOpenPrivateChat(friendId: number) {
  privateChatFriendId.value = friendId
  privateChatVisible.value = true
}

function openMemberProfile(userId: number) {
  memberProfileUserId.value = userId
  memberProfileVisible.value = true
}

function handleRefreshFriends() {
  chatStore.fetchFriends()
  chatStore.fetchIncomingRequests()
}

/**
 * 格式化时间
 */
const formatTime = (timeInput: any) => {
  if (!timeInput) return ''
  let d
  if (typeof timeInput === 'number') {
    d = dayjs(timeInput)
  } else if (typeof timeInput === 'string' && /^\d+$/.test(timeInput)) {
    d = dayjs(Number(timeInput))
  } else {
    d = dayjs(timeInput)
  }
  if (!d.isValid()) return ''
  return d.format('MM-DD HH:mm')
}

/**
 * 获取状态文本
 */
const getStatusText = (status: string): string => {
  const statusMap: Record<string, string> = {
    online: '在线',
    offline: '离线',
    away: '离开'
  }
  return statusMap[status] || status
}

/**
 * 滚动到底部
 */
/**
 * 生成一个 Emoji 漂浮粒子
 */
function spawnEmojiParticle(emoji: string, senderNickname: string) {
  if (emojiParticles.value.length >= MAX_PARTICLES) {
    emojiParticles.value = emojiParticles.value.slice(1)
  }
  emojiParticles.value = [...emojiParticles.value, {
    id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    emoji,
    senderNickname,
    x: Math.random() * 80 + 5,
    size: Math.floor(Math.random() * 24) + 32,
    duration: Math.floor(Math.random() * 1500) + 2500
  }]
}

/**
 * 移除已完成动画的粒子
 */
function removeParticle(id: string) {
  emojiParticles.value = emojiParticles.value.filter(p => p.id !== id)
}

/**
 * 发送 Emoji 互动消息
 */
async function sendEmoji(emoji: string) {
  try {
    if (!currentRoom.value) return
    await MessageAPI.sendMessage({
      roomId: currentRoom.value.roomId,
      messageType: 5,
      content: emoji
    })
  } catch (error: any) {
    antMessage.error(error.response?.data?.msg || 'Emoji 发送失败')
  }
}

/**
 * 滚动到底部（使用 requestAnimationFrame 避免布局抖动）
 */
const scrollToBottom = () => {
  requestAnimationFrame(() => {
    nextTick(() => {
      const messageContainer = document.querySelector('.message-list-container')
      if (messageContainer) {
        messageContainer.scrollTop = messageContainer.scrollHeight
      }
    })
  })
}

// 监听标签页切换，清除未读消息数
const handleTabChange = (key: string) => {
  if (key === 'chat') {
    unreadCount.value = 0
  }
}

// 生命周期
onMounted(async () => {
  await loadRoomInfo()
  if (currentRoom.value) {
    await initRoomSubscription()
  }
  if (authStore.user) {
    chatStore.initChat()
  }
})
</script>

<style scoped lang="scss">
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

.room-detail-view {
  position: relative;
  min-height: 100vh;
  background-image: url('/bg-main.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

/* 背景遮罩：统一使用 --bg-overlay，浅色白色遮罩保留背景图可见，深色深色遮罩实现暗色沉浸 */
.room-detail-view::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--bg-overlay);
  z-index: 0;
}

  .loading-container {
    position: relative;
    z-index: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 50vh;

    p {
      margin-top: 16px;
      color: var(--text-muted);
    }
  }

  .room-detail-content {
    position: relative;
    z-index: 1;
    max-width: 1400px;
    margin: 0 auto;
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 24px;
    height: calc(100vh - 48px);

    .room-header {
      flex-shrink: 0;
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      padding: 24px;
      background: var(--surface-card);
      border-radius: 12px;
      box-shadow: var(--shadow-sm);

      .header-left {
        display: flex;
        align-items: center;
        gap: 16px;

        .back-btn {
          border-radius: 8px;
        }

        .room-title {
          h1 {
            margin: 0;
            color: var(--text-primary);
            font-size: 24px;
            font-weight: 600;
          }

          p {
            margin: 4px 0 0 0;
            color: var(--text-muted);
            font-size: 14px;
          }
        }
      }

      .header-right {
        display: flex;
        align-items: center;
        gap: 12px;

        .room-status {
          font-weight: 500;
        }
      }
    }

    .room-info-cards {
      flex-shrink: 0;
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 16px;

      .info-card {
        padding: 20px;
        background: var(--surface-card);
        border-radius: 12px;
        box-shadow: var(--shadow-sm);
        transition: all 0.3s ease;

      &:hover {
        box-shadow: var(--shadow-md);
      }

        &.active-call {
          border: 2px solid var(--brand-accent);
          background: linear-gradient(135deg, rgba(var(--brand-accent-rgb), 0.06) 0%, var(--surface-card) 100%);
        }

        .card-header {
          display: flex;
          align-items: center;
          gap: 8px;
          margin-bottom: 12px;

          .card-icon {
            font-size: 18px;
            color: var(--brand-primary);
          }

          h3 {
            margin: 0;
            color: var(--text-primary);
            font-size: 14px;
            font-weight: 500;
          }
        }

        .card-content {
          .member-count,
          .expire-time,
          .room-code,
          .call-status {
            font-size: 16px;
            font-weight: 600;
            color: var(--text-primary);
          }

          .room-code {
            display: flex;
            align-items: center;
            gap: 8px;
            cursor: pointer;
            transition: color 0.2s;

            &:hover {
              color: var(--brand-primary);
            }

            .copy-icon {
              font-size: 14px;
            }
          }

          &.password-card {
            border: 1px solid rgba(var(--brand-primary-rgb), 0.2);

            &:hover {
              border-color: var(--brand-primary);
            }
            }

            .card-icon {
              color: var(--brand-primary);
            }
          }
          
          .password-card-content {
            display: flex;
            align-items: center;
            line-height: 1;

            .password-display {
              display: flex;
              align-items: center;
              line-height: 1;
              flex: 1;
              gap: 10px;

              .password-value {
                font-family: 'Courier New', monospace;
                font-size: 16px;
                font-weight: 700;
                color: var(--brand-primary);
                letter-spacing: 1.5px;
                line-height: 1;
                vertical-align: middle;

                &.hidden {
                  color: var(--text-muted);
                  letter-spacing: 3px;
                }

                .totp-timer {
                  font-size: 12px;
                  color: var(--color-warning);
                  margin-left: 6px;
                  font-weight: 400;
                  letter-spacing: 0;
                }
              }

              .password-toggle,
              .member-visibility-toggle {
                cursor: pointer;
                font-size: 16px;
                transition: color 0.2s;
                flex-shrink: 0;
                display: flex;
                align-items: center;
                line-height: 1;

                &:hover {
                  color: var(--brand-primary);
                }
              }

              .password-toggle {
                color: var(--text-muted);
              }

              .member-visibility-toggle {
                padding-left: 8px;
                border-left: 1px solid var(--border-light);
              }

              .edit-password-btn {
                margin-left: auto;
                cursor: pointer;
                font-size: 14px;
                color: var(--text-muted);
                transition: color 0.2s;
                padding: 2px 6px;
                border-radius: 4px;

                &:hover {
                  color: var(--brand-primary);
                  background: var(--brand-primary-10);
                }
              }
            }
          }

          .call-status {
            display: flex;
            align-items: center;
            gap: 8px;

            .status-indicator {
              width: 8px;
              height: 8px;
              border-radius: 50%;

              &.idle {
                background: var(--border-default);
              }

              &.calling {
                background: var(--color-warning);
                animation: pulse 2s infinite;
              }

              &.in-call {
                background: var(--brand-accent);
                animation: pulse 2s infinite;
              }
            }
          }
        }

        .card-loading {
          display: flex;
          align-items: center;
          gap: 8px;
          color: var(--text-muted);
          font-size: 14px;
        }

        .card-error {
          display: flex;
          flex-direction: column;
          gap: 4px;

          .error-text {
            color: var(--color-error);
            font-size: 12px;
          }
        }
      }
    }

    .communication-section {
      flex: 1;
      min-height: 0;
      background: var(--surface-card);
      border-radius: 12px;
      box-shadow: var(--shadow-sm);
      overflow: hidden;
      display: flex;
      flex-direction: column;

      /* 上部：标签栏 */
      .section-header {
        flex-shrink: 0;

        .communication-tabs {
          :deep(.ant-tabs-nav) {
            margin: 0;
            background: var(--surface-bg);
            padding: 12px 12px 0;

            &::before {
              display: none;
            }

            .ant-tabs-tabs-list {
              gap: 4px;
            }

            .ant-tabs-tab {
              padding: 10px 20px;
              border-radius: 10px 10px 0 0;
              border: 1px solid transparent;
              border-bottom: none;
              background: transparent;
              margin: 0;
              transition: background 0.2s ease, border-color 0.2s ease;

              &:hover:not(.ant-tabs-tab-active) {
                background: var(--brand-primary-subtle);
                border-color: var(--border-light);
              }

              .tab-title {
                display: flex;
                align-items: center;
                gap: 8px;
                font-weight: 500;
                color: var(--text-muted);
                transition: color 0.2s ease;

                .tab-badge {
                  :deep(.ant-badge-count) {
                    font-size: 10px;
                    min-width: 16px;
                    height: 16px;
                    line-height: 16px;
                  }
                }
              }
            }

            .ant-tabs-tab-active {
              background: var(--surface-card);
              border-color: var(--border-light);
              border-bottom-color: var(--surface-card);
              box-shadow: var(--shadow-sm);

              .tab-title {
                color: var(--brand-primary);
              }
            }
          }

          :deep(.ant-tabs-content-holder) {
            border-top: 1px solid var(--border-light);
            margin-top: -1px;
          }

          :deep(.ant-tabs-content) {
            display: none;
          }
        }
      }

      /* 中部：内容区域 */
      .section-body {
        flex: 1;
        min-height: 0;
        overflow: hidden;
        display: flex;
        flex-direction: column;

        .service-loading,
        .service-error {
          flex: 1;
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 20px;

          p {
            margin-top: 16px;
            color: var(--text-muted);
          }
        }
      }

      /* 下部：输入区 */
      .section-footer {
        flex-shrink: 0;
        border-top: 1px solid var(--border-light);
      }

      /* 内容区底部视觉分隔（footer 隐藏时提供视觉收束） */
      .section-body {
        &::after {
          content: '';
          display: block;
          flex-shrink: 0;
          height: 1px;
          background: var(--border-light);
          margin-top: auto;
        }
      }

      /* 消息列表 */
      .message-list-wrapper {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        display: flex;
        flex-direction: column;

        .message-list-container {
          flex: 1;
          padding: 16px;
          display: flex;
          flex-direction: column;

          .message-items {
            flex: none;
            display: flex;
            flex-direction: column;
          }

          .empty-messages {
            flex: 1;
            display: flex;
            align-items: center;
            justify-content: center;
          }
        }
      }

      .file-list-section {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }

      .files-container {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }

      .members-container {
        flex: 1;
        padding: 16px;
        overflow-y: auto;
        min-height: 0;

        .members-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 16px;

          h4 {
            margin: 0;
            color: var(--text-primary);
            font-size: 16px;
            font-weight: 500;
          }
        }

        .members-list {
          .member-item {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 12px;
            border: 1px solid var(--border-light);
            border-radius: 8px;
            margin-bottom: 8px;
            transition: all 0.2s;

            &:hover {
              background: var(--surface-bg);
              border-color: var(--border-default);
            }

            .member-avatar {
              position: relative;
              flex-shrink: 0;

              :deep(.ant-avatar) {
                border: 2px solid var(--border-light);
                object-fit: cover;
              }

              :deep(.ant-avatar-image img) {
                object-fit: cover;
              }

              .online-indicator {
                position: absolute;
                bottom: 0;
                right: 0;
                width: 12px;
                height: 12px;
                background: var(--brand-accent);
                border: 2px solid var(--surface-bg);
                border-radius: 50%;
              }
            }

            .member-info {
              flex: 1;

              .member-name {
                font-weight: 500;
                margin-bottom: 4px;
              }

              .member-meta {
                display: flex;
                gap: 12px;
                font-size: 12px;
                color: var(--text-muted);

                .status {
                  &.online {
                    color: var(--brand-accent);
                  }

                  &.offline {
                    color: var(--text-muted);
                  }

                  &.away {
                    color: var(--color-warning);
                  }
                }
              }
            }
          }
        }
      }
    }
  

  .room-not-found {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 60vh;

    .not-found-content {
      text-align: center;

      .not-found-icon {
        font-size: 64px;
        margin-bottom: 16px;
      }

      h3 {
        color: var(--text-primary);
        margin-bottom: 8px;
      }

      p {
        color: var(--text-muted);
        margin-bottom: 24px;
      }
    }
  }

  .global-loading {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--surface-overlay);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 9999;
  }

@keyframes pulse {
  0% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
  100% {
    opacity: 1;
  }
}

@media (max-width: 768px) {
  .room-detail-view {
    .room-detail-content {
      padding: 16px;

      .room-header {
        flex-direction: column;
        gap: 16px;

        .header-left {
          width: 100%;
        }

        .header-right {
          width: 100%;
          justify-content: flex-start;
        }
      }

      .room-info-cards {
        grid-template-columns: repeat(2, 1fr);
      }

      .communication-section {
        .section-header {
          .communication-tabs {
            :deep(.ant-tabs-tab) {
              padding: 10px 12px;
            }
          }
        }

        .message-list-wrapper,
        .files-container,
        .voice-container,
        .members-container {
          padding: 12px;
        }
      }
    }
  }
}

@media (prefers-reduced-motion: reduce) {
  .info-card {
    transition: none;

    &:hover {
      transform: none;
    }
  }

  .status-indicator {
    &.calling,
    &.in-call {
      animation: none;
    }
  }
}

/* 续期弹窗样式 */
.renew-modal-content {
  padding: 8px 0;
}

.renew-tip {
  margin: 0 0 16px;
  color: var(--text-secondary);
  font-size: 14px;
}

.renew-room-name {
  margin-bottom: 16px;
  padding: 8px 12px;
  background: var(--surface-bg);
  border-radius: 6px;
}

.renew-room-name .label {
  color: var(--text-muted);
  font-size: 13px;
}

.renew-room-name .value {
  color: var(--text-primary);
  font-weight: 500;
}

.renew-hours-selector {
  margin-top: 8px;
}

.renew-hours-selector :deep(.ant-radio-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.renew-hours-selector :deep(.ant-radio-button-wrapper) {
  flex: 1;
  text-align: center;
  min-width: 70px;
}
</style>
