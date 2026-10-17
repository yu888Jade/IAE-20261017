# 升级内容对照

本版本是在原项目基础上直接修改，保留首页、学习档案、学习计划、作文页面的主要界面风格，并重构登录与数据模型。

## 1. Spring Security + JWT

新增：`User`、`UserRepository`、`JwtService`、`JwtAuthenticationFilter`、`SecurityConfig`、`AuthController`。
密码使用 BCrypt；除 `/api/auth/**` 外，API 默认要求登录。

## 2. 真正多用户 + 多聊天会话

前端已移除 `userId = 1`。后端从 JWT 获取用户 ID，并验证 ChatSession 必须属于当前用户。
新增 ChatSession 会话表，会话可以新建、切换和删除。

## 3. 作文记录 + 错题本

新增 `EssayRecord` 和 `Mistake` 表及 API。作文批改会保存记录，作文结果中的错误可以一键加入错题本。

## 4. Redis 限流 + SSE

AI 聊天和作文批改共用 Redis 用户级限流（默认 10 次/分钟）。聊天使用 DeepSeek stream=true，后端 SseEmitter 转发，前端 fetch ReadableStream 实时展示。

## 5. Docker + 部署

新增前后端 Dockerfile、Nginx、docker-compose.yml、.env.example、DEPLOY.md。

## 首次运行最重要的两件事

1. 复制 `.env.example` 为 `.env`，填你自己的 `DEEPSEEK_API_KEY`。
2. 修改默认 `JWT_SECRET` 和 MySQL 密码后再部署公网。

## 当前交付校验

- 已扫描并移除前端 `userId = 1`。
- 已移除各页面硬编码的 `http://localhost:8080/api`，改为统一请求层与环境变量。
- 所有 `.js` 与 Vue `<script setup>` 已通过 Node 语法检查。
- `package.json` 和 `pom.xml` 已通过结构解析。
- 当前沙箱没有 Docker；同时不能访问 Maven Central，因此无法在此环境下载新增 Java 依赖完成最终 Maven/Docker 构建。请在你自己的联网电脑执行 `docker compose up -d --build` 做最终运行验证。
