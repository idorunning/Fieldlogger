import WebsiteShell from "../components/WebsiteShell";
import AdminConsole from "../components/AdminConsole";
export const metadata = { title: "Member administration · My Trail Log", robots: { index: false, follow: false } };
export const dynamic = "force-dynamic";
export default function AdminPage() { return <WebsiteShell><AdminConsole/></WebsiteShell>; }
