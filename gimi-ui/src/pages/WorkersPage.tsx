import { useWorkers } from '@/api/queries'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Table, TableHeader, TableHead, TableBody, TableRow, TableCell } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Server, Plus, Cpu, HardDrive, Wifi, WifiOff } from 'lucide-react'
import { cn, formatDate } from '@/lib/utils'

export function WorkersPage() {
  const { data: workers } = useWorkers()

  const activeCount = workers?.filter(w => w.status !== 'OFFLINE').length || 0
  const totalCapacity = workers?.reduce((sum, w) => sum + w.capacity, 0) || 0
  const activeJobs = workers?.reduce((sum, w) => sum + w.activeJobs, 0) || 0

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-text-primary">Delegates</h2>
          <p className="text-sm text-text-tertiary mt-0.5">Manage distributed execution workers</p>
        </div>
        <Button size="sm"><Plus className="h-3.5 w-3.5" /> New Delegate</Button>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <Card>
          <CardContent className="flex items-center gap-4 py-5">
            <div className="p-2.5 rounded-lg bg-status-success/10">
              <Server className="h-5 w-5 text-status-success" />
            </div>
            <div>
              <div className="text-2xl font-bold">{activeCount}/{workers?.length || 0}</div>
              <div className="text-xs text-text-tertiary">Active Delegates</div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-4 py-5">
            <div className="p-2.5 rounded-lg bg-primary-500/10">
              <Cpu className="h-5 w-5 text-primary-400" />
            </div>
            <div>
              <div className="text-2xl font-bold">{activeJobs}/{totalCapacity}</div>
              <div className="text-xs text-text-tertiary">Jobs / Capacity</div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-4 py-5">
            <div className="p-2.5 rounded-lg bg-warning/10">
              <HardDrive className="h-5 w-5 text-warning" />
            </div>
            <div>
              <div className="text-2xl font-bold">{totalCapacity > 0 ? Math.round((activeJobs / totalCapacity) * 100) : 0}%</div>
              <div className="text-xs text-text-tertiary">Utilization</div>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Delegate</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Labels</TableHead>
              <TableHead>Jobs</TableHead>
              <TableHead>CPU</TableHead>
              <TableHead>Memory</TableHead>
              <TableHead>Last Heartbeat</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {workers?.map((worker) => (
              <TableRow key={worker.id}>
                <TableCell>
                  <div className="flex items-center gap-3">
                    {worker.status === 'OFFLINE'
                      ? <WifiOff className="h-4 w-4 text-text-tertiary" />
                      : <Wifi className="h-4 w-4 text-status-success" />}
                    <div>
                      <div className="font-medium text-text-primary">{worker.name}</div>
                      <div className="text-xs text-text-tertiary font-mono">{worker.hostname}</div>
                    </div>
                  </div>
                </TableCell>
                <TableCell>
                  <span className={cn(
                    'inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium',
                    worker.status === 'ACTIVE' ? 'bg-status-success/15 text-status-success' :
                    worker.status === 'BUSY' ? 'bg-status-running/15 text-status-running' :
                    worker.status === 'IDLE' ? 'bg-status-queued/15 text-status-queued' :
                    'bg-bg-hover text-text-tertiary'
                  )}>
                    <span className={cn('h-1.5 w-1.5 rounded-full bg-current', worker.status === 'BUSY' && 'animate-pulse')} />
                    {worker.status}
                  </span>
                </TableCell>
                <TableCell>
                  <div className="flex flex-wrap gap-1">
                    {worker.labels?.map(l => <Badge key={l} variant="outline" className="text-[10px]">{l}</Badge>)}
                  </div>
                </TableCell>
                <TableCell>{worker.activeJobs}/{worker.capacity}</TableCell>
                <TableCell>
                  <UsageBar value={worker.cpuUsage || 0} />
                </TableCell>
                <TableCell>
                  <UsageBar value={worker.memoryUsage || 0} />
                </TableCell>
                <TableCell className="text-xs text-text-tertiary">{formatDate(worker.lastHeartbeat)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </div>
  )
}

function UsageBar({ value }: { value: number }) {
  return (
    <div className="flex items-center gap-2">
      <div className="w-16 h-1.5 rounded-full bg-bg-hover overflow-hidden">
        <div
          className={cn('h-full rounded-full', value > 80 ? 'bg-status-failed' : value > 50 ? 'bg-warning' : 'bg-status-success')}
          style={{ width: `${value}%` }}
        />
      </div>
      <span className="text-xs text-text-tertiary">{value}%</span>
    </div>
  )
}
