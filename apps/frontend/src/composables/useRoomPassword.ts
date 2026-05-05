import { ref, readonly } from 'vue'

export interface UseRoomPasswordOptions {
  roomId: () => number
  passwordMode: () => number | undefined
  passwordVisible: () => number | undefined
  isOwner: () => boolean
  showPasswordArea: () => boolean
  loadPasswordApi: (roomId: number) => Promise<{
    currentPassword?: string
    remainingSeconds?: number
  } | null>
}

export function useRoomPassword(options: UseRoomPasswordOptions) {
  const passwordHidden = ref(true)
  const currentPassword = ref('')
  const remainingSeconds = ref(0)
  const passwordLoading = ref(false)

  let totpTimeout: ReturnType<typeof setTimeout> | null = null
  let totpInitialized = false
  let visibilityHandler: (() => void) | null = null
  let countdownIntervalId: ReturnType<typeof setInterval> | null = null

  function startCountdown() {
    if (countdownIntervalId) clearInterval(countdownIntervalId)
    if (!options.showPasswordArea() || options.passwordMode() !== 2) return
    const secs = remainingSeconds.value
    if (secs <= 0) return

    countdownIntervalId = setInterval(() => {
      if (remainingSeconds.value > 0) {
        remainingSeconds.value--
      }
      if (remainingSeconds.value <= 0) {
        if (countdownIntervalId) clearInterval(countdownIntervalId)
        countdownIntervalId = null
        loadCurrentPassword()
      }
    }, 1000)
  }

  function stopCountdown() {
    if (countdownIntervalId) {
      clearInterval(countdownIntervalId)
      countdownIntervalId = null
    }
  }

  async function loadCurrentPassword() {
    if (!options.showPasswordArea()) return
    if (passwordLoading.value) return
    passwordLoading.value = true
    try {
      const data = await options.loadPasswordApi(options.roomId())
      if (data) {
        currentPassword.value = data.currentPassword ?? ''
        remainingSeconds.value = data.remainingSeconds ?? 0
        if (options.passwordMode() === 2) {
          startCountdown()
        }
      }
    } catch {
      // 失败时保留已有数据
    } finally {
      passwordLoading.value = false
    }
  }

  function scheduleNextRefresh() {
    if (!options.showPasswordArea() || options.passwordMode() !== 2) return
    if (totpTimeout) clearTimeout(totpTimeout)
    totpTimeout = setTimeout(async () => {
      await loadCurrentPassword()
      scheduleNextRefresh()
    }, Math.max(remainingSeconds.value, 1) * 1000)
  }

  function startTotpTimer() {
    if (totpInitialized) return
    totpInitialized = true
    stopTotpTimer()
    stopCountdown()
    loadCurrentPassword()
    scheduleNextRefresh()
  }

  function stopTotpTimer() {
    if (totpTimeout) {
      clearTimeout(totpTimeout)
      totpTimeout = null
    }
    stopCountdown()
  }

  async function togglePasswordVisibility() {
    passwordHidden.value = !passwordHidden.value
    if (!passwordHidden.value) {
      await loadCurrentPassword()
    }
  }

  function registerVisibilityHandler() {
    if (visibilityHandler) return
    visibilityHandler = () => {
      if (document.visibilityState === 'visible' && !passwordHidden.value) {
        loadCurrentPassword()
      }
    }
    document.addEventListener('visibilitychange', visibilityHandler)
  }

  function unregisterVisibilityHandler() {
    if (visibilityHandler) {
      document.removeEventListener('visibilitychange', visibilityHandler)
      visibilityHandler = null
    }
  }

  function initPasswordState() {
    stopTotpTimer()
    totpInitialized = false
    currentPassword.value = ''
    remainingSeconds.value = 0
    passwordHidden.value = true

    const mode = options.passwordMode()
    if (!mode || mode === 0) return

    if (mode === 2) {
      startTotpTimer()
    } else if (mode === 1) {
      loadCurrentPassword()
    }

    registerVisibilityHandler()
  }

  function resetPasswordState() {
    stopTotpTimer()
    stopCountdown()
    unregisterVisibilityHandler()
    totpInitialized = false
    passwordHidden.value = true
    currentPassword.value = ''
    remainingSeconds.value = 0
  }

  return {
    passwordHidden: readonly(passwordHidden),
    currentPassword: readonly(currentPassword),
    remainingSeconds: readonly(remainingSeconds),
    passwordLoading: readonly(passwordLoading),
    initPasswordState,
    resetPasswordState,
    togglePasswordVisibility,
    loadCurrentPassword,
  }
}
