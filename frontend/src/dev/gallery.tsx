// Dev-only component gallery: http://localhost:5173/gallery.html
// Not part of the production build (vite only builds index.html).
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@/index.css'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Textarea } from '@/components/ui/textarea'
import { StepProgress } from '@/components/ui/step-progress'
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

      <Section title="Checkbox">
        <div className="flex flex-wrap items-center gap-6">
          <Checkbox aria-label="Unchecked" />
          <Checkbox aria-label="Checked" defaultChecked />
          <Checkbox aria-label="Disabled" disabled />
          <Checkbox aria-label="Disabled checked" disabled defaultChecked />
        </div>
      </Section>

      <Section title="Radio group">
        <RadioGroup aria-label="Example" defaultValue="one" className="max-w-sm">
          <label className="flex items-center gap-3 text-[15px]">
            <RadioGroupItem value="one" /> Selected
          </label>
          <label className="flex items-center gap-3 text-[15px]">
            <RadioGroupItem value="two" /> Not selected
          </label>
          <label className="flex items-center gap-3 text-[15px]">
            <RadioGroupItem value="three" disabled /> Disabled
          </label>
        </RadioGroup>
      </Section>

      <Section title="Textarea">
        <div className="max-w-sm">
          <Textarea aria-label="Example" placeholder="Tell us more" />
        </div>
      </Section>

      <Section title="Step progress">
        <div className="max-w-sm space-y-6">
          <StepProgress current={1} total={5} label="Find your order" />
          <StepProgress current={3} total={5} />
        </div>
      </Section>

      <Section title="Alerts">
        <div className="max-w-sm space-y-3">
          <Alert variant="danger">
            <AlertTitle>We couldn’t find that order</AlertTitle>
            <AlertDescription>
              Check the order number and use the same email as on the order.
            </AlertDescription>
          </Alert>
          <Alert variant="info" role="status">
            <AlertTitle>Info</AlertTitle>
            <AlertDescription>Informational message.</AlertDescription>
          </Alert>
          <Alert variant="success" role="status">
            <AlertTitle>Success</AlertTitle>
            <AlertDescription>Everything went fine.</AlertDescription>
          </Alert>
          <Alert variant="warning" role="status">
            <AlertTitle>Warning</AlertTitle>
            <AlertDescription>Needs attention.</AlertDescription>
          </Alert>
        </div>
      </Section>

      <Section title="Card">
        <Card className="max-w-sm">
          <p className="font-semibold">Card title</p>
          <p className="text-sm text-muted-foreground">Content goes here.</p>
        </Card>
      </Section>
    </main>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Gallery />
  </StrictMode>,
)
