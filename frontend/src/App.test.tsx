import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from './App'
import { STORAGE_KEY } from './hooks/use-tasks'

describe('<App />', () => {
  it('shows the empty state', () => {
    render(<App />)
    expect(screen.getByText(/no tasks yet/i)).toBeInTheDocument()
  })

  it('adds, completes, and deletes a task', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('New task'), 'Write tests{Enter}')
    expect(screen.getByText('Write tests')).toBeInTheDocument()
    expect(screen.getByTestId('active-count')).toHaveTextContent('1 active')

    await user.click(screen.getByRole('checkbox'))
    expect(screen.getByRole('checkbox')).toBeChecked()
    expect(screen.getByTestId('active-count')).toHaveTextContent('0 active')

    await user.click(screen.getByRole('button', { name: /delete "write tests"/i }))
    expect(screen.queryByText('Write tests')).not.toBeInTheDocument()
  })

  it('disables Add for blank input', () => {
    render(<App />)
    expect(screen.getByRole('button', { name: /add/i })).toBeDisabled()
  })

  it('persists tasks to localStorage', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.type(screen.getByLabelText('New task'), 'Persist me{Enter}')
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
    expect(stored[0].title).toBe('Persist me')
  })
})
