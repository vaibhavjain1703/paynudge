"use client";

import { motion } from "motion/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { useToken } from "@/lib/session";

/** The card both auth forms sit in. Signed-in visitors are sent straight to the dashboard. */
export function AuthCard({
  title,
  description,
  footer,
  children,
}: {
  title: string;
  description: string;
  footer: { prompt: string; href: string; label: string };
  children: React.ReactNode;
}) {
  const token = useToken();
  const router = useRouter();

  useEffect(() => {
    if (token) router.replace("/dashboard");
  }, [token, router]);

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, ease: "easeOut" }}
      className="w-full max-w-sm"
    >
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent>{children}</CardContent>
        <CardFooter className="justify-center border-t py-4 text-sm text-muted-foreground">
          {footer.prompt}&nbsp;
          <Link href={footer.href} className="font-medium text-foreground underline-offset-4 hover:underline">
            {footer.label}
          </Link>
        </CardFooter>
      </Card>
    </motion.div>
  );
}
