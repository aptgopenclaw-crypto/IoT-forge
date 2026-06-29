import { createI18n } from 'vue-i18n'
import zhTW from '@/locales/zh-TW'
import zhCN from '@/locales/zh-CN'
import en from '@/locales/en'

export type LocaleKey = 'zh-TW' | 'zh-CN' | 'en'

const STORAGE_KEY = 'locale'

export function getStoredLocale(): LocaleKey {
  const stored = localStorage.getItem(STORAGE_KEY) as LocaleKey | null
  if (stored && ['zh-TW', 'zh-CN', 'en'].includes(stored)) return stored
  return 'zh-TW'
}

export const i18n = createI18n({
  legacy: false,
  locale: getStoredLocale(),
  fallbackLocale: 'zh-TW',
  messages: {
    'zh-TW': zhTW,
    'zh-CN': zhCN,
    en,
  },
})

export default i18n
