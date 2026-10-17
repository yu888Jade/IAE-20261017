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
