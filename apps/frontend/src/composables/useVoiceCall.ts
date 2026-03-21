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
 * - in-call —[退出]→  active 或 idle/locked（后端广播 call_end 后）
 * - call_end WS → locked（非房主）| idle（房主）
 *
 * Bug 修复说明：
 * 1. 使用 watch(roomId) 替代 onMounted，确保 roomId 有效（> 0）后才查询活跃通话，
 *    避免父组件数据未就绪时发出无效请求。
 * 2. 初始化时如果已被 WS 事件（notifyCallStart）驱动到 active/in-call，则不覆盖。
 * 3. notifyCallStart 修正：只要不是 in-call 就过渡到 active。
 *    原逻辑仅检查 locked|idle，当初始化竞争失败导致状态异常时无法恢复。
 * 4. leaveAndCleanup 增加防重入标记，防止 handleLeave 和 onBeforeUnmount 同时触发。
 * 5. handleLeave 使用 leaveAndCleanup 统一处理，消除重复代码。
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

  // 仅在 roomId 有效后注册 beforeunload，避免无意义注册
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
   * 安全退出并清理 WebRTC，带防重入保护
   */
  async function leaveAndCleanup(): Promise<void> {
    if (leavingInProgress) return
    if (callState.value !== 'in-call' || !currentCall.value?.callId) {
      cleanupWebRTC()
      return
    }
    leavingInProgress = true
    const callId = currentCall.value.callId
    // 先重置本地状态再调 API，防止 UI 闪烁
    cleanupWebRTC()
    currentCall.value = null
    // Bug 修复：退出后应显示 active（通话仍在进行，可重新加入），
    // 而非 locked（仅在通话不存在时才用）
    // 退出后重新拉取活跃通话，若通话仍在则 active，否则 idle/locked
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

    // -----------------------------------------------------------------------
    // 关键修复：主动为已在通话中的参与者建立 PeerConnection
    //
    // 使用 joinOrCreateCall 返回的 CallVO.participants 数据；
    // 若 participants 为空但 participantCount > 0，说明后端未填充参与者列表，
    // 则回退到 getCall 重新拉取完整数据。
    // -----------------------------------------------------------------------
    if (webrtcManager) {
      let existingParticipants = currentCall.value?.participants ?? []
      // 若 participants 为空但 participantCount > 0，重新拉取
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

    // notifyReady 之后服务端会广播 participant-join 给已有成员，
    // 同时本地可能已缓冲了来自已有成员的信令，统一在此处理
    await drainPendingSignals()
  }

  // ---------------------------------------------------------------------------
  // 通话操作
  // ---------------------------------------------------------------------------

  /**
   * 房主开启语音通话（从 idle 状态触发）
   * 调用 joinOrCreateCall，后端广播 call_start，其他人经 notifyCallStart 过渡到 active
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
   * 退出通话（仅自己离开，其他人通话继续）
   */
  async function handleLeave(): Promise<void> {
    if (!currentCall.value || leavingInProgress) return
    actionLoading.value = true
    try {
      await leaveAndCleanup()
      antMessage.success('已离开通话')
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
   *
   * [核心 Bug 修复]
   * 原逻辑：只检查 callState === 'locked' || callState === 'idle'
   * 问题：
   *   - 当 roomId 就绪时，initCallState 和 WS 事件存在竞争。若 WS 事件先到达，
   *     状态已经变为 active；若 initCallState 稍晚完成且 getActiveCall 偶发返回空，
   *     则状态会被重置为 locked/idle，导致其他用户看不到通话进行中的状态。
   *   - 更严重的情况：非房主用户的初始状态默认是 locked，WS 事件到达时已是 locked，
   *     看似能处理，但若 initCallState 在 WS 事件后完成且返回空（竞争窗口内），
   *     就会把 active 覆盖回 locked。
   * 新逻辑：只要不是 in-call（自己已在通话中），收到 call_start 都过渡到 active。
   * 这同时修复了：房主开启后，其他已在页面的用户状态不更新的 bug。
   */
  async function notifyCallStart(callId: number): Promise<void> {
    if (callState.value === 'in-call') return
    try {
      const res = await VoiceAPI.getCall(callId)
      // 确保 participants 字段始终为数组，防止后端返回 null 导致渲染崩溃
      const callData = res.data
      if (callData && !Array.isArray(callData.participants)) {
        callData.participants = []
      }
      currentCall.value = callData
      callState.value = 'active'
    } catch {
      callState.value = 'active'
    }
  }

  /**
   * 收到 call_end 事件：通话已结束
   * - 房主回到 idle（可再次开启）
   * - 非房主回到 locked（等待房主开启）
   */
  function notifyCallEnd(_callId: number): void {
    if (callState.value === 'locked') return // 已是最终状态，忽略
    cleanupWebRTC()
    currentCall.value = null
    callState.value = isOwner.value ? 'idle' : 'locked'
  }

  // ---------------------------------------------------------------------------
  // 信令处理（纯 WebRTC 媒体协商：offer / answer / ice-candidate）
  // 参与者名单管理已迁移到 handleRosterUpdate，不再在此处理。
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
      // 信令仅用于 P2P 协商，in-call 之前的信令直接丢弃（名单变更由 handleRosterUpdate 处理）
      console.log('[WebRTC] Not in-call, dropping signal:', data?.type)
      return
    }
    await handleSignalingInternal(data)
  }

  // ---------------------------------------------------------------------------
  // 名单刷新（由 voice_roster_update WS 事件触发，后端权威数据驱动 WebRTC diff）
  // ---------------------------------------------------------------------------

  async function handleRosterUpdate(callId: number): Promise<void> {
    if (!webrtcManager || callState.value !== 'in-call') return
    if (!currentCall.value?.callId || currentCall.value.callId !== callId) return

    let latestParticipants: any[] = []
    try {
      const res = await VoiceAPI.getCall(callId)
      if (!res.data) return
      if (!Array.isArray(res.data.participants)) res.data.participants = []
      currentCall.value = res.data
      latestParticipants = res.data.participants
    } catch (e) {
      console.warn('[WebRTC] handleRosterUpdate: failed to fetch participants:', e)
      return
    }

    const activeIds = new Set(
      latestParticipants
        .filter((p: any) => p.userId !== currentUserId.value)
        .map((p: any) => Number(p.userId))
    )
    const localIds = new Set(webrtcManager.getParticipantIds())

    // 1. 移除已离开的 PC
    for (const id of localIds) {
      if (!activeIds.has(id)) {
        console.log('[WebRTC] roster: removing departed userId:', id)
        webrtcManager.removeParticipant(id)
      }
    }

    // 2. 为新加入的参与者建立 PC（重新加入者强制重建）
    for (const p of latestParticipants) {
      if (p.userId === currentUserId.value) continue
      const uid = Number(p.userId)
      if (!localIds.has(uid)) {
        // 新增：直接 add（addParticipant 内部已有 already-exists 保护）
        console.log('[WebRTC] roster: adding new userId:', uid)
        await webrtcManager.addParticipant(uid, p.nickname)
      }
      // 已有连接的参与者不做任何操作，保持现有 PC
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
 