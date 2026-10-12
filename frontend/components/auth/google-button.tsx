"use client";

import Script from "next/script";
import { useEffect, useRef, useState } from "react";

type CredentialResponse = { credential: string };

type GoogleIdentity = {
  accounts: {
    id: {
      initialize(options: {
        client_id: string;
        callback: (response: CredentialResponse) => void;
        ux_mode?: "popup" | "redirect";
        context?: "signin" | "signup" | "use";
      }): void;
      renderButton(
        parent: HTMLElement,
        options: {
          type?: "standard" | "icon";
          theme?: "outline" | "filled_blue" | "filled_black";
          size?: "large" | "medium" | "small";
          text?: "signin_with" | "signup_with" | "continue_with" | "signin";
          shape?: "rectangular" | "pill" | "circle" | "square";
          logo_alignment?: "left" | "center";
          width?: number;
        },
      ): void;
    };
  };
};

declare global {
  interface Window {
    google?: GoogleIdentity;
  }
}

/**
 * Google's own "Continue with Google" button (Google Identity Services). Google hands the browser an ID token,
 * which the API verifies; we never see the user's Google password.
 */
export function GoogleButton({
  clientId,
  onCredential,
}: {
  clientId: string;
  onCredential: (idToken: string) => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const callback = useRef(onCredential);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    callback.current = onCredential;
  }, [onCredential]);

  useEffect(() => {
    const google = window.google;
    const parent = container.current;
    if (!loaded || !google || !parent) return;
    google.accounts.id.initialize({
      client_id: clientId,
      callback: (response) => callback.current(response.credential),
      ux_mode: "popup",
      context: "use",
    });
    google.accounts.id.renderButton(parent, {
      type: "standard",
      theme: document.documentElement.classList.contains("dark") ? "filled_black" : "outline",
      size: "large",
      text: "continue_with",
      shape: "pill",
      logo_alignment: "center",
      width: Math.min(400, parent.clientWidth),
    });
  }, [loaded, clientId]);

  return (
    <>
      <Script src="https://accounts.google.com/gsi/client" strategy="afterInteractive" onReady={() => setLoaded(true)} />
      <div ref={container} className="flex h-10 w-full justify-center" data-testid="google-button" />
    </>
  );
}
