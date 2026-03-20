import { useAuditEvents } from '@/api/queries'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Table, TableHeader, TableHead, TableBody, TableRow, TableCell } from '@/components/ui/table'
import { formatDate } from '@/lib/utils'
import { ClipboardList, User } from 'lucide-react'

export function AuditPage() {
  const { data: events } = useAuditEvents()

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-text-primary">Audit Trail</h2>
        <p className="text-sm text-text-tertiary mt-0.5">Track all actions and changes across the platform</p>
      </div>

      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Action</TableHead>
              <TableHead>Resource</TableHead>
              <TableHead>User</TableHead>
              <TableHead>Timestamp</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {events?.map((event) => (
              <TableRow key={event.id}>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <ClipboardList className="h-3.5 w-3.5 text-text-tertiary" />
                    <Badge variant="outline">{event.action}</Badge>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="text-sm text-text-primary">{event.resource}</div>
                  <div className="text-xs text-text-tertiary font-mono">{event.resourceId}</div>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <User className="h-3.5 w-3.5 text-text-tertiary" />
                    <span className="text-sm text-text-secondary">{event.userName}</span>
                  </div>
                </TableCell>
                <TableCell className="text-xs text-text-tertiary">{formatDate(event.timestamp)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </div>
  )
}
