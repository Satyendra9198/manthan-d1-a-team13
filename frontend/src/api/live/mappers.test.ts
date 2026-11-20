import { describe, expect, it } from 'vitest'
import type {
  BackendFinanceQueueItem,
  BackendTracking,
  BackendWarehouseItem,
  BackendWarehouseReturn,
} from './backend-types'
import {
  itemLabel,
  toBackendDecision,
  toDecisionResponse,
  toFinanceDecision,
  toFinanceQueueItem,
  toReturnDetails,
  toReturnTracking,
} from './mappers'

const line = (over: Partial<BackendWarehouseItem> = {}): BackendWarehouseItem => ({
  returnItemId: 'line-1',
  orderItemId: 'order-line-1',
  sku: 'APP-WOOL-SWTR-M',
  productName: 'Wool sweater',
  variant: 'M / Charcoal',
  category: 'APPAREL',
  unitPricePaise: 249_900,
  quantity: 1,
  reason: 'TOO_BIG',
  customerComment: 'Fits like an XL.',
  photos: [],
  grade: null,
  acceptedQuantity: null,
  rejectionReason: null,
  inspectionNote: null,
  ...over,
})

const warehouseReturn = (items: BackendWarehouseItem[]): BackendWarehouseReturn => ({
  returnId: 'ret-uuid',
  rmaNumber: 'RMA-DEMO-00006',
  status: 'RECEIVED',
  orderNumber: 'RR-10750',
  customerName: 'Karan Kapoor',
  customerFlagged: false,
  customerReturnsLast30Days: 1,
  refundMethod: 'ORIGINAL_PAYMENT',
  createdAt: '2026-11-14T10:00:00Z',
  receivedAt: '2026-11-19T10:00:00Z',
  refundAmountPaise: null,
  rejectionReason: null,
  allowedActions: ['COMPLETE_INSPECTION', 'REJECT_AFTER_INSPECTION'],
  items,
})

const sweater = line()
const wallets = line({
  returnItemId: 'line-2',
  sku: 'APP-WALLET-BRN',
  productName: 'Leather wallet',
  unitPricePaise: 129_900,
  quantity: 2,
  reason: 'CHANGED_MY_MIND',
  customerComment: null,
})

describe('itemLabel', () => {
  it('names one item, its quantity, or the first plus a count', () => {
    expect(itemLabel([{ productName: 'Linen shirt', quantity: 1 }])).toBe('Linen shirt')
    expect(itemLabel([{ productName: 'Linen shirt', quantity: 2 }])).toBe('Linen shirt ×2')
    expect(
      itemLabel([
        { productName: 'Linen shirt', quantity: 1 },
        { productName: 'Mug set', quantity: 1 },
      ]),
    ).toBe('Linen shirt + 1 more')
  })
})

describe('toReturnDetails', () => {
  it('combines the lines into one item priced at the whole return', () => {
    const details = toReturnDetails(warehouseReturn([sweater, wallets]), 'IN_TRANSIT', [])

    expect(details.id).toBe('RMA-DEMO-00006')
    expect(details.item.name).toBe('Wool sweater + 1 more')
    expect(details.item.pricePaise).toBe(249_900 + 2 * 129_900)
    expect(details.reason).toBe('Too big, Changed my mind')
    expect(details.status).toBe('received')
    expect(details.previousStatus).toBe('in_transit')
    expect(details.customerNote).toBe('Fits like an XL.')
    expect(details.refundMethod).toBe('original_payment')
  })

  it('shows the customer flag from the backend', () => {
    const flagged = { ...warehouseReturn([sweater]), customerFlagged: true, customerReturnsLast30Days: 4 }

    expect(toReturnDetails(flagged, null, []).customer).toEqual({
      id: 'RR-10750',
      name: 'Karan Kapoor',
      returnsLast30Days: 4,
      flaggedForReview: true,
    })
  })
})

describe('toBackendDecision', () => {
  const ret = warehouseReturn([sweater, wallets])

  it('accept: every unit of every line at the chosen grade', () => {
    expect(
      toBackendDecision(ret, { decision: 'accept', grade: 'C', notes: ' Seam torn ', requiresFinanceApproval: false }),
    ).toEqual({
      kind: 'inspection',
      body: {
        items: [
          { returnItemId: 'line-1', grade: 'C', acceptedQuantity: 1, note: 'Seam torn' },
          { returnItemId: 'line-2', grade: 'C', acceptedQuantity: 2, note: 'Seam torn' },
        ],
      },
    })
  })

  it('partial accept: the amount fills the lines in order and the short line carries the reason', () => {
    const decision = toBackendDecision(ret, {
      decision: 'partial_accept',
      grade: 'A',
      refundAmountPaise: 300_000,
      reason: 'Missing parts or accessories',
      notes: 'Box missing',
      requiresFinanceApproval: false,
    })

    expect(decision).toEqual({
      kind: 'inspection',
      body: {
        items: [
          { returnItemId: 'line-1', grade: 'A', acceptedQuantity: 1 },
          {
            returnItemId: 'line-2',
            grade: 'A',
            acceptedQuantity: 2,
            rejectionReason: 'OTHER',
            note: 'Missing parts or accessories — Box missing',
            refundAmountPaise: 300_000 - 249_900,
          },
        ],
      },
    })
  })

  it('partial accept: a line the amount does not reach is not accepted', () => {
    const decision = toBackendDecision(ret, {
      decision: 'partial_accept',
      grade: 'C',
      refundAmountPaise: 100_000,
      reason: 'Heavy wear',
      notes: '',
      requiresFinanceApproval: false,
    })

    expect(decision.kind === 'inspection' && decision.body.items).toEqual([
      {
        returnItemId: 'line-1',
        grade: 'C',
        acceptedQuantity: 1,
        rejectionReason: 'OTHER',
        note: 'Heavy wear',
        refundAmountPaise: 100_000,
      },
      { returnItemId: 'line-2', grade: null, acceptedQuantity: 0, rejectionReason: 'OTHER', note: 'Heavy wear' },
    ])
  })

  it('reject: the whole return with the agent reason', () => {
    expect(
      toBackendDecision(ret, { decision: 'reject', grade: null, reason: 'used_or_worn', notes: '' }),
    ).toEqual({ kind: 'reject', body: { reason: 'USED_OR_WORN', note: undefined } })
  })
})

describe('toDecisionResponse', () => {
  const ret = warehouseReturn([sweater])
  const accept = { decision: 'accept', grade: 'A', notes: '', requiresFinanceApproval: false } as const

  it('paid at once is "refund initiated"', () => {
    const res = toDecisionResponse(
      ret,
      accept,
      { rmaNumber: 'RMA-DEMO-00006', status: 'REFUNDED', refundAmountPaise: 249_900, financeApprovalRequired: false, refundStatus: 'SUCCEEDED' },
      '2026-11-20T10:00:00Z',
    )
    expect(res).toMatchObject({ outcome: 'refund_initiated', status: 'refunded', refundAmountPaise: 249_900 })
  })

  it('above the threshold is "sent to finance" and still inspected', () => {
    const res = toDecisionResponse(
      ret,
      accept,
      { rmaNumber: 'RMA-DEMO-00006', status: 'AWAITING_FINANCE_APPROVAL', refundAmountPaise: 5_499_900, financeApprovalRequired: true, refundStatus: null },
      '2026-11-20T10:00:00Z',
    )
    expect(res).toMatchObject({ outcome: 'sent_to_finance', status: 'inspected', requiresFinanceApproval: true })
  })

  it('rejected stays rejected', () => {
    const res = toDecisionResponse(
      ret,
      { decision: 'reject', grade: null, reason: 'used_or_worn', notes: '' },
      { rmaNumber: 'RMA-DEMO-00006', status: 'REJECTED', refundAmountPaise: 0, financeApprovalRequired: false, refundStatus: null },
      '2026-11-20T10:00:00Z',
    )
    expect(res).toMatchObject({ outcome: 'rejected', status: 'rejected' })
  })
})

describe('finance', () => {
  const queued: BackendFinanceQueueItem = {
    rmaNumber: 'RMA-DEMO-00009',
    orderNumber: 'RR-10780',
    customerName: 'Divya Menon',
    customerFlagged: false,
    customerReturnsLast30Days: 1,
    itemNames: ['Laptop 14 inch'],
    grade: 'A',
    partiallyAccepted: false,
    refundAmountPaise: 5_499_900,
    requestedAmountPaise: 5_499_900,
    refundMethod: 'ORIGINAL_PAYMENT',
    inspectedByName: 'Riya Kapoor',
    inspectedAt: '2026-11-19T10:00:00Z',
    refundKey: 'return-abc',
    itemCount: 1,
  }

  it('queue item uses the RMA as its id and keeps the refund key', () => {
    expect(toFinanceQueueItem(queued)).toMatchObject({
      returnId: 'RMA-DEMO-00009',
      item: { name: 'Laptop 14 inch', pricePaise: 5_499_900 },
      decision: 'accept',
      inspectedBy: 'Riya Kapoor',
      payoutMethod: 'Original payment',
      refundKey: 'return-abc',
    })
  })

  it('approval that reached REFUNDED is paid with its reference', () => {
    expect(
      toFinanceDecision(
        { rmaNumber: 'RMA-DEMO-00009', status: 'REFUNDED', refundStatus: 'SUCCEEDED', refundAmountPaise: 5_499_900, refundReference: 'MOCK-TXN-000001', decidedAt: '2026-11-20T10:00:00Z' },
        'approve',
      ),
    ).toMatchObject({ outcome: 'paid', status: 'refunded', refund: { reference: 'MOCK-TXN-000001' } })
  })

  it('approval the gateway could not pay yet is "payment failed"', () => {
    expect(
      toFinanceDecision(
        { rmaNumber: 'RMA-DEMO-00009', status: 'REFUND_PENDING', refundStatus: 'UNKNOWN', refundAmountPaise: 5_499_900, refundReference: null, decidedAt: '2026-11-20T10:00:00Z' },
        'approve',
      ),
    ).toMatchObject({ outcome: 'payment_failed', refund: null })
  })
})

describe('toReturnTracking', () => {
  const base: BackendTracking = {
    rmaNumber: 'RMA-DEMO-00007',
    orderNumber: 'RR-10760',
    status: 'REFUNDED',
    statusMessage: '₹949.50 refunded to your original payment method.',
    refundMethod: 'ORIGINAL_PAYMENT',
    createdAt: '2026-11-10T10:00:00Z',
    estimatedRefundPaise: 189_900,
    timeline: [
      { step: 'REQUESTED', state: 'COMPLETED', at: '2026-11-10T10:00:00Z' },
      { step: 'APPROVED', state: 'COMPLETED', at: '2026-11-10T10:00:00Z' },
      { step: 'RECEIVED', state: 'COMPLETED', at: '2026-11-15T10:00:00Z' },
      { step: 'INSPECTED', state: 'COMPLETED', at: '2026-11-17T10:00:00Z' },
      { step: 'REFUNDED', state: 'COMPLETED', at: '2026-11-17T10:05:00Z' },
    ],
    items: [
      {
        itemId: 'i1',
        productName: 'Linen shirt',
        variant: 'L / White',
        quantity: 1,
        reason: 'TOO_SMALL',
        lineTotalPaise: 189_900,
        grade: 'C',
        acceptedQuantity: 1,
        refundPaise: 94_950,
        rejectionReason: null,
        inspectionNote: 'A torn seam',
      },
    ],
    label: null,
    refund: {
      amountPaise: 94_950,
      requestedAmountPaise: 189_900,
      method: 'ORIGINAL_PAYMENT',
      status: 'REFUNDED',
      refundedAt: '2026-11-17T10:05:00Z',
      financeReview: false,
      reference: 'MOCK-TXN-DEMO-1',
      explanation: [],
    },
    rejection: null,
  }

  it('refunded at grade C: history, grade, note and payment reference', () => {
    const t = toReturnTracking(base)

    expect(t.status).toBe('refunded')
    expect(t.history.map((h) => h.status)).toEqual(['requested', 'approved', 'received', 'inspected', 'refunded'])
    expect(t.inspection).toEqual({
      decision: 'accept',
      grade: 'C',
      notes: 'A torn seam',
      partialReason: null,
      refundAmountPaise: 94_950,
    })
    expect(t.refund).toEqual({
      amountPaise: 94_950,
      paidAt: '2026-11-17T10:05:00Z',
      destination: 'Original payment method',
      reference: 'MOCK-TXN-DEMO-1',
    })
    expect(t.requiresFinanceApproval).toBe(false)
  })

  it('a partial accept with a chosen reason is explained by that reason', () => {
    const t = toReturnTracking({
      ...base,
      items: [{ ...base.items[0], grade: 'A', rejectionReason: 'OTHER', inspectionNote: 'Box missing', refundPaise: 120_000 }],
      refund: { ...base.refund!, amountPaise: 120_000 },
    })

    expect(t.inspection).toMatchObject({ decision: 'partial_accept', partialReason: 'Box missing', notes: '' })
  })

  it('under finance review before payment', () => {
    const t = toReturnTracking({
      ...base,
      status: 'INSPECTED',
      timeline: base.timeline.slice(0, 4),
      refund: { ...base.refund!, status: 'PROCESSING', refundedAt: null, financeReview: true, reference: null },
    })

    expect(t.status).toBe('inspected')
    expect(t.requiresFinanceApproval).toBe(true)
    expect(t.refund).toBeNull()
  })

  it('rejected: reason, note and when', () => {
    const t = toReturnTracking({
      ...base,
      status: 'REJECTED',
      timeline: [...base.timeline.slice(0, 3), { step: 'REJECTED', state: 'COMPLETED', at: '2026-11-18T10:00:00Z' }],
      items: [{ ...base.items[0], grade: null, acceptedQuantity: 0, refundPaise: 0 }],
      refund: null,
      rejection: { reason: 'USED_OR_WORN', message: 'The item shows signs of use or wear.', note: null },
    })

    expect(t.rejection).toEqual({ reason: 'used_or_worn', notes: '', at: '2026-11-18T10:00:00Z' })
    expect(t.inspection).toBeNull()
  })

  it('a declined request explains itself to the customer', () => {
    const t = toReturnTracking({
      ...base,
      status: 'REJECTED',
      refund: null,
      rejection: { reason: 'REQUEST_DECLINED', message: 'Your return request was not approved after review.', note: null },
    })

    expect(t.rejection).toMatchObject({ reason: 'other', notes: 'Your return request was not approved after review.' })
  })
})
