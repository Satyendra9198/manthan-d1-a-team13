// Dev-only component gallery: http://localhost:5173/gallery.html
// Not part of the production build (vite only builds index.html).
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@/index.css'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { TextField } from '@/components/ui/text-field'

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-4">
      <h2 className="text-sm font-medium text-muted-foreground">{title}</h2>
      {children}
    </section>
  )
}

function Gallery() {
  return (
    <main className="mx-auto max-w-3xl space-y-12 px-4 py-12">
      <h1 className="text-2xl font-semibold">ReturnRight UI</h1>

      <Section title="Buttons">
        <div className="flex flex-wrap items-center gap-4">
          <Button>Button</Button>
          <Button variant="outline">Button</Button>
          <Button variant="destructive">Button</Button>
          <Button variant="ghost">Button</Button>
        </div>
        <div className="flex flex-wrap items-center gap-4">
          <Button size="sm">Small</Button>
          <Button size="lg">Large</Button>
          <Button disabled>Disabled</Button>
        </div>
      </Section>

      <Section title="Badges">
        <div className="flex flex-wrap items-center gap-3">
          <Badge>Badge</Badge>
          <Badge variant="info">Badge</Badge>
          <Badge variant="success">Badge</Badge>
          <Badge variant="warning">Badge</Badge>
          <Badge variant="danger">Badge</Badge>
        </div>
      </Section>

      <Section title="Input">
        <div className="max-w-sm space-y-6">
          <TextField label="Label" placeholder="Value" />
          <TextField label="With hint" placeholder="Value" hint="Helper text" />
          <TextField label="With error" defaultValue="bad" error="This field is required" />
        </div>
      </Section>
    </main>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Gallery />
  </StrictMode>,
)
