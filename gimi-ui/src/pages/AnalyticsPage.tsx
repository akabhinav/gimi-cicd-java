import { useDORAMetrics } from '@/api/queries'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { AreaChart, Area, BarChart, Bar, LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'
import { TrendingUp, Clock, AlertTriangle, RotateCcw } from 'lucide-react'
import { cn } from '@/lib/utils'

const ratingColors: Record<string, string> = {
  ELITE: 'text-status-success bg-status-success/10 border-status-success/30',
  HIGH: 'text-primary-400 bg-primary-500/10 border-primary-500/30',
  MEDIUM: 'text-warning bg-warning/10 border-warning/30',
  LOW: 'text-status-failed bg-status-failed/10 border-status-failed/30',
}

const tooltipStyle = {
  contentStyle: { background: '#1c2128', border: '1px solid #2d333b', borderRadius: '8px', color: '#e6edf3', fontSize: '12px' },
}

export function AnalyticsPage() {
  const { data: dora } = useDORAMetrics()

  if (!dora) return <div className="text-text-tertiary">Loading...</div>

  const metrics = [
    {
      title: 'Deployment Frequency',
      value: `${dora.summary.deploymentFrequency}/day`,
      description: 'How often code is deployed to production',
      icon: TrendingUp,
      color: '#0078d4',
      data: dora.deploymentFrequency,
      chart: 'bar',
    },
    {
      title: 'Lead Time for Changes',
      value: `${dora.summary.leadTime}h`,
      description: 'Time from commit to production deployment',
      icon: Clock,
      color: '#2ea043',
      data: dora.leadTime,
      chart: 'area',
    },
    {
      title: 'Change Failure Rate',
      value: `${dora.summary.changeFailureRate}%`,
      description: 'Percentage of deployments causing failures',
      icon: AlertTriangle,
      color: '#d29922',
      data: dora.changeFailureRate,
      chart: 'line',
    },
    {
      title: 'Mean Time to Recovery',
      value: `${dora.summary.mttr}h`,
      description: 'Average time to recover from failures',
      icon: RotateCcw,
      color: '#f85149',
      data: dora.mttr,
      chart: 'area',
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-text-primary">DORA Metrics</h2>
          <p className="text-sm text-text-tertiary mt-0.5">DevOps Research and Assessment performance indicators</p>
        </div>
        <div className={cn('px-4 py-2 rounded-lg border text-sm font-semibold', ratingColors[dora.summary.rating])}>
          {dora.summary.rating} Performer
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        {metrics.map((metric) => (
          <Card key={metric.title}>
            <CardHeader>
              <div className="flex items-center gap-2">
                <metric.icon className="h-4 w-4" style={{ color: metric.color }} />
                <CardTitle>{metric.title}</CardTitle>
              </div>
              <span className="text-xl font-bold text-text-primary">{metric.value}</span>
            </CardHeader>
            <CardContent>
              <p className="text-xs text-text-tertiary mb-3">{metric.description}</p>
              <div className="h-40">
                <ResponsiveContainer width="100%" height="100%">
                  {metric.chart === 'bar' ? (
                    <BarChart data={metric.data}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#2d333b" />
                      <XAxis dataKey="date" axisLine={false} tickLine={false} tick={{ fill: '#6e7681', fontSize: 10 }}
                        tickFormatter={(v) => new Date(v).toLocaleDateString('en-US', { weekday: 'short' })} />
                      <YAxis axisLine={false} tickLine={false} tick={{ fill: '#6e7681', fontSize: 10 }} />
                      <Tooltip {...tooltipStyle} />
                      <Bar dataKey="value" fill={metric.color} radius={[4, 4, 0, 0]} name="Deployments" />
                    </BarChart>
                  ) : metric.chart === 'line' ? (
                    <LineChart data={metric.data}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#2d333b" />
                      <XAxis dataKey="date" axisLine={false} tickLine={false} tick={{ fill: '#6e7681', fontSize: 10 }}
                        tickFormatter={(v) => new Date(v).toLocaleDateString('en-US', { weekday: 'short' })} />
                      <YAxis axisLine={false} tickLine={false} tick={{ fill: '#6e7681', fontSize: 10 }} />
                      <Tooltip {...tooltipStyle} />
                      <Line type="monotone" dataKey="value" stroke={metric.color} strokeWidth={2} dot={{ fill: metric.color, r: 3 }} name="%" />
                    </LineChart>
                  ) : (
                    <AreaChart data={metric.data}>
                      <defs>
                        <linearGradient id={`grad-${metric.title}`} x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor={metric.color} stopOpacity={0.3} />
                          <stop offset="95%" stopColor={metric.color} stopOpacity={0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#2d333b" />
                      <XAxis dataKey="date" axisLine={false} tickLine={false} tick={{ fill: '#6e7681', fontSize: 10 }}
                        tickFormatter={(v) => new Date(v).toLocaleDateString('en-US', { weekday: 'short' })} />
                      <YAxis axisLine={false} tickLine={false} tick={{ fill: '#6e7681', fontSize: 10 }} />
                      <Tooltip {...tooltipStyle} />
                      <Area type="monotone" dataKey="value" stroke={metric.color} strokeWidth={2} fill={`url(#grad-${metric.title})`} name="Hours" />
                    </AreaChart>
                  )}
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
