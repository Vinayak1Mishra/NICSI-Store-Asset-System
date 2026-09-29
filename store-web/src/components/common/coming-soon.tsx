import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ArrowLeft, Clock, ShieldCheck, Database, Layers } from 'lucide-react';

interface ComingSoonProps {
  title: string;
  description: string;
  phase: string;
  tables: string[];
  features: string[];
}

export function ComingSoon({ title, description, phase, tables, features }: ComingSoonProps) {
  return (
    <div className="max-w-4xl mx-auto py-8 px-4 space-y-6">
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/masters/items" className="gap-1.5 text-xs text-muted-foreground">
            <ArrowLeft className="size-3.5" />
            Back to Active Masters (Phase 1)
          </Link>
        </Button>
      </div>

      <Card className="border-border shadow-sm">
        <CardHeader className="pb-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className="rounded-full bg-blue-100 dark:bg-blue-950 px-2.5 py-0.5 text-xs font-semibold text-blue-700 dark:text-blue-300 border border-blue-200">
                {phase}
              </span>
              <span className="rounded-full bg-amber-100 dark:bg-amber-950 px-2.5 py-0.5 text-xs font-semibold text-amber-700 dark:text-amber-300 border border-amber-200 flex items-center gap-1">
                <Clock className="size-3" />
                Scheduled Next
              </span>
            </div>
          </div>
          <CardTitle className="text-2xl mt-3 text-foreground">{title}</CardTitle>
          <CardDescription className="text-sm mt-1">{description}</CardDescription>
        </CardHeader>

        <CardContent className="space-y-6 pt-2">
          {/* Schema readiness */}
          <div className="rounded-lg border border-border bg-muted/40 p-4">
            <div className="flex items-center gap-2 text-xs font-semibold text-primary mb-2">
              <Database className="size-4" />
              <span>PostgreSQL Schema Ready (Migrations V1–V8 Active)</span>
            </div>
            <p className="text-xs text-muted-foreground mb-3">
              The underlying database schema for this module has already been verified and migrated in Phase 0.
            </p>
            <div className="flex flex-wrap gap-1.5">
              {tables.map((tbl) => (
                <code
                  key={tbl}
                  className="rounded bg-background px-2 py-1 text-[11px] font-mono font-medium border border-border text-foreground"
                >
                  {tbl}
                </code>
              ))}
            </div>
          </div>

          {/* Planned capabilities */}
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              <Layers className="size-4 text-primary" />
              <span>Phase Scope & Verification Requirements</span>
            </div>
            <ul className="grid gap-2 sm:grid-cols-2 text-xs">
              {features.map((feature, i) => (
                <li key={i} className="flex items-start gap-2 rounded-md border border-border/60 p-2.5 bg-card">
                  <ShieldCheck className="size-4 text-blue-600 shrink-0 mt-0.5" />
                  <span className="text-foreground">{feature}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* Protocol notice */}
          <div className="border-t border-border pt-4 text-xs text-muted-foreground flex items-center justify-between">
            <span>Protocol: Phase 1 (Masters) must be formally approved before Phase 2 begins.</span>
            <Button size="sm" asChild>
              <Link href="/masters/items">Go to Item Master</Link>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
