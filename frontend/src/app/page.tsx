import Link from "next/link";
import {
  MessageSquare,
  Workflow,
  LineChart,
  Plane,
  Puzzle,
  Users,
  Clock,
  ShieldCheck,
  ArrowRight,
} from "lucide-react";
import { ThemeToggle } from "@/components/ui/ThemeToggle";

const features = [
  {
    icon: MessageSquare,
    title: "RAG-powered chat",
    description: "Ask questions grounded in your own documents, URLs, and Notion pages via a Weaviate-backed knowledge base.",
  },
  {
    icon: Workflow,
    title: "Multi-agent workflows",
    description: "Compose and run multi-step agent graphs visually, with configurable patterns and full run history.",
  },
  {
    icon: LineChart,
    title: "Financial tracking & alerts",
    description: "Monitor crypto, stocks, and DeFi positions with real-time price alerts delivered as they happen.",
  },
  {
    icon: Plane,
    title: "Travel planning",
    description: "Plan trips with an agent that maps itineraries and keeps details organized in one place.",
  },
  {
    icon: Puzzle,
    title: "Skills & MCP connectors",
    description: "Extend the agent with custom skills and Model Context Protocol tool integrations.",
  },
  {
    icon: Clock,
    title: "Scheduled tasks",
    description: "Run recurring workflows and alerts on a schedule, backed by a durable task queue.",
  },
  {
    icon: Users,
    title: "Team workspaces",
    description: "Share a knowledge base, workflows, and skills with your org under a single admin-managed workspace.",
  },
  {
    icon: ShieldCheck,
    title: "Secure by default",
    description: "Passkey and one-time-code authentication, with per-request authorization across every service.",
  },
];

export default function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col bg-[--color-surface]">
      {/* Header */}
      <header className="flex items-center justify-between gap-2 px-4 py-4 sm:px-10 sm:py-5">
        <div className="flex items-center">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo-black-front-no-background.svg" alt="SkyProton" className="block h-12 w-auto dark:hidden sm:h-16 md:h-25" />
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo-white-front-no-background.svg" alt="SkyProton" className="hidden h-12 w-auto dark:block sm:h-16 md:h-25" />
        </div>
        <div className="flex items-center gap-1 sm:gap-3">
          <ThemeToggle />
          <Link
            href="/login"
            className="rounded-lg px-2.5 py-2 text-sm font-medium text-[--color-muted] transition-colors hover:text-current sm:px-3.5"
          >
            Sign in
          </Link>
          <Link
            href="/register"
            className="rounded-lg bg-black px-2.5 py-2 text-sm font-medium text-white transition-opacity hover:opacity-90 dark:bg-white dark:text-black sm:px-3.5"
          >
            Get started
          </Link>
        </div>
      </header>

      {/* Hero */}
      <section className="mx-auto flex w-full max-w-3xl flex-col items-center px-6 pb-12 pt-6 text-center sm:pb-16 sm:pt-20">
        <h1 className="text-3xl font-semibold tracking-tight sm:text-5xl">
          One agent system for your knowledge, workflows, finance, and travel.
        </h1>
        <p className="mt-5 max-w-xl text-balance text-base text-[--color-muted] sm:text-lg">
          SkyProton Agent System combines retrieval-augmented chat, multi-agent workflows,
          financial, and travel monitoring in a single workspace — built to extend with your own
          skills and tools.
        </p>
        <div className="mt-8 flex w-full max-w-xs flex-col gap-3 sm:max-w-none sm:w-auto sm:flex-row sm:flex-wrap sm:items-center sm:justify-center">
          <Link
            href="/register"
            className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-black px-5 py-2.5 text-sm font-medium text-white transition-opacity hover:opacity-90 dark:bg-white dark:text-black sm:w-auto"
          >
            Get started <ArrowRight className="h-4 w-4" />
          </Link>
          <Link
            href="/login"
            className="inline-flex w-full items-center justify-center gap-2 rounded-lg border border-[--color-border] px-5 py-2.5 text-sm font-medium transition-colors hover:bg-[--color-surface-raised] sm:w-auto"
          >
            Sign in
          </Link>
        </div>
      </section>

      {/* Feature grid */}
      <section className="mx-auto w-full max-w-5xl px-6 pb-20">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {features.map(({ icon: Icon, title, description }) => (
            <div
              key={title}
              className="rounded-2xl border border-[--color-border] bg-[--color-surface-raised] p-5"
            >
              <div className="mb-3 flex h-9 w-9 items-center justify-center rounded-lg bg-black dark:bg-white">
                <Icon className="h-4.5 w-4.5 text-white dark:text-black" />
              </div>
              <h3 className="text-sm font-semibold">{title}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-[--color-muted]">{description}</p>
            </div>
          ))}
        </div>
      </section>

      <footer className="mt-auto px-6 pb-8 text-center text-xs text-[--color-muted]">
        &copy; {new Date().getFullYear()} SkyProton
      </footer>
    </div>
  );
}
