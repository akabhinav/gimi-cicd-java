import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Plus, Cloud, Users, Zap, DollarSign, Activity, Server, Globe, CheckCircle2, AlertTriangle, XCircle } from 'lucide-react'
import { cn } from '@/lib/utils'

const tenants = [
  { id: 1, name: 'Acme Corp', email: 'admin@acme.com', plan: 'ENTERPRISE', region: 'us-east-1', status: 'ACTIVE', users: 48, builds: 8420, concurrent: 12, monthlyCost: '$499.00', health: 'HEALTHY' },
  { id: 2, name: 'StartupXYZ', email: 'dev@startupxyz.com', plan: 'PROFESSIONAL', region: 'eu-west-1', status: 'ACTIVE', users: 18, builds: 3240, concurrent: 5, monthlyCost: '$99.00', health: 'HEALTHY' },
  { id: 3, name: 'DevTeam Alpha', email: 'lead@devteam.io', plan: 'TEAM', region: 'us-west-2', status: 'ACTIVE', users: 4, builds: 812, concurrent: 2, monthlyCost: '$29.00', health: 'HEALTHY' },
  { id: 4, name: 'Solo Dev', email: 'solo@dev.com', plan: 'FREE', region: 'us-east-1', status: 'TRIAL', users: 1, builds: 67, concurrent: 1, monthlyCost: '$0.00', health: 'HEALTHY' },
  { id: 5, name: 'BigBank Inc', email: 'cicd@bigbank.com', plan: 'ENTERPRISE', region: 'eu-central-1', status: 'ACTIVE', users: 120, builds: 24100, concurrent: 22, monthlyCost: '$499.00', health: 'DEGRADED' },
  { id: 6, name: 'OldCo Legacy', email: 'admin@oldco.com', plan: 'TEAM', region: 'ap-south-1', status: 'SUSPENDED', users: 3, builds: 0, concurrent: 0, monthlyCost: '$29.00', health: 'OUTAGE' },
]

const plans = [
  { name: 'Free', price: '$0', users: '1', builds: '100/mo', concurrent: 1, features: ['5 pipelines', '7-day logs', 'Community support'] },
  { name: 'Team', price: '$29', users: '5', builds: '1,000/mo', concurrent: 3, features: ['25 pipelines', '30-day logs', 'Audit logs'] },
  { name: 'Professional', price: '$99', users: '25', builds: '10,000/mo', concurrent: 10, features: ['100 pipelines', 'SSO', 'Custom domains', 'Dedicated workers'] },
  { name: 'Enterprise', price: '$499', users: 'Unlimited', builds: 'Unlimited', concurrent: 50, features: ['Unlimited pipelines', 'Priority support', 'Dedicated infra', 'SLA 99.99%'] },
]

const regions = [
  { name: 'US East (Virginia)', provider: 'AWS', code: 'us-east-1', status: 'operational', tenants: 2 },
  { name: 'US West (Oregon)', provider: 'AWS', code: 'us-west-2', status: 'operational', tenants: 1 },
  { name: 'EU West (Ireland)', provider: 'AWS', code: 'eu-west-1', status: 'operational', tenants: 1 },
  { name: 'EU Central (Frankfurt)', provider: 'AWS', code: 'eu-central-1', status: 'operational', tenants: 1 },
  { name: 'AP South (Mumbai)', provider: 'AWS', code: 'ap-south-1', status: 'degraded', tenants: 1 },
  { name: 'US Central (Iowa)', provider: 'GCP', code: 'us-central1', status: 'operational', tenants: 0 },
]

const planColor: Record<string, string> = {
  FREE: 'text-text-tertiary border-border-default',
  TEAM: 'text-primary-400 border-primary-400/30',
  PROFESSIONAL: 'text-[#f0883e] border-[#f0883e]/30',
  ENTERPRISE: 'text-[#a371f7] border-[#a371f7]/30',
}

const statusColor: Record<string, string> = {
  ACTIVE: 'text-status-success',
  TRIAL: 'text-warning',
  SUSPENDED: 'text-status-failed',
  DEACTIVATED: 'text-text-tertiary',
}

export function SaasHostingPage() {
  const totalRevenue = tenants.reduce((sum, t) => sum + parseFloat(t.monthlyCost.replace('$', '').replace(',', '')), 0)
  const totalBuilds = tenants.reduce((sum, t) => sum + t.builds, 0)
  const totalUsers = tenants.reduce((sum, t) => sum + t.users, 0)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-text-primary">SaaS Hosting</h2>
          <p className="text-sm text-text-tertiary mt-0.5">Multi-tenant cloud platform management, billing, and monitoring</p>
        </div>
        <Button size="sm"><Plus className="h-3.5 w-3.5" /> Provision Tenant</Button>
      </div>

      {/* Platform Summary */}
      <div className="grid grid-cols-5 gap-4">
        <Card>
          <CardContent className="py-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-primary-500/10"><Cloud className="h-4 w-4 text-primary-400" /></div>
              <div>
                <div className="text-2xl font-bold text-text-primary">{tenants.length}</div>
                <div className="text-xs text-text-tertiary">Tenants</div>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-status-success/10"><Users className="h-4 w-4 text-status-success" /></div>
              <div>
                <div className="text-2xl font-bold text-text-primary">{totalUsers}</div>
                <div className="text-xs text-text-tertiary">Total Users</div>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-[#f0883e]/10"><Zap className="h-4 w-4 text-[#f0883e]" /></div>
              <div>
                <div className="text-2xl font-bold text-text-primary">{totalBuilds.toLocaleString()}</div>
                <div className="text-xs text-text-tertiary">Builds This Month</div>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-[#a371f7]/10"><DollarSign className="h-4 w-4 text-[#a371f7]" /></div>
              <div>
                <div className="text-2xl font-bold text-text-primary">${totalRevenue.toFixed(0)}</div>
                <div className="text-xs text-text-tertiary">MRR</div>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-4">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-status-success/10"><Activity className="h-4 w-4 text-status-success" /></div>
              <div>
                <div className="text-2xl font-bold text-text-primary">99.97%</div>
                <div className="text-xs text-text-tertiary">Uptime</div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Tenants Table */}
      <Card>
        <CardContent className="p-0">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border-default text-text-tertiary">
                <th className="text-left py-3 px-4 font-medium">Tenant</th>
                <th className="text-left py-3 px-4 font-medium">Plan</th>
                <th className="text-left py-3 px-4 font-medium">Region</th>
                <th className="text-left py-3 px-4 font-medium">Status</th>
                <th className="text-center py-3 px-4 font-medium">Users</th>
                <th className="text-center py-3 px-4 font-medium">Builds</th>
                <th className="text-center py-3 px-4 font-medium">Health</th>
                <th className="text-right py-3 px-4 font-medium">MRR</th>
              </tr>
            </thead>
            <tbody>
              {tenants.map((t) => (
                <tr key={t.id} className="border-b border-border-default last:border-0 hover:bg-bg-hover">
                  <td className="py-3 px-4">
                    <div className="font-medium text-text-primary">{t.name}</div>
                    <div className="text-xs text-text-tertiary">{t.email}</div>
                  </td>
                  <td className="py-3 px-4">
                    <Badge variant="outline" className={cn('text-[10px]', planColor[t.plan])}>{t.plan}</Badge>
                  </td>
                  <td className="py-3 px-4">
                    <span className="text-xs text-text-secondary flex items-center gap-1">
                      <Globe className="h-3 w-3" /> {t.region}
                    </span>
                  </td>
                  <td className="py-3 px-4">
                    <span className={cn('text-xs font-medium', statusColor[t.status])}>{t.status}</span>
                  </td>
                  <td className="py-3 px-4 text-center text-text-secondary">{t.users}</td>
                  <td className="py-3 px-4 text-center text-text-secondary">{t.builds.toLocaleString()}</td>
                  <td className="py-3 px-4 text-center">
                    {t.health === 'HEALTHY' ? (
                      <CheckCircle2 className="h-4 w-4 text-status-success mx-auto" />
                    ) : t.health === 'DEGRADED' ? (
                      <AlertTriangle className="h-4 w-4 text-warning mx-auto" />
                    ) : (
                      <XCircle className="h-4 w-4 text-status-failed mx-auto" />
                    )}
                  </td>
                  <td className="py-3 px-4 text-right font-mono text-xs text-text-secondary">{t.monthlyCost}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>

      {/* Regions Status */}
      <div>
        <h3 className="text-sm font-medium text-text-primary mb-3">Region Status</h3>
        <div className="grid grid-cols-3 gap-3">
          {regions.map((region) => (
            <Card key={region.code}>
              <CardContent className="py-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Server className="h-4 w-4 text-text-tertiary" />
                    <div>
                      <div className="text-sm font-medium text-text-primary">{region.name}</div>
                      <div className="text-[10px] text-text-tertiary">{region.provider} &middot; {region.code}</div>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-text-tertiary">{region.tenants} tenants</span>
                    <div className={cn('h-2 w-2 rounded-full',
                      region.status === 'operational' ? 'bg-status-success' : 'bg-warning'
                    )} />
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>

      {/* Plans */}
      <div>
        <h3 className="text-sm font-medium text-text-primary mb-3">Available Plans</h3>
        <div className="grid grid-cols-4 gap-3">
          {plans.map((plan) => (
            <Card key={plan.name}>
              <CardContent className="py-4">
                <div className="text-center mb-3">
                  <div className="text-lg font-bold text-text-primary">{plan.price}</div>
                  <div className="text-xs text-text-tertiary">/month</div>
                  <div className="text-sm font-medium text-primary-400 mt-1">{plan.name}</div>
                </div>
                <div className="space-y-1.5 text-xs text-text-secondary">
                  <div className="flex justify-between"><span>Users</span><span className="font-medium">{plan.users}</span></div>
                  <div className="flex justify-between"><span>Builds</span><span className="font-medium">{plan.builds}</span></div>
                  <div className="flex justify-between"><span>Concurrent</span><span className="font-medium">{plan.concurrent}</span></div>
                </div>
                <div className="mt-3 pt-3 border-t border-border-default space-y-1">
                  {plan.features.map((f) => (
                    <div key={f} className="flex items-center gap-1 text-[10px] text-text-tertiary">
                      <CheckCircle2 className="h-2.5 w-2.5 text-status-success" /> {f}
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  )
}
