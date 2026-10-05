import type { Metadata, Viewport } from "next";
import { phoneLayoutScript } from "../lib/phone-layout";
import "./globals.css";
import "./photo-ui.css";
import "./trail-brand.css";
export const metadata: Metadata = {
  title: "My Trail Log · Your nature photo journal",
  description:
    "Your personal countryside discovery journal. Photograph, discover and remember the interesting things along the way.",
  manifest: "/manifest.webmanifest",
  icons: { icon: "/favicon.svg", apple: "/icon-192.png" },
  appleWebApp: {
    capable: true,
    title: "My Trail Log",
    statusBarStyle: "default",
  },
};
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#154e45",
  viewportFit: "cover",
};
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-GB" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: phoneLayoutScript }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
