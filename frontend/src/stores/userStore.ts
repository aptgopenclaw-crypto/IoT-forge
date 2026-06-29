import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { listUsers, getUserTenantRoles } from '@/api/user'
import type { UserListItemDto, UserTenantMappingDto, UserListQuery } from '@/types/user'

export const useUserStore = defineStore('user', () => {
  const userList = ref<UserListItemDto[]>([])
  const pagination = reactive({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  })
  const tenantRoles = ref<UserTenantMappingDto[]>([])

  async function fetchUserList(params: UserListQuery) {
    const res = await listUsers(params)
    const data = res.body
    userList.value = data.content
    pagination.page = data.page
    pagination.size = data.size
    pagination.totalElements = data.totalElements
    pagination.totalPages = data.totalPages
  }

  async function fetchTenantRoles(userId: string) {
    const res = await getUserTenantRoles(userId)
    tenantRoles.value = res.body
  }

  return { userList, pagination, tenantRoles, fetchUserList, fetchTenantRoles }
})
