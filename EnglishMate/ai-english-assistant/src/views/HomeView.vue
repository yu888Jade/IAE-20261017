<script setup>
import {
  RouterLink,
} from 'vue-router'

import AppHeader
  from '../components/AppHeader.vue'

const features = [
  {
    icon: 'AI',
    title: 'AI 英语对话',
    description:
      '支持自由对话、CET-4 和 CET-6 模式，在真实上下文中练习英语表达。',
    route: '/chat',
    tag: 'DeepSeek',
  },
  {
    icon: 'W',
    title: '作文智能批改',
    description:
      '对英语作文进行评分、语法纠错、表达优化，并生成改进建议与优化范文。',
    route: '/essay',
    tag: 'AI Writing',
  },
  {
    icon: '7D',
    title: '个性化学习计划',
    description:
      '结合学习目标、当前水平、学习时间和薄弱项生成专属 7 天学习计划。',
    route: '/plan',
    tag: 'Personalized',
  },
  {
    icon: 'U',
    title: '个人学习档案',
    description:
      '保存英语水平、学习目标和薄弱方向，为 AI 对话及学习计划提供个性化依据。',
    route: '/profile',
    tag: 'MySQL',
  },
]

const techStack = [
  'Vue 3',
  'Spring Boot',
  'DeepSeek API',
  'MySQL',
  'Spring Data JPA',
]
</script>

<template>
  <div class="home-page">
    <AppHeader />

    <main>
      <!-- Hero -->
      <section class="hero">
        <div class="hero-inner">
          <div class="hero-content">
            <div class="hero-tag">
              AI + English Learning
            </div>

            <h1>
              让 AI 成为你的
              <span>
                个性化英语学习助手
              </span>
            </h1>

            <p class="hero-description">
              EnglishMate 是一个面向大学生的
              AI 英语学习平台，集成智能对话、
              作文批改、学习档案和个性化学习计划，
              让英语学习从“通用建议”变成
              “针对你的学习方案”。
            </p>

            <div class="hero-actions">
              <RouterLink
                class="primary-action"
                to="/chat"
              >
                开始 AI 对话
                <span>→</span>
              </RouterLink>

              <a
                class="secondary-action"
                href="#features"
              >
                查看核心功能
              </a>
            </div>

            <div class="tech-list">
              <span
                v-for="item in techStack"
                :key="item"
              >
                {{ item }}
              </span>
            </div>
          </div>

          <!-- 右侧产品示意 -->
          <div class="hero-preview">
            <div class="preview-top">
              <div class="preview-user">
                <div class="ai-avatar">
                  AI
                </div>

                <div>
                  <strong>
                    AI English Coach
                  </strong>

                  <p>
                    Personalized learning
                  </p>
                </div>
              </div>

              <span class="online-badge">
                ● Online
              </span>
            </div>

            <div class="preview-body">
              <div class="preview-message ai">
                Hi! Based on your learning
                profile, let's practice some
                CET-6 writing expressions today.
              </div>

              <div class="preview-message user">
                I want to improve my English
                writing skills.
              </div>

              <div class="preview-message ai">
                Great. Let's start by improving
                a common sentence used in
                argumentative essays.
              </div>
            </div>

            <div class="preview-bottom">
              <div class="preview-input">
                Type your message...
              </div>

              <div class="preview-send">
                ↑
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 数据/能力 -->
      <section class="capability-section">
        <div class="capability-grid">
          <div>
            <strong>3</strong>
            <span>AI 学习模式</span>
          </div>

          <div>
            <strong>4</strong>
            <span>核心学习模块</span>
          </div>

          <div>
            <strong>7 Days</strong>
            <span>个性化学习规划</span>
          </div>

          <div>
            <strong>MySQL</strong>
            <span>学习数据持久化</span>
          </div>
        </div>
      </section>

      <!-- 核心功能 -->
      <section
        id="features"
        class="features-section"
      >
        <div class="section-heading">
          <p>
            CORE FEATURES
          </p>

          <h2>
            一个助手，覆盖完整英语学习流程
          </h2>

          <span>
            不只是 AI 聊天，而是将学习档案、
            大模型能力和学习数据结合起来。
          </span>
        </div>

        <div class="feature-grid">
          <RouterLink
            v-for="feature in features"
            :key="feature.title"
            :to="feature.route"
            class="feature-card"
          >
            <div class="feature-top">
              <div class="feature-icon">
                {{ feature.icon }}
              </div>

              <span class="feature-tag">
                {{ feature.tag }}
              </span>
            </div>

            <h3>
              {{ feature.title }}
            </h3>

            <p>
              {{ feature.description }}
            </p>

            <div class="feature-link">
              立即体验
              <span>→</span>
            </div>
          </RouterLink>
        </div>
      </section>

      <!-- 使用流程 -->
      <section class="flow-section">
        <div class="section-heading">
          <p>
            LEARNING FLOW
          </p>

          <h2>
            从学习档案到 AI 个性化学习
          </h2>
        </div>

        <div class="flow-grid">
          <div class="flow-item">
            <span class="flow-number">
              01
            </span>

            <h3>
              建立学习档案
            </h3>

            <p>
              填写英语水平、学习目标、
              每日时间和薄弱方向。
            </p>
          </div>

          <div class="flow-arrow">
            →
          </div>

          <div class="flow-item">
            <span class="flow-number">
              02
            </span>

            <h3>
              AI 个性化学习
            </h3>

            <p>
              DeepSeek 根据学习档案调整
              对话难度和学习建议。
            </p>
          </div>

          <div class="flow-arrow">
            →
          </div>

          <div class="flow-item">
            <span class="flow-number">
              03
            </span>

            <h3>
              数据持续记录
            </h3>

            <p>
              聊天历史及用户档案通过
              MySQL 进行持久化管理。
            </p>
          </div>
        </div>

        <div class="flow-action">
          <RouterLink to="/profile">
            创建我的学习档案 →
          </RouterLink>
        </div>
      </section>
    </main>

    <footer class="footer">
      <div>
        <strong>
          EnglishMate
        </strong>

        <span>
          AI English Learning Assistant
        </span>
      </div>

      <p>
        Vue 3 · Spring Boot · DeepSeek · MySQL
      </p>
    </footer>
  </div>
</template>

<style scoped>
* {
  box-sizing: border-box;
}

html {
  scroll-behavior: smooth;
}

.home-page {
  min-height: 100vh;

  background: #f8fafc;

  color: #1f2937;

  font-family:
    Inter,
    "Microsoft YaHei",
    Arial,
    sans-serif;
}

/* HERO */

.hero {
  overflow: hidden;

  background:
    radial-gradient(
      circle at 82% 18%,
      #e0e7ff 0,
      transparent 27%
    ),
    radial-gradient(
      circle at 5% 90%,
      #f5f3ff 0,
      transparent 23%
    ),
    white;
}

.hero-inner {
  width: min(1240px, 90%);
  min-height: 600px;

  margin: 0 auto;

  display: grid;

  grid-template-columns:
    minmax(0, 1.05fr)
    minmax(380px, 0.95fr);

  align-items: center;

  gap: 80px;

  padding: 72px 0;
}

.hero-tag {
  display: inline-flex;

  padding: 8px 14px;

  border-radius: 999px;

  background: #eef2ff;

  color: #4f46e5;

  font-size: 12px;
  font-weight: 700;

  letter-spacing: 0.8px;
}

.hero-content h1 {
  max-width: 650px;

  margin: 22px 0 0;

  color: #111827;

  font-size: clamp(
    42px,
    5vw,
    64px
  );

  line-height: 1.12;

  letter-spacing: -2px;
}

.hero-content h1 span {
  display: block;

  margin-top: 7px;

  color: #4f46e5;
}

.hero-description {
  max-width: 650px;

  margin: 26px 0 0;

  color: #6b7280;

  font-size: 16px;

  line-height: 1.95;
}

.hero-actions {
  margin-top: 32px;

  display: flex;

  gap: 13px;

  flex-wrap: wrap;
}

.primary-action,
.secondary-action {
  padding: 13px 21px;

  border-radius: 10px;

  font-size: 14px;
  font-weight: 600;

  text-decoration: none;

  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.primary-action {
  background: #4f46e5;

  color: white;

  box-shadow:
    0 10px 25px
    rgba(79, 70, 229, 0.2);
}

.primary-action span {
  margin-left: 7px;
}

.secondary-action {
  border: 1px solid #d1d5db;

  background: white;

  color: #4b5563;
}

.primary-action:hover,
.secondary-action:hover {
  transform: translateY(-2px);
}

.tech-list {
  margin-top: 35px;

  display: flex;

  gap: 9px;

  flex-wrap: wrap;
}

.tech-list span {
  padding: 6px 10px;

  border: 1px solid #e5e7eb;

  border-radius: 7px;

  background: white;

  color: #6b7280;

  font-size: 11px;
  font-weight: 600;
}

/* HERO PREVIEW */

.hero-preview {
  overflow: hidden;

  border: 1px solid #e5e7eb;

  border-radius: 22px;

  background: white;

  box-shadow:
    0 30px 70px
    rgba(31, 41, 55, 0.14);

  transform: rotate(1deg);
}

.preview-top {
  padding: 18px 20px;

  display: flex;
  align-items: center;
  justify-content: space-between;

  border-bottom: 1px solid #eef0f3;
}

.preview-user {
  display: flex;
  align-items: center;

  gap: 11px;
}

.ai-avatar {
  width: 42px;
  height: 42px;

  display: grid;
  place-items: center;

  border-radius: 50%;

  background: #4f46e5;

  color: white;

  font-size: 12px;
  font-weight: 700;
}

.preview-user strong {
  color: #111827;

  font-size: 14px;
}

.preview-user p {
  margin: 4px 0 0;

  color: #9ca3af;

  font-size: 11px;
}

.online-badge {
  color: #16a34a;

  font-size: 11px;
  font-weight: 600;
}

.preview-body {
  min-height: 340px;

  padding: 26px;

  display: flex;
  flex-direction: column;

  gap: 19px;

  background: #fafbff;
}

.preview-message {
  max-width: 80%;

  padding: 13px 16px;

  border-radius: 15px;

  font-size: 13px;

  line-height: 1.7;
}

.preview-message.ai {
  align-self: flex-start;

  border:
    1px solid #e5e7eb;

  border-radius:
    15px 15px 15px 4px;

  background: white;

  color: #374151;
}

.preview-message.user {
  align-self: flex-end;

  border-radius:
    15px 15px 4px 15px;

  background: #4f46e5;

  color: white;
}

.preview-bottom {
  padding: 14px 16px;

  display: flex;

  gap: 10px;

  border-top:
    1px solid #eef0f3;
}

.preview-input {
  flex: 1;

  padding: 11px 14px;

  border: 1px solid #e5e7eb;

  border-radius: 9px;

  color: #9ca3af;

  font-size: 12px;
}

.preview-send {
  width: 40px;

  display: grid;
  place-items: center;

  border-radius: 9px;

  background: #4f46e5;

  color: white;
}

/* CAPABILITY */

.capability-section {
  border-top:
    1px solid #eef0f3;

  border-bottom:
    1px solid #eef0f3;

  background: white;
}

.capability-grid {
  width: min(1100px, 90%);

  margin: 0 auto;

  display: grid;

  grid-template-columns:
    repeat(4, 1fr);

  padding: 28px 0;
}

.capability-grid div {
  display: flex;
  flex-direction: column;

  align-items: center;

  gap: 5px;

  border-right:
    1px solid #e5e7eb;
}

.capability-grid div:last-child {
  border-right: none;
}

.capability-grid strong {
  color: #111827;

  font-size: 23px;
}

.capability-grid span {
  color: #9ca3af;

  font-size: 12px;
}

/* FEATURES */

.features-section,
.flow-section {
  width: min(1200px, 90%);

  margin: 0 auto;

  padding: 90px 0;
}

.section-heading {
  max-width: 700px;

  margin-bottom: 38px;
}

.section-heading > p {
  margin: 0 0 10px;

  color: #4f46e5;

  font-size: 12px;

  font-weight: 700;

  letter-spacing: 1.3px;
}

.section-heading h2 {
  margin: 0;

  color: #111827;

  font-size: 34px;

  letter-spacing: -0.8px;
}

.section-heading > span {
  display: block;

  margin-top: 13px;

  color: #6b7280;

  line-height: 1.8;
}

.feature-grid {
  display: grid;

  grid-template-columns:
    repeat(2, 1fr);

  gap: 20px;
}

.feature-card {
  min-height: 260px;

  padding: 28px;

  display: flex;
  flex-direction: column;

  border: 1px solid #e5e7eb;

  border-radius: 17px;

  background: white;

  color: inherit;

  text-decoration: none;

  transition:
    transform 0.2s,
    border-color 0.2s,
    box-shadow 0.2s;
}

.feature-card:hover {
  transform: translateY(-4px);

  border-color: #c7d2fe;

  box-shadow:
    0 16px 35px
    rgba(31, 41, 55, 0.08);
}

.feature-top {
  display: flex;

  align-items: center;
  justify-content: space-between;
}

.feature-icon {
  width: 46px;
  height: 46px;

  display: grid;
  place-items: center;

  border-radius: 12px;

  background: #eef2ff;

  color: #4338ca;

  font-size: 13px;
  font-weight: 800;
}

.feature-tag {
  padding: 5px 9px;

  border-radius: 999px;

  background: #f8fafc;

  color: #9ca3af;

  font-size: 10px;
  font-weight: 600;
}

.feature-card h3 {
  margin: 23px 0 10px;

  color: #111827;

  font-size: 20px;
}

.feature-card p {
  margin: 0;

  color: #6b7280;

  font-size: 14px;

  line-height: 1.8;
}

.feature-link {
  margin-top: auto;
  padding-top: 25px;

  color: #4f46e5;

  font-size: 13px;
  font-weight: 700;
}

.feature-link span {
  margin-left: 4px;
}

/* FLOW */

.flow-section {
  padding-top: 25px;
}

.flow-grid {
  display: grid;

  grid-template-columns:
    1fr auto 1fr auto 1fr;

  align-items: center;

  gap: 22px;
}

.flow-item {
  min-height: 185px;

  padding: 25px;

  border: 1px solid #e5e7eb;

  border-radius: 15px;

  background: white;
}

.flow-number {
  color: #4f46e5;

  font-size: 12px;
  font-weight: 800;
}

.flow-item h3 {
  margin: 17px 0 8px;

  color: #111827;

  font-size: 18px;
}

.flow-item p {
  margin: 0;

  color: #6b7280;

  font-size: 13px;

  line-height: 1.8;
}

.flow-arrow {
  color: #c7d2fe;

  font-size: 24px;
}

.flow-action {
  margin-top: 28px;
}

.flow-action a {
  color: #4f46e5;

  font-size: 14px;
  font-weight: 700;

  text-decoration: none;
}

/* FOOTER */

.footer {
  min-height: 110px;

  padding: 30px 5%;

  display: flex;

  align-items: center;
  justify-content: space-between;

  gap: 20px;

  border-top:
    1px solid #e5e7eb;

  background: #111827;

  color: white;
}

.footer div {
  display: flex;
  flex-direction: column;

  gap: 5px;
}

.footer strong {
  font-size: 17px;
}

.footer span,
.footer p {
  color: #9ca3af;

  font-size: 12px;
}

/* RESPONSIVE */

@media (max-width: 950px) {
  .hero-inner {
    grid-template-columns: 1fr;

    gap: 45px;
  }

  .hero-preview {
    max-width: 650px;
  }
}

@media (max-width: 760px) {
  .hero-inner {
    width: 92%;

    padding: 55px 0;
  }

  .hero-content h1 {
    font-size: 40px;

    letter-spacing: -1px;
  }

  .capability-grid {
    grid-template-columns:
      repeat(2, 1fr);

    gap: 25px 0;
  }

  .capability-grid div:nth-child(2) {
    border-right: none;
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }

  .flow-grid {
    grid-template-columns: 1fr;
  }

  .flow-arrow {
    display: none;
  }

  .section-heading h2 {
    font-size: 29px;
  }

  .footer {
    align-items: flex-start;

    flex-direction: column;
  }
}
</style>