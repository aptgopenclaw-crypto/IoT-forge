import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './assets/styles/theme-tokens.css'
import './assets/styles/element-overrides.css'
import './assets/styles/page-layout.css'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/themeStore'
import { useLocaleStore } from './stores/localeStore'
import i18n from './i18n'

// Register ECharts renderer + components globally (required by vue-echarts 8 + echarts 6)
use([CanvasRenderer, TooltipComponent, LegendComponent, GridComponent, BarChart, PieChart, LineChart])

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(i18n)

// Initialize theme and locale before mounting
useThemeStore(pinia)
useLocaleStore(pinia)

// Global error boundary — prevent stack traces from leaking to prod console
app.config.errorHandler = (err, _instance, info) => {
  if (import.meta.env.DEV) {
    console.error('[Vue Error]', err, info)
  }
  // Production: swallow to prevent info leakage; integrate Sentry/telemetry here later
}

window.addEventListener('unhandledrejection', (event) => {
  if (import.meta.env.DEV) {
    console.error('[Unhandled Rejection]', event.reason)
  }
  event.preventDefault()
})

window.addEventListener('error', (event) => {
  if (import.meta.env.DEV) {
    console.error('[Global Error]', event.error)
  }
})

app.mount('#app')
