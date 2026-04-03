import type {
  AnalyticsCompareRequest,
  AnalyticsCompareResponse,
  AnalyticsQueryRequest,
  AnalyticsQueryResponse,
  Annotation,
  BootstrapResponse,
  ConfigFileResponse,
  CreateAnnotationRequest,
  FilterOptionsResponse,
  SyncStatusResponse,
  UpdateAnnotationRequest,
} from '../types/api'

async function request<T>(input: string, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...init?.headers,
    },
    ...init,
  })

  if (!response.ok) {
    const fallback = `${response.status} ${response.statusText}`
    try {
      const body = (await response.json()) as { detail?: string; title?: string }
      throw new Error(body.detail ?? body.title ?? fallback)
    } catch {
      throw new Error(fallback)
    }
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export function getBootstrap() {
  return request<BootstrapResponse>('/api/bootstrap')
}

export function getConfigFile() {
  return request<ConfigFileResponse>('/api/config')
}

export function updateConfigFile(yaml: string) {
  return request<ConfigFileResponse>('/api/config', {
    method: 'PUT',
    body: JSON.stringify({ yaml }),
  })
}

export function getSyncStatus() {
  return request<SyncStatusResponse>('/api/sync/status')
}

export function runSync() {
  return request<{ accepted: boolean; syncRunId: number; status: string }>('/api/sync/run', {
    method: 'POST',
    body: JSON.stringify({ mode: 'incremental' }),
  })
}

export function stopSync() {
  return request<{ accepted: boolean; running: boolean; status: string }>('/api/sync/stop', {
    method: 'POST',
  })
}

export function getFilterOptions() {
  return request<FilterOptionsResponse>('/api/filters/options')
}

export function queryAnalytics(payload: AnalyticsQueryRequest) {
  return request<AnalyticsQueryResponse>('/api/analytics/query', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function compareAnalytics(payload: AnalyticsCompareRequest) {
  return request<AnalyticsCompareResponse>('/api/analytics/compare', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listAnnotations(from?: string, to?: string) {
  const params = new URLSearchParams()
  if (from) {
    params.set('from', from)
  }
  if (to) {
    params.set('to', to)
  }

  const query = params.toString()
  return request<Annotation[]>(`/api/annotations${query ? `?${query}` : ''}`)
}

export function createAnnotation(payload: CreateAnnotationRequest) {
  return request<Annotation>('/api/annotations', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateAnnotation(annotationId: number, payload: UpdateAnnotationRequest) {
  return request<Annotation>(`/api/annotations/${annotationId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteAnnotation(annotationId: number) {
  return request<void>(`/api/annotations/${annotationId}`, {
    method: 'DELETE',
  })
}
