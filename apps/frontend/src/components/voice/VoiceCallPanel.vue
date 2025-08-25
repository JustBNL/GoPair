<template>
  <div class="voice-call-panel">
    <!-- 简化的语音面板 -->
    <div class="voice-panel-content">
      <!-- 当前状态显示 -->
      <div class="call-status-section">
        <div class="status-display">
          <div class="status-icon">
            <PhoneOutlined />
          </div>
          <div class="status-text">
            <h3>语音通话功能</h3>
            <p>{{ callStateText }}</p>
          </div>
        </div>
      </div>

      <!-- 功能按钮 -->
      <div class="call-actions">
        <a-button 
          v-if="callState === 'idle'"
          type="primary" 
          size="large" 
          @click="initiateCall"
          :loading="loading"
        >
          <PhoneOutlined />
          发起通话
        </a-button>
        
        <a-button 
          v-else-if="callState === 'calling'"
          type="primary" 
          size="large" 
          @click="joinCall"
          :loading="loading"
        >
          <PhoneOutlined />
          加入通话
        </a-button>
        
        <a-button 
          v-else
          danger 
          size="large" 
          @click="leaveCall"
          :loading="loading"
        >
          离开通话
        </a-button>
      </div>

      <!-- 功能说明 -->
      <div class="feature-description">
        <a-alert
          message="语音通话功能"
          description="WebRTC实时语音通话功能正在开发中，敬请期待！"
          type="info"
          show-icon
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { PhoneOutlined } from '@ant-design/icons-vue'

interface Props {
  roomId: number
  currentUserId: number
}

interface Emits {
  (e: 'call-state-changed', state: 'idle' | 'calling' | 'in-call'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 基本状态
const callState = ref<'idle' | 'calling' | 'in-call'>('idle')
const loading = ref(false)

// 计算属性
const callStateText = computed(() => {
  const stateMap = {
    idle: '空闲状态',
    calling: '通话进行中',
    'in-call': '通话中'
  }
  return stateMap[callState.value]
})

// 方法
const initiateCall = async () => {
  try {
    loading.value = true
    // 模拟发起通话
    await new Promise(resolve => setTimeout(resolve, 1000))
    callState.value = 'calling'
    emit('call-state-changed', 'calling')
    antMessage.success('通话已发起（模拟）')
  } catch (error) {
    antMessage.error('发起通话失败')
  } finally {
    loading.value = false
  }
}

const joinCall = async () => {
  try {
    loading.value = true
    // 模拟加入通话
    await new Promise(resolve => setTimeout(resolve, 1000))
    callState.value = 'in-call'
    emit('call-state-changed', 'in-call')
    antMessage.success('已加入通话（模拟）')
  } catch (error) {
    antMessage.error('加入通话失败')
  } finally {
    loading.value = false
  }
}

const leaveCall = async () => {
  try {
    loading.value = true
    // 模拟离开通话
    await new Promise(resolve => setTimeout(resolve, 1000))
    callState.value = 'idle'
    emit('call-state-changed', 'idle')
    antMessage.success('已离开通话')
  } catch (error) {
    antMessage.error('离开通话失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.voice-call-panel {
  padding: 24px;
  background: white;
  border-radius: 8px;
  
  .voice-panel-content {
    text-align: center;
    
    .call-status-section {
      margin-bottom: 32px;
      
      .status-display {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 16px;
        
        .status-icon {
          font-size: 48px;
          color: #1890ff;
        }
        
        .status-text {
          h3 {
            margin: 0;
            color: #262626;
            font-size: 20px;
          }
          
          p {
            margin: 8px 0 0 0;
            color: #8c8c8c;
            font-size: 14px;
          }
        }
      }
    }
    
    .call-actions {
      margin-bottom: 32px;
    }
    
    .feature-description {
      max-width: 400px;
      margin: 0 auto;
    }
  }
}

@media (max-width: 768px) {
  .voice-call-panel {
    padding: 16px;
    
    .voice-panel-content {
      .status-display {
        .status-icon {
          font-size: 36px;
        }
        
        .status-text h3 {
          font-size: 18px;
        }
      }
    }
  }
}
</style> 