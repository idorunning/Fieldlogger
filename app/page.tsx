import AuthProvider from "./components/AuthProvider";
import Fieldnotes from "./components/Fieldnotes";
export default function Page() {
  return (
    <AuthProvider>
      <Fieldnotes />
    </AuthProvider>
  );
}
