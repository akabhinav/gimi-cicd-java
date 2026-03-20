import { useParams, Link } from 'react-router-dom'
import { usePipelineRun } from '@/api/queries'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { StatusBadge, Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { formatDuration, formatDate, cn } from '@/lib/utils'
import { ArrowLeft, GitBranch, Clock, RotateCcw, XCircle, ChevronDown, ChevronRight, Terminal } from 'lucide-react'
import { useState } from 'react'
import type { StageExecution } from '@/types'

export function ExecutionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: run } = usePipelineRun(id!)
  const [expandedStage, setExpandedStage] = useState<string | null>(null)

  if (!run) return <div className="text-text-tertiary">Loading...</div>

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/executions" className="p-1.5 rounded-md hover:bg-bg-hover text-text-tertiary">
            <ArrowLeft className="h-4 w-4" />
          </Link>
          <div>
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-semibold text-text-primary">{run.pipelineName}</h2>
              <StatusBadge status={run.status} />
            </div>
            <div className="flex items-center gap-4 mt-1 text-xs text-text-tertiary">
              <span className="flex items-center gap-1"><GitBranch className="h-3 w-3" /> {run.branch}</span>
              <span className="font-mono">{run.commit}</span>
              <span>{run.commitMessage}</span>
              <span className="flex items-center gap-1"><Clock className="h-3 w-3" /> {run.duration ? formatDuration(run.duration) : 'In progress'}</span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {run.status === 'RUNNING' && (
            <Button variant="danger" size="sm"><XCircle className="h-3.5 w-3.5" /> Abort</Button>
          )}
          <Button variant="secondary" size="sm"><RotateCcw className="h-3.5 w-3.5" /> Re-run</Button>
        </div>
      </div>

      {/* Stage execution timeline */}
      <div className="grid grid-cols-3 gap-6">
        {/* Left: Stage list */}
        <div className="col-span-1 space-y-2">
          <Card>
            <CardHeader><CardTitle>Stages</CardTitle></CardHeader>
            <div className="divide-y divide-border-primary">
              {run.stages.map((stage) => (
                <StageRow
                  key={stage.id}
                  stage={stage}
                  expanded={expandedStage === stage.id}
                  onToggle={() => setExpandedStage(expandedStage === stage.id ? null : stage.id)}
                />
              ))}
            </div>
          </Card>
        </div>

        {/* Right: Log viewer */}
        <div className="col-span-2">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Terminal className="h-4 w-4 text-primary-400" />
                Execution Logs
              </CardTitle>
              <Badge variant="outline">Live</Badge>
            </CardHeader>
            <CardContent className="p-0">
              <LogViewer run={run} expandedStage={expandedStage} />
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Execution info */}
      <div className="grid grid-cols-4 gap-4">
        <InfoCard label="Trigger" value={run.trigger} />
        <InfoCard label="Started" value={formatDate(run.startedAt)} />
        <InfoCard label="Finished" value={run.finishedAt ? formatDate(run.finishedAt) : 'In progress'} />
        <InfoCard label="Duration" value={run.duration ? formatDuration(run.duration) : '-'} />
      </div>
    </div>
  )
}

function StageRow({ stage, expanded, onToggle }: { stage: StageExecution; expanded: boolean; onToggle: () => void }) {
  return (
    <div>
      <button
        onClick={onToggle}
        className="w-full flex items-center justify-between px-4 py-3 hover:bg-bg-hover transition-colors text-left"
      >
        <div className="flex items-center gap-3">
          {expanded ? <ChevronDown className="h-3.5 w-3.5 text-text-tertiary" /> : <ChevronRight className="h-3.5 w-3.5 text-text-tertiary" />}
          <StatusBadge status={stage.status} />
          <span className="text-sm text-text-primary">{stage.name}</span>
        </div>
        <span className="text-xs text-text-tertiary">
          {stage.duration ? formatDuration(stage.duration) : '-'}
        </span>
      </button>
      {expanded && stage.steps.length > 0 && (
        <div className="pl-10 pb-2 space-y-1">
          {stage.steps.map((step) => (
            <div key={step.id} className="flex items-center justify-between px-3 py-1.5 rounded-md text-xs">
              <div className="flex items-center gap-2">
                <span className={cn(
                  'h-1.5 w-1.5 rounded-full',
                  step.status === 'SUCCESS' ? 'bg-status-success' :
                  step.status === 'RUNNING' ? 'bg-status-running animate-pulse' :
                  step.status === 'FAILED' ? 'bg-status-failed' : 'bg-text-tertiary'
                )} />
                <span className="text-text-secondary">{step.name}</span>
              </div>
              <span className="text-text-tertiary">{step.duration ? formatDuration(step.duration) : '-'}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function LogViewer({ run, expandedStage }: { run: { stages: StageExecution[] }; expandedStage: string | null }) {
  const stage = expandedStage ? run.stages.find(s => s.id === expandedStage) : run.stages.find(s => s.status === 'RUNNING') || run.stages[run.stages.length - 1]

  const logLines = stage?.steps
    .filter(s => s.logs)
    .flatMap(s => s.logs!.split('\n').map((line, i) => ({ step: s.name, line, num: i + 1 }))) || []

  const defaultLogs = [
    { step: stage?.name || 'Pipeline', line: `[${stage?.name || 'Pipeline'}] Stage started`, num: 1 },
    { step: stage?.name || 'Pipeline', line: `[${stage?.name || 'Pipeline'}] Initializing environment...`, num: 2 },
    { step: stage?.name || 'Pipeline', line: `[${stage?.name || 'Pipeline'}] Pulling container image...`, num: 3 },
    { step: stage?.name || 'Pipeline', line: `[${stage?.name || 'Pipeline'}] Running commands...`, num: 4 },
    ...(stage?.status === 'SUCCESS' ? [{ step: stage.name, line: `[${stage.name}] Stage completed successfully`, num: 5 }] : []),
    ...(stage?.status === 'RUNNING' ? [{ step: stage.name, line: `[${stage.name}] Executing...`, num: 5 }] : []),
    ...(stage?.status === 'FAILED' ? [{ step: stage.name, line: `[${stage.name}] Stage failed with exit code 1`, num: 5 }] : []),
  ]

  const lines = logLines.length > 0 ? logLines : defaultLogs

  return (
    <div className="bg-bg-primary font-mono text-xs overflow-auto max-h-[500px]">
      {lines.map((entry, i) => (
        <div
          key={i}
          className={cn(
            'flex gap-4 px-4 py-0.5 log-line',
            entry.line.includes('error') || entry.line.includes('failed') || entry.line.includes('FAILED')
              ? 'text-status-failed'
              : entry.line.includes('success') || entry.line.includes('completed')
              ? 'text-status-success'
              : 'text-text-secondary'
          )}
        >
          <span className="text-text-tertiary select-none w-8 text-right flex-shrink-0">{entry.num}</span>
          <span className="whitespace-pre-wrap break-all">{entry.line}</span>
        </div>
      ))}
      {stage?.status === 'RUNNING' && (
        <div className="flex items-center gap-2 px-4 py-2 text-status-running">
          <span className="h-2 w-2 rounded-full bg-status-running animate-pulse" />
          <span>Waiting for output...</span>
        </div>
      )}
    </div>
  )
}

function InfoCard({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <CardContent className="py-3">
        <div className="text-[10px] text-text-tertiary uppercase tracking-wider">{label}</div>
        <div className="text-sm text-text-primary mt-1 font-medium">{value}</div>
      </CardContent>
    </Card>
  )
}
