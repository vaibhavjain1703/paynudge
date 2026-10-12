import type { Metadata } from "next";
import { Geist, JetBrains_Mono } from "next/font/google";

import { Providers } from "./providers";
import "./globals.css";

const sans = Geist({
  variable: "--font-sans",
  subsets: ["latin"],
});

const mono = JetBrains_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: {
    default: "PayNudge",
    template: "%s · PayNudge",
  },
  description:
    "Import your dues, pick a reminder policy once, and PayNudge follows up until every customer pays.",
};

// Dark-first: the server renders .dark and this runs before paint to honour a saved light preference.
const themeScript = `(function(){try{if(localStorage.getItem("paynudge.theme")==="light")document.documentElement.classList.remove("dark")}catch(e){}})()`;

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`dark ${sans.variable} ${mono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className="flex min-h-full flex-col">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
