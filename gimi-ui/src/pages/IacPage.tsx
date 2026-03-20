import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Plus, Server, GitBranch, AlertTriangle, DollarSign, Shield, CheckCircle2, XCircle, Clock } from 'lucide-react'
import { cn } from '@/lib/utils'

const workspaces = [
  { id: 1, name: 'prod-vpc-infra', provider: 'Terraform', repo: 'org/infra-live', branch: 'main', status: 'ACTIVE', resources: 47, lastRun: 'Apply succeeded', lastRunTime: '12m ago', driftStatus: 'NO_DRIFT', monthlyCost: '$1,247.50' },
  { id: 2, name: 'staging-eks-cluster', provider: 'Terraform', repo: 'org/k8s-infra', branch: 'main', status: 'DRIFTED', resources: 31, lastRun: 'Drift detected', lastRunTime: '1h ago', driftStatus: 'DRIFTED', monthlyCost: '$892.00' },
  { id: 3, name: 'dev-rds-databases', provider: 'Pulumi', repo: 'org/db-infra', branch: 'develop', status: 'ACTIVE', resources: 12, lastRun: 'Plan: +2 ~1 -0', lastRunTime: '3h ago', driftStatus: 'NO_DRIFT', monthlyCost: '$436.80' },
  { id: 4, name: 'monitoring-stack', provider: 'CDK', repo: 'org/observability', branch: 'main', status: 'PLANNING', resources: 22, lastRun: 'Awaiting approval', lastRunTime: '30m ago', driftStatus: 'NO_DRIFT', monthlyCost: '$310.20' },
  { id: 5, name: 'ci-runners-pool', provider: 'OpenTofu', repo: 'org/ci-infra', branch: 'main', status: 'ERROR', resources: 8, lastRun: 'Apply failed', lastRunTime: '2h ago', driftStatus: 'UNKNOWN', monthlyCost: '$185.60' },
]

const recentRuns = [
  { id: 'r1', workspace: 'prod-vpc-infra', action: 'APPLY', status: 'SUCCEEDED', adds: 0, changes: 2, deletes: 0, triggeredBy: 'GitOps', time: '12m ago' },
  { id: 'r2', workspace: 'staging-eks-cluster', action: 'DRIFT_DETECT', status: 'SUCCEEDED', adds: 0, changes: 0, deletes: 0, triggeredBy: 'Scheduled', time: '1h ago' },
  { id: 'r3', workspace: 'monitoring-stack', action: 'PLAN', status: 'AWAITING_APPROVAL', adds: 3, changes: 1, deletes: 0, triggeredBy: 'PR #42', time: '30m ago' },
  { id: 'r4', workspace: 'ci-runners-pool', action: 'APPLY', status: 'FAILED', adds: 2, changes: 0, deletes: 0, triggeredBy: 'Manual', time: '2h ago' },
  { id: 'r5', workspace: 'dev-rds-databases', action: 'COST_ESTIMATE', status: 'SUCCEEDED', adds: 2, changes: 1, deletes: 0, triggeredBy: 'PR #38', time: '3h ago' },
]

const statusColor: Record<string, string> = {
  ACTIVE: 'text-status-success border-status-success/30',
  DRIFTED: 'text-warning border-warning/30',
  PLANNING: 'text-primary-400 border-primary-400/30',
  APPLYING: 'text-primary-400 border-primary-400/30',
  ERROR: 'text-status-failed border-status-failed/30',
  INACTIVE: 'text-text-tertiary border-border-default',
}

const runStatusIcon: Record<string, typeof CheckCircle2> = {
  SUCCEEDED: CheckCircle2,
  FAILED: XCircle,
  AWAITING_APPROVAL: Clock,
}

export function IacPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-text-primary">Infrastructure as Code</h2>
          <p className="text-sm text-text-tertiary mt-0.5">Manage workspaces, plans, drift detection, and cost estimation</p>
        </div>
        <Button size="sm"><Plus className="h-3.5 w-3.5" /> New Workspace</Button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-4 gap-4">
        <Card>
          <CardContent className="py-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-primary-500/10"><Server className="h-4 w-4 text-primary-400" /></div>
              <div>
                <div className="text-2xl font-bold text-text-primary">{workspaces.length}</div>
                <div className="text-xs text-text-tertiary">Workspaces</div>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-status-success/10"><CheckCircle2 className="h-4 w-4 text-status-success" /></div>
              <div>
                <div className="text-2xl font-bold text-text-primary">120</div>
                <div className="text-xs text-text-tertiary">Managed Resources</div>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-warning/10"><AlertTriangle className="h-4 w-4 text-warning" /></div>
              <div>
                <div className="text-2xl font-bold text-text-primary">1</div>
                <div className="text-xs text-text-tertiary">Drifted</div>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-[#f0883e]/10"><DollarSign className="h-4 w-4 text-[#f0883e]" /></div>
              <div>
                <div className="text-2xl font-bold text-text-primary">$3,072</div>
                <div className="text-xs text-text-tertiary">Monthly Cost</div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Workspaces Table */}
      <Card>
        <CardContent className="p-0">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border-default text-text-tertiary">
                <th className="text-left py-3 px-4 font-medium">Workspace</th>
                <th className="text-left py-3 px-4 font-medium">Provider</th>
                <th className="text-left py-3 px-4 font-medium">Status</th>
                <th className="text-left py-3 px-4 font-medium">Resources</th>
                <th className="text-left py-3 px-4 font-medium">Last Run</th>
                <th className="text-left py-3 px-4 font-medium">Drift</th>
                <th className="text-right py-3 px-4 font-medium">Cost/mo</th>
                <th className="text-right py-3 px-4 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {workspaces.map((ws) => (
                <tr key={ws.id} className="border-b border-border-default last:border-0 hover:bg-bg-hover">
                  <td className="py-3 px-4">
                    <div className="font-medium text-text-primary">{ws.name}</div>
                    <div className="text-xs text-text-tertiary flex items-center gap-1 mt-0.5">
                      <GitBranch className="h-3 w-3" /> {ws.repo} ({ws.branch})
                    </div>
                  </td>
                  <td className="py-3 px-4">
                    <Badge variant="outline" className="text-[10px]">{ws.provider}</Badge>
                  </td>
                  <td className="py-3 px-4">
                    <Badge variant="outline" className={cn('text-[10px]', statusColor[ws.status])}>
                      {ws.status}
                    </Badge>
                  </td>
                  <td className="py-3 px-4 text-text-secondary">{ws.resources}</td>
                  <td className="py-3 px-4">
                    <div className="text-text-secondary text-xs">{ws.lastRun}</div>
                    <div className="text-text-tertiary text-[10px]">{ws.lastRunTime}</div>
                  </td>
                  <td className="py-3 px-4">
                    {ws.driftStatus === 'DRIFTED' ? (
                      <span className="flex items-center gap-1 text-xs text-warning"><AlertTriangle className="h-3 w-3" /> Drift</span>
                    ) : ws.driftStatus === 'NO_DRIFT' ? (
                      <span className="flex items-center gap-1 text-xs text-status-success"><CheckCircle2 className="h-3 w-3" /> Clean</span>
                    ) : (
                      <span className="text-xs text-text-tertiary">Unknown</span>
                    )}
                  </td>
                  <td className="py-3 px-4 text-right text-text-secondary font-mono text-xs">{ws.monthlyCost}</td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex items-center gap-1 justify-end">
                      <Button variant="ghost" size="sm" className="h-7 text-xs">Plan</Button>
                      <Button variant="ghost" size="sm" className="h-7 text-xs">Apply</Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>

      {/* Recent Runs */}
      <div>
        <h3 className="text-sm font-medium text-text-primary mb-3">Recent Runs</h3>
        <div className="space-y-2">
          {recentRuns.map((run) => {
            const Icon = runStatusIcon[run.status] || Clock
            const isSuccess = run.status === 'SUCCEEDED'
            const isFailed = run.status === 'FAILED'
            return (
              <Card key={run.id}>
                <CardContent className="flex items-center justify-between py-3">
                  <div className="flex items-center gap-3">
                    <Icon className={cn('h-4 w-4',
                      isSuccess ? 'text-status-success' :
                      isFailed ? 'text-status-failed' :
                      'text-warning'
                    )} />
                    <div>
                      <span className="text-sm font-medium text-text-primary">{run.workspace}</span>
                      <span className="text-xs text-text-tertiary ml-2">{run.action}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    {(run.adds > 0 || run.changes > 0 || run.deletes > 0) && (
                      <div className="flex items-center gap-2 text-xs font-mono">
                        {run.adds > 0 && <span className="text-status-success">+{run.adds}</span>}
                        {run.changes > 0 && <span className="text-warning">~{run.changes}</span>}
                        {run.deletes > 0 && <span className="text-status-failed">-{run.deletes}</span>}
                      </div>
                    )}
                    <span className="text-xs text-text-tertiary">{run.triggeredBy}</span>
                    <span className="text-xs text-text-tertiary">{run.time}</span>
                  </div>
                </CardContent>
              </Card>
            )
          })}
        </div>
      </div>

      {/* Policy Violations Summary */}
      <Card>
        <CardContent className="py-4">
          <div className="flex items-center gap-2 mb-3">
            <Shield className="h-4 w-4 text-primary-400" />
            <span className="text-sm font-medium text-text-primary">Policy Compliance</span>
          </div>
          <div className="grid grid-cols-3 gap-4 text-center">
            <div>
              <div className="text-xl font-bold text-status-success">4</div>
              <div className="text-xs text-text-tertiary">Compliant</div>
            </div>
            <div>
              <div className="text-xl font-bold text-warning">1</div>
              <div className="text-xs text-text-tertiary">Warnings</div>
            </div>
            <div>
              <div className="text-xl font-bold text-status-failed">0</div>
              <div className="text-xs text-text-tertiary">Violations</div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
