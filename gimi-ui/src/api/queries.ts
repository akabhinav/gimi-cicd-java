import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import { mockData } from './mock-data'
import type { Pipeline, PipelineRun, Worker, DORAMetrics, FeatureFlag, SecurityScan, Template, Connector, AuditEvent, SLO } from '@/types'

const USE_MOCK = true

function useMockOrApi<T>(key: string[], mockFn: () => T, apiFn: () => Promise<T>) {
  return useQuery({
    queryKey: key,
    queryFn: USE_MOCK ? () => Promise.resolve(mockFn()) : apiFn,
    staleTime: 30000,
  })
}

export function usePipelines() {
  return useMockOrApi<Pipeline[]>(['pipelines'], () => mockData.pipelines, () => api.get('/pipelines'))
}

export function usePipeline(id: string) {
  return useMockOrApi<Pipeline>(['pipeline', id], () => mockData.pipelines.find(p => p.id === id)!, () => api.get(`/pipelines/${id}`))
}

export function usePipelineRuns(pipelineId?: string) {
  return useMockOrApi<PipelineRun[]>(
    ['runs', pipelineId ?? 'all'],
    () => pipelineId ? mockData.runs.filter(r => r.pipelineId === pipelineId) : mockData.runs,
    () => api.get(pipelineId ? `/pipelines/${pipelineId}/runs` : '/runs')
  )
}

export function usePipelineRun(runId: string) {
  return useMockOrApi<PipelineRun>(['run', runId], () => mockData.runs.find(r => r.id === runId)!, () => api.get(`/runs/${runId}`))
}

export function useWorkers() {
  return useMockOrApi<Worker[]>(['workers'], () => mockData.workers, () => api.get('/workers'))
}

export function useDORAMetrics() {
  return useMockOrApi<DORAMetrics>(['dora-metrics'], () => mockData.doraMetrics, () => api.get('/analytics/dora'))
}

export function useFeatureFlags() {
  return useMockOrApi<FeatureFlag[]>(['feature-flags'], () => mockData.featureFlags, () => api.get('/feature-flags'))
}

export function useSecurityScans() {
  return useMockOrApi<SecurityScan[]>(['security-scans'], () => mockData.securityScans, () => api.get('/security/scans'))
}

export function useTemplates() {
  return useMockOrApi<Template[]>(['templates'], () => mockData.templates, () => api.get('/templates'))
}

export function useConnectors() {
  return useMockOrApi<Connector[]>(['connectors'], () => mockData.connectors, () => api.get('/connectors'))
}

export function useAuditEvents() {
  return useMockOrApi<AuditEvent[]>(['audit-events'], () => mockData.auditEvents, () => api.get('/audit'))
}

export function useSLOs() {
  return useMockOrApi<SLO[]>(['slos'], () => mockData.slos, () => api.get('/slo'))
}

export function useRunPipeline() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (pipelineId: string) => api.post(`/pipelines/${pipelineId}/run`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['runs'] })
      qc.invalidateQueries({ queryKey: ['pipelines'] })
    },
  })
}

export function useToggleFeatureFlag() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      api.put(`/feature-flags/${id}`, { enabled }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['feature-flags'] }),
  })
}
