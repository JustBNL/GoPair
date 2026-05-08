import { ref, readonly, onBeforeUnmount } from 'vue'

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

  let countdownInterval: ReturnType<typeof setInterval> | null = null

  function startCountdown() {
    if (countdownInterval !== null) {
      clearInterval(countdownInterval)
      countdownInterval = null
    }

    if (options.passwordMode() !== 2) return

    countdownInterval = setInterval(() => {
      if (remainingSeconds.value <= 1) {
        if (!passwordHidden.value) {
          loadCurrentPassword()
        }
        if (countdownInterval !== null) {
          clearInterval(countdownInterval)
          countdownInterval = null
        }
      } else {
        remainingSeconds.value--
      }
    }, 1000)
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
        startCountdown()
      }
    } catch {
      // 失败时保留已有数据
    } finally {
      passwordLoading.value = false
    }
  }

  async function togglePasswordVisibility() {
    passwordHidden.value = !passwordHidden.value
    if (!passwordHidden.value) {
      await loadCurrentPassword()
    } else {
      if (countdownInterval !== null) {
        clearInterval(countdownInterval)
        countdownInterval = null
      }
    }
  }

  function resetPasswordState() {
    if (countdownInterval !== null) {
      clearInterval(countdownInterval)
      countdownInterval = null
    }
    passwordHidden.value = true
    currentPassword.value = ''
    remainingSeconds.value = 0
  }

  onBeforeUnmount(() => {
    if (countdownInterval !== null) {
      clearInterval(countdownInterval)
      countdownInterval = null
    }
  })

  return {
    passwordHidden: readonly(passwordHidden),
    currentPassword: readonly(currentPassword),
    remainingSeconds,
    passwordLoading: readonly(passwordLoading),
    resetPasswordState,
    togglePasswordVisibility,
    loadCurrentPassword,
  }
}
