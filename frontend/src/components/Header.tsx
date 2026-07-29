import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '../api/auth'
import { useMe } from '../hooks/useAuth'

export function Header() {
  const { data: user } = useMe()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  async function handleLogout() {
    await authApi.logout()
    queryClient.clear()
    navigate('/login', { replace: true })
  }

  return (
    <header className="flex items-center justify-between border-b border-slate-200 px-6 py-4 dark:border-slate-800">
      <span className="text-lg font-semibold text-slate-900 dark:text-slate-100">RepoMind</span>
      {user && (
        <div className="flex items-center gap-3">
          {user.avatarUrl && (
            <img src={user.avatarUrl} alt={user.username} className="h-8 w-8 rounded-full" />
          )}
          <span className="text-sm text-slate-600 dark:text-slate-300">{user.username}</span>
          <button
            onClick={handleLogout}
            className="text-sm text-slate-500 underline-offset-2 hover:underline dark:text-slate-400"
          >
            Sair
          </button>
        </div>
      )}
    </header>
  )
}
