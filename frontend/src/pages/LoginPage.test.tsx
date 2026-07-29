import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { LoginPage } from './LoginPage'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <LoginPage />
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  it('shows a link to the real GitHub OAuth authorization endpoint', () => {
    renderAt('/login')
    expect(screen.getByRole('link', { name: /entrar com github/i })).toHaveAttribute(
      'href',
      '/oauth2/authorization/github',
    )
  })

  it('shows an error banner only when redirected back with ?error=oauth', () => {
    renderAt('/login')
    expect(screen.queryByText(/nao foi possivel concluir o login/i)).not.toBeInTheDocument()

    renderAt('/login?error=oauth')
    expect(screen.getByText(/nao foi possivel concluir o login/i)).toBeInTheDocument()
  })
})
