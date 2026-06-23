<script setup lang="ts">
import { ref, computed } from 'vue'
import { useProviderStore } from '@/stores/provider'
import { useToastStore } from '@/stores/toast'
import type { ProviderInfo, ProviderConfigUpdateRequest, CustomProviderCreateRequest } from '@/types'

const providerStore = useProviderStore()
const toastStore = useToastStore()

const showConfigModal = ref(false)
const showCreateModal = ref(false)
const selectedProvider = ref<ProviderInfo | null>(null)
const configApiKey = ref('')
const showApiKey = ref(false)
const loadingApiKey = ref(false)
const saving = ref(false)
const creatingCustom = ref(false)
const testing = ref(false)
const testResult = ref<{ success: boolean; message: string; responseTimeMs?: number } | null>(null)

const currentProvider = computed(() => providerStore.currentConfig?.currentProvider)

const sortedProviders = computed(() => {
  const providers = [...providerStore.providers]

  // Sort by priority:
  // 1. Current selected provider first
  // 2. Has API Key comes before no API Key
  // 3. Default order is what's stored
  providers.sort((a, b) => {
    const aIsCurrent = a.code === currentProvider.value?.code
    const bIsCurrent = b.code === currentProvider.value?.code
    if (aIsCurrent && !bIsCurrent) return -1
    if (!aIsCurrent && bIsCurrent) return 1

    if (a.hasApiKey && !b.hasApiKey) return -1
    if (!a.hasApiKey && b.hasApiKey) return 1

    return 0
  })

  return providers
})

// Config edit form
const configForm = ref({
  baseUrl: '',
  modelName: '',
  modelNameSmall: ''
})

const customForm = ref({
  name: '',
  code: '',
  description: '',
  baseUrl: '',
  modelName: '',
  modelNameSmall: ''
})
const createApiKey = ref('')
const showCreateApiKey = ref(false)

function normalizeInput(value?: string | null): string {
  return (value || '').trim()
}

function toProviderCode(value: string): string {
  const normalized = value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')

  if (!normalized) {
    return `custom-${Date.now()}`
  }
  return normalized.slice(0, 50)
}

function getHostname(baseUrl?: string): string {
  if (!baseUrl) return ''
  try {
    const url = new URL(baseUrl)
    return url.hostname
  } catch {
    return ''
  }
}

function isOfficialClaude(provider: ProviderInfo): boolean {
  return provider.code === 'claude' && providerStore.activeTool === 'CLAUDE_CODE'
}

function openCreateModal() {
  customForm.value = {
    name: '',
    code: '',
    description: '',
    baseUrl: '',
    modelName: '',
    modelNameSmall: ''
  }
  createApiKey.value = ''
  showCreateApiKey.value = false
  showCreateModal.value = true
}

async function createCustomProvider() {
  const name = normalizeInput(customForm.value.name)
  const baseUrl = normalizeInput(customForm.value.baseUrl)
  const modelName = normalizeInput(customForm.value.modelName)
  const code = toProviderCode(normalizeInput(customForm.value.code) || name)

  if (!name) {
    toastStore.error('请填写名称')
    return
  }

  if (!baseUrl) {
    toastStore.error('请填写 Base URL')
    return
  }

  if (providerStore.providers.some((provider) => provider.code === code)) {
    toastStore.error(`模型编码 ${code} 已存在，请修改后重试`)
    return
  }

  creatingCustom.value = true
  try {
    const payload: CustomProviderCreateRequest = {
      code,
      name,
      description: normalizeInput(customForm.value.description) || undefined,
      baseUrl,
      modelName,
      modelNameSmall: normalizeInput(customForm.value.modelNameSmall) || undefined
    }

    await providerStore.createCustomProvider(payload, normalizeInput(createApiKey.value) || undefined)
    showCreateModal.value = false
    toastStore.success(`已新增自定义模型 ${name}`)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    toastStore.error(err.response?.data?.message || '新增失败，请重试')
  } finally {
    creatingCustom.value = false
  }
}

async function openConfigModal(provider: ProviderInfo) {
  const baseUrl = normalizeInput(provider.baseUrl)
  const modelName = normalizeInput(provider.modelName)
  const modelNameSmall = normalizeInput(provider.modelNameSmall)

  selectedProvider.value = provider
  configForm.value = {
    baseUrl,
    modelName,
    modelNameSmall
  }
  configApiKey.value = ''
  showApiKey.value = false
  testResult.value = null
  showConfigModal.value = true

  loadingApiKey.value = true
  try {
    const keyInfo = await providerStore.getApiKeyPlain(provider.code)
    if (selectedProvider.value?.code !== provider.code) {
      return
    }
    const apiKeyValue = normalizeInput(keyInfo?.apiKey)
    configApiKey.value = apiKeyValue
  } catch {
    configApiKey.value = ''
  } finally {
    if (selectedProvider.value?.code === provider.code) {
      loadingApiKey.value = false
    }
  }
}

async function saveConfig() {
  if (!selectedProvider.value) return

  saving.value = true
  try {
    const nextBaseUrl = normalizeInput(configForm.value.baseUrl)
    const nextModelName = normalizeInput(configForm.value.modelName)
    const nextModelNameSmall = normalizeInput(configForm.value.modelNameSmall)
    const nextApiKey = normalizeInput(configApiKey.value)

    const configPayload: ProviderConfigUpdateRequest = {
      baseUrl: nextBaseUrl,
      modelName: nextModelName,
      modelNameSmall: nextModelNameSmall
    }

    await providerStore.updateProviderConfig(selectedProvider.value.code, configPayload)
    if (nextApiKey) {
      await providerStore.setApiKey(selectedProvider.value.code, nextApiKey)
    }
    showConfigModal.value = false
    if (nextApiKey) {
      toastStore.success(`${selectedProvider.value.name} 配置和 API Key 已更新`)
    } else {
      toastStore.success(`${selectedProvider.value.name} 配置已更新`)
    }
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
      modelName: configForm.value.modelName || undefined,
      apiKey: configApiKey.value.trim() || undefined
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

async function deleteProvider(provider: ProviderInfo) {
  if (confirm(`确定要删除自定义模型「${provider.name}」吗？此操作不可恢复。`)) {
    try {
      await providerStore.deleteProvider(provider.code)
      toastStore.success(`已删除 ${provider.name}`)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      toastStore.error(err.response?.data?.message || '删除失败，请重试')
    }
  }
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-10">
      <div>
        <h2 class="text-3xl font-bold tracking-tight text-gray-900">模型管理</h2>
        <p class="text-gray-500 mt-2 font-light">管理你的 AI 模型提供商和 API 密钥配置</p>
      </div>
      <button
        @click="openCreateModal"
        class="px-6 py-3 bg-gray-900 hover:bg-gray-800 text-white rounded-xl transition-all duration-200 hover:shadow-lg hover:-translate-y-0.5"
      >
        <span class="flex items-center gap-2">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
          </svg>
          新增自定义模型
        </span>
      </button>
    </div>

    <!-- Providers Grid -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <div
        v-for="provider in sortedProviders"
        :key="provider.id"
        class="bg-white rounded-2xl p-6 border border-gray-100 shadow-sm hover:shadow-xl hover:-translate-y-1 transition-all duration-300 group"
      >
        <div class="flex items-start justify-between mb-5">
          <div class="min-w-0">
            <div class="flex items-center gap-2 mb-1.5">
              <h3 class="font-semibold text-lg text-gray-900 truncate">{{ provider.name }}</h3>
            </div>
            <p v-if="provider.description" class="text-sm text-gray-500 line-clamp-2">{{ provider.description }}</p>
            <p v-else class="text-sm text-gray-400 italic">无描述</p>
          </div>
        </div>

        <div class="flex flex-wrap gap-2 mb-5">
          <span
            v-if="provider.hasApiKey"
            class="px-3 py-1 bg-emerald-50 text-emerald-700 text-xs font-medium rounded-full"
          >
            已配置
          </span>
          <span
            v-else-if="isOfficialClaude(provider)"
            class="px-3 py-1 bg-blue-50 text-blue-700 text-xs font-medium rounded-full"
          >
            官方登录
          </span>
          <span
            v-else
            class="px-3 py-1 bg-gray-100 text-gray-500 text-xs font-medium rounded-full"
          >
            未配置
          </span>
          <span
            v-if="provider.isBuiltin"
            class="px-3 py-1 bg-blue-50 text-blue-700 text-xs font-medium rounded-full"
          >
            内置
          </span>
          <span
            v-else
            class="px-3 py-1 bg-amber-50 text-amber-700 text-xs font-medium rounded-full"
          >
            自定义
          </span>
        </div>

        <div class="space-y-3 text-sm mb-6">
          <div class="flex justify-between items-center">
            <span class="text-gray-500">模型</span>
            <span class="text-gray-900 font-mono text-xs bg-gray-50 px-2 py-1 rounded-md max-w-[180px] truncate">{{ provider.modelName }}</span>
          </div>
          <div v-if="provider.modelNameSmall" class="flex justify-between items-center">
            <span class="text-gray-500">小模型</span>
            <span class="text-gray-900 font-mono text-xs bg-gray-50 px-2 py-1 rounded-md max-w-[180px] truncate">{{ provider.modelNameSmall }}</span>
          </div>
          <div v-if="getHostname(provider.baseUrl)" class="flex justify-between items-center">
            <span class="text-gray-500">端点</span>
            <span class="text-gray-900 text-xs bg-gray-50 px-2 py-1 rounded-md max-w-[180px] truncate">{{ getHostname(provider.baseUrl) }}</span>
          </div>
        </div>

        <div class="flex gap-3">
          <button
            @click="openConfigModal(provider)"
            class="flex-1 px-4 py-2.5 text-sm bg-gray-900 hover:bg-gray-800 text-white rounded-xl transition-all duration-200 flex items-center justify-center group-hover:shadow-md"
            title="编辑模型配置"
          >
            <svg class="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
            </svg>
            编辑配置
          </button>
          <button
            v-if="provider.hasApiKey"
            @click="deleteKey(provider.code)"
            class="px-4 py-2.5 text-sm border border-red-200 hover:bg-red-50 text-red-600 rounded-xl transition-colors"
          >
            删除密钥
          </button>
          <button
            v-if="!provider.isBuiltin"
            @click="deleteProvider(provider)"
            class="px-4 py-2.5 text-sm border border-red-300 hover:bg-red-600 hover:text-white text-red-700 rounded-xl transition-colors"
            title="删除此自定义模型"
          >
            删除模型
          </button>
        </div>
      </div>
    </div>

    <!-- Create Custom Provider Modal -->
    <div
      v-if="showCreateModal"
      class="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-200"
      @click.self="showCreateModal = false"
    >
      <div class="bg-white rounded-2xl p-8 w-full max-w-lg shadow-2xl animate-in zoom-in-95 duration-200">
        <h3 class="text-xl font-semibold text-gray-900 mb-6">新增自定义模型</h3>

        <div class="space-y-5">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">名称</label>
              <input
                v-model="customForm.name"
                type="text"
                class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-gray-900 focus:border-gray-900 transition-all outline-none"
                placeholder="例如: Qwen 3.5 Plus"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">编码 (可选)</label>
              <input
                v-model="customForm.code"
                type="text"
                class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-gray-900 focus:border-gray-900 transition-all outline-none"
                placeholder="留空自动生成"
              />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">API Key (可选)</label>
            <div class="flex gap-2">
              <input
                v-model="createApiKey"
                :type="showCreateApiKey ? 'text' : 'password'"
                class="flex-1 px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-gray-900 focus:border-gray-900 transition-all outline-none"
                placeholder="可选，创建后自动保存"
              />
              <button
                type="button"
                @click="showCreateApiKey = !showCreateApiKey"
                :disabled="!createApiKey"
                class="px-3 py-3 text-sm bg-gray-100 hover:bg-gray-200 rounded-xl disabled:opacity-50 transition-colors whitespace-nowrap flex-shrink-0"
              >
                {{ showCreateApiKey ? '隐藏' : '显示' }}
              </button>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">Base URL</label>
            <input
              v-model="customForm.baseUrl"
              type="text"
              class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-gray-900 focus:border-gray-900 transition-all outline-none"
              placeholder="例如: https://dashscope.aliyuncs.com/compatible-mode/v1"
            />
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">模型名称 (可选)</label>
              <input
                v-model="customForm.modelName"
                type="text"
                class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-gray-900 focus:border-gray-900 transition-all outline-none"
                placeholder="例如: qwen-plus，不填则留空"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">小模型名称 (可选)</label>
              <input
                v-model="customForm.modelNameSmall"
                type="text"
                class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-gray-900 focus:border-gray-900 transition-all outline-none"
                placeholder="例如: qwen-turbo"
              />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">说明 (可选)</label>
            <input
              v-model="customForm.description"
              type="text"
              class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-gray-900 focus:border-gray-900 transition-all outline-none"
              placeholder="例如: 通义千问兼容端点"
            />
          </div>
        </div>

        <p class="mt-4 text-xs text-gray-500">
          提示：编码仅支持小写字母、数字和中划线；留空会根据名称自动生成
        </p>

        <div class="flex justify-end gap-3 mt-8">
          <button
            @click="showCreateModal = false"
            class="px-5 py-2.5 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors"
          >
            取消
          </button>
          <button
            @click="createCustomProvider"
            :disabled="creatingCustom"
            class="px-5 py-2.5 bg-gray-900 hover:bg-gray-800 text-white rounded-xl disabled:opacity-50 transition-all"
          >
            {{ creatingCustom ? '创建中...' : '确认新增' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Config Modal -->
    <div
      v-if="showConfigModal"
      class="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-200"
      @click.self="showConfigModal = false"
    >
      <div class="bg-white rounded-2xl p-8 w-full max-w-lg shadow-2xl animate-in zoom-in-95 duration-200">
        <h3 class="text-xl font-semibold text-gray-900 mb-6">
          编辑配置 - {{ selectedProvider?.name }}
        </h3>

        <div class="space-y-5">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">Base URL</label>
            <input
              v-model="configForm.baseUrl"
              type="text"
              class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-gray-900 focus:border-gray-900 transition-all outline-none"
              placeholder="例如: https://api.example.com"
            />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">模型名称 (可选)</label>
              <input
                v-model="configForm.modelName"
                type="text"
                class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-gray-900 focus:border-gray-900 transition-all outline-none"
                placeholder="例如: gpt-4；Kimi Coding 可留空"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">小模型名称 (可选)</label>
              <input
                v-model="configForm.modelNameSmall"
                type="text"
                class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-gray-900 focus:border-gray-900 transition-all outline-none"
                placeholder="例如: gpt-3.5-turbo"
              />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">API Key (可选)</label>
            <div class="flex gap-2">
              <input
                v-model="configApiKey"
                :type="showApiKey ? 'text' : 'password'"
                class="flex-1 px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-gray-900 focus:border-gray-900 transition-all outline-none"
                :placeholder="loadingApiKey ? '正在加载 API Key...' : '留空则不更新 API Key'"
                :disabled="loadingApiKey"
              />
              <button
                type="button"
                @click="showApiKey = !showApiKey"
                :disabled="loadingApiKey || !configApiKey"
                class="px-3 py-3 text-sm bg-gray-100 hover:bg-gray-200 rounded-xl disabled:opacity-50 transition-colors whitespace-nowrap flex-shrink-0"
              >
                {{ showApiKey ? '隐藏' : '显示' }}
              </button>
            </div>
            <p v-if="!showApiKey && configApiKey" class="mt-2 text-xs text-gray-500">
              当前已隐藏，请点击显示查看内容
            </p>
          </div>
        </div>

        <!-- Test Result -->
        <div
          v-if="testResult"
          class="mt-6 p-4 rounded-xl text-sm animate-in fade-in slide-in-from-bottom-2 duration-200"
          :class="testResult.success ? 'bg-emerald-50 text-emerald-700 border border-emerald-100' : 'bg-red-50 text-red-700 border border-red-100'"
        >
          <div class="flex items-center gap-2">
            <svg v-if="testResult.success" class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
            </svg>
            <svg v-else class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/>
            </svg>
            <span>{{ testResult.message }}</span>
            <span v-if="testResult.responseTimeMs" class="ml-auto text-xs opacity-75 font-mono">
              {{ testResult.responseTimeMs }}ms
            </span>
          </div>
        </div>

        <p class="mt-4 text-xs text-gray-500">
          提示：API Key 留空表示不修改；模型名称可留空（如 Kimi Coding）；配置修改后仅更新数据库，切换模型时才会同步到 settings.json
        </p>

        <div class="flex justify-end gap-3 mt-8">
          <button
            @click="showConfigModal = false"
            class="px-5 py-2.5 text-gray-600 hover:bg-gray-100 rounded-xl transition-colors"
          >
            取消
          </button>
          <button
            @click="testConnection"
            :disabled="testing || loadingApiKey"
            class="px-5 py-2.5 bg-gray-800 hover:bg-gray-700 text-white rounded-xl disabled:opacity-50 transition-colors flex items-center gap-2"
          >
            <svg v-if="testing" class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"/>
            </svg>
            {{ testing ? '测试中...' : '测试连接' }}
          </button>
          <button
            @click="saveConfig"
            :disabled="saving || loadingApiKey"
            class="px-5 py-2.5 bg-gray-900 hover:bg-gray-800 text-white rounded-xl disabled:opacity-50 transition-colors"
          >
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
