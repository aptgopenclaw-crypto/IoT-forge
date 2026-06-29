<script setup lang="ts">
/**
 * Reusable password rule hint list driven by the public describe endpoint.
 *
 * Drops into login / reset / change-password / force-change pages and shows
 * the live rule list returned by the backend. Optional `password` prop turns
 * each rule into a check/cross indicator using a light client-side evaluator.
 * The authoritative validation still happens server-side — this is purely
 * UX guidance to prevent obviously-bad submissions.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { Check, X } from 'lucide-vue-next'
import { describePolicy } from '@/api/passwordPolicy'
import type { PasswordPolicyDto } from '@/types/passwordPolicy'

const props = defineProps<{
  /** When provided, fetch tenant-specific rules; otherwise platform default. */
  tenantId?: string
  /** Live password value — when present, render pass/fail per rule. */
  password?: string
  /** Optional user identity to evaluate `not_contains_username` locally. */
  username?: string
  email?: string
}>()

const policy = ref<PasswordPolicyDto | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await describePolicy(props.tenantId)
    policy.value = res.body
  } catch {
    policy.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.tenantId, load)

const SPECIAL = /[!@#$%^&*()_+\-=[\]{}|;':,.<>?/~`]/g

/**
 * Per-rule pass/fail evaluator. Mirrors backend `PasswordValidator` logic
 * closely enough for UX hints. Server is still the source of truth — only the
 * basic complexity rules are checked here; history / max_length / username
 * containment are best-effort with the data we have client-side.
 */
const checks = computed(() => {
  const p = policy.value
  const pw = props.password ?? ''
  if (!p) return [] as { label: string; pass: boolean; show: boolean }[]
  const len = pw.length
  const countMatching = (re: RegExp) => (pw.match(re) || []).length

  const items: { label: string; pass: boolean; show: boolean }[] = []
  items.push({ label: `密碼長度至少 ${p.minLength} 字元`, pass: len >= p.minLength, show: true })
  if (p.maxLength > 0) {
    items.push({ label: `密碼長度不超過 ${p.maxLength} 字元`, pass: len <= p.maxLength, show: true })
  }
  if (p.requireUppercase) {
    const need = Math.max(1, p.minUppercase || 1)
    items.push({
      label: need > 1 ? `至少 ${need} 個大寫英文字母` : '須包含大寫英文字母',
      pass: countMatching(/[A-Z]/g) >= need,
      show: true,
    })
  }
  if (p.requireLowercase) {
    const need = Math.max(1, p.minLowercase || 1)
    items.push({
      label: need > 1 ? `至少 ${need} 個小寫英文字母` : '須包含小寫英文字母',
      pass: countMatching(/[a-z]/g) >= need,
      show: true,
    })
  }
  if (p.requireDigit) {
    const need = Math.max(1, p.minDigits || 1)
    items.push({
      label: need > 1 ? `至少 ${need} 個數字` : '須包含數字',
      pass: countMatching(/\d/g) >= need,
      show: true,
    })
  }
  if (p.requireSpecial) {
    const need = Math.max(1, p.minSpecialChars || 1)
    items.push({
      label: need > 1 ? `至少 ${need} 個特殊字元` : '須包含特殊字元',
      pass: countMatching(SPECIAL) >= need,
      show: true,
    })
  }
  if (p.notContainsUsername && (props.username || props.email)) {
    const lower = pw.toLowerCase()
    const u = (props.username || '').toLowerCase()
    const email = (props.email || '').toLowerCase()
    const local = email.includes('@') ? email.slice(0, email.indexOf('@')) : email
    const bad = (u && lower.includes(u)) || (local && lower.includes(local))
    items.push({ label: '不可包含使用者名稱或電子郵件', pass: !bad, show: true })
  }
  if (p.historyCount > 0) {
    // History check is server-side only — display as informational.
    items.push({ label: `不可與前 ${p.historyCount} 次密碼相同`, pass: true, show: true })
  }
  if (p.expireDays > 0) {
    items.push({ label: `每 ${p.expireDays} 天需更換密碼`, pass: true, show: true })
  }
  return items
})

const showIndicators = computed(() => props.password !== undefined)
</script>

<template>
  <div class="rules-hint" v-loading="loading">
    <template v-if="policy">
      <div v-if="showIndicators">
        <div v-for="(c, i) in checks" :key="i" class="rule-row">
          <Check v-if="c.pass" :size="14" class="rule-pass" />
          <X v-else :size="14" class="rule-fail" />
          <span :class="c.pass ? 'rule-pass-text' : 'rule-fail-text'">{{ c.label }}</span>
        </div>
      </div>
      <ul v-else class="rules-list">
        <li v-for="(line, i) in policy.describe" :key="i">{{ line }}</li>
      </ul>
    </template>
  </div>
</template>

<style scoped>
.rules-hint { font-size: 12px; line-height: 1.7; color: var(--text-body, #555); }
.rule-row { display: flex; align-items: center; gap: 6px; }
.rule-pass { color: #22c55e; }
.rule-fail { color: #ef4444; }
.rule-pass-text { color: var(--text-body, #555); }
.rule-fail-text { color: #ef4444; }
.rules-list { margin: 0; padding-left: 18px; }
.rules-list li { list-style: disc; }
</style>
