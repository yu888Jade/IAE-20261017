# EnglishMate｜多用户 AI 英语学习助手

EnglishMate 是一个面向大学生英语学习场景的 AI Web 应用，围绕英语对话、四六级专项练习、作文批改、错题管理和学习计划等场景，将大模型能力与用户、会话及学习数据管理结合起来。

本项目采用前后端分离架构。前端使用 Vue 3，后端使用 Spring Boot；MySQL 用于保存用户和学习业务数据，Redis 用于 AI 接口限流，并通过 DeepSeek API 提供大模型能力。

> 项目定位：个人学习与求职作品项目。项目重点展示 AI API 接入、JWT 身份认证、多用户数据隔离、SSE 流式输出、请求重复提交控制及容器化编排等实践。

## 目录

- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [核心实现](#核心实现)
- [项目结构](#项目结构)
- [运行环境](#运行环境)
- [Docker Compose 启动](#docker-compose-启动推荐)
- [本地开发启动](#本地开发启动)
- [主要 API](#主要-api)
- [配置说明](#配置说明)
- [安全注意事项](#安全注意事项)
- [当前限制与后续改进](#当前限制与后续改进)

## 功能概览

### 1. 用户注册、登录与身份认证

- 支持用户注册和登录。
- 使用 BCrypt 对用户密码进行哈希处理。
- 使用 JWT 进行无状态身份认证。
- 前端统一请求层自动携带 `Authorization: Bearer <token>`。
- 后端从当前认证上下文中获取用户身份，并校验业务数据归属，避免只依赖前端传入的用户 ID。

### 2. AI 英语对话

- 支持自由对话、CET-4 和 CET-6 等学习模式。
- 根据不同模式组织 Prompt。
- 从数据库读取当前会话的历史消息，构建多轮对话上下文。
- 通过 SSE 流式返回模型生成内容，前端逐步展示回复。
- 支持创建、切换、查看和删除聊天会话。

### 3. 作文智能批改

- 调用 DeepSeek API 对英语作文进行分析。
- 保存作文原文、评分和结构化批改结果。
- 支持查看历史批改记录。
- 将作文中的错误关联到错题本，方便后续复习。

### 4. 错题管理

- 查看已保存的错题。
- 将错题标记为“已掌握”。
- 删除不再需要的错题记录。

### 5. 学习档案与学习计划

- 为当前登录用户保存个人学习档案。
- 提供学习计划查看与重新生成入口。
- 学习档案、学习计划等业务数据按当前用户进行管理。

### 6. AI 请求保护与部署

- 使用 Redis 实现用户维度的 AI 请求限流，默认每个用户每分钟最多 10 次 AI 请求。
- 使用 `requestId` 和生成状态控制，降低同一请求重复提交导致的重复处理。
- 使用 Docker Compose 编排前端 Nginx、Spring Boot、MySQL 和 Redis。
- 通过环境变量管理 API Key、数据库密码和 JWT 密钥等配置。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、JavaScript、Vue Router、Axios、Vite |
| 后端 | Java 17、Spring Boot、Spring Security、Spring Data JPA |
| 身份认证 | JWT、BCrypt |
| 数据库 | MySQL 8 |
| 缓存与限流 | Redis 7 |
| 大模型 | DeepSeek API |
| 流式通信 | SSE、浏览器 Fetch API、ReadableStream |
| 部署 | Docker、Docker Compose、Nginx |

## 系统架构

```text
用户浏览器
   |
   | HTTP / SSE
   v
Vue 3 前端
   |-- Vue Router：页面路由
   |-- Axios：普通 API 请求与 Token 携带
   |-- Fetch + ReadableStream：读取 AI 流式回复
   |
   v
Nginx
   |
   v
Spring Boot 后端
   |-- Spring Security + JWT：身份认证
   |-- Controller / Service / Repository：业务分层
   |-- MySQL：用户、会话、消息及学习数据
   |-- Redis：AI 请求限流
   |
   v
DeepSeek API
```

## 核心实现

### 1. JWT 身份认证与多用户数据隔离

用户登录或注册成功后，后端签发 JWT。后续请求携带 Token，后端验证身份并从当前认证信息中获取用户 ID。

读取或修改会话、作文记录、错题、学习档案等数据时，后端还会检查记录是否属于当前用户。这样不能仅通过修改请求中的 `userId` 或 `sessionId` 就访问其他用户的数据。

### 2. SSE 流式 AI 对话

聊天接口使用流式响应。后端将模型生成的内容持续写回客户端，前端通过 `fetch` 和 `ReadableStream` 读取数据并逐步更新消息显示。

选择这一方式是因为浏览器原生 `EventSource` 对自定义 `Authorization` 请求头的支持不够灵活，而项目的聊天接口需要携带 JWT。对于当前以服务端向客户端持续输出为主的 AI 回复场景，SSE 比完整双向通信更直接。

### 3. 多轮上下文与会话持久化

聊天会话与消息分别保存。每个会话关联所属用户，每条消息关联会话和请求标识。发送新消息时，后端从数据库读取当前用户、当前会话的历史消息，再与本次输入组合成模型请求上下文。

为控制请求体大小和模型调用成本，当前实现对上下文消息数量和字符量设置了限制；后续可以继续加入历史对话摘要。

### 4. 请求重复提交控制

前端为一次消息发送生成 `requestId`。后端使用该标识检查已完成结果和消息记录，并配合生成状态管理降低同一请求被重复处理的概率。

这主要用于处理连续点击、网络重试等情况。它不等同于分布式系统中对所有故障场景都提供严格的“恰好一次”执行保证。

### 5. Redis 用户级限流

AI 聊天和作文批改使用用户维度的限流规则，默认每分钟最多 10 次。达到限制时，接口返回限流错误，避免单个用户短时间内反复触发模型调用。

当前使用固定窗口策略，逻辑相对简单；固定窗口在时间边界可能出现突发请求，后续可根据需求升级为滑动窗口或令牌桶。

### 6. 作文批改与错题关联

作文批改结果在成功后保存，便于用户查看历史记录，而不必每次重新调用模型。作文中的错误可以加入错题本，并通过掌握状态辅助后续复习。

## 项目结构

仓库根目录中的 `EnglishMate/` 文件夹包含项目主体，目录结构如下：

```text
EnglishMate/
├── ai-english-assistant/       # Vue 3 前端
│   ├── src/
│   │   ├── api/                # 前端 API 封装
│   │   ├── components/         # 通用组件
│   │   ├── router/             # 路由配置
│   │   └── views/              # 页面视图
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── ai-english-backend/         # Spring Boot 后端
│   ├── src/main/java/com/english/assistant/
│   │   ├── config/             # 安全与 Web 配置
│   │   ├── controller/         # REST API
│   │   ├── dto/                # 请求与响应对象
│   │   ├── entity/             # 数据实体
│   │   ├── exception/          # 统一异常处理
│   │   ├── repository/         # 数据访问
│   │   ├── security/           # JWT 与当前用户
│   │   └── service/            # 业务逻辑
│   ├── Dockerfile
│   └── pom.xml
├── docker-compose.yml           # 服务编排
├── .env.example                 # 环境变量模板
└── DEPLOY.md                    # 部署说明
```

## 运行环境

推荐使用 Docker Compose 启动整个项目。这样不需要分别手动安装和配置 MySQL、Redis 及 Nginx。

如果选择本地开发，需要准备：

- Java 17
- Node.js 22.18 或兼容的项目要求版本
- MySQL 8
- Redis 7
- 可用的 DeepSeek API Key

首次启动前，请确认你已获得 DeepSeek API Key，并了解其使用可能产生的费用。

## Docker Compose 启动（推荐）

以下命令需要在项目目录中执行，也就是进入 `IAE-20261017/EnglishMate/` 后再操作。

### 第一步：下载代码并进入项目目录

如果你还没有把项目下载到电脑，先执行：

```bash
git clone https://github.com/yu888Jade/IAE-20261017.git
cd IAE-20261017/EnglishMate
```

如果你已经下载或解压了项目，则直接进入包含 `docker-compose.yml` 和 `.env.example` 的 `EnglishMate` 文件夹即可。

### 第二步：创建环境变量文件

macOS / Linux：

```bash
cp .env.example .env
```

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

然后使用文本编辑器打开 `.env`，填写自己的配置。至少需要修改以下项目：

```env
DEEPSEEK_API_KEY=填写你自己的DeepSeek_API_Key
MYSQL_ROOT_PASSWORD=请修改为强密码
JWT_SECRET=请填写至少32字符的随机密钥
WEB_PORT=80
CORS_ALLOWED_ORIGIN=http://localhost
```

不要把真实 `.env` 文件上传到 GitHub。

### 第三步：启动服务

```bash
docker compose up -d --build
```

首次启动需要下载镜像和构建应用，具体耗时取决于网络与电脑性能。

### 第四步：访问项目

浏览器打开：

```text
http://localhost
```

根据页面提示注册账号后即可体验功能。AI 功能需要有效的 DeepSeek API Key。

### 查看运行状态与日志

```bash
docker compose ps
docker compose logs -f backend
```

停止服务：

```bash
docker compose down
```

该命令默认保留 Docker 数据卷中的数据库数据。不要随意使用 `docker compose down -v`，因为它会删除项目使用的数据卷。

## 本地开发启动

本地开发需要先单独启动 MySQL 和 Redis，并准备好后端所需的环境变量。

### 1. 配置数据库

创建 MySQL 数据库 `ai_english`，并确认数据库地址、用户名和密码可用。后端配置支持通过环境变量设置数据库连接信息。

### 2. 启动后端

进入后端目录：

```bash
cd EnglishMate/ai-english-backend
```

根据你的环境设置以下变量：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT
DEEPSEEK_API_KEY
JWT_SECRET
CORS_ALLOWED_ORIGIN
```

本地前后端分开运行时，`CORS_ALLOWED_ORIGIN` 通常需要设置为前端开发地址，例如 `http://localhost:5173`。

macOS / Linux 可以使用 Maven Wrapper 启动：

```bash
./mvnw spring-boot:run
```

Windows 可以使用：

```powershell
.\mvnw.cmd spring-boot:run
```

### 3. 启动前端

打开另一个终端，进入前端目录：

```bash
cd EnglishMate/ai-english-assistant
```

安装依赖：

```bash
npm install
```

在前端目录创建 `.env.development` 文件，写入：

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

启动开发服务器：

```bash
npm run dev
```

根据终端显示的本地地址打开前端页面。若端口、数据库或环境变量与示例不同，请按实际配置调整。

## 主要 API

以下列出主要接口路径。除注册和登录接口外，业务接口需要携带有效 JWT；具体请求参数和响应结构以代码中的 Controller、DTO 为准。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/auth/me` | 获取当前用户信息 |
| GET | `/api/chat/sessions` | 获取会话列表 |
| POST | `/api/chat/sessions` | 创建会话 |
| DELETE | `/api/chat/sessions/{id}` | 删除会话 |
| GET | `/api/chat/sessions/{id}/messages` | 获取会话消息 |
| POST | `/api/chat/sessions/{id}/stream` | 发送消息并获取 SSE 流式回复 |
| POST | `/api/essay/check` | 批改作文 |
| GET | `/api/essay/records` | 获取作文批改记录 |
| GET | `/api/essay/records/{id}` | 获取指定作文记录 |
| DELETE | `/api/essay/records/{id}` | 删除作文记录 |
| GET | `/api/mistakes` | 获取错题列表 |
| POST | `/api/mistakes` | 添加错题 |
| PUT | `/api/mistakes/{id}/mastered` | 更新错题掌握状态 |
| DELETE | `/api/mistakes/{id}` | 删除错题 |
| GET | `/api/profile` | 获取学习档案 |
| POST | `/api/profile` | 保存学习档案 |
| GET | `/api/plan` | 获取学习计划 |
| POST | `/api/plan/regenerate` | 重新生成学习计划 |
| GET | `/api/dashboard` | 获取首页概览数据 |

## 配置说明

| 环境变量 | 说明 |
| --- | --- |
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 |
| `MYSQL_ROOT_PASSWORD` | Docker Compose 中 MySQL root 密码 |
| `JWT_SECRET` | JWT 签名密钥，应使用足够长的随机值 |
| `WEB_PORT` | 前端对外暴露端口，默认 `80` |
| `CORS_ALLOWED_ORIGIN` | 允许访问后端的前端来源 |
| `DB_URL` | MySQL JDBC 连接地址 |
| `DB_USERNAME` | MySQL 用户名 |
| `DB_PASSWORD` | MySQL 密码 |
| `REDIS_HOST` | Redis 主机名 |
| `REDIS_PORT` | Redis 端口 |
| `DEEPSEEK_API_URL` | DeepSeek API 地址，可选配置 |
| `DEEPSEEK_MODEL` | 使用的模型名称，可选配置 |
| `JWT_EXPIRATION_MS` | JWT 有效期，单位为毫秒 |

## 安全注意事项

- **不要提交 API Key、真实密码、JWT Secret 或其他访问凭据。**
- `.env` 已加入 Git 忽略规则；提交前仍应检查 GitHub 上的文件列表。
- `.env.example` 只能放示例值，不能放真实密钥。
- 如果密钥曾经上传到公开仓库，应立即在对应平台撤销或轮换密钥。仅删除当前文件并不能保证历史提交中的密钥已被清除。
- 项目中的数据库自动建表配置主要为学习和开发便利；正式生产环境应进一步使用数据库迁移工具、HTTPS、强密钥、日志监控和备份策略。
- 请在自己的环境中完成实际启动验证后，再向招聘方表示项目已经成功部署运行。

## 当前限制与后续改进

当前项目主要用于学习与求职作品展示，仍可从以下方向继续完善：

- 增加真实运行截图或演示视频。
- 补充后端自动化测试和前端测试。
- 使用 Flyway 等工具管理数据库结构变更。
- 为历史会话增加分页、摘要压缩和更精细的上下文管理。
- 完善日志、模型调用耗时、错误率及 Token 成本统计。
- 根据实际负载评估限流策略与并发控制方案。

## 作者

- GitHub：[@yu888Jade](https://github.com/yu888Jade)
- 项目仓库：[IAE-20261017](https://github.com/yu888Jade/IAE-20261017)

欢迎查看代码与项目结构。欢迎提出问题和改进建议。
