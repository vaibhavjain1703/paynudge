/** Reads mail from the local Mailpit inbox (docker-compose service "mail"). */
const MAILPIT = "http://localhost:8025";

type Summary = { ID: string };

/** `ID` is Mailpit's own; `MessageID` is the email's Message-ID header, without the angle brackets. */
export type Mail = { ID: string; MessageID: string; Subject: string; Text: string };

/** The newest message to this address, waiting up to `timeoutMs` for one that passes `accept`. */
export async function latestMessage(
  email: string,
  { timeoutMs = 10_000, accept = () => true }: { timeoutMs?: number; accept?: (mail: Mail) => boolean } = {},
): Promise<Mail> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const search = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
    const { messages = [] } = (await search.json()) as { messages?: Summary[] };
    if (messages.length > 0) {
      const mail = (await (await fetch(`${MAILPIT}/api/v1/message/${messages[0].ID}`)).json()) as Mail;
      if (accept(mail)) return mail;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`No matching email arrived for ${email}`);
}

/** How many messages Mailpit holds for this address. */
export async function messageCount(email: string): Promise<number> {
  const search = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
  const { messages_count = 0 } = (await search.json()) as { messages_count?: number };
  return messages_count;
}

/** A message's raw headers, each name mapped to its values. */
export async function headersOf(mail: Mail): Promise<Record<string, string[]>> {
  return (await (await fetch(`${MAILPIT}/api/v1/message/${mail.ID}/headers`)).json()) as Record<string, string[]>;
}

/** The six-digit sign-in code in the newest email to this address. */
export async function latestCode(email: string, { notIn = [] as string[] } = {}): Promise<string> {
  const mail = await latestMessage(email, {
    accept: (candidate) => {
      const code = candidate.Text.match(/\b(\d{6})\b/)?.[1];
      return Boolean(code && !notIn.includes(code));
    },
  });
  return mail.Text.match(/\b(\d{6})\b/)![1];
}
