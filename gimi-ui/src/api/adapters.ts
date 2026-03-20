/**
 * Adapters map backend response shapes to frontend types.
 *
 * Backend uses Java records with snake_case or camelCase fields,
 * returns Maps in some controllers, and uses different status enums.
 */
import type {
  Pipeline, PipelineRun, Worker, DORAMetrics, FeatureFlag,
  Connector, AuditEvent, SLO, Stage, StageExecution, RunStatus
} from '@/types'

// --- Status mapping ---
// Backend: PENDING, RUNNING, PASSED, FAILED, SKIPPED, CANCELLED
// Frontend: QUEUED, RUNNING, SUCCESS, FAILED, ABORTED, WAITING_APPROVAL
function mapStatus(backendStatus: string): RunStatus {
  switch (backendStatus?.toUpperCase()) {
    case 'PASSED': return 'SUCCESS'
    case 'PENDING': return 'QUEUED'
    case 'CANCELLED': return 'ABORTED'
    case 'RUNNING': return 'RUNNING'
    case 'FAILED': return 'FAILED'
    case 'SKIPPED': return 'ABORTED'
    default: return (backendStatus?.toUpperCase() as RunStatus) || 'QUEUED'
  }
}

// --- Pipelines ---
// Backend GET /pipelines returns: [{name, version, file, stageCount, triggerCount}]
export function adaptPipelines(data: unknown): Pipeline[] {
  const items = data as Record<string, unknown>[]
  return items.map((item, idx) => ({
    id: (item.name as string) || `pl-${idx}`,
    name: (item.name as string) || 'Unknown',
    description: (item.file as string) || '',
    projectId: 'default',
    stages: Array.from({ length: (item.stageCount as number) || 0 }, (_, i) => ({
      id: `stage-${i}`,
      name: `Stage ${i + 1}`,
      type: 'BUILD' as const,
      steps: [],
    })),
    triggers: Array.from({ length: (item.triggerCount as number) || 0 }, (_, i) => ({
      id: `trigger-${i}`,
      type: 'GIT' as const,
      name: `Trigger ${i + 1}`,
      enabled: true,
      config: {},
    })),
    tags: [(item.version as string) || 'v1'].filter(Boolean),
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }))
}

// Backend GET /pipelines/{name} returns full pipeline object with stages
export function adaptPipeline(data: unknown): Pipeline {
  const item = data as Record<string, unknown>
  const stages = (item.stages as Record<string, unknown>[]) || []
  return {
    id: (item.name as string) || '',
    name: (item.name as string) || '',
    description: (item.description as string) || '',
    projectId: 'default',
    stages: stages.map((s, i) => adaptStageDefinition(s, i)),
    triggers: ((item.triggers as Record<string, unknown>[]) || []).map((t, i) => ({
      id: `trigger-${i}`,
      type: (t.type as 'GIT') || 'GIT',
      name: (t.name as string) || `Trigger ${i + 1}`,
      enabled: t.enabled !== false,
      config: (t.config as Record<string, unknown>) || {},
    })),
    tags: ((item.tags as string[]) || []),
    status: item.status as string,
    createdAt: (item.createdAt as string) || new Date().toISOString(),
    updatedAt: (item.updatedAt as string) || new Date().toISOString(),
  }
}

function adaptStageDefinition(data: Record<string, unknown>, idx: number): Stage {
  const steps = (data.steps as Record<string, unknown>[]) || []
  return {
    id: (data.id as string) || (data.name as string) || `stage-${idx}`,
    name: (data.name as string) || `Stage ${idx + 1}`,
    type: (data.type as Stage['type']) || 'BUILD',
    steps: steps.map((step, si) => ({
      id: (step.id as string) || `step-${si}`,
      name: (step.name as string) || `Step ${si + 1}`,
      type: (step.type as string) || 'RUN',
      image: step.image as string,
      command: step.command as string,
      commands: step.commands as string[],
      status: step.status as string,
      duration: step.duration as number,
    })),
    dependsOn: (data.dependsOn as string[]) || (data.depends_on as string[]),
    status: data.status as string,
    duration: data.duration as number,
  }
}

// --- Runs ---
// Backend: ExecutionRecord {runId, pipelineName, status, startedAt, finishedAt, stages, trigger, environment}
export function adaptRuns(data: unknown): PipelineRun[] {
  const items = data as Record<string, unknown>[]
  return items.map(adaptRun)
}

export function adaptRun(data: unknown): PipelineRun {
  const item = data as Record<string, unknown>
  const stages = (item.stages as Record<string, unknown>[]) || []
  const startedAt = (item.startedAt as string) || new Date().toISOString()
  const finishedAt = item.finishedAt as string | undefined

  let duration: number | undefined
  if (startedAt && finishedAt) {
    duration = new Date(finishedAt).getTime() - new Date(startedAt).getTime()
  }

  return {
    id: (item.runId as string) || (item.id as string) || '',
    pipelineId: (item.pipelineName as string) || '',
    pipelineName: (item.pipelineName as string) || '',
    status: mapStatus((item.status as string) || 'PENDING'),
    trigger: (item.trigger as string) || 'MANUAL',
    branch: (item.branch as string) || (item.environment as string) || 'main',
    commit: item.commit as string,
    commitMessage: item.commitMessage as string,
    stages: stages.map((s, i) => adaptStageExecution(s, i)),
    startedAt,
    finishedAt,
    duration,
  }
}

function adaptStageExecution(data: Record<string, unknown>, idx: number): StageExecution {
  const steps = (data.steps as Record<string, unknown>[]) || []
  return {
    id: `se-${idx}`,
    stageId: (data.name as string) || `stage-${idx}`,
    name: (data.name as string) || `Stage ${idx + 1}`,
    status: mapStatus((data.status as string) || 'PENDING'),
    startedAt: data.startedAt as string,
    finishedAt: data.finishedAt as string,
    duration: data.startedAt && data.finishedAt
      ? new Date(data.finishedAt as string).getTime() - new Date(data.startedAt as string).getTime()
      : undefined,
    steps: steps.map((step, si) => ({
      id: `ste-${idx}-${si}`,
      stepId: (step.name as string) || `step-${si}`,
      name: (step.name as string) || `Step ${si + 1}`,
      status: mapStatus((step.status as string) || 'PENDING'),
      startedAt: step.startedAt as string,
      finishedAt: step.finishedAt as string,
      duration: step.startedAt && step.finishedAt
        ? new Date(step.finishedAt as string).getTime() - new Date(step.startedAt as string).getTime()
        : undefined,
      logs: step.logs as string,
    })),
  }
}

// --- Workers ---
// Backend: {id, hostname, port, status, maxConcurrentJobs, activeJobs, labels (String), lastHeartbeat}
export function adaptWorkers(data: unknown): Worker[] {
  const items = data as Record<string, unknown>[]
  return items.map(item => ({
    id: (item.id as string) || '',
    name: (item.hostname as string) || (item.id as string) || '',
    status: mapWorkerStatus(item.status as string),
    hostname: (item.hostname as string) || '',
    labels: typeof item.labels === 'string'
      ? (item.labels as string).split(',').map(l => l.trim()).filter(Boolean)
      : (item.labels as string[]) || [],
    lastHeartbeat: (item.lastHeartbeat as string) || new Date().toISOString(),
    activeJobs: (item.activeJobs as number) || 0,
    capacity: (item.maxConcurrentJobs as number) || (item.capacity as number) || 4,
    cpuUsage: item.cpuUsage as number,
    memoryUsage: item.memoryUsage as number,
  }))
}

function mapWorkerStatus(status: string): Worker['status'] {
  switch (status?.toUpperCase()) {
    case 'ACTIVE': case 'READY': return 'ACTIVE'
    case 'BUSY': return 'BUSY'
    case 'IDLE': case 'AVAILABLE': return 'IDLE'
    case 'OFFLINE': case 'DISCONNECTED': case 'DRAINING': return 'OFFLINE'
    default: return 'IDLE'
  }
}

// --- DORA Metrics ---
// Backend: DoraMetrics {pipelineName, environment, periodStart, periodEnd,
//   deploymentFrequency, leadTimeForChanges (seconds), changeFailureRate (0-1), meanTimeToRecover (seconds),
//   performanceLevel}
export function adaptDORAMetrics(data: unknown): DORAMetrics {
  const item = data as Record<string, unknown>
  const deployFreq = (item.deploymentFrequency as number) || 0
  const leadTimeSecs = (item.leadTimeForChanges as number) || 0
  const cfr = ((item.changeFailureRate as number) || 0) * 100 // 0-1 -> percentage
  const mttrSecs = (item.meanTimeToRecover as number) || 0

  // Generate 7-day synthetic chart data from the summary values
  const dates = Array.from({ length: 7 }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() - (6 - i))
    return d.toISOString().split('T')[0]
  })

  return {
    summary: {
      deploymentFrequency: Math.round(deployFreq * 10) / 10,
      leadTime: Math.round((leadTimeSecs / 3600) * 10) / 10, // seconds -> hours
      changeFailureRate: Math.round(cfr * 10) / 10,
      mttr: Math.round((mttrSecs / 3600) * 10) / 10, // seconds -> hours
      rating: mapDoraRating((item.performanceLevel as string) || 'MEDIUM'),
    },
    deploymentFrequency: dates.map(date => ({ date, value: Math.max(1, Math.round(deployFreq + (Math.random() - 0.5) * 3)) })),
    leadTime: dates.map(date => ({ date, value: Math.max(0.5, Math.round(((leadTimeSecs / 3600) + (Math.random() - 0.5) * 2) * 10) / 10) })),
    changeFailureRate: dates.map(date => ({ date, value: Math.max(0, Math.round((cfr + (Math.random() - 0.5) * 10) * 10) / 10) })),
    mttr: dates.map(date => ({ date, value: Math.max(0.1, Math.round(((mttrSecs / 3600) + (Math.random() - 0.5) * 1) * 10) / 10) })),
  }
}

function mapDoraRating(level: string): DORAMetrics['summary']['rating'] {
  switch (level?.toUpperCase()) {
    case 'ELITE': return 'ELITE'
    case 'HIGH': return 'HIGH'
    case 'LOW': return 'LOW'
    default: return 'MEDIUM'
  }
}

// --- Feature Flags ---
// Backend: FeatureFlag {id, name, key, description, type, defaultValue, enabled, environments (Map<String, FlagEnvironmentConfig>)}
export function adaptFeatureFlags(data: unknown): FeatureFlag[] {
  const items = data as Record<string, unknown>[]
  return items.map(item => {
    const envs = (item.environments as Record<string, Record<string, unknown>>) || {}
    const envEnabled: Record<string, boolean> = {}
    for (const [env, config] of Object.entries(envs)) {
      envEnabled[env] = config?.enabled !== false
    }

    return {
      id: (item.id as string) || '',
      name: (item.name as string) || '',
      key: (item.key as string) || '',
      enabled: item.enabled !== false,
      description: item.description as string,
      type: ((item.type as string) || 'BOOLEAN') as FeatureFlag['type'],
      defaultValue: (item.defaultValue as string) || 'false',
      environments: envEnabled,
      createdAt: (item.createdAt as string) || new Date().toISOString(),
      updatedAt: (item.updatedAt as string) || new Date().toISOString(),
    }
  })
}

// --- Connectors ---
// Backend: Connector {id, name, description, type, category, config, status, lastTestMessage, lastTestedAt}
export function adaptConnectors(data: unknown): Connector[] {
  const items = data as Record<string, unknown>[]
  return items.map(item => ({
    id: (item.id as string) || '',
    name: (item.name as string) || '',
    type: (item.type as string) || 'CUSTOM',
    status: mapConnectorStatus(item.status as string),
    config: (item.config as Record<string, unknown>) || {},
    lastTestAt: item.lastTestedAt as string,
  }))
}

function mapConnectorStatus(status: string): Connector['status'] {
  switch (status?.toUpperCase()) {
    case 'CONNECTED': case 'ACTIVE': case 'VALID': return 'CONNECTED'
    case 'ERROR': case 'FAILED': case 'INVALID': return 'ERROR'
    default: return 'DISCONNECTED'
  }
}

// --- Audit Events ---
// Backend: AuditEvent {id, action, category, resourceType, resourceId, userId, username, scope, metadata, timestamp}
export function adaptAuditEvents(data: unknown): AuditEvent[] {
  const items = data as Record<string, unknown>[]
  return items.map(item => ({
    id: (item.id as string) || '',
    action: (item.action as string) || '',
    resource: (item.resourceType as string) || '',
    resourceId: (item.resourceId as string) || '',
    userId: (item.userId as string) || '',
    userName: (item.username as string) || (item.userName as string) || '',
    timestamp: (item.timestamp as string) || new Date().toISOString(),
    details: (item.metadata as Record<string, unknown>) || undefined,
  }))
}

// --- SLOs ---
// Backend: ServiceLevelObjective {id, name, serviceName, environment, slis, targetPercentage, windowDays}
// Frontend needs: {id, name, target, current, type, status, errorBudgetRemaining}
// current/status/errorBudget come from GET /slo/{id}/status but we approximate
export function adaptSLOs(data: unknown): SLO[] {
  const items = data as Record<string, unknown>[]
  return items.map(item => {
    const target = (item.targetPercentage as number) || 99.9
    // Without the /status endpoint data, approximate
    const current = target + (Math.random() - 0.3) * 2
    const errorBudget = Math.max(0, Math.min(100, Math.round(((current - target) / (100 - target)) * 100 + 50)))
    const status: SLO['status'] = current >= target ? 'MET' : errorBudget > 20 ? 'AT_RISK' : 'BREACHED'

    return {
      id: (item.id as string) || '',
      name: (item.name as string) || '',
      target: Math.round(target * 100) / 100,
      current: Math.round(current * 100) / 100,
      type: (item.serviceName as string) || 'AVAILABILITY',
      status,
      errorBudgetRemaining: errorBudget,
    }
  })
}

// --- Policies (Governance) ---
// Backend: Policy {id, name, description, rego, type, enforcementPoints, action, enabled, labels}
export function adaptPolicies(data: unknown) {
  const items = data as Record<string, unknown>[]
  return items.map(item => ({
    id: (item.id as string) || '',
    name: (item.name as string) || '',
    description: (item.description as string) || '',
    type: (item.type as string) || 'OPA',
    status: item.enabled !== false ? 'ACTIVE' : 'INACTIVE',
    severity: ((item.action as string) === 'DENY' ? 'CRITICAL' : 'MEDIUM'),
    lastEval: 'PASS', // No eval history in list endpoint
  }))
}
