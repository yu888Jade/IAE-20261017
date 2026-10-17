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
  let terminalEventReceived = false

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

    if (event === 'done') {
      // 服务端已明确结束，不再依赖底层连接是否立刻关闭（部分代理会延迟关闭 SSE 连接）。
      terminalEventReceived = true

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

  try {
    while (!terminalEventReceived) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() || ''
      for (const eventText of events) consumeEvent(eventText)
    }

    if (!terminalEventReceived) {
      buffer += decoder.decode()
      if (buffer.trim()) consumeEvent(buffer)
    }
  } finally {
    // 收到 done 后主动释放连接，避免代理层挂起；流已正常结束时 cancel 无副作用。
    reader.cancel().catch(() => {})
  }
}
