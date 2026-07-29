import { describe, expect, it } from 'vitest'
import { toCamelCase, toSnakeCase } from './caseConverter'

describe('toCamelCase', () => {
  it('converts snake_case keys recursively', () => {
    expect(toCamelCase({ full_name: 'x', primary_language: 'Java' })).toEqual({
      fullName: 'x',
      primaryLanguage: 'Java',
    })
  })

  it('converts keys inside arrays and nested objects', () => {
    const input = { analyzed_commit_sha: 'abc', suggestions: [{ quality_score: 80 }] }
    expect(toCamelCase(input)).toEqual({
      analyzedCommitSha: 'abc',
      suggestions: [{ qualityScore: 80 }],
    })
  })

  it('leaves primitives, null and arrays of primitives untouched', () => {
    expect(toCamelCase(42)).toBe(42)
    expect(toCamelCase(null)).toBeNull()
    expect(toCamelCase(['a', 'b'])).toEqual(['a', 'b'])
  })
})

describe('toSnakeCase', () => {
  it('converts camelCase keys recursively', () => {
    expect(toSnakeCase({ repositoryId: '1', qualityScore: 90 })).toEqual({
      repository_id: '1',
      quality_score: 90,
    })
  })

  it('round-trips with toCamelCase', () => {
    const original = { githubRepoId: 10, primaryLanguage: 'Java' }
    expect(toCamelCase(toSnakeCase(original))).toEqual(original)
  })
})
