import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AppLayout } from '@/components/layout/AppLayout'
import { DashboardPage } from '@/pages/DashboardPage'
import { PipelinesPage } from '@/pages/PipelinesPage'
import { PipelineDetailPage } from '@/pages/PipelineDetailPage'
import { ExecutionsPage } from '@/pages/ExecutionsPage'
import { ExecutionDetailPage } from '@/pages/ExecutionDetailPage'
import { WorkersPage } from '@/pages/WorkersPage'
import { AnalyticsPage } from '@/pages/AnalyticsPage'
import { SLOsPage } from '@/pages/SLOsPage'
import { SecurityPage } from '@/pages/SecurityPage'
import { FeatureFlagsPage } from '@/pages/FeatureFlagsPage'
import { AuditPage } from '@/pages/AuditPage'
import { ConnectorsPage } from '@/pages/ConnectorsPage'
import { TemplatesPage } from '@/pages/TemplatesPage'
import { NotificationsPage } from '@/pages/NotificationsPage'
import { GovernancePage } from '@/pages/GovernancePage'
import { SettingsPage } from '@/pages/SettingsPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/pipelines" element={<PipelinesPage />} />
            <Route path="/pipelines/:id" element={<PipelineDetailPage />} />
            <Route path="/executions" element={<ExecutionsPage />} />
            <Route path="/executions/:id" element={<ExecutionDetailPage />} />
            <Route path="/workers" element={<WorkersPage />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
            <Route path="/slos" element={<SLOsPage />} />
            <Route path="/security" element={<SecurityPage />} />
            <Route path="/feature-flags" element={<FeatureFlagsPage />} />
            <Route path="/audit" element={<AuditPage />} />
            <Route path="/connectors" element={<ConnectorsPage />} />
            <Route path="/templates" element={<TemplatesPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/governance" element={<GovernancePage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
