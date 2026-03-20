import { useMemo, useCallback } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  type Node,
  type Edge,
  Position,
  MarkerType,
  Handle,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import type { Stage } from '@/types'
import { cn } from '@/lib/utils'
import { formatDuration } from '@/lib/utils'
import {
  Hammer, TestTube, Rocket, UserCheck, Shield, Server, Wrench
} from 'lucide-react'

const stageIcons: Record<string, React.ComponentType<{ className?: string }>> = {
  BUILD: Hammer,
  TEST: TestTube,
  DEPLOY: Rocket,
  APPROVAL: UserCheck,
  SECURITY_SCAN: Shield,
  INFRASTRUCTURE: Server,
  CUSTOM: Wrench,
}

const statusColors: Record<string, string> = {
  SUCCESS: 'border-status-success bg-status-success/5',
  RUNNING: 'border-status-running bg-status-running/5',
  FAILED: 'border-status-failed bg-status-failed/5',
  QUEUED: 'border-status-queued bg-status-queued/5',
  PENDING: 'border-border-secondary bg-bg-tertiary',
}

const statusDotColors: Record<string, string> = {
  SUCCESS: 'bg-status-success',
  RUNNING: 'bg-status-running animate-pulse',
  FAILED: 'bg-status-failed',
  QUEUED: 'bg-status-queued',
  PENDING: 'bg-text-tertiary',
}

function StageNode({ data }: { data: { stage: Stage } }) {
  const stage = data.stage
  const Icon = stageIcons[stage.type] || Wrench
  const borderColor = statusColors[stage.status || 'PENDING'] || statusColors.PENDING
  const dotColor = statusDotColors[stage.status || 'PENDING'] || statusDotColors.PENDING

  return (
    <>
      <Handle type="target" position={Position.Left} className="!bg-border-secondary !border-border-primary !w-2 !h-2" />
      <div className={cn(
        'px-4 py-3 rounded-lg border-2 min-w-[180px] bg-bg-card transition-all',
        borderColor
      )}>
        <div className="flex items-center gap-2.5">
          <div className="p-1.5 rounded-md bg-bg-secondary">
            <Icon className="h-4 w-4 text-text-secondary" />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className={cn('h-2 w-2 rounded-full flex-shrink-0', dotColor)} />
              <span className="text-sm font-medium text-text-primary truncate">{stage.name}</span>
            </div>
            <div className="text-[10px] text-text-tertiary mt-0.5">
              {stage.type} {stage.duration ? `· ${formatDuration(stage.duration)}` : ''}
            </div>
          </div>
        </div>
        {stage.steps.length > 0 && (
          <div className="mt-2 pt-2 border-t border-border-primary">
            <div className="text-[10px] text-text-tertiary">{stage.steps.length} step{stage.steps.length > 1 ? 's' : ''}</div>
          </div>
        )}
        {stage.strategy && (
          <div className="mt-1">
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-primary-500/10 text-primary-400">
              {stage.strategy.type}
            </span>
          </div>
        )}
      </div>
      <Handle type="source" position={Position.Right} className="!bg-border-secondary !border-border-primary !w-2 !h-2" />
    </>
  )
}

const nodeTypes = { stage: StageNode }

export function PipelineGraph({ stages }: { stages: Stage[] }) {
  const { nodes, edges } = useMemo(() => {
    const levelMap = new Map<string, number>()
    const placed = new Set<string>()

    function getLevel(stageId: string): number {
      if (levelMap.has(stageId)) return levelMap.get(stageId)!
      const stage = stages.find(s => s.id === stageId)
      if (!stage || !stage.dependsOn?.length) {
        levelMap.set(stageId, 0)
        return 0
      }
      const maxParentLevel = Math.max(...stage.dependsOn.map(d => getLevel(d)))
      const level = maxParentLevel + 1
      levelMap.set(stageId, level)
      return level
    }

    stages.forEach(s => getLevel(s.id))

    const levelGroups = new Map<number, Stage[]>()
    stages.forEach(stage => {
      const level = levelMap.get(stage.id)!
      if (!levelGroups.has(level)) levelGroups.set(level, [])
      levelGroups.get(level)!.push(stage)
    })

    const nodeList: Node[] = []
    const edgeList: Edge[] = []

    levelGroups.forEach((group, level) => {
      const totalHeight = group.length * 100
      const startY = -(totalHeight - 100) / 2

      group.forEach((stage, idx) => {
        nodeList.push({
          id: stage.id,
          type: 'stage',
          position: { x: level * 260, y: startY + idx * 100 },
          data: { stage },
        })

        stage.dependsOn?.forEach(dep => {
          edgeList.push({
            id: `${dep}-${stage.id}`,
            source: dep,
            target: stage.id,
            type: 'smoothstep',
            markerEnd: { type: MarkerType.ArrowClosed, color: '#373e47' },
            style: { stroke: '#373e47', strokeWidth: 2 },
            animated: stage.status === 'RUNNING',
          })
        })

        if (!stage.dependsOn?.length && level === 0) {
          placed.add(stage.id)
        }
      })
    })

    // Connect first-level stages that have no dependencies
    const firstLevelStages = stages.filter(s => !s.dependsOn?.length)
    if (firstLevelStages.length > 0 && !stages.some(s => s.dependsOn?.length)) {
      for (let i = 0; i < firstLevelStages.length - 1; i++) {
        edgeList.push({
          id: `seq-${firstLevelStages[i].id}-${firstLevelStages[i + 1].id}`,
          source: firstLevelStages[i].id,
          target: firstLevelStages[i + 1].id,
          type: 'smoothstep',
          markerEnd: { type: MarkerType.ArrowClosed, color: '#373e47' },
          style: { stroke: '#373e47', strokeWidth: 2 },
        })
      }
    }

    return { nodes: nodeList, edges: edgeList }
  }, [stages])

  return (
    <div className="h-[350px] rounded-md bg-bg-primary border border-border-primary">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.3 }}
        proOptions={{ hideAttribution: true }}
        nodesDraggable={false}
        nodesConnectable={false}
        minZoom={0.5}
        maxZoom={1.5}
      >
        <Background color="#2d333b" gap={20} size={1} />
        <Controls
          showInteractive={false}
          className="!bg-bg-secondary !border-border-primary !rounded-lg [&>button]:!bg-bg-secondary [&>button]:!border-border-primary [&>button]:!text-text-secondary [&>button:hover]:!bg-bg-hover"
        />
      </ReactFlow>
    </div>
  )
}
