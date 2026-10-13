/** Reads sign-in codes from the local Mailpit inbox (docker-compose service "mail"). */
const MAILPIT = "http://localhost:8025";

type Summary = { ID: string };

export async function latestCode(email: string, { notIn = [] as string[] } = {}): Promise<string> {
  for (let attempt = 0; attempt < 40; attempt++) {
    const search = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
    const { messages = [] } = (await search.json()) as { messages?: Summary[] };
    if (messages.length > 0) {
      const message = (await (await fetch(`${MAILPIT}/api/v1/message/${messages[0].ID}`)).json()) as {
        Text: string;
      };
      const code = message.Text.match(/\b(\d{6})\b/)?.[1];
      if (code && !notIn.includes(code)) return code;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`No sign-in code arrived for ${email}`);
}
