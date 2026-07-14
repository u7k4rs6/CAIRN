import { LoginForm } from "@/components/LoginForm";
import { CairnMark } from "@/components/CairnMark";

export default function LoginPage() {
  return (
    <div className="contour-bg px-4 py-16 max-w-sm mx-auto flex flex-col items-center">
      <CairnMark size={32} className="text-route mb-3" />
      <h1 className="text-xl font-display font-bold text-ink mb-4">Sign in</h1>
      <LoginForm />
    </div>
  );
}
