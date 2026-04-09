import type { RoomMember } from '@/types/room'

/** 后端 room_member.status 为 0/1；不能用 status || 'offline'，否则 0 会被当成假值 */
export function normalizeMemberPresence(status: unknown): 'online' | 'offline' | 'away' {
  if (status === 'online' || status === 0) return 'online'
  if (status === 'away') return 'away'
  return 'offline'
}

export function memberPresence(m: RoomMember): 'online' | 'offline' | 'away' {
  return normalizeMemberPresence(m.status)
}

export function memberNameInitial(m: RoomMember): string {
  const n = m.nickname?.trim()
  if (n) return n.charAt(0).toUpperCase()
  return 'U'
}

export function normalizeRoomMemberRow(raw: Record<string, unknown>): RoomMember {
  const userId = Number(raw.userId ?? 0)
  const nickname =
    typeof raw.nickname === 'string' && raw.nickname.trim()
      ? raw.nickname.trim()
      : `用户${userId}`
  const roleNum = raw.role as number | undefined
  const isOwner = Boolean(raw.isOwner) || roleNum === 2
  return {
    userId,
    roomId: Number(raw.roomId ?? 0),
    nickname,
    joinTime: String(raw.joinTime ?? ''),
    isOwner,
    status: normalizeMemberPresence(raw.status),
    role: isOwner ? 'owner' : 'member',
    lastActiveTime: raw.lastActiveTime != null ? String(raw.lastActiveTime) : undefined,
    avatar: typeof raw.avatar === 'string' ? raw.avatar : undefined
  }
}

export function normalizeRoomMembersList(data: unknown): RoomMember[] {
  if (!Array.isArray(data)) return []
  return data.map((r) => normalizeRoomMemberRow(r as Record<string, unknown>))
}
