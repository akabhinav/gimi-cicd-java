import { useState } from 'react'
import { Link } from 'react-router-dom'
import { usePipelines } from '@/api/queries'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge, StatusBadge } from '@/components/ui/badge'
import { formatDate } from '@/lib/utils'
import { Search, Plus, GitBranch, MoreVertical, Clock, Layers } from 'lucide-react'

export function PipelinesPage() {
  const { data: pipelines, isLoading } = usePipelines()
  const [search, setSearch] = useState('')

  const filtered = pipelines?.filter(p =>
    p.name.toLowerCase().includes(search.toLowerCase()) ||
    p.description?.toLowerCase().includes(search.toLowerCase())
  ) || []

  if (isLoading) return <LoadingSkeleton />

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-text-primary">Pipelines</h2>
          <p className="text-sm text-text-tertiary mt-0.5">{pipelines?.length || 0} pipelines configured</p>
        </div>
        <Button>
          <Plus className="h-4 w-4" />
          New Pipeline
        </Button>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3">
        <div className="w-80">
          <Input
            placeholder="Search pipelines..."
            icon={<Search className="h-3.5 w-3.5" />}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {/* Pipeline Cards */}
      <div className="grid gap-3">
        {filtered.map((pipeline) => (
          <Link key={pipeline.id} to={`/pipelines/${pipeline.id}`}>
            <Card hoverable className="p-0">
              <div className="flex items-center justify-between p-4">
                <div className="flex items-center gap-4">
                  <div className="h-10 w-10 rounded-lg bg-primary-500/10 flex items-center justify-center">
                    <GitBranch className="h-5 w-5 text-primary-400" />
                  </div>
                  <div>
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-semibold text-text-primary">{pipeline.name}</span>
                      {pipeline.status && <StatusBadge status={pipeline.status} />}
                    </div>
                    <p className="text-xs text-text-tertiary mt-0.5">{pipeline.description}</p>
                    <div className="flex items-center gap-3 mt-2">
                      {pipeline.tags?.map(tag => (
                        <Badge key={tag} variant="outline" className="text-[10px]">{tag}</Badge>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-6">
                  <div className="text-right">
                    <div className="flex items-center gap-1.5 text-xs text-text-tertiary">
                      <Layers className="h-3 w-3" />
                      {pipeline.stages.length} stages
                    </div>
                    <div className="flex items-center gap-1.5 text-xs text-text-tertiary mt-1">
                      <Clock className="h-3 w-3" />
                      {formatDate(pipeline.updatedAt)}
                    </div>
                  </div>
                  <button
                    className="p-1.5 text-text-tertiary hover:text-text-secondary rounded-md hover:bg-bg-active"
                    onClick={(e) => e.preventDefault()}
                  >
                    <MoreVertical className="h-4 w-4" />
                  </button>
                </div>
              </div>

              {/* Stage progress bar */}
              <div className="flex h-1">
                {pipeline.stages.map((stage) => (
                  <div
                    key={stage.id}
                    className={`flex-1 ${
                      stage.status === 'SUCCESS' ? 'bg-status-success' :
                      stage.status === 'RUNNING' ? 'bg-status-running' :
                      stage.status === 'FAILED' ? 'bg-status-failed' :
                      'bg-border-primary'
                    }`}
                  />
                ))}
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  )
}

function LoadingSkeleton() {
  return (
    <div className="space-y-4">
      {[1, 2, 3].map(i => (
        <div key={i} className="h-28 rounded-lg bg-bg-card border border-border-primary animate-pulse" />
      ))}
    </div>
  )
}
