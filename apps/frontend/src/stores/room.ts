import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import type { 
  RoomInfo, 
  RoomMember, 
  CreateRoomRequest, 
  JoinRoomRequest 
} from '@/types/room'
import type { BaseQuery, PageResult } from '@/types/api'
import { RoomAPI } from '@/api/room'
import { useAuthStore } from './auth'

/**
 * 房间状态管理Store
 */
export const useRoomStore = defineStore('room', () => {
  // ==================== 状态定义 ====================
  
  // 房间列表
  const roomList = ref<RoomInfo[]>([])
  const currentRoom = ref<RoomInfo | null>(null)
  const roomMembers = ref<RoomMember[]>([])
  
  // 加载状态
  const loading = ref(false)
  const createLoading = ref(false)
  const joinLoading = ref(false)
  const membersLoading = ref(false)
  
  // 分页信息
  const pagination = ref({
    current: 1,
    pageSize: 10,
    total: 0
  })

  // ==================== 计算属性 ====================
  
  // 是否有房间
  const hasRooms = computed(() => roomList.value.length > 0)
  
  // 当前用户是否为房主
  const isRoomOwner = computed(() => {
    const authStore = useAuthStore()
    return currentRoom.value?.ownerId === authStore.user?.userId
  })
  
  // 当前房间成员数量
  const currentRoomMemberCount = computed(() => roomMembers.value.length)

  // ==================== 操作方法 ====================
  
  /**
   * 获取用户房间列表
   */
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

  /**
   * 创建房间
   */
  async function createRoom(roomData: CreateRoomRequest): Promise<RoomInfo | null> {
    createLoading.value = true
    try {
      const response = await RoomAPI.createRoom(roomData)
      const newRoom = response.data
      
      // 将新房间添加到列表顶部
      roomList.value.unshift(newRoom)
      currentRoom.value = newRoom
      
      message.success('房间创建成功')
      return newRoom
    } catch (error) {
      console.error('创建房间失败:', error)
      throw error
    } finally {
      createLoading.value = false
    }
  }

  /**
   * 加入房间
   */
  async function joinRoom(joinData: JoinRoomRequest): Promise<RoomInfo | null> {
    joinLoading.value = true
    try {
      const response = await RoomAPI.joinRoom(joinData)
      const room = response.data
      
      // 检查房间是否已在列表中
      const existingIndex = roomList.value.findIndex(r => r.roomId === room.roomId)
      if (existingIndex === -1) {
        roomList.value.unshift(room)
      } else {
        roomList.value[existingIndex] = room
      }
      
      currentRoom.value = room
      message.success('成功加入房间')
      return room
    } catch (error) {
      console.error('加入房间失败:', error)
      throw error
    } finally {
      joinLoading.value = false
    }
  }

  /**
   * 根据房间码查询房间信息
   */
  async function getRoomByCode(roomCode: string): Promise<RoomInfo | null> {
    try {
      const response = await RoomAPI.getRoomByCode(roomCode)
      return response.data
    } catch (error) {
      console.error('查询房间失败:', error)
      throw error
    }
  }

  /**
   * 获取房间成员列表
   */
  async function fetchRoomMembers(roomId: number): Promise<void> {
    membersLoading.value = true
    try {
      const response = await RoomAPI.getRoomMembers(roomId)
      roomMembers.value = response.data
    } catch (error) {
      console.error('获取房间成员失败:', error)
      message.error('获取房间成员失败')
    } finally {
      membersLoading.value = false
    }
  }

  /**
   * 离开房间
   */
  async function leaveRoom(roomId: number): Promise<void> {
    try {
      await RoomAPI.leaveRoom(roomId)
      
      // 从房间列表中移除
      const index = roomList.value.findIndex(r => r.roomId === roomId)
      if (index !== -1) {
        roomList.value.splice(index, 1)
      }
      
      // 如果是当前房间，清空当前房间信息
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

  /**
   * 关闭房间（仅房主）
   */
  async function closeRoom(roomId: number): Promise<void> {
    try {
      await RoomAPI.closeRoom(roomId)
      
      // 更新房间状态或从列表中移除
      const index = roomList.value.findIndex(r => r.roomId === roomId)
      if (index !== -1) {
        roomList.value.splice(index, 1)
      }
      
      if (currentRoom.value?.roomId === roomId) {
        currentRoom.value = null
        roomMembers.value = []
      }
      
      message.success('房间已关闭')
    } catch (error) {
      console.error('关闭房间失败:', error)
      throw error
    }
  }

  /**
   * 刷新房间信息
   */
  async function refreshRoom(roomId: number): Promise<void> {
    try {
      const index = roomList.value.findIndex(r => r.roomId === roomId)
      if (index !== -1) {
        const response = await RoomAPI.getRoomByCode(roomList.value[index].roomCode)
        roomList.value[index] = response.data
        
        if (currentRoom.value?.roomId === roomId) {
          currentRoom.value = response.data
        }
      }
    } catch (error) {
      console.error('刷新房间信息失败:', error)
    }
  }

  /**
   * 设置当前房间
   */
  function setCurrentRoom(room: RoomInfo | null): void {
    currentRoom.value = room
    if (room) {
      fetchRoomMembers(room.roomId)
    } else {
      roomMembers.value = []
    }
  }

  /**
   * 清空房间数据
   */
  function clearRoomData(): void {
    roomList.value = []
    currentRoom.value = null
    roomMembers.value = []
    pagination.value = {
      current: 1,
      pageSize: 10,
      total: 0
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
    joinRoom,
    getRoomByCode,
    fetchRoomMembers,
    leaveRoom,
    closeRoom,
    refreshRoom,
    setCurrentRoom,
    clearRoomData
  }
}) 