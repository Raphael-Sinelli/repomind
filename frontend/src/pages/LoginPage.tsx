import { useSearchParams } from 'react-router-dom'
import { GITHUB_LOGIN_URL } from '../api/auth'
import { ErrorBanner } from '../components/ErrorBanner'

export function LoginPage() {
  const [searchParams] = useSearchParams()
  const oauthFailed = searchParams.get('error') === 'oauth'

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center gap-6 px-4 text-center">
      <div>
        <h1 className="text-3xl font-semibold text-slate-900 dark:text-slate-100">RepoMind</h1>
        <p className="mt-2 text-slate-500 dark:text-slate-400">
          Analise a qualidade dos seus repositorios GitHub com IA.
        </p>
      </div>

      {oauthFailed && (
        <ErrorBanner message="Nao foi possivel concluir o login com o GitHub. Tente novamente." />
      )}

      <a
        href={GITHUB_LOGIN_URL}
        className="flex items-center gap-2 rounded-lg bg-slate-900 px-5 py-3 font-medium text-white transition hover:bg-slate-700 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
      >
        Entrar com GitHub
      </a>
    </div>
  )
}
