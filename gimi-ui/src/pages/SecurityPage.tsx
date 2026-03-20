import { useSecurityScans } from '@/api/queries'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge, StatusBadge } from '@/components/ui/badge'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { Table, TableHeader, TableHead, TableBody, TableRow, TableCell } from '@/components/ui/table'
import { Shield, AlertTriangle, AlertCircle, Info, ChevronDown } from 'lucide-react'
import { cn } from '@/lib/utils'

const severityConfig: Record<string, { color: string; icon: React.ComponentType<{ className?: string }> }> = {
  CRITICAL: { color: 'text-status-failed bg-status-failed/10 border-status-failed/30', icon: AlertCircle },
  HIGH: { color: 'text-[#f0883e] bg-[#f0883e]/10 border-[#f0883e]/30', icon: AlertTriangle },
  MEDIUM: { color: 'text-warning bg-warning/10 border-warning/30', icon: AlertTriangle },
  LOW: { color: 'text-primary-400 bg-primary-500/10 border-primary-500/30', icon: Info },
  INFO: { color: 'text-text-tertiary bg-bg-hover border-border-primary', icon: Info },
}

export function SecurityPage() {
  const { data: scans } = useSecurityScans()

  const totalFindings = scans?.reduce((sum, s) => sum + s.findings.length, 0) || 0
  const criticalFindings = scans?.reduce((sum, s) => sum + s.summary.critical, 0) || 0
  const highFindings = scans?.reduce((sum, s) => sum + s.summary.high, 0) || 0

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-text-primary">Security Tests</h2>
        <p className="text-sm text-text-tertiary mt-0.5">SAST, DAST, and SCA scan results</p>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <StatCard label="Total Scans" value={scans?.length || 0} color="text-primary-400" />
        <StatCard label="Total Findings" value={totalFindings} color="text-warning" />
        <StatCard label="Critical" value={criticalFindings} color="text-status-failed" />
        <StatCard label="High" value={highFindings} color="text-[#f0883e]" />
      </div>

      <Tabs defaultValue="all">
        <TabsList>
          <TabsTrigger value="all">All Scans</TabsTrigger>
          <TabsTrigger value="sast">SAST</TabsTrigger>
          <TabsTrigger value="dast">DAST</TabsTrigger>
          <TabsTrigger value="sca">SCA</TabsTrigger>
        </TabsList>

        <TabsContent value="all">
          <div className="space-y-4">
            {scans?.map((scan) => (
              <Card key={scan.id}>
                <CardHeader>
                  <div className="flex items-center gap-3">
                    <Shield className="h-4 w-4 text-primary-400" />
                    <CardTitle>{scan.type} Scan</CardTitle>
                    <StatusBadge status={scan.status} />
                  </div>
                  <div className="flex items-center gap-2">
                    {Object.entries(scan.summary).map(([sev, count]) => (
                      count > 0 && (
                        <span key={sev} className={cn('px-2 py-0.5 rounded text-xs font-medium border', severityConfig[sev.toUpperCase()]?.color)}>
                          {count} {sev}
                        </span>
                      )
                    ))}
                  </div>
                </CardHeader>
                {scan.findings.length > 0 && (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Severity</TableHead>
                        <TableHead>Finding</TableHead>
                        <TableHead>File</TableHead>
                        <TableHead>CWE</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {scan.findings.map((finding) => {
                        const config = severityConfig[finding.severity]
                        return (
                          <TableRow key={finding.id}>
                            <TableCell>
                              <span className={cn('inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium border', config?.color)}>
                                {finding.severity}
                              </span>
                            </TableCell>
                            <TableCell>
                              <div className="font-medium text-text-primary text-sm">{finding.title}</div>
                              <div className="text-xs text-text-tertiary mt-0.5">{finding.description}</div>
                              {finding.recommendation && (
                                <div className="text-xs text-primary-400 mt-0.5">{finding.recommendation}</div>
                              )}
                            </TableCell>
                            <TableCell className="font-mono text-xs text-text-tertiary">
                              {finding.file && `${finding.file}${finding.line ? `:${finding.line}` : ''}`}
                            </TableCell>
                            <TableCell className="text-xs text-text-tertiary">{finding.cwe}</TableCell>
                          </TableRow>
                        )
                      })}
                    </TableBody>
                  </Table>
                )}
              </Card>
            ))}
          </div>
        </TabsContent>

        {['sast', 'dast', 'sca'].map(type => (
          <TabsContent key={type} value={type}>
            {scans?.filter(s => s.type.toLowerCase() === type).length === 0 ? (
              <Card><CardContent className="py-8 text-center text-sm text-text-tertiary">No {type.toUpperCase()} scans found</CardContent></Card>
            ) : (
              scans?.filter(s => s.type.toLowerCase() === type).map(scan => (
                <Card key={scan.id}><CardContent className="py-4"><pre className="text-xs text-text-secondary">{JSON.stringify(scan.summary, null, 2)}</pre></CardContent></Card>
              ))
            )}
          </TabsContent>
        ))}
      </Tabs>
    </div>
  )
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <Card>
      <CardContent className="py-4">
        <div className={cn('text-2xl font-bold', color)}>{value}</div>
        <div className="text-xs text-text-tertiary mt-0.5">{label}</div>
      </CardContent>
    </Card>
  )
}
