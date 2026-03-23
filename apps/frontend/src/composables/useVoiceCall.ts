import { ref, watch, onBeforeUnmount, type Ref } from 'vue'
import { message as antMessage, Modal } from 'ant-design-vue'
import { VoiceAPI } from '@/api/voice'
import { WebRTCManager } from '@/utils/webrtc/WebRTCManager'
import type { CallVO } from '@/types/api'

/**
 * 语音通话状态类型
 * - locked:  房主尚未开启，语音功能锁定（非房主视角的初始状态）
 * - idle:    房主可以开启通话（房主视角的初始状态）
 * - active:  通话进行中，当前用户未加入
 * - in-call: 当前用户已加入通话
 */
export type CallState = 'locked' | 'idle' | 'active' | 'in-call'

export interface UseVoiceCallReturn {
  callState: Ref<CallState>
  currentCall: Ref<CallVO | null>
  loading: Ref<boolean>
  actionLoading: Ref<boolean>
  isMuted: Ref<boolean>
  isSpeakerOff: Ref<boolean>
  handleOpen: () => Promise<void>
  handleJoin: () => Promise<void>
  handleLeave: () => Promise<void>
  handleEnd: () => void
  handleToggleMute: () => void
  handleToggleSpeaker: () => void
  notifyCallStart: (callId: number) => Promise<void>
  notifyCallEnd: (callId: number) => void
  handleSignaling: (data: any) => Promise<void>
  handleRosterUpdate: (callId: number) => Promise<void>
  handleLeaveBeforeUnmount: () => Promise<void>
}

/**
 * 语音通话业务逻辑 Composable
 *
 * 状态转换规则：
 * - 初始化（roomId 就绪后）：getActiveCall 有结果 → active；无结果且 isOwner → idle；无结果且非 isOwner → locked
 * - locked  —[call_start WS]→  active
 * - idle    —[房主点开启]→  in-call
 * - active  —[任何人加入]→  in-call
 * - in-call —[成员退出]→  active（通话继续，可重新加入）
 * - in-call —[房主退出通话]→  active（房主只断本地WebRTC，通话继续，其他人不受影响）
 * - call_end WS → locked（非房主）| idle（房主）
 *
 * Bug 修复说明：
 * ① handleRosterUpdate 的 callId 校验改为直接使用传入参数 callId，不再依赖 currentCall.value.callId。
 *    原因：leaveAndCleanup 会将 currentCall 置为 null，若 roster_update WS 事件在
 *    refreshCallStateAfterLeave 完成之前到达，currentCall.value 为 null，
 *    导致 handleRosterUpdate 提前 return，重新加入者的 PeerConnection 永远无法重建。
 * ② handleRosterUpdate 修复 localIds 快照时机：先执行所有 removeParticipant，
 *    再重新获取当前活跃 PC 列表作为 localIds，然后对 latestParticipants 做 add 判断。
 *    原因：remove 之前的 localIds 快照包含即将被删除的 userId，导致 add 判断时
 *    认为该用户「已存在」而跳过重建。
 * ③ 房主执行 handleLeave 时，仅断开本地 WebRTC 并将状态变为 active，不调用后端 leaveCall API。
 *    原因：后端 leaveCall 在剩余参与者为空时会自动 terminateCall + 广播 call_end，
 *    强制所有人退出，违背「房主退出不关闭通话」的需求。
 * ④ notifyCallStart 中 getCall 失败时用 getActiveCall 兜底，确保 currentCall 总能填充。
 */
export function useVoiceCall(
  roomId: Ref<number>,
  currentUserId: Ref<number>,
  isOwner: Ref<boolean>
): UseVoiceCallReturn {
  const callState = ref<CallState>('locked')
  const currentCall = ref<CallVO | null>(null)
  const loading = ref(false)
  const actionLoading = ref(false)
  const isMuted = ref(false)
  const isSpeakerOff = ref(false)

  let webrtcManager: WebRTCManager | null = null
  const pendingSignals: any[] = []

  // 防重入 / 防竞争标记
  let initialized = false
  let leavingInProgress = false
  // roster update serial mutex
  let rosterUpdating = false
  let rosterPendingCallId = null

  // ---------------------------------------------------------------------------
  // 初始化：watch roomId，有效时查询当前活跃通话
  // ---------------------------------------------------------------------------

  const initCallState = async (rid: number): Promise<void> => {
    if (rid <= 0 || initialized) return
    initialized = true
    loading.value = true
    try {
      const res = await VoiceAPI.getActiveCall(rid)
      // 若此时已因 WS 事件（notifyCallStart）驱动到 active/in-call，不覆盖
      if (callState.value === 'locked' || callState.value === 'idle') {
        if (res.data) {
          if (!Array.isArray(res.data.participants)) res.data.participants = []
          currentCall.value = res.data
          callState.value = 'active'
        } else {
          callState.value = isOwner.value ? 'idle' : 'locked'
        }
      }
    } catch {
      if (callState.value === 'locked' || callState.value === 'idle') {
        callState.value = isOwner.value ? 'idle' : 'locked'
      }
    } finally {
      loading.value = false
    }
  }

  const stopRoomIdWatch = watch(
    roomId,
    (newId) => {
      if (newId > 0 && !initialized) {
        initCallState(newId)
      }
    },
    { immediate: true }
  )

  // ---------------------------------------------------------------------------
  // 页面关闭时的 sendBeacon 兜底
  // ---------------------------------------------------------------------------

  function handleBeforeUnload(): void {
    if (callState.value === 'in-call' && currentCall.value?.callId) {
      const callId = currentCall.value.callId
      cleanupWebRTC()
      navigator.sendBeacon(
        `/voice/${callId}/leave`,
        new Blob([JSON.stringify({})], { type: 'application/json' })
      )
    }
  }

  // 仅在 roomId 有效后注册 beforeunload
  const stopBeaconWatch = watch(
    roomId,
    (newId) => {
      if (newId > 0) {
        window.addEventListener('beforeunload', handleBeforeUnload)
        stopBeaconWatch()
      }
    },
    { immediate: true }
  )

  onBeforeUnmount(async () => {
    stopRoomIdWatch()
    window.removeEventListener('beforeunload', handleBeforeUnload)
    await leaveAndCleanup()
  })

  // ---------------------------------------------------------------------------
  // WebRTC 工具方法
  // ---------------------------------------------------------------------------

  function cleanupWebRTC(): void {
    if (webrtcManager) {
      webrtcManager.leaveCall()
      webrtcManager = null
    }
    pendingSignals.length = 0
    isMuted.value = false
    isSpeakerOff.value = false
  }

  async function drainPendingSignals(): Promise<void> {
    if (pendingSignals.length === 0) return
    console.log('[WebRTC] Flushing buffered signals:', pendingSignals.length)
    const signals = pendingSignals.splice(0, pendingSignals.length)
    for (const sig of signals) {
      await handleSignalingInternal(sig)
    }
  }

  /**
   * 安全退出并清理 WebRTC，带防重入保护。
   *
   * [Bug 修复③] 区分房主与成员：
   * - 成员退出：调用后端 leaveCall API，后端更新参与者状态并广播 roster_update
   * - 房主退出：仅断开本地 WebRTC，状态变为 active（通话继续），不调用 leaveCall API
   *   防止后端因剩余参与者为空时自动 terminateCall + 广播 call_end
   */
  async function leaveAndCleanup(): Promise<void> {
    if (leavingInProgress) return
    if (callState.value !== 'in-call' || !currentCall.value?.callId) {
      cleanupWebRTC()
      return
    }
    leavingInProgress = true
    const callId = currentCall.value.callId

    // 先清理本地 WebRTC 资源
    cleanupWebRTC()

    if (isOwner.value) {
      // --- 房主：标记为已离开，但通话继续存在 ---
      // 调用专用 ownerLeave 接口：后端只更新 leaveTime，不 terminateCall，
      // 广播 voice_roster_update 让其他成员移除房主的 PeerConnection。
      try {
        await VoiceAPI.ownerLeaveCall(callId)
      } catch (e) {
        console.warn('[WebRTC] ownerLeaveCall API failed:', e)
      } finally {
        leavingInProgress = false
      }
      // 重新拉取最新通话状态（房主已不在参与者列表中）
      await refreshCallStateAfterLeave()
    } else {
      // --- 成员：调用后端 leaveCall，通知其他人 ---
      currentCall.value = null
      try {
        await VoiceAPI.leaveCall(callId)
      } catch (e) {
        console.warn('[WebRTC] leaveCall API failed (may be ok on unload):', e)
      } finally {
        leavingInProgress = false
      }
      // 退出后刷新通话状态
      await refreshCallStateAfterLeave()
    }
  }

  /**
   * 退出后刷新通话状态：若通话仍活跃则 active，否则 idle/locked
   */
  async function refreshCallStateAfterLeave(): Promise<void> {
    try {
      const res = await VoiceAPI.getActiveCall(roomId.value)
      if (res.data) {
        const callData = res.data
        if (!Array.isArray(callData.participants)) callData.participants = []
        currentCall.value = callData
        callState.value = 'active'
      } else {
        currentCall.value = null
        callState.value = isOwner.value ? 'idle' : 'locked'
      }
    } catch {
      currentCall.value = null
      callState.value = isOwner.value ? 'idle' : 'locked'
    }
  }

  // ---------------------------------------------------------------------------
  // WebRTC 建立公共逻辑（handleOpen 和 handleJoin 共用）
  // ---------------------------------------------------------------------------

  async function setupWebRTCAndJoin(resolvedCallId: number): Promise<void> {
    webrtcManager = new WebRTCManager({}, {
      onError: (err) => {
        console.error('[WebRTC] Error:', err)
        antMessage.error('音频连接失败: ' + err.message)
      }
    })
    webrtcManager.setCurrentUserIdGetter(() => currentUserId.value)
    webrtcManager.setSignalingSender((msg: any) => {
      VoiceAPI.forwardSignaling({
        callId: resolvedCallId,
        type: msg.type,
        targetUserId: msg.targetUserId,
        data: msg.sdp ?? msg.candidate ?? msg
      }).catch((e) => console.error('[WebRTC] Signaling send failed:', e))
    })

    if (!window.isSecureContext) {
      antMessage.error('当前页面不是安全上下文（需要 HTTPS），无法获取麦克风。请使用 https:// 地址访问。', 6)
      webrtcManager = null
    } else if (!navigator.mediaDevices?.getUserMedia) {
      antMessage.error('当前浏览器不支持麦克风访问，请使用 Chrome / Safari 最新版。', 6)
      webrtcManager = null
    } else {
      try {
        await webrtcManager.initializeLocalStream()
        console.log('[WebRTC] Local audio stream ready, callId:', resolvedCallId)
      } catch (err: any) {
        const msg = err?.message ?? String(err)
        if (msg.includes('Permission') || msg.includes('NotAllowed') || msg.includes('权限')) {
          antMessage.warning('请在浏览器中允许麦克风权限后重试。', 5)
        } else {
          antMessage.warning('麦克风获取失败，通话已加入但无音频: ' + msg)
        }
        webrtcManager = null
      }
    }

    if (webrtcManager) {
      await webrtcManager.joinCall(resolvedCallId)
    }

    callState.value = 'in-call'
    isMuted.value = false
    isSpeakerOff.value = false

    // 主动为已在通话中的参与者建立 PeerConnection
    if (webrtcManager) {
      let existingParticipants = currentCall.value?.participants ?? []
      // 若 participants 为空但 participantCount > 0，重新拉取完整数据
      if (existingParticipants.length === 0 && (currentCall.value?.participantCount ?? 0) > 0) {
        console.log('[WebRTC] participants array empty but participantCount > 0, fetching full call data...')
        try {
          const fullRes = await VoiceAPI.getCall(resolvedCallId)
          if (fullRes.data) {
            if (!Array.isArray(fullRes.data.participants)) fullRes.data.participants = []
            currentCall.value = fullRes.data
            existingParticipants = fullRes.data.participants
          }
        } catch (e) {
          console.warn('[WebRTC] Failed to fetch full call data for participant setup:', e)
          // 兜底：用 getActiveCall
          try {
            const activeRes = await VoiceAPI.getActiveCall(roomId.value)
            if (activeRes.data) {
              if (!Array.isArray(activeRes.data.participants)) activeRes.data.participants = []
              currentCall.value = activeRes.data
              existingParticipants = activeRes.data.participants
            }
          } catch (e2) {
            console.warn('[WebRTC] Fallback getActiveCall also failed:', e2)
          }
        }
      }
      if (existingParticipants.length > 0) {
        console.log('[WebRTC] Existing participants:', existingParticipants.map((p: any) => p.userId))
        for (const p of existingParticipants) {
          if (p.userId !== currentUserId.value) {
            await webrtcManager.addParticipant(p.userId, p.nickname)
          }
        }
      }
    }

    await VoiceAPI.notifyReady(resolvedCallId)
    console.log('[WebRTC] notifyReady sent, callId:', resolvedCallId)

    // Refresh currentCall after notifyReady so VoiceCallPanel shows the
    // complete participant list including the current user.
    try {
      const refreshed = await VoiceAPI.getCall(resolvedCallId)
      if (refreshed.data) {
        if (!Array.isArray(refreshed.data.participants)) refreshed.data.participants = []
        currentCall.value = refreshed.data
        console.log('[WebRTC] currentCall refreshed, participants:',
          refreshed.data.participants.map((p) => p.userId))
      }
    } catch (e) {
      console.warn('[WebRTC] post-notifyReady getCall failed, trying getActiveCall:', e)
      try {
        const ar = await VoiceAPI.getActiveCall(roomId.value)
        if (ar.data) {
          if (!Array.isArray(ar.data.participants)) ar.data.participants = []
          currentCall.value = ar.data
        }
      } catch {}
    }

    await drainPendingSignals()
  }

  // ---------------------------------------------------------------------------
  // 通话操作
  // ---------------------------------------------------------------------------

  /**
   * 房主开启语音通话（从 idle 状态触发）
   */
  async function handleOpen(): Promise<void> {
    actionLoading.value = true
    try {
      const res = await VoiceAPI.joinOrCreateCall(roomId.value)
      const callData = res.data
      if (callData && !Array.isArray(callData.participants)) callData.participants = []
      currentCall.value = callData
      await setupWebRTCAndJoin(callData.callId)
      antMessage.success('语音频道已开启')
    } catch (error: any) {
      cleanupWebRTC()
      callState.value = 'idle'
      antMessage.error(error.response?.data?.msg || '开启通话失败')
    } finally {
      actionLoading.value = false
    }
  }

  /**
   * 加入通话（从 active 状态触发，房主 / 成员均可）
   */
  async function handleJoin(): Promise<void> {
    actionLoading.value = true
    try {
      const res = await VoiceAPI.joinOrCreateCall(roomId.value)
      const callData = res.data
      if (callData && !Array.isArray(callData.participants)) callData.participants = []
      currentCall.value = callData
      await setupWebRTCAndJoin(callData.callId)
      antMessage.success('已加入通话')
    } catch (error: any) {
      cleanupWebRTC()
      callState.value = 'active'
      antMessage.error(error.response?.data?.msg || '加入通话失败')
    } finally {
      actionLoading.value = false
    }
  }

  /**
   * 退出通话
   * [Bug 修复③] 房主退出时不调用后端 leaveCall，只断开本地 WebRTC
   */
  async function handleLeave(): Promise<void> {
    if (!currentCall.value || leavingInProgress) return
    actionLoading.value = true
    try {
      await leaveAndCleanup()
      antMessage.success(isOwner.value ? '已退出通话（通话仍在进行中）' : '已离开通话')
    } catch (error: any) {
      antMessage.error(error.response?.data?.msg || '操作失败')
    } finally {
      actionLoading.value = false
    }
  }

  /**
   * 房主结束通话（强制所有人退出）
   * 后端广播 call_end，所有客户端经 notifyCallEnd 回到初始状态
   */
  function handleEnd(): void {
    if (!currentCall.value) return
    Modal.confirm({
      title: '确认结束通话',
      content: '结束通话后，所有参与者将被强制退出语音频道。确定要结束吗？',
      okText: '结束通话',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        if (!currentCall.value) return
        actionLoading.value = true
        try {
          const callId = currentCall.value.callId
          cleanupWebRTC()
          currentCall.value = null
          callState.value = 'idle'
          await VoiceAPI.endCall(callId)
          antMessage.success('通话已结束')
        } catch (error: any) {
          antMessage.error(error.response?.data?.msg || '结束通话失败')
        } finally {
          actionLoading.value = false
        }
      }
    })
  }

  function handleToggleMute(): void {
    if (!webrtcManager) return
    isMuted.value = webrtcManager.toggleMute()
  }

  function handleToggleSpeaker(): void {
    isSpeakerOff.value = !isSpeakerOff.value
    document.querySelectorAll('audio[data-voice-user-id]').forEach((el) => {
      ;(el as HTMLAudioElement).muted = isSpeakerOff.value
    })
  }

  // ---------------------------------------------------------------------------
  // WebSocket 通知处理（由父组件 useRoomWebSocket 回调调用）
  // ---------------------------------------------------------------------------

  /**
   * 收到 call_start 事件：通话已开启
   * [Bug 修复④] getCall 失败时用 getActiveCall 兜底，确保 currentCall 总能填充
   */
  async function notifyCallStart(callId: number): Promise<void> {
    if (callState.value === 'in-call') return
    try {
      const res = await VoiceAPI.getCall(callId)
      const callData = res.data
      if (callData && !Array.isArray(callData.participants)) {
        callData.participants = []
      }
      currentCall.value = callData
      callState.value = 'active'
    } catch {
      // getCall 失败（如 500）时，用 getActiveCall 兜底
      try {
        const activeRes = await VoiceAPI.getActiveCall(roomId.value)
        if (activeRes.data) {
          if (!Array.isArray(activeRes.data.participants)) activeRes.data.participants = []
          currentCall.value = activeRes.data
        }
      } catch {
        // 兜底也失败，至少把状态设为 active，让用户可以尝试加入
      }
      callState.value = 'active'
    }
  }

  /**
   * 收到 call_end 事件：通话已结束
   * - 房主回到 idle（可再次开启）
   * - 非房主回到 locked（等待房主开启）
   */
  function notifyCallEnd(_callId: number): void {
    if (callState.value === 'locked') return
    cleanupWebRTC()
    currentCall.value = null
    callState.value = isOwner.value ? 'idle' : 'locked'
  }

  // ---------------------------------------------------------------------------
  // 信令处理（纯 WebRTC 媒体协商：offer / answer / ice-candidate）
  // ---------------------------------------------------------------------------

  async function handleSignalingInternal(data: any): Promise<void> {
    if (!webrtcManager) {
      console.warn('[WebRTC] Manager not ready, signal dropped:', data?.type)
      return
    }

    const sigType = data?.type
    const fromUserId = data?.fromUserId ?? data?.userId
    console.log('[WebRTC] Processing signal:', sigType, 'from:', fromUserId)

    try {
      if (sigType === 'offer' || sigType === 'answer') {
        const sdpPayload = data?.data
        if (!sdpPayload) {
          console.error('[WebRTC] Missing SDP payload for', sigType)
          return
        }
        const sdpObj = sdpPayload instanceof RTCSessionDescription
          ? sdpPayload
          : new RTCSessionDescription(sdpPayload)
        await webrtcManager.handleSignalingMessage({
          type: sigType,
          fromUserId,
          targetUserId: data?.targetUserId ?? currentUserId.value,
          sdp: sdpObj
        })
      } else if (sigType === 'ice-candidate') {
        const candidatePayload = data?.data
        if (!candidatePayload) {
          console.error('[WebRTC] Missing ICE candidate payload')
          return
        }
        await webrtcManager.handleSignalingMessage({
          type: sigType,
          fromUserId,
          targetUserId: data?.targetUserId ?? currentUserId.value,
          candidate: candidatePayload instanceof RTCIceCandidate
            ? candidatePayload
            : new RTCIceCandidate(candidatePayload)
        })
      } else {
        console.warn('[WebRTC] Unknown signal type (ignored):', sigType)
      }
    } catch (error) {
      console.error('[WebRTC] Error processing signal:', sigType, error)
    }
  }

  async function handleSignaling(data: any): Promise<void> {
    if (!webrtcManager || callState.value !== 'in-call') {
      console.log('[WebRTC] Not in-call, dropping signal:', data?.type)
      return
    }
    await handleSignalingInternal(data)
  }

  // ---------------------------------------------------------------------------
  // 名单刷新（由 voice_roster_update WS 事件触发）
  //
  // [Bug 修复①②]
  // ① 移除对 currentCall.value.callId 的依赖校验：改为直接使用传入参数 callId。
  //    leaveAndCleanup 会将 currentCall 置为 null，若 roster_update 在
  //    refreshCallStateAfterLeave 完成前到达，currentCall 为 null 会导致提前 return，
  //    重新加入者的 PeerConnection 永远无法重建。
  // ② 修复 localIds 快照时机：先执行所有 removeParticipant，再重新获取 localIds，
  //    然后对 latestParticipants 做 add 判断。
  //    原快照在 remove 之前获取，导致被删除的 userId 仍在 localIds 中，
  //    add 判断时认为该用户「已存在」而跳过重建。
  // ---------------------------------------------------------------------------

  async function handleRosterUpdate(callId: number): Promise<void> {
    if (!webrtcManager || callState.value !== 'in-call') return
    if (rosterUpdating) {
      rosterPendingCallId = callId
      return
    }
    rosterUpdating = true
    try {
      await executeRosterUpdate(callId)
      while (rosterPendingCallId !== null) {
        const nextId = rosterPendingCallId
        rosterPendingCallId = null
        if (webrtcManager && callState.value === 'in-call') {
          await executeRosterUpdate(nextId)
        }
      }
    } finally {
      rosterUpdating = false
    }
  }

  async function executeRosterUpdate(callId: number): Promise<void> {
    let latestParticipants: any[] = []
    try {
      const res = await VoiceAPI.getCall(callId)
      if (!res.data) return
      if (!Array.isArray(res.data.participants)) res.data.participants = []
      currentCall.value = res.data
      latestParticipants = res.data.participants
    } catch (e) {
      console.warn('[WebRTC] executeRosterUpdate: getCall failed:', e)
      try {
        const activeRes = await VoiceAPI.getActiveCall(roomId.value)
        if (!activeRes.data) return
        if (!Array.isArray(activeRes.data.participants)) activeRes.data.participants = []
        currentCall.value = activeRes.data
        latestParticipants = activeRes.data.participants
      } catch (e2) {
        console.warn('[WebRTC] executeRosterUpdate: fallback also failed:', e2)
        return
      }
    }

    if (!webrtcManager) return

    const activeIds = new Set(
      latestParticipants
        .filter((p) => p.userId !== currentUserId.value)
        .map((p) => Number(p.userId))
    )

    const localIdsBefore = new Set(webrtcManager.getParticipantIds())
    for (const id of localIdsBefore) {
      if (!activeIds.has(id)) {
        console.log('[WebRTC] roster: removing departed userId:', id)
        webrtcManager.removeParticipant(id)
      }
    }

    const localIdsAfterRemove = new Set(webrtcManager.getParticipantIds())
    for (const p of latestParticipants) {
      if (p.userId === currentUserId.value) continue
      const uid = Number(p.userId)
      if (!localIdsAfterRemove.has(uid)) {
        console.log('[WebRTC] roster: adding new userId:', uid)
        await webrtcManager.addParticipant(uid, p.nickname)
      }
    }
  }
  async function handleLeaveBeforeUnmount(): Promise<void> {
    await leaveAndCleanup()
  }

  return {
    callState,
    currentCall,
    loading,
    actionLoading,
    isMuted,
    isSpeakerOff,
    handleOpen,
    handleJoin,
    handleLeave,
    handleEnd,
    handleToggleMute,
    handleToggleSpeaker,
    notifyCallStart,
    notifyCallEnd,
    handleSignaling,
    handleRosterUpdate,
    handleLeaveBeforeUnmount
  }
} 