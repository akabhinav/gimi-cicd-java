import type { Pipeline, PipelineRun, Worker, DORAMetrics, FeatureFlag, SecurityScan, Template, Connector, AuditEvent, SLO } from '@/types'

const pipelines: Pipeline[] = [
  {
    id: 'pl-1',
    name: 'Backend CI Pipeline',
    description: 'Builds, tests, and deploys the Spring Boot backend service',
    projectId: 'proj-1',
    tags: ['java', 'spring-boot', 'production'],
    stages: [
      {
        id: 'st-1', name: 'Build', type: 'BUILD', status: 'SUCCESS', duration: 45000,
        steps: [
          { id: 'stp-1', name: 'Compile', type: 'RUN', command: 'mvn compile', status: 'SUCCESS', duration: 20000 },
          { id: 'stp-2', name: 'Unit Tests', type: 'RUN', command: 'mvn test', status: 'SUCCESS', duration: 25000 },
        ],
      },
      {
        id: 'st-2', name: 'Security Scan', type: 'SECURITY_SCAN', status: 'SUCCESS', duration: 30000,
        dependsOn: ['st-1'],
        steps: [
          { id: 'stp-3', name: 'SAST Scan', type: 'SECURITY', command: 'sonar-scanner', status: 'SUCCESS', duration: 30000 },
        ],
      },
      {
        id: 'st-3', name: 'Build Image', type: 'BUILD', status: 'SUCCESS', duration: 60000,
        dependsOn: ['st-1'],
        steps: [
          { id: 'stp-4', name: 'Docker Build', type: 'RUN', image: 'docker:24', command: 'docker build -t app .', status: 'SUCCESS', duration: 40000 },
          { id: 'stp-5', name: 'Push to Registry', type: 'RUN', command: 'docker push registry/app:latest', status: 'SUCCESS', duration: 20000 },
        ],
      },
      {
        id: 'st-4', name: 'Deploy Staging', type: 'DEPLOY', status: 'SUCCESS', duration: 90000,
        dependsOn: ['st-2', 'st-3'],
        strategy: { type: 'CANARY', config: { increments: [10, 25, 50, 100] } },
        steps: [
          { id: 'stp-6', name: 'Deploy Canary', type: 'DEPLOY', command: 'kubectl apply', status: 'SUCCESS', duration: 45000 },
          { id: 'stp-7', name: 'Verify Metrics', type: 'VERIFY', command: 'verify-slo', status: 'SUCCESS', duration: 45000 },
        ],
      },
      {
        id: 'st-5', name: 'Approval', type: 'APPROVAL', status: 'SUCCESS', duration: 120000,
        dependsOn: ['st-4'],
        approval: { approvers: ['admin@gimi.dev'], timeout: 3600, minimumApprovals: 1 },
        steps: [],
      },
      {
        id: 'st-6', name: 'Deploy Production', type: 'DEPLOY', status: 'RUNNING', duration: 0,
        dependsOn: ['st-5'],
        strategy: { type: 'BLUE_GREEN', config: {} },
        steps: [
          { id: 'stp-8', name: 'Blue-Green Deploy', type: 'DEPLOY', command: 'kubectl apply -f prod/', status: 'RUNNING', duration: 0 },
        ],
      },
    ],
    triggers: [
      { id: 'tr-1', type: 'GIT', name: 'Push to main', enabled: true, config: { branch: 'main', event: 'PUSH' } },
      { id: 'tr-2', type: 'CRON', name: 'Nightly Build', enabled: true, config: { expression: '0 2 * * *' } },
    ],
    status: 'RUNNING',
    createdAt: '2026-01-15T10:30:00Z',
    updatedAt: '2026-03-20T08:15:00Z',
  },
  {
    id: 'pl-2',
    name: 'Frontend Deploy',
    description: 'CI/CD for React frontend application',
    projectId: 'proj-1',
    tags: ['react', 'typescript', 'cdn'],
    stages: [
      { id: 'st-10', name: 'Install', type: 'BUILD', status: 'SUCCESS', duration: 15000, steps: [{ id: 's1', name: 'npm install', type: 'RUN', command: 'npm ci', status: 'SUCCESS', duration: 15000 }] },
      { id: 'st-11', name: 'Lint & Test', type: 'TEST', status: 'SUCCESS', duration: 30000, dependsOn: ['st-10'], steps: [{ id: 's2', name: 'Lint', type: 'RUN', command: 'npm run lint', status: 'SUCCESS', duration: 10000 }, { id: 's3', name: 'Test', type: 'RUN', command: 'npm test', status: 'SUCCESS', duration: 20000 }] },
      { id: 'st-12', name: 'Build', type: 'BUILD', status: 'SUCCESS', duration: 25000, dependsOn: ['st-11'], steps: [{ id: 's4', name: 'Build', type: 'RUN', command: 'npm run build', status: 'SUCCESS', duration: 25000 }] },
      { id: 'st-13', name: 'Deploy CDN', type: 'DEPLOY', status: 'SUCCESS', duration: 10000, dependsOn: ['st-12'], steps: [{ id: 's5', name: 'Upload', type: 'RUN', command: 'aws s3 sync', status: 'SUCCESS', duration: 10000 }] },
    ],
    triggers: [{ id: 'tr-3', type: 'GIT', name: 'PR to main', enabled: true, config: { branch: 'main', event: 'PULL_REQUEST' } }],
    status: 'SUCCESS',
    createdAt: '2026-02-01T14:00:00Z',
    updatedAt: '2026-03-19T16:30:00Z',
  },
  {
    id: 'pl-3',
    name: 'Database Migration',
    description: 'Flyway database migration pipeline with rollback support',
    projectId: 'proj-1',
    tags: ['database', 'flyway', 'postgres'],
    stages: [
      { id: 'st-20', name: 'Validate', type: 'BUILD', status: 'SUCCESS', duration: 5000, steps: [{ id: 's10', name: 'Validate SQL', type: 'RUN', command: 'flyway validate', status: 'SUCCESS', duration: 5000 }] },
      { id: 'st-21', name: 'Migrate Staging', type: 'DEPLOY', status: 'SUCCESS', duration: 12000, dependsOn: ['st-20'], steps: [{ id: 's11', name: 'Run Migration', type: 'RUN', command: 'flyway migrate', status: 'SUCCESS', duration: 12000 }] },
      { id: 'st-22', name: 'Approval', type: 'APPROVAL', status: 'FAILED', duration: 0, dependsOn: ['st-21'], approval: { approvers: ['dba@gimi.dev'], timeout: 7200, minimumApprovals: 1 }, steps: [] },
    ],
    triggers: [{ id: 'tr-4', type: 'WEBHOOK', name: 'Manual Trigger', enabled: true, config: {} }],
    status: 'FAILED',
    createdAt: '2026-02-10T09:00:00Z',
    updatedAt: '2026-03-18T11:00:00Z',
  },
  {
    id: 'pl-4',
    name: 'Microservice - Payment',
    description: 'Payment microservice CI/CD with canary deployment',
    projectId: 'proj-2',
    tags: ['java', 'payment', 'critical'],
    stages: [
      { id: 'st-30', name: 'Build & Test', type: 'BUILD', status: 'SUCCESS', duration: 55000, steps: [{ id: 's20', name: 'Maven Build', type: 'RUN', command: 'mvn clean verify', status: 'SUCCESS', duration: 55000 }] },
      { id: 'st-31', name: 'Security', type: 'SECURITY_SCAN', status: 'SUCCESS', duration: 40000, dependsOn: ['st-30'], steps: [{ id: 's21', name: 'Trivy Scan', type: 'SECURITY', command: 'trivy image', status: 'SUCCESS', duration: 40000 }] },
      { id: 'st-32', name: 'Deploy', type: 'DEPLOY', status: 'QUEUED', duration: 0, dependsOn: ['st-31'], strategy: { type: 'CANARY', config: { increments: [5, 15, 50, 100] } }, steps: [] },
    ],
    triggers: [{ id: 'tr-5', type: 'GIT', name: 'Tag v*', enabled: true, config: { event: 'TAG', pattern: 'v*' } }],
    status: 'QUEUED',
    createdAt: '2026-01-20T08:00:00Z',
    updatedAt: '2026-03-20T09:00:00Z',
  },
  {
    id: 'pl-5',
    name: 'Infrastructure - Terraform',
    description: 'Terraform infrastructure provisioning pipeline',
    projectId: 'proj-2',
    tags: ['terraform', 'aws', 'infrastructure'],
    stages: [
      { id: 'st-40', name: 'Plan', type: 'INFRASTRUCTURE', status: 'SUCCESS', duration: 20000, steps: [{ id: 's30', name: 'Terraform Plan', type: 'RUN', command: 'terraform plan', status: 'SUCCESS', duration: 20000 }] },
      { id: 'st-41', name: 'Approval', type: 'APPROVAL', status: 'SUCCESS', duration: 300000, dependsOn: ['st-40'], approval: { approvers: ['infra@gimi.dev'], timeout: 14400, minimumApprovals: 2 }, steps: [] },
      { id: 'st-42', name: 'Apply', type: 'INFRASTRUCTURE', status: 'SUCCESS', duration: 180000, dependsOn: ['st-41'], steps: [{ id: 's31', name: 'Terraform Apply', type: 'RUN', command: 'terraform apply -auto-approve', status: 'SUCCESS', duration: 180000 }] },
    ],
    triggers: [{ id: 'tr-6', type: 'GIT', name: 'Push to infra/', enabled: true, config: { branch: 'main', event: 'PUSH', paths: ['infra/**'] } }],
    status: 'SUCCESS',
    createdAt: '2026-03-01T10:00:00Z',
    updatedAt: '2026-03-19T14:00:00Z',
  },
]

const runs: PipelineRun[] = [
  {
    id: 'run-1', pipelineId: 'pl-1', pipelineName: 'Backend CI Pipeline', status: 'RUNNING',
    trigger: 'GIT', branch: 'main', commit: 'a1b2c3d', commitMessage: 'feat: add payment processing endpoint',
    startedAt: '2026-03-20T08:15:00Z', duration: 245000,
    stages: [
      { id: 'se-1', stageId: 'st-1', name: 'Build', status: 'SUCCESS', startedAt: '2026-03-20T08:15:00Z', finishedAt: '2026-03-20T08:15:45Z', duration: 45000, steps: [{ id: 'ste-1', stepId: 'stp-1', name: 'Compile', status: 'SUCCESS', duration: 20000, startedAt: '2026-03-20T08:15:00Z', finishedAt: '2026-03-20T08:15:20Z' }, { id: 'ste-2', stepId: 'stp-2', name: 'Unit Tests', status: 'SUCCESS', duration: 25000, startedAt: '2026-03-20T08:15:20Z', finishedAt: '2026-03-20T08:15:45Z' }] },
      { id: 'se-2', stageId: 'st-2', name: 'Security Scan', status: 'SUCCESS', startedAt: '2026-03-20T08:15:45Z', finishedAt: '2026-03-20T08:16:15Z', duration: 30000, steps: [{ id: 'ste-3', stepId: 'stp-3', name: 'SAST Scan', status: 'SUCCESS', duration: 30000, startedAt: '2026-03-20T08:15:45Z', finishedAt: '2026-03-20T08:16:15Z' }] },
      { id: 'se-3', stageId: 'st-3', name: 'Build Image', status: 'SUCCESS', startedAt: '2026-03-20T08:15:45Z', finishedAt: '2026-03-20T08:16:45Z', duration: 60000, steps: [{ id: 'ste-4', stepId: 'stp-4', name: 'Docker Build', status: 'SUCCESS', duration: 40000, startedAt: '2026-03-20T08:15:45Z', finishedAt: '2026-03-20T08:16:25Z' }, { id: 'ste-5', stepId: 'stp-5', name: 'Push to Registry', status: 'SUCCESS', duration: 20000, startedAt: '2026-03-20T08:16:25Z', finishedAt: '2026-03-20T08:16:45Z' }] },
      { id: 'se-4', stageId: 'st-4', name: 'Deploy Staging', status: 'SUCCESS', startedAt: '2026-03-20T08:16:45Z', finishedAt: '2026-03-20T08:18:15Z', duration: 90000, steps: [{ id: 'ste-6', stepId: 'stp-6', name: 'Deploy Canary', status: 'SUCCESS', duration: 45000, startedAt: '2026-03-20T08:16:45Z', finishedAt: '2026-03-20T08:17:30Z' }, { id: 'ste-7', stepId: 'stp-7', name: 'Verify Metrics', status: 'SUCCESS', duration: 45000, startedAt: '2026-03-20T08:17:30Z', finishedAt: '2026-03-20T08:18:15Z' }] },
      { id: 'se-5', stageId: 'st-5', name: 'Approval', status: 'SUCCESS', startedAt: '2026-03-20T08:18:15Z', finishedAt: '2026-03-20T08:20:15Z', duration: 120000, steps: [] },
      { id: 'se-6', stageId: 'st-6', name: 'Deploy Production', status: 'RUNNING', startedAt: '2026-03-20T08:20:15Z', duration: 0, steps: [{ id: 'ste-8', stepId: 'stp-8', name: 'Blue-Green Deploy', status: 'RUNNING', duration: 0, startedAt: '2026-03-20T08:20:15Z', logs: '> Deploying to production cluster...\n> Switching traffic to green environment\n> Current: 40% green, 60% blue\n> Health checks passing...\n> Scaling green replicas: 3/5 ready' }] },
    ],
  },
  {
    id: 'run-2', pipelineId: 'pl-2', pipelineName: 'Frontend Deploy', status: 'SUCCESS',
    trigger: 'GIT', branch: 'main', commit: 'e5f6g7h', commitMessage: 'fix: resolve dashboard rendering issue',
    startedAt: '2026-03-19T16:00:00Z', finishedAt: '2026-03-19T16:01:20Z', duration: 80000,
    stages: [
      { id: 'se-10', stageId: 'st-10', name: 'Install', status: 'SUCCESS', duration: 15000, startedAt: '2026-03-19T16:00:00Z', finishedAt: '2026-03-19T16:00:15Z', steps: [] },
      { id: 'se-11', stageId: 'st-11', name: 'Lint & Test', status: 'SUCCESS', duration: 30000, startedAt: '2026-03-19T16:00:15Z', finishedAt: '2026-03-19T16:00:45Z', steps: [] },
      { id: 'se-12', stageId: 'st-12', name: 'Build', status: 'SUCCESS', duration: 25000, startedAt: '2026-03-19T16:00:45Z', finishedAt: '2026-03-19T16:01:10Z', steps: [] },
      { id: 'se-13', stageId: 'st-13', name: 'Deploy CDN', status: 'SUCCESS', duration: 10000, startedAt: '2026-03-19T16:01:10Z', finishedAt: '2026-03-19T16:01:20Z', steps: [] },
    ],
  },
  {
    id: 'run-3', pipelineId: 'pl-3', pipelineName: 'Database Migration', status: 'FAILED',
    trigger: 'WEBHOOK', branch: 'main', commit: 'i8j9k0l', commitMessage: 'chore: add users table index',
    startedAt: '2026-03-18T10:30:00Z', finishedAt: '2026-03-18T10:31:00Z', duration: 60000,
    stages: [
      { id: 'se-20', stageId: 'st-20', name: 'Validate', status: 'SUCCESS', duration: 5000, startedAt: '2026-03-18T10:30:00Z', finishedAt: '2026-03-18T10:30:05Z', steps: [] },
      { id: 'se-21', stageId: 'st-21', name: 'Migrate Staging', status: 'SUCCESS', duration: 12000, startedAt: '2026-03-18T10:30:05Z', finishedAt: '2026-03-18T10:30:17Z', steps: [] },
      { id: 'se-22', stageId: 'st-22', name: 'Approval', status: 'FAILED', duration: 43000, startedAt: '2026-03-18T10:30:17Z', finishedAt: '2026-03-18T10:31:00Z', steps: [] },
    ],
  },
  {
    id: 'run-4', pipelineId: 'pl-1', pipelineName: 'Backend CI Pipeline', status: 'SUCCESS',
    trigger: 'CRON', branch: 'main', commit: 'm1n2o3p', commitMessage: 'Nightly build',
    startedAt: '2026-03-20T02:00:00Z', finishedAt: '2026-03-20T02:06:00Z', duration: 360000,
    stages: [
      { id: 'se-30', stageId: 'st-1', name: 'Build', status: 'SUCCESS', duration: 45000, startedAt: '2026-03-20T02:00:00Z', finishedAt: '2026-03-20T02:00:45Z', steps: [] },
      { id: 'se-31', stageId: 'st-2', name: 'Security Scan', status: 'SUCCESS', duration: 30000, startedAt: '2026-03-20T02:00:45Z', finishedAt: '2026-03-20T02:01:15Z', steps: [] },
      { id: 'se-32', stageId: 'st-3', name: 'Build Image', status: 'SUCCESS', duration: 60000, startedAt: '2026-03-20T02:00:45Z', finishedAt: '2026-03-20T02:01:45Z', steps: [] },
      { id: 'se-33', stageId: 'st-4', name: 'Deploy Staging', status: 'SUCCESS', duration: 90000, startedAt: '2026-03-20T02:01:45Z', finishedAt: '2026-03-20T02:03:15Z', steps: [] },
      { id: 'se-34', stageId: 'st-5', name: 'Approval', status: 'SUCCESS', duration: 120000, startedAt: '2026-03-20T02:03:15Z', finishedAt: '2026-03-20T02:05:15Z', steps: [] },
      { id: 'se-35', stageId: 'st-6', name: 'Deploy Production', status: 'SUCCESS', duration: 45000, startedAt: '2026-03-20T02:05:15Z', finishedAt: '2026-03-20T02:06:00Z', steps: [] },
    ],
  },
  {
    id: 'run-5', pipelineId: 'pl-4', pipelineName: 'Microservice - Payment', status: 'QUEUED',
    trigger: 'GIT', branch: 'release/v2.1', commit: 'q4r5s6t', commitMessage: 'release: v2.1.0',
    startedAt: '2026-03-20T09:00:00Z', duration: 0,
    stages: [
      { id: 'se-40', stageId: 'st-30', name: 'Build & Test', status: 'QUEUED', duration: 0, steps: [] },
      { id: 'se-41', stageId: 'st-31', name: 'Security', status: 'QUEUED', duration: 0, steps: [] },
      { id: 'se-42', stageId: 'st-32', name: 'Deploy', status: 'QUEUED', duration: 0, steps: [] },
    ],
  },
]

const workers: Worker[] = [
  { id: 'w-1', name: 'worker-prod-01', status: 'BUSY', hostname: 'ip-10-0-1-101.ec2.internal', labels: ['production', 'docker', 'linux'], lastHeartbeat: '2026-03-20T08:20:00Z', activeJobs: 2, capacity: 4, cpuUsage: 72, memoryUsage: 65 },
  { id: 'w-2', name: 'worker-prod-02', status: 'ACTIVE', hostname: 'ip-10-0-1-102.ec2.internal', labels: ['production', 'docker', 'linux'], lastHeartbeat: '2026-03-20T08:20:05Z', activeJobs: 1, capacity: 4, cpuUsage: 35, memoryUsage: 42 },
  { id: 'w-3', name: 'worker-staging-01', status: 'IDLE', hostname: 'ip-10-0-2-201.ec2.internal', labels: ['staging', 'docker', 'linux'], lastHeartbeat: '2026-03-20T08:19:55Z', activeJobs: 0, capacity: 2, cpuUsage: 5, memoryUsage: 28 },
  { id: 'w-4', name: 'worker-build-01', status: 'BUSY', hostname: 'ip-10-0-3-301.ec2.internal', labels: ['build', 'docker', 'linux', 'gpu'], lastHeartbeat: '2026-03-20T08:20:02Z', activeJobs: 3, capacity: 6, cpuUsage: 88, memoryUsage: 79 },
  { id: 'w-5', name: 'worker-build-02', status: 'OFFLINE', hostname: 'ip-10-0-3-302.ec2.internal', labels: ['build', 'docker', 'linux'], lastHeartbeat: '2026-03-20T06:15:00Z', activeJobs: 0, capacity: 4, cpuUsage: 0, memoryUsage: 0 },
]

const doraMetrics: DORAMetrics = {
  summary: { deploymentFrequency: 4.2, leadTime: 2.3, changeFailureRate: 8.5, mttr: 0.75, rating: 'HIGH' },
  deploymentFrequency: [
    { date: '2026-03-14', value: 3 }, { date: '2026-03-15', value: 5 }, { date: '2026-03-16', value: 2 },
    { date: '2026-03-17', value: 6 }, { date: '2026-03-18', value: 4 }, { date: '2026-03-19', value: 7 },
    { date: '2026-03-20', value: 3 },
  ],
  leadTime: [
    { date: '2026-03-14', value: 3.1 }, { date: '2026-03-15', value: 2.5 }, { date: '2026-03-16', value: 2.8 },
    { date: '2026-03-17', value: 1.9 }, { date: '2026-03-18', value: 2.2 }, { date: '2026-03-19', value: 1.8 },
    { date: '2026-03-20', value: 2.3 },
  ],
  changeFailureRate: [
    { date: '2026-03-14', value: 12 }, { date: '2026-03-15', value: 8 }, { date: '2026-03-16', value: 15 },
    { date: '2026-03-17', value: 5 }, { date: '2026-03-18', value: 10 }, { date: '2026-03-19', value: 6 },
    { date: '2026-03-20', value: 8 },
  ],
  mttr: [
    { date: '2026-03-14', value: 1.2 }, { date: '2026-03-15', value: 0.8 }, { date: '2026-03-16', value: 1.5 },
    { date: '2026-03-17', value: 0.5 }, { date: '2026-03-18', value: 0.9 }, { date: '2026-03-19', value: 0.4 },
    { date: '2026-03-20', value: 0.7 },
  ],
}

const featureFlags: FeatureFlag[] = [
  { id: 'ff-1', name: 'New Payment Flow', key: 'new_payment_flow', enabled: true, description: 'Enable the new streamlined payment processing flow', type: 'BOOLEAN', defaultValue: 'false', environments: { development: true, staging: true, production: false }, createdAt: '2026-03-01T10:00:00Z', updatedAt: '2026-03-18T14:30:00Z' },
  { id: 'ff-2', name: 'Dark Mode', key: 'dark_mode', enabled: true, description: 'Enable dark mode theme across the application', type: 'BOOLEAN', defaultValue: 'true', environments: { development: true, staging: true, production: true }, createdAt: '2026-02-15T09:00:00Z', updatedAt: '2026-03-10T11:00:00Z' },
  { id: 'ff-3', name: 'API Rate Limit', key: 'api_rate_limit', enabled: true, description: 'Configurable API rate limiting threshold', type: 'NUMBER', defaultValue: '1000', environments: { development: true, staging: true, production: true }, createdAt: '2026-03-05T08:00:00Z', updatedAt: '2026-03-19T16:00:00Z' },
  { id: 'ff-4', name: 'Beta Dashboard', key: 'beta_dashboard', enabled: false, description: 'Access to the new analytics dashboard', type: 'BOOLEAN', defaultValue: 'false', environments: { development: true, staging: false, production: false }, createdAt: '2026-03-10T12:00:00Z', updatedAt: '2026-03-15T10:00:00Z' },
]

const securityScans: SecurityScan[] = [
  {
    id: 'ss-1', pipelineRunId: 'run-1', type: 'SAST', status: 'COMPLETED',
    summary: { critical: 0, high: 2, medium: 5, low: 12, info: 8 },
    findings: [
      { id: 'f-1', severity: 'HIGH', title: 'SQL Injection Vulnerability', description: 'Unsanitized input in query builder', file: 'src/main/java/QueryBuilder.java', line: 45, cwe: 'CWE-89', recommendation: 'Use parameterized queries' },
      { id: 'f-2', severity: 'HIGH', title: 'Hardcoded Credential', description: 'API key found in source code', file: 'src/main/java/config/ApiConfig.java', line: 12, cwe: 'CWE-798', recommendation: 'Move to environment variable or secret manager' },
      { id: 'f-3', severity: 'MEDIUM', title: 'Weak Cryptographic Algorithm', description: 'MD5 used for hashing', file: 'src/main/java/util/HashUtil.java', line: 23, cwe: 'CWE-327', recommendation: 'Use SHA-256 or bcrypt' },
      { id: 'f-4', severity: 'MEDIUM', title: 'Missing Input Validation', description: 'User input not validated before processing', file: 'src/main/java/controller/UserController.java', line: 67, cwe: 'CWE-20', recommendation: 'Add input validation annotations' },
    ],
    startedAt: '2026-03-20T08:15:45Z', finishedAt: '2026-03-20T08:16:15Z',
  },
  {
    id: 'ss-2', pipelineRunId: 'run-1', type: 'SCA', status: 'COMPLETED',
    summary: { critical: 1, high: 3, medium: 7, low: 15, info: 4 },
    findings: [
      { id: 'f-10', severity: 'CRITICAL', title: 'CVE-2026-1234 in log4j', description: 'Remote code execution vulnerability', cwe: 'CWE-502', recommendation: 'Upgrade to log4j 2.21.0+' },
      { id: 'f-11', severity: 'HIGH', title: 'CVE-2026-5678 in jackson-databind', description: 'Deserialization vulnerability', cwe: 'CWE-502', recommendation: 'Upgrade to jackson 2.17.0+' },
    ],
    startedAt: '2026-03-20T08:15:45Z', finishedAt: '2026-03-20T08:16:10Z',
  },
]

const templates: Template[] = [
  { id: 'tpl-1', name: 'Basic CI', description: 'Standard CI pipeline with build and test', type: 'PIPELINE', version: '1.0.0', content: 'stages:\n  - name: Build\n    steps: [compile, test]', tags: ['ci', 'basic'], createdAt: '2026-01-01T00:00:00Z' },
  { id: 'tpl-2', name: 'Docker Build & Push', description: 'Build Docker image and push to registry', type: 'STAGE', version: '2.1.0', content: 'steps:\n  - docker build\n  - docker push', tags: ['docker', 'container'], createdAt: '2026-01-15T00:00:00Z' },
  { id: 'tpl-3', name: 'Blue-Green Deploy', description: 'Blue-green deployment strategy template', type: 'PIPELINE', version: '1.2.0', content: 'strategy: blue-green', tags: ['deploy', 'blue-green'], createdAt: '2026-02-01T00:00:00Z' },
  { id: 'tpl-4', name: 'Java Spring Boot', description: 'Full CI/CD for Spring Boot applications', type: 'PIPELINE', version: '3.0.0', content: 'stages: [build, test, scan, deploy]', tags: ['java', 'spring-boot'], createdAt: '2026-02-15T00:00:00Z' },
  { id: 'tpl-5', name: 'DB Migration', description: 'Database migration with Flyway', type: 'PIPELINE', version: '1.0.0', content: 'stages: [validate, migrate, verify]', tags: ['database', 'flyway'], createdAt: '2026-03-01T00:00:00Z' },
]

const connectors: Connector[] = [
  { id: 'cn-1', name: 'GitHub - gimi-org', type: 'GIT', status: 'CONNECTED', config: { url: 'https://github.com/gimi-org' }, lastTestAt: '2026-03-20T08:00:00Z' },
  { id: 'cn-2', name: 'Docker Hub', type: 'DOCKER_REGISTRY', status: 'CONNECTED', config: { registry: 'docker.io/gimi' }, lastTestAt: '2026-03-20T07:00:00Z' },
  { id: 'cn-3', name: 'AWS - Production', type: 'AWS', status: 'CONNECTED', config: { region: 'us-east-1' }, lastTestAt: '2026-03-20T06:00:00Z' },
  { id: 'cn-4', name: 'Kubernetes - Staging', type: 'KUBERNETES', status: 'CONNECTED', config: { cluster: 'staging-cluster' }, lastTestAt: '2026-03-19T20:00:00Z' },
  { id: 'cn-5', name: 'Slack Workspace', type: 'SLACK', status: 'ERROR', config: { workspace: 'gimi-dev' }, lastTestAt: '2026-03-18T12:00:00Z' },
]

const auditEvents: AuditEvent[] = [
  { id: 'ae-1', action: 'PIPELINE_EXECUTED', resource: 'Pipeline', resourceId: 'pl-1', userId: 'u-1', userName: 'admin@gimi.dev', timestamp: '2026-03-20T08:15:00Z', details: { trigger: 'GIT', branch: 'main' } },
  { id: 'ae-2', action: 'FEATURE_FLAG_TOGGLED', resource: 'FeatureFlag', resourceId: 'ff-1', userId: 'u-2', userName: 'dev@gimi.dev', timestamp: '2026-03-18T14:30:00Z', details: { flag: 'new_payment_flow', enabled: true } },
  { id: 'ae-3', action: 'CONNECTOR_CREATED', resource: 'Connector', resourceId: 'cn-3', userId: 'u-1', userName: 'admin@gimi.dev', timestamp: '2026-03-15T09:00:00Z' },
  { id: 'ae-4', action: 'PIPELINE_UPDATED', resource: 'Pipeline', resourceId: 'pl-2', userId: 'u-3', userName: 'engineer@gimi.dev', timestamp: '2026-03-14T16:00:00Z' },
  { id: 'ae-5', action: 'USER_LOGIN', resource: 'User', resourceId: 'u-1', userId: 'u-1', userName: 'admin@gimi.dev', timestamp: '2026-03-20T07:00:00Z' },
]

const slos: SLO[] = [
  { id: 'slo-1', name: 'API Availability', target: 99.9, current: 99.95, type: 'AVAILABILITY', status: 'MET', errorBudgetRemaining: 85 },
  { id: 'slo-2', name: 'API Latency P99', target: 95, current: 93.2, type: 'LATENCY', status: 'AT_RISK', errorBudgetRemaining: 32 },
  { id: 'slo-3', name: 'Error Rate', target: 99.5, current: 98.8, type: 'ERROR_RATE', status: 'BREACHED', errorBudgetRemaining: 0 },
  { id: 'slo-4', name: 'Deployment Success Rate', target: 98, current: 99.1, type: 'DEPLOYMENT', status: 'MET', errorBudgetRemaining: 72 },
]

export const mockData = {
  pipelines,
  runs,
  workers,
  doraMetrics,
  featureFlags,
  securityScans,
  templates,
  connectors,
  auditEvents,
  slos,
}
