import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('<App />', () => {
  it('opens on the customer return wizard', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Start a return' })).toBeInTheDocument()
  })
})
