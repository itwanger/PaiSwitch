import { apiPost, apiGet, apiPut, apiDelete } from './client'
import type {
  LoginResponse,
  ProviderInfo,
  ApiKeyInfo,
  ApiKeyPlainInfo,
  ConfigInfo,
  BackupInfo,
  SwitchResult,
  NaturalLanguageResponse,
  ConversationHistoryResponse,
  ProviderConfigUpdateRequest,
  CustomProviderCreateRequest,
  ProviderTestRequest,
  ProviderTestResult,
  SkillListResponse,
  SkillDetail,
  TrashSkillEntry,
  RenameSkillRequest,
  SkillSummary,
  TargetTool
} from '@/types'

function toolQuery(tool: TargetTool): string {
  const param = tool === 'CODEX' ? 'codex' : 'claude_code'
  return `?tool=${param}`
}

// Auth API
export const authApi = {
  login: (username: string, password: string) =>
    apiPost<LoginResponse>('/auth/login', { username, password }),

  register: (username: string, email: string, password: string) =>
    apiPost<LoginResponse>('/auth/register', { username, email, password })
}

// Provider API
export const providerApi = {
  getAll: (tool: TargetTool) => apiGet<ProviderInfo[]>(`/providers${toolQuery(tool)}`),

  getMy: (tool: TargetTool) => apiGet<ProviderInfo[]>(`/providers/my${toolQuery(tool)}`),

  getByCode: (code: string, tool: TargetTool) =>
    apiGet<ProviderInfo>(`/providers/${code}${toolQuery(tool)}`),

  createCustom: (data: CustomProviderCreateRequest, tool: TargetTool) =>
    apiPost<ProviderInfo>(`/providers/custom${toolQuery(tool)}`, data),

  update: (code: string, data: Partial<ProviderInfo>, tool: TargetTool) =>
    apiPut<ProviderInfo>(`/providers/${code}${toolQuery(tool)}`, data),

  updateConfig: (code: string, data: ProviderConfigUpdateRequest, tool: TargetTool) =>
    apiPut<ProviderInfo>(`/providers/${code}/config${toolQuery(tool)}`, data),

  testConnection: (code: string, tool: TargetTool, data?: ProviderTestRequest) =>
    apiPost<ProviderTestResult>(`/providers/${code}/test${toolQuery(tool)}`, data || {}),

  delete: (code: string, tool: TargetTool) =>
    apiDelete<void>(`/providers/${code}${toolQuery(tool)}`)
}

// API Key API
export const apiKeyApi = {
  set: (providerCode: string, apiKey: string, tool: TargetTool) =>
    apiPost<ApiKeyInfo>(`/api-keys${toolQuery(tool)}`, { providerCode, apiKey }),

  getAll: () => apiGet<ApiKeyInfo[]>('/api-keys'),

  getPlain: (providerCode: string, tool: TargetTool) =>
    apiGet<ApiKeyPlainInfo>(`/api-keys/${providerCode}/plain${toolQuery(tool)}`),

  delete: (providerCode: string, tool: TargetTool) =>
    apiDelete<void>(`/api-keys/${providerCode}${toolQuery(tool)}`)
}

// Config API
export const configApi = {
  get: (tool: TargetTool) => apiGet<ConfigInfo>(`/config${toolQuery(tool)}`),

  update: (
    data: { providerId: number; apiTimeout?: number; extraConfig?: Record<string, unknown> },
    tool: TargetTool
  ) => apiPut<ConfigInfo>(`/config${toolQuery(tool)}`, data),

  getBackups: (page = 0, size = 20) =>
    apiGet<{ backups: BackupInfo[]; total: number }>(`/config/backups?page=${page}&size=${size}`),

  restoreBackup: (backupId: number) =>
    apiPost<ConfigInfo>(`/config/backups/${backupId}/restore`)
}

// Switch API
export const switchApi = {
  switchTo: (providerCode: string, tool: TargetTool, clientInfo?: string) =>
    apiPost<SwitchResult>(`/switch${toolQuery(tool)}`, { providerCode, clientInfo }),

  naturalLanguageSwitch: (prompt: string, sessionId?: string, clientInfo?: string) =>
    apiPost<NaturalLanguageResponse>('/ai/switch-by-nl', { prompt, sessionId, clientInfo }),

  chat: (prompt: string, sessionId?: string) =>
    apiPost<NaturalLanguageResponse>('/ai/chat', { prompt, sessionId }),

  getLatestConversation: () =>
    apiGet<ConversationHistoryResponse>('/ai/conversations/latest'),

  getConversationBySessionId: (sessionId: string) =>
    apiGet<ConversationHistoryResponse>(`/ai/conversations/${encodeURIComponent(sessionId)}`)
}

// Skills API
export const skillApi = {
  list: () => apiGet<SkillListResponse>('/skills'),

  getDetail: (folderName: string) =>
    apiGet<SkillDetail>(`/skills/${encodeURIComponent(folderName)}`),

  rename: (folderName: string, data: RenameSkillRequest) =>
    apiPost<SkillSummary>(`/skills/${encodeURIComponent(folderName)}/rename`, data),

  moveToTrash: (folderName: string) =>
    apiPost<void>(`/skills/${encodeURIComponent(folderName)}/trash`),

  listTrash: () => apiGet<TrashSkillEntry[]>('/skills/trash'),

  restore: (trashEntry: string) =>
    apiPost<SkillSummary>(`/skills/trash/${encodeURIComponent(trashEntry)}/restore`)
}
