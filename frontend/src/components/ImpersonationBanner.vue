<script setup lang="ts">
/**
 * [Phase 4 / 4.1.6 / ADR-002] ImpersonationBanner
 *
 * Red top banner shown inside TenantLayout whenever the current access token
 * has scope=IMPERSONATION. Surfaces the active session id, a live MM:SS
 * countdown to token expiry, and a destructive "end" button that calls the
 * platform revoke endpoint then signs the operator out.
 *
 * Visual contract:
 *   - Sticky at top of TenantLayout, full bleed, red (#dc2626) background.
 *   - role="alert" so screen readers announce the impersonation state.
 *   - Hidden entirely when not impersonating (no DOM, no layout shift).
 */
import { ElButton, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { AlertTriangle } from 'lucide-vue-next'
import { useImpersonation } from '@/composables/useImpersonation'

const { t } = useI18n()
const {
  isImpersonating,
  sessionId,
  formattedRemaining,
  isExpired,
  ending,
  endImpersonation,
} = useImpersonation()

async function onEndClick() {
  try {
    await ElMessageBox.confirm(
      t('impersonation.endConfirmMessage'),
      t('impersonation.endConfirmTitle'),
      {
        type: 'warning',
        confirmButtonText: t('impersonation.endConfirmOk'),
        cancelButtonText: t('impersonation.endConfirmCancel'),
      },
    )
  } catch {
    return // user cancelled
  }
  await endImpersonation()
}
</script>

<template>
  <div
    v-if="isImpersonating"
    class="impersonation-banner"
    role="alert"
    data-testid="impersonation-banner"
  >
    <span class="banner-icon" aria-hidden="true">
      <AlertTriangle :size="18" />
    </span>
    <span class="banner-label">{{ t('impersonation.bannerLabel') }}</span>
    <span class="banner-divider" aria-hidden="true">·</span>
    <span class="banner-session">
      {{ t('impersonation.bannerSession') }}:
      <code data-testid="impersonation-session">{{ sessionId }}</code>
    </span>
    <span class="banner-divider" aria-hidden="true">·</span>
    <span class="banner-remaining">
      {{ t('impersonation.remainingTime') }}:
      <strong data-testid="impersonation-countdown">{{ formattedRemaining }}</strong>
    </span>
    <span v-if="isExpired" class="banner-expired" data-testid="impersonation-expired">
      {{ t('impersonation.expired') }}
    </span>
    <span class="banner-spacer" />
    <ElButton
      type="danger"
      size="small"
      :loading="ending"
      data-testid="impersonation-end-btn"
      @click="onEndClick"
    >
      {{ t('impersonation.endButton') }}
    </ElButton>
  </div>
</template>

<style scoped>
.impersonation-banner {
  position: sticky;
  top: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  background: #dc2626;
  color: #ffffff;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.2px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.18);
}

.banner-icon {
  display: inline-flex;
  align-items: center;
}

.banner-divider {
  opacity: 0.6;
}

.banner-session code {
  background: rgba(255, 255, 255, 0.16);
  padding: 1px 6px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', 'Menlo', monospace;
  font-size: 12px;
}

.banner-remaining strong {
  font-variant-numeric: tabular-nums;
}

.banner-expired {
  margin-left: 4px;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.28);
  border-radius: 4px;
  font-size: 12px;
}

.banner-spacer {
  flex: 1;
}
</style>
