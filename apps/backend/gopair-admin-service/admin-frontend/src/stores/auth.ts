import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginResponse } from '@/types'

const TOKEN_KEY    = 'admin_token'
const ADMIN_ID_KEY = 'admin_id'
const ADMIN_NAME   = 'admin_username'
const ADMIN_NICK   = 'admin_nickname'

export const useAuthStore = defineStore('auth', () => {
  const token    = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const adminId  = ref<number>(Number(localStorage.getItem(ADMIN_ID_KEY) ?? '0'))
  const username = ref<string>(localStorage.getItem(ADMIN_NAME) ?? '')
  const nickname = ref<string>(localStorage.getItem(ADMIN_NICK) ?? '')

  const isLoggedIn = computed(() => !!token.value)

  function setAuth(data: LoginResponse) {
    token.value    = data.token
    adminId.value  = data.adminId
    username.value = data.username
    nickname.value = data.nickname
    localStorage.setItem(TOKEN_KEY,    data.token)
    localStorage.setItem(ADMIN_ID_KEY, String(data.adminId))
    localStorage.setItem(ADMIN_NAME,   data.username)
    localStorage.setItem(ADMIN_NICK,    data.nickname)
  }

  function logout() {
    token.value    = ''
    adminId.value  = 0
    username.value = ''
    nickname.value = ''
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(ADMIN_ID_KEY)
    localStorage.removeItem(ADMIN_NAME)
    localStorage.removeItem(ADMIN_NICK)
  }

  return { token, adminId, username, nickname, isLoggedIn, setAuth, logout }
})