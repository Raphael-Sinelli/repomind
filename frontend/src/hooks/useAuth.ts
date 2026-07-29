import { useQuery } from '@tanstack/react-query'
import { authApi } from '../api/auth'
import { isUnauthorized } from '../api/client'

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: authApi.me,
    retry: (failureCount, err) => !isUnauthorized(err) && failureCount < 2,
  })
}
