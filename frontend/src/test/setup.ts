import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
  localStorage.clear()
})

// Fresh mock backend for every test.
import { beforeEach } from 'vitest'
import { resetInspectionMock } from '@/api/mocks/inspection.mock'
beforeEach(() => resetInspectionMock())

// jsdom has no ResizeObserver; Radix (radio group, etc.) needs one.
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
}

import { resetDashboardMock } from '@/api/mocks/dashboard.mock'
beforeEach(() => resetDashboardMock())
