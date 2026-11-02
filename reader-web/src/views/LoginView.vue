<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await auth.login({ username: username.value, password: password.value })
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e) {
    error.value = (e as Error).message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrap">
    <span class="glow glow-a"></span>
    <span class="glow glow-b"></span>

    <div class="card auth-card">
      <div class="auth-brand">
        <BrandMark :size="46" />
        <div class="brand-copy">
          <div class="brand-name">页庐</div>
          <div class="brand-sub">自己的读书小屋</div>
        </div>
      </div>

      <h1>欢迎回来</h1>
      <p class="text-sub intro">登录后继续上次的阅读进度</p>

      <form @submit.prevent="onSubmit">
        <label for="login-username">用户名</label>
        <input
          id="login-username"
          v-model="username"
          class="input"
          type="text"
          autocomplete="username"
          placeholder="请输入用户名"
        />

        <label for="login-password">密码</label>
        <input
          id="login-password"
          v-model="password"
          class="input"
          type="password"
          autocomplete="current-password"
          placeholder="请输入密码"
        />

        <p v-if="error" class="error">{{ error }}</p>

        <button class="btn btn-primary submit" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '进入页庐' }}
        </button>
      </form>

      <p class="foot text-sub">
        还没有账号？
        <RouterLink to="/register">立即注册</RouterLink>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-wrap {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 24px;
  overflow: hidden;
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(10px);
  pointer-events: none;
}

.glow-a {
  width: 520px;
  height: 520px;
  left: -160px;
  top: -180px;
  background: radial-gradient(circle, var(--bg-glow-1) 0%, transparent 68%);
}

.glow-b {
  width: 460px;
  height: 460px;
  right: -140px;
  bottom: -160px;
  background: radial-gradient(circle, var(--bg-glow-2) 0%, transparent 68%);
}

.auth-card {
  position: relative;
  width: 420px;
  max-width: 100%;
  padding: 36px 34px 30px;
  border-radius: 22px;
  background: color-mix(in srgb, var(--surface) 94%, transparent);
  backdrop-filter: blur(18px);
  box-shadow: var(--shadow-md);
  animation: rise 0.35s ease both;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 26px;
}

.brand-name {
  font-family: var(--serif);
  font-size: 25px;
  font-weight: 700;
  letter-spacing: 0.06em;
  line-height: 1.1;
  color: var(--text);
}

.brand-sub {
  margin-top: 3px;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--text-sub);
}

h1 {
  margin: 0 0 4px;
  font-size: 23px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.intro {
  margin: 0;
  font-size: 13.5px;
}

form {
  display: flex;
  flex-direction: column;
  margin-top: 24px;
}

label {
  font-size: 12.5px;
  color: var(--text-sub);
  margin: 12px 0 6px;
}

label:first-of-type {
  margin-top: 0;
}

.input {
  height: 44px;
}

.error {
  margin: 12px 0 0;
  padding: 8px 11px;
  border-radius: 9px;
  font-size: 12.5px;
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 8%, var(--surface));
}

.submit {
  margin-top: 22px;
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 0.04em;
}

.foot {
  margin: 22px 0 0;
  text-align: center;
  font-size: 13.5px;
}

.foot a {
  font-weight: 600;
}
</style>
