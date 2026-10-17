# 本次所有修改源码（完整文件）
> 以下不是 Diff；每个代码块都是最终 ZIP 中对应文件的完整内容。

## `ai-english-assistant/src/api/chat.js`

```javascript
import request from '../utils/request'

export const getSessions = () => request.get('/chat/sessions')
export const createSession = data => request.post('/chat/sessions', data)
export const deleteSession = id => request.delete(`/chat/sessions/${id}`)
export const getMessages = id => request.get(`/chat/sessions/${id}/messages`)

function clearExpiredLogin() {
  localStorage.removeItem('englishmate.token')
  localStorage.removeItem('englishmate.user')
  if (location.pathname !== '/login') location.href = '/login'
}

async function readError(response) {
  const text = await response.text()
  try {
    const parsed = JSON.parse(text)
    return {
      code: parsed.code || `HTTP_${response.status}`,
      message: parsed.message || `请求失败（${response.status}）`
    }
  } catch {
    return {
      code: `HTTP_${response.status}`,
      message: text || `请求失败（${response.status}）`
    }
  }
}

export async function streamMessage(sessionId, payload, onChunk, options = {}) {
  const token = localStorage.getItem('englishmate.token')
  const base = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
  const response = await fetch(`${base}/chat/sessions/${sessionId}/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload),
    signal: options.signal
  })

  if (response.status === 401) {
    clearExpiredLogin()
    const error = new Error('登录已过期，请重新登录')
    error.status = 401
    error.code = 'UNAUTHORIZED'
    throw error
  }

  if (!response.ok) {
    const detail = await readError(response)
    const error = new Error(detail.message)
    error.status = response.status
    error.code = detail.code
    throw error
  }

  if (!response.body) {
    const error = new Error('浏览器未收到流式响应')
    error.code = 'STREAM_UNAVAILABLE'
    throw error
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const consumeEvent = eventText => {
    const lines = eventText.split(/\r?\n/)
    const event = (lines.find(line => line.startsWith('event:')) || '').slice(6).trim()
    const data = lines
      .filter(line => line.startsWith('data:'))
      .map(line => line.slice(5).trimStart())
      .join('\n')

    if (event === 'chunk' && data) {
      onChunk(data)
      return
    }

    if (event === 'error') {
      let detail = { code: 'AI_SERVICE_ERROR', message: data || 'AI 服务暂时不可用' }
      try {
        detail = { ...detail, ...JSON.parse(data) }
      } catch {
        // 服务端错误不是 JSON 时直接显示原始文本。
      }
      const error = new Error(detail.message)
      error.code = detail.code
      throw error
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''
    for (const eventText of events) consumeEvent(eventText)
  }

  buffer += decoder.decode()
  if (buffer.trim()) consumeEvent(buffer)
}

```

## `ai-english-assistant/src/api/essay.js`

```javascript
import request from '../utils/request'

export const checkEssay = data => request.post('/essay/check', data)
export const getEssayRecords = userId => request.get('/essay/records', { params: { userId } })
export const deleteEssayRecord = id => request.delete(`/essay/records/${id}`)

```

## `ai-english-assistant/src/api/mistakes.js`

```javascript
import request from '../utils/request'

export const getMistakes = userId => request.get('/mistakes', { params: { userId } })
export const addMistake = data => request.post('/mistakes', data)
export const setMistakeMastered = (id, value) => request.put(`/mistakes/${id}/mastered`, null, { params: { value } })
export const deleteMistake = id => request.delete(`/mistakes/${id}`)

```

## `ai-english-assistant/src/views/ChatView.vue`

```vue
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import {
  createSession,
  deleteSession,
  getMessages,
  getSessions,
  streamMessage
} from '../api/chat'

const MAX_MESSAGE_LENGTH = 4000

const sessions = ref([])
const activeId = ref('')
const messages = ref([])
const inputValue = ref('')
const isLoading = ref(false)
const error = ref('')
const mode = ref('free')
const chatBox = ref(null)
const currentStream = ref(null)
const lastFailed = ref(null)
let openSequence = 0

const active = computed(() => sessions.value.find(session => session.id === activeId.value))
const activeGenerating = computed(
  () => isLoading.value && currentStream.value?.sessionId === activeId.value
)
const canRetry = computed(
  () => lastFailed.value && lastFailed.value.sessionId === activeId.value && !isLoading.value
)

function createRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, char => {
    const random = Math.random() * 16 | 0
    const value = char === 'x' ? random : (random & 0x3) | 0x8
    return value.toString(16)
  })
}

async function scrollToBottom() {
  await nextTick()
  if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight
}

function abortCurrentStream() {
  const stream = currentStream.value
  if (!stream) return
  stream.controller.abort()
  currentStream.value = null
  isLoading.value = false
}

async function refreshSessions() {
  const response = await getSessions()
  sessions.value = Array.isArray(response.data) ? response.data : []
}

async function loadSessions() {
  await refreshSessions()
  if (sessions.value.length > 0) {
    await openSession(sessions.value[0].id)
  } else {
    activeId.value = ''
    messages.value = []
  }
}

async function newSession() {
  if (isLoading.value) return
  abortCurrentStream()
  error.value = ''
  lastFailed.value = null
  try {
    const response = await createSession({ mode: mode.value, title: '新对话' })
    sessions.value.unshift(response.data)
    await openSession(response.data.id)
  } catch (e) {
    error.value = e.response?.data?.message || '创建会话失败，请稍后重试'
  }
}

async function openSession(id) {
  if (!id) return
  if (currentStream.value?.sessionId !== id) abortCurrentStream()

  const sequence = ++openSequence
  activeId.value = id
  error.value = ''
  lastFailed.value = null

  try {
    const response = await getMessages(id)
    if (sequence !== openSequence || activeId.value !== id) return
    messages.value = (Array.isArray(response.data) ? response.data : []).map(item => ({
      id: item.id,
      role: item.role,
      content: item.content
    }))
    await scrollToBottom()
  } catch (e) {
    if (sequence !== openSequence) return
    error.value = e.response?.data?.message || '聊天记录加载失败，请重试'
  }
}

async function removeSession(id) {
  if (!confirm('确定删除这个会话及全部聊天记录吗？')) return

  if (currentStream.value?.sessionId === id) abortCurrentStream()
  error.value = ''
  lastFailed.value = null

  try {
    await deleteSession(id)
    sessions.value = sessions.value.filter(session => session.id !== id)

    if (activeId.value === id) {
      ++openSequence
      activeId.value = ''
      messages.value = []
      if (sessions.value.length) await openSession(sessions.value[0].id)
    }
  } catch (e) {
    error.value = e.response?.data?.message || '删除会话失败，请稍后重试'
  }
}

function toErrorMessage(e) {
  if (e.code === 'AI_RATE_LIMITED') return '请求太频繁：每个用户每分钟最多 10 次 AI 请求。'
  if (e.code === 'AI_PROVIDER_RATE_LIMITED') return 'AI 服务当前繁忙，请稍后再试。'
  if (e.code === 'AI_TIMEOUT') return 'AI 响应超时，可以点击“重试本条”。'
  if (e.code === 'CHAT_CONTEXT_TOO_LARGE') return '当前对话太长，请新建会话后继续。'
  if (e.code === 'CHAT_GENERATION_IN_PROGRESS') return '当前会话已有一条消息正在生成，请稍候。'
  if (e.code === 'AI_CONFIGURATION_ERROR') return 'AI 服务配置异常，请联系管理员检查服务端配置。'
  if (e.status === 401) return '登录已过期，请重新登录。'
  return e.message || 'AI 请求失败，请稍后重试。'
}

async function ensureSession() {
  if (activeId.value) return activeId.value
  const response = await createSession({ mode: mode.value, title: '新对话' })
  sessions.value.unshift(response.data)
  activeId.value = response.data.id
  messages.value = []
  return response.data.id
}

async function sendRequest(text, stableRequestId, appendUser) {
  if (isLoading.value) return

  let sessionId
  try {
    sessionId = await ensureSession()
  } catch (e) {
    error.value = e.response?.data?.message || '创建会话失败，请稍后重试'
    return
  }
  if (!sessionId) return

  if (appendUser) messages.value.push({ role: 'user', content: text })

  const assistantMessage = { role: 'assistant', content: '' }
  messages.value.push(assistantMessage)

  const controller = new AbortController()
  const streamState = { controller, sessionId, requestId: stableRequestId }
  currentStream.value = streamState
  isLoading.value = true
  error.value = ''
  await scrollToBottom()

  try {
    await streamMessage(
      sessionId,
      { message: text, requestId: stableRequestId },
      chunk => {
        assistantMessage.content += chunk
        if (activeId.value === sessionId) scrollToBottom()
      },
      { signal: controller.signal }
    )

    lastFailed.value = null
    await refreshSessions()
  } catch (e) {
    const aborted = e?.name === 'AbortError'
    const index = messages.value.indexOf(assistantMessage)
    if (index >= 0) messages.value.splice(index, 1)

    if (!aborted && activeId.value === sessionId) {
      error.value = toErrorMessage(e)
      lastFailed.value = { sessionId, text, requestId: stableRequestId }
    }
  } finally {
    if (currentStream.value === streamState) {
      currentStream.value = null
      isLoading.value = false
    }
    if (activeId.value === sessionId) await scrollToBottom()
  }
}

async function send() {
  const text = inputValue.value.trim()
  if (!text || isLoading.value) return
  if (text.length > MAX_MESSAGE_LENGTH) {
    error.value = `单条消息不能超过 ${MAX_MESSAGE_LENGTH} 个字符。`
    return
  }

  inputValue.value = ''
  lastFailed.value = null
  await sendRequest(text, createRequestId(), true)
}

async function retryLast() {
  const failed = lastFailed.value
  if (!failed || failed.sessionId !== activeId.value || isLoading.value) return
  lastFailed.value = null
  await sendRequest(failed.text, failed.requestId, false)
}

function handleEnter(event) {
  if (event.shiftKey) return
  send()
}

onMounted(async () => {
  try {
    await loadSessions()
  } catch (e) {
    error.value = e.response?.data?.message || '聊天数据加载失败'
  }
})

onBeforeUnmount(() => abortCurrentStream())
</script>

<template>
  <div class="page">
    <AppHeader />
    <main class="layout">
      <aside class="side">
        <div class="side-top">
          <select v-model="mode" :disabled="isLoading">
            <option value="free">自由对话</option>
            <option value="cet4">CET-4</option>
            <option value="cet6">CET-6</option>
          </select>
          <button type="button" :disabled="isLoading" @click="newSession">＋ 新建会话</button>
        </div>

        <div v-if="!sessions.length" class="side-empty">当前账号暂无会话</div>

        <div
          v-for="session in sessions"
          :key="session.id"
          class="session"
          :class="{ on: session.id === activeId }"
          @click="openSession(session.id)"
        >
          <span>
            <b>{{ session.title }}</b>
            <small>{{ session.mode.toUpperCase() }}</small>
          </span>
          <button type="button" aria-label="删除会话" @click.stop="removeSession(session.id)">×</button>
        </div>
      </aside>

      <section class="chat">
        <header>
          <div>
            <small>{{ active?.mode?.toUpperCase() || 'ENGLISHMATE' }}</small>
            <h1>{{ active?.title || 'AI 英语助手' }}</h1>
          </div>
          <span v-if="activeGenerating">AI 正在生成…</span>
        </header>

        <div ref="chatBox" class="messages">
          <div v-if="!messages.length" class="empty">
            <h2>开始一段新的英语练习</h2>
            <p>当前账号暂无聊天记录。发送第一条消息后，会话和回复才会写入数据库。</p>
          </div>

          <div
            v-for="(message, index) in messages"
            :key="message.id || `${message.role}-${index}`"
            :class="['msg', message.role]"
          >
            <div>{{ message.content || (activeGenerating && index === messages.length - 1 ? '▍' : '') }}</div>
          </div>
        </div>

        <div v-if="error" class="error">
          <span>{{ error }}</span>
          <button v-if="canRetry" type="button" @click="retryLast">重试本条</button>
        </div>

        <footer>
          <div class="composer">
            <textarea
              v-model="inputValue"
              :maxlength="MAX_MESSAGE_LENGTH"
              placeholder="输入英语问题或练习内容…"
              @keydown.enter.exact.prevent
              @keyup.enter.exact="handleEnter"
            ></textarea>
            <small>{{ inputValue.length }}/{{ MAX_MESSAGE_LENGTH }}</small>
          </div>
          <button
            type="button"
            :disabled="!inputValue.trim() || isLoading"
            @click="send"
          >
            {{ isLoading ? '生成中…' : '发送' }}
          </button>
        </footer>
      </section>
    </main>
  </div>
</template>

<style scoped>
*{box-sizing:border-box}.page{min-height:100vh;background:#f7f8fc;color:#252b3d}.layout{width:min(1280px,calc(100% - 32px));height:calc(100vh - 96px);margin:12px auto;display:grid;grid-template-columns:280px 1fr;gap:14px}.side,.chat{background:white;border:1px solid #e6e8f1;border-radius:22px;overflow:hidden}.side{padding:14px;overflow:auto}.side-top{display:grid;gap:8px;margin-bottom:12px}.side-top select,.side-top button{padding:11px;border:1px solid #e1e4ef;border-radius:12px;background:#f8f9fd}.side-top button{background:#5b5cf0;color:white;border:0;cursor:pointer}.side-top button:disabled,.side-top select:disabled{opacity:.55;cursor:not-allowed}.side-empty{padding:18px 10px;color:#9aa1b2;text-align:center;font-size:12px}.session{display:flex;justify-content:space-between;align-items:center;padding:11px;border-radius:12px;cursor:pointer;margin:4px 0}.session:hover,.session.on{background:#f0f1ff}.session span{min-width:0}.session b,.session small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.session b{font-size:13px}.session small{margin-top:4px;color:#949bad;font-size:9px}.session>button{border:0;background:transparent;color:#9aa1b2;font-size:18px}.chat{display:grid;grid-template-rows:auto 1fr auto auto}.chat header{padding:18px 22px;border-bottom:1px solid #eceef5;display:flex;justify-content:space-between;align-items:center}.chat h1{margin:3px 0 0;font-size:20px}.chat header small,.chat header span{color:#7778e8}.messages{overflow:auto;padding:24px}.empty{text-align:center;color:#8a91a3;margin-top:15vh}.msg{display:flex;margin:12px 0}.msg>div{max-width:min(720px,85%);padding:12px 15px;border-radius:16px;line-height:1.75;white-space:pre-wrap;overflow-wrap:anywhere}.msg.user{justify-content:flex-end}.msg.user>div{background:#5b5cf0;color:white;border-bottom-right-radius:5px}.msg.assistant>div{background:#f3f5fa;border-bottom-left-radius:5px}.error{display:flex;justify-content:space-between;gap:12px;align-items:center;margin:0 18px 8px;padding:10px 12px;border-radius:10px;background:#fff1f3;color:#b42348;font-size:12px}.error button{border:0;border-radius:8px;padding:7px 10px;background:#ffe1e6;color:#a72d45;cursor:pointer}.chat footer{border-top:1px solid #eceef5;padding:12px 14px;display:grid;grid-template-columns:1fr auto;gap:10px}.composer{position:relative}.composer textarea{width:100%;height:72px;resize:none;border:1px solid #dfe3ee;border-radius:13px;padding:12px 14px 24px;outline:none;line-height:1.5}.composer textarea:focus{border-color:#7778ee;box-shadow:0 0 0 3px rgba(91,92,240,.08)}.composer small{position:absolute;right:10px;bottom:7px;color:#a0a6b4;font-size:9px}.chat footer>button{min-width:84px;border:0;border-radius:13px;background:#5b5cf0;color:white;font-weight:800;cursor:pointer}.chat footer>button:disabled{background:#b6b8ef;cursor:not-allowed;opacity:.72}@media(max-width:780px){.layout{grid-template-columns:1fr;height:auto}.side{max-height:260px}.chat{min-height:70vh}}
</style>

```

## `ai-english-assistant/src/views/EssayView.vue`

```vue
<script setup>
import { computed, onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import { me } from '../api/auth'
import { checkEssay, getEssayRecords } from '../api/essay'
import { addMistake } from '../api/mistakes'

const savedNotice = ref('')
const records = ref([])
const recordsLoading = ref(false)
const recordsError = ref('')
const currentUserId = ref(null)
const essay = ref('')
const level = ref('cet4')
const result = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const copied = ref(false)
const canRetry = ref(false)
const lastSubmission = ref(null)
const currentRecordId = ref(null)
const savingMistakes = ref({})
const savedMistakes = ref({})

const wordCount = computed(() => essay.value.trim() ? essay.value.trim().split(/\s+/).filter(Boolean).length : 0)
const levelName = computed(() => level.value === 'cet6' ? 'CET-6 六级' : 'CET-4 四级')
const canSubmit = computed(() => !loading.value && wordCount.value >= 20 && essay.value.trim().length <= 10000)

async function ensureCurrentUserId() {
  if (currentUserId.value) return currentUserId.value
  const response = await me()
  const id = Number(response.data?.id)
  if (!Number.isInteger(id) || id <= 0) throw new Error('CURRENT_USER_INVALID')
  currentUserId.value = id
  return id
}

function cleanText(value) {
  if (value == null) return ''
  return String(value)
    .replace(/```(?:json)?/gi, '')
    .replace(/\*\*/g, '')
    .replace(/^#{1,6}\s*/gm, '')
    .replace(/^---+$/gm, '')
    .trim()
}

function normalizeResult(raw) {
  let data = raw?.result ?? raw
  if (typeof data === 'string') {
    const cleaned = data.replace(/```json|```/gi, '').trim()
    data = JSON.parse(cleaned)
  }
  if (data?.data && typeof data.data === 'object') data = data.data

  const score = Number(data?.score)
  if (!Number.isFinite(score) || score < 0 || score > 100) throw new Error('AI_RETURN_FORMAT')
  if (!Array.isArray(data?.strengths) || !Array.isArray(data?.errors) || !Array.isArray(data?.suggestions)) {
    throw new Error('AI_RETURN_FORMAT')
  }
  if (typeof data?.improvedEssay !== 'string' || !data.improvedEssay.trim()) throw new Error('AI_RETURN_FORMAT')

  const errors = data.errors.map(item => {
    const original = cleanText(item?.original)
    const correct = cleanText(item?.correct)
    const problem = cleanText(item?.problem)
    if (!original || !correct || !problem) throw new Error('AI_RETURN_FORMAT')
    return { original, correct, problem }
  })

  return {
    score,
    strengths: data.strengths.map(cleanText).filter(Boolean),
    errors,
    suggestions: data.suggestions.map(cleanText).filter(Boolean),
    improvedEssay: cleanText(data.improvedEssay)
  }
}

function newRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, char => {
    const random = Math.random() * 16 | 0
    const value = char === 'x' ? random : (random & 0x3) | 0x8
    return value.toString(16)
  })
}

function errorText(error) {
  const code = error?.response?.data?.code
  const message = error?.response?.data?.message
  canRetry.value = [
    'AI_TIMEOUT',
    'AI_RATE_LIMITED',
    'AI_PROVIDER_RATE_LIMITED',
    'AI_SERVICE_ERROR',
    'AI_INVALID_RESPONSE',
    'ESSAY_GENERATION_IN_PROGRESS'
  ].includes(code)
  if (message) return message
  if (error?.code === 'ECONNABORTED') {
    canRetry.value = true
    return 'AI 批改超时，请稍后重试。'
  }
  if (!error?.response) {
    canRetry.value = true
    return '无法连接后端服务，请确认网络和 Spring Boot 服务状态。'
  }
  return '作文批改失败，请稍后重新尝试。'
}

async function loadRecords(showLoading = true) {
  if (showLoading) recordsLoading.value = true
  recordsError.value = ''
  try {
    const userId = await ensureCurrentUserId()
    const response = await getEssayRecords(userId)
    records.value = Array.isArray(response.data) ? response.data : []
  } catch (error) {
    records.value = []
    recordsError.value = error?.response?.data?.message || '作文记录加载失败，请稍后重试。'
  } finally {
    if (showLoading) recordsLoading.value = false
  }
}

async function performCheck(payload) {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  canRetry.value = false
  result.value = null
  currentRecordId.value = null
  copied.value = false
  try {
    const response = await checkEssay(payload)
    result.value = normalizeResult(response.data)
    currentRecordId.value = Number(response.data?.recordId) || null
    if (!currentRecordId.value) throw new Error('ESSAY_RECORD_NOT_SAVED')
    canRetry.value = false
    await loadRecords(false)
  } catch (error) {
    console.error('作文批改请求失败：', error)
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function submitEssay() {
  const content = essay.value.trim()
  if (!content) {
    errorMessage.value = '请先输入需要批改的英语作文。'
    return
  }
  if (wordCount.value < 20) {
    errorMessage.value = '作文内容过短，至少输入 20 个英文单词后再进行批改。'
    return
  }
  if (content.length > 10000) {
    errorMessage.value = '作文内容过长，请控制在 10000 个字符以内。'
    return
  }
  const payload = { essay: content, level: level.value, requestId: newRequestId() }
  lastSubmission.value = payload
  await performCheck(payload)
}

async function retryEssay() {
  if (!lastSubmission.value || loading.value) return
  await performCheck(lastSubmission.value)
}

async function saveMistake(item, index) {
  if (!currentRecordId.value) {
    savedNotice.value = '当前批改结果尚未完成数据库保存，不能加入错题本。'
    return
  }

  const key = `${currentRecordId.value}:${index}`
  if (savingMistakes.value[key] || savedMistakes.value[key]) return

  savingMistakes.value = { ...savingMistakes.value, [key]: true }
  try {
    await addMistake({
      essayRecordId: currentRecordId.value,
      originalText: item.original,
      correctedText: item.correct,
      explanation: item.problem,
      source: 'essay'
    })
    savedMistakes.value = { ...savedMistakes.value, [key]: true }
    savedNotice.value = '已保存到错题本。'
  } catch (error) {
    savedNotice.value = error?.response?.data?.message || '保存错题失败，请稍后重试。'
  } finally {
    const next = { ...savingMistakes.value }
    delete next[key]
    savingMistakes.value = next
    setTimeout(() => { savedNotice.value = '' }, 1800)
  }
}

function openRecord(record) {
  essay.value = record.originalContent
  level.value = record.level
  currentRecordId.value = record.id
  errorMessage.value = ''
  canRetry.value = false
  try {
    result.value = normalizeResult(record.resultJson)
  } catch {
    result.value = null
    errorMessage.value = '这条历史批改记录格式异常，无法展示。'
  }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function resetEssay() {
  essay.value = ''
  result.value = null
  currentRecordId.value = null
  errorMessage.value = ''
  canRetry.value = false
  lastSubmission.value = null
  copied.value = false
}

async function copyImprovedEssay() {
  if (!result.value?.improvedEssay) return
  try {
    await navigator.clipboard.writeText(result.value.improvedEssay)
    copied.value = true
    setTimeout(() => { copied.value = false }, 1600)
  } catch {
    errorMessage.value = '复制失败，请手动选择优化作文内容。'
  }
}

onMounted(() => loadRecords(true))
</script>

<template>
  <div class="app-shell essay-page">
    <AppHeader />
    <main class="page-wrap essay-wrap">
      <section class="essay-hero glass-card">
        <div>
          <p class="page-kicker">AI WRITING COACH</p>
          <h1>把每一次作文，变成一次真正的提升</h1>
          <p>提交后由 DeepSeek 完成批改；只有批改成功并保存到 MySQL 的记录才会出现在历史列表。</p>
        </div>
        <div class="hero-chip"><span>✦</span><div><small>当前模式</small><strong>{{ levelName }}</strong></div></div>
      </section>

      <div class="workspace">
        <section class="editor-card glass-card">
          <div class="card-header">
            <div><p class="card-label">YOUR ESSAY</p><h2>提交作文</h2></div>
            <span class="word-counter" :class="{ready:wordCount>=20}">{{ wordCount }} words</span>
          </div>

          <label class="field-label" for="level">考试类型</label>
          <select id="level" v-model="level" class="control select-control" :disabled="loading">
            <option value="cet4">CET-4 四级</option>
            <option value="cet6">CET-6 六级</option>
          </select>

          <div class="writing-field">
            <label class="field-label" for="essay">英语作文</label>
            <textarea id="essay" v-model="essay" maxlength="10000" class="control essay-input" :disabled="loading" placeholder="Paste or write your essay here...\n\n建议输入 120 - 250 词，AI 会给出更完整的分析。"></textarea>
            <div class="input-meta"><span>{{ wordCount < 20 ? `还需 ${20-wordCount} 词即可批改` : '✓ 已达到批改字数' }}</span><span>{{ levelName }}</span></div>
          </div>

          <div v-if="errorMessage" class="error-message">
            {{ errorMessage }}
            <button v-if="canRetry" class="copy-btn" type="button" :disabled="loading" @click="retryEssay">重试本次批改</button>
          </div>
          <div class="actions">
            <button class="secondary-btn" type="button" :disabled="loading" @click="resetEssay">清空</button>
            <button class="primary-btn submit-btn" type="button" :disabled="!canSubmit" @click="submitEssay">
              <span v-if="!loading">✦ 开始 AI 批改</span>
              <span v-else>AI 正在阅读作文…</span>
            </button>
          </div>
        </section>

        <aside class="tips-card glass-card">
          <p class="card-label">HOW IT WORKS</p>
          <h2>这次批改，你会得到什么？</h2>
          <div class="tip-item"><span>01</span><div><strong>综合评分</strong><p>从准确性、表达、结构与词汇给出 0 - 100 分。</p></div></div>
          <div class="tip-item"><span>02</span><div><strong>逐句纠错</strong><p>原句、推荐写法、问题原因放在一起，更容易看懂。</p></div></div>
          <div class="tip-item"><span>03</span><div><strong>下一步练什么</strong><p>批改建议直接来自本次 AI 返回并写入数据库。</p></div></div>
          <div class="tip-item"><span>04</span><div><strong>优化作文</strong><p>优化作文来自本次 AI 返回，不使用前端占位内容。</p></div></div>
        </aside>
      </div>

      <section class="glass-card" style="margin-top:18px;padding:22px;border-radius:22px">
        <div class="section-head"><span class="section-icon essay-icon">H</span><div><h3>最近作文记录</h3><p>只显示当前登录账号在 MySQL 中的有效记录</p></div></div>
        <div v-if="recordsLoading" class="empty-text" style="margin-top:16px">正在读取作文记录…</div>
        <div v-else-if="recordsError" class="error-message">
          {{ recordsError }}
          <button type="button" class="copy-btn" @click="loadRecords(true)">重新加载</button>
        </div>
        <div v-else-if="records.length" style="display:grid;gap:8px;margin-top:14px">
          <button v-for="r in records.slice(0,6)" :key="r.id" class="copy-btn" style="text-align:left;padding:12px" @click="openRecord(r)">
            {{ r.level.toUpperCase() }} · {{ r.score }} 分 · {{ r.originalContent.slice(0,55) }}{{ r.originalContent.length>55?'…':'' }}
          </button>
        </div>
        <div v-else class="empty-text" style="margin-top:16px">暂无作文记录</div>
      </section>

      <section v-if="loading" class="loading-card glass-card">
        <div class="loader-orbit"><i></i><span>AI</span></div>
        <div><h3>正在逐句分析你的作文</h3><p>批改期间按钮已锁定，完成并保存后才会刷新历史记录。</p></div>
      </section>

      <p v-if="savedNotice" class="error-message">{{ savedNotice }}</p>

      <section v-if="result && !loading" class="result-section fade-up">
        <div class="result-overview glass-card">
          <div class="score-ring" :style="{'--score': result.score * 3.6 + 'deg'}"><div><strong>{{ result.score }}</strong><span>/ 100</span></div></div>
          <div class="result-summary"><p class="page-kicker">AI ANALYSIS</p><h2>本次 AI 批改结果</h2><p>以下内容来自本次 DeepSeek 返回并已保存到当前账号的作文记录。</p></div>
          <div class="mini-stats"><div><strong>{{ result.errors.length }}</strong><span>待修改</span></div><div><strong>{{ result.strengths.length }}</strong><span>亮点</span></div><div><strong>{{ result.suggestions.length }}</strong><span>建议</span></div></div>
        </div>

        <div class="result-grid">
          <section class="result-card glass-card strengths-card">
            <div class="section-head"><span class="section-icon success">✓</span><div><h3>做得不错</h3><p>AI 返回的优点</p></div></div>
            <div v-if="result.strengths.length" class="pill-list"><div v-for="(item,index) in result.strengths" :key="index"><span>✦</span>{{ item }}</div></div>
            <p v-else class="empty-text">本次批改没有返回优点条目。</p>
          </section>

          <section class="result-card glass-card suggestion-card">
            <div class="section-head"><span class="section-icon idea">↗</span><div><h3>下一步这样练</h3><p>AI 返回的练习建议</p></div></div>
            <ol v-if="result.suggestions.length" class="suggestion-list"><li v-for="(item,index) in result.suggestions" :key="index"><span>{{ String(index+1).padStart(2,'0') }}</span><p>{{ item }}</p></li></ol>
            <p v-else class="empty-text">本次批改没有返回建议条目。</p>
          </section>
        </div>

        <section class="corrections-card glass-card">
          <div class="section-head corrections-head"><span class="section-icon warning">!</span><div><h3>逐句修改</h3><p>{{ result.errors.length ? `本次返回 ${result.errors.length} 条修改项` : '本次没有返回修改项' }}</p></div></div>
          <div v-if="result.errors.length" class="correction-list">
            <article v-for="(item,index) in result.errors" :key="index" class="correction-item">
              <div class="correction-number">{{ String(index+1).padStart(2,'0') }}</div>
              <div class="correction-body">
                <div class="compare-row original"><span>原句</span><p>{{ item.original }}</p></div>
                <div class="arrow-line"><span>↓</span></div>
                <div class="compare-row corrected"><span>推荐</span><p>{{ item.correct }}</p></div>
                <div class="explain"><span>WHY</span><p>{{ item.problem }}</p></div>
                <button
                  class="copy-btn"
                  style="margin-top:8px"
                  :disabled="!currentRecordId || savingMistakes[`${currentRecordId}:${index}`] || savedMistakes[`${currentRecordId}:${index}`]"
                  @click="saveMistake(item,index)"
                >
                  {{ savedMistakes[`${currentRecordId}:${index}`] ? '✓ 已加入错题本' : savingMistakes[`${currentRecordId}:${index}`] ? '保存中…' : '＋ 加入错题本' }}
                </button>
              </div>
            </article>
          </div>
          <div v-else class="perfect-state"><span>✓</span><div><strong>没有逐句修改项</strong><p>这是本次 AI 返回的真实结果。</p></div></div>
        </section>

        <section class="improved-card glass-card">
          <div class="improved-head"><div class="section-head"><span class="section-icon essay-icon">A</span><div><h3>优化后的作文</h3><p>来自本次 AI 批改结果</p></div></div><button class="copy-btn" type="button" @click="copyImprovedEssay">{{ copied ? '✓ 已复制' : '复制范文' }}</button></div>
          <div class="improved-paper">{{ result.improvedEssay }}</div>
        </section>
      </section>
    </main>
  </div>
</template>

<style scoped>
.essay-wrap{padding-top:34px}.essay-hero{border-radius:28px;padding:32px 36px;display:flex;align-items:center;justify-content:space-between;gap:30px;background:linear-gradient(135deg,rgba(255,255,255,.96),rgba(242,245,255,.84));margin-bottom:18px}.essay-hero h1{margin:0;font-size:clamp(30px,4vw,44px);letter-spacing:-.045em;line-height:1.18}.essay-hero>div>p:last-child{max-width:720px;margin:12px 0 0;color:var(--muted);line-height:1.75}.hero-chip{flex:0 0 auto;display:flex;align-items:center;gap:12px;padding:13px 16px;border:1px solid var(--line);background:rgba(255,255,255,.72);border-radius:16px}.hero-chip>span{display:grid;place-items:center;width:34px;height:34px;border-radius:12px;color:white;background:linear-gradient(135deg,#5b5cf0,#7f6df6)}.hero-chip small,.hero-chip strong{display:block}.hero-chip small{font-size:10px;color:var(--muted-2);margin-bottom:2px}.hero-chip strong{font-size:13px}.workspace{display:grid;grid-template-columns:minmax(0,1.65fr) minmax(280px,.75fr);gap:18px;align-items:start}.editor-card,.tips-card{border-radius:24px}.editor-card{padding:28px}.tips-card{padding:26px}.card-header{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:22px}.card-label{margin:0 0 6px;color:var(--primary);font-size:10px;font-weight:850;letter-spacing:.12em}.card-header h2,.tips-card h2{margin:0;font-size:22px;letter-spacing:-.025em}.word-counter{padding:7px 10px;border-radius:999px;background:#f2f3f8;color:#8b93a6;font-size:11px;font-weight:750}.word-counter.ready{background:#eafbf4;color:#14835b}.field-label{display:block;margin:0 0 8px;font-size:12px;font-weight:750;color:#3b4354}.control{width:100%;border:1px solid #dce1ed;background:rgba(255,255,255,.9);border-radius:13px;color:var(--text);outline:none;transition:.2s}.control:focus{border-color:rgba(91,92,240,.5);box-shadow:0 0 0 4px rgba(91,92,240,.08)}.select-control{height:46px;padding:0 14px;margin-bottom:18px}.essay-input{display:block;min-height:260px;padding:16px;resize:vertical;line-height:1.75}.input-meta{display:flex;justify-content:space-between;gap:15px;margin-top:8px;color:#9aa1b1;font-size:10px}.error-message{margin-top:14px;padding:12px 14px;border-radius:12px;background:#fff1f3;color:#b42348;font-size:12px}.actions{display:flex;justify-content:flex-end;gap:9px;margin-top:18px}.submit-btn{min-width:150px}.tip-item{display:grid;grid-template-columns:30px 1fr;gap:10px;padding:15px 0;border-bottom:1px solid rgba(93,106,165,.1)}.tip-item>span{color:#5b5cf0;font-size:10px;font-weight:850}.tip-item strong{font-size:13px}.tip-item p{margin:4px 0 0;color:var(--muted);font-size:11px;line-height:1.6}.tips-note{margin-top:18px;padding:13px;border-radius:13px;background:#f5f6ff;color:#747c91;font-size:11px;line-height:1.65}.loading-card{margin-top:18px;padding:24px 28px;border-radius:22px;display:flex;align-items:center;gap:18px}.loader-orbit{position:relative;width:54px;height:54px;display:grid;place-items:center;flex:0 0 auto}.loader-orbit i{position:absolute;inset:0;border:3px solid #e6e8f7;border-top-color:#5b5cf0;border-radius:50%;animation:spin .8s linear infinite}.loader-orbit span{font-size:11px;font-weight:850;color:#5b5cf0}.loading-card h3{margin:0 0 4px;font-size:16px}.loading-card p{margin:0;color:var(--muted);font-size:12px}.result-section{margin-top:22px;display:grid;gap:16px}.result-overview{border-radius:24px;padding:26px 30px;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:24px}.score-ring{--score:0deg;width:108px;height:108px;border-radius:50%;display:grid;place-items:center;background:conic-gradient(#5b5cf0 var(--score),#edf0f8 0);position:relative}.score-ring:after{content:"";position:absolute;inset:8px;background:white;border-radius:50%}.score-ring>div{z-index:1;text-align:center}.score-ring strong{display:block;font-size:31px;line-height:1;color:#4e4fdd}.score-ring span{display:block;margin-top:5px;font-size:10px;color:#9aa1b1}.result-summary h2{margin:0;font-size:26px}.result-summary>p:last-child{margin:7px 0 0;color:var(--muted);line-height:1.7}.mini-stats{display:flex;gap:10px}.mini-stats>div{min-width:70px;text-align:center;padding:12px;border-radius:15px;background:#f7f8fc}.mini-stats strong,.mini-stats span{display:block}.mini-stats strong{font-size:20px}.mini-stats span{font-size:9px;color:#98a2b3;margin-top:3px}.result-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.result-card,.corrections-card,.improved-card{border-radius:22px;padding:25px}.section-head{display:flex;align-items:center;gap:12px}.section-head h3{margin:0;font-size:18px}.section-head p{margin:3px 0 0;color:var(--muted-2);font-size:11px}.section-icon{width:34px;height:34px;border-radius:11px;display:grid;place-items:center;font-weight:850}.section-icon.success{background:#eafbf3;color:#169461}.section-icon.idea{background:#eef0ff;color:#5657df}.section-icon.warning{background:#fff4e9;color:#d87622}.section-icon.essay-icon{background:#f2efff;color:#6756e8}.pill-list{display:grid;gap:9px;margin-top:18px}.pill-list>div{display:flex;gap:9px;padding:11px 12px;border-radius:12px;background:#f4fbf8;color:#375c4d;font-size:12px;line-height:1.6}.pill-list span{color:#1da36c}.suggestion-list{list-style:none;padding:0;margin:16px 0 0;display:grid;gap:8px}.suggestion-list li{display:grid;grid-template-columns:30px 1fr;gap:10px;align-items:start;padding:10px 0;border-bottom:1px solid rgba(93,106,165,.08)}.suggestion-list li>span{font-size:9px;color:#5b5cf0;font-weight:850;padding-top:4px}.suggestion-list p{margin:0;color:#4c5568;font-size:12px;line-height:1.7}.empty-text{margin:18px 0 0;color:#969dad;font-size:12px}.corrections-head{padding-bottom:18px;border-bottom:1px solid var(--line)}.correction-list{display:grid;gap:12px;margin-top:16px}.correction-item{display:grid;grid-template-columns:44px minmax(0,1fr);gap:13px;padding:16px;border:1px solid rgba(93,106,165,.12);border-radius:17px;background:rgba(252,253,255,.84)}.correction-number{width:36px;height:36px;border-radius:12px;display:grid;place-items:center;background:#f0f1ff;color:#5758dc;font-size:11px;font-weight:850}.compare-row{padding:11px 13px;border-radius:12px}.compare-row span{display:block;margin-bottom:4px;font-size:9px;font-weight:850;letter-spacing:.08em}.compare-row p{margin:0;font-size:13px;line-height:1.7}.compare-row.original{background:#fff5f5}.compare-row.original span{color:#d9595f}.compare-row.corrected{background:#effbf5}.compare-row.corrected span{color:#14835b}.arrow-line{height:16px;display:flex;align-items:center;padding-left:13px;color:#a3a9b8;font-size:11px}.explain{display:flex;gap:10px;margin-top:9px;padding:10px 13px;border-radius:12px;background:#f7f8fc}.explain span{flex:0 0 auto;color:#6366e8;font-size:9px;font-weight:850;padding-top:3px}.explain p{margin:0;color:#687184;font-size:11px;line-height:1.65}.perfect-state{margin-top:18px;padding:18px;border-radius:15px;background:#f1fbf7;display:flex;gap:12px}.perfect-state>span{color:#159565;font-weight:850}.perfect-state strong{display:block}.perfect-state p{margin:4px 0 0;color:#5d746c;font-size:12px}.improved-head{display:flex;justify-content:space-between;align-items:center;gap:15px}.copy-btn{padding:9px 12px;border-radius:11px;border:1px solid var(--line);background:white;color:#555d70;font-size:11px;font-weight:750;cursor:pointer}.copy-btn:hover{border-color:rgba(91,92,240,.32);color:#5152d9}.improved-paper{margin-top:18px;padding:24px;border-radius:16px;background:linear-gradient(180deg,#fbfcff,#f7f9fd);border:1px solid rgba(93,106,165,.09);font-family:Georgia,'Times New Roman',serif;font-size:15px;line-height:2;color:#30384a;white-space:pre-wrap}.error-message,.empty-text{line-height:1.6}
@media(max-width:900px){.essay-hero{align-items:flex-start;flex-direction:column}.hero-chip{display:none}.workspace,.result-grid{grid-template-columns:1fr}.tips-card{order:2}.result-overview{grid-template-columns:auto 1fr}.mini-stats{grid-column:1/-1}.correction-item{grid-template-columns:36px 1fr}}
@media(max-width:600px){.essay-hero,.editor-card,.tips-card,.result-card,.corrections-card,.improved-card{padding:20px}.result-overview{padding:20px;grid-template-columns:1fr}.score-ring{width:92px;height:92px}.mini-stats{width:100%}.mini-stats>div{flex:1}.input-meta{flex-direction:column;gap:2px}.correction-item{grid-template-columns:1fr}.improved-head{align-items:flex-start;flex-direction:column}.actions{display:grid;grid-template-columns:1fr 1.5fr}.submit-btn{min-width:0}}
</style>

```

## `ai-english-assistant/src/views/MistakeView.vue`

```vue
<script setup>
import { onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import { me } from '../api/auth'
import { deleteMistake, getMistakes, setMistakeMastered } from '../api/mistakes'

const items = ref([])
const loading = ref(false)
const errorMessage = ref('')
const pending = ref({})
const currentUserId = ref(null)

async function ensureCurrentUserId() {
  if (currentUserId.value) return currentUserId.value
  const response = await me()
  const id = Number(response.data?.id)
  if (!Number.isInteger(id) || id <= 0) throw new Error('CURRENT_USER_INVALID')
  currentUserId.value = id
  return id
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const userId = await ensureCurrentUserId()
    const response = await getMistakes(userId)
    items.value = Array.isArray(response.data) ? response.data : []
  } catch (error) {
    items.value = []
    errorMessage.value = error?.response?.data?.message || '错题本加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function toggle(mistake) {
  if (pending.value[mistake.id]) return
  const nextValue = !mistake.mastered
  pending.value = { ...pending.value, [mistake.id]: true }
  errorMessage.value = ''
  try {
    const response = await setMistakeMastered(mistake.id, nextValue)
    const updated = response.data
    items.value = items.value.map(item => item.id === mistake.id ? updated : item)
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '更新掌握状态失败，请稍后重试。'
  } finally {
    const next = { ...pending.value }
    delete next[mistake.id]
    pending.value = next
  }
}

async function remove(mistake) {
  if (pending.value[mistake.id]) return
  if (!confirm('确定删除这条错题吗？')) return

  pending.value = { ...pending.value, [mistake.id]: true }
  errorMessage.value = ''
  try {
    await deleteMistake(mistake.id)
    items.value = items.value.filter(item => item.id !== mistake.id)
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '删除错题失败，请稍后重试。'
  } finally {
    const next = { ...pending.value }
    delete next[mistake.id]
    pending.value = next
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <AppHeader />
    <main>
      <div class="head">
        <div>
          <p>MISTAKE BOOK</p>
          <h1>我的错题本</h1>
          <span>只有在作文批改结果中点击“加入错题本”后，真实保存的数据才会显示在这里。</span>
        </div>
      </div>

      <div v-if="errorMessage" class="error-box">
        {{ errorMessage }}
        <button type="button" :disabled="loading" @click="load">重新加载</button>
      </div>

      <div v-if="loading" class="loading-box">正在读取当前账号的错题数据…</div>
      <div v-else-if="!items.length" class="empty">暂无错题，去练习吧</div>

      <template v-else>
        <article v-for="m in items" :key="m.id" :class="{ done: m.mastered }">
          <div>
            <small>{{ m.source }}</small>
            <h3>{{ m.originalText }}</h3>
            <p class="correct">✓ {{ m.correctedText }}</p>
            <p>{{ m.explanation }}</p>
          </div>
          <div class="actions">
            <button type="button" :disabled="pending[m.id]" @click="toggle(m)">
              {{ pending[m.id] ? '处理中…' : m.mastered ? '取消掌握' : '标记掌握' }}
            </button>
            <button type="button" class="danger" :disabled="pending[m.id]" @click="remove(m)">
              {{ pending[m.id] ? '处理中…' : '删除' }}
            </button>
          </div>
        </article>
      </template>
    </main>
  </div>
</template>

<style scoped>
.page{min-height:100vh;background:#f7f8fc}main{width:min(980px,calc(100% - 32px));margin:32px auto}.head{margin-bottom:22px}.head p{color:#5b5cf0;font-weight:800;font-size:11px}.head h1{margin:7px 0}.head span{color:#858ca0}.empty,article,.error-box,.loading-box{background:white;border:1px solid #e6e8f1;border-radius:18px;padding:22px;margin:12px 0}.empty,.loading-box{color:#8a91a3;text-align:center;padding:50px}.error-box{color:#b42348;background:#fff7f8}.error-box button{margin-left:10px;padding:7px 10px;border:0;border-radius:8px;background:#ffe5e8;color:#a92f42;cursor:pointer}.error-box button:disabled{opacity:.55;cursor:not-allowed}.correct{color:#2a8a5d}article.done{opacity:.6}.actions{display:flex;gap:8px;margin-top:14px}.actions button{padding:8px 12px;border:0;border-radius:9px;background:#eceeff;color:#5556cf;cursor:pointer}.actions button:disabled{cursor:not-allowed;opacity:.55}.actions .danger{background:#fff0f1;color:#ba4050}
</style>

```

## `ai-english-backend/src/main/java/com/english/assistant/controller/ChatController.java`

```java
package com.english.assistant.controller;

import com.english.assistant.dto.ChatMessage;
import com.english.assistant.dto.ChatSendRequest;
import com.english.assistant.dto.CreateChatSessionRequest;
import com.english.assistant.entity.ChatHistory;
import com.english.assistant.entity.ChatSession;
import com.english.assistant.entity.User;
import com.english.assistant.exception.ApiException;
import com.english.assistant.repository.ChatHistoryRepository;
import com.english.assistant.repository.ChatSessionRepository;
import com.english.assistant.security.CurrentUserService;
import com.english.assistant.service.ChatGenerationService;
import com.english.assistant.service.DeepSeekService;
import com.english.assistant.service.RateLimitService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final Set<String> MODES = Set.of("free", "cet4", "cet6");
    private static final long SSE_TIMEOUT_MS = 75_000L;
    private static final int MAX_CONTEXT_MESSAGES = 40;
    private static final int MAX_CONTEXT_CHARS = 20_000;

    private final ChatSessionRepository sessions;
    private final ChatHistoryRepository messages;
    private final CurrentUserService current;
    private final DeepSeekService ai;
    private final RateLimitService rate;
    private final ChatGenerationService generations;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(
            ChatSessionRepository sessions,
            ChatHistoryRepository messages,
            CurrentUserService current,
            DeepSeekService ai,
            RateLimitService rate,
            ChatGenerationService generations
    ) {
        this.sessions = sessions;
        this.messages = messages;
        this.current = current;
        this.ai = ai;
        this.rate = rate;
        this.generations = generations;
    }

    @GetMapping("/sessions")
    public List<ChatSession> list() {
        User user = current.get();
        return sessions.findOwnedSessions(user.getId(), accountCreatedAt(user));
    }

    @PostMapping("/sessions")
    public ChatSession create(@Valid @RequestBody CreateChatSessionRequest request) {
        String mode = normalize(request.mode());
        String title = request.title() == null || request.title().isBlank()
                ? "新对话"
                : request.title().trim();
        return sessions.save(new ChatSession(UUID.randomUUID().toString(), current.id(), title, mode));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        User user = current.get();
        Long uid = user.getId();
        LocalDateTime createdAt = accountCreatedAt(user);
        ChatSession session = sessions.findOwnedById(id, uid, createdAt)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "会话不存在"));

        generations.cancelAndRun(id, () -> {
            messages.deleteOwnedSessionHistory(uid, id);
            sessions.deleteOwnedById(id, uid, createdAt);
        });
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/{id}/messages")
    public List<ChatHistory> history(@PathVariable String id) {
        User user = current.get();
        requireSession(id, user.getId(), accountCreatedAt(user));
        return messages.findOwnedHistory(user.getId(), id, accountCreatedAt(user));
    }

    @PostMapping(value = "/sessions/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id, @Valid @RequestBody ChatSendRequest request) {
        User user = current.get();
        Long uid = user.getId();
        LocalDateTime createdAt = accountCreatedAt(user);
        ChatSession session = requireSession(id, uid, createdAt);
        String requestId = request.requestId();

        ChatHistory completed = messages
                .findFirstOwnedRequestMessage(uid, id, requestId, "assistant", createdAt)
                .orElse(null);
        if (completed != null) return replay(completed.getContent());

        ChatGenerationService.GenerationState state = generations.begin(id, requestId);
        try {
            completed = messages
                    .findFirstOwnedRequestMessage(uid, id, requestId, "assistant", createdAt)
                    .orElse(null);
            if (completed != null) {
                generations.finish(id, state);
                return replay(completed.getContent());
            }

            if (!rate.allow(uid)) {
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "AI_RATE_LIMITED",
                        "AI 请求过于频繁，请一分钟后再试"
                );
            }

            String text = request.message().trim();
            boolean hasUserMessage = messages
                    .findFirstOwnedRequestMessage(uid, id, requestId, "user", createdAt)
                    .isPresent();
            if (!hasUserMessage) {
                messages.save(new ChatHistory(uid, "user", text, session.getMode(), id, requestId));
            }

            String title = session.getTitle();
            if ("新对话".equals(title)) {
                String normalizedTitle = text.replaceAll("\\s+", " ");
                title = normalizedTitle.length() > 28
                        ? normalizedTitle.substring(0, 28) + "…"
                        : normalizedTitle;
                session.setTitle(title);
            }
            LocalDateTime updateTime = LocalDateTime.now();
            session.setUpdateTime(updateTime);
            int updated = sessions.updateOwnedSessionMeta(id, uid, createdAt, title, updateTime);
            if (updated == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "会话不存在");
            }

            List<ChatMessage> databaseContext = loadDatabaseContext(uid, id, createdAt);

            SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
            emitter.onTimeout(() -> generations.finish(id, state));
            emitter.onCompletion(() -> generations.finish(id, state));

            executor.submit(() -> generate(id, uid, createdAt, session, request, databaseContext, state, emitter));
            return emitter;
        } catch (RuntimeException e) {
            generations.finish(id, state);
            throw e;
        }
    }

    private List<ChatMessage> loadDatabaseContext(Long uid, String sessionId, LocalDateTime accountCreatedAt) {
        List<ChatHistory> recent = messages.findOwnedRecentHistory(
                uid,
                sessionId,
                accountCreatedAt,
                PageRequest.of(0, MAX_CONTEXT_MESSAGES)
        );
        Collections.reverse(recent);

        List<ChatMessage> context = new ArrayList<>();
        int totalChars = 0;
        for (ChatHistory message : recent) {
            if (!("user".equals(message.getRole()) || "assistant".equals(message.getRole()))) continue;
            if (message.getContent() == null || message.getContent().isBlank()) continue;
            totalChars += message.getContent().length();
            if (totalChars > MAX_CONTEXT_CHARS) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "CHAT_CONTEXT_TOO_LARGE",
                        "当前对话上下文过长，请新建会话后继续"
                );
            }
            context.add(new ChatMessage(message.getRole(), message.getContent()));
        }
        return context;
    }

    private void generate(
            String sessionId,
            Long uid,
            LocalDateTime accountCreatedAt,
            ChatSession session,
            ChatSendRequest request,
            List<ChatMessage> databaseContext,
            ChatGenerationService.GenerationState state,
            SseEmitter emitter
    ) {
        try {
            String answer = ai.streamChat(databaseContext, session.getMode(), uid, chunk -> {
                if (state.isCancelled()) throw new ChatStreamCancelledException();
                try {
                    emitter.send(SseEmitter.event().name("chunk").data(chunk));
                } catch (Exception e) {
                    throw new ChatStreamCancelledException();
                }
            });

            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("AI返回内容为空");
            }

            boolean saved = generations.runIfActive(sessionId, state, () -> {
                if (sessions.findOwnedById(sessionId, uid, accountCreatedAt).isEmpty()) return;
                messages.save(new ChatHistory(
                        uid,
                        "assistant",
                        answer,
                        session.getMode(),
                        sessionId,
                        request.requestId()
                ));
            });

            if (saved && sessions.findOwnedById(sessionId, uid, accountCreatedAt).isPresent()) {
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            }
            emitter.complete();
        } catch (ChatStreamCancelledException ignored) {
            emitter.complete();
        } catch (Exception e) {
            sendStreamError(emitter, e);
        } finally {
            generations.finish(sessionId, state);
        }
    }

    private SseEmitter replay(String content) {
        SseEmitter emitter = new SseEmitter(10_000L);
        executor.submit(() -> {
            try {
                emitter.send(SseEmitter.event().name("chunk").data(content));
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void sendStreamError(SseEmitter emitter, Exception error) {
        String code = "AI_SERVICE_ERROR";
        String message = "AI 服务暂时不可用，请稍后重试";

        Throwable root = rootCause(error);
        if (root instanceof HttpTimeoutException) {
            code = "AI_TIMEOUT";
            message = "AI 响应超时，请重试";
        } else if (root instanceof DeepSeekService.DeepSeekHttpException deepSeekError) {
            if (deepSeekError.getStatusCode() == 429) {
                code = "AI_PROVIDER_RATE_LIMITED";
                message = "AI 服务当前繁忙，请稍后重试";
            } else if (deepSeekError.getStatusCode() == 401 || deepSeekError.getStatusCode() == 403) {
                code = "AI_CONFIGURATION_ERROR";
                message = "AI 服务配置异常，请联系管理员";
            }
        } else if (root instanceof IllegalStateException && root.getMessage() != null) {
            message = root.getMessage();
        }

        try {
            String json = mapper.writeValueAsString(new StreamError(code, message));
            emitter.send(SseEmitter.event().name("error").data(json));
            emitter.complete();
        } catch (Exception sendError) {
            emitter.completeWithError(sendError);
        }
    }

    private Throwable rootCause(Throwable error) {
        Throwable currentError = error;
        while (currentError.getCause() != null && currentError.getCause() != currentError) {
            currentError = currentError.getCause();
        }
        return currentError;
    }

    private ChatSession requireSession(String id, Long uid, LocalDateTime accountCreatedAt) {
        return sessions.findOwnedById(id, uid, accountCreatedAt)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "会话不存在"));
    }

    private LocalDateTime accountCreatedAt(User user) {
        return user.getCreateTime() == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : user.getCreateTime();
    }

    private String normalize(String mode) {
        String value = mode == null ? "free" : mode.trim().toLowerCase();
        if (!MODES.contains(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHAT_MODE_INVALID", "不支持的对话模式");
        }
        return value;
    }

    private record StreamError(String code, String message) {}

    private static class ChatStreamCancelledException extends RuntimeException {}
}

```

## `ai-english-backend/src/main/java/com/english/assistant/controller/EssayController.java`

```java
package com.english.assistant.controller;

import com.english.assistant.dto.EssayCheckResponse;
import com.english.assistant.dto.EssayRequest;
import com.english.assistant.entity.EssayRecord;
import com.english.assistant.entity.User;
import com.english.assistant.exception.ApiException;
import com.english.assistant.repository.EssayRecordRepository;
import com.english.assistant.security.CurrentUserService;
import com.english.assistant.service.EssayService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/essay")
public class EssayController {
    private final CurrentUserService current;
    private final EssayRecordRepository repo;
    private final EssayService essays;

    public EssayController(CurrentUserService current, EssayRecordRepository repo, EssayService essays) {
        this.current = current;
        this.repo = repo;
        this.essays = essays;
    }

    @PostMapping("/check")
    public EssayCheckResponse check(@Valid @RequestBody EssayRequest request) {
        return essays.check(current.id(), request);
    }

    @GetMapping("/records")
    public List<EssayRecord> records(@RequestParam Long userId) {
        User user = current.get();
        assertSameUser(userId, user.getId());
        return repo.findValidOwnedRecords(user.getId(), accountCreatedAt(user));
    }

    @GetMapping("/records/{id}")
    public EssayRecord record(@PathVariable Long id) {
        User user = current.get();
        Long uid = user.getId();
        return repo.findOwnedById(id, uid, accountCreatedAt(user))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ESSAY_RECORD_NOT_FOUND", "作文记录不存在"));
    }

    @DeleteMapping("/records/{id}")
    public void delete(@PathVariable Long id) {
        User user = current.get();
        Long uid = user.getId();
        int deleted = repo.deleteOwnedById(id, uid, accountCreatedAt(user));
        if (deleted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ESSAY_RECORD_NOT_FOUND", "作文记录不存在");
        }
    }

    private void assertSameUser(Long requestedUserId, Long currentUserId) {
        if (requestedUserId == null || !requestedUserId.equals(currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_SCOPE_FORBIDDEN", "无权读取其他用户的数据");
        }
    }

    private LocalDateTime accountCreatedAt(User user) {
        return user.getCreateTime() == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : user.getCreateTime();
    }
}

```

## `ai-english-backend/src/main/java/com/english/assistant/controller/MistakeController.java`

```java
package com.english.assistant.controller;

import com.english.assistant.dto.MistakeRequest;
import com.english.assistant.entity.Mistake;
import com.english.assistant.entity.User;
import com.english.assistant.exception.ApiException;
import com.english.assistant.repository.MistakeRepository;
import com.english.assistant.security.CurrentUserService;
import com.english.assistant.service.MistakeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/mistakes")
public class MistakeController {
    private final MistakeRepository repo;
    private final CurrentUserService current;
    private final MistakeService mistakes;

    public MistakeController(MistakeRepository repo, CurrentUserService current, MistakeService mistakes) {
        this.repo = repo;
        this.current = current;
        this.mistakes = mistakes;
    }

    @GetMapping
    public List<Mistake> list(@RequestParam Long userId) {
        User user = current.get();
        assertSameUser(userId, user.getId());
        return repo.findValidOwnedMistakes(user.getId(), accountCreatedAt(user));
    }

    @PostMapping
    public Mistake add(@Valid @RequestBody MistakeRequest request) {
        return mistakes.add(current.get(), request);
    }

    @PutMapping("/{id}/mastered")
    public Mistake mastered(@PathVariable Long id, @RequestParam boolean value) {
        User user = current.get();
        Long uid = user.getId();
        LocalDateTime createdAt = accountCreatedAt(user);
        int updated = repo.updateOwnedMastered(id, uid, createdAt, value);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MISTAKE_NOT_FOUND", "错题不存在");
        }
        return repo.findOwnedById(id, uid, createdAt)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MISTAKE_NOT_FOUND", "错题不存在"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User user = current.get();
        Long uid = user.getId();
        int deleted = repo.deleteOwnedById(id, uid, accountCreatedAt(user));
        if (deleted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MISTAKE_NOT_FOUND", "错题不存在或不属于当前用户");
        }
        return ResponseEntity.noContent().build();
    }

    private void assertSameUser(Long requestedUserId, Long currentUserId) {
        if (requestedUserId == null || !requestedUserId.equals(currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_SCOPE_FORBIDDEN", "无权读取其他用户的数据");
        }
    }

    private LocalDateTime accountCreatedAt(User user) {
        return user.getCreateTime() == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : user.getCreateTime();
    }
}

```

## `ai-english-backend/src/main/java/com/english/assistant/dto/ChatSendRequest.java`

```java
package com.english.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatSendRequest(
        @NotBlank(message = "消息不能为空")
        @Size(max = 4000, message = "单条消息不能超过4000个字符")
        String message,

        @NotBlank(message = "requestId不能为空")
        @Pattern(
                regexp = "^[0-9a-fA-F-]{36}$",
                message = "requestId格式不正确"
        )
        String requestId
) {
}

```

## `ai-english-backend/src/main/java/com/english/assistant/dto/MistakeRequest.java`

```java
package com.english.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class MistakeRequest {
    @NotNull(message = "作文记录ID不能为空")
    private Long essayRecordId;

    @NotBlank(message = "原始错误内容不能为空")
    @Size(max = 3000, message = "原始错误内容过长")
    private String originalText;

    @NotBlank(message = "修改后的内容不能为空")
    @Size(max = 3000, message = "修改后的内容过长")
    private String correctedText;

    @NotBlank(message = "错误说明不能为空")
    @Size(max = 5000, message = "错误说明过长")
    private String explanation;

    @NotBlank(message = "错题来源不能为空")
    @Pattern(regexp = "^essay$", message = "不支持的错题来源")
    private String source;

    public MistakeRequest() {}

    public Long getEssayRecordId() { return essayRecordId; }
    public void setEssayRecordId(Long essayRecordId) { this.essayRecordId = essayRecordId; }
    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }
    public String getCorrectedText() { return correctedText; }
    public void setCorrectedText(String correctedText) { this.correctedText = correctedText; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}

```

## `ai-english-backend/src/main/java/com/english/assistant/entity/Mistake.java`

```java
package com.english.assistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "mistake_book",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mistake_user_hash",
                columnNames = {"user_id", "content_hash"}
        ),
        indexes = {
                @Index(name = "idx_mistake_user", columnList = "user_id"),
                @Index(name = "idx_mistake_essay_record", columnList = "user_id,essay_record_id")
        }
)
public class Mistake {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "essay_record_id")
    private Long essayRecordId;

    @Lob
    private String originalText;

    @Lob
    private String correctedText;

    @Lob
    private String explanation;

    @Column(length = 30)
    private String source;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    private boolean mastered;
    private LocalDateTime createTime;

    public Mistake() {}

    public Mistake(
            Long userId,
            Long essayRecordId,
            String originalText,
            String correctedText,
            String explanation,
            String source,
            String contentHash
    ) {
        this.userId = userId;
        this.essayRecordId = essayRecordId;
        this.originalText = originalText;
        this.correctedText = correctedText;
        this.explanation = explanation;
        this.source = source;
        this.contentHash = contentHash;
        this.createTime = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getEssayRecordId() { return essayRecordId; }
    public String getOriginalText() { return originalText; }
    public void setOriginalText(String value) { originalText = value; }
    public String getCorrectedText() { return correctedText; }
    public void setCorrectedText(String value) { correctedText = value; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String value) { explanation = value; }
    public String getSource() { return source; }
    public void setSource(String value) { source = value; }
    public String getContentHash() { return contentHash; }
    public boolean isMastered() { return mastered; }
    public void setMastered(boolean value) { mastered = value; }
    public LocalDateTime getCreateTime() { return createTime; }
}

```

## `ai-english-backend/src/main/java/com/english/assistant/repository/ChatHistoryRepository.java`

```java
package com.english.assistant.repository;

import com.english.assistant.entity.ChatHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    @Query("""
            select h
              from ChatHistory h
             where h.userId = :userId
               and h.sessionId = :sessionId
               and h.createTime >= :accountCreatedAt
             order by h.createTime asc
            """)
    List<ChatHistory> findOwnedHistory(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select h
              from ChatHistory h
             where h.userId = :userId
               and h.sessionId = :sessionId
               and h.createTime >= :accountCreatedAt
             order by h.createTime desc
            """)
    List<ChatHistory> findOwnedRecentHistory(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt,
            Pageable pageable
    );

    @Query("""
            select h
              from ChatHistory h
             where h.userId = :userId
               and h.sessionId = :sessionId
               and h.requestId = :requestId
               and h.role = :role
               and h.createTime >= :accountCreatedAt
             order by h.createTime desc
            """)
    List<ChatHistory> findOwnedRequestMessages(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("requestId") String requestId,
            @Param("role") String role,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt,
            Pageable pageable
    );

    default Optional<ChatHistory> findFirstOwnedRequestMessage(
            Long userId,
            String sessionId,
            String requestId,
            String role,
            LocalDateTime accountCreatedAt
    ) {
        return findOwnedRequestMessages(userId, sessionId, requestId, role, accountCreatedAt, Pageable.ofSize(1))
                .stream()
                .findFirst();
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            delete from ChatHistory h
             where h.userId = :userId
               and h.sessionId = :sessionId
            """)
    int deleteOwnedSessionHistory(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId
    );

    long countByUserId(Long userId);

    long countByUserIdAndRole(Long userId, String role);
}

```

## `ai-english-backend/src/main/java/com/english/assistant/repository/ChatSessionRepository.java`

```java
package com.english.assistant.repository;

import com.english.assistant.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    @Query("""
            select s
              from ChatSession s
             where s.userId = :userId
               and s.createTime >= :accountCreatedAt
             order by s.updateTime desc
            """)
    List<ChatSession> findOwnedSessions(
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select s
              from ChatSession s
             where s.id = :id
               and s.userId = :userId
               and s.createTime >= :accountCreatedAt
            """)
    Optional<ChatSession> findOwnedById(
            @Param("id") String id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update ChatSession s
               set s.title = :title,
                   s.updateTime = :updateTime
             where s.id = :id
               and s.userId = :userId
               and s.createTime >= :accountCreatedAt
            """)
    int updateOwnedSessionMeta(
            @Param("id") String id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt,
            @Param("title") String title,
            @Param("updateTime") LocalDateTime updateTime
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            delete from ChatSession s
             where s.id = :id
               and s.userId = :userId
               and s.createTime >= :accountCreatedAt
            """)
    int deleteOwnedById(
            @Param("id") String id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

}

```

## `ai-english-backend/src/main/java/com/english/assistant/repository/EssayRecordRepository.java`

```java
package com.english.assistant.repository;

import com.english.assistant.entity.EssayRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EssayRecordRepository extends JpaRepository<EssayRecord, Long> {

    @Query("""
            select e
              from EssayRecord e
             where e.userId = :userId
               and e.createTime >= :accountCreatedAt
               and e.level is not null
               and e.level <> ''
               and e.originalContent is not null
               and e.originalContent <> ''
               and e.resultJson is not null
               and e.resultJson <> ''
               and e.score is not null
             order by e.createTime desc
            """)
    List<EssayRecord> findValidOwnedRecords(
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select e
              from EssayRecord e
             where e.id = :id
               and e.userId = :userId
               and e.createTime >= :accountCreatedAt
            """)
    Optional<EssayRecord> findOwnedById(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select e
              from EssayRecord e
             where e.userId = :userId
               and e.requestId = :requestId
            """)
    Optional<EssayRecord> findByUserIdAndRequestId(
            @Param("userId") Long userId,
            @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            delete from EssayRecord e
             where e.id = :id
               and e.userId = :userId
               and e.createTime >= :accountCreatedAt
            """)
    int deleteOwnedById(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    long countByUserId(Long userId);
}

```

## `ai-english-backend/src/main/java/com/english/assistant/repository/MistakeRepository.java`

```java
package com.english.assistant.repository;

import com.english.assistant.entity.Mistake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MistakeRepository extends JpaRepository<Mistake, Long> {

    @Query("""
            select m
              from Mistake m
             where m.userId = :userId
               and m.createTime >= :accountCreatedAt
               and m.essayRecordId is not null
               and m.originalText is not null
               and m.originalText <> ''
               and m.correctedText is not null
               and m.correctedText <> ''
               and m.explanation is not null
               and m.explanation <> ''
             order by m.createTime desc
            """)
    List<Mistake> findValidOwnedMistakes(
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select m
              from Mistake m
             where m.id = :id
               and m.userId = :userId
               and m.createTime >= :accountCreatedAt
            """)
    Optional<Mistake> findOwnedById(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select m
              from Mistake m
             where m.userId = :userId
               and m.contentHash = :contentHash
               and m.createTime >= :accountCreatedAt
            """)
    Optional<Mistake> findOwnedByContentHash(
            @Param("userId") Long userId,
            @Param("contentHash") String contentHash,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update Mistake m
               set m.mastered = :value
             where m.id = :id
               and m.userId = :userId
               and m.createTime >= :accountCreatedAt
            """)
    int updateOwnedMastered(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt,
            @Param("value") boolean value
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            delete from Mistake m
             where m.id = :id
               and m.userId = :userId
               and m.createTime >= :accountCreatedAt
            """)
    int deleteOwnedById(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    long countByUserId(Long userId);
}

```

## `ai-english-backend/src/main/java/com/english/assistant/service/MistakeService.java`

```java
package com.english.assistant.service;

import com.english.assistant.dto.MistakeRequest;
import com.english.assistant.entity.Mistake;
import com.english.assistant.entity.User;
import com.english.assistant.exception.ApiException;
import com.english.assistant.repository.EssayRecordRepository;
import com.english.assistant.repository.MistakeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class MistakeService {
    private final MistakeRepository repo;
    private final EssayRecordRepository essays;

    public MistakeService(MistakeRepository repo, EssayRecordRepository essays) {
        this.repo = repo;
        this.essays = essays;
    }

    public Mistake add(User user, MistakeRequest request) {
        Long userId = user.getId();
        java.time.LocalDateTime accountCreatedAt = user.getCreateTime() == null
                ? java.time.LocalDateTime.of(1970, 1, 1, 0, 0)
                : user.getCreateTime();
        essays.findOwnedById(request.getEssayRecordId(), userId, accountCreatedAt)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ESSAY_RECORD_NOT_FOUND",
                        "对应的作文记录不存在或不属于当前用户"
                ));

        String original = request.getOriginalText().trim();
        String corrected = request.getCorrectedText().trim();
        String explanation = request.getExplanation().trim();
        String source = request.getSource().trim().toLowerCase();
        String hash = hash(source + "\n" + original + "\n" + corrected + "\n" + explanation);

        Mistake existing = repo.findOwnedByContentHash(userId, hash, accountCreatedAt).orElse(null);
        if (existing != null) return existing;

        try {
            return repo.save(new Mistake(
                    userId,
                    request.getEssayRecordId(),
                    original,
                    corrected,
                    explanation,
                    source,
                    hash
            ));
        } catch (DataIntegrityViolationException e) {
            return repo.findOwnedByContentHash(userId, hash, accountCreatedAt).orElseThrow(() -> e);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成错题指纹", e);
        }
    }
}

```
