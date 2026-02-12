# PaiSwitch

AI 模型切换工具，支持 Claude、DeepSeek、智谱 AI、OpenRouter 等多种模型的快速切换。

## 项目结构

```
PaiSwitch/
├── ClaudeModelSwitcher/    # macOS 原生应用 (Swift/SwiftUI)
├── paiswitch-backend/      # Spring Boot 后端服务
├── paiswitch-web/          # Vue 3 Web 前端
└── *.sh                    # Shell 脚本 (命令行切换)
```

---

## 快速开始

### 方式一：macOS 原生应用（推荐）

1. 用 Xcode 打开 `ClaudeModelSwitcher/ClaudeModelSwitcher.xcodeproj`
2. 选择目标设备为 Mac
3. 点击运行 (⌘R)

**功能特性：**
- 系统托盘快速切换
- API Key 安全存储 (Keychain)
- 自定义模型提供商
- 配置历史备份
- 菜单栏快捷操作
- **在线模式**（登录后启用）
- **AI 自然语言切换**（在线模式）

**系统要求：** macOS 13.0+

**在线模式：**
1. 启动后端服务
2. 在 macOS 应用中点击「登录以启用在线功能」
3. 输入服务器地址（如 `http://localhost:8080/api/v1`）
4. 登录后可使用 AI 助手进行自然语言切换

---

### 方式二：Web 界面

#### 1. 启动后端

```bash
cd paiswitch-backend
mvn spring-boot:run
```

后端会自动创建数据库和表。

#### 2. 启动前端

```bash
cd paiswitch-web
npm install
npm run dev
```

访问 http://localhost:3000

**默认账号：** `admin` / `admin123`

**功能特性：**
- 用户注册/登录
- AI 自然语言切换（如"切换到 DeepSeek"）
- 配置在线管理
- 多用户支持

---

### 方式三：命令行脚本

```bash
# 交互式菜单
./model_manager.sh

# 直接切换
./switch_to_deepseek.sh    # 切换到 DeepSeek V3
./switch_to_zhipu.sh       # 切换到智谱 AI
./switch_to_claude.sh      # 切换回 Claude 官方
./restore_claude_config.sh # 恢复原始配置
```

---

## 技术栈

### macOS 原生应用
| 组件 | 技术 |
|------|------|
| 语言 | Swift 5 |
| 框架 | SwiftUI |
| 最低版本 | macOS 13.0 |
| 存储 | Keychain + UserDefaults |

### 后端
| 组件 | 版本 |
|------|------|
| JDK | 17 |
| Spring Boot | 3.2.2 |
| Spring AI | 1.0.0-M4 |
| MySQL | 8.0+ |
| Flyway | 数据库迁移 |

### Web 前端
| 组件 | 版本 |
|------|------|
| Vue | 3.4 |
| TypeScript | 5.3 |
| Vite | 5.0 |
| Tailwind CSS | 3.4 |
| Pinia | 2.1 |

---

## 内置模型提供商

| 代码 | 名称 | 模型 | API 兼容 |
|------|------|------|----------|
| claude | Claude (Official) | claude-sonnet-4 | Anthropic |
| deepseek | DeepSeek V3 | deepseek-chat | Anthropic |
| zhipu | 智谱 AI | glm-4.5 | Anthropic |
| openrouter | OpenRouter | 多模型 | Anthropic |

---

## 功能对比

| 功能 | macOS 应用 | macOS 在线模式 | Web 界面 | 命令行 |
|------|-----------|---------------|----------|--------|
| 快速切换 | ✅ | ✅ | ✅ | ✅ |
| 系统托盘 | ✅ | ✅ | ❌ | ❌ |
| 自然语言切换 | ❌ | ✅ | ✅ | ❌ |
| 多用户 | ❌ | ✅ | ✅ | ❌ |
| API Key 安全存储 | Keychain | Keychain | AES 加密 | 明文 |
| 自定义提供商 | ✅ | ✅ | ✅ | ❌ |
| 配置备份 | ✅ | ✅ | ✅ | ✅ |
| 需要后端 | ❌ | ✅ | ✅ | ❌ |

---

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/auth/register | 注册用户 |
| POST | /api/v1/auth/login | 登录 |
| GET | /api/v1/providers | 获取模型列表 |
| POST | /api/v1/api-keys | 设置 API Key |
| POST | /api/v1/switch | 切换模型 |
| POST | /api/v1/ai/switch-by-nl | 自然语言切换 |

完整 API 文档：http://localhost:8080/swagger-ui.html

---

## 配置说明

### Claude Code 配置文件

位置：`~/.claude/settings.json`

```json
{
  "env": {
    "ANTHROPIC_BASE_URL": "https://api.anthropic.com",
    "ANTHROPIC_AUTH_TOKEN": "your-api-key"
  }
}
```

切换模型时，工具会自动修改此文件。

### 环境变量

| 变量名 | 描述 | 默认值 |
|--------|------|--------|
| `DB_PASSWORD` | MySQL 密码 | 123456 |
| `JWT_SECRET` | JWT 密钥 | 内置值 |
| `AES_ENCRYPTION_KEY` | 加密密钥 | 内置值 |

---

## 目录结构详解

### macOS 原生应用

```
ClaudeModelSwitcher/
├── App/                    # 应用入口
├── Models/                 # 数据模型
│   ├── ModelProvider.swift
│   ├── ClaudeConfig.swift
│   └── CustomProviderConfiguration.swift
├── Views/                  # SwiftUI 视图
│   ├── ContentView.swift
│   ├── ProviderListView.swift
│   ├── SettingsView.swift
│   └── TrayMenuView.swift
├── ViewModels/             # 视图模型
│   └── MainViewModel.swift
└── Services/               # 服务层
    ├── ConfigManager.swift
    ├── BackupManager.swift
    ├── KeychainManager.swift
    └── CustomProviderManager.swift
```

### 后端

```
paiswitch-backend/
├── src/main/java/com/paicoding/paiswitch/
│   ├── controller/         # REST API
│   ├── service/            # 业务逻辑
│   ├── repository/         # 数据访问
│   ├── domain/             # 实体和 DTO
│   └── common/             # 配置、异常、安全
└── src/main/resources/
    ├── application.yml
    └── db/migration/       # Flyway 迁移
```

### Web 前端

```
paiswitch-web/
├── src/
│   ├── api/                # API 调用
│   ├── stores/             # Pinia 状态
│   ├── views/              # 页面组件
│   ├── components/         # 公共组件
│   ├── router/             # 路由配置
│   └── types/              # TypeScript 类型
└── vite.config.ts
```

---

## 常见问题

### Q: 切换后 Claude Code 不生效？

重启 Claude Code CLI 或重新打开终端。

### Q: 如何添加自定义模型提供商？

- **macOS 应用**：设置 → 添加自定义提供商
- **Web 界面**：模型管理 → 添加自定义提供商

### Q: API Key 存储在哪里？

- **macOS 应用**：系统 Keychain（最安全）
- **后端服务**：MySQL 数据库（AES-256 加密）
- **命令行脚本**：明文存储在配置文件

### Q: 如何重置数据库？

```sql
DROP DATABASE paiswitch;
CREATE DATABASE paiswitch CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

重启后端，Flyway 会自动重建表。

---

## 开发指南

### 构建 macOS 应用

```bash
cd ClaudeModelSwitcher
xcodebuild -scheme ClaudeModelSwitcher -configuration Release
```

### 构建后端 JAR

```bash
cd paiswitch-backend
mvn clean package -DskipTests
java -jar target/paiswitch-backend-1.0.0-SNAPSHOT.jar
```

### 构建 Web 前端

```bash
cd paiswitch-web
npm run build
```

---

## 详细文档

- [后端文档](paiswitch-backend/README.md)
- [前端文档](paiswitch-web/README.md)

---

## License

MIT
