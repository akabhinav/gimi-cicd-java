import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'

interface AppLayoutProps {
  onLogout?: () => void
}

export function AppLayout({ onLogout }: AppLayoutProps) {
  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <div className="flex flex-col flex-1 overflow-hidden">
        <TopBar onLogout={onLogout} />
        <main className="flex-1 overflow-auto bg-bg-primary p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
