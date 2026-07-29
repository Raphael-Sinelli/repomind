import { api } from './client'
import type { User } from './types'

export const authApi = {
  async me(): Promise<User> {
    const response = await api.get<User>('/me')
    return response.data
  },

  async logout(): Promise<void> {
    await api.post('/logout')
  },
}

export const GITHUB_LOGIN_URL = '/oauth2/authorization/github'
