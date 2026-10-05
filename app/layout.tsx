import type { Metadata, Viewport } from "next";
import "./globals.css";
import "./photo-ui.css";
export const metadata: Metadata = {
  title: "Field Logger · A world worth noticing",
  description:
    "Your personal countryside discovery journal. Photograph, discover and remember the interesting things along the way.",
  manifest: "/manifest.webmanifest",
  icons: { icon: "/favicon.svg", apple: "/icon-192.png" },
  appleWebApp: {
    capable: true,
    title: "Field Logger",
    statusBarStyle: "default",
  },
};
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#173f35",
  viewportFit: "cover",
};
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-GB">
      <body>{children}</body>
    </html>
  );
}
