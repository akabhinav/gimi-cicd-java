export interface Pipeline {
  id: string
  name: string
  description?: string
  projectId: string
  stages: Stage[]
  triggers?: Trigger[]
  status?: string
  lastRun?: PipelineRun
  createdAt: string
  updatedAt: string
  tags?: string[]
}

export interface Stage {
  id: string
  name: string
  type: StageType
  steps: Step[]
  dependsOn?: string[]
  when?: string
  approval?: ApprovalConfig
  strategy?: DeploymentStrategy
  status?: string
  duration?: number
}

export type StageType = 'BUILD' | 'TEST' | 'DEPLOY' | 'APPROVAL' | 'CUSTOM' | 'SECURITY_SCAN' | 'INFRASTRUCTURE'

export interface Step {
  id: string
  name: string
  type: string
  image?: string
  command?: string
  commands?: string[]
  environment?: Record<string, string>
  timeout?: number
  status?: string
  duration?: number
  logs?: string
}

export interface Trigger {
  id: string
  type: 'GIT' | 'CRON' | 'WEBHOOK' | 'ARTIFACT'
  name: string
  enabled: boolean
  config: Record<string, unknown>
}

export interface ApprovalConfig {
  approvers: string[]
  timeout: number
  minimumApprovals: number
}

export interface DeploymentStrategy {
  type: 'ROLLING' | 'BLUE_GREEN' | 'CANARY'
  config: Record<string, unknown>
}

export interface PipelineRun {
  id: string
  pipelineId: string
  pipelineName?: string
  status: RunStatus
  trigger: string
  triggerInfo?: Record<string, string>
  branch?: string
  commit?: string
  commitMessage?: string
  stages: StageExecution[]
  startedAt: string
  finishedAt?: string
  duration?: number
  artifacts?: Artifact[]
}

export type RunStatus = 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'ABORTED' | 'WAITING_APPROVAL'

export interface StageExecution {
  id: string
  stageId: string
  name: string
  status: RunStatus
  steps: StepExecution[]
  startedAt?: string
  finishedAt?: string
  duration?: number
}

export interface StepExecution {
  id: string
  stepId: string
  name: string
  status: RunStatus
  logs?: string
  startedAt?: string
  finishedAt?: string
  duration?: number
}

export interface Artifact {
  id: string
  name: string
  type: string
  size: number
  url: string
  createdAt: string
}

export interface Worker {
  id: string
  name: string
  status: 'ACTIVE' | 'IDLE' | 'OFFLINE' | 'BUSY'
  hostname: string
  labels?: string[]
  lastHeartbeat: string
  activeJobs: number
  capacity: number
  cpuUsage?: number
  memoryUsage?: number
}

export interface DORAMetrics {
  deploymentFrequency: MetricDataPoint[]
  leadTime: MetricDataPoint[]
  changeFailureRate: MetricDataPoint[]
  mttr: MetricDataPoint[]
  summary: {
    deploymentFrequency: number
    leadTime: number
    changeFailureRate: number
    mttr: number
    rating: 'ELITE' | 'HIGH' | 'MEDIUM' | 'LOW'
  }
}

export interface MetricDataPoint {
  date: string
  value: number
  label?: string
}

export interface FeatureFlag {
  id: string
  name: string
  key: string
  enabled: boolean
  description?: string
  type: 'BOOLEAN' | 'STRING' | 'NUMBER' | 'JSON'
  defaultValue: string
  environments: Record<string, boolean>
  createdAt: string
  updatedAt: string
}

export interface SecurityScan {
  id: string
  pipelineRunId: string
  type: 'SAST' | 'DAST' | 'SCA'
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  findings: SecurityFinding[]
  summary: {
    critical: number
    high: number
    medium: number
    low: number
    info: number
  }
  startedAt: string
  finishedAt?: string
}

export interface SecurityFinding {
  id: string
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO'
  title: string
  description: string
  file?: string
  line?: number
  cwe?: string
  recommendation?: string
}

export interface Template {
  id: string
  name: string
  description?: string
  type: string
  version: string
  content: string
  tags?: string[]
  createdAt: string
}

export interface Connector {
  id: string
  name: string
  type: string
  status: 'CONNECTED' | 'DISCONNECTED' | 'ERROR'
  config: Record<string, unknown>
  lastTestAt?: string
}

export interface AuditEvent {
  id: string
  action: string
  resource: string
  resourceId: string
  userId: string
  userName: string
  timestamp: string
  details?: Record<string, unknown>
}

export interface SLO {
  id: string
  name: string
  target: number
  current: number
  type: string
  status: 'MET' | 'AT_RISK' | 'BREACHED'
  errorBudgetRemaining: number
}

export interface Project {
  id: string
  name: string
  description?: string
  orgId: string
}
