import { useQuery } from '@tanstack/react-query'
import { repositoriesApi } from '../api/repositories'

export function useRepositories(refresh = false) {
  return useQuery({
    queryKey: ['repositories'],
    queryFn: () => repositoriesApi.list(refresh),
  })
}
