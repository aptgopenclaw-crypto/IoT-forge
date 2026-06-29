import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getMenuTree, getMyMenus } from '@/api/rbac'
import type { MenuDto, UserMenuDto, BreadcrumbItem } from '@/types/rbac'
import router from '@/router'
import type { RouteRecordRaw } from 'vue-router'

// Dynamic component map — lazy-loaded views
const viewModules = import.meta.glob('@/views/**/*.vue')

function resolveComponent(component: string | null) {
  if (!component) return undefined
  const key = `/src/${component}`
  return viewModules[key]
}

function buildRoutesFromMenus(menus: UserMenuDto[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []
  for (const menu of menus) {
    if (menu.menuType === 'BUTTON') continue
    if (menu.menuType === 'DIRECTORY') {
      if (menu.children?.length) {
        routes.push(...buildRoutesFromMenus(menu.children))
      }
      continue
    }
    if (menu.routeName && menu.routePath) {
      const comp = resolveComponent(menu.component)
      if (comp) {
        routes.push({
          path: menu.routePath,
          name: menu.routeName,
          component: comp,
          meta: {
            menuId: menu.menuId,
            keepAlive: false,
          },
        })
      }
    }
  }
  return routes
}

function flattenMenuNames(menus: UserMenuDto[]): Set<string> {
  const names = new Set<string>()
  for (const menu of menus) {
    if (menu.routeName) names.add(menu.routeName)
    if (menu.children?.length) {
      for (const name of flattenMenuNames(menu.children)) {
        names.add(name)
      }
    }
  }
  return names
}

function flattenMenuTree(tree: UserMenuDto[]): UserMenuDto[] {
  const result: UserMenuDto[] = []
  for (const menu of tree) {
    result.push(menu)
    if (menu.children?.length) {
      result.push(...flattenMenuTree(menu.children))
    }
  }
  return result
}

function findBreadcrumbItems(
  menus: UserMenuDto[],
  routeName: string,
  path: BreadcrumbItem[] = [],
): BreadcrumbItem[] | null {
  for (const menu of menus) {
    const currentPath: BreadcrumbItem[] = [
      ...path,
      { label: menu.name, path: menu.routePath ?? null },
    ]
    if (menu.routeName === routeName) return currentPath
    if (menu.children?.length) {
      const found = findBreadcrumbItems(menu.children, routeName, currentPath)
      if (found) return found
    }
  }
  return null
}

// Track dynamically added route names so we can remove them on re-fetch
let dynamicRouteNames: string[] = []

export const useMenuStore = defineStore('menu', () => {
  // ── State ──
  const menuTree = ref<MenuDto[]>([])
  const userMenus = ref<UserMenuDto[]>([])
  const loading = ref(false)
  const initialized = ref(false)

  // ── Getters ──
  const sidebarMenus = computed(() => userMenus.value)

  // ── Actions ──
  async function fetchMyMenus() {
    loading.value = true
    try {
      const res = await getMyMenus()
      userMenus.value = res.body

      // Remove previously injected dynamic routes
      for (const name of dynamicRouteNames) {
        if (router.hasRoute(name)) {
          router.removeRoute(name)
        }
      }
      dynamicRouteNames = []

      // [Phase 4 / 4.1.3] Dynamic routes are nested under the matching layout
      // shell — `/platform/*` paths render inside PlatformLayout, every other
      // path renders inside TenantLayout. Both parents are named routes
      // registered in router/index.ts.
      const routes = buildRoutesFromMenus(userMenus.value)
      for (const route of routes) {
        if (route.name && !router.hasRoute(route.name as string)) {
          const parentName =
            typeof route.path === 'string' && route.path.startsWith('/platform')
              ? 'PlatformRoot'
              : 'TenantRoot'
          router.addRoute(parentName, route)
          dynamicRouteNames.push(route.name as string)
        }
      }
      initialized.value = true
    } catch (e) {
      initialized.value = true
      throw e
    } finally {
      loading.value = false
    }
  }

  async function fetchMenuTree() {
    loading.value = true
    try {
      const res = await getMenuTree()
      menuTree.value = res.body
    } finally {
      loading.value = false
    }
  }

  function hasRouteAccess(routeName: string | symbol | null | undefined): boolean {
    if (!routeName || typeof routeName === 'symbol') return false
    const allowedNames = flattenMenuNames(userMenus.value)
    const implicitChildren: Record<string, string> = {
      EditUser: 'UserList',
      CreateUser: 'UserList',
      RepairTicketDetail: 'RepairTicket',
      InspectionRecord: 'InspectionTask',
      ReplacementOrderDetail: 'ReplacementOrder',
      ReplacementSelfCheck: 'ReplacementOrder',
    }
    const parent = implicitChildren[routeName]
    if (parent) return allowedNames.has(parent)
    return allowedNames.has(routeName)
  }

  function getBreadcrumbs(routeName: string): BreadcrumbItem[] {
    return findBreadcrumbItems(userMenus.value, routeName) ?? []
  }

  function flattenTree(tree?: UserMenuDto[]): UserMenuDto[] {
    return flattenMenuTree(tree ?? userMenus.value)
  }

  function $reset() {
    for (const name of dynamicRouteNames) {
      if (router.hasRoute(name)) {
        router.removeRoute(name)
      }
    }
    dynamicRouteNames = []
    menuTree.value = []
    userMenus.value = []
    loading.value = false
    initialized.value = false
  }

  return {
    // State
    menuTree,
    userMenus,
    loading,
    initialized,
    // Getters
    sidebarMenus,
    // Actions
    fetchMyMenus,
    fetchMenuTree,
    hasRouteAccess,
    getBreadcrumbs,
    flattenMenuTree: flattenTree,
    $reset,
  }
})
