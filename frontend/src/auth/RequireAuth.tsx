import { Navigate, Outlet, useLocation } from "react-router";
import type { Role } from "../api/types";
import { Forbidden } from "../pages/public/Forbidden";
import { hasRole, loginPathFor, useSession } from "./session";

/** UX-only guard (§11): the backend is the authority. Unauthenticated → login; wrong role → 403 page. */
export function RequireAuth({ roles }: { roles: Role[] }) {
  const session = useSession();
  const location = useLocation();
  // Expiry is enforced by the API: a 401 clears the session and redirects (client.ts).
  const live = session;

  if (!live) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`${loginPathFor(roles)}?next=${next}`} replace />;
  }
  if (!hasRole(live.principal, roles)) {
    return <Forbidden />;
  }
  return <Outlet />;
}
