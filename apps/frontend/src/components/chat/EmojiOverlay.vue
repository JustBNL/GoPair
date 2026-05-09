<template>
  <div class="emoji-overlay" role="presentation" aria-hidden="true">
    <span
      v-for="p in particles"
      :key="p.id"
      class="emoji-particle"
      :style="{
        top: p.y + 'vh',
        left: p.x + 'vw',
        fontSize: p.size + 'px'
      }"
    >{{ p.emoji }}</span>
  </div>
</template>

<script setup lang="ts">
import type { EmojiParticle } from '@/types/api'

interface Props {
  particles: EmojiParticle[]
}

defineProps<Props>()
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
  left: 0;
  width: 60px;
  height: 60px;
  line-height: 60px;
  text-align: center;
  user-select: none;
  background: rgba(255, 0, 0, 0.5);
  animation: emoji-slide-left 2.5s linear forwards;
  will-change: transform, opacity;
}

@keyframes emoji-slide-left {
  0% {
    transform: translateX(0) scale(0.3);
    opacity: 0;
  }
  15% {
    transform: translateX(-5vw) scale(1.4);
    opacity: 1;
  }
  30% {
    transform: translateX(-10vw) scale(1.0);
    opacity: 1;
  }
  80% {
    opacity: 1;
  }
  100% {
    transform: translateX(-110vw) scale(0.6);
    opacity: 0;
  }
}
</style>
