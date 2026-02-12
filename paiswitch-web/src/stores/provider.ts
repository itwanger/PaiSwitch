import { defineStore } from 'pinia'
import { ref } from 'vue'
import { providerApi, apiKeyApi, configApi, switchApi } from '@/api'
import type { ProviderInfo, ApiKeyInfo, ConfigInfo, SwitchResult, ProviderConfigUpdateRequest, ProviderTestRequest, ProviderTestResult } from '@/types'

export const useProviderStore = defineStore('provider', () => {
  const providers = ref<ProviderInfo[]>([])
  const apiKeys = ref<ApiKeyInfo[]>([])
  const currentConfig = ref<ConfigInfo | null>(null)
  const loading = ref(false)

  async function fetchProviders() {
    loading.value = true
    try {
      providers.value = await providerApi.getMy()
    } finally {
      loading.value = false
    }
  }

  async function fetchApiKeys() {
    apiKeys.value = await apiKeyApi.getAll()
  }

  async function fetchConfig() {
    currentConfig.value = await configApi.get()
  }

  async function setApiKey(providerCode: string, apiKey: string) {
    await apiKeyApi.set(providerCode, apiKey)
    await fetchApiKeys()
    await fetchProviders()
  }

  async function deleteApiKey(providerCode: string) {
    await apiKeyApi.delete(providerCode)
    await fetchApiKeys()
    await fetchProviders()
  }

  async function switchProvider(providerCode: string): Promise<SwitchResult> {
    const result = await switchApi.switchTo(providerCode)
    if (result.success) {
      await fetchConfig()
    }
    return result
  }

  async function naturalLanguageSwitch(prompt: string, sessionId?: string) {
    return switchApi.naturalLanguageSwitch(prompt, sessionId)
  }

  async function updateProviderConfig(providerCode: string, config: ProviderConfigUpdateRequest) {
    const result = await providerApi.updateConfig(providerCode, config)
    await fetchProviders()
    return result
  }

  async function testProviderConnection(providerCode: string, testConfig?: ProviderTestRequest): Promise<ProviderTestResult> {
    return providerApi.testConnection(providerCode, testConfig)
  }

  async function init() {
    await Promise.all([fetchProviders(), fetchApiKeys(), fetchConfig()])
  }

  return {
    providers,
    apiKeys,
    currentConfig,
    loading,
    fetchProviders,
    fetchApiKeys,
    fetchConfig,
    setApiKey,
    deleteApiKey,
    switchProvider,
    naturalLanguageSwitch,
    updateProviderConfig,
    testProviderConnection,
    init
  }
})
