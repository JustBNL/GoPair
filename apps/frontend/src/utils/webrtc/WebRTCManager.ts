/**
 * WebRTC管理器 - 核心语音通话实现
 */

export interface RTCConfig {
  iceServers: RTCIceServer[]
  iceCandidatePoolSize?: number
}

export interface CallParticipant {
  userId: number
  nickname: string
  audioStream?: MediaStream
  peerConnection?: RTCPeerConnection
  audioElement?: HTMLAudioElement
  isMuted: boolean
  isSpeaking: boolean
  connectionState: RTCPeerConnectionState
}

export interface CallState {
  callId: number | null
  isInCall: boolean
  isInitiator: boolean
  participants: Map<number, CallParticipant>
  localStream: MediaStream | null
  callStartTime: number | null
  callDuration: number
}

export interface WebRTCCallbacks {
  onCallStateChanged: (state: CallState) => void
  onParticipantJoined: (participant: CallParticipant) => void
  onParticipantLeft: (userId: number) => void
  onParticipantMuted: (userId: number, isMuted: boolean) => void
  onParticipantSpeaking: (userId: number, isSpeaking: boolean) => void
  onConnectionStateChanged: (userId: number, state: RTCPeerConnectionState) => void
  onAudioLevelChanged: (userId: number, level: number) => void
  onError: (error: Error) => void
}

export class WebRTCManager {
  private config: RTCConfig
  private callbacks: Partial<WebRTCCallbacks>
  private callState: CallState
  private localAudioContext: AudioContext | null = null
  private localAnalyser: AnalyserNode | null = null
  private speakingDetectionInterval: number | null = null
  // 在 remoteDescription 设置前到达的 ICE candidate 缓冲区
  private pendingCandidates: Map<number, RTCIceCandidate[]> = new Map()
  // addParticipant 尚未完成时提前到达的 offer 缓冲区
  // 防止 participant-join 和 offer 信号并发时重复创建 PeerConnection
  private pendingOffers: Map<number, RTCSessionDescription> = new Map()
  // 远程音频分析资源追踪：每个参与者独立的 AudioContext / AnalyserNode / interval
  private remoteAudioAnalyses: Map<number, { context: AudioContext, analyser: AnalyserNode, interval: number }> = new Map()

  // 默认WebRTC配置
  private static readonly DEFAULT_CONFIG: RTCConfig = {
    iceServers: [
      { urls: 'stun:stun.l.google.com:19302' },
      { urls: 'stun:stun1.l.google.com:19302' },
      { urls: 'stun:stun2.l.google.com:19302' }
    ],
    iceCandidatePoolSize: 10
  }

  constructor(config?: Partial<RTCConfig>, callbacks?: Partial<WebRTCCallbacks>) {
    this.config = { ...WebRTCManager.DEFAULT_CONFIG, ...config }
    this.callbacks = callbacks || {}

    this.callState = {
      callId: null,
      isInCall: false,
      isInitiator: false,
      participants: new Map(),
      localStream: null,
      callStartTime: null,
      callDuration: 0
    }
  }

  /**
   * 初始化本地音频流
   */
  async initializeLocalStream(): Promise<MediaStream> {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          sampleRate: 48000
        },
        video: false
      })

      console.log(`[WebRTC] initializeLocalStream: 麦克风获取成功，trackCount=${stream.getAudioTracks().length}`)
      this.callState.localStream = stream
      await this.setupAudioAnalysis(stream)

      return stream
    } catch (error) {
      const err = new Error(`获取麦克风权限失败: ${error}`)
      console.error(`[WebRTC] initializeLocalStream: 麦克风获取失败`, error)
      this.callbacks.onError?.(err)
      throw err
    }
  }

  /**
   * 设置音频分析（用于检测说话状态）
   */
  private async setupAudioAnalysis(stream: MediaStream): Promise<void> {
    try {
      this.localAudioContext = new AudioContext()
      this.localAnalyser = this.localAudioContext.createAnalyser()

      const source = this.localAudioContext.createMediaStreamSource(stream)
      source.connect(this.localAnalyser)

      this.localAnalyser.fftSize = 256
      this.localAnalyser.smoothingTimeConstant = 0.8

      this.startSpeakingDetection()
    } catch (error) {
      console.error('音频分析设置失败:', error)
    }
  }

  /**
   * 开始说话检测
   */
  private startSpeakingDetection(): void {
    if (!this.localAnalyser) return

    const dataArray = new Uint8Array(this.localAnalyser.frequencyBinCount)
    let isSpeaking = false

    const checkSpeaking = () => {
      if (!this.localAnalyser || !this.callState.isInCall) return

      this.localAnalyser.getByteFrequencyData(dataArray)

      // 计算音频能量
      let total = 0
      for (let i = 0; i < dataArray.length; i++) total += dataArray[i]
      const average = total / dataArray.length
      const threshold = 30 // 说话检测阈值

      const newIsSpeaking = average > threshold

      if (newIsSpeaking !== isSpeaking) {
        isSpeaking = newIsSpeaking
        // 这里可以通过信令通知其他参与者
        // this.callbacks.onParticipantSpeaking?.(currentUserId, isSpeaking)
      }
    }

    this.speakingDetectionInterval = window.setInterval(checkSpeaking, 100)
  }

  /**
   * 停止说话检测
   */
  private stopSpeakingDetection(): void {
    if (this.speakingDetectionInterval) {
      clearInterval(this.speakingDetectionInterval)
      this.speakingDetectionInterval = null
    }
  }

  /**
   * 清理指定参与者的远程音频分析资源
   */
  private cleanupRemoteAudioAnalysis(userId: number): void {
    const analysis = this.remoteAudioAnalyses.get(userId)
    if (!analysis) return
    clearInterval(analysis.interval)
    analysis.analyser.disconnect()
    if (analysis.context.state !== 'closed') {
      analysis.context.close()
    }
    this.remoteAudioAnalyses.delete(userId)
  }

  /**
   * 清理所有远程音频分析资源（leaveCall 时调用）
   */
  private cleanupAllRemoteAudioAnalyses(): void {
    this.remoteAudioAnalyses.forEach((_, userId) => {
      this.cleanupRemoteAudioAnalysis(userId)
    })
  }

  /**
   * 创建新的对等连接
   */
  private createPeerConnection(userId: number): RTCPeerConnection {
    const peerConnection = new RTCPeerConnection(this.config)

    // 添加本地音频流
    if (this.callState.localStream) {
      this.callState.localStream.getTracks().forEach(track => {
        peerConnection.addTrack(track, this.callState.localStream!)
      })
    }

    // 处理ICE候选
    peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        console.log(`[WebRTC] onicecandidate: userId=${userId}, candidate=${event.candidate.candidate.substring(0, 60)}...`)
        this.sendSignalingMessage({
          type: 'ice-candidate',
          targetUserId: userId,
          candidate: event.candidate
        })
      }
    }

    // 处理远程音频流
    // [Bug Fix] event.streams 可能为空数组（某些浏览器在 unified-plan 下 track 先于 stream 到达）
    // 若 streams[0] 不存在，则用 event.track 手动构造 MediaStream
    peerConnection.ontrack = (event) => {
      console.log(`[WebRTC] ontrack: userId=${userId}, streams=${event.streams?.length ?? 0}, track.kind=${event.track?.kind}`)
      const remoteStream = (event.streams && event.streams.length > 0)
        ? event.streams[0]
        : new MediaStream([event.track])
      this.handleRemoteStream(userId, remoteStream)
    }

    // 连接状态变化
    peerConnection.onconnectionstatechange = () => {
      console.log(`[WebRTC] onconnectionstatechange: userId=${userId}, state=${peerConnection.connectionState}`)
      const participant = this.callState.participants.get(userId)
      if (participant) {
        participant.connectionState = peerConnection.connectionState
        this.callbacks.onConnectionStateChanged?.(userId, peerConnection.connectionState)
      }

      if (peerConnection.connectionState === 'failed') {
        this.handleConnectionFailure(userId)
      }
    }

    return peerConnection
  }

  /**
   * 处理远程音频流
   */
  private handleRemoteStream(userId: number, stream: MediaStream): void {
    console.log(`[WebRTC] handleRemoteStream: userId=${userId}, streamId=${stream.id}, trackCount=${stream.getTracks().length}`)
    const participant = this.callState.participants.get(userId)
    if (!participant) {
      console.warn(`[WebRTC] handleRemoteStream: participant=${userId} 不存在，忽略远程流`)
      return
    }
    if (!stream) {
      console.warn(`[WebRTC] handleRemoteStream: stream 为空，忽略`)
      return
    }

    participant.audioStream = stream

    // 创建音频元素播放远程音频，必须挂载到 DOM 才能自动播放
    const audioElement = document.createElement('audio')
    audioElement.srcObject = stream
    audioElement.autoplay = true
    audioElement.setAttribute('playsinline', '') // iOS Safari 内联播放
    audioElement.setAttribute('data-voice-user-id', String(userId))
    audioElement.style.display = 'none'
    document.body.appendChild(audioElement)
    console.log(`[WebRTC] handleRemoteStream: audioElement 已挂载到 DOM，userId=${userId}`)
    audioElement.play().then(() => {
      console.log(`[WebRTC] handleRemoteStream: play() 成功，userId=${userId}`)
    }).catch((err) => {
      console.warn(`[WebRTC] handleRemoteStream: play() 被浏览器阻止，userId=${userId}，err=${err}`)
    })
    participant.audioElement = audioElement

    // 设置远程音频分析
    this.setupRemoteAudioAnalysis(userId, stream)
  }

  /**
   * 设置远程音频分析（说话检测 + 音量电平）
   * 音频资源（AudioContext / AnalyserNode / interval）均纳入追踪，随时可清理
   */
  private setupRemoteAudioAnalysis(userId: number, stream: MediaStream): void {
    try {
      const audioContext = new AudioContext()
      const analyser = audioContext.createAnalyser()
      const source = audioContext.createMediaStreamSource(stream)

      source.connect(analyser)
      analyser.fftSize = 256

      const dataArray = new Uint8Array(analyser.frequencyBinCount)

      const checkRemoteSpeaking = () => {
        const participant = this.callState.participants.get(userId)
        if (!participant || !this.callState.isInCall) return

        analyser.getByteFrequencyData(dataArray)
        let total = 0
        for (let i = 0; i < dataArray.length; i++) total += dataArray[i]
        const average = total / dataArray.length
        const isSpeaking = average > 20

        if (participant.isSpeaking !== isSpeaking) {
          participant.isSpeaking = isSpeaking
          this.callbacks.onParticipantSpeaking?.(userId, isSpeaking)
        }

        this.callbacks.onAudioLevelChanged?.(userId, average)
      }

      const intervalId = setInterval(checkRemoteSpeaking, 100)
      this.remoteAudioAnalyses.set(userId, { context: audioContext, analyser, interval: intervalId })
    } catch (error) {
      console.error('远程音频分析设置失败:', error)
    }
  }

  /**
   * 发起通话
   */
  async initiateCall(callId: number, participantIds: number[]): Promise<void> {
    try {
      if (this.callState.isInCall) {
        throw new Error('已在通话中')
      }

      await this.initializeLocalStream()

      this.callState.callId = callId
      this.callState.isInCall = true
      this.callState.isInitiator = true
      this.callState.callStartTime = Date.now()

      // 为每个参与者创建连接
      for (const userId of participantIds) {
        await this.addParticipant(userId)
      }

      this.callbacks.onCallStateChanged?.(this.callState)
    } catch (error) {
      this.callbacks.onError?.(error as Error)
      throw error
    }
  }

  /**
   * 加入通话（本地流已由外部 initializeLocalStream 初始化）
   */
  async joinCall(callId: number): Promise<void> {
    try {
      if (this.callState.isInCall) {
        throw new Error('已在通话中')
      }

      this.callState.callId = callId
      this.callState.isInCall = true
      this.callState.isInitiator = false
      this.callState.callStartTime = Date.now()

      this.callbacks.onCallStateChanged?.(this.callState)
    } catch (error) {
      this.callbacks.onError?.(error as Error)
      throw error
    }
  }

  /**
   * 添加参与者
   */
  async addParticipant(userId: number, nickname?: string): Promise<void> {
    const existing = this.callState.participants.get(userId)
    if (existing) {
      // 若连接已失败或关闭，移除旧记录以允许重建
      if (existing.peerConnection?.connectionState === 'failed' ||
          existing.peerConnection?.connectionState === 'closed' ||
          existing.peerConnection?.iceConnectionState === 'failed' ||
          existing.peerConnection?.iceConnectionState === 'closed') {
        console.log(`[WebRTC] addParticipant: userId=${userId} 连接已失败，移除旧记录并重建`)
        this.removeParticipant(userId)
      } else {
        console.log(`[WebRTC] addParticipant: userId=${userId} 已存在且连接正常，跳过`)
        return
      }
    }

    const peerConnection = this.createPeerConnection(userId)

    // createPeerConnection 内部会尝试 addTrack，但如果当时 localStream 为 null，这里补充添加
    // 因为麦克风的权限申请是异步的，在浏览器上的体现就是会进行一个提示用户是否需要开启浏览器麦克风权限
    if (this.callState.localStream) {
      const existingSenders = peerConnection.getSenders()
      const hasAudioSender = existingSenders.some(s => s.track?.kind === 'audio')
      if (!hasAudioSender) {
        this.callState.localStream.getTracks().forEach(track => {
          peerConnection.addTrack(track, this.callState.localStream!)
        })
      }
    }

    const participant: CallParticipant = {
      userId,
      nickname: nickname || `用户${userId}`,
      peerConnection,
      isMuted: false,
      isSpeaking: false,
      connectionState: 'new'
    }

    this.callState.participants.set(userId, participant)
    console.log(`[WebRTC] addParticipant: 成功，userId=${userId}, 当前 participants=${Array.from(this.callState.participants.keys()).join(',')}`)

    // Glare 解决方案：userId 较大的一方主动发 offer，较小的一方等待
    const currentUserId = this.getCurrentUserId()
    const shouldSendOffer = currentUserId !== null && currentUserId > userId
    console.log(`[WebRTC] addParticipant: userId=${userId}, currentUserId=${currentUserId}, shouldSendOffer=${shouldSendOffer}`)
    if (shouldSendOffer) {
      if (!this.callState.localStream) {
        console.warn(`[WebRTC] addParticipant: userId=${userId} - localStream 未就绪，跳过发 offer`)
      } else {
        try {
          const offer = await peerConnection.createOffer({
            offerToReceiveAudio: true,
            offerToReceiveVideo: false
          })
          await peerConnection.setLocalDescription(offer)
          console.log(`[WebRTC] 发送 offer 给 userId=${userId}, sdp.type=${offer.type}, sdp.sdp长度=${offer.sdp?.length ?? 0}`)
          this.sendSignalingMessage({
            type: 'offer',
            targetUserId: userId,
            sdp: offer
          })
        } catch (err) {
          console.error(`[WebRTC] addParticipant: 发送 offer 失败 userId=${userId}`, err)
        }
      }
    } else {
      console.log(`[WebRTC] addParticipant: userId=${userId} - 等待对方 offer`)
      const bufferedOffer = this.pendingOffers.get(userId)
      if (bufferedOffer) {
        console.log(`[WebRTC] addParticipant: 发现缓冲 offer，处理 userId=${userId}`)
        this.pendingOffers.delete(userId)
        await this.handleOffer(peerConnection, userId, bufferedOffer)
      }
    }

    this.callbacks.onParticipantJoined?.(participant)
  }

  /**
   * 移除参与者
   */
  removeParticipant(userId: number): void {
    const participant = this.callState.participants.get(userId)
    if (!participant) return

    // 关闭连接
    participant.peerConnection?.close()

    // 停止音频播放
    if (participant.audioElement) {
      participant.audioElement.srcObject = null
      participant.audioElement.remove()
    }

    // 清理远程音频分析资源（AudioContext / AnalyserNode / interval）
    this.cleanupRemoteAudioAnalysis(userId)

    this.callState.participants.delete(userId)
    this.pendingCandidates.delete(userId)
    this.pendingOffers.delete(userId)
    console.log(`[WebRTC] removeParticipant: 成功，userId=${userId}`)
    this.callbacks.onParticipantLeft?.(userId)
  }

  async handleSignalingMessage(message: any): Promise<void> {
    console.log(`[WebRTC] handleSignalingMessage: type=${message.type}, fromUserId=${message.fromUserId}, targetUserId=${message.targetUserId}`)
    try {
      const { type, fromUserId, targetUserId, sdp, candidate } = message
      const currentUserId = this.getCurrentUserId()
      if (targetUserId && targetUserId !== currentUserId) {
        console.log(`[WebRTC] handleSignalingMessage: 非目标用户，targetUserId=${targetUserId}, currentUserId=${currentUserId}，忽略`)
        return
      }
      const participant = this.callState.participants.get(fromUserId)
      if (!participant?.peerConnection) {
        console.log(`[WebRTC] handleSignalingMessage: participant=${fromUserId} 不存在，缓冲 offer（type=${type}）`)
        if (type === 'offer') {
          this.pendingOffers.set(fromUserId, sdp)
          return
        }
        return
      }
      const peerConnection = this.callState.participants.get(fromUserId)?.peerConnection
      if (!peerConnection) return
      switch (type) {
        case 'offer':
          await this.handleOffer(peerConnection, fromUserId, sdp)
          break
        case 'answer':
          await this.handleAnswer(peerConnection, sdp, fromUserId)
          break
        case 'ice-candidate':
          await this.handleIceCandidate(peerConnection, candidate, fromUserId)
          break
        default:
          console.warn(`[WebRTC] handleSignalingMessage: 未知 type=${type}`)
      }
    } catch (error) {
      console.error('[WebRTC] handleSignalingMessage: 处理失败', error)
      this.callbacks.onError?.(error as Error)
    }
  }

  private async handleOffer(peerConnection: RTCPeerConnection, fromUserId: number, offer: RTCSessionDescription): Promise<void> {
    console.log(`[WebRTC] handleOffer: fromUserId=${fromUserId}, signalingState=${peerConnection.signalingState}, offer.type=${offer.type}`)
    if (peerConnection.signalingState === 'have-local-offer') {
      console.log(`[WebRTC] handleOffer: signalingState=have-local-offer，执行 rollback`)
      await peerConnection.setLocalDescription({ type: 'rollback' })
    }
    await peerConnection.setRemoteDescription(offer)
    console.log(`[WebRTC] handleOffer: setRemoteDescription 成功`)
    const answer = await peerConnection.createAnswer()
    await peerConnection.setLocalDescription(answer)
    console.log(`[WebRTC] handleOffer: setLocalDescription(answer) 成功，发送 answer 给 fromUserId=${fromUserId}`)
    await this.flushPendingCandidates(peerConnection, fromUserId)
    this.sendSignalingMessage({ type: 'answer', targetUserId: fromUserId, sdp: answer })
  }

  private async handleAnswer(peerConnection: RTCPeerConnection, answer: RTCSessionDescription, fromUserId: number): Promise<void> {
    console.log(`[WebRTC] handleAnswer: fromUserId=${fromUserId}, answer.type=${answer.type}, signalingState=${peerConnection.signalingState}`)
    await peerConnection.setRemoteDescription(answer)
    console.log(`[WebRTC] handleAnswer: setRemoteDescription(answer) 成功`)
    await this.flushPendingCandidates(peerConnection, fromUserId)
  }

  private async handleIceCandidate(peerConnection: RTCPeerConnection, candidate: RTCIceCandidate, fromUserId: number): Promise<void> {
    console.log(`[WebRTC] handleIceCandidate: fromUserId=${fromUserId}, candidate=${candidate?.candidate?.substring(0, 60) ?? 'null'}`)
    if (!peerConnection.remoteDescription) {
      console.log(`[WebRTC] handleIceCandidate: remoteDescription 未就绪，缓冲 candidate`)
      const pending = this.pendingCandidates.get(fromUserId) ?? []
      pending.push(candidate)
      this.pendingCandidates.set(fromUserId, pending)
      return
    }
    await peerConnection.addIceCandidate(candidate)
    console.log(`[WebRTC] handleIceCandidate: addIceCandidate 成功`)
  }

  private async flushPendingCandidates(peerConnection: RTCPeerConnection, userId: number): Promise<void> {
    const pending = this.pendingCandidates.get(userId)
    if (!pending || pending.length === 0) return
    console.log(`[WebRTC] flushPendingCandidates: 刷新 ${pending.length} 个 buffered candidates`)
    this.pendingCandidates.delete(userId)
    for (const candidate of pending) {
      try {
        await peerConnection.addIceCandidate(candidate)
      } catch (e) {
        console.warn(`[WebRTC] flushPendingCandidates: addIceCandidate 失败`, e)
      }
    }
  }

  toggleMute(): boolean {
    if (!this.callState.localStream) return false
    const audioTracks = this.callState.localStream.getAudioTracks()
    if (audioTracks.length === 0) return false
    const isMuted = !audioTracks[0].enabled
    audioTracks.forEach(track => { track.enabled = isMuted })
    return !isMuted
  }

  setVolume(userId: number, volume: number): void {
    const participant = this.callState.participants.get(userId)
    if (participant?.audioElement) {
      participant.audioElement.volume = Math.max(0, Math.min(1, volume / 100))
    }
  }

  leaveCall(): void {
    this.stopSpeakingDetection()
    this.cleanupAllRemoteAudioAnalyses()
    this.callState.participants.forEach((_, userId) => { this.removeParticipant(userId) })
    if (this.callState.localStream) {
      this.callState.localStream.getTracks().forEach(track => track.stop())
      this.callState.localStream = null
    }
    if (this.localAudioContext && this.localAudioContext.state !== 'closed') {
      this.localAudioContext.close()
      this.localAudioContext = null
    }
    this.callState = {
      callId: null, isInCall: false, isInitiator: false,
      participants: new Map(), localStream: null,
      callStartTime: null, callDuration: 0
    }
    this.callbacks.onCallStateChanged?.(this.callState)
  }

  private handleConnectionFailure(userId: number): void {
    console.error(`[WebRTC] handleConnectionFailure: userId=${userId}`)
  }

  /**
   * 发送信令消息。初始为 no-op，等待外部通过 setSignalingSender 注入实际发送逻辑。
   */
  private sendSignalingMessage(message: any): void {
    if (process.env.NODE_ENV !== 'production') {
      console.warn(
        `[WebRTCManager] sendSignalingMessage 未注入 sender，消息将静默丢弃。`,
        `type=${message?.type}, targetUserId=${message?.targetUserId}`
      )
    }
  }

  private getCurrentUserId(): number {
    return 0
  }

  setSignalingSender(sender: (message: any) => void): void {
    const wrappedSender = (message: any) => {
      console.log(`[WebRTC] sendSignalingMessage: >>> 发送信令 type=${message.type}, targetUserId=${message.targetUserId}`)
      sender(message)
    }
    this.sendSignalingMessage = wrappedSender
  }

  setCurrentUserIdGetter(getter: () => number): void {
    this.getCurrentUserId = getter
  }

  hasParticipant(userId: number): boolean {
    return this.callState.participants.has(userId)
  }

  getParticipantIds(): number[] {
    return Array.from(this.callState.participants.keys())
  }

  getCallState(): CallState {
    return { ...this.callState }
  }

  async getNetworkStats(userId?: number): Promise<RTCStatsReport | null> {
    const participant = userId
      ? this.callState.participants.get(userId)
      : Array.from(this.callState.participants.values())[0]
    if (!participant?.peerConnection) return null
    return await participant.peerConnection.getStats()
  }

  destroy(): void {
    this.leaveCall()
  }
}
