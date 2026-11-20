import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeAll, describe, expect, it, vi } from 'vitest'
import { CustomerWizard } from './customer-wizard'

beforeAll(() => {
  // jsdom has no object URLs; the photo previews need them.
  URL.createObjectURL = vi.fn(() => 'blob:preview')
  URL.revokeObjectURL = vi.fn()
})

const photo = (name: string, type = 'image/jpeg') => new File(['photo'], name, { type })

function setup(options?: Parameters<typeof userEvent.setup>[0]) {
  const user = userEvent.setup(options)
  render(<CustomerWizard />)

  const orderNumber = () => screen.getByLabelText('Order number')
  const email = () => screen.getByLabelText('Email')
  const submit = () => user.click(screen.getByRole('button', { name: 'Find my order' }))
  const item = (name: string) => screen.getByRole('checkbox', { name })
  const continueButton = () => screen.getByRole('button', { name: 'Continue' })
  const backButton = () => screen.getByRole('button', { name: 'Back' })
  // The dropzone's label says "You’ve added 3 photos" once it's full.
  const photoInput = () => screen.getByLabelText(/take a photo or upload|you’ve added 3 photos/i)
  const reason = (name: string) => screen.getByRole('radio', { name })

  /** Fill step 1 and wait for step 2. */
  async function findOrder(number = 'RR-10482', address = 'ananya.rao@example.com') {
    await user.clear(orderNumber())
    await user.type(orderNumber(), number)
    await user.clear(email())
    await user.type(email(), address)
    await submit()
    await screen.findByText('Step 2 of 5 · Choose items')
  }

  /** Get to step 3 with the given items picked (default: both returnable items of RR-10482). */
  async function toReasonStep(names = ['Linen shirt', 'Ceramic mug set']) {
    await findOrder()
    for (const name of names) await user.click(item(name))
    await user.click(continueButton())
    await screen.findByText('Step 3 of 5 · Reason and photos')
  }

  /** Answer the item on screen with a reason and one photo. */
  async function answerItem(reasonName = 'Too large', fileName = 'front.jpg') {
    await user.click(reason(reasonName))
    await user.upload(photoInput(), photo(fileName))
  }

  return {
    user,
    orderNumber,
    email,
    submit,
    item,
    continueButton,
    backButton,
    photoInput,
    reason,
    findOrder,
    toReasonStep,
    answerItem,
  }
}

describe('Customer wizard · step 1 (find order)', () => {
  it('starts on step 1 of 5', () => {
    setup()
    expect(screen.getByText('Step 1 of 5 · Find your order')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Start a return' })).toBeInTheDocument()
  })

  it('asks for both fields before looking anything up', async () => {
    const { submit, orderNumber, email } = setup()
    await submit()

    expect(orderNumber()).toHaveAccessibleDescription('Enter your order number')
    expect(email()).toHaveAccessibleDescription('Enter your email')
    expect(orderNumber()).toHaveFocus()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('rejects a malformed email', async () => {
    const { user, submit, orderNumber, email } = setup()
    await user.type(orderNumber(), 'RR-10482')
    await user.type(email(), 'ananya.rao')
    await submit()

    expect(email()).toHaveAccessibleDescription('Enter a valid email, like name@example.com')
    expect(email()).toHaveFocus()
  })

  it('clears a field error once the field is edited', async () => {
    const { user, submit, orderNumber } = setup()
    await submit()
    await user.type(orderNumber(), 'R')
    expect(orderNumber()).not.toHaveAttribute('aria-invalid')
  })

  it('shows the not-found message for an unknown order', async () => {
    const { user, submit, orderNumber, email } = setup()
    await user.type(orderNumber(), 'RR-99999')
    await user.type(email(), 'ananya.rao@example.com')
    await submit()

    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t find that order')
    expect(screen.getByText('Step 1 of 5 · Find your order')).toBeInTheDocument()
  })

  it('finds the order ignoring case and spaces', async () => {
    const { findOrder } = setup()
    await findOrder('  rr-10482 ', 'Ananya.Rao@Example.com')

    expect(screen.getByRole('heading', { name: 'Which items are you returning?' })).toBeInTheDocument()
    expect(screen.getByText('Order #RR-10482 · delivered 6 Nov 2026')).toBeInTheDocument()
  })

  it('keeps the details when going back to step 1', async () => {
    const { user, findOrder, backButton, orderNumber, email } = setup()
    await findOrder()
    await user.click(backButton())

    expect(orderNumber()).toHaveValue('RR-10482')
    expect(email()).toHaveValue('ananya.rao@example.com')
  })
})

describe('Customer wizard · step 2 (choose items)', () => {
  it('lists every item and only lets returnable ones be picked', async () => {
    const { findOrder, item } = setup()
    await findOrder()

    expect(item('Linen shirt')).toBeEnabled()
    expect(item('Linen shirt')).toHaveAccessibleDescription(
      'Apparel · ₹1,899 You can return this until 6 Dec 2026.',
    )
    expect(item('Ceramic mug set')).toBeEnabled()
    expect(item('Wireless earbuds Pro')).toBeDisabled()
    expect(item('Cotton briefs, 3-pack')).toBeDisabled()
    expect(screen.getAllByText('Returnable')).toHaveLength(2)
    expect(screen.getAllByText('Not returnable')).toHaveLength(2)
    expect(
      screen.getByText('Innerwear can’t be returned for hygiene reasons.'),
    ).toBeInTheDocument()
  })

  it('waits for a selection, then totals the estimated refund', async () => {
    const { user, findOrder, item, continueButton } = setup()
    await findOrder()

    expect(screen.getByText('No items selected')).toBeInTheDocument()
    expect(continueButton()).toBeDisabled()

    await user.click(item('Linen shirt'))
    expect(screen.getByText('1 item selected')).toBeInTheDocument()

    // Clicking anywhere on the card toggles it too.
    await user.click(screen.getByText('Ceramic mug set'))
    expect(item('Ceramic mug set')).toBeChecked()
    expect(screen.getByText('2 items selected')).toBeInTheDocument()
    expect(screen.getByText('Est. refund ₹3,149')).toBeInTheDocument()
    expect(continueButton()).toBeEnabled()

    await user.click(item('Linen shirt'))
    expect(screen.getByText('Est. refund ₹1,250')).toBeInTheDocument()
  })

  it('ignores clicks on items that cannot be returned', async () => {
    const { user, findOrder, item } = setup()
    await findOrder()
    await user.click(screen.getByText('Wireless earbuds Pro'))
    expect(item('Wireless earbuds Pro')).not.toBeChecked()
    expect(screen.getByText('No items selected')).toBeInTheDocument()
  })

  it('keeps the selection when coming back from step 3', async () => {
    const { user, toReasonStep, backButton, item } = setup()
    await toReasonStep(['Linen shirt'])
    await user.click(backButton())
    expect(item('Linen shirt')).toBeChecked()
  })

  it('starts fresh when a different order is looked up', async () => {
    const { user, findOrder, item, backButton } = setup()
    await findOrder()
    await user.click(item('Linen shirt'))
    await user.click(backButton())

    await findOrder('RR-10517', 'rahul.mehta@example.com')
    expect(screen.getByText('No items selected')).toBeInTheDocument()
    expect(item('Denim jacket')).not.toBeChecked()
  })
})

describe('Customer wizard · step 3 (reason and photos)', () => {
  it('goes through the picked items one at a time', async () => {
    const { user, toReasonStep, answerItem, continueButton } = setup()
    await toReasonStep()

    expect(
      screen.getByText('Item 1 of 2 · each item needs its own reason and photos'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '1 · Linen shirt' })).toHaveAttribute(
      'aria-current',
      'step',
    )
    expect(screen.getByRole('heading', { name: 'Why are you returning it?' })).toBeInTheDocument()
    expect(screen.getByText('Linen shirt · Apparel · ₹1,899')).toBeInTheDocument()
    expect(screen.getByText('Next: Ceramic mug set')).toBeInTheDocument()

    await answerItem()
    await user.click(continueButton())

    expect(screen.getByText('Ceramic mug set · Home · ₹1,250')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '2 · Ceramic mug set' })).toHaveAttribute(
      'aria-current',
      'step',
    )
    expect(screen.getByText('Last item')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Why are you returning it?' })).toHaveFocus()
  })

  it('needs a reason and a photo before moving on', async () => {
    const { user, toReasonStep, continueButton, reason, photoInput } = setup()
    await toReasonStep()
    await user.click(continueButton())

    expect(screen.getByText('Choose a reason')).toBeInTheDocument()
    expect(screen.getByText('Add at least one photo')).toBeInTheDocument()
    expect(reason('Too large')).toHaveFocus()
    expect(screen.getByText('Linen shirt · Apparel · ₹1,899')).toBeInTheDocument()

    await user.click(reason('Not as described'))
    expect(screen.queryByText('Choose a reason')).not.toBeInTheDocument()
    await user.click(continueButton())
    expect(photoInput()).toHaveFocus()

    await user.upload(photoInput(), photo('tag.png', 'image/png'))
    expect(screen.queryByText('Add at least one photo')).not.toBeInTheDocument()
  })

  it('remembers each item’s answers when moving between items', async () => {
    const { user, toReasonStep, answerItem, continueButton, backButton, reason } = setup()
    await toReasonStep()
    await answerItem('Too small', 'front.jpg')
    await user.type(screen.getByRole('textbox', { name: 'Tell us more' }), 'Tags attached')
    await user.click(continueButton())

    expect(reason('Too small')).not.toBeChecked()
    expect(screen.getByRole('textbox', { name: 'Tell us more' })).toHaveValue('')

    await user.click(backButton())
    expect(reason('Too small')).toBeChecked()
    expect(screen.getByRole('textbox', { name: 'Tell us more' })).toHaveValue('Tags attached')
    expect(screen.getByText('13 / 500')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Added photos' })).getByText('front.jpg')).toBeInTheDocument()
  })

  it('checks photo type, size and count, and lets photos be removed', async () => {
    const { user, toReasonStep, photoInput } = setup({ applyAccept: false })
    await toReasonStep(['Linen shirt'])

    await user.upload(photoInput(), photo('scan.gif', 'image/gif'))
    expect(screen.getByText('scan.gif isn’t a JPG or PNG.')).toBeInTheDocument()

    const huge = photo('huge.jpg')
    Object.defineProperty(huge, 'size', { value: 6 * 1024 * 1024 })
    await user.upload(photoInput(), huge)
    expect(screen.getByText('huge.jpg is larger than 5 MB.')).toBeInTheDocument()

    await user.upload(photoInput(), [photo('a.jpg'), photo('b.jpg'), photo('c.jpg'), photo('d.jpg')])
    expect(screen.getByText('You can add up to 3 photos.')).toBeInTheDocument()
    const added = screen.getByRole('list', { name: 'Added photos' })
    expect(within(added).getAllByRole('listitem')).toHaveLength(3)
    expect(photoInput()).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Remove a.jpg' }))
    expect(within(added).getAllByRole('listitem')).toHaveLength(2)
    expect(within(added).queryByText('a.jpg')).not.toBeInTheDocument()
    expect(photoInput()).toBeEnabled()
  })

  it('only leaves step 3 when every item is answered', async () => {
    const { user, toReasonStep, answerItem, continueButton } = setup()
    await toReasonStep()

    // Skip item 1 via its tab, answer item 2, then try to finish.
    await user.click(screen.getByRole('button', { name: '2 · Ceramic mug set' }))
    await answerItem('Damaged on arrival', 'chip.jpg')
    await user.click(continueButton())

    expect(screen.getByText('Linen shirt · Apparel · ₹1,899')).toBeInTheDocument()
    expect(screen.getByText('Choose a reason')).toBeInTheDocument()

    await answerItem('Too large', 'front.jpg')
    await user.click(screen.getByRole('button', { name: '2 · Ceramic mug set' }))
    await user.click(continueButton())

    expect(screen.getByText('Step 4 of 5 · Refund method')).toBeInTheDocument()
    await user.click(continueButton())
    const review = (name: string) => within(screen.getByRole('region', { name }))
    expect(review('Linen shirt').getByText('Too large')).toBeInTheDocument()
    expect(review('Ceramic mug set').getByText('Damaged on arrival')).toBeInTheDocument()
  })

  it('has no item tabs when only one item is returned', async () => {
    const { toReasonStep } = setup()
    await toReasonStep(['Linen shirt'])
    expect(screen.queryByRole('navigation', { name: 'Items to return' })).not.toBeInTheDocument()
    expect(screen.queryByText(/^Item 1 of/)).not.toBeInTheDocument()
  })
})

describe('Customer wizard · steps 4–5 (placeholders)', () => {
  it('reaches step 5, which has no Continue, and Back returns to the last item', async () => {
    const { user, toReasonStep, answerItem, continueButton, backButton } = setup()
    await toReasonStep()
    await answerItem()
    await user.click(continueButton())
    await answerItem('Not as described', 'mug.jpg')
    await user.click(continueButton())

    expect(screen.getByText('Step 4 of 5 · Refund method')).toBeInTheDocument()
    await user.click(continueButton())
    expect(screen.getByText('Step 5 of 5 · Review')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Continue' })).not.toBeInTheDocument()

    await user.click(backButton())
    await user.click(backButton())
    expect(screen.getByText('Ceramic mug set · Home · ₹1,250')).toBeInTheDocument()
  })
})
