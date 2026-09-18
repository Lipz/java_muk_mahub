import { redirect } from "next/navigation";
import { getSession } from "@/src/lib/session";
import LoginForm from "./login-form";

export const metadata = { title: "Sign in" };

export default async function LoginPage() {
  if (await getSession()) redirect("/dashboard");
  return <LoginForm />;
}