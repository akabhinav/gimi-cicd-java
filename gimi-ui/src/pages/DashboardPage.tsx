import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { StatusBadge } from '@/components/ui/badge'
import { usePipelines, usePipelineRuns, useWorkers, useDORAMetrics } from '@/api/queries'
import { formatDuration, formatDate } from '@/lib/utils'
import { GitBranch, Play, Server, TrendingUp, Clock, AlertTriangle, CheckCircle2, XCircle } from 'lucide-react'
import { Link } from 'react-router-dom'
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'

export function DashboardPage() {
  const { data: pipelines } = usePipelines()
  const { data: runs } = usePipelineRuns()
  const { data: workers } = useWorkers()
  const { data: dora } = useDORAMetrics()

  const activeRuns = runs?.filter(r => r.status === 'RUNNING' || r.status === 'QUEUED') || []
  const successRuns = runs?.filter(r => r.status === 'SUCCESS') || []
  const failedRuns = runs?.filter(r => r.status === 'FAILED') || []
  const activeWorkers = workers?.filter(w => w.status !== 'OFFLINE') || []

  const stats = [
    { label: 'Total Pipelines', value: pipelines?.length || 0, icon: GitBranch, color: 'text-primary-400', bg: 'bg-primary-500/10' },
    { label: 'Active Runs', value: activeRuns.length, icon: Play, color: 'text-status-running', bg: 'bg-status-running/10' },
    { label: 'Success Rate', value: runs?.length ? `${Math.round((successRuns.length / runs.length) * 100)}%` : '0%', icon: CheckCircle2, color: 'text-status-success', bg: 'bg-status-success/10' },
    { label: 'Active Delegates', value: `${activeWorkers.length}/${workers?.length || 0}`, icon: Server, color: 'text-warning', bg: 'bg-warning/10' },
  ]

  return (
    <div className="space-y-6">
      {/* Stats Grid */}
      <div className="grid grid-cols-4 gap-4">
        {stats.map((stat) => (
          <Card key={stat.label}>
            <CardContent className="flex items-center gap-4 py-5">
              <div className={`p-2.5 rounded-lg ${stat.bg}`}>
                <stat.icon className={`h-5 w-5 ${stat.color}`} />
              </div>
              <div>
                <div className="text-2xl font-bold text-text-primary">{stat.value}</div>
                <div className="text-xs text-text-tertiary">{stat.label}</div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-3 gap-4">
        {/* Deployment Frequency Chart */}
        <Card className="col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrendingUp className="h-4 w-4 text-primary-400" />
              Deployment Frequency
            </CardTitle>
            <span className="text-xs text-text-tertiary">Last 7 days</span>
          </CardHeader>
          <CardContent>
            <div className="h-48">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={dora?.deploymentFrequency || []}>
                  <defs>
                    <linearGradient id="colorDeploy" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#0078d4" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#0078d4" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <XAxis dataKey="date" axisLine={false} tickLine={false} tick={{ fill: '#6e7681', fontSize: 11 }}
                    tickFormatter={(v) => new Date(v).toLocaleDateString('en-US', { weekday: 'short' })} />
                  <YAxis axisLine={false} tickLine={false} tick={{ fill: '#6e7681', fontSize: 11 }} />
                  <Tooltip
                    contentStyle={{ background: '#1c2128', border: '1px solid #2d333b', borderRadius: '8px', color: '#e6edf3', fontSize: '12px' }}
                    labelFormatter={(v) => new Date(v).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                  />
                  <Area type="monotone" dataKey="value" stroke="#0078d4" strokeWidth={2} fill="url(#colorDeploy)" name="Deployments" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* DORA Summary */}
        <Card>
          <CardHeader>
            <CardTitle>DORA Performance</CardTitle>
            <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${dora?.summary.rating === 'ELITE' ? 'bg-status-success/15 text-status-success' : dora?.summary.rating === 'HIGH' ? 'bg-primary-500/15 text-primary-400' : 'bg-warning/15 text-warning'}`}>
              {dora?.summary.rating}
            </span>
          </CardHeader>
          <CardContent className="space-y-4">
            {[
              { label: 'Deploy Frequency', value: `${dora?.summary.deploymentFrequency}/day`, icon: TrendingUp },
              { label: 'Lead Time', value: `${dora?.summary.leadTime}h`, icon: Clock },
              { label: 'Change Failure', value: `${dora?.summary.changeFailureRate}%`, icon: AlertTriangle },
              { label: 'MTTR', value: `${dora?.summary.mttr}h`, icon: XCircle },
            ].map((metric) => (
              <div key={metric.label} className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm text-text-secondary">
                  <metric.icon className="h-3.5 w-3.5" />
                  {metric.label}
                </div>
                <span className="text-sm font-medium text-text-primary">{metric.value}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>

      {/* Recent Executions */}
      <Card>
        <CardHeader>
          <CardTitle>Recent Executions</CardTitle>
          <Link to="/executions" className="text-xs text-primary-400 hover:text-primary-300">View all</Link>
        </CardHeader>
        <div className="divide-y divide-border-primary">
          {runs?.slice(0, 5).map((run) => (
            <Link
              key={run.id}
              to={`/executions/${run.id}`}
              className="flex items-center justify-between px-5 py-3 hover:bg-bg-hover transition-colors"
            >
              <div className="flex items-center gap-4">
                <StatusBadge status={run.status} />
                <div>
                  <div className="text-sm font-medium text-text-primary">{run.pipelineName}</div>
                  <div className="text-xs text-text-tertiary mt-0.5">
                    {run.branch && <span className="text-primary-400">{run.branch}</span>}
                    {run.commit && <span className="ml-2 font-mono">{run.commit}</span>}
                    {run.commitMessage && <span className="ml-2">{run.commitMessage}</span>}
                  </div>
                </div>
              </div>
              <div className="text-right">
                <div className="text-xs text-text-secondary">{run.duration ? formatDuration(run.duration) : '-'}</div>
                <div className="text-xs text-text-tertiary">{formatDate(run.startedAt)}</div>
              </div>
            </Link>
          ))}
        </div>
      </Card>
    </div>
  )
}
