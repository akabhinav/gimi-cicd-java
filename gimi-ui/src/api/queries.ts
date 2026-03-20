import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import { mockData } from './mock-data'
import { adaptPipelines, adaptPipeline, adaptRuns, adaptRun, adaptWorkers, adaptDORAMetrics, adaptFeatureFlags, adaptConnectors, adaptAuditEvents, adaptSLOs, adaptPolicies } from './adapters'
import type { Pipeline, PipelineRun, Worker, DORAMetrics, FeatureFlag, SecurityScan, Template, Connector, AuditEvent, SLO } from '@/types'

// Toggle mock mode: 'mock' = always mock, 'api' = always real, 'auto' = use API if token present
const MODE: 'mock' | 'api' | 'auto' = 'auto'

function useMock(): boolean {
  if (MODE === 'mock') return true
  if (MODE === 'api') return false
  return !localStorage.getItem('gimi_token')
}

function useMockOrApi<T>(key: string[], mockFn: () => T, apiFn: () => Promise<T>) {
  const isMock = useMock()
  return useQuery({
    queryKey: key,
    queryFn: isMock ? () => Promise.resolve(mockFn()) : apiFn,
    staleTime: 30000,
  })
}

// --- Pipelines ---
// Backend: GET /api/pipelines returns List<Map> with {name, version, file, stageCount, triggerCount}
// Backend: GET /api/pipelines/{name} returns full pipeline YAML parsed
export function usePipelines() {
  return useMockOrApi<Pipeline[]>(
    ['pipelines'],
    () => mockData.pipelines,
    async () => adaptPipelines(await api.get('/pipelines'))
  )
}

export function usePipeline(id: string) {
  return useMockOrApi<Pipeline>(
    ['pipeline', id],
    () => mockData.pipelines.find(p => p.id === id || p.name === id)!,
    async () => adaptPipeline(await api.get(`/pipelines/${encodeURIComponent(id)}`))
  )
}

// --- Runs ---
// Backend: POST /api/runs {pipelineName, trigger, environment}
// Backend: GET /api/runs?limit=50 returns List<ExecutionRecord>
// Backend: GET /api/runs/{runId} returns ExecutionRecord
export function usePipelineRuns(pipelineId?: string) {
  return useMockOrApi<PipelineRun[]>(
    ['runs', pipelineId ?? 'all'],
    () => pipelineId ? mockData.runs.filter(r => r.pipelineId === pipelineId) : mockData.runs,
    async () => {
      const runs = await api.get<unknown[]>('/runs?limit=50')
      const adapted = adaptRuns(runs)
      return pipelineId ? adapted.filter(r => r.pipelineId === pipelineId || r.pipelineName === pipelineId) : adapted
    }
  )
}

export function usePipelineRun(runId: string) {
  return useMockOrApi<PipelineRun>(
    ['run', runId],
    () => mockData.runs.find(r => r.id === runId)!,
    async () => adaptRun(await api.get(`/runs/${runId}`))
  )
}

// --- Workers ---
// Backend: GET /api/workers returns List<Map> with {id, hostname, port, status, maxConcurrentJobs, activeJobs, labels, lastHeartbeat}
export function useWorkers() {
  return useMockOrApi<Worker[]>(
    ['workers'],
    () => mockData.workers,
    async () => adaptWorkers(await api.get('/workers'))
  )
}

// --- DORA Metrics ---
// Backend: GET /api/analytics/dora/{pipelineName}?days=7 returns DoraMetrics record
export function useDORAMetrics(pipelineName?: string) {
  return useMockOrApi<DORAMetrics>(
    ['dora-metrics', pipelineName ?? 'all'],
    () => mockData.doraMetrics,
    async () => adaptDORAMetrics(await api.get(`/analytics/dora/${encodeURIComponent(pipelineName || 'all')}?days=7`))
  )
}

// --- Feature Flags ---
// Backend: GET /api/feature-flags returns List<FeatureFlag>
// Backend: PUT /api/feature-flags/{key} updates flag
export function useFeatureFlags() {
  return useMockOrApi<FeatureFlag[]>(
    ['feature-flags'],
    () => mockData.featureFlags,
    async () => adaptFeatureFlags(await api.get('/feature-flags'))
  )
}

// --- Security Scans ---
// Backend: POST /api/security/scan?runId={runId} triggers a scan, no list endpoint
// Use mock data as the backend only supports triggering scans per-run
export function useSecurityScans() {
  return useMockOrApi<SecurityScan[]>(
    ['security-scans'],
    () => mockData.securityScans,
    () => Promise.resolve(mockData.securityScans) // No list endpoint on backend yet
  )
}

// --- Templates ---
// No dedicated backend endpoint, use mock
export function useTemplates() {
  return useMockOrApi<Template[]>(
    ['templates'],
    () => mockData.templates,
    () => Promise.resolve(mockData.templates)
  )
}

// --- Connectors ---
// Backend: GET /api/connectors returns List<Connector>
export function useConnectors() {
  return useMockOrApi<Connector[]>(
    ['connectors'],
    () => mockData.connectors,
    async () => adaptConnectors(await api.get('/connectors'))
  )
}

// --- Audit Events ---
// Backend: GET /api/audit returns List<AuditEvent>
export function useAuditEvents() {
  return useMockOrApi<AuditEvent[]>(
    ['audit-events'],
    () => mockData.auditEvents,
    async () => adaptAuditEvents(await api.get('/audit'))
  )
}

// --- SLOs ---
// Backend: GET /api/slo returns List<ServiceLevelObjective>
// Need to combine with GET /api/slo/{id}/status for current values
export function useSLOs() {
  return useMockOrApi<SLO[]>(
    ['slos'],
    () => mockData.slos,
    async () => adaptSLOs(await api.get('/slo'))
  )
}

// --- Policies (Governance) ---
// Backend: GET /api/policies returns List<Policy>
export function usePolicies() {
  return useMockOrApi(
    ['policies'],
    () => [],
    async () => adaptPolicies(await api.get('/policies'))
  )
}

// --- Mutations ---

export function useRunPipeline() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (pipelineName: string) =>
      api.post('/runs', { pipelineName, trigger: 'MANUAL', environment: 'default' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['runs'] })
      qc.invalidateQueries({ queryKey: ['pipelines'] })
    },
  })
}

export function useCancelRun() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (runId: string) => api.post(`/runs/${runId}/cancel`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['runs'] })
    },
  })
}

export function useToggleFeatureFlag() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      api.put(`/feature-flags/${encodeURIComponent(key)}`, { enabled }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['feature-flags'] }),
  })
}

export function useTestConnector() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.post(`/connectors/${id}/test`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['connectors'] }),
  })
}
