# EnglishMate AI 对话多会话重构说明

## 1. 推荐的数据结构

前端 localStorage key：`englishmate.chat.sessions.v2`

```json
{
  "version": 2,
  "updatedAt": "2026-10-12T08:00:00.000Z",
  "activeMode": "cet6",
  "sessions": {
    "free": {
      "sessionId": "uuid-free",
      "messages": [
        {"role": "user", "content": "Hello", "createdAt": "..."},
        {"role": "assistant", "content": "Hi!", "createdAt": "..."}
      ],
      "draft": "",
      "updatedAt": "...",
      "clearedAt": null
    },
    "cet4": {
      "sessionId": "uuid-cet4",
      "messages": [],
      "draft": "",
      "updatedAt": "...",
      "clearedAt": null
    },
    "cet6": {
      "sessionId": "uuid-cet6",
      "messages": [],
      "draft": "",
      "updatedAt": "...",
      "clearedAt": null
    }
  }
}
```

每种模式完全独立保存：`sessionId + messages + draft + updatedAt`。

## 2. 核心文件

前端：
- `src/stores/chatSessionStore.js`：三模式状态、localStorage、版本控制、竞态保护、发送与清空。
- `src/api/chat.js`：发送、按模式加载历史、按模式彻底清空。
- `src/views/ChatView.vue`：模式切换和页面 UI。

后端：
- `entity/ChatHistory.java`：新增 `sessionId`。
- `entity/ChatSessionState.java`：记录每个 user + mode 当前有效 sessionId。
- `repository/ChatHistoryRepository.java`：按 user + mode + session 查询，按模式删除。
- `repository/ChatSessionStateRepository.java`：保存当前有效 session。
- `service/ChatSessionService.java`：session 校验/旋转。
- `controller/ChatController.java`：新接口逻辑。

## 3. 接口

发送：
`POST /api/chat/{userId}`

请求体：
```json
{
  "message": "hello",
  "mode": "cet6",
  "sessionId": "uuid",
  "history": [
    {"role": "user", "content": "hello"}
  ]
}
```

加载当前模式历史：
`GET /api/chat/history/{userId}/{mode}?sessionId=uuid`

清空当前模式：
`DELETE /api/chat/history/{userId}/{mode}`

请求体：
```json
{
  "sessionId": "old-uuid",
  "nextSessionId": "new-uuid"
}
```

清空时后端会先把当前有效 session 切到 `nextSessionId`，再删除该模式全部历史。因此旧 AI 请求即使稍后返回，也不能再写回数据库。

## 4. 为什么清空后不会复活

普通 `messages = []` 只能清 UI。如果 AI 请求仍在进行，它稍后返回时可能再次 push 旧回复。

本版本同时使用：
1. 前端 `requestGeneration`；
2. 每模式独立 `sessionId`；
3. 后端 `ChatSessionState` 当前 session 校验；
4. DELETE 清理数据库；

因此旧请求会被前端和后端两层拒绝。

## 5. 缓存版本控制

缓存包含：
- `version: 2`
- 根级 `updatedAt`
- 每个模式自己的 `updatedAt`
- `clearedAt`

如果以后升级数据结构，只需要把 `SCHEMA_VERSION` 改成 3，并在 `loadState()` 中添加迁移逻辑。当前实现遇到版本不一致会安全创建新结构，避免旧缓存导致 Vue 页面报错。

## 6. 潜在问题与当前处理

### 模式切换时 AI 仍在回复
请求绑定发送时的 mode 和 sessionId。即使用户已经切到另一模式，回复也只会写入原模式，不会串台。

### 清空与 AI 回复同时发生
清空会立即旋转 sessionId，并提高 requestGeneration。旧回复到达后会被丢弃；后端也会再次检查 session 是否仍有效。

### 首次加载历史与首次发送同时发生
`hydrationPromiseByMode` 保证发送会等待该模式初始化完成，避免同时创建 session 的竞态。

### localStorage 太大
每个模式最多保留 200 条消息，防止无限增长。若未来聊天量明显增大，建议改用 IndexedDB 或后端分页。

### localStorage 被用户手动清空
会生成新的 sessionId，因此旧后端聊天不会自动重新出现。若以后需要“跨浏览器恢复”，应把当前 sessionId 映射完全交给后端账户体系管理。

### 网络异常时点击清空
UI 和 localStorage 会立即清空并换新 sessionId。即使后端请求失败，旧记录也不会在当前浏览器重新显示；页面会提示后端同步失败，可再次点击清空进行同步。

## 7. 运行

前端：
```bash
cd ai-english-assistant
npm install
npm run dev
```

后端：启动 Spring Boot 项目。

原项目配置使用 Hibernate 自动更新数据库时，会自动新增 `session_id` 字段和 `chat_session_state` 表。
