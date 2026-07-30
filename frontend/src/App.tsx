import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from './pages/LoginPage'
import { RepositoriesPage } from './pages/RepositoriesPage'
import { RepositoryDetailPage } from './pages/RepositoryDetailPage'
import { RequireAuth } from './components/RequireAuth'
import { WakeUpBanner } from './components/WakeUpBanner'

export default function App() {
  return (
    <BrowserRouter>
      <WakeUpBanner />
      <Routes>
        <Route path="/" element={<Navigate to="/repositories" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/repositories"
          element={
            <RequireAuth>
              <RepositoriesPage />
            </RequireAuth>
          }
        />
        <Route
          path="/repositories/:id"
          element={
            <RequireAuth>
              <RepositoryDetailPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/repositories" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
