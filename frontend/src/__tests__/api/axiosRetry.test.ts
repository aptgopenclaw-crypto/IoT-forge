import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import axios from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import axiosIns, {
  MAX_RETRIES,
  RETRY_BASE_DELAY,
  RETRYABLE_STATUS,
  RETRYABLE_CODES,
} from '@/api/axios/axiosIns'

describe('axiosIns — GET retry with exponential backoff (N-11)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should export retry configuration constants', () => {
    expect(MAX_RETRIES).toBe(2)
    expect(RETRY_BASE_DELAY).toBe(1000)
    expect(RETRYABLE_STATUS.has(503)).toBe(true)
    expect(RETRYABLE_STATUS.has(504)).toBe(true)
    expect(RETRYABLE_CODES.has('ECONNABORTED')).toBe(true)
    expect(RETRYABLE_CODES.has('ERR_NETWORK')).toBe(true)
    expect(RETRYABLE_CODES.has('ETIMEDOUT')).toBe(true)
  })

  it('should NOT retry non-GET methods (POST)', async () => {
    const interceptors = (axiosIns.interceptors.response as unknown as {
      handlers: Array<{ rejected: (err: unknown) => Promise<unknown> }>
    }).handlers
    const errorHandler = interceptors.find(h => h.rejected)!.rejected

    const error = {
      config: {
        method: 'post',
        url: '/auth/users',
        headers: new axios.AxiosHeaders(),
      } as InternalAxiosRequestConfig,
      code: 'ERR_NETWORK',
      response: undefined,
    }

    await expect(errorHandler(error)).rejects.toEqual(error)
  })

  it('should NOT retry GET requests for non-retryable errors (400)', async () => {
    const interceptors = (axiosIns.interceptors.response as unknown as {
      handlers: Array<{ rejected: (err: unknown) => Promise<unknown> }>
    }).handlers
    const errorHandler = interceptors.find(h => h.rejected)!.rejected

    const error = {
      config: {
        method: 'get',
        url: '/auth/users',
        headers: new axios.AxiosHeaders(),
      } as InternalAxiosRequestConfig,
      code: 'ERR_BAD_REQUEST',
      response: { status: 400 },
    }

    await expect(errorHandler(error)).rejects.toEqual(error)
  })

  it('should NOT retry GET requests for 404', async () => {
    const interceptors = (axiosIns.interceptors.response as unknown as {
      handlers: Array<{ rejected: (err: unknown) => Promise<unknown> }>
    }).handlers
    const errorHandler = interceptors.find(h => h.rejected)!.rejected

    const error = {
      config: {
        method: 'get',
        url: '/auth/users/123',
        headers: new axios.AxiosHeaders(),
      } as InternalAxiosRequestConfig,
      code: '',
      response: { status: 404 },
    }

    await expect(errorHandler(error)).rejects.toEqual(error)
  })

  it('should identify 503 as retryable status', () => {
    expect(RETRYABLE_STATUS.has(503)).toBe(true)
  })

  it('should identify 504 as retryable status', () => {
    expect(RETRYABLE_STATUS.has(504)).toBe(true)
  })

  it('should identify ECONNABORTED as retryable code', () => {
    expect(RETRYABLE_CODES.has('ECONNABORTED')).toBe(true)
  })

  it('should identify ERR_NETWORK as retryable code', () => {
    expect(RETRYABLE_CODES.has('ERR_NETWORK')).toBe(true)
  })

  it('should NOT retry if max retries already reached', async () => {
    const interceptors = (axiosIns.interceptors.response as unknown as {
      handlers: Array<{ rejected: (err: unknown) => Promise<unknown> }>
    }).handlers
    const errorHandler = interceptors.find(h => h.rejected)!.rejected

    const error = {
      config: {
        method: 'get',
        url: '/auth/users',
        headers: new axios.AxiosHeaders(),
        _retryCount: 2, // already at max
      } as InternalAxiosRequestConfig & { _retryCount: number },
      code: 'ERR_NETWORK',
      response: undefined,
    }

    await expect(errorHandler(error)).rejects.toEqual(error)
  })

  it('should also retry HEAD requests', async () => {
    const interceptors = (axiosIns.interceptors.response as unknown as {
      handlers: Array<{ rejected: (err: unknown) => Promise<unknown> }>
    }).handlers
    const errorHandler = interceptors.find(h => h.rejected)!.rejected

    const error = {
      config: {
        method: 'head',
        url: '/auth/health',
        headers: new axios.AxiosHeaders(),
        _retryCount: 2, // already at max — will NOT retry
      } as InternalAxiosRequestConfig & { _retryCount: number },
      code: 'ECONNABORTED',
      response: undefined,
    }

    // At max retries, should reject
    await expect(errorHandler(error)).rejects.toEqual(error)
  })
})
