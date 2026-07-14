import { SignupForm } from "@/components/SignupForm";
import { CairnMark } from "@/components/CairnMark";

export default function SignupPage() {
  return (
    <div className="contour-bg px-4 py-16 max-w-sm mx-auto flex flex-col items-center">
      <CairnMark size={32} className="text-route mb-3" />
      <h1 className="text-xl font-display font-bold text-ink mb-4">Create your account</h1>
      <SignupForm />
    </div>
  );
}
