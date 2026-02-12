<script setup lang="ts">
import { ref } from 'vue'
import { useProviderStore } from '@/stores/provider'
import { useToastStore } from '@/stores/toast'
import type { ProviderInfo } from '@/types'

const providerStore = useProviderStore()
const toastStore = useToastStore()

const showKeyModal = ref(false)
const showConfigModal = ref(false)
const selectedProvider = ref<ProviderInfo | null>(null)
const apiKey = ref('')
const saving = ref(false)
const testing = ref(false)
const testResult = ref<{ success: boolean; message: string; responseTimeMs?: number } | null>(null)

// Config edit form
const configForm = ref({
  baseUrl: '',
  modelName: '',
  modelNameSmall: ''
})

function openKeyModal(provider: ProviderInfo) {
  selectedProvider.value = provider
  apiKey.value = ''
  showKeyModal.value = true
}

function openConfigModal(provider: ProviderInfo) {
  selectedProvider.value = provider
  configForm.value = {
    baseUrl: provider.baseUrl || '',
    modelName: provider.modelName || '',
    modelNameSmall: provider.modelNameSmall || ''
  }
  testResult.value = null
  showConfigModal.value = true
}

async function saveApiKey() {
  if (!selectedProvider.value || !apiKey.value) return

  saving.value = true
  try {
    await providerStore.setApiKey(selectedProvider.value.code, apiKey.value)
    showKeyModal.value = false
    toastStore.success(`${selectedProvider.value.name} API Key 已保存`)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    toastStore.error(err.response?.data?.message || '保存失败，请重试')
  } finally {
    saving.value = false
  }
}

async function saveConfig() {
  if (!selectedProvider.value) return

  saving.value = true
  try {
    await providerStore.updateProviderConfig(selectedProvider.value.code, {
      baseUrl: configForm.value.baseUrl || undefined,
      modelName: configForm.value.modelName || undefined,
      modelNameSmall: configForm.value.modelNameSmall || undefined
    })
    showConfigModal.value = false
    toastStore.success(`${selectedProvider.value.name} 配置已更新`)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    toastStore.error(err.response?.data?.message || '保存失败，请重试')
  } finally {
    saving.value = false
  }
}

async function testConnection() {
  if (!selectedProvider.value) return

  testing.value = true
  testResult.value = null
  try {
    const result = await providerStore.testProviderConnection(selectedProvider.value.code, {
      baseUrl: configForm.value.baseUrl || undefined,
      modelName: configForm.value.modelName || undefined
    })
    testResult.value = result
    if (result.success) {
      toastStore.success(`连接测试成功 (${result.responseTimeMs}ms)`)
    } else {
      toastStore.error(result.message)
    }
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    testResult.value = { success: false, message: err.response?.data?.message || '测试失败' }
    toastStore.error(testResult.value.message)
  } finally {
    testing.value = false
  }
}

async function deleteKey(providerCode: string) {
  if (confirm('确定要删除此 API Key 吗？')) {
    try {
      await providerStore.deleteApiKey(providerCode)
      toastStore.success('API Key 已删除')
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      toastStore.error(err.response?.data?.message || '删除失败，请重试')
    }
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
            <div class="flex items-center gap-2">
              <h3 class="font-semibold text-gray-900">{{ provider.name }}</h3>
              <span
                v-if="provider.hasApiKey"
                class="px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded-full"
              >
                已配置
              </span>
              <span
                v-else
                class="px-2 py-0.5 bg-gray-100 text-gray-500 text-xs rounded-full"
              >
                未配置
              </span>
            </div>
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
            @click="openConfigModal(provider)"
            class="px-3 py-2 text-sm bg-blue-100 hover:bg-blue-200 text-blue-700 rounded-lg transition-colors"
            title="编辑模型配置"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
            </svg>
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
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
            placeholder="请输入 API Key"
          />
        </div>

        <div class="flex justify-end gap-3">
          <button
            @click="showKeyModal = false"
            class="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
          >
            取消
          </button>
          <button
            @click="saveApiKey"
            :disabled="saving || !apiKey"
            class="px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg disabled:opacity-50 transition-colors"
          >
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Config Modal -->
    <div
      v-if="showConfigModal"
      class="fixed inset-0 bg-black/50 flex items-center justify-center z-50"
      @click.self="showConfigModal = false"
    >
      <div class="bg-white rounded-xl p-6 w-full max-w-md">
        <h3 class="text-lg font-semibold text-gray-900 mb-4">
          编辑配置 - {{ selectedProvider?.name }}
        </h3>

        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Base URL</label>
            <input
              v-model="configForm.baseUrl"
              type="text"
              class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
              placeholder="例如: https://api.example.com"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">模型名称</label>
            <input
              v-model="configForm.modelName"
              type="text"
              class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
              placeholder="例如: gpt-4"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">小模型名称 (可选)</label>
            <input
              v-model="configForm.modelNameSmall"
              type="text"
              class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
              placeholder="例如: gpt-3.5-turbo"
            />
          </div>
        </div>

        <!-- Test Result -->
        <div
          v-if="testResult"
          class="mt-4 p-3 rounded-lg text-sm"
          :class="testResult.success ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'"
        >
          <div class="flex items-center gap-2">
            <svg v-if="testResult.success" class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
            </svg>
            <svg v-else class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/>
            </svg>
            <span>{{ testResult.message }}</span>
            <span v-if="testResult.responseTimeMs" class="ml-auto text-xs opacity-75">
              {{ testResult.responseTimeMs }}ms
            </span>
          </div>
        </div>

        <p class="mt-4 text-xs text-gray-500">
          提示：配置修改后仅更新数据库，切换模型时才会同步到 settings.json
        </p>

        <div class="flex justify-end gap-3 mt-6">
          <button
            @click="showConfigModal = false"
            class="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
          >
            取消
          </button>
          <button
            @click="testConnection"
            :disabled="testing"
            class="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg disabled:opacity-50 transition-colors flex items-center gap-2"
          >
            <svg v-if="testing" class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"/>
            </svg>
            {{ testing ? '测试中...' : '测试连接' }}
          </button>
          <button
            @click="saveConfig"
            :disabled="saving"
            class="px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg disabled:opacity-50 transition-colors"
          >
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
