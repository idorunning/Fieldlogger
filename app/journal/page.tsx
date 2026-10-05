import AuthProvider from "../components/AuthProvider";
import Fieldnotes from "../components/Fieldnotes";
export const metadata = { title: "Your journal · My Trail Log" };
export default function JournalPage() {
  return <AuthProvider><Fieldnotes /></AuthProvider>;
}
