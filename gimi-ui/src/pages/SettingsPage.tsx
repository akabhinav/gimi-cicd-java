import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { Settings, User, Shield, Bell, Palette } from 'lucide-react'

export function SettingsPage() {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-text-primary">Settings</h2>
        <p className="text-sm text-text-tertiary mt-0.5">Platform configuration and preferences</p>
      </div>

      <Tabs defaultValue="general">
        <TabsList>
          <TabsTrigger value="general"><Settings className="h-3.5 w-3.5" /> General</TabsTrigger>
          <TabsTrigger value="account"><User className="h-3.5 w-3.5" /> Account</TabsTrigger>
          <TabsTrigger value="security"><Shield className="h-3.5 w-3.5" /> Security</TabsTrigger>
          <TabsTrigger value="notifications"><Bell className="h-3.5 w-3.5" /> Notifications</TabsTrigger>
        </TabsList>

        <TabsContent value="general">
          <Card>
            <CardHeader><CardTitle>General Settings</CardTitle></CardHeader>
            <CardContent className="space-y-4 max-w-lg">
              <div>
                <label className="text-xs font-medium text-text-secondary block mb-1.5">Project Name</label>
                <Input defaultValue="Default Project" />
              </div>
              <div>
                <label className="text-xs font-medium text-text-secondary block mb-1.5">Organization</label>
                <Input defaultValue="gimi-org" />
              </div>
              <div>
                <label className="text-xs font-medium text-text-secondary block mb-1.5">API Base URL</label>
                <Input defaultValue="https://api.gimi.dev" />
              </div>
              <Button size="sm">Save Changes</Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="account">
          <Card>
            <CardHeader><CardTitle>Account Settings</CardTitle></CardHeader>
            <CardContent className="space-y-4 max-w-lg">
              <div>
                <label className="text-xs font-medium text-text-secondary block mb-1.5">Email</label>
                <Input defaultValue="admin@gimi.dev" type="email" />
              </div>
              <div>
                <label className="text-xs font-medium text-text-secondary block mb-1.5">Display Name</label>
                <Input defaultValue="Admin User" />
              </div>
              <Button size="sm">Update Profile</Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="security">
          <Card>
            <CardHeader><CardTitle>Security Settings</CardTitle></CardHeader>
            <CardContent className="space-y-4 max-w-lg">
              <div className="flex items-center justify-between p-3 rounded-md bg-bg-secondary border border-border-primary">
                <div>
                  <div className="text-sm text-text-primary">Two-Factor Authentication</div>
                  <div className="text-xs text-text-tertiary mt-0.5">Add an extra layer of security</div>
                </div>
                <Button variant="outline" size="sm">Enable</Button>
              </div>
              <div className="flex items-center justify-between p-3 rounded-md bg-bg-secondary border border-border-primary">
                <div>
                  <div className="text-sm text-text-primary">SSO Configuration</div>
                  <div className="text-xs text-text-tertiary mt-0.5">Configure OIDC/SAML providers</div>
                </div>
                <Button variant="outline" size="sm">Configure</Button>
              </div>
              <div className="flex items-center justify-between p-3 rounded-md bg-bg-secondary border border-border-primary">
                <div>
                  <div className="text-sm text-text-primary">API Tokens</div>
                  <div className="text-xs text-text-tertiary mt-0.5">Manage personal access tokens</div>
                </div>
                <Button variant="outline" size="sm">Manage</Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="notifications">
          <Card>
            <CardHeader><CardTitle>Notification Preferences</CardTitle></CardHeader>
            <CardContent className="space-y-3">
              {['Pipeline Success', 'Pipeline Failure', 'Approval Required', 'Security Alerts', 'Worker Offline'].map((pref) => (
                <div key={pref} className="flex items-center justify-between p-3 rounded-md bg-bg-secondary border border-border-primary">
                  <span className="text-sm text-text-primary">{pref}</span>
                  <div className="flex items-center gap-4">
                    {['Email', 'Slack'].map(ch => (
                      <label key={ch} className="flex items-center gap-1.5 text-xs text-text-secondary">
                        <input type="checkbox" defaultChecked className="rounded border-border-primary" /> {ch}
                      </label>
                    ))}
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
