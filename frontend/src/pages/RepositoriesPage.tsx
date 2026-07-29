import { Link } from 'react-router-dom'
import { useRepositories } from '../hooks/useRepositories'
import { Header } from '../components/Header'
import { Spinner } from '../components/Spinner'
import { ErrorBanner } from '../components/ErrorBanner'
import { apiErrorMessage } from '../api/client'

export function RepositoriesPage() {
  const { data: repositories, isLoading, isError, error } = useRepositories()

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
      <Header />
      <main className="mx-auto max-w-3xl px-6 py-8">
        <h1 className="mb-6 text-2xl font-semibold text-slate-900 dark:text-slate-100">
          Seus repositorios
        </h1>

        {isLoading && <Spinner label="Buscando repositorios..." />}
        {isError && <ErrorBanner message={apiErrorMessage(error)} />}

        {repositories && repositories.length === 0 && (
          <p className="text-slate-500 dark:text-slate-400">Nenhum repositorio encontrado.</p>
        )}

        <ul className="flex flex-col gap-3">
          {repositories?.map((repo) => (
            <li key={repo.id}>
              <Link
                to={`/repositories/${repo.id}`}
                className="block rounded-lg border border-slate-200 bg-white px-4 py-3 transition hover:border-slate-400 dark:border-slate-800 dark:bg-slate-900 dark:hover:border-slate-600"
              >
                <div className="flex items-center justify-between">
                  <span className="font-medium text-slate-900 dark:text-slate-100">
                    {repo.fullName}
                  </span>
                  <span className="text-sm text-slate-500 dark:text-slate-400">
                    ★ {repo.stars}
                  </span>
                </div>
                {repo.description && (
                  <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                    {repo.description}
                  </p>
                )}
                {repo.primaryLanguage && (
                  <span className="mt-2 inline-block text-xs text-slate-400 dark:text-slate-500">
                    {repo.primaryLanguage}
                  </span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      </main>
    </div>
  )
}
