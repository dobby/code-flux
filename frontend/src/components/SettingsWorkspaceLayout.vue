<script setup lang="ts">
const props = defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <div class="settings-view">
    <div class="settings-view__body settings-workspace-layout" :data-settings-title="props.title">
      <slot />
    </div>
  </div>
</template>
