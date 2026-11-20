import type { ReturnStage } from "@/api/returns"
import type { BadgeProps } from "@/components/ui/badge"

/** How each stage is named on screen. */
export const STAGE_TITLES: Record<ReturnStage, string> = {
  requested: "Requested",
  approved: "Approved",
  in_transit: "In transit",
  received: "Received",
  inspected: "Inspected",
  refunded: "Refunded",
  rejected: "Rejected",
}

/** Badge colour for a return currently at each stage. */
export const STAGE_BADGE_VARIANTS: Record<ReturnStage, NonNullable<BadgeProps["variant"]>> = {
  requested: "default",
  approved: "success",
  in_transit: "info",
  received: "info",
  inspected: "info",
  refunded: "success",
  rejected: "danger",
}
