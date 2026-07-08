<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="event-rule-notification">
    <div class="page-header">
      <h2>{{ t('eventRule.notificationTitle') }}</h2>
      <p class="subtitle">{{ t('eventRule.notificationSubtitle') }}</p>
    </div>

    <el-card shadow="never">
      <el-empty :description="t('common.noData')" />
    </el-card>
  </div>
</template>
