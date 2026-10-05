import type { ReactNode } from "react";
import WebsiteShell from "./WebsiteShell";
export default function PolicyLayout({ title, intro, children }: { title: string; intro: string; children: ReactNode }) {
  return <WebsiteShell><div className="company-container company-policy"><aside><p className="company-kicker">Clear, useful small print</p><h1>{title}</h1><p>{intro}</p><p className="company-policy-date">Last updated: 5 October 2026</p><nav aria-label="Policies"><a href="/privacy">Privacy & your data</a><a href="/terms">Terms of use</a><a href="/cookies">Cookies & device storage</a><a href="/community-rules">Community rules</a><a href="/support">Help & contact</a></nav></aside><article className="company-policy-content">{children}</article></div></WebsiteShell>;
}
