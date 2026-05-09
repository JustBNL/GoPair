/**
 * P2P连接管理器 - 整合WebRTC核心功能
 */

import { WebRTCManager, type CallState, type CallParticipant, type WebRTCCallbacks } from './WebRTCManager'
import { AudioDeviceManager, type AudioDevice, type AudioTestResult, type AudioQuality, type AudioDeviceCallbacks } from './AudioDeviceManager'
import { SignalingProtocol, type SignalingCallbacks, type CallInviteData } from './SignalingProtocol'

export interface P2PCallState {
  // 通话基本信息
  callId: number | null
  roomId: number
  currentUserId: number
  
  // 通话状态
  isInCall: boolean
  isInitiator: boolean
  callStartTime: number | null
  callDuration: number
  
  // 参与者信息
  participants: Map<number, CallParticipant>
  localAudioEnabled: boolean
  
  // 音频设备信息
  selectedInputDevice: AudioDevice | null
  selectedOutputDevice: AudioDevice | null
  availableInputDevices: AudioDevice[]
  availableOutputDevices: AudioDevice[]
  
  // 音频质量
  audioQuality: AudioQuality | null
  currentVolumeLevel: number
}

export interface P2PConnectionCallbacks {
  // 通话状态回调
  onCallStateChanged: (state: P2PCallState) => void
  onCallInviteReceived: (callId: number, inviter: string, data: CallInviteData) => void
  onCallStarted: (callId: number) => void
  onCallEnded: (callId: number, reason?: string) => void
  onCallFailed: (callId: number, error: Error) => void
  
  // 参与者回调
  onParticipantJoined: (participant: CallParticipant) => void
  onParticipantLeft: (userId: number) => void
  onParticipantMuted: (userId: number, isMuted: boolean) => void
  onParticipantSpeaking: (userId: number, isSpeaking: boolean) => void
  
  // 音频设备回调
  onDeviceChanged: (devices: AudioDevice[]) => void
  onAudioQualityChanged: (quality: AudioQuality) => void
  onVolumeChanged: (level: number) => void
  
  // 错误回调
  onError: (error: Error) => void
}

export class P2PConnectionManager {
  private webrtcManager: WebRTCManager
  private audioDeviceManager: AudioDeviceManager
  private signalingProtocol: SignalingProtocol
  private callbacks: Partial<P2PConnectionCallbacks>
  
  private callState: P2PCallState
  private isInitialized: boolean = false

  constructor(callbacks?: Partial<P2PConnectionCallbacks>) {
    this.callbacks = callbacks || {}
    
    // 初始化各个管理器
    this.webrtcManager = new WebRTCManager({}, this.createWebRTCCallbacks())
    this.audioDeviceManager = new AudioDeviceManager(this.createAudioDeviceCallbacks())
    this.signalingProtocol = new SignalingProtocol(this.createSignalingCallbacks())
    
    // 初始化状态
    this.callState = {
      callId: null,
      roomId: 0,
      currentUserId: 0,
      isInCall: false,
      isInitiator: false,
      callStartTime: null,
      callDuration: 0,
      participants: new Map(),
      localAudioEnabled: true,
      selectedInputDevice: null,
      selectedOutputDevice: null,
      availableInputDevices: [],
      availableOutputDevices: [],
      audioQuality: null,
      currentVolumeLevel: 0
    }
  }

  /**
   * 初始化P2P连接管理器
   */
  async initialize(signalingClient: any, userId: number, roomId: number): Promise<void> {
    try {
      this.callState.currentUserId = userId
      this.callState.roomId = roomId
      
      // 初始化信令协议
      await this.signalingProtocol.initialize(signalingClient, userId, roomId)
      
      // 初始化音频设备管理器
      await this.audioDeviceManager.initialize()
      
      // 设置WebRTC管理器的回调函数
      this.webrtcManager.setSignalingSender((message: any) => {
        this.handleWebRTCSignaling(message)
      })
      
      this.webrtcManager.setCurrentUserIdGetter(() => userId)
      
      // 更新设备列表
      this.updateDeviceLists()
      
      this.isInitialized = true
      this.notifyStateChanged()
      
    } catch (error) {
      this.callbacks.onError?.(error as Error)
      throw error
    }
  }

  /**
   * 创建WebRTC回调
   */
  private createWebRTCCallbacks(): Partial<WebRTCCallbacks> {
    return {
      onCallStateChanged: (state: CallState) => {
        this.callState.callId = state.callId
        this.callState.isInCall = state.isInCall
        this.callState.isInitiator = state.isInitiator
        this.callState.callStartTime = state.callStartTime
        this.callState.callDuration = state.callDuration
        this.callState.participants = state.participants
        
        this.notifyStateChanged()
        
        if (state.isInCall && state.callId) {
          this.callbacks.onCallStarted?.(state.callId)
        } else if (!state.isInCall && this.callState.callId) {
          this.callbacks.onCallEnded?.(this.callState.callId)
        }
      },
      
      onParticipantJoined: (participant: CallParticipant) => {
        this.callbacks.onParticipantJoined?.(participant)
      },
      
      onParticipantLeft: (userId: number) => {
        this.callbacks.onParticipantLeft?.(userId)
      },
      
      onParticipantMuted: (userId: number, isMuted: boolean) => {
        this.callbacks.onParticipantMuted?.(userId, isMuted)
      },
      
      onParticipantSpeaking: (userId: number, isSpeaking: boolean) => {
        this.callbacks.onParticipantSpeaking?.(userId, isSpeaking)
      },
      
      onError: (error: Error) => {
        this.callbacks.onError?.(error)
      }
    }
  }

  /**
   * 创建音频设备回调
   */
  private createAudioDeviceCallbacks(): Partial<AudioDeviceCallbacks> {
    return {
      onDeviceChanged: (devices: AudioDevice[]) => {
        this.updateDeviceLists()
        this.callbacks.onDeviceChanged?.(devices)
      },
      
      onVolumeChanged: (level: number) => {
        this.callState.currentVolumeLevel = level
        this.callbacks.onVolumeChanged?.(level)
      },
      
      onQualityChanged: (quality: AudioQuality) => {
        this.callState.audioQuality = quality
        this.callbacks.onAudioQualityChanged?.(quality)
      },
      
      onError: (error: Error) => {
        this.callbacks.onError?.(error)
      }
    }
  }

  /**
   * 创建信令回调
   */
  private createSignalingCallbacks(): Partial<SignalingCallbacks> {
    return {
      onCallInvite: (message, data: CallInviteData) => {
        this.callbacks.onCallInviteReceived?.(
          message.callId,
          data.initiatorNickname,
          data
        )
      },
      
      onCallAccept: (message) => {
      },
      
      onCallReject: (message, reason) => {
      },
      
      onCallEnd: (message) => {
        this.endCall()
      },
      
      onWebRTCOffer: async (message, data) => {
        await this.webrtcManager.handleSignalingMessage({
          type: 'offer',
          fromUserId: message.fromUserId,
          sdp: data.sdp
        })
      },
      
      onWebRTCAnswer: async (message, data) => {
        await this.webrtcManager.handleSignalingMessage({
          type: 'answer',
          fromUserId: message.fromUserId,
          sdp: data.sdp
        })
      },
      
      onICECandidate: async (message, data) => {
        await this.webrtcManager.handleSignalingMessage({
          type: 'ice-candidate',
          fromUserId: message.fromUserId,
          candidate: data.candidate
        })
      },
      
      onSignalingError: (error: Error) => {
        this.callbacks.onError?.(error)
      }
    }
  }

  /**
   * 处理WebRTC信令消息
   */
  private async handleWebRTCSignaling(message: any): Promise<void> {
    const { type, targetUserId, sdp, candidate } = message
    
    switch (type) {
      case 'offer':
        await this.signalingProtocol.sendWebRTCOffer(
          this.callState.callId!,
          targetUserId,
          sdp
        )
        break
        
      case 'answer':
        await this.signalingProtocol.sendWebRTCAnswer(
          this.callState.callId!,
          targetUserId,
          sdp
        )
        break
        
      case 'ice-candidate':
        await this.signalingProtocol.sendICECandidate(
          this.callState.callId!,
          targetUserId,
          candidate
        )
        break
    }
  }

  /**
   * 发起通话
   */
  async initiateCall(participants: number[], options?: {
    callType?: 'ONE_TO_ONE' | 'MULTI_USER'
    autoRecord?: boolean
    allowInvite?: boolean
    muteOnJoin?: boolean
  }): Promise<number> {
    if (!this.isInitialized) {
      throw new Error('P2P连接管理器未初始化')
    }

    if (this.callState.isInCall) {
      throw new Error('已在通话中')
    }

    try {
      const callId = Date.now() // 简单的callId生成
      
      // 准备通话邀请数据
      const inviteData: CallInviteData = {
        initiatorNickname: `用户${this.callState.currentUserId}`, // 可以从用户信息获取
        callType: options?.callType || 'MULTI_USER',
        participants,
        settings: {
          autoRecord: options?.autoRecord || false,
          allowInvite: options?.allowInvite || true,
          muteOnJoin: options?.muteOnJoin || false
        }
      }
      
      // 发送通话邀请
      await this.signalingProtocol.sendCallInvite(callId, participants, inviteData)
      
      // 初始化WebRTC连接
      await this.webrtcManager.initiateCall(callId, participants)
      
      return callId
      
    } catch (error) {
      this.callbacks.onCallFailed?.(0, error as Error)
      throw error
    }
  }

  /**
   * 接受通话
   */
  async acceptCall(callId: number, fromUserId: number): Promise<void> {
    try {
      // 发送接受信号
      await this.signalingProtocol.sendCallAccept(callId, fromUserId)
      
      // 加入通话
      await this.webrtcManager.joinCall(callId)
      
    } catch (error) {
      this.callbacks.onCallFailed?.(callId, error as Error)
      throw error
    }
  }

  /**
   * 拒绝通话
   */
  async rejectCall(callId: number, fromUserId: number, reason?: string): Promise<void> {
    await this.signalingProtocol.sendCallReject(callId, fromUserId, reason)
  }

  /**
   * 结束通话
   */
  async endCall(): Promise<void> {
    if (!this.callState.isInCall || !this.callState.callId) {
      return
    }

    try {
      const callId = this.callState.callId
      
      // 发送结束通话信号
      await this.signalingProtocol.sendCallEnd(callId)
      
      // 离开WebRTC通话
      this.webrtcManager.leaveCall()
      
      this.callbacks.onCallEnded?.(callId)
      
    } catch (error) {
      this.callbacks.onError?.(error as Error)
    }
  }

  /**
   * 切换静音状态
   */
  toggleMute(): boolean {
    const isMuted = this.webrtcManager.toggleMute()
    this.callState.localAudioEnabled = !isMuted
    
    if (this.callState.callId) {
      this.signalingProtocol.sendMuteStatus(
        this.callState.callId,
        `用户${this.callState.currentUserId}`,
        isMuted
      )
    }
    
    this.notifyStateChanged()
    return isMuted
  }

  /**
   * 选择音频输入设备
   */
  async selectInputDevice(deviceId: string): Promise<void> {
    try {
      const stream = await this.audioDeviceManager.selectInputDevice(deviceId)
      
      // 如果在通话中，需要更新WebRTC流
      if (this.callState.isInCall) {
      }
      
      this.updateDeviceLists()
      this.notifyStateChanged()
      
    } catch (error) {
      this.callbacks.onError?.(error as Error)
    }
  }

  /**
   * 选择音频输出设备
   */
  async selectOutputDevice(deviceId: string): Promise<void> {
    try {
      await this.audioDeviceManager.selectOutputDevice(deviceId)
      this.updateDeviceLists()
      this.notifyStateChanged()
      
    } catch (error) {
      this.callbacks.onError?.(error as Error)
    }
  }

  /**
   * 测试音频设备
   */
  async testInputDevice(deviceId?: string): Promise<AudioTestResult> {
    return await this.audioDeviceManager.testInputDevice(deviceId)
  }

  async testOutputDevice(deviceId?: string): Promise<AudioTestResult> {
    return await this.audioDeviceManager.testOutputDevice(deviceId)
  }

  /**
   * 设置参与者音量
   */
  setParticipantVolume(userId: number, volume: number): void {
    this.webrtcManager.setVolume(userId, volume)
  }

  /**
   * 获取网络质量统计
   */
  async getNetworkStats(userId?: number): Promise<RTCStatsReport | null> {
    return await this.webrtcManager.getNetworkStats(userId)
  }

  /**
   * 更新设备列表
   */
  private updateDeviceLists(): void {
    this.callState.availableInputDevices = this.audioDeviceManager.getInputDevices()
    this.callState.availableOutputDevices = this.audioDeviceManager.getOutputDevices()
    
    // 更新当前选中的设备
    const currentInputDeviceId = this.audioDeviceManager.getCurrentInputStream()?.getAudioTracks()[0]?.getSettings().deviceId
    const currentOutputDeviceId = this.audioDeviceManager.getCurrentOutputDeviceId()
    
    this.callState.selectedInputDevice = this.callState.availableInputDevices.find(
      d => d.deviceId === currentInputDeviceId
    ) || null
    
    this.callState.selectedOutputDevice = this.callState.availableOutputDevices.find(
      d => d.deviceId === currentOutputDeviceId
    ) || null
  }

  /**
   * 通知状态变化
   */
  private notifyStateChanged(): void {
    this.callbacks.onCallStateChanged?.(this.callState)
  }

  /**
   * 获取当前状态
   */
  getCallState(): P2PCallState {
    return { ...this.callState }
  }

  /**
   * 是否在通话中
   */
  isInCall(): boolean {
    return this.callState.isInCall
  }

  /**
   * 获取参与者列表
   */
  getParticipants(): CallParticipant[] {
    return Array.from(this.callState.participants.values())
  }

  /**
   * 获取可用设备列表
   */
  getAvailableDevices(): { input: AudioDevice[], output: AudioDevice[] } {
    return {
      input: this.callState.availableInputDevices,
      output: this.callState.availableOutputDevices
    }
  }

  /**
   * 销毁P2P连接管理器
   */
  destroy(): void {
    this.webrtcManager.destroy()
    this.audioDeviceManager.destroy()
    this.signalingProtocol.destroy()
    
    this.isInitialized = false
  }
} 