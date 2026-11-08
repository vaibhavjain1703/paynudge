"use client";

import { Inbox } from "lucide-react";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useMe } from "@/lib/auth";
import type { DueMessage } from "@/lib/dues";
import { formatDateTime } from "@/lib/money";

/**
 * One reminder (or owner alert) exactly as PayNudge wrote it. Its links work, so the pay link opens the customer's
 * pay page. Where reminders are kept instead of emailed (the live demo), it says so.
 */
export function MessageDialog({
  message,
  to,
  timeZone,
  onOpenChange,
}: {
  message: DueMessage | null;
  to: string | null;
  timeZone: string;
  onOpenChange: (open: boolean) => void;
}) {
  const me = useMe();
  const owner = message?.channel === "OWNER_EMAIL";
  return (
    <Dialog open={message !== null} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        {message && (
          <>
            <DialogHeader>
              <DialogTitle className="pr-6 leading-snug">{message.subject ?? "Reminder"}</DialogTitle>
              <DialogDescription>
                To {owner ? "you" : (to ?? "the customer")} · {formatDateTime(message.sentAt, timeZone)}
              </DialogDescription>
            </DialogHeader>
            {me.data?.features.remindersKept && !owner && (
              <p className="flex items-start gap-2 rounded-lg bg-muted p-3 text-xs text-muted-foreground">
                <Inbox className="mt-px size-3.5 shrink-0" aria-hidden="true" />
                On this server reminders stay in PayNudge instead of being emailed, so this is the only copy. Its pay
                link works: open it to see what your customer would.
              </p>
            )}
            <div className="max-h-[55vh] overflow-y-auto rounded-lg border bg-muted/30 p-4 text-sm leading-relaxed whitespace-pre-wrap break-words">
              <Linked text={message.body} />
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

const URL = /(https?:\/\/[^\s]+)/g;

/** Plain text with its web addresses as links. React escapes the text, so nothing in it can become markup. */
function Linked({ text }: { text: string }) {
  return text.split(URL).map((part, index) =>
    index % 2 === 1 ? (
      <a
        key={index}
        href={part}
        target="_blank"
        rel="noopener noreferrer"
        className="font-medium text-primary underline underline-offset-4"
      >
        {part}
      </a>
    ) : (
      part
    ),
  );
}
