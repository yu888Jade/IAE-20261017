<script setup>
import {onMounted,ref} from 'vue'
import AppHeader from '../components/AppHeader.vue'
import {getProfile,saveProfile as saveProfileApi} from '../api/profile'
const goal=ref('CET-6'),level=ref(''),studyTime=ref(''),weakness=ref(''),message=ref(''),loading=ref(false),saving=ref(false)
async function loadProfile(){loading.value=true;message.value='';try{const {data}=await getProfile();if(data){goal.value=data.goal||'CET-6';level.value=data.level||'';studyTime.value=data.studyTime||'';weakness.value=data.weakness||''}}catch(e){message.value='暂时无法读取学习档案'}finally{loading.value=false}}
async function saveProfile(){if(saving.value)return;const cleanLevel=level.value.trim(),cleanStudyTime=studyTime.value.trim(),cleanWeakness=weakness.value.trim();if(!cleanLevel||!cleanStudyTime||!cleanWeakness){message.value='请完整填写学习档案';return}if(cleanLevel.length>100||cleanStudyTime.length>50||cleanWeakness.length>200){message.value='填写内容过长，请按输入框限制精简后再保存';return}saving.value=true;message.value='';try{await saveProfileApi({goal:goal.value,level:cleanLevel,studyTime:cleanStudyTime,weakness:cleanWeakness});message.value='学习档案已保存。若档案内容发生变化，原学习计划会自动标记为过期，请前往学习计划重新生成。'}catch(e){message.value=e.response?.data?.message||'保存失败，请稍后重试'}finally{saving.value=false}}
onMounted(loadProfile)
</script>

<template>
  <div class="profile-page">
    <AppHeader />

    <main class="main-content">
      <section class="page-heading">
        <p class="tag">
          LEARNING PROFILE
        </p>

        <h1>
          我的英语学习档案
        </h1>

        <p>
          完善你的学习目标、当前水平和薄弱方向。
          AI 对话和学习计划会根据这些信息进行个性化调整。
        </p>
      </section>

      <section class="profile-card">
        <div
          v-if="loading"
          class="loading"
        >
          正在读取学习档案...
        </div>

        <template v-else>
          <div class="field">
            <label>
              学习目标
            </label>

            <select v-model="goal">
              <option value="CET-4">
                CET-4
              </option>

              <option value="CET-6">
                CET-6
              </option>

              <option value="口语提升">
                口语提升
              </option>
            </select>
          </div>

          <div class="field">
            <label>
              当前英语水平
            </label>

            <input
              v-model="level"
              maxlength="100"
              placeholder="例如：四级 520 分左右"
            />
          </div>

          <div class="field">
            <label>
              每天可学习时间
            </label>

            <input
              v-model="studyTime"
              maxlength="50"
              placeholder="例如：60 分钟"
            />
          </div>

          <div class="field">
            <label>
              薄弱方向
            </label>

            <input
              v-model="weakness"
              maxlength="200"
              placeholder="例如：听力、写作、词汇"
            />
          </div>

          <div class="tip-card">
            <strong>
              这些信息会用于什么？
            </strong>

            <p>
              AI 会结合你的学习档案调整对话难度，
              并生成更符合你实际情况的 7 天学习计划。
            </p>
          </div>

          <button
            class="save-button"
            :disabled="saving"
            @click="saveProfile"
          >
            {{
              saving
                ? '保存中...'
                : '保存学习档案'
            }}
          </button>

          <p
            v-if="message"
            class="message"
          >
            {{ message }}
          </p>
        </template>
      </section>
    </main>
  </div>
</template>

<style scoped>
* {
  box-sizing: border-box;
}

.profile-page {
  min-height: 100vh;

  background: #f7f9fc;

  color: #1f2937;

  font-family:
    Inter,
    "Microsoft YaHei",
    Arial,
    sans-serif;
}

.navbar {
  height: 72px;

  padding: 0 8%;

  display: flex;

  align-items: center;

  justify-content: space-between;

  background: white;

  border-bottom:
    1px solid #e5e7eb;
}

.logo {
  display: flex;

  align-items: center;

  gap: 10px;

  color: #1f2937;

  font-size: 20px;

  font-weight: 700;

  text-decoration: none;
}

.logo-icon {
  width: 38px;

  height: 38px;

  display: flex;

  align-items: center;

  justify-content: center;

  border-radius: 10px;

  background: #4f46e5;

  color: white;

  font-size: 14px;
}

nav {
  display: flex;

  gap: 28px;
}

nav a {
  color: #4b5563;

  text-decoration: none;

  font-size: 15px;
}

nav a:hover,
nav .active {
  color: #4f46e5;
}

.main-content {
  width: 760px;

  max-width: 90%;

  margin: 0 auto;

  padding: 55px 0 80px;
}

.page-heading {
  margin-bottom: 28px;
}

.tag {
  margin: 0 0 10px;

  color: #4f46e5;

  font-size: 12px;

  font-weight: 700;

  letter-spacing: 1.3px;
}

.page-heading h1 {
  margin: 0;

  color: #111827;

  font-size: 36px;
}

.page-heading p:last-child {
  margin-top: 15px;

  color: #6b7280;

  line-height: 1.8;
}

.profile-card {
  padding: 34px;

  background: white;

  border-radius: 20px;

  box-shadow:
    0 12px 35px
    rgba(15, 23, 42, 0.06);
}

.field {
  margin-bottom: 22px;
}

.field label {
  display: block;

  margin-bottom: 9px;

  color: #374151;

  font-size: 14px;

  font-weight: 600;
}

input,
select {
  width: 100%;

  padding: 13px 14px;

  border: 1px solid #d1d5db;

  border-radius: 10px;

  outline: none;

  background: white;

  font: inherit;
}

input:focus,
select:focus {
  border-color: #6366f1;

  box-shadow:
    0 0 0 3px
    rgba(99, 102, 241, 0.1);
}

.tip-card {
  margin: 10px 0 25px;

  padding: 17px 18px;

  border-radius: 12px;

  background: #eef2ff;

  color: #4338ca;
}

.tip-card strong {
  font-size: 14px;
}

.tip-card p {
  margin: 7px 0 0;

  font-size: 13px;

  line-height: 1.7;
}

.save-button {
  width: 100%;

  padding: 13px;

  border: none;

  border-radius: 10px;

  background: #4f46e5;

  color: white;

  font-size: 15px;

  cursor: pointer;
}

.save-button:hover {
  background: #4338ca;
}

.save-button:disabled {
  opacity: 0.6;

  cursor: not-allowed;
}

.message {
  margin-top: 16px;

  padding: 12px 14px;

  border-radius: 10px;

  background: #f8fafc;

  color: #4b5563;

  line-height: 1.6;
}

.loading {
  padding: 40px 0;

  text-align: center;

  color: #6b7280;
}

@media (
max-width: 760px
) {
  .navbar {
    padding: 0 5%;
  }

  nav {
    display: none;
  }

  .page-heading h1 {
    font-size: 30px;
  }

  .profile-card {
    padding: 25px;
  }
}
</style>
