import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Plus, Scale, CheckCircle2, XCircle, Shield } from 'lucide-react'
import { cn } from '@/lib/utils'

const policies = [
  { id: 1, name: 'Require Security Scan', description: 'All pipelines must include a security scan stage before deployment', type: 'OPA', status: 'ACTIVE', severity: 'HIGH', lastEval: 'PASS' },
  { id: 2, name: 'Approval Required for Production', description: 'Production deployments require at least one approval', type: 'OPA', status: 'ACTIVE', severity: 'CRITICAL', lastEval: 'PASS' },
  { id: 3, name: 'No Hardcoded Secrets', description: 'Pipeline configurations must not contain hardcoded secrets or API keys', type: 'OPA', status: 'ACTIVE', severity: 'CRITICAL', lastEval: 'FAIL' },
  { id: 4, name: 'Max Pipeline Duration', description: 'Pipelines must complete within 30 minutes', type: 'OPA', status: 'INACTIVE', severity: 'MEDIUM', lastEval: 'PASS' },
]

export function GovernancePage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-text-primary">Governance & Policies</h2>
          <p className="text-sm text-text-tertiary mt-0.5">OPA-based policy enforcement for pipelines</p>
        </div>
        <Button size="sm"><Plus className="h-3.5 w-3.5" /> New Policy</Button>
      </div>

      <div className="space-y-3">
        {policies.map((policy) => (
          <Card key={policy.id}>
            <CardContent className="flex items-center justify-between py-4">
              <div className="flex items-center gap-4">
                <div className={cn('p-2.5 rounded-lg', policy.status === 'ACTIVE' ? 'bg-primary-500/10' : 'bg-bg-hover')}>
                  <Scale className={cn('h-5 w-5', policy.status === 'ACTIVE' ? 'text-primary-400' : 'text-text-tertiary')} />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-text-primary">{policy.name}</span>
                    <Badge variant="outline" className="text-[10px]">{policy.type}</Badge>
                    <Badge variant="outline" className={cn('text-[10px]',
                      policy.severity === 'CRITICAL' ? 'border-status-failed/30 text-status-failed' :
                      policy.severity === 'HIGH' ? 'border-[#f0883e]/30 text-[#f0883e]' :
                      'border-warning/30 text-warning'
                    )}>
                      {policy.severity}
                    </Badge>
                  </div>
                  <div className="text-xs text-text-tertiary mt-1">{policy.description}</div>
                </div>
              </div>
              <div className="flex items-center gap-4">
                <div className={cn('flex items-center gap-1.5 text-xs font-medium',
                  policy.lastEval === 'PASS' ? 'text-status-success' : 'text-status-failed'
                )}>
                  {policy.lastEval === 'PASS' ? <CheckCircle2 className="h-3.5 w-3.5" /> : <XCircle className="h-3.5 w-3.5" />}
                  {policy.lastEval}
                </div>
                <Badge variant={policy.status === 'ACTIVE' ? 'default' : 'outline'}>
                  {policy.status}
                </Badge>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
