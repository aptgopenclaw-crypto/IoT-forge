import dayjs from 'dayjs'

export const formatDateTime = (iso: string) => dayjs(iso).format('YYYY-MM-DD HH:mm:ss')

export const formatDate = (iso: string) => dayjs(iso).format('YYYY-MM-DD')
