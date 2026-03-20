import { useFeatureFlags } from '@/api/queries'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Table, TableHeader, TableHead, TableBody, TableRow, TableCell } from '@/components/ui/table'
import { Flag, Plus, Search, Check, X } from 'lucide-react'
import { cn, formatDate } from '@/lib/utils'
import { useState } from 'react'

export function FeatureFlagsPage() {
  const { data: flags } = useFeatureFlags()
  const [search, setSearch] = useState('')

  const filtered = flags?.filter(f =>
    f.name.toLowerCase().includes(search.toLowerCase()) ||
    f.key.toLowerCase().includes(search.toLowerCase())
  ) || []

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-text-primary">Feature Flags</h2>
          <p className="text-sm text-text-tertiary mt-0.5">{flags?.length || 0} flags configured</p>
        </div>
        <Button size="sm"><Plus className="h-3.5 w-3.5" /> New Flag</Button>
      </div>

      <div className="w-80">
        <Input
          placeholder="Search flags..."
          icon={<Search className="h-3.5 w-3.5" />}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Flag</TableHead>
              <TableHead>Key</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Development</TableHead>
              <TableHead>Staging</TableHead>
              <TableHead>Production</TableHead>
              <TableHead>Updated</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filtered.map((flag) => (
              <TableRow key={flag.id}>
                <TableCell>
                  <div className="flex items-center gap-3">
                    <div className={cn('p-1.5 rounded-md', flag.enabled ? 'bg-status-success/10' : 'bg-bg-hover')}>
                      <Flag className={cn('h-4 w-4', flag.enabled ? 'text-status-success' : 'text-text-tertiary')} />
                    </div>
                    <div>
                      <div className="font-medium text-text-primary">{flag.name}</div>
                      <div className="text-xs text-text-tertiary mt-0.5">{flag.description}</div>
                    </div>
                  </div>
                </TableCell>
                <TableCell className="font-mono text-xs text-text-secondary">{flag.key}</TableCell>
                <TableCell><Badge variant="outline">{flag.type}</Badge></TableCell>
                <TableCell><EnvToggle enabled={flag.environments.development} /></TableCell>
                <TableCell><EnvToggle enabled={flag.environments.staging} /></TableCell>
                <TableCell><EnvToggle enabled={flag.environments.production} /></TableCell>
                <TableCell className="text-xs text-text-tertiary">{formatDate(flag.updatedAt)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </div>
  )
}

function EnvToggle({ enabled }: { enabled: boolean }) {
  return (
    <div className={cn(
      'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs',
      enabled ? 'bg-status-success/10 text-status-success' : 'bg-bg-hover text-text-tertiary'
    )}>
      {enabled ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
      {enabled ? 'On' : 'Off'}
    </div>
  )
}
