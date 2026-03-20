import { useConnectors } from '@/api/queries'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Plus, Plug, GitBranch, Container, Cloud, Ship, MessageSquare, CheckCircle2, XCircle, AlertCircle } from 'lucide-react'
import { cn, formatDate } from '@/lib/utils'

const connectorIcons: Record<string, React.ComponentType<{ className?: string }>> = {
  GIT: GitBranch,
  DOCKER_REGISTRY: Container,
  AWS: Cloud,
  KUBERNETES: Ship,
  SLACK: MessageSquare,
}

const statusConfig: Record<string, { icon: React.ComponentType<{ className?: string }>; color: string }> = {
  CONNECTED: { icon: CheckCircle2, color: 'text-status-success' },
  DISCONNECTED: { icon: XCircle, color: 'text-text-tertiary' },
  ERROR: { icon: AlertCircle, color: 'text-status-failed' },
}

export function ConnectorsPage() {
  const { data: connectors } = useConnectors()

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-text-primary">Connectors</h2>
          <p className="text-sm text-text-tertiary mt-0.5">Manage integrations with external services</p>
        </div>
        <Button size="sm"><Plus className="h-3.5 w-3.5" /> New Connector</Button>
      </div>

      <div className="grid grid-cols-2 gap-4">
        {connectors?.map((connector) => {
          const Icon = connectorIcons[connector.type] || Plug
          const status = statusConfig[connector.status]
          const StatusIcon = status?.icon || AlertCircle

          return (
            <Card key={connector.id} hoverable>
              <CardContent className="flex items-center justify-between py-4">
                <div className="flex items-center gap-4">
                  <div className="p-2.5 rounded-lg bg-bg-secondary border border-border-primary">
                    <Icon className="h-5 w-5 text-primary-400" />
                  </div>
                  <div>
                    <div className="font-medium text-text-primary">{connector.name}</div>
                    <div className="flex items-center gap-2 mt-1">
                      <Badge variant="outline" className="text-[10px]">{connector.type}</Badge>
                      {connector.lastTestAt && (
                        <span className="text-[10px] text-text-tertiary">Tested {formatDate(connector.lastTestAt)}</span>
                      )}
                    </div>
                  </div>
                </div>
                <div className={cn('flex items-center gap-1.5 text-xs font-medium', status?.color)}>
                  <StatusIcon className="h-3.5 w-3.5" />
                  {connector.status}
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>
    </div>
  )
}
