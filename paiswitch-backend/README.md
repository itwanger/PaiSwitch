# PaiSwitch Backend

AI 模型切换后端服务

## 技术栈

| 组件 | 版本 |
|------|------|
| JDK | 17 |
| Spring Boot | 3.2.2 |
| Spring AI | 1.0.0-M4 |
| MySQL | 8.0+ |
| Flyway | 数据库迁移 |

---

## 快速开始

### 前置条件

- **JDK 17+**
- **Maven 3.8+**
- **MySQL 8.0+**（确保服务已启动）

### 启动

```bash
cd paiswitch-backend
mvn spring-boot:run
```

应用会自动：
1. 创建数据库 `paiswitch`
2. 创建所有数据表
3. 插入内置模型提供商数据

启动成功后访问：http://localhost:8080/swagger-ui.html

---

### 方式二：Docker 部署

#### 1. 前置条件

- Docker Desktop 已安装并运行
- Docker Compose 可用

#### 2. 配置环境变量

```bash
cd docker

# 创建环境变量文件
cat > .env << 'EOF'
DB_PASSWORD=your_mysql_password
JWT_SECRET=your-jwt-secret-key-at-least-256-bits-long
AES_ENCRYPTION_KEY=your-aes-32-characters-key-here
ANTHROPIC_API_KEY=sk-ant-xxx
EOF
```

#### 3. 启动服务

```bash
cd docker
docker-compose up -d
```

#### 4. 查看日志

```bash
# 查看所有日志
docker-compose logs -f

# 只看后端日志
docker-compose logs -f backend
```

#### 5. 停止服务

```bash
docker-compose down

# 删除数据卷（清空数据库）
docker-compose down -v
```

---

## API 文档

启动后访问：
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

---

## API 接口

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/auth/register | 注册用户 |
| POST | /api/v1/auth/login | 登录获取 Token |

### 模型提供商

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/providers | 获取所有提供商 |
| GET | /api/v1/providers/my | 获取用户可用的提供商 |
| POST | /api/v1/providers/custom | 创建自定义提供商 |

### API Key 管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/api-keys | 设置 API Key |
| GET | /api/v1/api-keys | 获取用户的所有 API Key |
| DELETE | /api/v1/api-keys/{providerCode} | 删除 API Key |

### 配置管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/config | 获取当前配置 |
| PUT | /api/v1/config | 更新配置 |
| GET | /api/v1/config/backups | 获取配置备份 |
| POST | /api/v1/config/backups/{id}/restore | 恢复备份 |

### 模型切换

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/switch | 切换到指定提供商 |
| POST | /api/v1/ai/switch-by-nl | 自然语言切换 |
| POST | /api/v1/ai/chat | AI 聊天 |

---

## 快速测试

### 1. 注册用户

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }'
```

返回的 `data.token` 就是 JWT Token。

### 2. 设置 API Key

```bash
curl -X POST http://localhost:8080/api/v1/api-keys \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{
    "providerCode": "deepseek",
    "apiKey": "sk-xxx"
  }'
```

### 3. 切换模型

```bash
curl -X POST http://localhost:8080/api/v1/switch \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{
    "providerCode": "deepseek"
  }'
```

### 4. 自然语言切换

```bash
curl -X POST http://localhost:8080/api/v1/ai/switch-by-nl \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{
    "prompt": "帮我切换到 DeepSeek"
  }'
```

---

## 环境变量

| 变量名 | 描述 | 默认值 |
|--------|------|--------|
| `DB_PASSWORD` | MySQL 密码 | 123456 |
| `JWT_SECRET` | JWT 密钥（至少 256 位） | 内置值 |
| `AES_ENCRYPTION_KEY` | API Key 加密密钥（32 字符） | 内置值 |
| `ANTHROPIC_API_KEY` | Anthropic API Key | - |

---

## 项目结构

```
src/main/java/com/paicoding/paiswitch/
├── PaiSwitchApplication.java      # 启动类
├── common/
│   ├── config/                    # 配置类（安全、JWT、加密）
│   ├── exception/                 # 异常处理
│   ├── response/                  # 统一响应
│   └── security/                  # JWT 认证组件
├── controller/                    # REST API 控制器
├── domain/
│   ├── dto/                       # 数据传输对象
│   ├── entity/                    # JPA 实体
│   └── enums/                     # 枚举
├── repository/                    # 数据访问层
└── service/                       # 业务逻辑
    └── ai/                        # Spring AI 服务
```

---

## 常见问题

### Q: 启动报错 "Unknown database 'paiswitch'"

需要先创建数据库：
```sql
CREATE DATABASE paiswitch CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Q: 启动报错 "Access denied for user 'root'"

检查 `application.yml` 中的数据库密码是否正确。

### Q: 如何查看 SQL 执行日志？

修改 `application.yml`：
```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

### Q: 如何重置数据库？

```sql
DROP DATABASE paiswitch;
CREATE DATABASE paiswitch CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

重新启动应用，Flyway 会自动重建所有表。
