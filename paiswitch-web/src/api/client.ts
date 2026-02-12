import axios from 'axios'
import type { ApiResponse } from '@/types'
import { useAuthStore } from '@/stores/auth'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api

export async function apiGet<T>(url: string): Promise<T> {
  const response = await api.get<ApiResponse<T>>(url)
  return response.data.data
}

export async function apiPost<T>(url: string, data?: unknown): Promise<T> {
  const response = await api.post<ApiResponse<T>>(url, data)
  return response.data.data
}

export async function apiPut<T>(url: string, data?: unknown): Promise<T> {
  const response = await api.put<ApiResponse<T>>(url, data)
  return response.data.data
}

export async function apiDelete<T>(url: string): Promise<T> {
  const response = await api.delete<ApiResponse<T>>(url)
  return response.data.data
}
