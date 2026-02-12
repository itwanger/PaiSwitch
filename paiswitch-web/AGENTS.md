# AGENTS.md

This file provides guidance to Qoder (qoder.com) when working with code in this repository.

## Common Commands

### Development
```bash
npm run dev        # Start development server on port 3000
npm run build      # Type check with vue-tsc and build for production
npm run preview    # Preview production build
npm run lint       # Run ESLint with auto-fix
```

### Type Checking
The build process includes TypeScript type checking via `vue-tsc`. Always run `npm run build` or manually run `vue-tsc` before committing to catch type errors.

## Architecture Overview

### Tech Stack
- Vue 3.4 with Composition API
- TypeScript
- Vite 5 (build tool)
- Pinia (state management)
- Vue Router 4 (routing)
- Tailwind CSS 3.4 (styling)
- Axios (HTTP client)

### Application Structure

**State Management (Pinia Stores)**
- `stores/auth.ts`: Manages authentication state, token, and user info. Stores token/user in localStorage with keys `paiswitch_token` and `paiswitch_user`
- `stores/provider.ts`: Manages AI providers, API keys, and configuration. Handles provider switching and natural language switching
- `stores/toast.ts`: Toast notification system

**API Layer**
- `api/client.ts`: Axios instance configured with `/api/v1` base URL, 30s timeout, and interceptors for auth token injection and 401 handling
- `api/index.ts`: Organized API endpoints grouped by domain (auth, provider, apiKey, config, switch)
- All API functions use typed wrapper functions (`apiGet`, `apiPost`, `apiPut`, `apiDelete`) that extract data from `ApiResponse<T>` wrapper

**Routing**
- Authentication guard in `router/index.ts` checks `requiresAuth` meta field
- Lazy-loads provider store on first protected route access
- Routes: `/login` (public), `/` (layout with nested dashboard, providers, config, ai-chat routes)

**Views**
- `LoginView.vue`: Login and registration forms
- `LayoutView.vue`: Main layout with navigation
- `DashboardView.vue`: Dashboard overview
- `ProvidersView.vue`: Provider management (largest component at 12.6 KB)
- `ConfigView.vue`: Configuration settings
- `AiChatView.vue`: Natural language AI chat interface

### Key Design Patterns

**API Response Wrapper**
All backend responses follow `ApiResponse<T>` structure:
```typescript
{ code: number, message: string, data: T }
```
The `api/client.ts` wrapper functions automatically unwrap to return just the `data` field.

**Authentication Flow**
1. Token stored in localStorage and injected into all requests via Axios interceptor
2. 401 responses trigger automatic logout and redirect to login
3. Router guard prevents unauthenticated access to protected routes

**Provider Management**
- Providers can be built-in or custom
- Each provider has API key management, configuration (baseUrl, modelName), and test connection functionality
- Natural language switching allows AI-driven provider selection

### Backend Integration

**Development Proxy**
Vite dev server proxies `/api` requests to `http://localhost:8080` (configured in vite.config.ts).

**Path Alias**
`@/` is aliased to `src/` directory for cleaner imports.

### Type Definitions

All API types are centralized in `types/api.ts` including:
- `ProviderInfo`, `ApiKeyInfo`, `ConfigInfo`, `BackupInfo`
- `SwitchResult`, `NaturalLanguageResponse`
- `ProviderConfigUpdateRequest`, `ProviderTestRequest`
