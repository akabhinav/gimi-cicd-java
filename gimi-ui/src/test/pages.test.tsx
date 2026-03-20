import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { DashboardPage } from '@/pages/DashboardPage'
import { PipelinesPage } from '@/pages/PipelinesPage'
import { ExecutionsPage } from '@/pages/ExecutionsPage'
import { WorkersPage } from '@/pages/WorkersPage'
import { AnalyticsPage } from '@/pages/AnalyticsPage'
import { SecurityPage } from '@/pages/SecurityPage'
import { FeatureFlagsPage } from '@/pages/FeatureFlagsPage'
import { SLOsPage } from '@/pages/SLOsPage'
import { AuditPage } from '@/pages/AuditPage'
import { ConnectorsPage } from '@/pages/ConnectorsPage'
import { TemplatesPage } from '@/pages/TemplatesPage'
import { SettingsPage } from '@/pages/SettingsPage'
import { GovernancePage } from '@/pages/GovernancePage'
import { NotificationsPage } from '@/pages/NotificationsPage'

function Wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <QueryClientProvider client={qc}>
      <BrowserRouter>{children}</BrowserRouter>
    </QueryClientProvider>
  )
}

describe('DashboardPage', () => {
  it('renders stat cards', () => {
    render(<Wrapper><DashboardPage /></Wrapper>)
    expect(screen.getByText('Total Pipelines')).toBeInTheDocument()
    expect(screen.getByText('Active Runs')).toBeInTheDocument()
    expect(screen.getByText('Success Rate')).toBeInTheDocument()
    expect(screen.getByText('Active Delegates')).toBeInTheDocument()
  })

  it('renders DORA performance section', () => {
    render(<Wrapper><DashboardPage /></Wrapper>)
    expect(screen.getByText('DORA Performance')).toBeInTheDocument()
  })

  it('renders recent executions', () => {
    render(<Wrapper><DashboardPage /></Wrapper>)
    expect(screen.getByText('Recent Executions')).toBeInTheDocument()
  })
})

describe('PipelinesPage', () => {
  it('renders pipeline list header', async () => {
    render(<Wrapper><PipelinesPage /></Wrapper>)
    expect(await screen.findByText('Pipelines')).toBeInTheDocument()
    expect(await screen.findByText('New Pipeline')).toBeInTheDocument()
  })

  it('renders pipeline cards', async () => {
    render(<Wrapper><PipelinesPage /></Wrapper>)
    expect(await screen.findByText('Backend CI Pipeline')).toBeInTheDocument()
    expect(await screen.findByText('Frontend Deploy')).toBeInTheDocument()
  })
})

describe('ExecutionsPage', () => {
  it('renders executions table', () => {
    render(<Wrapper><ExecutionsPage /></Wrapper>)
    expect(screen.getByText('Pipeline Executions')).toBeInTheDocument()
  })

  it('renders filter buttons', () => {
    render(<Wrapper><ExecutionsPage /></Wrapper>)
    expect(screen.getByText('All')).toBeInTheDocument()
    expect(screen.getByText('RUNNING')).toBeInTheDocument()
    expect(screen.getByText('SUCCESS')).toBeInTheDocument()
  })
})

describe('WorkersPage', () => {
  it('renders delegates page', () => {
    render(<Wrapper><WorkersPage /></Wrapper>)
    expect(screen.getByText('Delegates')).toBeInTheDocument()
    expect(screen.getByText('New Delegate')).toBeInTheDocument()
  })

  it('renders worker stats', () => {
    render(<Wrapper><WorkersPage /></Wrapper>)
    expect(screen.getByText('Active Delegates')).toBeInTheDocument()
    expect(screen.getByText('Utilization')).toBeInTheDocument()
  })
})

describe('AnalyticsPage', () => {
  it('renders DORA metrics', async () => {
    render(<Wrapper><AnalyticsPage /></Wrapper>)
    expect(await screen.findByText('DORA Metrics')).toBeInTheDocument()
    expect(await screen.findByText('Deployment Frequency')).toBeInTheDocument()
    expect(await screen.findByText('Lead Time for Changes')).toBeInTheDocument()
    expect(await screen.findByText('Change Failure Rate')).toBeInTheDocument()
    expect(await screen.findByText('Mean Time to Recovery')).toBeInTheDocument()
  })
})

describe('SecurityPage', () => {
  it('renders security tests page', () => {
    render(<Wrapper><SecurityPage /></Wrapper>)
    expect(screen.getByText('Security Tests')).toBeInTheDocument()
  })
})

describe('FeatureFlagsPage', () => {
  it('renders feature flags', async () => {
    render(<Wrapper><FeatureFlagsPage /></Wrapper>)
    expect(await screen.findByText('Feature Flags')).toBeInTheDocument()
    expect(await screen.findByText('New Payment Flow')).toBeInTheDocument()
  })
})

describe('SLOsPage', () => {
  it('renders SLOs', async () => {
    render(<Wrapper><SLOsPage /></Wrapper>)
    expect(await screen.findByText('Service Level Objectives')).toBeInTheDocument()
    expect(await screen.findByText('API Availability')).toBeInTheDocument()
  })
})

describe('AuditPage', () => {
  it('renders audit trail', () => {
    render(<Wrapper><AuditPage /></Wrapper>)
    expect(screen.getByText('Audit Trail')).toBeInTheDocument()
  })
})

describe('ConnectorsPage', () => {
  it('renders connectors', () => {
    render(<Wrapper><ConnectorsPage /></Wrapper>)
    expect(screen.getByText('Connectors')).toBeInTheDocument()
    expect(screen.getByText('New Connector')).toBeInTheDocument()
  })
})

describe('TemplatesPage', () => {
  it('renders templates', async () => {
    render(<Wrapper><TemplatesPage /></Wrapper>)
    expect(await screen.findByText('Templates')).toBeInTheDocument()
    expect(await screen.findByText('Basic CI')).toBeInTheDocument()
  })
})

describe('SettingsPage', () => {
  it('renders settings tabs', () => {
    render(<Wrapper><SettingsPage /></Wrapper>)
    expect(screen.getByText('Settings')).toBeInTheDocument()
    expect(screen.getByText('General')).toBeInTheDocument()
    expect(screen.getByText('Account')).toBeInTheDocument()
    expect(screen.getByText('Security')).toBeInTheDocument()
  })
})

describe('GovernancePage', () => {
  it('renders governance policies', () => {
    render(<Wrapper><GovernancePage /></Wrapper>)
    expect(screen.getByText('Governance & Policies')).toBeInTheDocument()
    expect(screen.getByText('Require Security Scan')).toBeInTheDocument()
  })
})

describe('NotificationsPage', () => {
  it('renders notification channels', () => {
    render(<Wrapper><NotificationsPage /></Wrapper>)
    expect(screen.getByText('Notification Channels')).toBeInTheDocument()
    expect(screen.getByText('Add Channel')).toBeInTheDocument()
  })
})
