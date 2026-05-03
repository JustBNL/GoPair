<template>
  <a-modal
    :open="visible"
    :title="isLoading ? '加载中...' : memberProfile?.nickname ? `${memberProfile.nickname} 的资料` : '用户资料'"
    :footer="null"
    :width="420"
    :confirm-loading="actionLoading"
    @cancel="handleClose"
    class="member-profile-modal"
  >
    <div v-if="isLoading" class="loading-state">
      <a-spin size="large" />
    </div>

    <div v-else-if="memberProfile" class="profile-content">
      <!-- 头像区域 -->
      <div class="profile-avatar-section">
        <div class="avatar-wrapper clickable">
          <a-image
            v-if="memberProfile?.avatar"
            :src="memberProfile.avatar"
            :preview="{
              src: memberProfile.avatarOriginalUrl || memberProfile.avatar
            }"
            :width="80"
            :height="80"
            :preview-mask="false"
            class="profile-avatar-image"
          />
          <a-avatar v-else :size="80">
            {{ nicknameInitial }}
          </a-avatar>
        </div>
        <div class="profile-info">
          <h3 class="profile-nickname">{{ memberProfile.nickname }}</h3>
          <p class="profile-email">{{ memberProfile.email || '暂无邮箱' }}</p>
        </div>
      </div>

      <a-divider />

      <!-- 关系状态 -->
      <div class="relationship-status">
        <a-tag v-if="friendStatus?.isFriend" color="green">已是你好友</a-tag>
        <a-tag v-else-if="friendStatus?.isRequestReceived" color="orange">对方请求加你为好友</a-tag>
        <a-tag v-else-if="friendStatus?.isRequestSent" color="blue">已发送好友申请</a-tag>
        <a-tag v-else color="default">非好友</a-tag>
      </div>

      <!-- 收到申请时的处理按钮 -->
      <div v-if="friendStatus?.isRequestReceived" class="request-actions">
        <p class="request-hint">是否同意 {{ memberProfile.nickname }} 的好友申请？</p>
        <div class="request-buttons">
          <a-button type="primary" :loading="actionLoading" @click="handleAccept">
            同意
          </a-button>
          <a-button :loading="actionLoading" @click="handleReject">
            拒绝
          </a-button>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <!-- 非好友：添加好友 + 发消息 -->
        <template v-if="!friendStatus?.isFriend && !friendStatus?.isRequestSent && !friendStatus?.isRequestReceived">
          <a-button type="primary" :loading="actionLoading" @click="handleAddFriend">
            加为好友
          </a-button>
        </template>

        <!-- 已是好友：发消息 + 删除好友 -->
        <template v-if="friendStatus?.isFriend">
          <a-button type="primary" :loading="actionLoading" @click="handleOpenChat">
            发消息
          </a-button>
          <a-popconfirm
            title="确定删除该好友？删除后双方都将移除好友关系。"
            ok-text="确定删除"
            cancel-text="取消"
            @confirm="handleDeleteFriend"
          >
            <a-button danger :loading="actionLoading">
              删除好友
            </a-button>
          </a-popconfirm>
        </template>

        <!-- 已发出申请：等待确认 -->
        <template v-if="friendStatus?.isRequestSent && !friendStatus?.isFriend">
          <a-button disabled>
            等待对方确认
          </a-button>
        </template>
      </div>

      <!-- 附言输入（非好友时显示） -->
      <div v-if="!friendStatus?.isFriend && !friendStatus?.isRequestSent" class="friend-message-section">
        <a-input
          v-model:value="friendMessage"
          placeholder="添加附言（选填）"
          :maxlength="100"
          show-count
        />
      </div>
    </div>

    <div v-else class="error-state">
      <p>无法加载用户资料</p>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useChatStore } from '@/stores/chat'
import { ChatAPI } from '@/api/chat'
import type { FriendStatusVO, UserPublicProfile } from '@/types/chat'

const props = defineProps<{
  visible: boolean
  memberId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'openChat', friendId: number): void
  (e: 'refreshFriends'): void
}>()

const chatStore = useChatStore()

const isLoading = ref(false)
const actionLoading = ref(false)
const memberProfile = ref<UserPublicProfile | null>(null)
const friendStatus = ref<FriendStatusVO | null>(null)
const friendMessage = ref('')

const nicknameInitial = computed(() => {
  return (memberProfile.value?.nickname || 'U').charAt(0).toUpperCase()
})

watch(
  () => props.visible,
  async (val) => {
    if (val && props.memberId) {
      await loadProfile()
    } else {
      memberProfile.value = null
      friendStatus.value = null
      friendMessage.value = ''
    }
  }
)

async function loadProfile() {
  isLoading.value = true
  try {
    const [profileRes, statusRes] = await Promise.all([
      ChatAPI.getUserProfile(props.memberId),
      chatStore.checkFriendStatus(props.memberId)
    ])
    memberProfile.value = profileRes.data
    friendStatus.value = statusRes
  } catch {
    message.error('无法加载用户资料')
  } finally {
    isLoading.value = false
  }
}

async function handleAddFriend() {
  actionLoading.value = true
  try {
    await chatStore.sendRequest(props.memberId, friendMessage.value || undefined)
    message.success('好友申请已发送')
    await loadProfile()
    emit('refreshFriends')
  } catch {
    // 错误已在 API 层处理
  } finally {
    actionLoading.value = false
  }
}

async function handleAccept() {
  if (!friendStatus.value?.requestId) return
  actionLoading.value = true
  try {
    await chatStore.acceptRequest(friendStatus.value.requestId)
    message.success('已同意好友申请')
    await loadProfile()
    emit('refreshFriends')
  } catch {
    // 错误已在 API 层处理
  } finally {
    actionLoading.value = false
  }
}

async function handleReject() {
  if (!friendStatus.value?.requestId) return
  actionLoading.value = true
  try {
    await chatStore.rejectRequest(friendStatus.value.requestId)
    message.success('已拒绝好友申请')
    await loadProfile()
  } catch {
    // 错误已在 API 层处理
  } finally {
    actionLoading.value = false
  }
}

async function handleDeleteFriend() {
  actionLoading.value = true
  try {
    await chatStore.removeFriend(props.memberId)
    message.success('已删除好友')
    emit('refreshFriends')
    handleClose()
  } catch {
    // 错误已在 API 层处理
  } finally {
    actionLoading.value = false
  }
}

function handleOpenChat() {
  emit('openChat', props.memberId)
  handleClose()
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<style scoped>
.member-profile-modal :deep(.ant-modal-header) {
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-light, #e8e8e8);
}

.loading-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}

.error-state {
  text-align: center;
  padding: 40px 0;
  color: var(--text-muted, #999);
}

.profile-content {
  padding: 8px 0;
}

.profile-avatar-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}

.avatar-wrapper :deep(.ant-avatar) {
  border: 3px solid var(--brand-primary, #5B87BD);
  flex-shrink: 0;
}

.avatar-wrapper.clickable {
  cursor: pointer;

  :deep(.ant-image) {
    img {
      border-radius: 50%;
      border: 3px solid var(--brand-primary, #5B87BD);
    }
  }
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-nickname {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary, #1a1a1a);
  margin: 0 0 4px;
  word-break: break-word;
}

.profile-email {
  font-size: 13px;
  color: var(--text-secondary, #666);
  margin: 0;
  word-break: break-all;
}

.relationship-status {
  margin-bottom: 16px;
}

.request-actions {
  margin-bottom: 16px;
  padding: 12px;
  background: var(--surface-bg, #f6f8fb);
  border-radius: 8px;
}

.request-hint {
  font-size: 13px;
  color: var(--text-secondary, #666);
  margin: 0 0 10px;
}

.request-buttons {
  display: flex;
  gap: 8px;
}

.action-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.friend-message-section {
  margin-top: 12px;
}
</style>
