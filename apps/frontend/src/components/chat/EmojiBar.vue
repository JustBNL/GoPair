<template>
  <div
    class="emoji-bar"
    :class="{ throttled }"
  >
    <button
      v-for="emoji in EMOJI_LIST"
      :key="emoji"
      class="emoji-btn"
      :title="emoji"
      @click="handleEmojiClick(emoji)"
    >{{ emoji }}</button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'

const emit = defineEmits<{
  (e: 'send-emoji', emoji: string): void
}>()

/** 预设 Emoji 列表 */
const EMOJI_LIST = ['😄', '🎉', '👍', '❤️', '🔥', '😂', '🙏', '💡', '👏', '🚀', '✨', '😎']

const lastSendTime = ref(0)
const throttled = ref(false)

/**
 * 处理 Emoji 点击，含 2 秒节流
 */
function handleEmojiClick(emoji: string) {
  const now = Date.now()
  if (now - lastSendTime.value < 2000) {
    message.warning('发送太频繁啦，请稍等一下～', 1.5)
    return
  }
  lastSendTime.value = now
  throttled.value = true
  emit('send-emoji', emoji)
  setTimeout(() => {
    throttled.value = false
  }, 2000)
}
</script>

<style scoped lang="scss">
.emoji-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 6px 0;
  transition: opacity 0.2s;

  &.throttled {
    opacity: 0.5;
    pointer-events: none;
  }
}

.emoji-btn {
  width: 32px;
  height: 32px;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  border: none;
  background: transparent;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, transform 0.15s;

  &:hover {
    background: #f0f0f0;
    transform: scale(1.2);
  }

  &:active {
    transform: scale(1.05);
  }
}
</style>
