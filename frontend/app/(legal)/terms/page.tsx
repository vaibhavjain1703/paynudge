import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "Terms" };

export default function TermsPage() {
  return (
    <>
      <h1>Terms</h1>
      <p>Last updated 26 October 2026.</p>
      <p>
        PayNudge is an early version offered as is, free of charge, without any warranty. Payments run in
        Razorpay&apos;s test mode, so no real money moves.
      </p>

      <h2>Using PayNudge</h2>
      <ul>
        <li>Add only customers who owe your business money and expect to hear from you about it.</li>
        <li>Do not use PayNudge for spam, threats or anything unlawful. Unsubscribes are always honoured.</li>
        <li>
          Requests are rate limited, and accounts used for abuse may be removed. Demo businesses are deleted a day after
          they are made.
        </li>
      </ul>

      <h2>Your data</h2>
      <p>
        What you add stays yours; the{" "}
        <Link href="/privacy" className="font-medium text-primary underline-offset-4 hover:underline">
          privacy page
        </Link>{" "}
        says what is stored and who handles it. While
        PayNudge is in development, data may occasionally need to be reset; keep your own copy of anything important.
      </p>

      <h2>Changes</h2>
      <p>These terms may change as PayNudge grows; the date above shows the latest version.</p>
    </>
  );
}
