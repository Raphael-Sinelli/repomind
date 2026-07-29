export interface User {
  id: string
  githubId: number
  username: string
  email: string | null
  avatarUrl: string | null
  createdAt: string
}

export interface Repository {
  id: string
  githubRepoId: number
  fullName: string
  description: string | null
  stars: number
  primaryLanguage: string | null
  lastSyncedAt: string | null
}

export interface Analysis {
  id: string
  repositoryId: string
  summary: string
  qualityScore: number
  suggestions: string[]
  modelUsed: string
  analyzedCommitSha: string
  createdAt: string
}
