<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <div class="settings-view">
    <div class="settings-view__body">
      <slot />
    </div>
  </div>
</template>
