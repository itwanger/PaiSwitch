<script setup lang="ts">
import { ref, computed } from 'vue'
import { useProviderStore } from '@/stores/provider'
import type { BackupInfo } from '@/types'

const providerStore = useProviderStore()

const backups = computed(() => [] as BackupInfo[]) // TODO: fetch backups

async function handleSwitch(providerCode: string) {
  const result = await providerStore.switchProvider(providerCode)
  if (result.success) {
    alert(`已切换到 ${result.currentProvider?.name}`)
  } else {
    alert(`切换失败: ${result.message}`)
  }
}
</script>

<template>
  <div>
    <h2 class="text-2xl font-bold text-gray-900 mb-6">配置管理</h2>

    <!-- Current Config -->
    <div class="bg-white rounded-xl p-6 shadow-sm mb-6">
      <h3 class="text-lg font-semibold text-gray-900 mb-4">当前配置</h3>

      <div v-if="providerStore.currentConfig" class="space-y-4">
        <div class="flex justify-between items-center py-2 border-b border-gray-100">
          <span class="text-gray-500">当前模型</span>
          <span class="font-medium text-gray-900">
            {{ providerStore.currentConfig.currentProvider.name }}
          </span>
        </div>

        <div class="flex justify-between items-center py-2 border-b border-gray-100">
          <span class="text-gray-500">模型代码</span>
          <span class="font-mono text-gray-900">
            {{ providerStore.currentConfig.currentProvider.code }}
          </span>
        </div>

        <div class="flex justify-between items-center py-2 border-b border-gray-100">
          <span class="text-gray-500">API 超时</span>
          <span class="text-gray-900">
            {{ providerStore.currentConfig.apiTimeout }}ms
          </span>
        </div>
      </div>
    </div>

    <!-- Switch Model -->
    <div class="bg-white rounded-xl p-6 shadow-sm">
      <h3 class="text-lg font-semibold text-gray-900 mb-4">切换模型</h3>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <button
          v-for="provider in providerStore.providers.filter(p => p.hasApiKey)"
          :key="provider.code"
          @click="handleSwitch(provider.code)"
          class="p-4 border rounded-lg text-left hover:border-primary-500 hover:bg-primary-50 transition-colors"
          :class="{
            'border-primary-500 bg-primary-50': providerStore.currentConfig?.currentProvider.code === provider.code
          }"
        >
          <div class="font-medium text-gray-900">{{ provider.name }}</div>
          <div class="text-sm text-gray-500">{{ provider.modelName }}</div>
        </button>
      </div>

      <div v-if="providerStore.providers.filter(p => p.hasApiKey).length === 0" class="text-center py-8 text-gray-500">
        暂无可用的模型，请先在「模型管理」中配置 API Key
      </div>
    </div>
  </div>
</template>
