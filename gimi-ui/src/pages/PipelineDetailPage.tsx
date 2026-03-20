import { useParams, Link } from 'react-router-dom'
import { usePipeline, usePipelineRuns } from '@/api/queries'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { StatusBadge, Badge } from '@/components/ui/badge'
import { PipelineGraph } from '@/components/pipeline/PipelineGraph'
import { formatDuration, formatDate } from '@/lib/utils'
import { Play, GitBranch, Clock, Settings, ArrowLeft } from 'lucide-react'

export function PipelineDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: pipeline } = usePipeline(id!)
  const { data: runs } = usePipelineRuns(id)

  if (!pipeline) return <div className="text-text-tertiary">Loading...</div>

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/pipelines" className="p-1.5 rounded-md hover:bg-bg-hover text-text-tertiary">
            <ArrowLeft className="h-4 w-4" />
          </Link>
          <div>
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-semibold text-text-primary">{pipeline.name}</h2>
              {pipeline.status && <StatusBadge status={pipeline.status} />}
            </div>
            <p className="text-sm text-text-tertiary mt-0.5">{pipeline.description}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm">
            <Settings className="h-3.5 w-3.5" /> Configure
          </Button>
          <Button size="sm">
            <Play className="h-3.5 w-3.5" /> Run Pipeline
          </Button>
        </div>
      </div>

      <Tabs defaultValue="studio">
        <TabsList>
          <TabsTrigger value="studio">Pipeline Studio</TabsTrigger>
          <TabsTrigger value="executions">Execution History</TabsTrigger>
          <TabsTrigger value="triggers">Triggers</TabsTrigger>
          <TabsTrigger value="inputs">Input Sets</TabsTrigger>
        </TabsList>

        <TabsContent value="studio">
          <Card>
            <CardHeader>
              <CardTitle>Pipeline Stages</CardTitle>
              <div className="flex items-center gap-2">
                {pipeline.tags?.map(tag => (
                  <Badge key={tag} variant="outline" className="text-[10px]">{tag}</Badge>
                ))}
              </div>
            </CardHeader>
            <CardContent>
              <PipelineGraph stages={pipeline.stages} />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="executions">
          <Card>
            <div className="divide-y divide-border-primary">
              {runs?.map((run) => (
                <Link
                  key={run.id}
                  to={`/executions/${run.id}`}
                  className="flex items-center justify-between px-5 py-3 hover:bg-bg-hover transition-colors"
                >
                  <div className="flex items-center gap-4">
                    <StatusBadge status={run.status} />
                    <div>
                      <div className="text-sm text-text-primary">
                        <span className="font-mono text-xs text-primary-400">{run.commit}</span>
                        <span className="ml-2">{run.commitMessage}</span>
                      </div>
                      <div className="flex items-center gap-2 mt-0.5 text-xs text-text-tertiary">
                        <GitBranch className="h-3 w-3" /> {run.branch}
                        <span className="ml-2">{run.trigger}</span>
                      </div>
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="flex items-center gap-1 text-xs text-text-secondary">
                      <Clock className="h-3 w-3" /> {run.duration ? formatDuration(run.duration) : '-'}
                    </div>
                    <div className="text-xs text-text-tertiary">{formatDate(run.startedAt)}</div>
                  </div>
                </Link>
              ))}
              {(!runs || runs.length === 0) && (
                <div className="px-5 py-8 text-center text-sm text-text-tertiary">
                  No executions yet. Run the pipeline to see results.
                </div>
              )}
            </div>
          </Card>
        </TabsContent>

        <TabsContent value="triggers">
          <Card>
            <CardContent>
              <div className="space-y-3">
                {pipeline.triggers?.map((trigger) => (
                  <div key={trigger.id} className="flex items-center justify-between p-3 rounded-md bg-bg-secondary border border-border-primary">
                    <div className="flex items-center gap-3">
                      <Badge variant="outline">{trigger.type}</Badge>
                      <span className="text-sm text-text-primary">{trigger.name}</span>
                    </div>
                    <Badge variant={trigger.enabled ? 'default' : 'outline'}>
                      {trigger.enabled ? 'Enabled' : 'Disabled'}
                    </Badge>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="inputs">
          <Card>
            <CardContent>
              <div className="text-sm text-text-tertiary text-center py-8">
                No input sets configured. Create an input set to parameterize pipeline runs.
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
