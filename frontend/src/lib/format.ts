export function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(value)
}

export function formatPercent(value: number | null): string {
  if (value === null) {
    return 'n/a'
  }
  return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`
}

export function formatDate(iso: string | null): string {
  if (!iso) {
    return '—'
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(date)
}

export function formatDateTime(iso: string | null): string {
  if (!iso) {
    return 'Never'
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return 'Never'
  }
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

export function shiftDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return toIsoDate(date)
}
