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

      this.callState.localStream = stream
      await this.setupAudioAnalysis(stream)
      
      return stream
    } catch (error) {
      const err = new Error(`获取麦克风权限失败: ${error}`)
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
      const average = dataArray.reduce((sum, value) => sum + value, 0) / dataArray.length
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
        this.sendSignalingMessage({
          type: 'ice-candidate',
          targetUserId: userId,
          candidate: event.candidate
        })
      }
    }

    // 处理远程音频流
    peerConnection.ontrack = (event) => {
      const [remoteStream] = event.streams
      this.handleRemoteStream(userId, remoteStream)
    }

    // 连接状态变化
    peerConnection.onconnectionstatechange = () => {
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
    const participant = this.callState.participants.get(userId)
    if (!participant) return

    participant.audioStream = stream

    // 创建音频元素播放远程音频，必须挂载到 DOM 才能自动播放
    const audioElement = document.createElement('audio')
    audioElement.srcObject = stream
    audioElement.autoplay = true
    audioElement.setAttribute('playsinline', '') // iOS Safari 内联播放
    audioElement.setAttribute('data-voice-user-id', String(userId))
    audioElement.style.display = 'none'
    document.body.appendChild(audioElement)
    audioElement.play().catch(err => console.warn('[WebRTC] audio.play() failed:', err))
    participant.audioElement = audioElement

    // 设置远程音频分析
    this.setupRemoteAudioAnalysis(userId, stream)
  }

  /**
   * 设置远程音频分析
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
        const average = dataArray.reduce((sum, value) => sum + value, 0) / dataArray.length
        const isSpeaking = average > 20

        if (participant.isSpeaking !== isSpeaking) {
          participant.isSpeaking = isSpeaking
          this.callbacks.onParticipantSpeaking?.(userId, isSpeaking)
        }

        this.callbacks.onAudioLevelChanged?.(userId, average)
      }

      setInterval(checkRemoteSpeaking, 100)
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
    if (this.callState.participants.has(userId)) {
      console.log('[WebRTC] addParticipant: already exists, skipping userId:', userId)
      return
    }

    const peerConnection = this.createPeerConnection(userId)

    // createPeerConnection 内部会尝试 addTrack，但如果当时 localStream 为 null，这里补充添加
    if (this.callState.localStream) {
      const existingSenders = peerConnection.getSenders()
      const hasAudioSender = existingSenders.some(s => s.track?.kind === 'audio')
      if (!hasAudioSender) {
        this.callState.localStream.getTracks().forEach(track => {
          peerConnection.addTrack(track, this.callState.localStream!)
          console.log('[WebRTC] addParticipant: addTrack for userId:', userId, 'track:', track.kind)
        })
      }
    } else {
      console.warn('[WebRTC] addParticipant: localStream is null for userId:', userId)
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

    // Glare 解决方案：userId 较大的一方主动发 offer，较小的一方等待
    const currentUserId = this.getCurrentUserId()
    const shouldSendOffer = currentUserId !== null && currentUserId > userId
    console.log('[WebRTC] addParticipant: userId:', userId, 'currentUserId:', currentUserId, 'shouldSendOffer:', shouldSendOffer, 'hasLocalStream:', !!this.callState.localStream)
    if (shouldSendOffer) {
      if (!this.callState.localStream) {
        console.error('[WebRTC] addParticipant: cannot send offer, localStream is null!')
      } else {
        try {
          const offer = await peerConnection.createOffer({
            offerToReceiveAudio: true,
            offerToReceiveVideo: false
          })
          console.log('[WebRTC] addParticipant: offer created for userId:', userId)
          await peerConnection.setLocalDescription(offer)
          console.log('[WebRTC] addParticipant: offer setLocalDescription done, sending to userId:', userId)
          this.sendSignalingMessage({
            type: 'offer',
            targetUserId: userId,
            sdp: offer
          })
          console.log('[WebRTC] addParticipant: offer sent to userId:', userId)
        } catch (err) {
          console.error('[WebRTC] addParticipant: failed to create/send offer for userId:', userId, err)
        }
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

    this.callState.participants.delete(userId)
    this.callbacks.onParticipantLeft?.(userId)
  }

  /**
   * 处理信令消息
   */
  async handleSignalingMessage(message: any): Promise<void> {
    try {
      const { type, fromUserId, targetUserId, sdp, candidate } = message

      if (targetUserId && targetUserId !== this.getCurrentUserId()) {
        return // 消息不是发给当前用户的
      }

      const participant = this.callState.participants.get(fromUserId)
      if (!participant?.peerConnection) {
        // 如果参与者不存在，先添加
        await this.addParticipant(fromUserId)
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
      }
    } catch (error) {
      console.error('处理信令消息失败:', error)
      this.callbacks.onError?.(error as Error)
    }
  }

  /**
   * 处理Offer
   */
  private async handleOffer(peerConnection: RTCPeerConnection, fromUserId: number, offer: RTCSessionDescription): Promise<void> {
    // Glare 处理：如果本地已经发出了 offer（have-local-offer），需要先 rollback
    if (peerConnection.signalingState === 'have-local-offer') {
      console.log('[WebRTC] Glare detected, rolling back local offer for userId:', fromUserId)
      await peerConnection.setLocalDescription({ type: 'rollback' })
    }

    await peerConnection.setRemoteDescription(offer)
    
    const answer = await peerConnection.createAnswer()
    await peerConnection.setLocalDescription(answer)
    
    // 冲刷在 remoteDescription 设置前缓冲的 ICE candidate
    await this.flushPendingCandidates(peerConnection, fromUserId)

    this.sendSignalingMessage({
      type: 'answer',
      targetUserId: fromUserId,
      sdp: answer
    })
  }

  /**
   * 处理Answer
   */
  private async handleAnswer(peerConnection: RTCPeerConnection, answer: RTCSessionDescription, fromUserId: number): Promise<void> {
    await peerConnection.setRemoteDescription(answer)
    // 冲刷在 remoteDescription 设置前缓冲的 ICE candidate
    await this.flushPendingCandidates(peerConnection, fromUserId)
  }

  /**
   * 处理ICE候选
   */
  private async handleIceCandidate(peerConnection: RTCPeerConnection, candidate: RTCIceCandidate, fromUserId: number): Promise<void> {
    if (!peerConnection.remoteDescription) {
      // remoteDescription 尚未设置，缓冲 candidate
      const pending = this.pendingCandidates.get(fromUserId) ?? []
      pending.push(candidate)
      this.pendingCandidates.set(fromUserId, pending)
      console.log('[WebRTC] Buffering ICE candidate for userId:', fromUserId, 'total:', pending.length)
      return
    }
    await peerConnection.addIceCandidate(candidate)
  }

  /**
   * 冲刷指定用户的待处理 ICE candidate
   */
  private async flushPendingCandidates(peerConnection: RTCPeerConnection, userId: number): Promise<void> {
    const pending = this.pendingCandidates.get(userId)
    if (!pending || pending.length === 0) return
    this.pendingCandidates.delete(userId)
    console.log('[WebRTC] Flushing', pending.length, 'buffered ICE candidates for userId:', userId)
    for (const candidate of pending) {
      try {
        await peerConnection.addIceCandidate(candidate)
      } catch (e) {
        console.warn('[WebRTC] Failed to add buffered ICE candidate:', e)
      }
    }
  }

  /**
   * 静音/取消静音
   */
  toggleMute(): boolean {
    if (!this.callState.localStream) return false

    const audioTracks = this.callState.localStream.getAudioTracks()
    const isMuted = !audioTracks[0]?.enabled

    audioTracks.forEach(track => {
      track.enabled = isMuted
    })

    return !isMuted
  }

  /**
   * 设置音量
   */
  setVolume(userId: number, volume: number): void {
    const participant = this.callState.participants.get(userId)
    if (participant?.audioElement) {
      participant.audioElement.volume = Math.max(0, Math.min(1, volume / 100))
    }
  }

  /**
   * 离开通话
   */
  leaveCall(): void {
    // 停止说话检测
    this.stopSpeakingDetection()

    // 关闭所有连接
    this.callState.participants.forEach((participant, userId) => {
      this.removeParticipant(userId)
    })

    // 停止本地流
    if (this.callState.localStream) {
      this.callState.localStream.getTracks().forEach(track => track.stop())
      this.callState.localStream = null
    }

    // 关闭音频上下文
    if (this.localAudioContext && this.localAudioContext.state !== 'closed') {
      this.localAudioContext.close()
      this.localAudioContext = null
    }

    // 重置状态
    this.callState = {
      callId: null,
      isInCall: false,
      isInitiator: false,
      participants: new Map(),
      localStream: null,
      callStartTime: null,
      callDuration: 0
    }

    this.callbacks.onCallStateChanged?.(this.callState)
  }

  /**
   * 处理连接失败
   */
  private handleConnectionFailure(userId: number): void {
    console.error(`与用户 ${userId} 的连接失败`)
    // 可以尝试重新连接或移除参与者
    // this.removeParticipant(userId)
  }

  /**
   * 发送信令消息（需要外部实现）
   */
  private sendSignalingMessage(message: any): void {
    // 这个方法需要被外部WebSocket客户端重写
    console.log('发送信令消息:', message)
  }

  /**
   * 获取当前用户ID（需要外部实现）
   */
  private getCurrentUserId(): number {
    // 这个方法需要被外部重写
    return 0
  }

  /**
   * 设置信令发送函数
   */
  setSignalingSender(sender: (message: any) => void): void {
    this.sendSignalingMessage = sender
  }

  /**
   * 设置当前用户ID获取函数
   */
  setCurrentUserIdGetter(getter: () => number): void {
    this.getCurrentUserId = getter
  }

  /**
   * 获取当前通话状态
   */
  getCallState(): CallState {
    return { ...this.callState }
  }

  /**
   * 获取网络质量统计
   */
  async getNetworkStats(userId?: number): Promise<RTCStatsReport | null> {
    const participant = userId
      ? this.callState.participants.get(userId)
      : Array.from(this.callState.participants.values())[0]

    if (!participant?.peerConnection) return null

    return await participant.peerConnection.getStats()
  }

  /**
   * 销毁实例
   */
  destroy(): void {
    this.leaveCall()
  }
}