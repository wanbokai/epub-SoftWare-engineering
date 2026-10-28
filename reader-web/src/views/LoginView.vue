<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

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
    <div class="card auth-card">
      <h1>登录</h1>
      <p class="text-sub">欢迎回到云端阅读器</p>

      <form @submit.prevent="onSubmit">
        <label>用户名</label>
        <input v-model="username" class="input" type="text" autocomplete="username" placeholder="请输入用户名" />

        <label>密码</label>
        <input v-model="password" class="input" type="password" autocomplete="current-password" placeholder="请输入密码" />

        <p v-if="error" class="error">{{ error }}</p>

        <button class="btn btn-primary submit" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
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
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  width: 380px;
  max-width: 100%;
}

h1 {
  margin: 0 0 4px;
  font-size: 24px;
}

form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 24px;
}

label {
  font-size: 13px;
  color: var(--text-sub);
  margin-top: 8px;
}

.submit {
  margin-top: 16px;
  width: 100%;
  padding: 10px;
}

.error {
  color: var(--danger);
  font-size: 13px;
  margin: 8px 0 0;
}

.foot {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
}
</style>
