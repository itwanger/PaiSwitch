import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface ToastMessage {
  id: number
  type: 'success' | 'error' | 'warning' | 'info'
  message: string
  duration: number
}

let toastId = 0

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<ToastMessage[]>([])

  function addToast(type: ToastMessage['type'], message: string, duration = 3000) {
    const id = ++toastId
    toasts.value.push({ id, type, message, duration })

    if (duration > 0) {
      setTimeout(() => {
        removeToast(id)
      }, duration)
    }

    return id
  }

  function removeToast(id: number) {
    const index = toasts.value.findIndex(t => t.id === id)
    if (index > -1) {
      toasts.value.splice(index, 1)
    }
  }

  function success(message: string, duration?: number) {
    return addToast('success', message, duration)
  }

  function error(message: string, duration?: number) {
    return addToast('error', message, duration)
  }

  function warning(message: string, duration?: number) {
    return addToast('warning', message, duration)
  }

  function info(message: string, duration?: number) {
    return addToast('info', message, duration)
  }

  return {
    toasts,
    addToast,
    removeToast,
    success,
    error,
    warning,
    info
  }
})
