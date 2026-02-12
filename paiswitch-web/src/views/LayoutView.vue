<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useProviderStore } from '@/stores/provider'
import { onMounted } from 'vue'
import Toast from '@/components/Toast.vue'

const router = useRouter()
const authStore = useAuthStore()
const providerStore = useProviderStore()

const navItems = [
  { path: '/', name: 'Dashboard', label: '仪表盘', icon: '📊' },
  { path: '/providers', name: 'Providers', label: '模型管理', icon: '🔌' },
  { path: '/config', name: 'Config', label: '配置', icon: '⚙️' },
  { path: '/ai-chat', name: 'AIChat', label: 'AI 助手', icon: '🤖' }
]

onMounted(() => {
  providerStore.init()
})

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen flex">
    <!-- Toast Notifications -->
    <Toast />

    <!-- Sidebar -->
    <aside class="w-64 bg-white border-r border-gray-200">
      <div class="p-4 border-b border-gray-200">
        <h1 class="text-xl font-bold text-primary-600">PaiSwitch</h1>
      </div>

      <nav class="p-4 space-y-1">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="flex items-center gap-3 px-4 py-2 rounded-lg text-gray-700 hover:bg-gray-100 transition-colors"
          active-class="bg-primary-50 text-primary-600"
        >
          <span>{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>

    <!-- Main Content -->
    <div class="flex-1 flex flex-col">
      <!-- Header -->
      <header class="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-6">
        <div class="text-gray-600">
          当前模型:
          <span v-if="providerStore.currentConfig" class="font-medium text-gray-900">
            {{ providerStore.currentConfig.currentProvider.name }}
          </span>
        </div>

        <div class="flex items-center gap-4">
          <span class="text-gray-600">{{ authStore.user?.username }}</span>
          <button
            @click="handleLogout"
            class="text-gray-500 hover:text-gray-700 text-sm"
          >
            退出
          </button>
        </div>
      </header>

      <!-- Page Content -->
      <main class="flex-1 p-6 overflow-auto">
        <router-view />
      </main>
    </div>
  </div>
</template>
