import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Alert, AlertDescription, AlertTitle } from './alert'
import { Badge } from './badge'
import { Button } from './button'
import { Checkbox } from './checkbox'
import { RadioGroup, RadioGroupItem } from './radio-group'
import { StepProgress } from './step-progress'
import { TextField } from './text-field'

describe('Button', () => {
  it('renders each variant and defaults to type="button"', () => {
    for (const variant of ['default', 'outline', 'destructive', 'ghost'] as const) {
      render(<Button variant={variant}>{variant}</Button>)
      const btn = screen.getByRole('button', { name: variant })
      expect(btn).toHaveAttribute('data-variant', variant)
      expect(btn).toHaveAttribute('type', 'button')
    }
  })

  it('applies the solid red style for destructive', () => {
    render(<Button variant="destructive">Delete</Button>)
    expect(screen.getByRole('button')).toHaveClass('bg-destructive', 'text-destructive-foreground')
  })

  it('handles clicks and respects disabled', async () => {
    const onClick = vi.fn()
    const user = userEvent.setup()
    const { rerender } = render(<Button onClick={onClick}>Go</Button>)
    await user.click(screen.getByRole('button'))
    expect(onClick).toHaveBeenCalledOnce()

    rerender(<Button onClick={onClick} disabled>Go</Button>)
    await user.click(screen.getByRole('button'))
    expect(onClick).toHaveBeenCalledOnce()
  })

  it('can render as a link via asChild', () => {
    render(<Button asChild><a href="/returns">Returns</a></Button>)
    expect(screen.getByRole('link', { name: 'Returns' })).toHaveAttribute('data-slot', 'button')
  })
})

describe('Badge', () => {
  it.each([
    ['default', 'bg-muted'],
    ['info', 'bg-info'],
    ['success', 'bg-success'],
    ['warning', 'bg-warning'],
    ['danger', 'bg-danger'],
  ] as const)('%s variant uses %s', (variant, cls) => {
    render(<Badge variant={variant}>{variant}</Badge>)
    expect(screen.getByText(variant)).toHaveClass(cls, 'rounded-full')
  })
})

describe('TextField', () => {
  it('associates the label with the input', async () => {
    const user = userEvent.setup()
    render(<TextField label="Order number" placeholder="Value" />)
    const input = screen.getByLabelText('Order number')
    expect(input).toHaveAttribute('placeholder', 'Value')
    await user.type(input, 'RR-1024')
    expect(input).toHaveValue('RR-1024')
  })

  it('shows a hint linked via aria-describedby', () => {
    render(<TextField label="Email" hint="We never share it" />)
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('We never share it')
  })

  it('marks the input invalid when an error is given', () => {
    render(<TextField label="Email" hint="ignored" error="Required" />)
    const input = screen.getByLabelText('Email')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('Required')
    expect(screen.queryByText('ignored')).not.toBeInTheDocument()
  })
})

describe('StepProgress', () => {
  it('captions the step and fills one bar per step reached', () => {
    const { container } = render(<StepProgress current={2} total={5} label="Second step" />)
    expect(screen.getByText('Step 2 of 5 · Second step')).toBeInTheDocument()
    expect(container.querySelectorAll('[data-slot="step-progress-segment"]')).toHaveLength(5)
    expect(container.querySelectorAll('[data-complete]')).toHaveLength(2)
  })

  it('leaves out the separator when there is no label', () => {
    render(<StepProgress current={3} total={5} />)
    expect(screen.getByText('Step 3 of 5')).toBeInTheDocument()
  })
})

describe('Checkbox', () => {
  it('toggles on click and reports the new value', async () => {
    const onCheckedChange = vi.fn()
    const user = userEvent.setup()
    render(<Checkbox aria-label="Linen shirt" onCheckedChange={onCheckedChange} />)

    const box = screen.getByRole('checkbox', { name: 'Linen shirt' })
    expect(box).not.toBeChecked()
    await user.click(box)
    expect(box).toBeChecked()
    expect(onCheckedChange).toHaveBeenCalledWith(true)
  })

  it('does nothing when disabled', async () => {
    const onCheckedChange = vi.fn()
    const user = userEvent.setup()
    render(<Checkbox aria-label="Earbuds" disabled onCheckedChange={onCheckedChange} />)
    await user.click(screen.getByRole('checkbox'))
    expect(onCheckedChange).not.toHaveBeenCalled()
  })
})

describe('RadioGroup', () => {
  // Arrow-key selection relies on Radix's async roving focus, which jsdom doesn't
  // reproduce; it's covered in the browser instead.
  it('selects one option at a time', async () => {
    const onValueChange = vi.fn()
    const user = userEvent.setup()
    render(
      <RadioGroup aria-label="Reason" onValueChange={onValueChange}>
        <label>
          <RadioGroupItem value="too_large" /> Too large
        </label>
        <label>
          <RadioGroupItem value="too_small" /> Too small
        </label>
      </RadioGroup>
    )

    await user.click(screen.getByText('Too large'))
    expect(screen.getByRole('radio', { name: 'Too large' })).toBeChecked()
    expect(onValueChange).toHaveBeenLastCalledWith('too_large')

    await user.click(screen.getByText('Too small'))
    expect(screen.getByRole('radio', { name: 'Too small' })).toBeChecked()
    expect(screen.getByRole('radio', { name: 'Too large' })).not.toBeChecked()
  })
})

describe('Alert', () => {
  it('is announced and uses the tone colours', () => {
    render(
      <Alert variant="danger">
        <AlertTitle>We couldn’t find that order</AlertTitle>
        <AlertDescription>Check the order number.</AlertDescription>
      </Alert>
    )
    const alert = screen.getByRole('alert')
    expect(alert).toHaveClass('bg-danger', 'text-danger-foreground')
    expect(alert).toHaveTextContent('We couldn’t find that orderCheck the order number.')
  })
})
