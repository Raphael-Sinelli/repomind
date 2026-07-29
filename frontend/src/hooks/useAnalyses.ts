import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { analysesApi } from '../api/analyses'
import type { Analysis } from '../api/types'

export function useAnalysisHistory(repositoryId: string) {
  return useQuery({
    queryKey: ['analyses', repositoryId],
    queryFn: () => analysesApi.history(repositoryId),
  })
}

export function useRunAnalysis(repositoryId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => analysesApi.run(repositoryId),
    onSuccess: (result) => {
      queryClient.setQueryData<Analysis[]>(['analyses', repositoryId], (old) => {
        if (!old) return [result]
        if (old.some((a) => a.id === result.id)) return old
        return [result, ...old]
      })
    },
  })
}
