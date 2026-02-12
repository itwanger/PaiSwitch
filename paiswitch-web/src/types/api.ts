export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

export interface UserInfo {
  id: number
  username: string
  email: string
  nickname?: string
  avatarUrl?: string
  status: string
  createdAt: string
}

export interface ProviderInfo {
  id: number
  code: string
  name: string
  description?: string
  baseUrl: string
  modelName: string
  modelNameSmall?: string
  isBuiltin: boolean
  isActive: boolean
  sortOrder: number
  iconUrl?: string
  hasApiKey?: boolean
  createdAt: string
}

export interface ProviderConfigUpdateRequest {
  baseUrl?: string
  modelName?: string
  modelNameSmall?: string
}

export interface ProviderTestRequest {
  baseUrl?: string
  modelName?: string
  apiKey?: string
}

export interface ProviderTestResult {
  success: boolean
  message: string
  modelName?: string
  responseTimeMs?: number
}

export interface ApiKeyInfo {
  id: number
  providerId: number
  providerCode: string
  providerName: string
  keyHint: string
  isValid: boolean
  lastUsedAt?: string
  expiresAt?: string
  createdAt: string
}

export interface ApiKeyPlainInfo {
  providerCode: string
  apiKey?: string | null
}

export interface ConfigInfo {
  id: number
  userId: number
  currentProvider: ProviderInfo
  apiTimeout: number
  extraConfig?: Record<string, unknown>
  updatedAt: string
}

export interface BackupInfo {
  id: number
  providerId: number
  providerCode: string
  providerName: string
  backupName?: string
  configContent: Record<string, unknown>
  backupType: string
  createdAt: string
}

export interface SwitchResult {
  success: boolean
  message: string
  previousProvider?: ProviderInfo
  currentProvider?: ProviderInfo
  switchedAt: string
}

export interface NaturalLanguageResponse {
  aiResponse: string
  switchTriggered?: boolean
  switchResult?: SwitchResult
  sessionId: string
}
