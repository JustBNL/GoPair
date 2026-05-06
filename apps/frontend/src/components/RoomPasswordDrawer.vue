<template>
  <a-drawer
    v-model:open="drawerVisible"
    placement="right"
    :width="400"
    :closable="true"
    :mask-closable="true"
    title="房间密码设置"
    class="room-password-drawer"
    @close="handleClose"
  >
    <div class="drawer-content">
      <div class="room-name-tip">
        <span class="label">房间</span>
        <span class="value">{{ roomName }}</span>
      </div>

      <a-divider />

      <div class="form-section">
        <div class="section-label">密码模式</div>
        <a-segmented
          v-model:value="formData.mode"
          :options="modeOptions"
          block
          class="mode-segmented"
        />
        <div class="mode-hint">
          <span v-if="formData.mode === 0">无需密码即可加入房间</span>
          <span v-else-if="formData.mode === 1">加入者需输入您设置的固定密码</span>
          <span v-else>每 5 分钟自动刷新动态令牌，更安全，无需输入</span>
        </div>
      </div>

      <div v-if="formData.mode === 1" class="form-section">
        <div class="section-label">设置固定密码</div>
        <a-input-password
          v-model:value="formData.rawPassword"
          placeholder="请输入 4-20 位密码"
          :maxlength="20"
          size="large"
          class="password-input"
        />
      </div>

      <div v-if="formData.mode !== 0" class="form-section">
        <div class="section-label">密码可见性</div>
        <div class="visibility-row">
          <span class="visibility-desc">
            {{ formData.visible === 1 ? '成员可查看密码/令牌' : '成员不可查看' }}
          </span>
          <a-switch
            v-model:checked="visibleChecked"
            checked-children="可见"
            un-checked-children="隐藏"
          />
        </div>
        <div class="visibility-hint">开启后，房间成员可在密码卡片查看</div>
      </div>
    </div>

    <div class="drawer-footer">
      <a-button @click="handleClose">取消</a-button>
      <a-button
        type="primary"
        :loading="submitting"
        @click="handleSubmit"
      >
        保存设置
      </a-button>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import { updateRoomPassword, RoomAPI } from '@/api/room'

const props = defineProps<{
  visible: boolean
  roomId: number
  roomName: string
  currentPasswordMode: number
  currentPasswordVisible: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'success'): void
}>()

const drawerVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v)
})

const submitting = ref(false)

const formData = reactive({
  mode: 0,
  rawPassword: '',
  visible: 0
})

const visibleChecked = computed({
  get: () => formData.visible === 1,
  set: (v: boolean) => { formData.visible = v ? 1 : 0 }
})

const modeOptions = [
  { label: '关闭', value: 0 },
  { label: '固定密码', value: 1 },
  { label: '动态令牌', value: 2 }
]

watch(() => props.visible, (val) => {
  if (val) {
    formData.mode = props.currentPasswordMode ?? 0
    formData.rawPassword = ''
    formData.visible = props.currentPasswordVisible ?? 0
  }
})

function handleClose() {
  drawerVisible.value = false
}

async function handleSubmit() {
  if (formData.mode === 1 && !formData.rawPassword) {
    message.warning('请输入固定密码')
    return
  }
  if (formData.mode === 1 && (formData.rawPassword.length < 4 || formData.rawPassword.length > 20)) {
    message.warning('密码长度需为 4-20 位')
    return
  }

  submitting.value = true
  try {
    await RoomAPI.updateRoomPassword(props.roomId, {
      mode: formData.mode,
      rawPassword: formData.mode === 1 ? formData.rawPassword : undefined,
      visible: formData.mode !== 0 ? formData.visible : undefined
    })
    message.success('密码设置已更新')
    emit('success')
    handleClose()
  } catch (e: any) {
    message.error(e?.response?.data?.msg || '设置失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.room-password-drawer {
  .drawer-content {
    display: flex;
    flex-direction: column;
    gap: 0;
  }

  .room-name-tip {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;

    .label {
      color: var(--text-muted);
      flex-shrink: 0;
    }

    .value {
      color: var(--text-primary);
      font-weight: 600;
    }
  }

  .form-section {
    padding: 12px 0;

    .section-label {
      font-size: 13px;
      color: var(--text-secondary);
      margin-bottom: 10px;
      font-weight: 500;
    }
  }

  .mode-segmented {
    margin-bottom: 8px;
  }

  .mode-hint {
    font-size: 12px;
    color: var(--text-muted);
    line-height: 1.5;
  }

  .password-input {
    margin-top: 4px;
  }

  .visibility-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    background: var(--surface-raised);
    border-radius: 8px;
    border: 1px solid var(--border-color);

    .visibility-desc {
      font-size: 13px;
      color: var(--text-primary);
    }
  }

  .visibility-hint {
    font-size: 12px;
    color: var(--text-muted);
    margin-top: 6px;
  }

  .drawer-footer {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    padding: 16px 24px;
    border-top: 1px solid var(--border-color);
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    background: var(--surface-bg);
  }
}
</style>
