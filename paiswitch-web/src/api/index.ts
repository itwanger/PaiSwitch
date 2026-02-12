import { apiPost, apiGet, apiPut, apiDelete } from './client'
import type {
  LoginResponse,
  UserInfo,
  ProviderInfo,
  ApiKeyInfo,
  ApiKeyPlainInfo,
  ConfigInfo,
  BackupInfo,
  SwitchResult,
  NaturalLanguageResponse,
  ConversationHistoryResponse,
  ProviderConfigUpdateRequest,
  ProviderTestRequest,
  ProviderTestResult
} from '@/types'

// Auth API
export const authApi = {
  login: (username: string, password: string) =>
    apiPost<LoginResponse>('/auth/login', { username, password }),

  register: (username: string, email: string, password: string) =>
    apiPost<LoginResponse>('/auth/register', { username, email, password })
}

// Provider API
export const providerApi = {
  getAll: () => apiGet<ProviderInfo[]>('/providers'),

  getMy: () => apiGet<ProviderInfo[]>('/providers/my'),

  getByCode: (code: string) => apiGet<ProviderInfo>(`/providers/${code}`),

  createCustom: (data: {
    code: string
    name: string
    description?: string
    baseUrl: string
    modelName: string
    modelNameSmall?: string
    iconUrl?: string
  }) => apiPost<ProviderInfo>('/providers/custom', data),

  update: (code: string, data: Partial<ProviderInfo>) =>
    apiPut<ProviderInfo>(`/providers/${code}`, data),

  updateConfig: (code: string, data: ProviderConfigUpdateRequest) =>
    apiPut<ProviderInfo>(`/providers/${code}/config`, data),

  testConnection: (code: string, data?: ProviderTestRequest) =>
    apiPost<ProviderTestResult>(`/providers/${code}/test`, data || {})
}

// API Key API
export const apiKeyApi = {
  set: (providerCode: string, apiKey: string) =>
    apiPost<ApiKeyInfo>('/api-keys', { providerCode, apiKey }),

  getAll: () => apiGet<ApiKeyInfo[]>('/api-keys'),

  getPlain: (providerCode: string) =>
    apiGet<ApiKeyPlainInfo>(`/api-keys/${providerCode}/plain`),

  delete: (providerCode: string) =>
    apiDelete<void>(`/api-keys/${providerCode}`)
}

// Config API
export const configApi = {
  get: () => apiGet<ConfigInfo>('/config'),

  update: (data: { providerId: number; apiTimeout?: number; extraConfig?: Record<string, unknown> }) =>
    apiPut<ConfigInfo>('/config', data),

  getBackups: (page = 0, size = 20) =>
    apiGet<{ backups: BackupInfo[]; total: number }>(`/config/backups?page=${page}&size=${size}`),

  restoreBackup: (backupId: number) =>
    apiPost<ConfigInfo>(`/config/backups/${backupId}/restore`)
}

// Switch API
export const switchApi = {
  switchTo: (providerCode: string, clientInfo?: string) =>
    apiPost<SwitchResult>('/switch', { providerCode, clientInfo }),

  naturalLanguageSwitch: (prompt: string, sessionId?: string, clientInfo?: string) =>
    apiPost<NaturalLanguageResponse>('/ai/switch-by-nl', { prompt, sessionId, clientInfo }),

  chat: (prompt: string, sessionId?: string) =>
    apiPost<NaturalLanguageResponse>('/ai/chat', { prompt, sessionId }),

  getLatestConversation: () =>
    apiGet<ConversationHistoryResponse>('/ai/conversations/latest'),

  getConversationBySessionId: (sessionId: string) =>
    apiGet<ConversationHistoryResponse>(`/ai/conversations/${encodeURIComponent(sessionId)}`)
}
