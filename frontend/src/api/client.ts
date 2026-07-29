import axios, { type AxiosError } from 'axios'
import { toCamelCase, toSnakeCase } from '../lib/caseConverter'

export const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  // Backend usa CookieCsrfTokenRepository (cookie XSRF-TOKEN); o Axios precisa
  // ser instruido a le-lo e reenviar como X-XSRF-TOKEN em toda mutacao.
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

api.interceptors.request.use((config) => {
  if (config.data && typeof config.data === 'object') {
    config.data = toSnakeCase(config.data)
  }
  return config
})

api.interceptors.response.use((response) => {
  if (response.data && typeof response.data === 'object') {
    response.data = toCamelCase(response.data)
  }
  return response
})

interface BackendErrorBody {
  error?: { code?: string; message?: string; status?: number }
}

/** Extrai a mensagem legivel do envelope {error:{...}} do backend, nunca o texto generico do Axios. */
export function apiErrorMessage(err: unknown, fallback = 'Algo deu errado. Tente novamente.'): string {
  const axiosErr = err as AxiosError<BackendErrorBody>
  return axiosErr?.response?.data?.error?.message ?? fallback
}

export function isUnauthorized(err: unknown): boolean {
  const axiosErr = err as AxiosError
  return axiosErr?.response?.status === 401
}
