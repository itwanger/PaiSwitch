import { defineStore } from 'pinia'
import { ref } from 'vue'
import { providerApi, apiKeyApi, configApi, switchApi } from '@/api'
import type {
  ProviderInfo,
  ApiKeyInfo,
  ApiKeyPlainInfo,
  ConfigInfo,
  SwitchResult,
  ProviderConfigUpdateRequest,
  ProviderTestRequest,
  ProviderTestResult,
  ConversationHistoryResponse,
  CustomProviderCreateRequest,
  TargetTool
} from '@/types'

const ACTIVE_TOOL_STORAGE_KEY = 'paiswitch.activeTool'

function loadInitialTool(): TargetTool {
  const stored = localStorage.getItem(ACTIVE_TOOL_STORAGE_KEY)
  return stored === 'CODEX' ? 'CODEX' : 'CLAUDE_CODE'
}

export const useProviderStore = defineStore('provider', () => {
  const activeTool = ref<TargetTool>(loadInitialTool())
  const providers = ref<ProviderInfo[]>([])
  const apiKeys = ref<ApiKeyInfo[]>([])
  const currentConfig = ref<ConfigInfo | null>(null)
  const loading = ref(false)

  function setActiveTool(tool: TargetTool) {
    if (activeTool.value === tool) return
    activeTool.value = tool
    localStorage.setItem(ACTIVE_TOOL_STORAGE_KEY, tool)
    // Reload tool-scoped data; api-keys list is global so we keep it.
    return Promise.all([fetchProviders(), fetchConfig()])
  }

  async function fetchProviders() {
    loading.value = true
    try {
      providers.value = await providerApi.getMy(activeTool.value)
    } finally {
      loading.value = false
    }
  }

  async function fetchApiKeys() {
    apiKeys.value = await apiKeyApi.getAll()
  }

  async function fetchConfig() {
    try {
      currentConfig.value = await configApi.get(activeTool.value)
    } catch (e) {
      // Codex may have no current provider yet — treat as null instead of throwing.
      currentConfig.value = null
    }
  }

  async function setApiKey(providerCode: string, apiKey: string) {
    await apiKeyApi.set(providerCode, apiKey, activeTool.value)
    await fetchApiKeys()
    await fetchProviders()
  }

  async function deleteApiKey(providerCode: string) {
    await apiKeyApi.delete(providerCode, activeTool.value)
    await fetchApiKeys()
    await fetchProviders()
  }

  async function getApiKeyPlain(providerCode: string): Promise<ApiKeyPlainInfo> {
    return apiKeyApi.getPlain(providerCode, activeTool.value)
  }

  async function switchProvider(providerCode: string): Promise<SwitchResult> {
    const result = await switchApi.switchTo(providerCode, activeTool.value)
    if (result.success) {
      await fetchConfig()
    }
    return result
  }

  async function naturalLanguageSwitch(prompt: string, sessionId?: string) {
    return switchApi.naturalLanguageSwitch(prompt, sessionId)
  }

  async function getLatestConversation(): Promise<ConversationHistoryResponse> {
    return switchApi.getLatestConversation()
  }

  async function getConversationBySessionId(sessionId: string): Promise<ConversationHistoryResponse> {
    return switchApi.getConversationBySessionId(sessionId)
  }

  async function updateProviderConfig(providerCode: string, config: ProviderConfigUpdateRequest) {
    const result = await providerApi.updateConfig(providerCode, config, activeTool.value)
    await fetchProviders()
    return result
  }

  async function createCustomProvider(data: CustomProviderCreateRequest, apiKey?: string) {
    const provider = await providerApi.createCustom(data, activeTool.value)
    if (apiKey && apiKey.trim()) {
      await apiKeyApi.set(provider.code, apiKey.trim(), activeTool.value)
    }
    await Promise.all([fetchProviders(), fetchApiKeys()])
    return provider
  }

  async function testProviderConnection(providerCode: string, testConfig?: ProviderTestRequest): Promise<ProviderTestResult> {
    return providerApi.testConnection(providerCode, activeTool.value, testConfig)
  }

  async function deleteProvider(providerCode: string) {
    await providerApi.delete(providerCode, activeTool.value)
    await Promise.all([fetchProviders(), fetchApiKeys()])
  }

  async function init() {
    await Promise.all([fetchProviders(), fetchApiKeys(), fetchConfig()])
  }

  return {
    activeTool,
    providers,
    apiKeys,
    currentConfig,
    loading,
    setActiveTool,
    fetchProviders,
    fetchApiKeys,
    fetchConfig,
    setApiKey,
    deleteApiKey,
    getApiKeyPlain,
    switchProvider,
    naturalLanguageSwitch,
    getLatestConversation,
    getConversationBySessionId,
    updateProviderConfig,
    createCustomProvider,
    testProviderConnection,
    deleteProvider,
    init
  }
})
