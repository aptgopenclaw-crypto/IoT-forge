import { defineStore } from 'pinia'
import { ref } from 'vue'
import { i18n, type LocaleKey } from '@/i18n'

const STORAGE_KEY = 'locale'

export const useLocaleStore = defineStore('locale', () => {
  const locale = ref<LocaleKey>(
    (localStorage.getItem(STORAGE_KEY) as LocaleKey | null) ?? 'zh-TW',
  )

  function setLocale(newLocale: LocaleKey) {
    locale.value = newLocale
    i18n.global.locale.value = newLocale
    localStorage.setItem(STORAGE_KEY, newLocale)
    // Update html lang attribute for accessibility
    document.documentElement.lang =
      newLocale === 'zh-TW' ? 'zh-TW'
      : newLocale === 'zh-CN' ? 'zh-CN'
      : 'en'
  }

  // Apply on init
  setLocale(locale.value)

  return { locale, setLocale }
})
