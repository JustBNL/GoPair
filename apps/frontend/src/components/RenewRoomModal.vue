<template>
  <a-modal
    :open="visible"
    title="续期房间"
    :confirm-loading="renewLoading"
    :width="400"
    centered
    @ok="handleConfirm"
    @cancel="handleCancel"
    ok-text="确认续期"
    cancel-text="取消"
  >
    <div class="renew-info" v-if="room">
      <p class="renew-tip">续期后房间将恢复正常使用。选择续期时长：</p>
      <div class="renew-room-name">
        <span class="label">房间名称：</span>
        <span class="value">{{ room.roomName }}</span>
      </div>
      <div class="renew-hours-selector">
        <a-radio-group v-model:value="selectedHours">
          <a-radio-button v-for="opt in RENEW_HOURS_OPTIONS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </a-radio-button>
        </a-radio-group>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { RENEW_HOURS_OPTIONS } from '@/types/room'
import { useRoomStore } from '@/stores/room'
import type { RoomInfo } from '@/types/room'

interface Props {
  visible: boolean
  room: RoomInfo | null
}

const props = defineProps<Props>()

interface Emits {
  'update:visible': [visible: boolean]
  success: [room: RoomInfo]
}

const emit = defineEmits<Emits>()

const roomStore = useRoomStore()
const selectedHours = ref(24)
const renewLoading = ref(false)

watch(() => props.visible, (newVal) => {
  if (newVal) {
    selectedHours.value = 24
  }
})

async function handleConfirm() {
  if (!props.room) return
  renewLoading.value = true
  try {
    await roomStore.renewRoom(props.room.roomId, selectedHours.value)
    emit('update:visible', false)
    emit('success', roomStore.currentRoom || props.room)
    message.success('续期成功，房间已恢复正常')
  } catch (e: any) {
    message.error(e?.response?.data?.msg || e?.message || '续期失败，请重试')
  } finally {
    renewLoading.value = false
  }
}

function handleCancel() {
  emit('update:visible', false)
}
</script>

<style scoped>
.renew-info {
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
