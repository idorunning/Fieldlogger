import { ArrowUpRight, Leaf } from "lucide-react";
import type { ReactNode } from "react";
import "../company-site.css";

export default function WebsiteShell({ children }: { children: ReactNode }) {
  return <div className="company-site">
    <a href="#company-main" className="company-skip">Skip to content</a>
    <header className="company-header company-container"><a href="/" className="company-brand" aria-label="My Trail Log home"><Leaf aria-hidden="true"/><span>My Trail Log</span></a><nav aria-label="Main navigation"><a href="/#how-it-works">The app</a><a href="/pricing">Plans</a><a href="/#about">About</a><a href="/support">Help</a></nav><a href="/journal" className="company-button company-button-primary company-header-journal">My journal <ArrowUpRight size={17}/></a></header>
    <main id="company-main">{children}</main>
    <footer className="company-footer"><div className="company-container company-footer-top"><div><a href="/" className="company-brand"><Leaf aria-hidden="true"/><span>My Trail Log</span></a><p>A little world worth noticing.</p></div><nav aria-label="Help and policies"><a href="/support">Support</a><a href="/privacy">Privacy & data</a><a href="/terms">Terms</a><a href="/cookies">Cookies & device storage</a><a href="/community-rules">Community rules</a><a href="/delete-account">Delete my account</a></nav><div className="company-footer-contact"><a href="mailto:ntracey@gmail.com">ntracey@gmail.com</a><a href="/admin">Member administration <ArrowUpRight size={14}/></a></div></div><div className="company-container company-footer-small"><p>© {new Date().getUTCFullYear()} My Trail Log · Operated by Nathan Tracey</p><p>Thinking About Ltd is a proposed name; the company is not yet incorporated.</p><p>Postal contact: 422 Milton Road, Waterlooville, PO8 8LD, United Kingdom.</p><p>Woodland and wildlife photographs: Unsplash. <a href="/support#credits">Photo & data credits</a></p></div></footer>
  </div>;
}
