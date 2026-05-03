<template>
  <div class="emoji-bar">
    <a-tabs
      v-model:activeKey="activeTab"
      size="small"
      class="emoji-picker-tabs"
      :tab-bar-style="{ marginBottom: 0 }"
    >
      <a-tab-pane
        v-for="cat in EMOJI_CATEGORIES"
        :key="cat.key"
      >
        <template #tab>
          <span class="tab-label">{{ cat.key }}</span>
        </template>
        <div class="emoji-grid">
          <button
            v-for="emoji in cat.emojis"
            :key="emoji"
            class="emoji-btn"
            :title="emoji"
            @click="handleEmojiClick(emoji)"
          >{{ emoji }}</button>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { EMOJI_CATEGORIES } from '@/utils/emoji'

const emit = defineEmits<{
  (e: 'send-emoji', emoji: string): void
}>()

const activeTab = ref(EMOJI_CATEGORIES[0].key)

const lastSendTime = ref(0)

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
  emit('send-emoji', emoji)
}
</script>

<style scoped lang="scss">
.emoji-bar {
  width: 100%;
}

.emoji-picker-tabs {
  :deep(.ant-tabs-nav) {
    margin-bottom: 0;
  }

  :deep(.ant-tabs-tab) {
    padding: 4px 8px;
    font-size: 12px;
  }

  :deep(.ant-tabs-tabpane) {
    padding: 4px 0;
  }

  :deep(.ant-tabs-content) {
    height: 88px;
    overflow-y: auto;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-thumb {
      background: var(--border-default);
      border-radius: 2px;
    }
  }
}

.tab-label {
  font-size: 13px;
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(40px, 1fr));
  gap: 2px;
  padding: 4px 0;
}

.emoji-btn {
  width: 40px;
  height: 40px;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  border: none;
  background: transparent;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, transform 0.15s;

  &:hover {
    background: var(--border-light);
    transform: scale(1.15);
  }

  &:active {
    transform: scale(1.0);
  }
}
</style>
