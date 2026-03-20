import { useState } from 'react'
import { Link } from 'react-router-dom'
import { usePipelineRuns } from '@/api/queries'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { StatusBadge, Badge } from '@/components/ui/badge'
import { Table, TableHeader, TableHead, TableBody, TableRow, TableCell } from '@/components/ui/table'
import { formatDuration, formatDate } from '@/lib/utils'
import { Search, GitBranch, Clock, ExternalLink } from 'lucide-react'

export function ExecutionsPage() {
  const { data: runs } = usePipelineRuns()
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('all')

  const filtered = runs?.filter(r => {
    const matchSearch = !search ||
      r.pipelineName?.toLowerCase().includes(search.toLowerCase()) ||
      r.commit?.includes(search) ||
      r.branch?.includes(search)
    const matchStatus = statusFilter === 'all' || r.status === statusFilter
    return matchSearch && matchStatus
  }) || []

  const statuses = ['all', 'RUNNING', 'SUCCESS', 'FAILED', 'QUEUED']

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-text-primary">Pipeline Executions</h2>
        <p className="text-sm text-text-tertiary mt-0.5">{runs?.length || 0} total executions</p>
      </div>

      <div className="flex items-center gap-3">
        <div className="w-80">
          <Input
            placeholder="Search by pipeline, commit, branch..."
            icon={<Search className="h-3.5 w-3.5" />}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="flex items-center gap-1 ml-2">
          {statuses.map(s => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                statusFilter === s
                  ? 'bg-primary-500/15 text-primary-400 border border-primary-500/30'
                  : 'text-text-tertiary hover:text-text-secondary hover:bg-bg-hover border border-transparent'
              }`}
            >
              {s === 'all' ? 'All' : s}
            </button>
          ))}
        </div>
      </div>

      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Status</TableHead>
              <TableHead>Pipeline</TableHead>
              <TableHead>Trigger</TableHead>
              <TableHead>Branch / Commit</TableHead>
              <TableHead>Duration</TableHead>
              <TableHead>Started</TableHead>
              <TableHead></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filtered.map((run) => (
              <TableRow key={run.id}>
                <TableCell><StatusBadge status={run.status} /></TableCell>
                <TableCell>
                  <div className="font-medium text-text-primary">{run.pipelineName}</div>
                  <div className="text-xs text-text-tertiary mt-0.5">{run.commitMessage}</div>
                </TableCell>
                <TableCell><Badge variant="outline">{run.trigger}</Badge></TableCell>
                <TableCell>
                  <div className="flex items-center gap-1.5 text-xs">
                    <GitBranch className="h-3 w-3 text-text-tertiary" />
                    <span className="text-primary-400">{run.branch}</span>
                    {run.commit && <span className="font-mono text-text-tertiary ml-1">{run.commit}</span>}
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-1 text-xs text-text-secondary">
                    <Clock className="h-3 w-3" />
                    {run.duration ? formatDuration(run.duration) : '-'}
                  </div>
                </TableCell>
                <TableCell className="text-xs text-text-tertiary">{formatDate(run.startedAt)}</TableCell>
                <TableCell>
                  <Link to={`/executions/${run.id}`} className="text-primary-400 hover:text-primary-300">
                    <ExternalLink className="h-3.5 w-3.5" />
                  </Link>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </div>
  )
}
