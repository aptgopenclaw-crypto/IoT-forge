import { ref, computed, watch, type Ref } from 'vue'
import { describePolicy } from '@/api/passwordPolicy'
import type { PasswordPolicyDto } from '@/types/passwordPolicy'

const SPECIAL = /[!@#$%^&*()_+\-=[\]{}|;':,.<>?/~`]/g

/**
 * Composable that fetches the effective password policy from the backend and
 * exposes a reactive validator function. Replaces any hardcoded regex logic.
 *
 * @param tenantId - Optional reactive tenantId; when it changes the policy reloads.
 */
export function usePasswordPolicy(tenantId?: Ref<string | undefined | null>) {
  const policy = ref<PasswordPolicyDto | null>(null)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const res = await describePolicy(tenantId?.value ?? undefined)
      policy.value = res.body
    } catch {
      policy.value = null
    } finally {
      loading.value = false
    }
  }

  if (tenantId) {
    watch(tenantId, load, { immediate: true })
  } else {
    load()
  }

  /**
   * Validates the password against the fetched policy.
   * Returns `true` if valid or if the policy has not loaded yet (defers to backend).
   */
  function validatePassword(password: string): boolean {
    const p = policy.value
    if (!p || !password) return true // defer to backend when policy unavailable

    if (password.length < p.minLength) return false
    if (p.maxLength > 0 && password.length > p.maxLength) return false
    if (p.requireUppercase && (password.match(/[A-Z]/g) || []).length < Math.max(1, p.minUppercase || 1)) return false
    if (p.requireLowercase && (password.match(/[a-z]/g) || []).length < Math.max(1, p.minLowercase || 1)) return false
    if (p.requireDigit && (password.match(/\d/g) || []).length < Math.max(1, p.minDigits || 1)) return false
    if (p.requireSpecial && (password.match(SPECIAL) || []).length < Math.max(1, p.minSpecialChars || 1)) return false

    return true
  }

  /** Human-readable rule descriptions from the backend. */
  const descriptions = computed(() => policy.value?.describe ?? [])

  return { policy, loading, validatePassword, descriptions, reload: load }
}
