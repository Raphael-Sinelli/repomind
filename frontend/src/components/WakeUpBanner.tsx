import { useEffect, useState } from 'react'

// Modulo, nao estado de componente: garante que o ping so dispara uma vez por
// carregamento de pagina, mesmo com StrictMode remontando o componente em dev
// ou o usuario navegando entre rotas (App nao remonta em troca de rota, mas o
// guard deixa isso explicito em vez de depender so desse detalhe do React Router).
let pingStarted = false

export function WakeUpBanner() {
  const [waking, setWaking] = useState(false)

  useEffect(() => {
    if (pingStarted) return
    pingStarted = true

    const showDelay = setTimeout(() => setWaking(true), 800)

    fetch('/api/v1/me', { credentials: 'include' }).finally(() => {
      clearTimeout(showDelay)
      setWaking(false)
    })

    return () => clearTimeout(showDelay)
  }, [])

  if (!waking) return null

  return (
    <div className="flex items-center justify-center gap-2 bg-amber-100 px-4 py-2 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-200">
      <span className="h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent" />
      Acordando o servidor, ~30s na primeira vez...
    </div>
  )
}
