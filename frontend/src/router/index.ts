import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { publicRoutes } from '@/router/publicRoutes'
import { vmsRoutes } from '@/views/vms'
import { resolveScopeRedirect } from '@/router/guards'
import TenantLayout from '@/layouts/TenantLayout.vue'
import PlatformLayout from '@/layouts/PlatformLayout.vue'

// noauth routes — no login required, rendered at top level without any layout chrome.
const noauthRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/select-tenant',
    name: 'SelectTenant',
    component: () => import('@/views/tenant/SelectTenantView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/login/ForgotPasswordView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/reset-password',
    name: 'ResetPassword',
    component: () => import('@/views/login/ResetPasswordView.vue'),
    meta: { requiresAuth: false },
  },
  {
    // [Phase 3] Reached only with a `password_change` temp token in authStore;
    // the page itself guards against direct navigation by redirecting to /login
    // when authStore.passwordChangeToken is empty.
    path: '/force-change-password',
    name: 'ForceChangePassword',
    component: () => import('@/views/login/ForceChangePasswordView.vue'),
    meta: { requiresAuth: false },
  },
]

// [Phase 4 / 4.1.3 / ADR-004] Platform-scope routes — rendered inside
// PlatformLayout (dark theme). All children carry `requiresScope: 'PLATFORM'`
// which the 4.1.5 guard enforces. (4.1.8) the legacy `superAdminOnly` meta
// has been retired in favour of `requiresScope` everywhere.
//
// NOTE: child paths intentionally stay ABSOLUTE so existing path-literal
// assertions (see __tests__/router/tenantAuthConfigRoute.test.ts) keep working.
const platformChildren: RouteRecordRaw[] = [
  {
    // [Phase 4 / 4.1.4] Default platform landing page — every super_admin
    // login redirects here (`resolveLandingPath` in router/guards.ts).
    // Re-uses the existing TenantManageView until 4.1.9 ships a dedicated
    // platform-side tenants dashboard.
    path: '/platform/tenants',
    name: 'PlatformTenantManage',
    component: () => import('@/views/admin/tenant/TenantManageView.vue'),
    meta: { requiresScope: 'PLATFORM' },
  },
  {
    // [Platform/Tenant Separation 2.1.6] Canonical route now carries the
    // tenantId so cross-tenant editing is unambiguous and the URL aligns with
    // the backend /v1/platform/tenants/{tenantId}/auth-config endpoint.
    path: '/platform/tenants/:tenantId/auth-config',
    name: 'TenantAuthConfig',
    component: () => import('@/views/admin/setting/TenantAuthConfigView.vue'),
    meta: { requiresScope: 'PLATFORM' },
    props: true,
  },
  {
    // Legacy alias — preserves seeded menu's `route_path` and any external
    // bookmarks; rewrites to the canonical URL using the current tenant.
    path: '/platform/auth-config',
    name: 'TenantAuthConfigLegacy',
    redirect: () => {
      const tenantId =
        (typeof localStorage !== 'undefined' && localStorage.getItem('lastTenantId')) || ''
      return tenantId
        ? { name: 'TenantAuthConfig', params: { tenantId } }
        : { name: 'Login' }
    },
    meta: { requiresScope: 'PLATFORM' },
  },
  {
    // [Phase 4 / 4.1.9 / ADR-002] Dedicated impersonation management page —
    // super_admin can start a new impersonation session and review their own
    // history (active / expired / revoked).
    path: '/platform/impersonations',
    name: 'PlatformImpersonationManage',
    component: () => import('@/views/platform/ImpersonationManageView.vue'),
    meta: { requiresScope: 'PLATFORM' },
  },
  {
    path: '/platform/password-policy',
    name: 'PlatformPasswordPolicy',
    component: () => import('@/views/admin/setting/PlatformPasswordPolicyView.vue'),
    meta: { requiresScope: 'PLATFORM' },
  },
  {
    path: '/platform/announcements',
    name: 'PlatformAnnouncementManage',
    component: () => import('@/views/platform/PlatformAnnouncementManageView.vue'),
    meta: { requiresScope: 'PLATFORM' },
  },
]

// [Phase 4 / 4.1.3] Tenant-scope static admin routes — rendered inside
// TenantLayout. Also injected dynamically by menuStore based on backend menus.
const tenantStaticAdminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin/users',
    name: 'UserList',
    component: () => import('@/views/admin/UserListView.vue'),
  },
  {
    path: '/admin/users/create',
    name: 'CreateUser',
    component: () => import('@/views/admin/CreateUserView.vue'),
  },
  {
    path: '/admin/users/:userId/edit',
    name: 'EditUser',
    component: () => import('@/views/admin/EditUserView.vue'),
  },
  {
    path: '/admin/system/menus',
    name: 'MenuManage',
    component: () => import('@/views/admin/menu/MenuManageView.vue'),
  },
  {
    path: '/admin/system/tenants',
    name: 'TenantManage',
    component: () => import('@/views/admin/tenant/TenantManageView.vue'),
    // [Phase 4 / 4.1.8] Was `superAdminOnly: true`; now scope-gated so the
    // 4.1.5 guard handles redirection (TENANT/IMPERSONATION → / or /?impersonating=1).
    // The canonical super_admin entry-point is /platform/tenants under PlatformLayout.
    meta: { requiresScope: 'PLATFORM' },
  },
  {
    path: '/admin/system/roles',
    name: 'RolePermission',
    component: () => import('@/views/admin/role/RolePermissionView.vue'),
  },
  {
    path: '/admin/audit/history',
    name: 'AuditHistory',
    component: () => import('@/views/admin/audit/AuditHistoryView.vue'),
  },
  {
    path: '/admin/system/settings',
    name: 'SystemSettings',
    component: () => import('@/views/admin/setting/SystemSettingsView.vue'),
  },
  {
    path: '/admin/security/password-policy',
    name: 'TenantPasswordPolicy',
    component: () => import('@/views/admin/setting/TenantPasswordPolicyView.vue'),
  },
  {
    path: '/admin/system/announcements',
    name: 'AnnouncementManagement',
    component: () => import('@/views/admin/announcement/AnnouncementManagementView.vue'),
  },
  // ── Device Management ───────────────────────────────────────────
  {
    path: '/device/list',
    name: 'DeviceList',
    component: () => import('@/views/admin/device/DeviceListView.vue'),
  },
  {
    path: '/schemas',
    name: 'DeviceTemplate',
    component: () => import('@/views/admin/schema/DeviceTemplateView.vue'),
  },
  {
    path: '/device/work-orders',
    name: 'WorkOrderList',
    component: () => import('@/views/admin/device/WorkOrderListView.vue'),
  },
  {
    path: '/device/work-orders/pending',
    name: 'WorkOrderPendingReview',
    component: () => import('@/views/admin/device/PendingReviewView.vue'),
  },
  // ── Dispatch Management ─────────────────────────────────────────
  {
    path: '/dispatch/all',
    name: 'DispatchAll',
    component: () => import('@/views/admin/device/AllWorkOrdersView.vue'),
  },
  {
    path: '/dispatch/pending-approval',
    name: 'DispatchApproval',
    component: () => import('@/views/admin/device/PendingApprovalView.vue'),
  },
  {
    path: '/dispatch/my-tasks',
    name: 'DispatchMyTasks',
    component: () => import('@/views/admin/device/MyTasksView.vue'),
  },
  {
    path: '/dispatch/pending-review',
    name: 'DispatchReview',
    component: () => import('@/views/admin/device/PendingReviewView.vue'),
  },
  {
    path: '/device/circuits',
    name: 'CircuitList',
    component: () => import('@/views/admin/device/CircuitListView.vue'),
  },
  {
    path: '/device/contracts',
    name: 'ContractList',
    component: () => import('@/views/admin/device/ContractListView.vue'),
  },
  // ── Telemetry ───────────────────────────────────────────────────
  {
    path: '/telemetry/live',
    name: 'TelemetryLive',
    component: () => import('@/views/admin/telemetry/TelemetryLiveView.vue'),
  },
  {
    path: '/telemetry/history',
    name: 'TelemetryHistory',
    component: () => import('@/views/admin/telemetry/TelemetryHistoryView.vue'),
  },
  // ── Event Rule ──────────────────────────────────────────────────
  {
    path: '/event-rules/list',
    name: 'EventRuleList',
    component: () => import('@/views/admin/eventrule/EventRuleListView.vue'),
  },
  {
    path: '/event-rules/logs',
    name: 'EventRuleLogs',
    component: () => import('@/views/admin/eventrule/EventRuleLogsView.vue'),
  },
  {
    path: '/event-rules/notifications',
    name: 'EventRuleNotification',
    component: () => import('@/views/admin/eventrule/EventRuleNotificationView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    ...noauthRoutes,
    {
      // [Phase 4 / 4.1.3] Platform shell — dark theme, super_admin only.
      // Named so `menuStore` can later attach platform-prefixed menu routes
      // via `router.addRoute('PlatformRoot', ...)`.
      path: '/platform',
      name: 'PlatformRoot',
      component: PlatformLayout,
      meta: { requiresScope: 'PLATFORM' },
      children: platformChildren,
    },
    {
      // [Phase 4 / 4.1.3] Tenant shell — green theme, default for every
      // signed-in tenant user (including impersonation sessions).
      // Named so `menuStore` can attach tenant-prefixed menu routes via
      // `router.addRoute('TenantRoot', ...)`.
      path: '/',
      name: 'TenantRoot',
      component: TenantLayout,
      children: [
        ...publicRoutes,
        ...tenantStaticAdminRoutes,
        ...vmsRoutes,
      ],
    },
  ],
})

router.beforeEach(async (to, _from, next) => {
  // 1. noauth routes → pass through
  if (to.meta.requiresAuth === false) {
    next()
    return
  }

  // 2. Not logged in → /login
  const passExam = sessionStorage.getItem('passExam')
  if (!passExam) {
    next('/login')
    return
  }

  // 3. Bootstrap: first protected route access — load user info + menus
  const { useAuthStore } = await import('@/stores/authStore')
  const authStore = useAuthStore()

  authStore._initAxiosCallbacks()

  const { useMenuStore } = await import('@/stores/menuStore')
  const menuStore = useMenuStore()

  if (!menuStore.initialized) {
    // Restore session: if access token was lost (page refresh), use refresh token cookie
    if (!authStore.accessToken) {
      try {
        await authStore.restoreSession()
      } catch {
        authStore.clearAuth()
        next('/login')
        return
      }
    }

    try {
      if (!authStore.userInfo) {
        await authStore.getPermission()
      }
      await menuStore.fetchMyMenus()

      // Load dept options once after login (cached in store).
      // [Phase 5 / ADR-007] Skip for PLATFORM scope: dept is a tenant-only
      // concept and /v1/auth/dept/options is blocked for PLATFORM tokens by
      // ScopeEnforcementFilter. PLATFORM super_admin has no use for dept
      // selectors on the platform shell.
      if (authStore.scope !== 'PLATFORM') {
        const { useDeptStore } = await import('@/stores/deptStore')
        const deptStore = useDeptStore()
        if (!deptStore.initialized) {
          try {
            await deptStore.fetchDeptOptions()
          } catch {
            // Degradation: dept options failed — forms will show empty dept selector
          }
        }
      }
    } catch {
      // Non-auth error: degrade gracefully (show fallback menus)
      if (!menuStore.initialized) {
        menuStore.initialized = true
      }
    }
    // Re-enter to match dynamically added routes
    next({ ...to, replace: true })
    return
  }

  // 4. publicRoutes → only check login (already checked above)
  if (to.meta.isPublic) {
    // [Phase 4 / 4.1.5] Even publicRoutes (Home/Profile/etc.) live under the
    // tenant shell — bounce a PLATFORM-scoped token straight to its own shell.
    const publicScopeRedirect = resolveScopeRedirect(authStore.accessToken, to)
    if (publicScopeRedirect && publicScopeRedirect !== to.fullPath) {
      next(publicScopeRedirect)
      return
    }
    next()
    return
  }

  // 4.5. [Phase 4 / 4.1.5] Scope mismatch → redirect to the natural shell.
  // PLATFORM tokens hitting /admin/* → /platform/tenants;
  // TENANT/IMPERSONATION tokens hitting /platform/* → / or /?impersonating=1.
  const scopeRedirect = resolveScopeRedirect(authStore.accessToken, to)
  if (scopeRedirect && scopeRedirect !== to.fullPath) {
    next(scopeRedirect)
    return
  }

  // 5. [Phase 4 / 4.1.8] The legacy `superAdminOnly` meta has been removed —
  //    scope enforcement above (step 4.5) is the single source of truth for
  //    platform-only routes. Menu-based access still gates everything else.

  // 5.5. Routes with `requiresScope` that already passed step 4.5 are
  //       scope-gated, not menu-gated — allow them without menu check.
  if (to.meta.requiresScope) {
    next()
    return
  }

  // 6. private routes → check menu-based access
  if (!menuStore.hasRouteAccess(to.name as string)) {
    next('/')
    return
  }

  next()
})

export default router
