import { useParams, Link } from 'react-router-dom'
import { useRepositories } from '../hooks/useRepositories'
import { useAnalysisHistory, useRunAnalysis } from '../hooks/useAnalyses'
import { Header } from '../components/Header'
import { Spinner } from '../components/Spinner'
import { ErrorBanner } from '../components/ErrorBanner'
import { apiErrorMessage } from '../api/client'
import type { Analysis } from '../api/types'

function ScoreBadge({ score }: { score: number }) {
  const color =
    score >= 75
      ? 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300'
      : score >= 50
        ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
        : 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300'

  return (
    <span className={`rounded-full px-3 py-1 text-sm font-semibold ${color}`}>{score}/100</span>
  )
}

function AnalysisCard({ analysis }: { analysis: Analysis }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
      <div className="flex items-center justify-between gap-4">
        <ScoreBadge score={analysis.qualityScore} />
        <span className="text-xs text-slate-400 dark:text-slate-500">
          {new Date(analysis.createdAt).toLocaleString()} · {analysis.modelUsed}
        </span>
      </div>
      <p className="mt-3 text-slate-700 dark:text-slate-300">{analysis.summary}</p>
      {analysis.suggestions.length > 0 && (
        <ul className="mt-3 list-inside list-disc space-y-1 text-sm text-slate-600 dark:text-slate-400">
          {analysis.suggestions.map((suggestion, i) => (
            <li key={i}>{suggestion}</li>
          ))}
        </ul>
      )}
      <span className="mt-3 block font-mono text-xs text-slate-400 dark:text-slate-600">
        commit {analysis.analyzedCommitSha.slice(0, 7)}
      </span>
    </div>
  )
}

export function RepositoryDetailPage() {
  const { id } = useParams<{ id: string }>()
  const repositoryId = id!

  const { data: repositories } = useRepositories()
  const repository = repositories?.find((r) => r.id === repositoryId)

  const { data: history, isLoading: historyLoading } = useAnalysisHistory(repositoryId)
  const runAnalysis = useRunAnalysis(repositoryId)

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
      <Header />
      <main className="mx-auto max-w-3xl px-6 py-8">
        <Link to="/repositories" className="text-sm text-slate-500 hover:underline dark:text-slate-400">
          ← Repositorios
        </Link>

        <div className="mt-2 flex items-center justify-between">
          <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">
            {repository?.fullName ?? 'Repositorio'}
          </h1>
          <button
            onClick={() => runAnalysis.mutate()}
            disabled={runAnalysis.isPending}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-700 disabled:opacity-50 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
          >
            {runAnalysis.isPending ? 'Analisando...' : 'Analisar'}
          </button>
        </div>

        {runAnalysis.isError && (
          <div className="mt-4">
            <ErrorBanner message={apiErrorMessage(runAnalysis.error)} />
          </div>
        )}

        <div className="mt-6 flex flex-col gap-4">
          {historyLoading && <Spinner label="Carregando historico..." />}
          {!historyLoading && history?.length === 0 && !runAnalysis.data && (
            <p className="text-slate-500 dark:text-slate-400">
              Nenhuma analise ainda. Clique em "Analisar" para comecar.
            </p>
          )}
          {history?.map((analysis) => (
            <AnalysisCard key={analysis.id} analysis={analysis} />
          ))}
        </div>
      </main>
    </div>
  )
}
