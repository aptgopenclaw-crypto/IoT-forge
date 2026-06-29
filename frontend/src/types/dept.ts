export interface DeptDto {
  id: number
  pid: number | null
  deptName: string
  deptSort: number | null
  status: number
  createBy: string | null
  updateBy: string | null
  createTime: string | null
  updateTime: string | null
  children?: DeptDto[]
}

export interface DeptOptionVO {
  value: number
  label: string
  pid: number | null
  children?: DeptOptionVO[]
}

export interface CreateDeptRequest {
  deptName: string
  pid: number | null
  deptSort?: number
}

export interface UpdateDeptRequest {
  deptId: number
  deptName: string
  deptSort?: number
  status?: number
}
