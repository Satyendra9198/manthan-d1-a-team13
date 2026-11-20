import { useId } from "react"
import type { Order, OrderItem } from "@/api/orders"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { formatDate, formatMoney } from "@/lib/format"
import { cn } from "@/lib/utils"
import { WizardFooter } from "./wizard-footer"

type ChooseItemsStepProps = {
  order: Order
  selectedIds: string[]
  onSelectedIdsChange: (ids: string[]) => void
  onBack: () => void
  onContinue: () => void
}

/** Wizard step 2: pick which items to return. Items the policy excludes can't be picked. */
export function ChooseItemsStep({
  order,
  selectedIds,
  onSelectedIdsChange,
  onBack,
  onContinue,
}: ChooseItemsStepProps) {
  const headingId = useId()
  const selectedItems = order.items.filter((item) => selectedIds.includes(item.id))
  const count = selectedItems.length
  const refund = selectedItems.reduce((sum, item) => sum + item.price, 0)

  function setSelected(itemId: string, selected: boolean) {
    onSelectedIdsChange(
      selected ? [...selectedIds, itemId] : selectedIds.filter((id) => id !== itemId),
    )
  }

  return (
    <div className="grid gap-4">
      <div className="grid gap-1">
        <h1 id={headingId} className="text-xl font-semibold tracking-tight">
          Which items are you returning?
        </h1>
        <p className="text-sm text-muted-foreground">
          Order #{order.orderNumber} · delivered {formatDate(order.deliveredAt)}
        </p>
      </div>

      <ul aria-labelledby={headingId} className="grid gap-4">
        {order.items.map((item) => (
          <ItemOption
            key={item.id}
            item={item}
            currency={order.currency}
            selected={selectedIds.includes(item.id)}
            onSelectedChange={(selected) => setSelected(item.id, selected)}
          />
        ))}
      </ul>

      <WizardFooter>
        <div aria-live="polite" className="min-w-0 flex-1">
          <p className="text-sm">
            {count === 0 ? "No items selected" : `${count} ${count === 1 ? "item" : "items"} selected`}
          </p>
          <p className="text-[13px] text-muted-foreground">
            {count === 0 ? "Choose at least one item" : `Est. refund ${formatMoney(refund, order.currency)}`}
          </p>
        </div>
        <Button variant="outline" onClick={onBack}>
          Back
        </Button>
        <Button onClick={onContinue} disabled={count === 0}>
          Continue
        </Button>
      </WizardFooter>
    </div>
  )
}

type ItemOptionProps = {
  item: OrderItem
  currency: string
  selected: boolean
  onSelectedChange: (selected: boolean) => void
}

/** One item as a selectable card; the whole card toggles the checkbox. */
function ItemOption({ item, currency, selected, onSelectedChange }: ItemOptionProps) {
  const nameId = useId()
  const detailsId = useId()
  const eligibility = item.eligibility
  const returnable = eligibility.returnable

  return (
    <li>
      <label
        className={cn(
          "grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 rounded-lg border p-4 transition-colors",
          returnable
            ? "cursor-pointer bg-card hover:border-foreground/40"
            : "cursor-not-allowed bg-muted/70",
          selected && "border-primary ring-1 ring-primary hover:border-primary",
        )}
      >
        <Checkbox
          className="mt-0.5"
          checked={selected}
          disabled={!returnable}
          onCheckedChange={(value) => onSelectedChange(value === true)}
          aria-labelledby={nameId}
          aria-describedby={detailsId}
        />
        <div className="flex items-start justify-between gap-3">
          <span
            id={nameId}
            className={cn("font-medium", !returnable && "text-muted-foreground")}
          >
            {item.name}
          </span>
          <Badge variant={returnable ? "success" : "danger"}>
            {returnable ? "Returnable" : "Not returnable"}
          </Badge>
        </div>
        <div id={detailsId} className="col-start-2 grid gap-1 text-[13px]">
          <p className="text-muted-foreground">
            {item.category} · {formatMoney(item.price, currency)}
          </p>
          <p className={returnable ? "text-muted-foreground" : "text-foreground"}>
            {eligibility.returnable
              ? `You can return this until ${formatDate(eligibility.returnBy)}.`
              : eligibility.reason}
          </p>
        </div>
      </label>
    </li>
  )
}
