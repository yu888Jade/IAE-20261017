<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import { getStudyPlan, regenerateStudyPlan } from '../api/plan'
import { getProfile } from '../api/profile'

const userKey = (() => {
  try { return JSON.parse(localStorage.getItem('englishmate.user') || '{}').username || 'default' }
  catch { return 'default' }
})()

const plan = ref(null)
const planVersion = ref(null)
const planStatus = ref('LOADING')
const profileReady = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const activeDay = ref(0)
const checked = ref({})
const cacheKey = `englishmate.plan.${userKey}`
const progressKey = `englishmate.plan.progress.${userKey}`

function isProfileComplete(profile) {
  return Boolean(
    profile &&
    String(profile.goal || '').trim() &&
    String(profile.level || '').trim() &&
    String(profile.studyTime || '').trim() &&
    String(profile.weakness || '').trim()
  )
}

function clearPlanState() {
  plan.value = null
  planVersion.value = null
  checked.value = {}
  activeDay.value = 0
  localStorage.removeItem(cacheKey)
  localStorage.removeItem(progressKey)
}

function saveCache() {
  if (!plan.value || !planVersion.value) return
  localStorage.setItem(cacheKey, JSON.stringify({
    version: planVersion.value,
    plan: plan.value
  }))
}

function loadCache() {
  try {
    const raw = localStorage.getItem(cacheKey)
    if (!raw) return
    const cached = JSON.parse(raw)
    if (!cached?.version || !cached?.plan) {
      localStorage.removeItem(cacheKey)
      return
    }
    planVersion.value = cached.version
    plan.value = cached.plan

    const progressRaw = localStorage.getItem(progressKey)
    if (!progressRaw) return
    const progressCache = JSON.parse(progressRaw)
    if (progressCache?.version === cached.version && progressCache?.checked) {
      checked.value = progressCache.checked
    } else {
      localStorage.removeItem(progressKey)
      checked.value = {}
    }
  } catch {
    clearPlanState()
  }
}

function saveProgress() {
  if (!planVersion.value) return
  localStorage.setItem(progressKey, JSON.stringify({
    version: planVersion.value,
    checked: checked.value
  }))
}

async function loadProfileState() {
  try {
    const { data } = await getProfile()
    profileReady.value = isProfileComplete(data)
    return profileReady.value
  } catch {
    profileReady.value = false
    return false
  }
}

async function loadPlan() {
  loadCache()
  loading.value = !plan.value
  errorMessage.value = ''

  const ready = await loadProfileState()
  if (!ready) {
    planStatus.value = 'PROFILE_REQUIRED'
    clearPlanState()
    errorMessage.value = '请先完整填写并保存学习档案，再生成个性化学习计划。'
    loading.value = false
    return
  }

  try {
    const { data } = await getStudyPlan()
    planStatus.value = data.status

    if (data.status === 'PLAN_READY' && data.plan && data.planVersion) {
      const versionChanged = planVersion.value !== data.planVersion
      plan.value = data.plan
      planVersion.value = data.planVersion
      if (versionChanged) {
        checked.value = {}
        localStorage.removeItem(progressKey)
      }
      saveCache()
      return
    }

    if (data.status === 'PLAN_NOT_GENERATED' || data.status === 'PLAN_STALE') {
      clearPlanState()
      errorMessage.value = data.message || '请生成新的7天学习计划。'
    }
  } catch (e) {
    const code = e.response?.data?.code
    if (code === 'PROFILE_REQUIRED' || code === 'PROFILE_INCOMPLETE') {
      profileReady.value = false
      planStatus.value = code
      clearPlanState()
    }
    errorMessage.value = e.response?.data?.message || '暂时无法读取学习计划，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function regenerate() {
  if (loading.value || !profileReady.value) return
  loading.value = true
  errorMessage.value = ''
  planStatus.value = 'PLAN_GENERATING'

  try {
    const { data } = await regenerateStudyPlan()
    if (data.status !== 'PLAN_READY' || !data.plan || !data.planVersion) {
      throw new Error('Invalid plan response')
    }

    plan.value = data.plan
    planVersion.value = data.planVersion
    planStatus.value = 'PLAN_READY'
    activeDay.value = 0
    checked.value = {}
    saveCache()
    localStorage.removeItem(progressKey)
    resetTimer()
  } catch (e) {
    const code = e.response?.data?.code
    if (code === 'PROFILE_REQUIRED' || code === 'PROFILE_INCOMPLETE') {
      profileReady.value = false
      planStatus.value = code
      clearPlanState()
    } else if (code === 'PLAN_GENERATION_IN_PROGRESS') {
      planStatus.value = 'PLAN_GENERATING'
    } else {
      planStatus.value = plan.value ? 'PLAN_READY' : 'PLAN_ERROR'
    }
    errorMessage.value = e.response?.data?.message || '学习计划生成失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

const days = computed(() => plan.value?.days || [])
const current = computed(() => days.value[activeDay.value] || null)
const totalTasks = computed(() => days.value.reduce((n, d) => n + (d.tasks?.length || 0), 0))
const doneTasks = computed(() => Object.values(checked.value).filter(Boolean).length)
const progress = computed(() => totalTasks.value ? Math.round(doneTasks.value / totalTasks.value * 100) : 0)
const currentDone = computed(() => {
  if (!current.value) return 0
  const day = current.value.day || activeDay.value + 1
  return (current.value.tasks || []).filter((_, i) => checked.value[taskKey(day, i)]).length
})
const currentProgress = computed(() => current.value?.tasks?.length ? Math.round(currentDone.value / current.value.tasks.length * 100) : 0)

function taskKey(day, index) { return `${day}-${index}` }
function toggleTask(day, index) {
  const key = taskKey(day, index)
  checked.value = { ...checked.value, [key]: !checked.value[key] }
  saveProgress()
}

const focusSeconds = ref(25 * 60)
const timerRunning = ref(false)
let timerId = null
const timerText = computed(() => {
  const m = Math.floor(focusSeconds.value / 60).toString().padStart(2, '0')
  const s = (focusSeconds.value % 60).toString().padStart(2, '0')
  return `${m}:${s}`
})
const timerProgress = computed(() => Math.max(0, Math.min(100, (1 - focusSeconds.value / (25 * 60)) * 100)))
function clearTimerInterval() { if (timerId) { clearInterval(timerId); timerId = null } }
function startTimer() {
  if (timerRunning.value) { timerRunning.value = false; clearTimerInterval(); return }
  if (focusSeconds.value <= 0) focusSeconds.value = 25 * 60
  timerRunning.value = true
  timerId = setInterval(() => {
    if (focusSeconds.value > 0) focusSeconds.value--
    if (focusSeconds.value <= 0) {
      timerRunning.value = false
      clearTimerInterval()
    }
  }, 1000)
}
function resetTimer() { timerRunning.value = false; clearTimerInterval(); focusSeconds.value = 25 * 60 }

watch(plan, saveCache, { deep: true })
watch(activeDay, resetTimer)
onMounted(loadPlan)
onBeforeUnmount(clearTimerInterval)
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="page-wrap">
      <section class="plan-hero glass-card">
        <div>
          <p class="page-kicker">Personalized study plan</p>
          <h1>你的 7 天英语成长路线</h1>
          <p>根据学习档案自动规划目标、任务和复盘。计划会自动保存，切换页面后不会丢失。</p>
        </div>
        <div class="hero-actions">
          <RouterLink to="/profile" class="secondary-btn">调整学习档案</RouterLink>
          <button class="primary-btn" :disabled="loading || !profileReady" @click="regenerate">{{ loading ? 'AI 正在规划…' : !profileReady ? '请先完善学习档案' : plan ? '重新生成计划' : '生成学习计划' }}</button>
        </div>
      </section>

      <section v-if="plan" class="overview-grid">
        <div class="summary-card glass-card"><span>学习画像</span><strong>{{ plan.profileSummary || '已根据你的档案生成' }}</strong></div>
        <div class="summary-card glass-card"><span>本周重点</span><strong>{{ plan.focus || '稳步提升薄弱项' }}</strong></div>
        <div class="summary-card glass-card progress-card"><span>计划进度</span><strong>{{ progress }}%</strong><div class="bar"><i :style="{ width: progress + '%' }"></i></div></div>
      </section>

      <section v-if="loading && !plan" class="loading-panel glass-card"><div class="spinner"></div><h2>AI 正在读取你的学习档案</h2><p>正在为你安排 7 天目标、训练任务和每日复盘。</p></section>
      <section v-if="errorMessage" class="error-panel">
        <span>{{ errorMessage }}</span>
        <RouterLink v-if="!profileReady" to="/profile">去完善学习档案</RouterLink>
        <button v-else-if="!loading" type="button" @click="loadPlan">重新检查</button>
      </section>

      <template v-if="plan && current">
        <div class="day-tabs">
          <button v-for="(d, i) in days" :key="d.day || i" :class="{ active: activeDay === i }" @click="activeDay = i">
            <small>DAY {{ d.day || i + 1 }}</small><strong>{{ d.title || '学习日' }}</strong>
          </button>
        </div>

        <section class="day-card glass-card fade-up" :key="activeDay">
          <div class="day-main">
            <div class="day-heading">
              <div class="heading-copy">
                <span class="soft-badge">DAY {{ current.day || activeDay + 1 }}</span>
                <h2>{{ current.title }}</h2>
                <p>{{ current.goal }}</p>
                <div class="day-meta">
                  <span>◷ {{ current.duration || '按计划完成' }}</span>
                  <span>✓ {{ currentDone }}/{{ current.tasks?.length || 0 }} 项完成</span>
                </div>
              </div>

              <div class="focus-timer" :class="{ running: timerRunning }">
                <div class="timer-top"><span>FOCUS 25</span><i>专注一下</i></div>
                <div class="timer-middle">
                  <strong>{{ timerText }}</strong>
                  <button class="timer-play" :title="timerRunning ? '暂停' : '开始'" @click="startTimer">{{ timerRunning ? 'Ⅱ' : '▶' }}</button>
                </div>
                <div class="timer-track"><i :style="{ width: timerProgress + '%' }"></i></div>
                <button class="timer-reset" @click="resetTimer">重新计时</button>
              </div>
            </div>

            <div class="task-summary"><span>今日任务</span><div><strong>{{ currentProgress }}%</strong><i><b :style="{ width: currentProgress + '%' }"></b></i></div></div>
            <div class="task-list">
              <div v-for="(task, i) in current.tasks" :key="i" :class="['task', { done: checked[taskKey(current.day || activeDay + 1, i)] }]" @click="toggleTask(current.day || activeDay + 1, i)">
                <button class="check">{{ checked[taskKey(current.day || activeDay + 1, i)] ? '✓' : '' }}</button>
                <div><small>任务 {{ String(i + 1).padStart(2, '0') }}</small><p>{{ task }}</p></div>
              </div>
            </div>
          </div>

          <aside class="review-card">
            <span>✦ DAILY REVIEW</span>
            <h3>今日复盘</h3>
            <p>{{ current.review }}</p>
            <div class="review-progress"><div><span>今日完成</span><strong>{{ currentProgress }}%</strong></div><div class="mini-bar"><i :style="{ width: currentProgress + '%' }"></i></div></div>
            <div class="tip">完成一项任务就勾选一下。看到进度慢慢长起来，会比只看“60 分钟”更有成就感。</div>
          </aside>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.plan-hero{border-radius:28px;padding:34px 36px;display:flex;align-items:flex-end;justify-content:space-between;gap:30px;background:linear-gradient(135deg,rgba(255,255,255,.94),rgba(242,245,255,.82))}.plan-hero h1{margin:0;font-size:clamp(30px,4vw,44px);letter-spacing:-.045em}.plan-hero p:not(.page-kicker){max-width:650px;margin:12px 0 0;color:var(--muted);line-height:1.75}.hero-actions{display:flex;gap:10px;flex-wrap:wrap}.overview-grid{display:grid;grid-template-columns:1.1fr 1.2fr .8fr;gap:14px;margin-top:16px}.summary-card{min-height:118px;border-radius:20px;padding:22px;display:flex;flex-direction:column;justify-content:center}.summary-card span{color:var(--muted-2);font-size:12px;font-weight:700}.summary-card strong{margin-top:8px;font-size:17px;line-height:1.5}.progress-card strong{font-size:28px;color:#5556df}.bar{height:6px;margin-top:10px;border-radius:99px;background:#ebedf5;overflow:hidden}.bar i{display:block;height:100%;border-radius:99px;background:linear-gradient(90deg,#5b5cf0,#69bdf3);transition:width .3s}.day-tabs{display:grid;grid-template-columns:repeat(7,1fr);gap:8px;margin:26px 0 12px}.day-tabs button{min-width:0;padding:13px 8px;border-radius:14px;background:rgba(255,255,255,.62);border:1px solid var(--line);color:#737b8f;cursor:pointer;transition:.2s}.day-tabs small,.day-tabs strong{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.day-tabs small{font-size:9px;font-weight:800;letter-spacing:.08em}.day-tabs strong{margin-top:5px;font-size:12px}.day-tabs button.active{color:white;border-color:transparent;background:linear-gradient(135deg,#5b5cf0,#7255ef);box-shadow:0 9px 22px rgba(91,92,240,.2);transform:translateY(-2px)}.day-card{border-radius:26px;padding:28px;display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:24px}.day-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:22px;padding-bottom:20px;border-bottom:1px solid var(--line)}.heading-copy{min-width:0}.day-heading h2{margin:12px 0 5px;font-size:28px;letter-spacing:-.03em}.day-heading p{margin:0;color:var(--muted);line-height:1.7}.day-meta{display:flex;gap:14px;flex-wrap:wrap;margin-top:12px;color:#8b93a6;font-size:11px}.focus-timer{width:176px;flex:0 0 176px;padding:12px 13px;border-radius:16px;background:linear-gradient(145deg,#f3f4ff,#fbfcff);border:1px solid rgba(91,92,240,.1);box-shadow:0 8px 22px rgba(82,86,180,.06);transition:.25s}.focus-timer.running{box-shadow:0 10px 26px rgba(91,92,240,.15);border-color:rgba(91,92,240,.22)}.timer-top{display:flex;align-items:center;justify-content:space-between;gap:8px}.timer-top span{color:#5b5cf0;font-size:9px;font-weight:850;letter-spacing:.08em}.timer-top i{font-style:normal;color:#a0a6b6;font-size:9px}.timer-middle{display:flex;align-items:center;justify-content:space-between;margin-top:6px}.timer-middle strong{font-size:24px;letter-spacing:-.035em;font-variant-numeric:tabular-nums;color:#30364a}.timer-play{width:32px;height:32px;border-radius:11px;background:linear-gradient(135deg,#5b5cf0,#7455ef);color:white;cursor:pointer;font-size:11px;box-shadow:0 6px 14px rgba(91,92,240,.2)}.timer-track{height:4px;margin-top:8px;border-radius:99px;background:#e6e8f3;overflow:hidden}.timer-track i{display:block;height:100%;background:linear-gradient(90deg,#5b5cf0,#7bc9f7);transition:width .25s}.timer-reset{margin-top:7px;padding:0;background:transparent;color:#9aa1b2;font-size:9px;cursor:pointer}.timer-reset:hover{color:#5b5cf0}.task-summary{display:flex;align-items:center;justify-content:space-between;gap:15px;padding:14px 8px 5px}.task-summary>span{font-size:11px;font-weight:800;color:#727a8f}.task-summary>div{display:flex;align-items:center;gap:8px}.task-summary strong{font-size:10px;color:#5b5cf0}.task-summary i{display:block;width:72px;height:4px;border-radius:99px;background:#eceef6;overflow:hidden}.task-summary b{display:block;height:100%;border-radius:99px;background:#5b5cf0}.task-list{margin-top:4px}.task{display:flex;align-items:flex-start;gap:13px;padding:15px 8px;border-bottom:1px solid rgba(93,106,165,.09);cursor:pointer;border-radius:12px;transition:.2s}.task:hover{padding-left:12px;background:#fafbff}.check{width:24px;height:24px;flex:0 0 auto;margin-top:2px;border-radius:8px;border:1.5px solid #cfd3e2;background:white;color:white;font-size:12px}.task small{color:var(--muted-2);font-size:10px;font-weight:750}.task p{margin:3px 0 0;line-height:1.65;color:#3e465a}.task.done .check{background:#5b5cf0;border-color:#5b5cf0}.task.done p{text-decoration:line-through;color:#9da3b2}.review-card{padding:24px;border-radius:20px;background:linear-gradient(145deg,#f1f2ff,#f8fbff);align-self:start}.review-card>span{color:#5b5cf0;font-size:10px;font-weight:850;letter-spacing:.1em}.review-card h3{margin:12px 0 8px}.review-card p{margin:0;color:#596176;line-height:1.75}.review-progress{margin-top:20px;padding-top:16px;border-top:1px solid rgba(91,92,240,.12)}.review-progress>div:first-child{display:flex;justify-content:space-between;align-items:center;font-size:11px;color:#858ca0}.review-progress strong{color:#5657df}.mini-bar{height:5px;margin-top:8px;border-radius:99px;background:#e6e8f3;overflow:hidden}.mini-bar i{display:block;height:100%;border-radius:99px;background:linear-gradient(90deg,#5b5cf0,#74c6f7)}.tip{margin-top:16px;padding-top:14px;border-top:1px solid rgba(91,92,240,.1);color:#969dad;font-size:11px;line-height:1.65}.loading-panel{text-align:center;border-radius:24px;padding:60px 20px;margin-top:22px}.spinner{width:38px;height:38px;margin:auto;border:4px solid #e5e8f3;border-top-color:#5b5cf0;border-radius:50%;animation:spin .8s linear infinite}.loading-panel h2{margin:18px 0 8px}.loading-panel p{color:var(--muted)}.error-panel{margin-top:18px;padding:15px 18px;border-radius:14px;background:#fff0f3;color:#b42347}.error-panel{display:flex;align-items:center;justify-content:space-between;gap:14px;flex-wrap:wrap}.error-panel a,.error-panel button{border:0;border-radius:10px;padding:8px 12px;background:#fff;color:#8f1d3d;font-weight:700;text-decoration:none;cursor:pointer}
@media(max-width:900px){.plan-hero{align-items:flex-start;flex-direction:column}.overview-grid{grid-template-columns:1fr}.day-tabs{overflow:auto;display:flex;padding-bottom:5px}.day-tabs button{min-width:110px}.day-card{grid-template-columns:1fr}.review-card{order:-1}}
@media(max-width:660px){.plan-hero{padding:24px}.day-card{padding:18px}.day-heading{flex-direction:column}.focus-timer{width:100%;flex-basis:auto}.timer-middle strong{font-size:28px}}
</style>
