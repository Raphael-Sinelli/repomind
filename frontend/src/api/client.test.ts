import { describe, expect, it } from 'vitest'
import { apiErrorMessage, isUnauthorized } from './client'

function axiosErrorWithStatus(status: number, body?: unknown) {
  return { response: { status, data: body } }
}

describe('apiErrorMessage', () => {
  it('extracts the backend message from the {error:{message}} envelope', () => {
    const err = axiosErrorWithStatus(400, { error: { message: 'Repositorio invalido.' } })
    expect(apiErrorMessage(err)).toBe('Repositorio invalido.')
  })

  it('falls back to a generic message when the envelope is missing', () => {
    const err = axiosErrorWithStatus(500, { message: 'Internal Server Error' })
    expect(apiErrorMessage(err)).toBe('Algo deu errado. Tente novamente.')
  })

  it('never surfaces raw axios text like "Request failed with status code 400"', () => {
    const err = { message: 'Request failed with status code 400' }
    expect(apiErrorMessage(err)).toBe('Algo deu errado. Tente novamente.')
  })
})

describe('isUnauthorized', () => {
  it('is true only for 401 responses', () => {
    expect(isUnauthorized(axiosErrorWithStatus(401))).toBe(true)
    expect(isUnauthorized(axiosErrorWithStatus(403))).toBe(false)
    expect(isUnauthorized(axiosErrorWithStatus(200))).toBe(false)
  })
})
