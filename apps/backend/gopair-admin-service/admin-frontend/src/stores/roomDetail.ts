import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useRoomDetailStore = defineStore('roomDetail', () => {
  const pendingRoomId = ref<number | null>(null)

  function setPendingRoomId(roomId: number) {
    pendingRoomId.value = roomId
  }

  function clearPendingRoomId() {
    pendingRoomId.value = null
  }

  return { pendingRoomId, setPendingRoomId, clearPendingRoomId }
})
