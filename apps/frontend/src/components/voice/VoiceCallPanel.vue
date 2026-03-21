<template>
  <div class="voice-call-panel">
    <!-- 加载中 -->
    <div v-if="loading" class="state-center">
      <a-spin size="large" />
      <p>加载中...</p>
    </div>

    <!-- 锁定状态：房主尚未开启（非房主视角） -->
    <div v-else-if="callState === 'locked'" class="state-center">
      <div class="voice-icon locked">
        <LockOutlined />
      </div>
      <h3>语音频道</h3>
      <p class="desc">语音功能未开启</p>
      <p class="desc-sub">请联系房主开启语音通话</p>
    </div>

    <!-- 空闲状态：房主视角，可开启 -->
    <div v-else-if="callState === 'idle'" class="state-center">
      <div class="voice-icon idle">
        <PhoneOutlined />
      </div>
      <h3>语音频道</h3>
      <p class="desc">语音频道已准备就绪</p>
      <a-button type="primary" size="large" :loading="actionLoading" @click="emit('open')">
        <PhoneOutlined /> 开启语音通话
      </a-button>
    </div>

    <!-- 通话进行中，当前用户未加入 -->
    <div v-else-if="callState === 'active'" class="state-center">
      <div class="voice-icon active"><PhoneOutlined /></div>
      <h3>语音频道</h3>
      <p class="desc">{{ (currentCall?.participants ?? []).length }} 人正在通话中</p>
      <div class="participant-avatars">
        <a-tooltip
          v-for="p in (currentCall?.participants ?? []).slice(0, 5)"
          :key="p.userId"
          :title="p.nickname || ('用户 ' + p.userId)"
        >
          <a-avatar :size="36" class="participant-avatar">
            {{ (p.nickname || String(p.userId)).charAt(0).toUpperCase() }}
          </a-avatar>
        </a-tooltip>
        <span v-if="(currentCall?.participants ?? []).length > 5" class="more-count">
          +{{ (currentCall?.participants ?? []).length - 5 }}
        </span>
      </div>
      <a-button type="primary" size="large" :loading="actionLoading" @click="emit('join')">
        <PhoneOutlined /> 加入通话
      </a-button>
    </div>

    <!-- 通话中 -->
    <div v-else-if="callState === 'in-call'" class="state-in-call">
      <div class="call-header">
        <div class="voice-icon in-call"><PhoneOutlined /></div>
        <div>
          <h3>通话中</h3>
          <p class="desc">{{ (currentCall?.participants ?? []).length }} 人参与</p>
        </div>
      </div>

      <div class="participants-list">
        <div v-for="p in (currentCall?.participants ?? [])" :key="p.userId" class="participant-row">
          <a-avatar :size="32">{{ (p.nickname || String(p.userId)).charAt(0).toUpperCase() }}</a-avatar>
          <span class="participant-id">{{ p.nickname || ('用户 ' + p.userId) }}</span>
          <a-tag v-if="p.isInitiator" color="blue" size="small">发起人</a-tag>
          <AudioMutedOutlined v-if="p.muted" class="muted-icon" />
        </div>
      </div>

      <div class="call-controls">
        <a-button shape="circle" size="large" @click="emit('toggle-mute')" :class="{ 'ctrl-btn--off': isMuted }">
          <AudioMutedOutlined v-if="isMuted" />
          <AudioOutlined v-else />
        </a-button>
        <a-button shape="circle" size="large" @click="emit('toggle-speaker')" :class="{ 'ctrl-btn--off': isSpeakerOff }">
          <SoundOutlined v-if="!isSpeakerOff" />
          <AudioMutedOutlined v-else />
        </a-button>
      </div>

      <div class="call-actions">
        <a-button size="large" :loading="actionLoading" @click="emit('leave')">
          离开通话
        </a-button>
        <!-- 房主专属：强制结束所有人的通话 -->
        <a-button
          v-if="isOwner"
          danger
          size="large"
          :loading="actionLoading"
          @click="emit('end')"
        >
          结束通话
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  PhoneOutlined,
  AudioOutlined,
  AudioMutedOutlined,
  SoundOutlined,
  LockOutlined
} from '@ant-design/icons-vue'
import type { CallVO } from '@/types/api'
import type { CallState } from '@/composables/useVoiceCall'

interface Props {
  callState: CallState
  isOwner: boolean
  currentCall: CallVO | null
  loading: boolean
  actionLoading: boolean
  isMuted: boolean
  isSpeakerOff: boolean
}

interface Emits {
  (e: 'open'): void
  (e: 'join'): void
  (e: 'leave'): void
  (e: 'end'): void
  (e: 'toggle-mute'): void
  (e: 'toggle-speaker'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()
</script>

<style scoped lang="scss">
.voice-call-panel {
  display: flex;
  flex-direction: column;
  min-height: 320px;
  padding: 24px;

  .state-center {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    flex: 1;
    gap: 12px;
    text-align: center;

    h3 { margin: 0; font-size: 20px; color: #262626; }
    .desc { margin: 0; color: #8c8c8c; font-size: 14px; }
    .desc-sub { margin: 0; color: #bfbfbf; font-size: 13px; }
  }

  .voice-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 72px;
    height: 72px;
    border-radius: 50%;
    font-size: 32px;

    &.locked  { background: #f5f5f5; color: #bfbfbf; }
    &.idle    { background: #f0f5ff; color: #1890ff; }
    &.active  { background: #fff7e6; color: #fa8c16; animation: pulse 1.5s ease-in-out infinite; }
    &.in-call { background: #f6ffed; color: #52c41a; }
  }

  .participant-avatars {
    display: flex;
    align-items: center;
    gap: 4px;
    margin: 8px 0;

    .participant-avatar { border: 2px solid #fff; }
    .more-count { font-size: 12px; color: #8c8c8c; margin-left: 4px; }
  }

  .state-in-call {
    display: flex;
    flex-direction: column;
    gap: 20px;

    .call-header {
      display: flex;
      align-items: center;
      gap: 16px;

      h3 { margin: 0; font-size: 18px; color: #262626; }
      .desc { margin: 0; color: #8c8c8c; font-size: 13px; }
    }

    .participants-list {
      border: 1px solid #f0f0f0;
      border-radius: 8px;
      overflow: hidden;

      .participant-row {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 10px 14px;
        border-bottom: 1px solid #f0f0f0;

        &:last-child { border-bottom: none; }
        .participant-id { flex: 1; font-size: 14px; color: #262626; }
        .muted-icon { color: #ff4d4f; font-size: 14px; }
      }
    }

    .call-controls {
      display: flex;
      justify-content: center;
      gap: 16px;

      .ctrl-btn--off {
        background: #ff4d4f;
        border-color: #ff4d4f;
        color: #fff;

        &:hover {
          background: #ff7875;
          border-color: #ff7875;
        }
      }
    }

    .call-actions {
      display: flex;
      gap: 12px;
    }
  }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50%       { transform: scale(1.08); }
}
</style>
