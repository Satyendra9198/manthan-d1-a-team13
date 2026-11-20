/** Reasons an agent can reject a return. The label is shown to the customer. */
export const REJECT_REASONS = [
  'not_as_described',
  'used_or_worn',
  'missing_parts_or_tags',
  'outside_return_window',
  'other',
] as const

export type RejectReason = (typeof REJECT_REASONS)[number]

export const REJECT_REASON_LABELS: Record<RejectReason, string> = {
  not_as_described: 'Not as described',
  used_or_worn: 'Used or worn',
  missing_parts_or_tags: 'Missing parts or tags',
  outside_return_window: 'Outside return window',
  other: 'Other',
}

/** "Other" means nothing to the customer on its own, so it needs a note. */
export function rejectNoteRequired(reason: RejectReason): boolean {
  return reason === 'other'
}
