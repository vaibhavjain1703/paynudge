import type { Metadata } from "next";

export const metadata: Metadata = { title: "Privacy" };

export default function PrivacyPage() {
  return (
    <>
      <h1>Privacy</h1>
      <p>Last updated 26 October 2026.</p>
      <p>
        PayNudge sends payment reminders for small businesses. It is an early version: payments run in Razorpay&apos;s
        test mode, so no real money moves, and on this site reminders to customers are kept inside PayNudge instead of
        being emailed.
      </p>

      <h2>What PayNudge stores</h2>
      <ul>
        <li>
          For business owners: your email address, your business name and settings, and a hash of your password if you
          set one. Sign-in codes are stored only as hashes and expire after 10 minutes.
        </li>
        <li>
          For your customers: the names, email addresses and phone numbers you add, what they owe and when, the
          reminders PayNudge writes for them, their payments, and whether they unsubscribed.
        </li>
      </ul>

      <h2>How it is used</h2>
      <p>
        Only to schedule and write reminders, take payments and show you your dashboard. PayNudge does not sell data,
        show ads or use tracking cookies.
      </p>

      <h2>Who else handles it</h2>
      <ul>
        <li>Supabase hosts the database and Render runs the API, both in Singapore. Vercel serves the web app.</li>
        <li>Razorpay handles payments (in test mode here). PayNudge never sees card or bank details.</li>
        <li>Brevo delivers sign-in codes to owners. Google sign-in shares your email address and name with PayNudge.</li>
      </ul>

      <h2>In your browser</h2>
      <p>
        Your session (it lasts two hours) and your light or dark preference are kept in your browser&apos;s local
        storage. Nothing else is stored there.
      </p>

      <h2>Demo businesses</h2>
      <p>&quot;Try the demo&quot; creates a sample business with made-up customers. It is deleted a day later.</p>

      <h2>Your choices</h2>
      <ul>
        <li>Every reminder carries an unsubscribe link; after it is used, that customer gets no more reminders.</li>
        <li>Deleting a customer deletes their dues, reminders and payments.</li>
        <li>
          To delete your account, or for any question, open an issue on{" "}
          <a
            href="https://github.com/vaibhavjain1703/paynudge/issues"
            className="font-medium text-primary underline-offset-4 hover:underline"
          >
            GitHub
          </a>
          .
        </li>
      </ul>
    </>
  );
}
