<template>
  <div class="emoji-overlay" role="presentation" aria-hidden="true">
    <span
      v-for="p in particles"
      :key="p.id"
      class="emoji-particle"
      :style="{
        top: p.y + 'vh',
        fontSize: p.size + 'px',
        animationDuration: p.duration + 'ms'
      }"
      @animationend="$emit('particle-done', p.id)"
    >{{ p.emoji }}</span>
  </div>
</template>

<script setup lang="ts">
import type { EmojiParticle } from '@/types/api'

interface Props {
  particles: EmojiParticle[]
}

defineProps<Props>()
defineEmits<{
  (e: 'particle-done', id: string): void
}>()
</script>

<style scoped lang="scss">
.emoji-overlay {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 9999;
  overflow: hidden;
}

.emoji-particle {
  position: absolute;
  top: 0;
  line-height: 1;
  user-select: none;
  animation: emoji-slide-left linear forwards;
  will-change: transform, opacity;
}

@keyframes emoji-slide-left {
  0% {
    transform: translateX(0) scale(1);
    opacity: 1;
  }
  100% {
    transform: translateX(-110vw) scale(0.6);
    opacity: 0;
  }
}
</style>
