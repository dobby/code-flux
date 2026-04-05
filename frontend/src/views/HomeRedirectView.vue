<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useWorkspaceStore } from '../stores/workspace'

const router = useRouter()
const workspace = useWorkspaceStore()

const hasPages = computed(() => workspace.orderedPages.length > 0)

onMounted(async () => {
  await workspace.initialize()
  if (workspace.orderedPages[0]) {
    await router.replace({ name: 'page', params: { pageId: workspace.orderedPages[0].id } })
    return
  }
  await router.replace({ name: 'widgets' })
})
</script>

<template>
  <section class="workspace-empty-state">
    <strong>{{ hasPages ? 'Opening workspace…' : 'Preparing workspace…' }}</strong>
    <p>Loading the V2 workspace shell and resolving the default page.</p>
  </section>
</template>
