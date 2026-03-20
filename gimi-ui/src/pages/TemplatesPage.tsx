import { useTemplates } from '@/api/queries'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Plus, BookTemplate, FileCode } from 'lucide-react'
import { formatDate } from '@/lib/utils'

export function TemplatesPage() {
  const { data: templates } = useTemplates()

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-text-primary">Templates</h2>
          <p className="text-sm text-text-tertiary mt-0.5">Reusable pipeline and stage templates</p>
        </div>
        <Button size="sm"><Plus className="h-3.5 w-3.5" /> New Template</Button>
      </div>

      <div className="grid grid-cols-3 gap-4">
        {templates?.map((template) => (
          <Card key={template.id} hoverable>
            <CardContent className="py-4 space-y-3">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-primary-500/10">
                    {template.type === 'PIPELINE' ? <BookTemplate className="h-4 w-4 text-primary-400" /> : <FileCode className="h-4 w-4 text-primary-400" />}
                  </div>
                  <div>
                    <div className="font-medium text-text-primary">{template.name}</div>
                    <div className="text-xs text-text-tertiary mt-0.5">{template.description}</div>
                  </div>
                </div>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5">
                  <Badge variant="outline" className="text-[10px]">{template.type}</Badge>
                  <Badge variant="outline" className="text-[10px]">v{template.version}</Badge>
                </div>
              </div>
              <div className="flex flex-wrap gap-1">
                {template.tags?.map(tag => (
                  <span key={tag} className="text-[10px] px-1.5 py-0.5 rounded bg-bg-hover text-text-tertiary">{tag}</span>
                ))}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
