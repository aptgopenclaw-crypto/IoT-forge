import { describe, it, expect, beforeEach } from 'vitest'
import axios from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'

// Import the module to test — axiosIns is the default export
import axiosIns from '@/api/axios/axiosIns'

describe('axiosIns', () => {
  describe('default headers', () => {
    it('should include X-Requested-With: XMLHttpRequest', () => {
      const headers = axiosIns.defaults.headers as Record<string, unknown>
      expect(headers['X-Requested-With']).toBe('XMLHttpRequest')
    })

    it('should include Content-Type: application/json', () => {
      const headers = axiosIns.defaults.headers as Record<string, unknown>
      expect(headers['Content-Type']).toBe('application/json')
    })
  })

  describe('base configuration', () => {
    it('should have baseURL from VITE_API_BASE_URL env variable', () => {
      expect(axiosIns.defaults.baseURL).toBe(import.meta.env.VITE_API_BASE_URL || '/v1')
    })

    it('should have timeout 15000ms', () => {
      expect(axiosIns.defaults.timeout).toBe(15000)
    })

    it('should have withCredentials true', () => {
      expect(axiosIns.defaults.withCredentials).toBe(true)
    })
  })

  describe('request interceptor', () => {
    it('should attach Accept-Language from localStorage', async () => {
      localStorage.setItem('locale', 'zh-TW')

      // Simulate the interceptor by getting it from the manager
      const interceptors = (axiosIns.interceptors.request as unknown as { handlers: Array<{ fulfilled: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }> }).handlers
      const requestInterceptor = interceptors.find((h) => h.fulfilled)

      const mockConfig = {
        headers: new axios.AxiosHeaders(),
      } as InternalAxiosRequestConfig

      const result = requestInterceptor!.fulfilled(mockConfig)
      expect(result.headers.get('Accept-Language')).toBe('zh-TW')

      localStorage.removeItem('locale')
    })

    it('should not set Accept-Language when locale is not stored', async () => {
      localStorage.removeItem('locale')

      const interceptors = (axiosIns.interceptors.request as unknown as { handlers: Array<{ fulfilled: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }> }).handlers
      const requestInterceptor = interceptors.find((h) => h.fulfilled)

      const mockConfig = {
        headers: new axios.AxiosHeaders(),
      } as InternalAxiosRequestConfig

      const result = requestInterceptor!.fulfilled(mockConfig)
      expect(result.headers.get('Accept-Language')).toBeFalsy()
    })

    it('should extend timeout to 60s for responseType blob', () => {
      const interceptors = (axiosIns.interceptors.request as unknown as { handlers: Array<{ fulfilled: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }> }).handlers
      const requestInterceptor = interceptors.find((h) => h.fulfilled)

      const mockConfig = {
        headers: new axios.AxiosHeaders(),
        responseType: 'blob',
        url: '/auth/some-resource',
      } as InternalAxiosRequestConfig

      const result = requestInterceptor!.fulfilled(mockConfig)
      expect(result.timeout).toBe(60_000)
    })

    it('should extend timeout to 60s for URL containing /export', () => {
      const interceptors = (axiosIns.interceptors.request as unknown as { handlers: Array<{ fulfilled: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }> }).handlers
      const requestInterceptor = interceptors.find((h) => h.fulfilled)

      const mockConfig = {
        headers: new axios.AxiosHeaders(),
        url: '/auth/audit/user/usage/history/export',
      } as InternalAxiosRequestConfig

      const result = requestInterceptor!.fulfilled(mockConfig)
      expect(result.timeout).toBe(60_000)
    })

    it('should extend timeout to 60s for URL containing /download', () => {
      const interceptors = (axiosIns.interceptors.request as unknown as { handlers: Array<{ fulfilled: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }> }).handlers
      const requestInterceptor = interceptors.find((h) => h.fulfilled)

      const mockConfig = {
        headers: new axios.AxiosHeaders(),
        url: '/auth/announcements/1/attachments/2/download',
      } as InternalAxiosRequestConfig

      const result = requestInterceptor!.fulfilled(mockConfig)
      expect(result.timeout).toBe(60_000)
    })

    it('should NOT extend timeout for normal JSON requests', () => {
      const interceptors = (axiosIns.interceptors.request as unknown as { handlers: Array<{ fulfilled: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }> }).handlers
      const requestInterceptor = interceptors.find((h) => h.fulfilled)

      const mockConfig = {
        headers: new axios.AxiosHeaders(),
        url: '/auth/users',
        timeout: 15000,
      } as InternalAxiosRequestConfig

      const result = requestInterceptor!.fulfilled(mockConfig)
      expect(result.timeout).toBe(15000)
    })
  })
})
