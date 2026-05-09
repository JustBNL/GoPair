<template>
  <div class="image-message">
    <a-image
      :src="fileUrl"
      :alt="fileName"
      :preview="{ src: content || fileUrl }"
      class="message-image"
    />
    <div v-if="fileName" class="image-caption">
      {{ fileName }}
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  fileUrl: string
  fileName?: string
  content?: string
}>()
</script>

<style scoped lang="less">
.image-message {
  .message-image {
    max-width: 200px;
    max-height: 200px;
    border-radius: 4px;
  }

  .image-caption {
    padding: 4px 8px;
    font-size: 12px;
    color: var(--text-muted);
    text-align: center;
  }
}

@media (max-width: 768px) {
  .image-message {
    .message-image {
      max-width: 150px;
      max-height: 150px;
    }
  }
}
</style>
