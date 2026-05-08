import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import type {
  RoomInfo,
  RoomMember,
  CreateRoomRequest,
  JoinRoomRequest,
  UpdateRoomPasswordRequest
} from '@/types/room'
import type { BaseQuery } from '@/types/api'
import { RoomAPI } from '@/api/room'
import { normalizeRoomMembersList } from '@/utils/roomMemberDisplay'
import { useAuthStore } from './auth'

/**
 * 房间状态管理Store
 */
export const useRoomStore = defineStore('room', () => {
  // ==================== 状态定义 ====================

  const roomList = ref<RoomInfo[]>([])
  const currentRoom = ref<RoomInfo | null>(null)
  const roomMembers = ref<RoomMember[]>([])

  const loading = ref(false)
  const createLoading = ref(false)
  const joinLoading = ref(false)
  const membersLoading = ref(false)

  const pagination = ref({
    current: 1,
    pageSize: 10,
    total: 0
  })

  // ==================== 计算属性 ====================

  const hasRooms = computed(() => roomList.value.length > 0)

  const isRoomOwner = computed(() => {
    const authStore = useAuthStore()
    return currentRoom.value?.ownerId === authStore.user?.userId
  })

  const currentRoomMemberCount = computed(() => roomMembers.value.length)

  // ==================== 操作方法 ====================

  async function fetchUserRooms(query: BaseQuery = {}): Promise<void> {
    loading.value = true
    try {
      const response = await RoomAPI.getUserRooms(query)
      const result = response.data
      roomList.value = result.records
      pagination.value = {
        current: result.current,
        pageSize: result.size,
        total: result.total
      }
    } catch (error) {
      console.error('获取房间列表失败:', error)
      message.error('获取房间列表失败')
    } finally {
      loading.value = false
    }
  }

  async function createRoom(roomData: CreateRoomRequest): Promise<RoomInfo | null> {
    createLoading.value = true
    try {
      const response = await RoomAPI.createRoom(roomData)
      const newRoom = response.data
      roomList.value.unshift(newRoom)
      currentRoom.value = newRoom
      return newRoom
    } catch (error) {
      console.error('创建房间失败:', error)
      throw error
    } finally {
      createLoading.value = false
    }
  }

  async function requestJoinRoomAsync(joinData: JoinRoomRequest): Promise<string | null> {
    joinLoading.value = true
    try {
      const resp = await RoomAPI.joinRoomAsync(joinData)
      const token = resp.data.joinToken
      if (token) {
        message.loading({ content: '加入中…', key: 'joinAsync', duration: 1 })
        return token
      }
      return null
    } catch (e) {
      console.error('异步加入请求失败:', e)
      throw e
    } finally {
      joinLoading.value = false
    }
  }

  async function queryJoinResult(token: string): Promise<'JOINED' | 'PROCESSING' | 'FAILED'> {
    const resp = await RoomAPI.getJoinResult(token)
    const status = resp.data.status
    if (status === 'JOINED') {
      message.success({ content: '加入成功', key: 'joinAsync' })
      await fetchUserRooms()
    } else if (status === 'FAILED') {
      message.error({ content: '加入失败，请重试', key: 'joinAsync' })
    }
    return status
  }

  /**
   * 入房成功后：刷新房间列表 + 并行预取成员列表。
   * JOINED 时立即拉成员数据，后续进入 RoomDetailView 时成员列表已就绪，感知无等待。
   */
  async function prefetchAfterJoin(token: string, roomCode: string): Promise<'JOINED' | 'PROCESSING' | 'FAILED'> {
    const resp = await RoomAPI.getJoinResult(token)
    const status = resp.data.status
    if (status === 'JOINED') {
      message.success({ content: '加入成功', key: 'joinAsync' })
      // 刷新列表（串行，先拿到最新房间 ID）
      await fetchUserRooms()
      // 找到刚加入的房间，并行预取成员
      const joinedRoom = roomList.value.find(r => r.roomCode === roomCode)
      if (joinedRoom) {
        fetchRoomMembers(joinedRoom.roomId).catch(() => {
          // 预取失败静默，RoomDetailView 进入时会正常加载
        })
      }
    } else if (status === 'FAILED') {
      message.error({ content: '加入失败，请重试', key: 'joinAsync' })
    }
    return status
  }

  async function getRoomByCode(roomCode: string): Promise<RoomInfo | null> {
    try {
      const response = await RoomAPI.getRoomByCode(roomCode)
      return response.data
    } catch (error) {
      console.error('查询房间失败:', error)
      throw error
    }
  }

  async function fetchRoomMembers(roomId: number): Promise<void> {
    membersLoading.value = true
    try {
      const response = await RoomAPI.getRoomMembers(roomId)
      roomMembers.value = normalizeRoomMembersList(response.data)
    } catch (error) {
      console.error('获取房间成员失败:', error)
      message.error('获取房间成员失败')
    } finally {
      membersLoading.value = false
    }
  }

  async function leaveRoom(roomId: number): Promise<void> {
    try {
      await RoomAPI.leaveRoom(roomId)
      const index = roomList.value.findIndex(r => r.roomId === roomId)
      if (index !== -1) roomList.value.splice(index, 1)
      if (currentRoom.value?.roomId === roomId) {
        currentRoom.value = null
        roomMembers.value = []
      }
      message.success('已离开房间')
    } catch (error) {
      console.error('离开房间失败:', error)
      throw error
    }
  }

  async function closeRoom(roomId: number): Promise<void> {
    try {
      await RoomAPI.closeRoom(roomId)
      message.success('房间已关闭')
      await fetchUserRooms()
    } catch (error) {
      console.error('关闭房间失败:', error)
      throw error
    }
  }

  function setCurrentRoom(room: RoomInfo | null): void {
    currentRoom.value = room
    if (room) {
      fetchRoomMembers(room.roomId)
    } else {
      roomMembers.value = []
    }
  }

  function clearRoomData(): void {
    roomList.value = []
    currentRoom.value = null
    roomMembers.value = []
    pagination.value = { current: 1, pageSize: 10, total: 0 }
  }

  async function updateRoomPassword(roomId: number, data: UpdateRoomPasswordRequest): Promise<void> {
    await RoomAPI.updateRoomPassword(roomId, data)
    const idx = roomList.value.findIndex(r => r.roomId === roomId)
    if (idx !== -1) {
      roomList.value[idx] = { ...roomList.value[idx], passwordMode: data.mode, passwordVisible: data.visible ?? 1, currentPassword: undefined, remainingSeconds: undefined }
    }
    if (currentRoom.value?.roomId === roomId) {
      currentRoom.value = { ...currentRoom.value, passwordMode: data.mode, passwordVisible: data.visible ?? 1, currentPassword: undefined, remainingSeconds: undefined }
    }
  }

  async function getRoomCurrentPassword(roomId: number): Promise<RoomInfo | null> {
    try {
      const response = await RoomAPI.getRoomCurrentPassword(roomId)
      return response.data
    } catch (error) {
      console.error('获取房间密码失败:', error)
      return null
    }
  }

  async function renewRoom(roomId: number, extendHours: number): Promise<void> {
    const response = await RoomAPI.renewRoom(roomId, extendHours)
    const updatedRoom = response.data
    const idx = roomList.value.findIndex(r => r.roomId === roomId)
    if (idx !== -1) {
      roomList.value[idx] = updatedRoom
    }
    if (currentRoom.value?.roomId === roomId) {
      currentRoom.value = updatedRoom
    }
  }

  async function reopenRoom(roomId: number, expireHours: number): Promise<void> {
    const response = await RoomAPI.reopenRoom(roomId, expireHours)
    const updatedRoom = response.data
    const idx = roomList.value.findIndex(r => r.roomId === roomId)
    if (idx !== -1) {
      roomList.value[idx] = updatedRoom
    }
    if (currentRoom.value?.roomId === roomId) {
      currentRoom.value = updatedRoom
    }
  }

  // ==================== 返回 ====================

  return {
    // 状态
    roomList,
    currentRoom,
    roomMembers,
    loading,
    createLoading,
    joinLoading,
    membersLoading,
    pagination,
    // 计算属性
    hasRooms,
    isRoomOwner,
    currentRoomMemberCount,
    // 方法
    fetchUserRooms,
    createRoom,
    requestJoinRoomAsync,
    queryJoinResult,
    prefetchAfterJoin,
    getRoomByCode,
    fetchRoomMembers,
    leaveRoom,
    closeRoom,
    setCurrentRoom,
    clearRoomData,
    updateRoomPassword,
    getRoomCurrentPassword,
    renewRoom,
    reopenRoom
  }
})
