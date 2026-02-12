import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'
import type { UserInfo } from '@/types'

const TOKEN_KEY = 'paiswitch_token'
const USER_KEY = 'paiswitch_user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  function loadUser() {
    const savedUser = localStorage.getItem(USER_KEY)
    if (savedUser) {
      user.value = JSON.parse(savedUser)
    }
  }

  async function login(username: string, password: string) {
    const response = await authApi.login(username, password)
    token.value = response.token
    user.value = response.user
    localStorage.setItem(TOKEN_KEY, response.token)
    localStorage.setItem(USER_KEY, JSON.stringify(response.user))
    return response
  }

  async function register(username: string, email: string, password: string) {
    const response = await authApi.register(username, email, password)
    token.value = response.token
    user.value = response.user
    localStorage.setItem(TOKEN_KEY, response.token)
    localStorage.setItem(USER_KEY, JSON.stringify(response.user))
    return response
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  loadUser()

  return {
    token,
    user,
    isLoggedIn,
    login,
    register,
    logout
  }
})
