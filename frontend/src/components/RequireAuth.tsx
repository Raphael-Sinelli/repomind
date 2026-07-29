import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useMe } from '../hooks/useAuth'
import { Spinner } from './Spinner'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { data: user, isLoading, isError } = useMe()

  if (isLoading) return <Spinner label="Verificando sessao..." />
  if (isError || !user) return <Navigate to="/login" replace />

  return children
}
