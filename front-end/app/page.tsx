import { redirect } from "next/navigation";
import { getSession } from "@/src/lib/session";

export default async function Home() {
  redirect((await getSession()) ? "/dashboard" : "/login");
}