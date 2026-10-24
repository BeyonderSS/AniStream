"use client";
import { useParams } from "next/navigation";
import { Notice } from "@/components/app";
import { SECTIONS } from "@/components/admin";

export default function AdminPage() {
  const { section } = useParams<{ section?: string[] }>();
  const View = SECTIONS[section?.[0] ?? ""];
  return View ? <View /> : <Notice title="No such admin page" body="Pick a page from the sidebar." />;
}
