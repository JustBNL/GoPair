<template>
  <a-modal
    :visible="visible"
    :title="null"
    :footer="null"
    :maskClosable="false"
    :width="480"
    centered
    @cancel="handleCancel"
    class="create-room-modal"
  >
    <!-- 模态框头部 -->
    <div class="modal-header">
      <div class="header-icon">
        <PlusCircleOutlined />
      </div>
      <h2 class="modal-title">创建房间</h2>
      <p class="modal-subtitle">设置房间信息，邀请团队成员协作</p>
    </div>

    <!-- 创建房间表单 -->
    <a-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      layout="vertical"
      @finish="handleSubmit"
      @finishFailed="handleSubmitFailed"
      class="create-form"
    >
      <!-- 房间名称 -->
      <a-form-item name="roomName" label="房间名称">
        <a-input
          v-model:value="formData.roomName"
          placeholder="请输入房间名称"
          size="large"
          :maxlength="50"
          showCount
          :prefix="h(HomeOutlined)"
        />
      </a-form-item>

      <!-- 房间描述 -->
      <a-form-item name="description" label="房间描述">
        <a-textarea
          v-model:value="formData.description"
          placeholder="简要描述房间用途（可选）"
          :rows="3"
          :maxlength="200"
          showCount
          resize="none"
        />
      </a-form-item>

      <!-- 高级设置 -->
      <a-collapse 
        v-model:activeKey="advancedSettingsOpen" 
        ghost 
        :bordered="false"
        class="advanced-settings"
      >
        <a-collapse-panel key="1" header="高级设置">
          <template #extra>
            <SettingOutlined />
          </template>
          
          <div class="advanced-content">
            <!-- 最大成员数 -->
            <a-form-item name="maxMembers" label="最大成员数">
              <a-slider
                v-model:value="formData.maxMembers"
                :min="2"
                :max="50"
                :step="1"
                :tooltip-formatter="(value: number) => `${value} 人`"
                class="member-slider"
              />
              <div class="slider-info">
                <span>2人</span>
                <span class="current-value">{{ formData.maxMembers }}人</span>
                <span>50人</span>
              </div>
            </a-form-item>

            <!-- 房间有效期 -->
            <a-form-item name="expireHours" label="房间有效期">
              <a-select
                v-model:value="formData.expireHours"
                size="large"
                placeholder="选择有效期"
              >
                <a-select-option :value="1">1小时</a-select-option>
                <a-select-option :value="6">6小时</a-select-option>
                <a-select-option :value="12">12小时</a-select-option>
                <a-select-option :value="24">24小时（推荐）</a-select-option>
                <a-select-option :value="48">48小时</a-select-option>
                <a-select-option :value="72">72小时</a-select-option>
                <a-select-option :value="168">1周</a-select-option>
              </a-select>
            </a-form-item>
          </div>
        </a-collapse-panel>
      </a-collapse>

      <!-- 操作按钮 -->
      <div class="form-actions">
        <a-button 
          size="large" 
          @click="handleCancel"
          class="cancel-btn"
        >
          取消
        </a-button>
        <a-button
          type="primary"
          size="large"
          html-type="submit"
          :loading="roomStore.createLoading"
          class="submit-btn"
        >
          <PlusOutlined v-if="!roomStore.createLoading" />
          {{ roomStore.createLoading ? '创建中...' : '创建房间' }}
        </a-button>
      </div>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, h } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import {
  PlusCircleOutlined,
  HomeOutlined,
  SettingOutlined,
  PlusOutlined
} from '@ant-design/icons-vue'
import { useRoomStore } from '@/stores/room'
import type { CreateRoomFormData, RoomInfo } from '@/types/room'

// ==================== 组件属性 ====================

interface Props {
  visible: boolean
}

const props = defineProps<Props>()

// ==================== 组件事件 ====================

interface Emits {
  'update:visible': [visible: boolean]
  success: [room: RoomInfo]
}

const emit = defineEmits<Emits>()

// ==================== 组件状态 ====================

const roomStore = useRoomStore()
const formRef = ref<FormInstance>()

// 高级设置展开状态
const advancedSettingsOpen = ref<string[]>([])

// 表单数据
const formData = reactive<CreateRoomFormData>({
  roomName: '',
  description: '',
  maxMembers: 10,
  expireHours: 24
})

// 表单验证规则
const formRules = {
  roomName: [
    { required: true, message: '请输入房间名称' },
    { min: 1, max: 50, message: '房间名称长度为1-50个字符' },
    { pattern: /^[^<>'"&]*$/, message: '房间名称不能包含特殊字符' }
  ],
  description: [
    { max: 200, message: '描述不能超过200个字符' }
  ],
  maxMembers: [
    { required: true, message: '请设置最大成员数' },
    { type: 'number', min: 2, max: 50, message: '成员数必须在2-50之间' }
  ],
  expireHours: [
    { required: true, message: '请选择房间有效期' }
  ]
}

// ==================== 监听器 ====================

// 监听可见性变化，重置表单
watch(() => props.visible, (newVal) => {
  if (newVal) {
    resetForm()
  }
})

// ==================== 事件处理 ====================

/**
 * 提交表单
 */
async function handleSubmit(values: CreateRoomFormData) {
  try {
    const room = await roomStore.createRoom({
      roomName: values.roomName.trim(),
      description: values.description?.trim() || undefined,
      maxMembers: values.maxMembers,
      expireHours: values.expireHours
    })
    
    if (room) {
      emit('success', room)
      resetForm()
      message.success('房间创建成功！')
    }
  } catch (error: any) {
    console.error('创建房间失败:', error)
    
    // 根据错误类型显示不同的提示
    const errorMessage = error?.message || '创建房间失败，请重试'
    message.error(errorMessage)
  }
}

/**
 * 提交失败处理
 */
function handleSubmitFailed(errorInfo: any) {
  console.warn('表单验证失败:', errorInfo)
  message.warning('请检查表单信息')
}

/**
 * 取消操作
 */
function handleCancel() {
  emit('update:visible', false)
}

/**
 * 重置表单
 */
function resetForm() {
  formData.roomName = ''
  formData.description = ''
  formData.maxMembers = 10
  formData.expireHours = 24
  advancedSettingsOpen.value = []
  
  // 清除表单验证状态
  formRef.value?.clearValidate()
}
</script>

<style scoped>
/* ==================== 模态框样式 ==================== */

:deep(.create-room-modal .ant-modal-content) {
  border-radius: 16px;
  overflow: hidden;
}

:deep(.create-room-modal .ant-modal-body) {
  padding: 0;
}

/* ==================== 模态框头部 ==================== */

.modal-header {
  text-align: center;
  padding: 32px 32px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.header-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.9;
}

.modal-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px;
}

.modal-subtitle {
  font-size: 14px;
  margin: 0;
  opacity: 0.8;
}

/* ==================== 表单样式 ==================== */

.create-form {
  padding: 32px;
}

:deep(.create-form .ant-form-item-label > label) {
  font-weight: 500;
  color: #1a202c;
}

:deep(.create-form .ant-input),
:deep(.create-form .ant-select-selector),
:deep(.create-form .ant-input-number) {
  border-radius: 8px;
}

:deep(.create-form .ant-input:focus),
:deep(.create-form .ant-select-focused .ant-select-selector),
:deep(.create-form .ant-input-focused) {
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

/* ==================== 高级设置 ==================== */

.advanced-settings {
  margin: 8px 0;
}

:deep(.advanced-settings .ant-collapse-header) {
  padding: 12px 0 !important;
  background: transparent !important;
  border: none !important;
  font-weight: 500;
  color: #667eea;
}

:deep(.advanced-settings .ant-collapse-content-box) {
  padding: 16px 0 0 !important;
}

.advanced-content {
  background: #f8fafc;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid #e2e8f0;
}

/* ==================== 滑块样式 ==================== */

.member-slider {
  margin-bottom: 8px;
}

:deep(.member-slider .ant-slider-rail) {
  background: #e2e8f0;
}

:deep(.member-slider .ant-slider-track) {
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
}

:deep(.member-slider .ant-slider-handle) {
  border-color: #667eea;
}

:deep(.member-slider .ant-slider-handle:focus) {
  box-shadow: 0 0 0 5px rgba(102, 126, 234, 0.1);
}

.slider-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6b7280;
}

.current-value {
  font-weight: 600;
  color: #667eea;
}

/* ==================== 操作按钮 ==================== */

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 32px;
}

.cancel-btn {
  flex: 1;
  height: 44px;
  border-radius: 8px;
  font-weight: 500;
  border-color: #d1d5db;
  color: #6b7280;
}

.cancel-btn:hover {
  border-color: #9ca3af;
  color: #374151;
}

.submit-btn {
  flex: 2;
  height: 44px;
  border-radius: 8px;
  font-weight: 500;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.submit-btn:hover {
  background: linear-gradient(135deg, #5a67d8 0%, #6b46c1 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.submit-btn:active {
  transform: translateY(0);
}

/* ==================== 响应式设计 ==================== */

@media (max-width: 576px) {
  :deep(.create-room-modal) {
    margin: 16px;
    max-width: calc(100vw - 32px);
  }
  
  .modal-header {
    padding: 24px 20px 20px;
  }
  
  .header-icon {
    font-size: 40px;
    margin-bottom: 12px;
  }
  
  .modal-title {
    font-size: 20px;
  }
  
  .modal-subtitle {
    font-size: 13px;
  }
  
  .create-form {
    padding: 24px 20px;
  }
  
  .advanced-content {
    padding: 16px;
  }
  
  .form-actions {
    flex-direction: column;
    gap: 8px;
  }
  
  .cancel-btn,
  .submit-btn {
    flex: none;
  }
}
</style> 