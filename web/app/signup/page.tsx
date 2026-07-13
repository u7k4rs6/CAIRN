import { SignupForm } from "@/components/SignupForm";

export default function SignupPage() {
  return (
    <div className="px-4 py-16 max-w-sm mx-auto">
      <h1 className="text-xl font-semibold mb-4">Create your account</h1>
      <SignupForm />
    </div>
  );
}
