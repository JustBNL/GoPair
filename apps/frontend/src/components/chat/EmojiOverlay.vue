<template>
  <div class="emoji-overlay" role="presentation" aria-hidden="true">
    <span
      v-for="p in particles"
      :key="p.id"
      class="emoji-particle"
      :style="{
        left: p.x + 'vw',
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
  bottom: -60px;
  line-height: 1;
  user-select: none;
  animation: emoji-float-up linear forwards;
  will-change: transform, opacity;
}

@keyframes emoji-float-up {
  0% {
    transform: translateY(0) scale(1);
    opacity: 1;
  }
  75% {
    opacity: 1;
  }
  100% {
    transform: translateY(-110vh) scale(0.5);
    opacity: 0;
  }
}
</style>
