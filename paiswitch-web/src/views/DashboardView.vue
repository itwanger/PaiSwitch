<script setup lang="ts">
import { computed } from 'vue'
import { useProviderStore } from '@/stores/provider'

const providerStore = useProviderStore()

const stats = computed(() => {
  const total = providerStore.providers.length
  const withKey = providerStore.providers.filter(p => p.hasApiKey).length
  return { total, withKey }
})

const currentProvider = computed(() => providerStore.currentConfig?.currentProvider)
</script>

<template>
  <div>
    <h2 class="text-2xl font-bold text-gray-900 mb-6">仪表盘</h2>

    <!-- Stats -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
      <div class="bg-white rounded-xl p-6 shadow-sm">
        <div class="text-gray-500 text-sm">当前模型</div>
        <div class="text-2xl font-bold text-gray-900 mt-1">
          {{ currentProvider?.name || '-' }}
        </div>
      </div>

      <div class="bg-white rounded-xl p-6 shadow-sm">
        <div class="text-gray-500 text-sm">可用模型</div>
        <div class="text-2xl font-bold text-gray-900 mt-1">{{ stats.total }}</div>
      </div>

      <div class="bg-white rounded-xl p-6 shadow-sm">
        <div class="text-gray-500 text-sm">已配置 API Key</div>
        <div class="text-2xl font-bold text-gray-900 mt-1">{{ stats.withKey }}</div>
      </div>
    </div>

    <!-- Quick Switch -->
    <div class="bg-white rounded-xl p-6 shadow-sm mb-8">
      <h3 class="text-lg font-semibold text-gray-900 mb-4">快速切换</h3>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <button
          v-for="provider in providerStore.providers.filter(p => p.hasApiKey)"
          :key="provider.code"
          @click="providerStore.switchProvider(provider.code)"
          class="p-4 border rounded-lg hover:border-primary-500 hover:bg-primary-50 transition-colors"
          :class="{ 'border-primary-500 bg-primary-50': currentProvider?.code === provider.code }"
        >
          <div class="font-medium text-gray-900">{{ provider.name }}</div>
          <div class="text-sm text-gray-500">{{ provider.modelName }}</div>
        </button>
      </div>
    </div>

    <!-- Providers List -->
    <div class="bg-white rounded-xl shadow-sm">
      <div class="p-4 border-b border-gray-100">
        <h3 class="text-lg font-semibold text-gray-900">模型列表</h3>
      </div>
      <div class="divide-y divide-gray-100">
        <div
          v-for="provider in providerStore.providers"
          :key="provider.id"
          class="p-4 flex items-center justify-between"
        >
          <div>
            <div class="font-medium text-gray-900">{{ provider.name }}</div>
            <div class="text-sm text-gray-500">{{ provider.modelName }}</div>
          </div>
          <div class="flex items-center gap-2">
            <span
              v-if="provider.hasApiKey"
              class="px-2 py-1 bg-green-100 text-green-700 text-xs rounded-full"
            >
              已配置
            </span>
            <span
              v-else
              class="px-2 py-1 bg-gray-100 text-gray-600 text-xs rounded-full"
            >
              未配置
            </span>
            <span
              v-if="currentProvider?.code === provider.code"
              class="px-2 py-1 bg-primary-100 text-primary-700 text-xs rounded-full"
            >
              当前
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
