"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";

import { api } from "@/lib/api";
import { type Business, businessNameRule, type Me, queryKeys } from "@/lib/auth";

const time = z.string().regex(/^\d{2}:\d{2}$/, "Pick a time");

// Mirrors UpdateBusinessRequest.java and BusinessService.java; the server stays the source of truth.
export const businessSettingsSchema = z
  .object({
    name: businessNameRule,
    senderName: z.string().trim().max(100, "Keep it under 100 characters"),
    timezone: z.string().min(1, "Pick a timezone"),
    quietStart: time,
    quietEnd: time,
    dailySendCap: z
      .string()
      .trim()
      .regex(/^\d+$/, "Enter a whole number")
      .transform(Number)
      .pipe(z.number().min(1, "Send at least 1 a day").max(500, "The limit is 500 a day")),
  })
  .refine((settings) => settings.quietStart !== settings.quietEnd, {
    path: ["quietEnd"],
    message: "Quiet hours need different start and end times",
  });

export type BusinessSettingsInput = z.input<typeof businessSettingsSchema>;
export type BusinessSettings = z.output<typeof businessSettingsSchema>;

/** The form's starting values. The API sends times as HH:mm:ss; the time inputs use HH:mm. */
export function settingsFormValues(business: Business): BusinessSettingsInput {
  return {
    name: business.name,
    senderName: business.senderName ?? "",
    timezone: business.timezone,
    quietStart: business.quietStart.slice(0, 5),
    quietEnd: business.quietEnd.slice(0, 5),
    dailySendCap: String(business.dailySendCap),
  };
}

export function useUpdateBusiness() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (settings: BusinessSettings) =>
      api<Business>("/api/business", { method: "PUT", body: settings }),
    onSuccess: (business) => {
      queryClient.setQueryData<Me>(queryKeys.me, (me) => (me ? { ...me, business } : me));
    },
  });
}
