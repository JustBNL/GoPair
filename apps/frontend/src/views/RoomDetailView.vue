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
        <a-tabs
          v-model:activeKey="activeTab"
          type="card"
          class="communication-tabs"
          :tab-bar-style="{ background: '#fafafa', margin: 0 }"
        >
          <!-- 聊天标签页 -->
          <a-tab-pane key="chat" class="tab-pane">
            <template #tab>
              <span class="tab-title">
                <MessageOutlined />
                聊天
                <a-badge v-if="unreadCount > 0" :count="unreadCount" class="tab-badge" />
              </span>
            </template>
            <div class="chat-container">
              <div class="chat-content">
                <!-- 消息加载状态 -->
                <div v-if="serviceStates.messages.loading" class="service-loading">
                  <a-spin size="large" />
                  <p>加载消息中...</p>
                </div>
                
                <!-- 消息加载失败 -->
                <div v-else-if="serviceStates.messages.error" class="service-error">
                  <a-result status="warning" :title="serviceStates.messages.error">
                    <template #extra>
                      <a-button type="primary" @click="retryService('messages')">
                        重试
                      </a-button>
                    </template>
                  </a-result>
                </div>
                
                <!-- 正常的消息显示 -->
                <div v-else class="message-list-container">
                  <div v-if="!messages || messages.length === 0" class="empty-messages">
                    <a-empty description="暂无消息，开始聊天吧！" />
                  </div>
                  <div v-else class="message-items">
                    <message-bubble
                      v-for="message in messages"
                      :key="message.messageId"
                      :message="message"
                      :show-sender-info="true"
                      @reply="handleReply"
                      @delete="(id) => handleDeleteMessage(messages.find(m => m.messageId === id)!)"
                      @recall="(id) => handleRecallMessage(messages.find(m => m.messageId === id)!)"
                    />
                  </div>
                </div>
                <!-- Emoji 互动栏 -->
                <div class="emoji-bar-container">
                  <emoji-bar @send-emoji="sendEmoji" />
                </div>
                <div class="message-input-container">
                  <message-input
                    v-if="currentRoom"
                    :room-id="currentRoom.roomId"
                    :reply-message="replyMessage"
                    :disabled="serviceStates.messages.error !== null"
                    @send-message="handleSendMessage"
                    @cancel-reply="() => (replyMessage = null)"
                    @upload-progress="handleUploadProgress"
                  />
                </div>
              </div>
            </div>
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
            <div class="files-container">
              <!-- 文件服务加载状态 -->
              <div v-if="serviceStates.files.loading" class="service-loading">
                <a-spin size="large" />
                <p>加载文件信息中...</p>
              </div>
              
              <!-- 文件服务加载失败 -->
              <div v-else-if="serviceStates.files.error" class="service-error">
                <a-result status="warning" :title="serviceStates.files.error">
                  <template #extra>
                    <a-button type="primary" @click="retryService('files')">
                      重试
                    </a-button>
                  </template>
                </a-result>
              </div>
              
              <!-- 正常的文件功能 -->
              <div v-else>
                <!-- 文件上传区域 -->
                <!-- 文件列表 -->
                <div class="file-list-section">
                  <file-list
                    :room-id="currentRoom.roomId"
                    :refresh="fileListRefresh"
                    @file-selected="handleFileSelected"
                    @file-deleted="handleFileDeleted"
                  />
                </div>
              </div>
            </div>
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
            <div class="voice-container">
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
                @open="handleOpen"
                @join="handleJoin"
                @leave="handleLeave"
                @end="handleEnd"
                @toggle-mute="handleToggleMute"
                @toggle-speaker="handleToggleSpeaker"
              />
            </div>
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
            <div class="members-container">
              <!-- 成员加载状态 -->
              <div v-if="serviceStates.members.loading" class="service-loading">
                <a-spin size="large" />
                <p>加载成员信息中...</p>
              </div>
              
              <!-- 成员加载失败 -->
              <div v-else-if="serviceStates.members.error" class="service-error">
                <a-result status="warning" :title="serviceStates.members.error">
                  <template #extra>
                    <a-button type="primary" @click="retryService('members')">
                      重试
                    </a-button>
                  </template>
                </a-result>
              </div>
              
              <!-- 正常的成员显示 -->
              <div v-else>
                <div class="members-header">
                  <h4>房间成员</h4>
                  <a-button type="text" size="small" @click="refreshMembers">
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
                    <div class="member-avatar">
                      <a-avatar :size="40">
                        {{ member.displayName?.charAt(0) || 'U' }}
                      </a-avatar>
                      <div v-if="member.status === 'online'" class="online-indicator"></div>
                    </div>
                    
                    <div class="member-info">
                      <div class="member-name">
                        {{ member.displayName }}
                        <a-tag v-if="member.isOwner" color="gold" size="small">
                          房主
                        </a-tag>
                        <a-tag v-if="member.userId === currentUser?.userId" color="blue" size="small">
                          我
                        </a-tag>
                      </div>
                      <div class="member-meta">
                        <span class="join-time">{{ formatTime(member.joinTime) }}</span>
                        <span :class="['status', member.status || 'offline']">
                          {{ getStatusText(member.status || 'offline') }}
                        </span>
                      </div>
                    </div>

                    <!-- 成员操作 -->
                    <div class="member-actions">
                      <a-dropdown v-if="isOwner && member.userId !== currentUser?.userId">
                        <a-button type="text" size="small">
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
          </a-tab-pane>
        </a-tabs>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message as antMessage, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  ArrowLeftOutlined,
  CopyOutlined,
  TeamOutlined,
  ClockCircleOutlined,
  KeyOutlined,
  MessageOutlined,
  FolderOutlined,
  PhoneOutlined,
  ReloadOutlined,
  MoreOutlined,
  UserDeleteOutlined
} from '@ant-design/icons-vue'

// API和工具导入
import { getRoomMembers } from '@/api/room'
import { MessageAPI } from '@/api/message'
import { FileAPI } from '@/api/file'
import { VoiceAPI } from '@/api/voice'
import { useRoomWebSocket } from '@/composables/useRoomWebSocket'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'
import type { RoomInfo, RoomMember } from '@/types/room'
import type { MessageVO, FileVO, MessageQueryDto } from '@/types/api'

// 组件导入
import MessageBubble from '@/components/chat/MessageBubble.vue'
import EmojiOverlay from '@/components/chat/EmojiOverlay.vue'
import EmojiBar from '@/components/chat/EmojiBar.vue'
import type { EmojiParticle } from '@/types/api'
import MessageInput from '@/components/chat/MessageInput.vue'
import FileList from '@/components/file/FileList.vue'
import VoiceCallPanel from '@/components/voice/VoiceCallPanel.vue'
import { useVoiceCall } from '@/composables/useVoiceCall'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 基础状态
const loading = ref(true)
const globalLoading = ref(false)
const currentRoom = ref<RoomInfo | null>(null)
const activeTab = ref('chat')

// WebSocket状态 - 使用新的Composable架构
const roomId = computed(() => currentRoom.value?.roomId || 0)
const { 
  roomState,
  isRoomConnected,
  connectToRoom,
  sendRoomMessage,
  replaceMessages 
} = useRoomWebSocket(roomId, {
  onMessage: (message: any) => {
    console.log('📨 收到聊天消息:', message)
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
    console.log('🗑️ 收到消息删除事件:', messageId)
    // 房间层已处理删除，这里无需重复修改数据
  },
  onFileUpload: () => {
    console.log('📁 收到文件上传事件')
    fileCount.value++
    fileListRefresh.value = !fileListRefresh.value
  },
  onFileDelete: () => {
    console.log('🗑️ 收到文件删除事件')
    fileCount.value = Math.max(0, fileCount.value - 1)
    fileListRefresh.value = !fileListRefresh.value
  },
  onMemberJoin: () => {
    console.log('👋 收到成员加入事件')
    loadRoomMembers()
  },
  onMemberLeave: () => {
    console.log('👋 收到成员离开事件')
    loadRoomMembers()
  },
  onCallStart: (callId: number, initiatorId: number) => {
    console.log('received call_start:', callId, initiatorId)
    notifyCallStart(callId)
  },
  onCallEnd: (callId: number) => {
    console.log('received call_end:', callId)
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
    console.warn(`${serviceName}服务调用失败:`, error)
    
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
  return currentRoom.value.status === 0 ? 'success' : 'default'
})

const statusText = computed(() => {
  if (!currentRoom.value) return '未知'
  return currentRoom.value.status === 0 ? '活跃' : '已关闭'
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
      console.warn('消息服务不可用:', error)
      serviceStates.value.messages.error = '消息服务暂时不可用'
    })
    
    loadFileCount().catch(error => {
      console.warn('文件服务不可用:', error)
      serviceStates.value.files.error = '文件服务暂时不可用'
    })
    
    loadRoomMembers().catch(error => {
      console.warn('成员服务不可用:', error)
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
    roomMembers.value = response.data || []  // 确保始终是数组
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
  
  try {
    console.log('🎯 新架构：房间WebSocket自动管理:', currentRoom.value.roomId)
    // 所有订阅逻辑已通过useRoomWebSocket composable自动处理
    // 无需手动管理事件处理器和清理逻辑
    console.log('✅ 房间订阅初始化完成（自动管理）')
  } catch (error) {
    console.error('❌ 房间订阅初始化失败:', error)
  }
}

/**
 * 处理消息发送（新架构）
 */
const handleSendMessage = async (messageData: any) => {
  try {
    if (!currentRoom.value) {
      throw new Error('房间信息不存在')
    }
    // 始终通过HTTP发送，由后端持久化并通过WebSocket广播
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
 * 简单的发送消息
 */
const sendMessage = async () => {
  if (!newMessage.value.trim() || !currentRoom.value) {
    console.warn('消息内容为空或房间信息不存在')
    return
  }
  
  const messageData = {
    roomId: currentRoom.value.roomId,
    messageType: 1, // TEXT
    content: newMessage.value.trim()
  }
  
  console.log('发送消息请求:', messageData)
  
  try {
    const response = await MessageAPI.sendMessage(messageData)
    console.log('消息发送成功:', response)
    newMessage.value = ''
  } catch (error: any) {
    console.error('发送消息失败:', error)
    console.error('错误详情:', {
      status: error.response?.status,
      statusText: error.response?.statusText,
      data: error.response?.data,
      config: error.config
    })
    
    // 提供更详细的错误信息
    if (error.response?.status === 500) {
      antMessage.error('服务器内部错误，请检查网络连接或稍后重试')
    } else if (error.response?.status === 404) {
      antMessage.error('消息服务不可用，请联系管理员')
    } else if (error.response?.status === 401) {
      antMessage.error('请重新登录后再试')
    } else {
      antMessage.error(error.response?.data?.msg || error.message || '发送消息失败')
    }
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
const kickMember = (member: RoomMember) => {
  Modal.confirm({
    title: '确认操作',
    content: `确定要将 ${member.displayName} 移出房间吗？`,
    onOk: async () => {
      try {
        // TODO: 实现踢出成员API
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

const scrollToBottom = () => {
  nextTick(() => {
    const messageContainer = document.querySelector('.message-list-container')
    if (messageContainer) {
      messageContainer.scrollTop = messageContainer.scrollHeight
    }
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
    await initRoomSubscription() // 改为房间订阅
  }
})

onUnmounted(() => {
  // 新架构：自动清理已通过useRoomWebSocket composable处理
  console.log('🧹 房间组件卸载（自动清理）')
})
</script>

<style scoped lang="scss">
.room-detail-view {
  min-height: 100vh;
  background: #f5f5f5;
  
  .loading-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 50vh;
    
    p {
      margin-top: 16px;
      color: #8c8c8c;
    }
  }
  
  .room-detail-content {
    max-width: 1400px;
    margin: 0 auto;
    padding: 24px;
    
    .room-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 24px;
      padding: 24px;
      background: white;
      border-radius: 12px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      
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
            color: #262626;
            font-size: 24px;
            font-weight: 600;
          }
          
          p {
            margin: 4px 0 0 0;
            color: #8c8c8c;
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
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
      
      .info-card {
        padding: 20px;
        background: white;
        border-radius: 12px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        transition: all 0.3s ease;
        
        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
        }
        
        &.active-call {
          border: 2px solid #52c41a;
          background: linear-gradient(135deg, #f6ffed 0%, #fff 100%);
        }
        
        .card-header {
          display: flex;
          align-items: center;
          gap: 8px;
          margin-bottom: 12px;
          
          .card-icon {
            font-size: 18px;
            color: #1890ff;
          }
          
          h3 {
            margin: 0;
            color: #262626;
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
            color: #262626;
          }
          
          .room-code {
            display: flex;
            align-items: center;
            gap: 8px;
            cursor: pointer;
            transition: color 0.2s;
            
            &:hover {
              color: #1890ff;
            }
            
            .copy-icon {
              font-size: 14px;
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
                background: #d9d9d9;
              }
              
              &.calling {
                background: #faad14;
                animation: pulse 2s infinite;
              }
              
              &.in-call {
                background: #52c41a;
                animation: pulse 2s infinite;
              }
            }
          }
        }
        
        // 卡片状态样式
        .card-loading {
          display: flex;
          align-items: center;
          gap: 8px;
          color: #8c8c8c;
          font-size: 14px;
        }
        
        .card-error {
          display: flex;
          flex-direction: column;
          gap: 4px;
          
          .error-text {
            color: #ff4d4f;
            font-size: 12px;
          }
        }
      }
    }
    
    .communication-section {
      background: white;
      border-radius: 12px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      overflow: hidden;
      
      .communication-tabs {
        :deep(.ant-tabs-nav) {
          margin: 0;
          background: #fafafa;
          
          .ant-tabs-tab {
            padding: 12px 16px;
            border-radius: 0;
            
            .tab-title {
              display: flex;
              align-items: center;
              gap: 8px;
              font-weight: 500;
              
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
            background: white;
            
            .tab-title {
              color: #1890ff;
            }
          }
        }
        
        :deep(.ant-tabs-content-holder) {
          .ant-tabs-content {
            height: 600px;
            
            .tab-pane {
              height: 100%;
              padding: 0;
            }
          }
        }
      }
      
      // 聊天容器
      .chat-container {
        height: 100%;
        display: flex;
        flex-direction: column;
        
        .chat-content {
          height: 100%;
          display: flex;
          flex-direction: column;
          
          .service-loading,
          .service-error {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            height: 100%;
            padding: 20px;
            
            p {
              margin-top: 16px;
              color: #8c8c8c;
            }
          }

          .message-list-container {
            flex: 1;
            overflow-y: auto;
            padding: 16px;
            
            .message-item {
              margin-bottom: 12px;
              
              &:last-child {
                margin-bottom: 0;
              }
            }
            
            .empty-messages {
              height: 100%;
              display: flex;
              align-items: center;
              justify-content: center;
            }
          }
          
          .message-input-container {
            border-top: 1px solid #f0f0f0;
            padding: 16px;
          }
        }
      }
      
      // 文件容器
      .files-container {
        height: 100%;
        display: flex;
        flex-direction: column;
        
        .file-list-section {
          flex: 1;
          overflow: hidden;
          padding: 16px;
        }
      }
      
      // 语音容器
      .voice-container {
        height: 100%;
        padding: 16px;
        overflow-y: auto;
      }
      
      // 成员容器
      .members-container {
        height: 100%;
        padding: 16px;
        
        .members-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 16px;
          
          h4 {
            margin: 0;
            color: #262626;
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
            border: 1px solid #f0f0f0;
            border-radius: 8px;
            margin-bottom: 8px;
            transition: all 0.2s;
            
            &:hover {
              background: #fafafa;
              border-color: #d9d9d9;
            }
            
            .member-avatar {
              position: relative;
              
              .online-indicator {
                position: absolute;
                bottom: 0;
                right: 0;
                width: 12px;
                height: 12px;
                background: #52c41a;
                border: 2px solid white;
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
                color: #8c8c8c;
                
                .status {
                  &.online {
                    color: #52c41a;
                  }
                  
                  &.offline {
                    color: #8c8c8c;
                  }
                  
                  &.away {
                    color: #faad14;
                  }
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
        color: #262626;
        margin-bottom: 8px;
      }
      
      p {
        color: #8c8c8c;
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
    background: rgba(255, 255, 255, 0.8);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 9999;
  }
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
        .communication-tabs {
          :deep(.ant-tabs-content) {
            height: 500px;
          }
        }
      }
    }
  }
}

.message-header {
  .message-time { margin-left: 8px; }
}
</style>
