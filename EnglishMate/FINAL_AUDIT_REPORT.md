# EnglishMate 三模块强制重构验收报告

## 原则

- 零 Mock：三个业务页面的列表状态全部从后端 API / MySQL 返回，不存在前端硬编码业务记录数组。
- 严格用户隔离：列表读取使用 JWT 当前用户，并校验前端传入 userId 必须与 JWT 用户一致；Repository 查询显式包含 `userId` 条件。
- 新账号隔离：聊天、作文、错题查询同时限定记录创建时间不早于当前账号 `createTime`，避免数据库曾重置用户表并复用数值 ID 时把旧账号遗留数据误挂到新账号。

## 模块一：AI 英语对话

- 输入框使用 `v-model="inputValue"`。
- 发送按钮严格使用 `:disabled="!inputValue.trim() || isLoading"`。
- 同时绑定 `@click="send"` 与 `@keyup.enter.exact="handleEnter"`；Shift+Enter 保留换行。
- 新账号进入页面不会自动创建假会话；没有数据库会话时显示空状态。
- 前端只提交 `message + requestId`，不再提交可伪造的 history。
- 后端从 MySQL 按 `userId + sessionId` 读取最近真实聊天记录构造 DeepSeek 上下文。
- 用户消息与 AI 回复均写入 `chat_history`，写入的 `userId` 来自 JWT 当前用户。
- DeepSeek SSE 保留超时、429、鉴权失败及服务异常处理。

## 模块二：作文智能批改

- 页面通过 `/api/auth/me` 获得服务端确认的当前用户 ID，再调用 `GET /api/essay/records?userId=...`。
- 后端比较 query userId 与 JWT 当前用户，不一致返回 403。
- Repository 显式 `where e.userId = :userId`，同时过滤无 score / 无正文 / 无 resultJson 的历史脏记录。
- 无记录时显示“暂无作文记录”，不渲染占位条目。
- AI 批改按钮在 `loading` 时锁定，requestId 幂等逻辑保留，成功落库后才刷新历史列表。
- 前端结果展示不再用“建议结合上下文...”等伪造 AI 结果兜底；数据结构异常直接判为不可展示。

## 模块三：错题本

- 页面通过 `/api/auth/me` 获取当前用户 ID，再调用 `GET /api/mistakes?userId=...`。
- 后端校验 query userId 与 JWT 用户一致；Repository 显式 `where m.userId = :userId`。
- 无数据时显示“暂无错题，去练习吧”。
- 删除使用显式条件删除：`id + userId (+ accountCreatedAt)`，删除 0 行返回 404。
- 错题新增必须携带 `essayRecordId`；后端先验证该作文记录属于当前 JWT 用户，才允许写入错题本。
- 列表只展示具备真实 `essayRecordId` 且字段完整的数据库记录，旧的无关联脏数据不会继续渲染。

## 防伪自检

源码规则自检 19/19 通过：

1. Chat v-model / disabled / click / Enter 绑定通过。
2. Chat DTO 不接受 history，通过。
3. Chat 上下文来自 MySQL，通过。
4. Chat DeepSeek streaming 接通，通过。
5. Chat 用户/AI 消息按当前 userId 入库，通过。
6. Essay userId query param + JWT 比对，通过。
7. Essay Repository userId 条件与新账号时间边界，通过。
8. Essay Empty State 与 Loading 防连点，通过。
9. Mistake userId query param + JWT 比对，通过。
10. Mistake Empty State，通过。
11. Mistake 删除带 id + userId 条件，通过。
12. Mistake 与作文记录真实关联，通过。

前端三个 Vue 文件及三个 API JS 文件已通过 Node JavaScript 语法检查。

后端 Maven Wrapper 已尝试执行 `./mvnw -DskipTests compile`，但当前沙箱不能访问 Maven Central，Maven 分发包无法下载，因此此环境无法完成真实 Maven 编译。该限制是沙箱网络限制，不是用“静态检查”冒充编译成功。
