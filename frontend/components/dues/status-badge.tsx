import { Badge } from "@/components/ui/badge";
import { DUE_STATUS_LABELS, type DueStatus, isOpen } from "@/lib/dues";
import { cn } from "@/lib/utils";

const STATUS_STYLES: Record<DueStatus, string> = {
  SCHEDULED: "",
  ACTIVE: "text-info",
  ESCALATED: "text-warning",
  PAID: "text-success",
  CANCELLED: "text-muted-foreground",
};

/** An open due whose customer unsubscribed gets no reminders, so it says that instead of "Reminding". */
export function StatusBadge({ status, unsubscribed = false }: { status: DueStatus; unsubscribed?: boolean }) {
  if (unsubscribed && isOpen(status)) {
    return (
      <Badge variant="outline" className="text-warning">
        Unsubscribed
      </Badge>
    );
  }
  return (
    <Badge variant={status === "CANCELLED" ? "outline" : "secondary"} className={cn(STATUS_STYLES[status])}>
      {DUE_STATUS_LABELS[status]}
    </Badge>
  );
}
