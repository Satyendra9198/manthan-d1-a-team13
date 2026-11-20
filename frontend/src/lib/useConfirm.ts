import { useCallback, useState } from "react";
import { newIdempotencyKey } from "../api/client";

/**
 * Confirm-dialog state with ONE Idempotency-Key per opening (contract 🔑, ADR-001).
 * The key is created when the dialog opens and reused for every resend from that dialog,
 * so a double click or a retry after a timeout replays instead of creating a second effect.
 */
export function useConfirm<T = undefined>() {
  const [state, setState] = useState<{ open: boolean; key: string; target: T | undefined }>({
    open: false,
    key: "",
    target: undefined,
  });

  const open = useCallback((target?: T) => {
    setState({ open: true, key: newIdempotencyKey(), target });
  }, []);

  const close = useCallback(() => {
    setState((s) => ({ ...s, open: false }));
  }, []);

  return { isOpen: state.open, idempotencyKey: state.key, target: state.target, open, close };
}
