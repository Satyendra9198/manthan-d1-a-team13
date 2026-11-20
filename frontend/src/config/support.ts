/** Customer support contact used on customer-facing screens. Replace with the real address. */
export const SUPPORT_EMAIL = 'support@returnright.example'

export const supportMailto = (subject?: string) =>
  `mailto:${SUPPORT_EMAIL}${subject ? `?subject=${encodeURIComponent(subject)}` : ''}`
