import { useEffect, useState } from "react"

/**
 * Minimal hash-based routing until the app adopts a router.
 * Hash URLs need no server setup and survive reloads.
 *
 *   #/                        start a return (wizard)
 *   #/track                   look up a return by its RMA number
 *   #/returns/RMA-2026-00731  that return's status
 */
export type Route =
  | { page: "start-return" }
  | { page: "track-return" }
  | { page: "return-status"; rmaNumber: string }

export const START_RETURN_HREF = "#/"
export const TRACK_RETURN_HREF = "#/track"

/** Link to a return's status page. */
export function returnStatusHref(rmaNumber: string): string {
  return `#/returns/${encodeURIComponent(rmaNumber)}`
}

/** Which page a hash path (without "#") points to. Unknown paths start a return. */
export function parseRoute(path: string): Route {
  if (/^\/track\/?$/.test(path)) return { page: "track-return" }
  const match = /^\/returns\/([^/]+)\/?$/.exec(path)
  if (match) return { page: "return-status", rmaNumber: decodeURIComponent(match[1]) }
  return { page: "start-return" }
}

/** The current page. Updates when the hash changes. */
export function useRoute(): Route {
  const [hash, setHash] = useState(() => window.location.hash)

  useEffect(() => {
    const onHashChange = () => setHash(window.location.hash)
    window.addEventListener("hashchange", onHashChange)
    return () => window.removeEventListener("hashchange", onHashChange)
  }, [])

  return parseRoute(hash.replace(/^#/, ""))
}
