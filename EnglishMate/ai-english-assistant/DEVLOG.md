# AI英语学习助手开发日志

## Day 1

### 今日完成
- 安装 Node.js 和 VS Code
- 创建 Vue 3 项目
- 配置 Vue Router
- 完成 AI 英语学习助手首页
- 使用 Vue v-for 实现功能卡片动态渲染
- 完成基础响应式页面布局

### 今日学习
- Vue 单文件组件基本结构
- template、script、style 的作用
- v-for 数据循环
- Vue 数据驱动页面的基本思想

### 遇到的问题
- 暂无

### 下一步
- 创建 AI 对话页面
- 配置 Vue Router 页面跳转
- 实现用户输入消息和模拟 AI 回复

## Day 2
一个 .vue 文件通常由三部分组成：
<script setup>  控制消息发送、模拟回复等功能
  JavaScript 逻辑
</script>

<template>  决定页面上显示什么
  页面结构
</template>

<style scoped>  决定页面好不好看
页面样式
</style>

## Day 3

### 今日完成

- 启动 Spring Boot 后端项目
- 创建 ChatController
- 实现 POST /api/chat 接口
- 前端安装 Axios
- 封装聊天接口请求
- 使用 Axios 完成 Vue 与 Spring Boot 数据交互
- 将原前端模拟 AI 回复改为后端返回
- 完成前后端接口联调
- 添加接口异常处理

### 今日学习

- 前后端分离基本结构
- HTTP POST 请求
- JSON 数据交互
- RestController
- RequestMapping
- PostMapping
- RequestBody
- Axios
- 跨域 CORS
- try / catch / finally

### 当前数据流程

Vue ChatView
→ Axios
→ Spring Boot ChatController
→ 返回 JSON
→ Vue 更新聊天消息

### 下一步

- 接入真实大语言模型 API
- 为 AI 设置英语教师角色
- 实现真实英语对话

## Day 4

### 今日完成

- 申请并配置 DeepSeek API Key
- 使用环境变量保护 API Key
- 在 Spring Boot 中配置 DeepSeek API
- 创建 DeepSeekService 业务层
- 使用 Java HttpClient 调用大语言模型接口
- 使用 Jackson 解析 DeepSeek JSON 返回结果
- 将 ChatController 与 DeepSeekService 连接
- 将临时字符串回复替换为真实 AI 回复
- 实现 Vue → Spring Boot → DeepSeek → Vue 完整调用链
- 添加 AI 服务异常处理

### 今日学习

- 大语言模型 API 基础
- API Key 与 Bearer Token
- Spring Boot Service 层
- @Service
- @Value
- Java HttpClient
- JSON 请求与响应
- System Prompt
- 大模型调用的安全架构

### 当前项目流程

用户输入英语
→ Vue ChatView
→ Axios
→ Spring Boot ChatController
→ DeepSeekService
→ DeepSeek API
→ AI生成回复
→ Spring Boot解析JSON
→ Vue显示AI回答

### 当前不足

- 每次只发送当前问题
- AI暂时不能记住之前的聊天
- 英语老师 Prompt 还比较简单
- 聊天记录刷新后仍会消失

### 下一步

- 实现多轮上下文对话
- 优化英语教师 Prompt
- 自动发现用户英语语法错误
- 给出自然表达建议
- 设置 CET-4 / CET-6 学习模式

## Day 5

### 今日完成
- 将单轮AI请求升级为多轮上下文对话
- 前端发送最近聊天历史至Spring Boot
- 创建ChatMessage与ChatRequest DTO
- 后端根据聊天历史构建DeepSeek messages
- 实现AI上下文记忆
- 设计EnglishMate英语教师System Prompt
- 实现英语语法与表达自动纠错
- 实现自然表达推荐
- 添加自由对话/CET-4/CET-6三种学习模式
- 根据学习模式动态生成System Prompt
- 限制上下文为最近12条消息以控制Token消耗

### 今日学习
- 大语言模型无状态API
- 多轮对话上下文拼接
- user / assistant / system角色
- DTO数据传输对象
- Prompt Engineering
- 动态System Prompt
- 上下文长度与Token成本

### 当前项目流程
用户选择学习模式
→ 输入英语
→ Vue保存会话历史
→ Axios发送mode和messages
→ Spring Boot解析ChatRequest
→ 根据mode生成System Prompt
→ 拼接历史对话
→ DeepSeek生成回复
→ 自动纠错及继续提问
→ Vue展示回答

### 下一步
- 开发AI作文批改页面
- 支持作文评分
- 提取语法问题
- 提供逐项修改建议
- 生成优化后的作文

# EnglishMate 开发日志


## 项目简介

EnglishMate 是一个基于大语言模型的 AI 英语学习助手，旨在帮助用户进行英语口语练习、考试专项训练以及英语作文智能批改。

项目采用前后端分离架构，通过 Vue3 构建用户交互界面，Spring Boot 提供后端接口服务，并接入 DeepSeek 大语言模型实现智能英语辅导功能。


---

# Day 5 - Day 6 开发记录


## 已完成功能

### 1. AI英语陪练功能

- 完成 Vue3 聊天交互页面开发。
- 实现用户与 AI 英语教师的多轮对话。
- 支持 CET-4、CET-6 以及自由对话模式切换。
- 后端通过 Spring Boot 接收用户消息，并调用 DeepSeek API 返回 AI 回复。


### 2. 前后端接口设计

完成前后端通信流程：
