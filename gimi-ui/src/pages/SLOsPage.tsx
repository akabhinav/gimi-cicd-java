import { useSLOs } from '@/api/queries'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import { Activity, Target, AlertTriangle, CheckCircle2, XCircle } from 'lucide-react'

export function SLOsPage() {
  const { data: slos } = useSLOs()

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-text-primary">Service Level Objectives</h2>
        <p className="text-sm text-text-tertiary mt-0.5">Monitor SLO compliance and error budgets</p>
      </div>

      <div className="grid grid-cols-2 gap-4">
        {slos?.map((slo) => {
          const statusIcon = slo.status === 'MET' ? CheckCircle2 : slo.status === 'AT_RISK' ? AlertTriangle : XCircle
          const statusColor = slo.status === 'MET' ? 'text-status-success' : slo.status === 'AT_RISK' ? 'text-warning' : 'text-status-failed'
          const StatusIcon = statusIcon

          return (
            <Card key={slo.id}>
              <CardHeader>
                <div className="flex items-center gap-2">
                  <Activity className="h-4 w-4 text-primary-400" />
                  <CardTitle>{slo.name}</CardTitle>
                </div>
                <div className={cn('flex items-center gap-1 text-xs font-medium', statusColor)}>
                  <StatusIcon className="h-3.5 w-3.5" />
                  {slo.status}
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                {/* SLO gauge */}
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-3xl font-bold text-text-primary">{slo.current}%</div>
                    <div className="text-xs text-text-tertiary">Current / Target: {slo.target}%</div>
                  </div>
                  <div className="relative h-16 w-16">
                    <svg className="h-16 w-16 -rotate-90" viewBox="0 0 64 64">
                      <circle cx="32" cy="32" r="28" fill="none" stroke="#2d333b" strokeWidth="6" />
                      <circle
                        cx="32" cy="32" r="28" fill="none"
                        stroke={slo.status === 'MET' ? '#2ea043' : slo.status === 'AT_RISK' ? '#d29922' : '#f85149'}
                        strokeWidth="6"
                        strokeDasharray={`${(slo.current / 100) * 175.93} 175.93`}
                        strokeLinecap="round"
                      />
                    </svg>
                    <div className="absolute inset-0 flex items-center justify-center">
                      <Target className="h-4 w-4 text-text-tertiary" />
                    </div>
                  </div>
                </div>

                {/* Error budget */}
                <div>
                  <div className="flex items-center justify-between text-xs mb-1.5">
                    <span className="text-text-secondary">Error Budget Remaining</span>
                    <span className={cn('font-medium', slo.errorBudgetRemaining > 50 ? 'text-status-success' : slo.errorBudgetRemaining > 20 ? 'text-warning' : 'text-status-failed')}>
                      {slo.errorBudgetRemaining}%
                    </span>
                  </div>
                  <div className="w-full h-2 rounded-full bg-bg-hover overflow-hidden">
                    <div
                      className={cn('h-full rounded-full transition-all', slo.errorBudgetRemaining > 50 ? 'bg-status-success' : slo.errorBudgetRemaining > 20 ? 'bg-warning' : 'bg-status-failed')}
                      style={{ width: `${slo.errorBudgetRemaining}%` }}
                    />
                  </div>
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>
    </div>
  )
}
