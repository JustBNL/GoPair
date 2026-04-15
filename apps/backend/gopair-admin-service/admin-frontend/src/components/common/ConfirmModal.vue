<script setup lang="ts">
interface Props {
  open: boolean
  title: string
  content: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
  loading?: boolean
}

interface Emits {
  (e: 'update:open', val: boolean): void
  (e: 'confirm'): void
}

const props = withDefaults(defineProps<Props>(), {
  confirmText: '确认',
  cancelText: '取消',
  danger: false,
  loading: false,
})
const emit = defineEmits<Emits>()

function onCancel() {
  emit('update:open', false)
}
function onConfirm() {
  emit('confirm')
  emit('update:open', false)
}
</script>

<template>
  <a-modal
    :open="open"
    :title="title"
    :ok-text="confirmText"
    :cancel-text="cancelText"
    :ok-button-props="{ danger: props.danger, htmlType: 'button' }"
    :confirm-loading="loading"
    @cancel="onCancel"
    @ok="onConfirm"
    :width="420"
  >
    <p class="confirm-modal__content">{{ content }}</p>
  </a-modal>
</template>

<style scoped>
.confirm-modal__content {
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin: 0;
}
</style>
