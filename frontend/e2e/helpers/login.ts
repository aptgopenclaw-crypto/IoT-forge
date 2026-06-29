import { type Page } from '@playwright/test'

/**
 * Login helper — fills email, password, captcha (any value when skip-verification=true), then submits.
 */
export async function login(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.fill('input[placeholder="Email"]', email)
  await page.fill('input[type="password"]', password)
  // Captcha skip-verification=true accepts any value
  await page.fill('input[placeholder*="驗證碼"]', '0000')
  await page.click('button:has-text("登入")')
  // Wait for navigation away from login
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10_000 })
}
