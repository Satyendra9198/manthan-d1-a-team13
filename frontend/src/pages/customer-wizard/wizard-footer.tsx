import type { ReactNode } from "react"

/**
 * Action bar for wizard steps, pinned to the bottom of the window with its
 * content lined up with the page column (summary on the left, buttons on the right).
 */
export function WizardFooter({ children }: { children: ReactNode }) {
  return (
    <>
      {/* Keeps the end of the step clear of the pinned bar. */}
      <div aria-hidden className="h-16" />
      <div className="fixed inset-x-0 bottom-0 z-10 border-t bg-background pb-[env(safe-area-inset-bottom)]">
        <div className="mx-auto flex max-w-md items-center gap-3 px-4 py-3">{children}</div>
      </div>
    </>
  )
}
