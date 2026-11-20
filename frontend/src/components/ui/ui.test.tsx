import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Badge } from './badge'
import { Button } from './button'
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
