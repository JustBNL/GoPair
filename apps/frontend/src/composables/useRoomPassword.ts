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

  async function loadCurrentPassword() {
    if (!options.showPasswordArea()) return
    if (passwordLoading.value) return
    passwordLoading.value = true
    try {
      const data = await options.loadPasswordApi(options.roomId())
      if (data) {
        currentPassword.value = data.currentPassword ?? ''
        remainingSeconds.value = data.remainingSeconds ?? 0
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
    }
  }

  function resetPasswordState() {
    passwordHidden.value = true
    currentPassword.value = ''
    remainingSeconds.value = 0
  }

  return {
    passwordHidden: readonly(passwordHidden),
    currentPassword: readonly(currentPassword),
    remainingSeconds: readonly(remainingSeconds),
    passwordLoading: readonly(passwordLoading),
    resetPasswordState,
    togglePasswordVisibility,
    loadCurrentPassword,
  }
}
