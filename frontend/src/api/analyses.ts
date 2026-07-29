import { api } from './client'
import type { Analysis } from './types'

export const analysesApi = {
  async run(repositoryId: string): Promise<Analysis> {
    const response = await api.post<Analysis>(`/repositories/${repositoryId}/analyses`)
    return response.data
  },

  async history(repositoryId: string): Promise<Analysis[]> {
    const response = await api.get<Analysis[]>(`/repositories/${repositoryId}/analyses`)
    return response.data
  },
}
