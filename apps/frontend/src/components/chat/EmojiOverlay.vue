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
      @animationend="onParticleDone(p.id)"
    >{{ p.emoji }}</span>
  </div>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import type { EmojiParticle } from '@/types/api'

interface Props {
  particles: EmojiParticle[]
}

const props = defineProps<Props>()
watch(() => props.particles, (newVal) => {
  console.log('[DEBUG] EmojiOverlay particles updated, count:', newVal.length, 'emojis:', newVal.map(p => p.emoji))
}, { deep: true })

const emit = defineEmits<{
  (e: 'particle-done', id: string): void
}>()

function onParticleDone(id: string) {
  emit('particle-done', id)
}
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
