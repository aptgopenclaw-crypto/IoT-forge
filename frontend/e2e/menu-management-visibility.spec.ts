import { test, expect } from '@playwright/test'
import { login } from './helpers/login'

/**
 * 驗證「選單管理」在左側 sidebar 的可見性：
 *   - super_admin (tenant context) → 應顯示
 *   - 場域管理者 (admin@iot.com)   → 應顯示
 *   - 一般用戶 (kevin@iot.com)     → 不應顯示
 */

test.describe('選單管理 visibility', () => {
  test('super_admin (tenant context) can see 選單管理', async ({ page }) => {
    await login(page, 'super@test.com', 'Test1234!')

    // Expand 系統管理
    await page.click('text=系統管理')
    await expect(page.getByText('選單管理')).toBeVisible()
  })

  test('場域管理者 (admin) can see 選單管理', async ({ page }) => {
    await login(page, 'admin@iot.com', 'Test1234!')

    await page.click('text=系統管理')
    await expect(page.getByText('選單管理')).toBeVisible()
  })

  test('一般用戶 (kevin) cannot see 選單管理', async ({ page }) => {
    await login(page, 'kevin@iot.com', 'Test1234!')

    await page.click('text=系統管理')
    // Wait for sub-menu to expand
    await expect(page.getByText('部門管理')).toBeVisible()
    // 選單管理 should NOT be present
    await expect(page.getByText('選單管理')).not.toBeVisible()
  })
})
