# PaiSwitch Web

Vue 3 前端应用

## 技术栈

- Vue 3.4
- TypeScript
- Vite 5
- Pinia (状态管理)
- Vue Router 4
- Tailwind CSS 3.4
- Axios

## 快速开始

### 安装依赖

```bash
cd paiswitch-web
npm install
```

### 开发模式

```bash
npm run dev
```

访问 http://localhost:3000

### 构建

```bash
npm run build
```

## 项目结构

```
src/
├── api/           # API 调用
│   ├── client.ts  # Axios 配置
│   └── index.ts   # API 接口
├── assets/        # 静态资源
├── components/    # 公共组件
├── router/        # 路由配置
├── stores/        # Pinia 状态管理
│   ├── auth.ts    # 认证状态
│   └── provider.ts # 提供商状态
├── types/         # TypeScript 类型
├── views/         # 页面组件
│   ├── LoginView.vue
│   ├── LayoutView.vue
│   ├── DashboardView.vue
│   ├── ProvidersView.vue
│   ├── ConfigView.vue
│   └── AiChatView.vue
├── App.vue
└── main.ts
```

## 功能

- 登录/注册
- 模型提供商管理
- API Key 配置
- 模型切换
- AI 自然语言切换

## 代理配置

开发模式下，`/api` 请求会代理到 `http://localhost:8080`。

如需修改后端地址，编辑 `vite.config.ts`。
