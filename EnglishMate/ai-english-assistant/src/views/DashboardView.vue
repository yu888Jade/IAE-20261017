<script setup>
import {ref,onMounted} from 'vue'
import AppHeader from '../components/AppHeader.vue'
import {getDashboard} from '../api/dashboard'
const data=ref({})
onMounted(async()=>{data.value=(await getDashboard()).data})
</script>
<template>
<div class="app-shell"><AppHeader/><main class="page-wrap"><h1>学习数据 Dashboard</h1><div class="glass-card" style="padding:30px;display:grid;gap:20px">
<div>AI 对话次数：{{data.chatCount||0}}</div>
<div>作文次数：{{data.essayCount||0}}</div>
<div>错题数量：{{data.mistakeCount||0}}</div>
</div></main></div>
</template>
