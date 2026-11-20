import { Compass, ShieldAlert } from "lucide-react";
import type { ReactNode } from "react";
import { isRouteErrorResponse, useRouteError } from "react-router";
import { homeFor, useSession } from "../../auth/session";
import { Logo } from "../../components/Brand";
import { ButtonLink } from "../../components/ui/Button";
import { EmptyState } from "../../components/ui/EmptyState";

function Shell({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-dvh place-items-center px-4">
      <div className="w-full max-w-lg">
        <div className="mb-4 flex justify-center">
          <Logo />
        </div>
        <div className="rounded-3xl border border-line bg-surface shadow-card">{children}</div>
      </div>
    </div>
  );
}

export function Forbidden() {
  const session = useSession();
  const home = session ? homeFor(session.principal.role) : "/";
  return (
    <Shell>
      <EmptyState
        icon={<ShieldAlert />}
        title="You don't have access to this area"
        description="Your role can't open this page. If you think this is a mistake, ask an administrator."
        action={<ButtonLink to={home}>Go to my workspace</ButtonLink>}
        testId="forbidden"
      />
    </Shell>
  );
}

export function NotFound() {
  return (
    <Shell>
      <EmptyState
        icon={<Compass />}
        title="Page not found"
        description="The page you're looking for doesn't exist or has moved."
        action={<ButtonLink to="/">Back to home</ButtonLink>}
      />
    </Shell>
  );
}

export function RouteError() {
  const error = useRouteError();
  if (isRouteErrorResponse(error) && error.status === 404) return <NotFound />;
  return (
    <Shell>
      <EmptyState
        icon={<ShieldAlert />}
        title="Something went wrong"
        description="An unexpected error occurred. Reload the page to try again."
        action={
          <button type="button" onClick={() => window.location.reload()} className="text-sm font-medium text-brand hover:underline">
            Reload
          </button>
        }
      />
    </Shell>
  );
}
