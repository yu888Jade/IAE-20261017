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
const isGenerating = ref(false)
const generatingSessionId = ref('')
const error = ref('')
const mode = ref('free')
const chatBox = ref(null)

let activeStream = null
let openSequence = 0
let localSeq = 0
let pinnedToBottom = true
let scrollFrameQueued = false

const active = computed(() => sessions.value.find(session => session.id === activeId.value))
const generatingInActive = computed(
  () => isGenerating.value && generatingSessionId.value === activeId.value
)

function createRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, char => {
    const random = Math.random() * 16 | 0
    const value = char === 'x' ? random : (random & 0x3) | 0x8
    return value.toString(16)
  })
}

function scrollToBottom(forcePinned = false) {
  if (forcePinned) pinnedToBottom = true
  return nextTick(() => {
    if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight
  })
}

function scheduleStickToBottom() {
  if (!pinnedToBottom || scrollFrameQueued) return
  scrollFrameQueued = true
  requestAnimationFrame(() => {
    scrollFrameQueued = false
    if (pinnedToBottom && chatBox.value) {
      chatBox.value.scrollTop = chatBox.value.scrollHeight
    }
  })
}

function handleScroll() {
  const el = chatBox.value
  if (!el) return
  pinnedToBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 48
}

function findMessage(localId) {
  return messages.value.find(item => item.localId === localId)
}

function abortGeneration(reason) {
  const stream = activeStream
  if (!stream) return
  stream.reason = reason
  stream.controller.abort()
}

function stopGenerating() {
  abortGeneration('stop')
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
  if (isGenerating.value) return
  abortGeneration('switch')
  error.value = ''
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
  if (activeStream && activeStream.sessionId !== id) abortGeneration('switch')

  const sequence = ++openSequence
  activeId.value = id
  error.value = ''
  pinnedToBottom = true

  try {
    const response = await getMessages(id)
    if (sequence !== openSequence || activeId.value !== id) return
    messages.value = (Array.isArray(response.data) ? response.data : []).map(item => ({
      id: item.id,
      role: item.role,
      content: item.content,
      status: 'completed'
    }))
    await scrollToBottom(true)
  } catch (e) {
    if (sequence !== openSequence) return
    error.value = e.response?.data?.message || '聊天记录加载失败，请重试'
  }
}

async function removeSession(id) {
  if (!confirm('确定删除这个会话及全部聊天记录吗？')) return

  if (activeStream && activeStream.sessionId === id) abortGeneration('switch')
  error.value = ''

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
  if (e.code === 'AI_TIMEOUT') return 'AI 响应超时，可以点击“重新生成”。'
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

async function runGeneration(sessionId, text, requestId, assistant) {
  const controller = new AbortController()
  activeStream = { controller, sessionId, reason: 'switch' }
  isGenerating.value = true
  generatingSessionId.value = sessionId
  error.value = ''
  pinnedToBottom = true
  await scrollToBottom(true)

  try {
    await streamMessage(
      sessionId,
      { message: text, requestId },
      chunk => {
        const target = findMessage(assistant.localId)
        if (!target) return
        if (target.status === 'thinking') target.status = 'streaming'
        target.content += chunk
        scheduleStickToBottom()
      },
      { signal: controller.signal }
    )

    const target = findMessage(assistant.localId)
    if (target) target.status = 'completed'
    await refreshSessions()
  } catch (e) {
    const target = findMessage(assistant.localId)
    const aborted = e?.name === 'AbortError'
    const reason = activeStream?.reason

    if (aborted && reason === 'stop') {
      if (target) {
        if (target.content) {
          target.status = 'stopped'
        } else {
          const index = messages.value.indexOf(target)
          if (index >= 0) messages.value.splice(index, 1)
        }
      }
    } else if (!aborted) {
      if (target) {
        target.status = 'error'
        target.errorText = toErrorMessage(e)
      } else if (activeId.value === sessionId) {
        error.value = toErrorMessage(e)
      }
    }
    // aborted + switch：离开会话，未保存的占位消息随历史重新加载自然消失。
  } finally {
    if (activeStream && activeStream.controller === controller) {
      activeStream = null
      isGenerating.value = false
      generatingSessionId.value = ''
    }
    if (activeId.value === sessionId) await scrollToBottom()
  }
}

async function send() {
  const text = inputValue.value.trim()
  if (!text || isGenerating.value) return
  if (text.length > MAX_MESSAGE_LENGTH) {
    error.value = `单条消息不能超过 ${MAX_MESSAGE_LENGTH} 个字符。`
    return
  }

  inputValue.value = ''

  let sessionId
  try {
    sessionId = await ensureSession()
  } catch (e) {
    error.value = e.response?.data?.message || '创建会话失败，请稍后重试'
    return
  }
  if (!sessionId) return

  messages.value.push({ role: 'user', content: text, status: 'completed' })
  const requestId = createRequestId()
  messages.value.push({
    localId: `local-${++localSeq}`,
    role: 'assistant',
    content: '',
    status: 'thinking',
    errorText: '',
    sourceText: text,
    requestId
  })
  const assistant = messages.value[messages.value.length - 1]
  await runGeneration(sessionId, text, requestId, assistant)
}

async function retryMessage(message) {
  if (isGenerating.value || !message.sourceText) return
  const sessionId = activeId.value
  message.status = 'thinking'
  message.content = ''
  message.errorText = ''
  pinnedToBottom = true
  await runGeneration(sessionId, message.sourceText, message.requestId, message)
}

function onPrimaryAction() {
  if (generatingInActive.value) {
    stopGenerating()
  } else {
    send()
  }
}

function handleEnter(event) {
  if (event.shiftKey) return
  event.preventDefault()
  onPrimaryAction()
}

onMounted(async () => {
  try {
    await loadSessions()
  } catch (e) {
    error.value = e.response?.data?.message || '聊天数据加载失败'
  }
})

onBeforeUnmount(() => abortGeneration('switch'))
</script>

<template>
  <div class="page">
    <AppHeader />
    <main class="layout">
      <aside class="side">
        <div class="side-top">
          <select v-model="mode" :disabled="isGenerating">
            <option value="free">自由对话</option>
            <option value="cet4">CET-4</option>
            <option value="cet6">CET-6</option>
          </select>
          <button type="button" :disabled="isGenerating" @click="newSession">＋ 新建会话</button>
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
        </header>

        <div ref="chatBox" class="messages" @scroll="handleScroll">
          <div v-if="!messages.length" class="empty">
            <h2>开始一段新的英语练习</h2>
            <p>当前账号暂无聊天记录。发送第一条消息后，会话和回复才会写入数据库。</p>
          </div>

          <div
            v-for="message in messages"
            :key="message.localId || message.id"
            class="msg-row"
            :class="message.role"
          >
            <div v-if="message.role === 'assistant'" class="avatar" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="15" height="15">
                <path
                  fill="currentColor"
                  d="M12 2l1.9 5.1L19 9l-5.1 1.9L12 16l-1.9-5.1L5 9l5.1-1.9L12 2zm7 11l.9 2.4L22 16l-2.1.6L19 19l-.9-2.4L16 16l2.1-.6L19 13z"
                />
              </svg>
            </div>

            <div class="bubble">
              <div v-if="message.status === 'thinking'" class="thinking">
                <span class="dots"><i></i><i></i><i></i></span>
                <span class="thinking-text">正在思考</span>
              </div>

              <template v-else>
                <div class="bubble-text">{{ message.content }}<span
                  v-if="message.status === 'streaming'"
                  class="stream-cursor"
                ></span></div>

                <div v-if="message.status === 'stopped'" class="stopped-tag">已停止生成</div>

                <div v-if="message.status === 'error'" class="msg-error">
                  <span>{{ message.errorText }}</span>
                  <button type="button" @click="retryMessage(message)">重新生成</button>
                </div>
              </template>
            </div>
          </div>

          <button
            v-if="!pinnedToBottom && messages.length"
            type="button"
            class="to-bottom"
            aria-label="回到最新消息"
            @click="scrollToBottom(true)"
          >↓</button>
        </div>

        <div v-if="error" class="error">
          <span>{{ error }}</span>
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
            class="action-btn"
            :class="{ stop: generatingInActive, send: !generatingInActive }"
            :disabled="!generatingInActive && (!inputValue.trim() || isGenerating)"
            @click="onPrimaryAction"
          >
            {{ generatingInActive ? '停止' : '发送' }}
          </button>
        </footer>
      </section>
    </main>
  </div>
</template>

<style scoped>
*{box-sizing:border-box}
.page{min-height:100vh;background:#f7f8fc;color:#252b3d}
.layout{width:min(1280px,calc(100% - 32px));height:calc(100vh - 96px);margin:12px auto;display:grid;grid-template-columns:280px 1fr;gap:14px}
.side,.chat{background:white;border:1px solid #e6e8f1;border-radius:22px;overflow:hidden}
.side{padding:14px;overflow:auto}
.side-top{display:grid;gap:8px;margin-bottom:12px}
.side-top select,.side-top button{padding:11px;border:1px solid #e1e4ef;border-radius:12px;background:#f8f9fd}
.side-top button{background:#5b5cf0;color:white;border:0;cursor:pointer}
.side-top button:disabled,.side-top select:disabled{opacity:.55;cursor:not-allowed}
.side-empty{padding:18px 10px;color:#9aa1b2;text-align:center;font-size:12px}
.session{display:flex;justify-content:space-between;align-items:center;padding:11px;border-radius:12px;cursor:pointer;margin:4px 0}
.session:hover,.session.on{background:#f0f1ff}
.session span{min-width:0}
.session b,.session small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.session b{font-size:13px}
.session small{margin-top:4px;color:#949bad;font-size:9px}
.session>button{border:0;background:transparent;color:#9aa1b2;font-size:18px}
.chat{display:grid;grid-template-rows:auto 1fr auto auto}
.chat header{padding:18px 22px;border-bottom:1px solid #eceef5;display:flex;justify-content:space-between;align-items:center}
.chat h1{margin:3px 0 0;font-size:20px}
.chat header small{color:#7778e8}
.messages{position:relative;overflow:auto;padding:24px}
.empty{text-align:center;color:#8a91a3;margin-top:15vh}

.msg-row{display:flex;align-items:flex-start;gap:10px;margin:16px 0}
.msg-row.user{justify-content:flex-end}
.avatar{flex:none;width:30px;height:30px;border-radius:50%;background:linear-gradient(145deg,#6b6cf4,#5148d8);color:white;display:flex;align-items:center;justify-content:center;margin-top:2px}
.bubble{max-width:min(720px,85%);padding:12px 15px;border-radius:16px;line-height:1.75;white-space:pre-wrap;overflow-wrap:anywhere}
.msg-row.user .bubble{background:#5b5cf0;color:white;border-bottom-right-radius:5px}
.msg-row.assistant .bubble{background:#f3f5fa;border-bottom-left-radius:5px}

.thinking{display:flex;align-items:center;gap:9px;padding:2px 0}
.thinking-text{color:#9aa1b2;font-size:13px;letter-spacing:.5px}
.dots{display:inline-flex;gap:4px;align-items:center}
.dots i{width:6px;height:6px;border-radius:50%;background:#9b9cf0;display:inline-block;animation:em-bounce 1.2s infinite ease-in-out}
.dots i:nth-child(2){animation-delay:.18s}
.dots i:nth-child(3){animation-delay:.36s}
@keyframes em-bounce{0%,80%,100%{transform:translateY(0);opacity:.45}40%{transform:translateY(-4px);opacity:1}}
.stream-cursor{display:inline-block;width:7px;height:1.05em;margin-left:2px;vertical-align:text-bottom;border-left:2px solid #5b5cf0;animation:em-blink 1s steps(1) infinite}
@keyframes em-blink{0%,50%{opacity:1}51%,100%{opacity:0}}
.stopped-tag{margin-top:8px;color:#9aa1b2;font-size:12px}
.msg-error{margin-top:8px;padding-top:8px;border-top:1px dashed #e3d7db;color:#b42348;font-size:13px;display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.msg-error button{border:0;border-radius:8px;padding:6px 12px;background:#ffe1e6;color:#a72d45;cursor:pointer;font-size:12px}
.msg-error button:hover{background:#ffd2d9}

.to-bottom{position:absolute;left:50%;bottom:14px;transform:translateX(-50%);width:34px;height:34px;border-radius:50%;border:1px solid #e1e4ef;background:white;color:#5b5cf0;font-size:15px;box-shadow:0 6px 18px rgba(70,78,130,.16);cursor:pointer}
.to-bottom:hover{background:#f4f5ff}

.error{margin:0 18px 8px;padding:10px 12px;border-radius:10px;background:#fff1f3;color:#b42348;font-size:12px}
.chat footer{border-top:1px solid #eceef5;padding:12px 14px;display:grid;grid-template-columns:1fr auto;gap:10px}
.composer{position:relative}
.composer textarea{width:100%;height:72px;resize:none;border:1px solid #dfe3ee;border-radius:13px;padding:12px 14px 24px;outline:none;line-height:1.5;font-family:inherit}
.composer textarea:focus{border-color:#7778ee;box-shadow:0 0 0 3px rgba(91,92,240,.08)}
.composer small{position:absolute;right:10px;bottom:7px;color:#a0a6b4;font-size:9px}
.action-btn{min-width:84px;border:0;border-radius:13px;font-weight:800;cursor:pointer}
.action-btn.send{background:#5b5cf0;color:white}
.action-btn.send:disabled{background:#b6b8ef;cursor:not-allowed;opacity:.72}
.action-btn.stop{background:#f1f2fb;color:#c1445f;border:1px solid #f0d3da;font-weight:700}
.action-btn.stop:hover{background:#fde8ec}
@media(max-width:780px){.layout{grid-template-columns:1fr;height:auto}.side{max-height:260px}.chat{min-height:70vh}}
</style>
