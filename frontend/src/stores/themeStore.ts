import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  const savedTheme = localStorage.getItem('theme')
  const isDark = ref(savedTheme !== 'light')

  function applyTheme(dark: boolean) {
    const html = document.documentElement
    if (dark) {
      html.classList.add('dark')
      html.classList.remove('light')
    } else {
      html.classList.remove('dark')
      html.classList.add('light')
    }
    localStorage.setItem('theme', dark ? 'dark' : 'light')
  }

  function toggleTheme() {
    isDark.value = !isDark.value
  }

  // Apply immediately on store creation
  applyTheme(isDark.value)

  watch(isDark, applyTheme)

  return { isDark, toggleTheme }
})
