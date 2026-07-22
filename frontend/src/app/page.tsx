"use client";

import { useEffect, useRef, useState } from "react";
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
  FileText,
  Globe,
  TrendingUp,
  Bell,
  TrainFront,
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

const heroTabs = [
  { id: "chat", icon: MessageSquare, label: "Chat" },
  { id: "workflow", icon: Workflow, label: "Workflows" },
  { id: "finance", icon: LineChart, label: "Finance" },
  { id: "travel", icon: Plane, label: "Travel" },
] as const;

type HeroTab = (typeof heroTabs)[number]["id"];

const graphEdges: [string, string][] = [
  ["retrieve", "plan"],
  ["plan", "toolA"],
  ["toolA", "respond"],
  ["toolA", "toolB"],
  ["toolB", "respond"],
];

function GraphNode({
  id,
  nodeRef,
  children,
}: {
  id: string;
  nodeRef: (id: string, el: HTMLDivElement | null) => void;
  children: React.ReactNode;
}) {
  return (
    <div
      ref={(el) => nodeRef(id, el)}
      className="rounded-lg border border-[--color-border] bg-[--color-surface] px-2 py-1.5 text-center text-[10px] font-medium leading-tight sm:px-3 sm:py-2 sm:text-xs"
    >
      {children}
    </div>
  );
}

function WorkflowGraph() {
  const containerRef = useRef<HTMLDivElement>(null);
  const nodeEls = useRef<Record<string, HTMLDivElement | null>>({});
  const [edges, setEdges] = useState<{ id: string; d: string; x2: number; y2: number }[]>([]);

  const setNodeRef = (id: string, el: HTMLDivElement | null) => {
    nodeEls.current[id] = el;
  };

  useEffect(() => {
    let cancelled = false;

    function measure() {
      const container = containerRef.current;
      if (!container) return;
      const containerBox = container.getBoundingClientRect();

      const next = graphEdges.flatMap(([fromId, toId]) => {
        const fromEl = nodeEls.current[fromId];
        const toEl = nodeEls.current[toId];
        if (!fromEl || !toEl) return [];
        const fromBox = fromEl.getBoundingClientRect();
        const toBox = toEl.getBoundingClientRect();
        const x1 = fromBox.right - containerBox.left;
        const y1 = fromBox.top + fromBox.height / 2 - containerBox.top;
        const x2 = toBox.left - containerBox.left;
        const y2 = toBox.top + toBox.height / 2 - containerBox.top;
        const dx = Math.max((x2 - x1) / 2, 12);
        return [
          {
            id: `${fromId}-${toId}`,
            d: `M${x1},${y1} C${x1 + dx},${y1} ${x2 - dx},${y2} ${x2},${y2}`,
            x2,
            y2,
          },
        ];
      });
      if (!cancelled) setEdges(next);
    }

    // Measure now, then again on the next couple of frames — the tab's own
    // entrance transition (transform/opacity) and web-font swap-in can both
    // shift node positions slightly after the first paint.
    measure();
    const raf1 = requestAnimationFrame(() => {
      measure();
      requestAnimationFrame(measure);
    });
    if (typeof document !== "undefined" && "fonts" in document) {
      document.fonts.ready.then(() => !cancelled && measure());
    }

    const ro = new ResizeObserver(measure);
    if (containerRef.current) ro.observe(containerRef.current);
    Object.values(nodeEls.current).forEach((el) => el && ro.observe(el));

    return () => {
      cancelled = true;
      cancelAnimationFrame(raf1);
      ro.disconnect();
    };
  }, []);

  return (
    <div
      ref={containerRef}
      className="workflow-dot-grid relative grid min-h-[130px] grid-cols-5 items-center gap-x-2 rounded-xl py-4 sm:min-h-[150px] sm:gap-x-4"
    >
      <svg className="pointer-events-none absolute inset-0 h-full w-full" aria-hidden="true">
        <defs>
          <marker id="graphArrow" markerWidth="6" markerHeight="6" refX="4.5" refY="3" orient="auto" markerUnits="userSpaceOnUse">
            <path d="M0,0 L6,3 L0,6 Z" style={{ fill: "var(--color-muted)" }} />
          </marker>
        </defs>
        {edges.map((edge) => (
          <path
            key={edge.id}
            d={edge.d}
            fill="none"
            strokeWidth={1.5}
            strokeOpacity={0.55}
            style={{ stroke: "var(--color-muted)" }}
            markerEnd="url(#graphArrow)"
          />
        ))}
        {edges.map((edge) => (
          <circle key={`${edge.id}-dot`} cx={edge.x2} cy={edge.y2} r={2} style={{ fill: "var(--color-muted)" }} />
        ))}
      </svg>

      <div className="relative z-10 flex justify-center">
        <GraphNode id="retrieve" nodeRef={setNodeRef}>Start</GraphNode>
      </div>
      <div className="relative z-10 flex justify-center">
        <GraphNode id="plan" nodeRef={setNodeRef}>Agent</GraphNode>
      </div>
      <div className="relative z-10 flex justify-center">
        <GraphNode id="toolA" nodeRef={setNodeRef}>Condition</GraphNode>
      </div>
      <div className="relative z-10 flex justify-center self-end pb-1">
        <GraphNode id="toolB" nodeRef={setNodeRef}>Agent</GraphNode>
      </div>
      <div className="relative z-10 flex justify-center">
        <GraphNode id="respond" nodeRef={setNodeRef}>End</GraphNode>
      </div>
    </div>
  );
}

export default function LandingPage() {
  const [activeHeroTab, setActiveHeroTab] = useState<HeroTab>("chat");

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
      <section className="relative overflow-hidden">
        {/* Background texture: soft gradient glow + faint dot grid */}
        <div className="pointer-events-none absolute inset-0 -z-10">
          <div
            className="absolute left-1/2 top-[-16%] h-[40rem] w-[64rem] -translate-x-1/2 rounded-full blur-2xl"
            style={{
              background:
                "radial-gradient(closest-side, var(--color-glow-a), transparent 72%)",
            }}
          />
          <div
            className="absolute left-1/2 top-[2%] h-[26rem] w-[44rem] -translate-x-[78%] rounded-full blur-2xl"
            style={{
              background:
                "radial-gradient(closest-side, var(--color-glow-b), transparent 72%)",
            }}
          />
          <div className="hero-dot-grid absolute inset-0" />
        </div>

        <div className="mx-auto flex w-full max-w-6xl flex-col items-center px-6 pb-16 pt-8 text-center sm:pb-20 sm:pt-16">
          <h1 className="max-w-4xl text-4xl font-semibold tracking-tight sm:text-6xl lg:text-7xl">
            One agent system for your knowledge, workflows, finance, and travel.
          </h1>
          <p className="mt-6 max-w-2xl text-balance text-lg text-[--color-muted] sm:text-xl">
            SkyProton Agent System combines retrieval-augmented chat, multi-agent workflows,
            financial, and travel monitoring in a single workspace — built to extend with your own
            skills and tools.
          </p>
          <div className="mt-9 flex w-full max-w-xs flex-col gap-3 sm:max-w-none sm:w-auto sm:flex-row sm:flex-wrap sm:items-center sm:justify-center">
            <Link
              href="/register"
              className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-black px-6 py-3 text-sm font-medium text-white transition-opacity hover:opacity-90 dark:bg-white dark:text-black sm:w-auto"
            >
              Get started <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              href="/login"
              className="inline-flex w-full items-center justify-center gap-2 rounded-lg border border-[--color-border] px-6 py-3 text-sm font-medium transition-colors hover:bg-[--color-surface-raised] sm:w-auto"
            >
              Sign in
            </Link>
          </div>

          {/* Product preview mockup */}
          <div className="hero-mockup-in mt-16 w-full max-w-5xl overflow-hidden rounded-2xl border border-[--color-border] bg-[--color-surface] text-left shadow-2xl shadow-black/10 dark:shadow-black/40">
            {/* Window chrome */}
            <div className="flex h-10 items-center gap-2 border-b border-[--color-border] bg-[--color-surface-raised] px-4">
              <span className="h-2.5 w-2.5 rounded-full bg-red-400/70" />
              <span className="h-2.5 w-2.5 rounded-full bg-amber-400/70" />
              <span className="h-2.5 w-2.5 rounded-full bg-emerald-400/70" />
              <span className="ml-3 text-xs font-medium text-[--color-muted]">
                SkyProton — Agent Workspace
              </span>
            </div>

            <div className="grid grid-cols-[3.25rem_1fr]">
              {/* Mini sidebar */}
              <div className="flex flex-col items-center gap-3 border-r border-[--color-border] bg-[--color-surface-raised] py-4">
                {heroTabs.map(({ id, icon: Icon, label }) => (
                  <button
                    key={id}
                    type="button"
                    onClick={() => setActiveHeroTab(id)}
                    aria-label={label}
                    aria-pressed={activeHeroTab === id}
                    className={`flex h-8 w-8 items-center justify-center rounded-lg transition-colors ${
                      activeHeroTab === id
                        ? "bg-black text-white dark:bg-white dark:text-black"
                        : "text-[--color-muted] hover:bg-[--color-border]/40 hover:text-current"
                    }`}
                  >
                    <Icon className="h-4 w-4" />
                  </button>
                ))}
              </div>

              {/* Tab content */}
              <div key={activeHeroTab} className="tab-fade-in flex min-h-[210px] flex-col gap-4 p-5 sm:p-6">
                {activeHeroTab === "chat" && (
                  <>
                    <div className="ml-auto max-w-[75%] rounded-2xl rounded-br-sm bg-black px-4 py-2.5 text-sm text-white dark:bg-white dark:text-black">
                      What&apos;s my portfolio exposure to ETH this week, and how much have I spent
                      on the Tokyo trip so far?
                    </div>
                    <div className="max-w-[85%] rounded-2xl rounded-bl-sm border border-[--color-border] bg-[--color-surface-raised] px-4 py-3 text-sm leading-relaxed">
                      Your ETH exposure is up 4.2% since Monday across two wallets. The Tokyo trip
                      is at $1,240 so far, with $32 in cashback tracked.
                      <div className="mt-2.5 flex flex-wrap gap-1.5">
                        <span className="rounded-full border border-[--color-border] px-2 py-0.5 text-[11px] text-[--color-muted]">
                          Weaviate KB
                        </span>
                        <span className="rounded-full border border-[--color-border] px-2 py-0.5 text-[11px] text-[--color-muted]">
                          Price alert task
                        </span>
                      </div>
                    </div>

                    <div className="mt-1 grid grid-cols-3 gap-2 sm:gap-3">
                      <div className="rounded-xl border border-[--color-border] p-2 sm:p-3">
                        <p className="text-[10px] text-[--color-muted] sm:text-[11px]">ETH exposure</p>
                        <p className="mt-1 text-xs font-semibold text-emerald-500 sm:text-sm">+4.2%</p>
                      </div>
                      <div className="rounded-xl border border-[--color-border] p-2 sm:p-3">
                        <p className="text-[10px] text-[--color-muted] sm:text-[11px]">Workflow run</p>
                        <p className="mt-1 text-xs font-semibold sm:text-sm">3 agents</p>
                      </div>
                      <div className="rounded-xl border border-[--color-border] p-2 sm:p-3">
                        <p className="text-[10px] text-[--color-muted] sm:text-[11px]">Next trip</p>
                        <p className="mt-1 text-xs font-semibold sm:text-sm">Tokyo · Aug 12</p>
                      </div>
                    </div>
                  </>
                )}

                {activeHeroTab === "workflow" && (
                  <div className="flex flex-1 flex-col justify-center">
                    <WorkflowGraph />
                    <p className="mt-3 text-xs text-[--color-muted]">Run history · 5/5 steps completed · 8.4s</p>
                  </div>
                )}

                {activeHeroTab === "finance" && (
                  <div className="flex flex-1 flex-col justify-center gap-3">
                    <div className="rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-4">
                      <p className="text-[11px] text-[--color-muted]">Portfolio value</p>
                      <div className="mt-1 flex items-baseline gap-2">
                        <span className="text-2xl font-semibold">$48,216.30</span>
                        <span className="flex items-center gap-0.5 text-xs font-medium text-emerald-500">
                          <TrendingUp className="h-3 w-3" /> +4.2%
                        </span>
                      </div>
                      <svg viewBox="0 0 300 60" className="mt-2 h-12 w-full" aria-hidden="true">
                        <defs>
                          <linearGradient id="financeSparkFill" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor="#10b981" stopOpacity="0.25" />
                            <stop offset="100%" stopColor="#10b981" stopOpacity="0" />
                          </linearGradient>
                        </defs>
                        <path
                          d="M0,45 L40,42 L80,48 L120,30 L160,34 L200,18 L240,22 L300,6 L300,60 L0,60 Z"
                          fill="url(#financeSparkFill)"
                        />
                        <path
                          d="M0,45 L40,42 L80,48 L120,30 L160,34 L200,18 L240,22 L300,6"
                          fill="none"
                          stroke="#10b981"
                          strokeWidth="2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />
                      </svg>
                      <div className="mt-2 flex gap-4 text-[11px] text-[--color-muted]">
                        <span>Cash · 18%</span>
                        <span>Stocks · 42%</span>
                        <span>Crypto · 40%</span>
                      </div>
                    </div>
                    <div className="flex items-center justify-between rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-4 py-3">
                      <div className="flex items-center gap-2">
                        <Bell className="h-4 w-4 text-[--color-muted]" />
                        <span className="text-sm font-medium">ETH crossed $3,800</span>
                      </div>
                      <span className="text-xs text-[--color-muted]">2m ago</span>
                    </div>
                  </div>
                )}

                {activeHeroTab === "travel" && (
                  <div className="flex flex-1 flex-col justify-center gap-3">
                    <div className="relative h-24 w-full overflow-hidden rounded-xl border border-[--color-border] bg-[--color-surface-raised]">
                      <svg viewBox="0 0 400 100" className="h-full w-full" aria-hidden="true">
                        <path
                          d="M40,78 Q200,14 360,52"
                          fill="none"
                          stroke="currentColor"
                          strokeOpacity="0.35"
                          strokeWidth="2"
                          strokeDasharray="8 6"
                        />
                        <circle cx="40" cy="78" r="5" fill="#6366f1" stroke="white" strokeWidth="1.5" />
                        <circle cx="360" cy="52" r="5" fill="#6366f1" stroke="white" strokeWidth="1.5" />
                      </svg>
                      <span className="absolute bottom-2 left-3 text-[11px] font-medium">Home</span>
                      <span className="absolute right-3 top-2 text-[11px] font-medium">Tokyo</span>
                    </div>
                    <div className="flex items-center justify-between rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-4 py-3">
                      <div>
                        <p className="text-sm font-medium">Tokyo trip</p>
                        <p className="text-xs text-[--color-muted]">Aug 12 – Aug 18</p>
                      </div>
                      <span className="rounded-full border border-[--color-border] px-2 py-0.5 text-[11px] text-[--color-muted]">
                        7 days
                      </span>
                    </div>
                    <div className="rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-4 py-3 text-sm">
                      <div className="flex items-center justify-between">
                        <span className="flex items-center gap-1.5">
                          <Plane className="h-3.5 w-3.5 text-[--color-muted]" /> Hong Kong → Tokyo
                        </span>
                        <span className="text-xs text-[--color-muted]">CX500</span>
                      </div>
                      <div className="mt-2 flex items-center justify-between">
                        <span className="flex items-center gap-1.5">
                          <TrainFront className="h-3.5 w-3.5 text-[--color-muted]" /> Tokyo → Kyoto
                        </span>
                        <span className="text-xs text-[--color-muted]">Shinkansen</span>
                      </div>
                    </div>
                    <div className="flex items-center justify-between rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-4 py-3">
                      <span className="text-sm font-medium">Trip expenses</span>
                      <span className="text-sm font-semibold">
                        $1,240 <span className="text-xs font-normal text-emerald-500">+$32 cashback</span>
                      </span>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Feature deep dives */}
      <section className="mx-auto w-full max-w-6xl px-6 py-16 sm:py-24">
        <div className="flex flex-col gap-20 sm:gap-28">
          {/* RAG-powered chat */}
          <div className="grid items-center gap-10 sm:grid-cols-2 sm:gap-16">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-[--color-muted]">RAG-powered chat</p>
              <h2 className="mt-3 text-2xl font-semibold tracking-tight sm:text-3xl">
                Answers grounded in your own documents, not the open web.
              </h2>
              <p className="mt-4 text-base leading-relaxed text-[--color-muted]">
                Upload files, paste URLs, or connect Notion — every response cites the
                Weaviate-backed source it came from, so you can trust what the agent tells you.
              </p>
            </div>
            <div className="overflow-hidden rounded-2xl border border-[--color-border] bg-[--color-surface-raised] p-5">
              <div className="rounded-xl border border-[--color-border] bg-[--color-surface] px-4 py-3 text-sm leading-relaxed">
                &ldquo;Our Q3 refund policy changed for annual plans — refunds now process within
                5 business days.&rdquo;
                <div className="mt-2.5 flex flex-wrap gap-1.5">
                  <span className="inline-flex items-center gap-1 rounded-full border border-[--color-border] px-2 py-0.5 text-[11px] text-[--color-muted]">
                    <FileText className="h-3 w-3" /> refund-policy.pdf
                  </span>
                  <span className="inline-flex items-center gap-1 rounded-full border border-[--color-border] px-2 py-0.5 text-[11px] text-[--color-muted]">
                    <Globe className="h-3 w-3" /> notion.so/billing
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Multi-agent workflows */}
          <div className="grid items-center gap-10 sm:grid-cols-2 sm:gap-16">
            <div className="order-2 overflow-hidden rounded-2xl border border-[--color-border] bg-[--color-surface-raised] p-6 sm:order-1">
              <WorkflowGraph />
              <p className="mt-4 text-xs text-[--color-muted]">Run history · 5/5 steps completed · 8.4s</p>
            </div>
            <div className="order-1 sm:order-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-[--color-muted]">Multi-agent workflows</p>
              <h2 className="mt-3 text-2xl font-semibold tracking-tight sm:text-3xl">
                Compose multi-step agent graphs, visually.
              </h2>
              <p className="mt-4 text-base leading-relaxed text-[--color-muted]">
                Chain retrieval, planning, and tool-calling steps into a reusable pattern, then
                replay any past run to see exactly what each agent decided.
              </p>
            </div>
          </div>

          {/* Financial tracking & alerts */}
          <div className="grid items-center gap-10 sm:grid-cols-2 sm:gap-16">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-[--color-muted]">Financial tracking & alerts</p>
              <h2 className="mt-3 text-2xl font-semibold tracking-tight sm:text-3xl">
                Watch crypto, stocks, and DeFi — get pinged the moment it moves.
              </h2>
              <p className="mt-4 text-base leading-relaxed text-[--color-muted]">
                Set thresholds once and let scheduled agents monitor prices around the clock,
                delivering alerts the moment they trigger.
              </p>
            </div>
            <div className="overflow-hidden rounded-2xl border border-[--color-border] bg-[--color-surface-raised] p-5">
              <div className="flex items-center justify-between rounded-xl border border-[--color-border] bg-[--color-surface] px-4 py-3">
                <div className="flex items-center gap-2">
                  <TrendingUp className="h-4 w-4 text-emerald-500" />
                  <span className="text-sm font-medium">ETH crossed $3,800</span>
                </div>
                <span className="text-xs text-[--color-muted]">2m ago</span>
              </div>
              <div className="mt-3 flex items-center justify-between rounded-xl border border-[--color-border] bg-[--color-surface] px-4 py-3">
                <div className="flex items-center gap-2">
                  <Bell className="h-4 w-4 text-[--color-muted]" />
                  <span className="text-sm font-medium">AAPL alert armed at $210</span>
                </div>
                <span className="text-xs text-[--color-muted]">Scheduled</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Feature grid */}
      <section className="mx-auto w-full max-w-6xl px-6 pb-20">
        <h2 className="mb-6 text-center text-sm font-medium text-[--color-muted]">
          Everything else, at a glance
        </h2>
        <div className="grid grid-cols-1 gap-px overflow-hidden rounded-2xl border border-[--color-border] bg-[--color-border] sm:grid-cols-2 lg:grid-cols-4">
          {features.map(({ icon: Icon, title, description }) => (
            <div key={title} className="bg-[--color-surface-raised] p-6">
              <div className="mb-3 flex h-9 w-9 items-center justify-center rounded-lg bg-black dark:bg-white">
                <Icon className="h-4.5 w-4.5 text-white dark:text-black" />
              </div>
              <h3 className="text-sm font-semibold">{title}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-[--color-muted]">{description}</p>
            </div>
          ))}
        </div>
      </section>

      {/* CTA band */}
      <section className="mt-auto bg-black py-16 text-center text-white dark:bg-white dark:text-black sm:py-20">
        <div className="mx-auto max-w-2xl px-6">
          <h2 className="text-3xl font-semibold tracking-tight sm:text-4xl">
            Ready to build with SkyProton?
          </h2>
          <p className="mt-4 text-base text-white/70 dark:text-black/60 sm:text-lg">
            Bring your own documents, connect your accounts, and let agents handle the rest.
          </p>
          <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <Link
              href="/register"
              className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-white px-6 py-3 text-sm font-medium text-black transition-opacity hover:opacity-90 dark:bg-black dark:text-white sm:w-auto"
            >
              Get started <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              href="/login"
              className="inline-flex w-full items-center justify-center gap-2 rounded-lg border border-white/30 px-6 py-3 text-sm font-medium transition-colors hover:bg-white/10 dark:border-black/20 dark:hover:bg-black/5 sm:w-auto"
            >
              Sign in
            </Link>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="px-6 py-12 sm:px-10">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-x-10 gap-y-4 border-b border-[--color-border] pb-8 text-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-[--color-muted]">Product</p>
          <div className="flex flex-wrap gap-x-10 gap-y-3">
            <Link href="/home" className="text-[--color-muted] transition-colors hover:text-current">Chat</Link>
            <Link href="/workflow" className="text-[--color-muted] transition-colors hover:text-current">Workflows</Link>
            <Link href="/financial" className="text-[--color-muted] transition-colors hover:text-current">Finance &amp; alerts</Link>
            <Link href="/travel" className="text-[--color-muted] transition-colors hover:text-current">Travel</Link>
          </div>
        </div>

        <div className="mx-auto mt-10 w-full max-w-6xl select-none overflow-hidden leading-none">
          <svg viewBox="0 0 1000 200" preserveAspectRatio="none" className="h-auto w-full" aria-hidden="true">
            <text
              x="0"
              y="165"
              textLength="1000"
              lengthAdjust="spacingAndGlyphs"
              fontSize="190"
              fontWeight="700"
              fill="currentColor"
              className="font-sans"
            >
              SKYPROTON
            </text>
          </svg>
          <span className="sr-only">SkyProton</span>
        </div>

        <div className="mx-auto w-full max-w-6xl text-right text-xs text-[--color-muted]">
          &copy; {new Date().getFullYear()} SkyProton
        </div>
      </footer>
    </div>
  );
}
