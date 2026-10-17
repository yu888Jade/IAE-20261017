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
