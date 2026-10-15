import { Wordmark } from "@/components/brand";
import { ThemeToggle } from "@/components/theme-toggle";

/** Pages people reach from a link in an email, without an account. */
export default function PublicLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-svh flex-col">
      <header className="flex items-center justify-between px-4 py-4 sm:px-6">
        <Wordmark />
        <ThemeToggle />
      </header>
      <main className="flex flex-1 items-start justify-center px-4 pt-[8vh] pb-16 sm:items-center sm:pt-0">
        {children}
      </main>
    </div>
  );
}
