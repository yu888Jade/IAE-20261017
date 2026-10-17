# EnglishMate 升级版

面向英语学习的 AI Web 项目，基于原项目升级为真正的多用户系统。

## 已完成的核心功能

- Spring Security + JWT 注册、登录、鉴权，BCrypt 密码加密
- 多用户数据隔离：后端从 JWT 获取当前用户，不再信任前端 userId
- 多聊天会话：创建、切换、删除会话，聊天消息按账号与 session 保存
- DeepSeek SSE 流式输出：前端使用 fetch + ReadableStream 携带 JWT 并实时显示 token
- 作文批改记录：每次批改保存原文、评分与完整 JSON 结果
- 错题本：作文错误可一键加入错题本，并支持“已掌握”和删除
- Redis AI 接口限流：每个用户默认每分钟最多 10 次 AI 请求；Redis 临时不可用时采用可用性优先策略
- 学习档案、学习计划改为当前登录用户数据
- Axios 统一请求层，自动添加 Authorization Bearer Token
- Docker Compose：Vue/Nginx + Spring Boot + MySQL + Redis 一键编排

## 目录

- `ai-english-assistant` Vue 3 前端
- `ai-english-backend` Spring Boot 后端
- `docker-compose.yml` 一键容器化启动
- `.env.example` 环境变量模板
- `DEPLOY.md` 云服务器公网部署教程

## 最简单启动方式：Docker

1. 安装 Docker Desktop（Windows/Mac）或 Docker Engine + Compose（Linux）。
2. 在本目录复制环境变量：

```bash
cp .env.example .env
```

3. 编辑 `.env`，至少填写真实 `DEEPSEEK_API_KEY`，并修改 JWT_SECRET。
4. 启动：

```bash
docker compose up -d --build
```

5. 浏览器访问 `http://localhost`。

数据库表会由 JPA 首次启动时自动创建。

## 本地开发启动

后端需要 Java 17、MySQL 8、Redis 7。建议使用环境变量配置数据库和 DeepSeek Key。前端开发时可新建 `.env.development`：

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

然后：

```bash
cd ai-english-assistant
npm install
npm run dev
```

后端：

```bash
cd ai-english-backend
./mvnw spring-boot:run
```

> 原始压缩包缺少 `.mvn/wrapper/maven-wrapper.properties`，升级版已经补上。首次运行 Maven Wrapper 需要能访问 Maven Central 下载 Maven 与依赖。

## 面试时可以重点讲

1. 为什么不能继续让前端传 `userId=1`，以及 JWT 如何避免横向越权。
2. BCrypt 为什么不等于“加密后可解密”，密码验证如何完成。
3. SSE 和 WebSocket 的差异，为什么大模型单向流式输出更适合 SSE。
4. 原生 EventSource 无法方便地加 Authorization Header，因此前端使用 fetch + ReadableStream 解析 SSE。
5. Redis 固定窗口限流的 key、TTL 与异常降级策略。
6. ChatSession / ChatHistory 的一对多业务关系，以及后端为何必须再次验证 session 是否属于当前用户。
7. Docker Compose 中 Nginx、Spring Boot、MySQL、Redis 的网络关系。

## 安全提醒

- 不要把真实 `DEEPSEEK_API_KEY`、生产数据库密码、JWT_SECRET 提交到 GitHub。
- 线上环境一定更换 `.env` 中的默认密码。
- 项目目前使用 `ddl-auto=update` 方便学习；正式生产项目建议进一步引入 Flyway。
