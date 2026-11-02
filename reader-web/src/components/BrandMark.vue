<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 36 })
</script>

<template>
  <span class="brand-mark" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <svg viewBox="0 0 32 32" fill="none">
      <path
        d="M6.5 14.2 16 6.4l9.5 7.8"
        stroke="currentColor"
        stroke-width="2.1"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <path
        d="M9.2 14.6v10.1c0 .9.7 1.6 1.6 1.6h10.4c.9 0 1.6-.7 1.6-1.6V14.6"
        stroke="currentColor"
        stroke-width="2.1"
        stroke-linecap="round"
      />
      <path d="M13 18.6h6M13 22.1h4.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
    </svg>
  </span>
</template>

<style scoped>
.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  border-radius: 11px;
  color: #fff;
  background:
    radial-gradient(120% 120% at 20% 0%, rgba(255, 255, 255, 0.28), transparent 55%),
    linear-gradient(145deg, #709c6d 0%, #456f4b 70%, #3a5f40 100%);
  box-shadow: 0 6px 18px rgba(54, 90, 58, 0.24), inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.brand-mark svg {
  width: 66%;
  height: 66%;
}
</style>
