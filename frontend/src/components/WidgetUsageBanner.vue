<script setup lang="ts">
import { ChevronRight, Copy, Flame } from 'lucide-vue-next'

defineProps<{
  title: string
  description: string
}>()

const emit = defineEmits<{
  (event: 'duplicate'): void
}>()
</script>

<template>
  <section class="usage-banner">
    <div class="usage-banner__icon">
      <Flame :size="15" />
    </div>
    <div class="usage-banner__content">
      <div class="usage-banner__eyebrow">Shared widget</div>
      <strong>{{ title }}</strong>
      <p>{{ description }}</p>
    </div>
    <button class="usage-banner__action" type="button" @click="emit('duplicate')">
      <Copy :size="14" />
      <span>Duplicate & customize</span>
      <ChevronRight :size="14" />
    </button>
  </section>
</template>

<style scoped>
.usage-banner {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid rgba(245, 158, 11, 0.28);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 251, 235, 0.98), rgba(255, 247, 214, 0.88));
  color: #92400e;
}

.usage-banner__icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  background: rgba(245, 158, 11, 0.14);
}

.usage-banner__content {
  display: grid;
  gap: 4px;
}

.usage-banner__eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.8;
}

.usage-banner__content strong {
  color: #78350f;
  font-size: 14px;
  font-weight: 700;
}

.usage-banner__content p {
  margin: 0;
  color: #92400e;
  font-size: 12px;
  line-height: 1.45;
}

.usage-banner__action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #78350f;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .usage-banner {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .usage-banner__action {
    grid-column: 1 / -1;
    justify-self: start;
  }
}
</style>
