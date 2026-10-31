<script setup lang="ts">
import { watchEffect } from 'vue'
import { RouterView } from 'vue-router'
import { useReaderStore } from '@/stores/reader'
import { applyTheme } from '@/utils/theme'

const store = useReaderStore()

// 主题变化即时写入根级 CSS 变量，设置页/书架/阅读页同步换肤。
watchEffect(() => {
  applyTheme(store.config.theme)
})
</script>

<template>
  <RouterView />
</template>
