<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

async function onLogout() {
  await auth.logout()
  router.push({ name: 'login' })
}

function isActive(name: string) {
  return route.name === name
}
</script>

<template>
  <header class="topbar">
    <div class="brand" @click="router.push('/')">📚 云端阅读器</div>
    <nav class="nav">
      <button class="nav-link" :class="{ active: isActive('bookshelf') }" @click="router.push('/')">
        书架
      </button>
      <button class="nav-link" :class="{ active: isActive('settings') }" @click="router.push('/settings')">
        设置
      </button>
    </nav>
    <div class="right">
      <span class="username">{{ auth.username }}</span>
      <button class="btn" @click="onLogout">退出登录</button>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 24px;
  height: 56px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  font-weight: 700;
  font-size: 18px;
  cursor: pointer;
  white-space: nowrap;
}

.nav {
  display: flex;
  gap: 8px;
  flex: 1;
}

.nav-link {
  border: none;
  background: none;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 14px;
  color: var(--text-sub);
}

.nav-link:hover {
  background: var(--bg);
}

.nav-link.active {
  color: var(--primary);
  background: rgba(59, 130, 246, 0.1);
  font-weight: 600;
}

.right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.username {
  font-size: 14px;
  color: var(--text-sub);
}
</style>
