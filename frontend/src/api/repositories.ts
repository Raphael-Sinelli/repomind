import { api } from './client'
import type { Repository } from './types'

export const repositoriesApi = {
  async list(refresh = false): Promise<Repository[]> {
    const response = await api.get<Repository[]>('/repositories', {
      params: refresh ? { refresh: true } : undefined,
    })
    return response.data
  },
}
