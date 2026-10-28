<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const email = ref('')
const password = ref('')
const confirm = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''
  if (!username.value || !email.value || !password.value) {
    error.value = '请填写完整信息'
    return
  }
  if (password.value.length < 8) {
    error.value = '密码长度须在 8~64 之间'
    return
  }
  if (!/(?=.*[A-Za-z])(?=.*\d).+/.test(password.value)) {
    error.value = '密码须同时包含字母和数字'
    return
  }
  if (password.value !== confirm.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await auth.register({
      username: username.value,
      email: email.value,
      password: password.value,
    })
    router.push('/')
  } catch (e) {
    error.value = (e as Error).message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="card auth-card">
      <h1>注册</h1>
      <p class="text-sub">创建你的云端阅读账号</p>

      <form @submit.prevent="onSubmit">
        <label>用户名（3~50 字符）</label>
        <input v-model="username" class="input" type="text" autocomplete="username" placeholder="请输入用户名" />

        <label>邮箱</label>
        <input v-model="email" class="input" type="email" autocomplete="email" placeholder="name@example.com" />

        <label>密码（8~64 字符，含字母和数字）</label>
        <input v-model="password" class="input" type="password" autocomplete="new-password" placeholder="请输入密码" />

        <label>确认密码</label>
        <input v-model="confirm" class="input" type="password" autocomplete="new-password" placeholder="再次输入密码" />

        <p v-if="error" class="error">{{ error }}</p>

        <button class="btn btn-primary submit" type="submit" :disabled="loading">
          {{ loading ? '注册中…' : '注册' }}
        </button>
      </form>

      <p class="foot text-sub">
        已有账号？
        <RouterLink to="/login">去登录</RouterLink>
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
  margin-top: 20px;
}

label {
  font-size: 13px;
  color: var(--text-sub);
  margin-top: 6px;
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
