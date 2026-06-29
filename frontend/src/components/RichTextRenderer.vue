<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'

const props = defineProps<{
  html: string | null | undefined
}>()

const FORBIDDEN_TAGS = new Set([
  'script', 'iframe', 'object', 'embed', 'form',
  'input', 'textarea', 'select', 'button',
])

// Remove forbidden elements that might escape nested-removal in certain DOM parsers
DOMPurify.addHook('uponSanitizeElement', (node, data) => {
  if (FORBIDDEN_TAGS.has(data.tagName)) {
    node.remove()
  }
})

// Force all links to open safely (prevent tabnabbing)
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A' && node.getAttribute('target') === '_blank') {
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

const sanitizedHtml = computed(() => {
  if (!props.html) return ''
  return DOMPurify.sanitize(props.html, {
    FORBID_TAGS: [...FORBIDDEN_TAGS],
    FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur'],
    ADD_ATTR: ['target', 'rel'],
  })
})
</script>

<template>
  <div class="rich-text-content" v-html="sanitizedHtml" />
</template>
