<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import BrandMark from '@/components/BrandMark.vue'

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
    <div class="brand" @click="router.push('/')">
      <BrandMark :size="34" />
      <div class="brand-copy">
        <span class="brand-name">页庐</span>
        <span class="brand-sub">自己的读书小屋</span>
      </div>
    </div>

    <nav class="nav">
      <button class="nav-link" :class="{ active: isActive('bookshelf') }" @click="router.push('/')">
        书架
      </button>
      <button
        class="nav-link"
        :class="{ active: isActive('settings') }"
        @click="router.push('/settings')"
      >
        设置
      </button>
    </nav>

    <div class="right">
      <span class="avatar">{{ (auth.username || '页').slice(0, 1) }}</span>
      <span class="username">{{ auth.username }}</span>
      <button class="btn btn-quiet" @click="onLogout">退出登录</button>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 0 28px;
  height: 64px;
  background: color-mix(in srgb, var(--surface) 88%, transparent);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 20;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand-name {
  font-family: var(--serif);
  font-weight: 700;
  font-size: 21px;
  letter-spacing: 0.04em;
  color: var(--text);
}

.brand-sub {
  margin-top: 2px;
  font-size: 10.5px;
  letter-spacing: 0.08em;
  color: var(--text-sub);
}

.nav {
  display: flex;
  gap: 4px;
  flex: 1;
}

.nav-link {
  border: none;
  background: none;
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 14px;
  color: var(--text-sub);
  transition: background 0.18s ease, color 0.18s ease;
}

.nav-link:hover {
  background: var(--surface-2);
  color: var(--text);
}

.nav-link.active {
  color: var(--primary-hover);
  background: var(--primary-soft);
  font-weight: 600;
}

.right {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--primary-soft);
  color: var(--primary-hover);
  font-size: 13px;
  font-weight: 700;
}

.username {
  font-size: 13px;
  color: var(--text-sub);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .topbar {
    gap: 14px;
    padding: 0 16px;
  }

  .brand-sub {
    display: none;
  }

  .username {
    display: none;
  }
}
</style>
