function snakeToCamel(key: string): string {
  return key.replace(/_([a-z0-9])/g, (_, c: string) => c.toUpperCase())
}

function camelToSnake(key: string): string {
  return key.replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`)
}

function convertKeys<T>(value: T, convert: (key: string) => string): T {
  if (Array.isArray(value)) {
    return value.map((item) => convertKeys(item, convert)) as T
  }
  if (value !== null && typeof value === 'object' && !(value instanceof Date)) {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([key, val]) => [
        convert(key),
        convertKeys(val, convert),
      ]),
    ) as T
  }
  return value
}

export function toCamelCase<T>(value: T): T {
  return convertKeys(value, snakeToCamel)
}

export function toSnakeCase<T>(value: T): T {
  return convertKeys(value, camelToSnake)
}
