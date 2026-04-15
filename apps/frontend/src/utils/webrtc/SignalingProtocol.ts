/**
 * WebRTC信令协议处理器
 */

// 注意: VoiceSignalingClient已迁移到新的Composable架构  
// import type { VoiceSignalingClient } from '@/composables/useVoiceWebSocket'

export interface SignalingMessage {
  type: 'offer' | 'answer' | 'ice-candidate' | 'call-invite' | 'call-accept' | 'call-reject' | 'call-end' | 'participant-join' | 'participant-leave' | 'mute-status' | 'speaking-status'
  callId: number
  fromUserId: number
  targetUserId?: number
  roomId: number
  timestamp: number
  data?: any
}

export interface CallInviteData {
  initiatorNickname: string
  callType: 'ONE_TO_ONE' | 'MULTI_USER'
  participants: number[]
  settings?: {
    autoRecord?: boolean
    allowInvite?: boolean
    muteOnJoin?: boolean
  }
}

export interface WebRTCOfferData {
  sdp: RTCSessionDescription
  iceServers?: RTCIceServer[]
}

export interface WebRTCAnswerData {
  sdp: RTCSessionDescription
}

export interface ICECandidateData {
  candidate: RTCIceCandidate
}

export interface ParticipantStatusData {
  userId: number
  nickname: string
  status: 'joined' | 'left' | 'muted' | 'unmuted' | 'speaking' | 'silent'
  timestamp: number
}

export interface SignalingCallbacks {
  onCallInvite: (message: SignalingMessage, data: CallInviteData) => void
  onCallAccept: (message: SignalingMessage) => void
  onCallReject: (message: SignalingMessage, reason?: string) => void
  onCallEnd: (message: SignalingMessage) => void
  onWebRTCOffer: (message: SignalingMessage, data: WebRTCOfferData) => void
  onWebRTCAnswer: (message: SignalingMessage, data: WebRTCAnswerData) => void
  onICECandidate: (message: SignalingMessage, data: ICECandidateData) => void
  onParticipantJoin: (message: SignalingMessage, data: ParticipantStatusData) => void
  onParticipantLeave: (message: SignalingMessage, data: ParticipantStatusData) => void
  onMuteStatus: (message: SignalingMessage, data: ParticipantStatusData) => void
  onSpeakingStatus: (message: SignalingMessage, data: ParticipantStatusData) => void
  onSignalingError: (error: Error) => void
}

export class SignalingProtocol {
  private signalingClient: any | null = null
  private callbacks: Partial<SignalingCallbacks>
  private currentUserId: number = 0
  private currentRoomId: number = 0
  private messageQueue: SignalingMessage[] = []
  private isConnected: boolean = false

  constructor(callbacks?: Partial<SignalingCallbacks>) {
    this.callbacks = callbacks || {}
  }

  /**
   * 初始化信令协议
   */
  async initialize(signalingClient: any, userId: number, roomId: number): Promise<void> {
    this.signalingClient = signalingClient
    this.currentUserId = userId
    this.currentRoomId = roomId
    
    // 设置WebSocket事件监听
    this.setupSignalingListeners()
    
    // 如果WebSocket已连接，处理队列中的消息
    if (this.signalingClient.isConnected()) {
      this.isConnected = true
      this.processMessageQueue()
    }
  }

  /**
   * 设置信令监听器
   */
  private setupSignalingListeners(): void {
    if (!this.signalingClient) return

    // 设置通用消息处理器
    this.signalingClient.addSignalingHandler('signaling', (data: any) => {
      this.handleIncomingMessage(data.message || data)
    })

    // 设置各种信令消息处理器
    this.signalingClient.addSignalingHandler('call-invite', (data: any) => {
      this.handleIncomingMessage({ type: 'call-invite', ...data })
    })

    this.signalingClient.addSignalingHandler('call-accept', (data: any) => {
      this.handleIncomingMessage({ type: 'call-accept', ...data })
    })

    this.signalingClient.addSignalingHandler('call-reject', (data: any) => {
      this.handleIncomingMessage({ type: 'call-reject', ...data })
    })

    this.signalingClient.addSignalingHandler('call-end', (data: any) => {
      this.handleIncomingMessage({ type: 'call-end', ...data })
    })

    // WebRTC相关消息
    this.signalingClient.addSignalingHandler('webrtc-offer', (data: any) => {
      this.handleIncomingMessage({ type: 'offer', ...data })
    })

    this.signalingClient.addSignalingHandler('webrtc-answer', (data: any) => {
      this.handleIncomingMessage({ type: 'answer', ...data })
    })

    this.signalingClient.addSignalingHandler('ice-candidate', (data: any) => {
      this.handleIncomingMessage({ type: 'ice-candidate', ...data })
    })

    // 参与者状态消息
    this.signalingClient.addSignalingHandler('participant-join', (data: any) => {
      this.handleIncomingMessage({ type: 'participant-join', ...data })
    })

    this.signalingClient.addSignalingHandler('participant-leave', (data: any) => {
      this.handleIncomingMessage({ type: 'participant-leave', ...data })
    })

    this.signalingClient.addSignalingHandler('mute-status', (data: any) => {
      this.handleIncomingMessage({ type: 'mute-status', ...data })
    })

    this.signalingClient.addSignalingHandler('speaking-status', (data: any) => {
      this.handleIncomingMessage({ type: 'speaking-status', ...data })
    })

    // 监听连接状态变化
    this.isConnected = this.signalingClient.isConnected()
    if (this.isConnected) {
      this.processMessageQueue()
    }
  }

  /**
   * 处理接收到的信令消息
   */
  private handleIncomingMessage(rawData: any): void {
    try {
      const message: SignalingMessage = {
        type: rawData.type,
        callId: rawData.callId,
        fromUserId: rawData.fromUserId,
        targetUserId: rawData.targetUserId,
        roomId: rawData.roomId || this.currentRoomId,
        timestamp: rawData.timestamp || Date.now(),
        data: rawData.data
      }

      // 检查消息是否为当前用户
      if (message.targetUserId && message.targetUserId !== this.currentUserId) {
        return // 消息不是发给当前用户的
      }

      // 检查消息是否为当前房间
      if (message.roomId !== this.currentRoomId) {
        return // 消息不是当前房间的
      }

      this.routeMessage(message)
    } catch (error) {
      console.error('处理信令消息失败:', error)
      this.callbacks.onSignalingError?.(error as Error)
    }
  }

  /**
   * 路由消息到对应的处理器
   */
  private routeMessage(message: SignalingMessage): void {
    switch (message.type) {
      case 'call-invite':
        this.callbacks.onCallInvite?.(message, message.data as CallInviteData)
        break
      case 'call-accept':
        this.callbacks.onCallAccept?.(message)
        break
      case 'call-reject':
        this.callbacks.onCallReject?.(message, message.data?.reason)
        break
      case 'call-end':
        this.callbacks.onCallEnd?.(message)
        break
      case 'offer':
        this.callbacks.onWebRTCOffer?.(message, message.data as WebRTCOfferData)
        break
      case 'answer':
        this.callbacks.onWebRTCAnswer?.(message, message.data as WebRTCAnswerData)
        break
      case 'ice-candidate':
        this.callbacks.onICECandidate?.(message, message.data as ICECandidateData)
        break
      case 'participant-join':
        this.callbacks.onParticipantJoin?.(message, message.data as ParticipantStatusData)
        break
      case 'participant-leave':
        this.callbacks.onParticipantLeave?.(message, message.data as ParticipantStatusData)
        break
      case 'mute-status':
        this.callbacks.onMuteStatus?.(message, message.data as ParticipantStatusData)
        break
      case 'speaking-status':
        this.callbacks.onSpeakingStatus?.(message, message.data as ParticipantStatusData)
        break
      default:
    }
  }

  /**
   * 发送通话邀请
   */
  async sendCallInvite(callId: number, participants: number[], data: CallInviteData): Promise<void> {
    const message: SignalingMessage = {
      type: 'call-invite',
      callId,
      fromUserId: this.currentUserId,
      roomId: this.currentRoomId,
      timestamp: Date.now(),
      data: {
        ...data,
        participants
      }
    }

    // 向每个参与者发送邀请
    for (const participantId of participants) {
      await this.sendMessage({
        ...message,
        targetUserId: participantId
      })
    }
  }

  /**
   * 发送通话接受
   */
  async sendCallAccept(callId: number, targetUserId: number): Promise<void> {
    const message: SignalingMessage = {
      type: 'call-accept',
      callId,
      fromUserId: this.currentUserId,
      targetUserId,
      roomId: this.currentRoomId,
      timestamp: Date.now()
    }

    await this.sendMessage(message)
  }

  /**
   * 发送通话拒绝
   */
  async sendCallReject(callId: number, targetUserId: number, reason?: string): Promise<void> {
    const message: SignalingMessage = {
      type: 'call-reject',
      callId,
      fromUserId: this.currentUserId,
      targetUserId,
      roomId: this.currentRoomId,
      timestamp: Date.now(),
      data: { reason }
    }

    await this.sendMessage(message)
  }

  /**
   * 发送通话结束
   */
  async sendCallEnd(callId: number, participants?: number[]): Promise<void> {
    const message: SignalingMessage = {
      type: 'call-end',
      callId,
      fromUserId: this.currentUserId,
      roomId: this.currentRoomId,
      timestamp: Date.now()
    }

    if (participants && participants.length > 0) {
      // 向指定参与者发送
      for (const participantId of participants) {
        await this.sendMessage({
          ...message,
          targetUserId: participantId
        })
      }
    } else {
      // 广播给房间内所有人
      await this.sendMessage(message)
    }
  }

  /**
   * 发送WebRTC Offer
   */
  async sendWebRTCOffer(callId: number, targetUserId: number, sdp: RTCSessionDescription, iceServers?: RTCIceServer[]): Promise<void> {
    const message: SignalingMessage = {
      type: 'offer',
      callId,
      fromUserId: this.currentUserId,
      targetUserId,
      roomId: this.currentRoomId,
      timestamp: Date.now(),
      data: {
        sdp,
        iceServers
      } as WebRTCOfferData
    }

    await this.sendMessage(message)
  }

  /**
   * 发送WebRTC Answer
   */
  async sendWebRTCAnswer(callId: number, targetUserId: number, sdp: RTCSessionDescription): Promise<void> {
    const message: SignalingMessage = {
      type: 'answer',
      callId,
      fromUserId: this.currentUserId,
      targetUserId,
      roomId: this.currentRoomId,
      timestamp: Date.now(),
      data: {
        sdp
      } as WebRTCAnswerData
    }

    await this.sendMessage(message)
  }

  /**
   * 发送ICE候选
   */
  async sendICECandidate(callId: number, targetUserId: number, candidate: RTCIceCandidate): Promise<void> {
    const message: SignalingMessage = {
      type: 'ice-candidate',
      callId,
      fromUserId: this.currentUserId,
      targetUserId,
      roomId: this.currentRoomId,
      timestamp: Date.now(),
      data: {
        candidate
      } as ICECandidateData
    }

    await this.sendMessage(message)
  }

  /**
   * 发送参与者加入通知
   */
  async sendParticipantJoin(callId: number, nickname: string): Promise<void> {
    const message: SignalingMessage = {
      type: 'participant-join',
      callId,
      fromUserId: this.currentUserId,
      roomId: this.currentRoomId,
      timestamp: Date.now(),
      data: {
        userId: this.currentUserId,
        nickname,
        status: 'joined',
        timestamp: Date.now()
      } as ParticipantStatusData
    }

    await this.sendMessage(message)
  }

  /**
   * 发送参与者离开通知
   */
  async sendParticipantLeave(callId: number, nickname: string): Promise<void> {
    const message: SignalingMessage = {
      type: 'participant-leave',
      callId,
      fromUserId: this.currentUserId,
      roomId: this.currentRoomId,
      timestamp: Date.now(),
      data: {
        userId: this.currentUserId,
        nickname,
        status: 'left',
        timestamp: Date.now()
      } as ParticipantStatusData
    }

    await this.sendMessage(message)
  }

  /**
   * 发送静音状态通知
   */
  async sendMuteStatus(callId: number, nickname: string, isMuted: boolean): Promise<void> {
    const message: SignalingMessage = {
      type: 'mute-status',
      callId,
      fromUserId: this.currentUserId,
      roomId: this.currentRoomId,
      timestamp: Date.now(),
      data: {
        userId: this.currentUserId,
        nickname,
        status: isMuted ? 'muted' : 'unmuted',
        timestamp: Date.now()
      } as ParticipantStatusData
    }

    await this.sendMessage(message)
  }

  /**
   * 发送说话状态通知
   */
  async sendSpeakingStatus(callId: number, nickname: string, isSpeaking: boolean): Promise<void> {
    const message: SignalingMessage = {
      type: 'speaking-status',
      callId,
      fromUserId: this.currentUserId,
      roomId: this.currentRoomId,
      timestamp: Date.now(),
      data: {
        userId: this.currentUserId,
        nickname,
        status: isSpeaking ? 'speaking' : 'silent',
        timestamp: Date.now()
      } as ParticipantStatusData
    }

    await this.sendMessage(message)
  }

  /**
   * 发送信令消息
   */
  private async sendMessage(message: SignalingMessage): Promise<void> {
    if (!this.isConnected || !this.signalingClient) {
      // 如果未连接，将消息加入队列
      this.messageQueue.push(message)
      return
    }

    try {
      this.signalingClient.send({
        type: 'signaling',
        message
      })
    } catch (error) {
      console.error('发送信令消息失败:', error)
      this.callbacks.onSignalingError?.(error as Error)
    }
  }

  /**
   * 处理消息队列
   */
  private async processMessageQueue(): Promise<void> {
    while (this.messageQueue.length > 0 && this.isConnected) {
      const message = this.messageQueue.shift()
      if (message) {
        await this.sendMessage(message)
      }
    }
  }

  /**
   * 设置回调函数
   */
  setCallbacks(callbacks: Partial<SignalingCallbacks>): void {
    this.callbacks = { ...this.callbacks, ...callbacks }
  }

  /**
   * 获取连接状态
   */
  isSignalingConnected(): boolean {
    return this.isConnected && this.signalingClient !== null
  }

  /**
   * 重新连接信令服务器
   */
  async reconnect(): Promise<void> {
    if (this.signalingClient) {
      await this.signalingClient.connect()
    }
  }

  /**
   * 销毁信令协议处理器
   */
  destroy(): void {
    this.messageQueue = []
    this.isConnected = false
    this.signalingClient = null
    this.callbacks = {}
  }
} 