/**
 * 音频设备管理器 - 处理音频输入输出设备
 */

export interface AudioDevice {
  deviceId: string
  label: string
  kind: MediaDeviceKind
  groupId: string
}

export interface AudioTestResult {
  success: boolean
  message: string
  level?: number
  latency?: number
}

export interface AudioQuality {
  level: 'poor' | 'fair' | 'good' | 'excellent'
  details: {
    volume: number
    clarity: number
    stability: number
    backgroundNoise: number
  }
}

export interface AudioDeviceCallbacks {
  onDeviceChanged: (devices: AudioDevice[]) => void
  onVolumeChanged: (level: number) => void
  onQualityChanged: (quality: AudioQuality) => void
  onError: (error: Error) => void
}

export class AudioDeviceManager {
  private callbacks: Partial<AudioDeviceCallbacks>
  private currentInputStream: MediaStream | null = null
  private currentOutputDevice: string | null = null
  private volumeMonitorInterval: number | null = null
  private audioContext: AudioContext | null = null
  private analyser: AnalyserNode | null = null
  
  private inputDevices: AudioDevice[] = []
  private outputDevices: AudioDevice[] = []
  
  constructor(callbacks?: Partial<AudioDeviceCallbacks>) {
    this.callbacks = callbacks || {}
    this.setupDeviceChangeListener()
  }

  /**
   * 初始化设备管理器
   */
  async initialize(): Promise<void> {
    try {
      await this.requestPermissions()
      await this.refreshDeviceList()
    } catch (error) {
      this.callbacks.onError?.(error as Error)
      throw error
    }
  }

  /**
   * 请求设备权限
   */
  private async requestPermissions(): Promise<void> {
    try {
      // 请求麦克风权限
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      stream.getTracks().forEach(track => track.stop())
    } catch (error) {
      throw new Error(`无法获取麦克风权限: ${error}`)
    }
  }

  /**
   * 刷新设备列表
   */
  async refreshDeviceList(): Promise<void> {
    try {
      const devices = await navigator.mediaDevices.enumerateDevices()
      
      this.inputDevices = devices
        .filter(device => device.kind === 'audioinput')
        .map(device => ({
          deviceId: device.deviceId,
          label: device.label || `麦克风 ${device.deviceId.slice(0, 8)}`,
          kind: device.kind,
          groupId: device.groupId
        }))

      this.outputDevices = devices
        .filter(device => device.kind === 'audiooutput')
        .map(device => ({
          deviceId: device.deviceId,
          label: device.label || `扬声器 ${device.deviceId.slice(0, 8)}`,
          kind: device.kind,
          groupId: device.groupId
        }))

      const allDevices = [...this.inputDevices, ...this.outputDevices]
      this.callbacks.onDeviceChanged?.(allDevices)
    } catch (error) {
      this.callbacks.onError?.(error as Error)
    }
  }

  /**
   * 获取输入设备列表
   */
  getInputDevices(): AudioDevice[] {
    return [...this.inputDevices]
  }

  /**
   * 获取输出设备列表
   */
  getOutputDevices(): AudioDevice[] {
    return [...this.outputDevices]
  }

  /**
   * 选择输入设备
   */
  async selectInputDevice(deviceId: string): Promise<MediaStream> {
    try {
      // 停止当前流
      if (this.currentInputStream) {
        this.currentInputStream.getTracks().forEach(track => track.stop())
      }

      // 创建新的音频流
      const constraints: MediaStreamConstraints = {
        audio: {
          deviceId: deviceId ? { exact: deviceId } : undefined,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          sampleRate: 48000
        }
      }

      const stream = await navigator.mediaDevices.getUserMedia(constraints)
      this.currentInputStream = stream
      
      // 设置音频分析
      await this.setupAudioAnalysis(stream)
      
      return stream
    } catch (error) {
      throw new Error(`选择输入设备失败: ${error}`)
    }
  }

  /**
   * 选择输出设备
   */
  async selectOutputDevice(deviceId: string): Promise<void> {
    try {
      this.currentOutputDevice = deviceId
    } catch (error) {
      throw new Error(`选择输出设备失败: ${error}`)
    }
  }

  /**
   * 测试输入设备
   */
  async testInputDevice(deviceId?: string): Promise<AudioTestResult> {
    try {
      const testStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          deviceId: deviceId ? { exact: deviceId } : undefined,
          echoCancellation: false // 测试时关闭回声消除以获取真实音量
        }
      })

      return new Promise((resolve) => {
        const audioContext = new AudioContext()
        const analyser = audioContext.createAnalyser()
        const source = audioContext.createMediaStreamSource(testStream)
        
        source.connect(analyser)
        analyser.fftSize = 256
        
        const dataArray = new Uint8Array(analyser.frequencyBinCount)
        let maxVolume = 0
        let sampleCount = 0
        
        const checkVolume = () => {
          analyser.getByteFrequencyData(dataArray)
          const average = dataArray.reduce((sum, value) => sum + value, 0) / dataArray.length
          maxVolume = Math.max(maxVolume, average)
          sampleCount++
          
          if (sampleCount < 30) { // 测试3秒
            setTimeout(checkVolume, 100)
          } else {
            // 清理资源
            testStream.getTracks().forEach(track => track.stop())
            audioContext.close()
            
            // 返回测试结果
            if (maxVolume > 30) {
              resolve({
                success: true,
                message: '麦克风工作正常',
                level: maxVolume
              })
            } else if (maxVolume > 10) {
              resolve({
                success: true,
                message: '检测到音频输入，但音量较低',
                level: maxVolume
              })
            } else {
              resolve({
                success: false,
                message: '未检测到音频输入，请检查麦克风设置',
                level: maxVolume
              })
            }
          }
        }
        
        checkVolume()
      })
    } catch (error) {
      return {
        success: false,
        message: `麦克风测试失败: ${error}`
      }
    }
  }

  /**
   * 测试输出设备
   */
  async testOutputDevice(deviceId?: string): Promise<AudioTestResult> {
    try {
      const audioContext = new AudioContext()
      
      // 创建测试音频 (440Hz 正弦波)
      const oscillator = audioContext.createOscillator()
      const gainNode = audioContext.createGain()
      
      oscillator.connect(gainNode)
      gainNode.connect(audioContext.destination)
      
      oscillator.frequency.setValueAtTime(440, audioContext.currentTime)
      gainNode.gain.setValueAtTime(0.1, audioContext.currentTime)
      
      const startTime = audioContext.currentTime
      oscillator.start(startTime)
      
      return new Promise((resolve) => {
        setTimeout(() => {
          oscillator.stop()
          audioContext.close()
          
          resolve({
            success: true,
            message: '扬声器测试完成，如果听到提示音说明工作正常',
            latency: Date.now() - startTime * 1000
          })
        }, 1000)
      })
    } catch (error) {
      return {
        success: false,
        message: `扬声器测试失败: ${error}`
      }
    }
  }

  /**
   * 设置音频分析
   */
  private async setupAudioAnalysis(stream: MediaStream): Promise<void> {
    try {
      this.audioContext = new AudioContext()
      this.analyser = this.audioContext.createAnalyser()
      
      const source = this.audioContext.createMediaStreamSource(stream)
      source.connect(this.analyser)
      
      this.analyser.fftSize = 2048
      this.analyser.smoothingTimeConstant = 0.8
      
      this.startVolumeMonitoring()
    } catch (error) {
      console.error('音频分析设置失败:', error)
    }
  }

  /**
   * 开始音量监控
   */
  private startVolumeMonitoring(): void {
    if (!this.analyser) return

    const dataArray = new Uint8Array(this.analyser.frequencyBinCount)
    
    const monitorVolume = () => {
      if (!this.analyser || !this.currentInputStream) return

      this.analyser.getByteFrequencyData(dataArray)
      
      // 计算音量级别
      const average = dataArray.reduce((sum, value) => sum + value, 0) / dataArray.length
      
      this.callbacks.onVolumeChanged?.(average)
      
      // 计算音频质量
      const quality = this.calculateAudioQuality(dataArray)
      this.callbacks.onQualityChanged?.(quality)
    }

    this.volumeMonitorInterval = window.setInterval(monitorVolume, 100)
  }

  /**
   * 停止音量监控
   */
  private stopVolumeMonitoring(): void {
    if (this.volumeMonitorInterval) {
      clearInterval(this.volumeMonitorInterval)
      this.volumeMonitorInterval = null
    }
  }

  /**
   * 计算音频质量
   */
  private calculateAudioQuality(frequencyData: Uint8Array): AudioQuality {
    // 计算音量
    const volume = frequencyData.reduce((sum, value) => sum + value, 0) / frequencyData.length

    // 计算信号清晰度 (高频能量 vs 低频能量)
    const lowFreqSum = frequencyData.slice(0, frequencyData.length / 4).reduce((sum, value) => sum + value, 0)
    const highFreqSum = frequencyData.slice(frequencyData.length * 3/4).reduce((sum, value) => sum + value, 0)
    const clarity = highFreqSum / (lowFreqSum + 1) // 避免除零

    // 计算信号稳定性 (方差)
    const mean = volume
    const variance = frequencyData.reduce((sum, value) => sum + Math.pow(value - mean, 2), 0) / frequencyData.length
    const stability = Math.max(0, 100 - variance / 10)

    // 估算背景噪音
    const sortedData = [...frequencyData].sort((a, b) => a - b)
    const backgroundNoise = sortedData.slice(0, Math.floor(sortedData.length * 0.1)).reduce((sum, value) => sum + value, 0) / Math.floor(sortedData.length * 0.1)

    // 综合评分
    const overallScore = (volume * 0.3 + clarity * 0.25 + stability * 0.25 + (100 - backgroundNoise) * 0.2) / 100

    let level: AudioQuality['level']
    if (overallScore > 0.8) level = 'excellent'
    else if (overallScore > 0.6) level = 'good'
    else if (overallScore > 0.4) level = 'fair'
    else level = 'poor'

    return {
      level,
      details: {
        volume: Math.round(volume),
        clarity: Math.round(clarity * 10),
        stability: Math.round(stability),
        backgroundNoise: Math.round(backgroundNoise)
      }
    }
  }

  /**
   * 设置设备变化监听
   */
  private setupDeviceChangeListener(): void {
    if (navigator.mediaDevices) {
      navigator.mediaDevices.addEventListener('devicechange', () => {
        this.refreshDeviceList()
      })
    }
  }

  /**
   * 获取当前输入流
   */
  getCurrentInputStream(): MediaStream | null {
    return this.currentInputStream
  }

  /**
   * 获取当前输出设备ID
   */
  getCurrentOutputDeviceId(): string | null {
    return this.currentOutputDevice
  }

  /**
   * 静音/取消静音
   */
  toggleMute(): boolean {
    if (!this.currentInputStream) return false

    const audioTracks = this.currentInputStream.getAudioTracks()
    const isMuted = !audioTracks[0]?.enabled

    audioTracks.forEach(track => {
      track.enabled = isMuted
    })

    return !isMuted
  }

  /**
   * 设置输入音量增益
   */
  setInputGain(gain: number): void {
  }

  /**
   * 获取音频设备详细信息
   */
  async getDeviceCapabilities(deviceId: string): Promise<MediaTrackCapabilities | null> {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { deviceId: { exact: deviceId } }
      })
      
      const track = stream.getAudioTracks()[0]
      const capabilities = track.getCapabilities()
      
      stream.getTracks().forEach(track => track.stop())
      
      return capabilities
    } catch (error) {
      console.error('获取设备能力失败:', error)
      return null
    }
  }

  /**
   * 应用音频约束
   */
  async applyAudioConstraints(constraints: MediaTrackConstraints): Promise<void> {
    if (!this.currentInputStream) return

    const track = this.currentInputStream.getAudioTracks()[0]
    if (track) {
      try {
        await track.applyConstraints(constraints)
      } catch (error) {
        console.error('应用音频约束失败:', error)
      }
    }
  }

  /**
   * 获取当前音频约束
   */
  getCurrentConstraints(): MediaTrackConstraints | null {
    if (!this.currentInputStream) return null

    const track = this.currentInputStream.getAudioTracks()[0]
    return track ? track.getConstraints() : null
  }

  /**
   * 获取当前音频设置
   */
  getCurrentSettings(): MediaTrackSettings | null {
    if (!this.currentInputStream) return null

    const track = this.currentInputStream.getAudioTracks()[0]
    return track ? track.getSettings() : null
  }

  /**
   * 销毁音频设备管理器
   */
  destroy(): void {
    this.stopVolumeMonitoring()
    
    if (this.currentInputStream) {
      this.currentInputStream.getTracks().forEach(track => track.stop())
      this.currentInputStream = null
    }

    if (this.audioContext && this.audioContext.state !== 'closed') {
      this.audioContext.close()
      this.audioContext = null
    }
  }
} 