import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Plus, Bell, MessageSquare, Mail, AlertTriangle } from 'lucide-react'

const channels = [
  { id: 1, name: 'Slack - #deployments', type: 'SLACK', icon: MessageSquare, events: ['Pipeline Success', 'Pipeline Failure'], enabled: true },
  { id: 2, name: 'Email - team@gimi.dev', type: 'EMAIL', icon: Mail, events: ['Approval Required', 'Security Alerts'], enabled: true },
  { id: 3, name: 'Slack - #alerts', type: 'SLACK', icon: MessageSquare, events: ['Worker Offline', 'SLO Breach'], enabled: true },
  { id: 4, name: 'PagerDuty - Production', type: 'PAGERDUTY', icon: AlertTriangle, events: ['Pipeline Failure', 'SLO Breach'], enabled: false },
]

export function NotificationsPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-text-primary">Notification Channels</h2>
          <p className="text-sm text-text-tertiary mt-0.5">Configure where pipeline events are sent</p>
        </div>
        <Button size="sm"><Plus className="h-3.5 w-3.5" /> Add Channel</Button>
      </div>

      <div className="space-y-3">
        {channels.map((channel) => (
          <Card key={channel.id}>
            <CardContent className="flex items-center justify-between py-4">
              <div className="flex items-center gap-4">
                <div className="p-2.5 rounded-lg bg-bg-secondary border border-border-primary">
                  <channel.icon className="h-5 w-5 text-primary-400" />
                </div>
                <div>
                  <div className="font-medium text-text-primary">{channel.name}</div>
                  <div className="flex items-center gap-1.5 mt-1.5">
                    {channel.events.map(e => <Badge key={e} variant="outline" className="text-[10px]">{e}</Badge>)}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <Badge variant={channel.enabled ? 'default' : 'outline'}>
                  {channel.enabled ? 'Active' : 'Disabled'}
                </Badge>
                <Button variant="ghost" size="sm">Edit</Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
