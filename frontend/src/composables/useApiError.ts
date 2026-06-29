import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import errorCodes from '@/generated/error-codes.json'

/**
 * API 錯誤型別 (Axios error shape)
 */
export interface ApiError {
  response?: {
    data?: {
      errorCode?: string
      errorMsg?: string
    }
  }
}

/**
 * 從 API 錯誤中提取 errorCode
 */
export function getErrorCode(err: unknown): string | undefined {
  const error = err as ApiError
  return error?.response?.data?.errorCode
}

/**
 * 共用 API 錯誤處理 composable
 *
 * @example
 * const { handleError } = useApiError()
 *
 * try {
 *   await createUser(payload)
 * } catch (err) {
 *   handleError(err, {
 *     '20001': t('user.create.emailExists'),
 *     '20005': t('user.create.userNotFound'),
 *   }, t('user.create.createFailed'))
 * }
 */
export function useApiError() {
  const { t } = useI18n()

  /**
   * 處理 API 錯誤：根據 errorCode 映射顯示對應訊息
   *
   * @param err - catch 到的錯誤物件
   * @param codeMessages - errorCode → 使用者訊息映射表
   * @param fallbackMessage - 無法匹配時的預設錯誤訊息
   * @returns errorCode (方便呼叫端做額外處理)
   */
  function handleError(
    err: unknown,
    codeMessages?: Record<string, string>,
    fallbackMessage?: string,
  ): string | undefined {
    const errorCode = getErrorCode(err)
    const msg =
      (errorCode && codeMessages?.[errorCode]) ||
      (errorCode && (errorCodes as Record<string, string>)[errorCode]) ||
      fallbackMessage ||
      t('common.operationFailed')
    ElMessage.error(msg)
    return errorCode
  }

  return { handleError, getErrorCode }
}
