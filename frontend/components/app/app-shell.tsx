"use client";

import { LogOut } from "lucide-react";
import { motion } from "motion/react";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { Wordmark } from "@/components/brand";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useLogout, useMe } from "@/lib/auth";
import { easeOut } from "@/lib/motion";
import { useToken } from "@/lib/session";

/** Everything behind sign-in. Without a token (or once the API rejects it) the visitor goes to /login. */
export function AppShell({ children }: { children: React.ReactNode }) {
  const token = useToken();
  const router = useRouter();

  useEffect(() => {
    if (token === null) router.replace("/login");
  }, [token, router]);

  return (
    <div className="flex min-h-svh flex-col">
      <header className="sticky top-0 z-10 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <Wordmark href="/dashboard" />
            <span className="text-muted-foreground" aria-hidden="true">
              /
            </span>
            <BusinessName />
          </div>
          <div className="flex items-center gap-1">
            <ThemeToggle />
            {token && <LogoutButton />}
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6">
        {token ? children : <PageSkeleton />}
      </main>
    </div>
  );
}

function BusinessName() {
  const me = useMe();
  if (me.data) {
    return (
      <motion.span
        initial={{ opacity: 0, x: -4 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.25, ease: easeOut }}
        className="truncate text-sm font-medium"
      >
        {me.data.business.name}
      </motion.span>
    );
  }
  return <Skeleton className="h-4 w-28" />;
}

function LogoutButton() {
  const logout = useLogout();
  return (
    <Button variant="ghost" size="sm" onClick={logout} aria-label="Log out">
      <LogOut aria-hidden="true" />
      <span className="hidden sm:inline">Log out</span>
    </Button>
  );
}

export function PageSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true" aria-label="Loading">
      <Skeleton className="h-8 w-56" />
      <div className="grid gap-4 sm:grid-cols-3">
        <Skeleton className="h-28" />
        <Skeleton className="h-28" />
        <Skeleton className="h-28" />
      </div>
      <Skeleton className="h-64" />
    </div>
  );
}
