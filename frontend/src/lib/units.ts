import type { OrderUnit } from "../api/types";

export function unitKey(u: { order_item_id: number; unit_index: number }): string {
  return `${u.order_item_id}:${u.unit_index}`;
}

/** Only eligible units that are not already in an active return can be selected [A-06][A-07]. */
export function isSelectable(u: OrderUnit): boolean {
  return u.eligibility.eligible && u.active_return_id === null;
}
