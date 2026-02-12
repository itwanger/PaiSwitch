<script setup lang="ts">
import { ref } from 'vue'
import { useProviderStore } from '@/stores/provider'
import type { ProviderInfo } from '@/types'

const providerStore = useProviderStore()

const showKeyModal = ref(false)
const selectedProvider = ref<ProviderInfo | null>(null)
const apiKey = ref('')
const saving = ref(false)

function openKeyModal(provider: ProviderInfo) {
  selectedProvider.value = provider
  apiKey.value = ''
  showKeyModal.value = true
}

async function saveApiKey() {
  if (!selectedProvider.value || !apiKey.value) return

  saving.value = true
  try {
    await providerStore.setApiKey(selectedProvider.value.code, apiKey.value)
    showKeyModal.value = false
  } finally {
    saving.value = false
  }
}

async function deleteKey(providerCode: string) {
  if (confirm('确定要删除此 API Key 吗？')) {
    await providerStore.deleteApiKey(providerCode)
  }
}
</script>

<template>
  <div>
    <h2 class="text-2xl font-bold text-gray-900 mb-6">模型管理</h2>

    <!-- Providers Grid -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <div
        v-for="provider in providerStore.providers"
        :key="provider.id"
        class="bg-white rounded-xl p-6 shadow-sm"
      >
        <div class="flex items-start justify-between mb-4">
          <div>
            <h3 class="font-semibold text-gray-900">{{ provider.name }}</h3>
            <p class="text-sm text-gray-500">{{ provider.description }}</p>
          </div>
          <span
            v-if="provider.isBuiltin"
            class="px-2 py-1 bg-blue-100 text-blue-700 text-xs rounded-full"
          >
            内置
          </span>
        </div>

        <div class="space-y-2 text-sm mb-4">
          <div class="flex justify-between">
            <span class="text-gray-500">模型</span>
            <span class="text-gray-900 font-mono">{{ provider.modelName }}</span>
          </div>
          <div v-if="provider.modelNameSmall" class="flex justify-between">
            <span class="text-gray-500">小模型</span>
            <span class="text-gray-900 font-mono">{{ provider.modelNameSmall }}</span>
          </div>
        </div>

        <div class="flex gap-2">
          <button
            v-if="provider.hasApiKey"
            @click="openKeyModal(provider)"
            class="flex-1 px-3 py-2 text-sm bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
          >
            更新 API Key
          </button>
          <button
            v-else
            @click="openKeyModal(provider)"
            class="flex-1 px-3 py-2 text-sm bg-primary-600 hover:bg-primary-700 text-white rounded-lg transition-colors"
          >
            配置 API Key
          </button>
          <button
            v-if="provider.hasApiKey"
            @click="deleteKey(provider.code)"
            class="px-3 py-2 text-sm bg-red-100 hover:bg-red-200 text-red-700 rounded-lg transition-colors"
          >
            删除
          </button>
        </div>
      </div>
    </div>

    <!-- API Key Modal -->
    <div
      v-if="showKeyModal"
      class="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
      @click.self="showKeyModal = false"
    >
      <div class="bg-white rounded-xl p-6 w-full max-w-md">
        <h3 class="text-lg font-semibold text-gray-900 mb-4">
          配置 API Key - {{ selectedProvider?.name }}
        </h3>

        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-1">API Key</label>
          <input
            v-model="apiKey"
            type="password"
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
            placeholder="请输入 API Key"
          />
        </div>

        <div class="flex justify-end gap-3">
          <button
            @click="showKeyModal = false"
            class="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg"
          >
            取消
          </button>
          <button
            @click="saveApiKey"
            :disabled="saving || !apiKey"
            class="px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg disabled:opacity-50"
          >
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
