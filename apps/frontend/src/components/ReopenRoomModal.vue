<template>
  <a-modal
    :open="visible"
    title="重新开启房间"
    :confirm-loading="reopenLoading"
    :width="440"
    centered
    @ok="handleConfirm"
    @cancel="handleCancel"
    ok-text="确认开启"
    cancel-text="取消"
  >
    <div class="reopen-info" v-if="room">
      <p class="reopen-tip">重新开启后房间将恢复正常使用。选择过期时长：</p>
      <div class="reopen-room-name">
        <span class="label">房间名称：</span>
        <span class="value">{{ room.roomName }}</span>
      </div>
      <div class="reopen-hours-selector">
        <a-radio-group v-model:value="selectedPreset">
          <a-radio-button v-for="opt in RENEW_MINUTES_OPTIONS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </a-radio-button>
          <a-radio-button :value="-1">自定义</a-radio-button>
        </a-radio-group>
        <Transition name="slide-fade">
          <div v-if="selectedPreset === -1" class="custom-duration-panel">
            <a-input-number
              v-model:value="customValue"
              :min="1"
              :max="customMaxByUnit"
              :precision="0"
              size="large"
              placeholder="请输入数值"
              class="custom-value-input"
            />
            <a-select
              v-model:value="customUnit"
              :options="TIME_UNIT_OPTIONS"
              class="custom-unit-select"
            />
            <span class="custom-equivalent" v-if="customValue > 0">
              等效 {{ customValue }} {{ unitLabel }}
            </span>
          </div>
        </Transition>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { RENEW_MINUTES_OPTIONS, TIME_UNIT_OPTIONS, TimeUnit, convertToMinutes } from '@/types/room'
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
const selectedPreset = ref<number>(1440)
const customValue = ref<number>(1)
const customUnit = ref<TimeUnit>(TimeUnit.DAYS)
const reopenLoading = ref(false)

const customMinutes = computed(() => {
  if (customValue.value <= 0) return 0
  return convertToMinutes({ value: customValue.value, unit: customUnit.value })
})

const customMaxByUnit = computed(() => {
  switch (customUnit.value) {
    case TimeUnit.MINUTES: return 14400
    case TimeUnit.HOURS:   return 240
    case TimeUnit.DAYS:    return 10
  }
})

const unitLabel = computed(() => {
  const map: Record<TimeUnit, string> = {
    [TimeUnit.MINUTES]: '分钟',
    [TimeUnit.HOURS]: '小时',
    [TimeUnit.DAYS]: '天'
  }
  return map[customUnit.value]
})

watch(() => props.visible, (newVal) => {
  if (newVal) {
    selectedPreset.value = 1440
    customValue.value = 1
    customUnit.value = TimeUnit.DAYS
  }
})

async function handleConfirm() {
  if (!props.room) return
  let expireMinutes: number
  if (selectedPreset.value === -1) {
    expireMinutes = convertToMinutes({ value: customValue.value, unit: customUnit.value })
  } else {
    expireMinutes = selectedPreset.value
  }
  reopenLoading.value = true
  try {
    await roomStore.reopenRoom(props.room.roomId, expireMinutes)
    emit('update:visible', false)
    emit('success', roomStore.currentRoom || props.room)
    message.success('房间已重新开启')
  } catch (e: any) {
    message.error(e?.response?.data?.msg || e?.message || '重新开启失败，请重试')
  } finally {
    reopenLoading.value = false
  }
}

function handleCancel() {
  emit('update:visible', false)
}
</script>

<style scoped>
.reopen-info {
  padding: 8px 0;
}

.reopen-tip {
  margin: 0 0 16px;
  color: var(--text-secondary);
  font-size: 14px;
}

.reopen-room-name {
  margin-bottom: 16px;
  padding: 8px 12px;
  background: var(--surface-bg);
  border-radius: 6px;
}

.reopen-room-name .label {
  color: var(--text-muted);
  font-size: 13px;
}

.reopen-room-name .value {
  color: var(--text-primary);
  font-weight: 500;
}

.reopen-hours-selector {
  margin-top: 8px;
}

.reopen-hours-selector :deep(.ant-radio-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.reopen-hours-selector :deep(.ant-radio-button-wrapper) {
  flex: 1;
  text-align: center;
  min-width: 70px;
}

.custom-duration-panel {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding: 16px;
  background: var(--surface-bg);
  border-radius: 8px;
  border: 1px solid var(--border-default);
}

.custom-value-input {
  width: 120px;
}

.custom-unit-select {
  width: 100px;
}

.custom-equivalent {
  width: 100%;
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.slide-fade-enter-active {
  transition: all 0.2s ease-out;
}
.slide-fade-leave-active {
  transition: all 0.15s ease-in;
}
.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
