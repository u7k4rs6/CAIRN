import { LoginForm } from "@/components/LoginForm";

export default function LoginPage() {
  return (
    <div className="px-4 py-16 max-w-sm mx-auto">
      <h1 className="text-xl font-semibold mb-4">Sign in</h1>
      <LoginForm />
    </div>
  );
}
