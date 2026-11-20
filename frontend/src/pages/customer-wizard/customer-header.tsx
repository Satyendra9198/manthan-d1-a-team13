import { START_RETURN_HREF, TRACK_RETURN_HREF } from "@/lib/hash-route"

type CustomerHeaderProps = {
  /** Which main link is the page being shown. */
  current?: "start-return" | "track-return"
  /** Runs when "Start a return" is clicked, e.g. to restart a finished wizard. */
  onStartReturn?: () => void
}

const LINKS = [
  { page: "start-return", label: "Start a return", href: START_RETURN_HREF },
  { page: "track-return", label: "Track a return", href: TRACK_RETURN_HREF },
] as const

/**
 * Top bar for the customer pages: brand, the two main links, and help.
 * On phones the main links drop to a second row so nothing gets squeezed.
 */
export function CustomerHeader({ current, onStartReturn }: CustomerHeaderProps) {
  return (
    <header className="border-b bg-background">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center px-4 sm:gap-8">
        <span className="flex h-12 items-center text-base font-semibold tracking-tight">
          ReturnRight
        </span>

        <nav
          aria-label="Main"
          className="order-last -mx-4 flex w-[calc(100%+2rem)] border-t sm:order-none sm:mx-0 sm:w-auto sm:gap-6 sm:border-0"
        >
          {LINKS.map((link) => (
            <a
              key={link.page}
              href={link.href}
              aria-current={current === link.page ? "page" : undefined}
              onClick={link.page === "start-return" ? onStartReturn : undefined}
              className="flex-1 border-b-2 border-transparent py-2.5 text-center text-sm text-muted-foreground transition-colors outline-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:ring-inset aria-[current=page]:border-primary aria-[current=page]:font-medium aria-[current=page]:text-foreground sm:flex-none sm:py-3.5"
            >
              {link.label}
            </a>
          ))}
        </nav>

        {/* TODO: link to the help page once it exists. */}
        <a
          href="#help"
          className="ml-auto rounded-sm text-sm text-muted-foreground transition-colors outline-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          Need help?
        </a>
      </div>
    </header>
  )
}
