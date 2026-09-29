'use client';

import Link from 'next/link';
import {
  BarChart3,
  Package,
  BookOpen,
  AlertTriangle,
  Laptop,
  ShieldAlert,
  Trash2,
  PackageCheck,
  ArrowRight,
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';

const REPORTS = [
  {
    title: 'Current Stock',
    description: 'Live stock balances across all stores — on-hand, reserved, and available quantities.',
    href: '/reports/stock/current',
    icon: Package,
    color: 'text-blue-600',
    bg: 'bg-blue-50 dark:bg-blue-950/40',
  },
  {
    title: 'Stock Ledger',
    description: 'Full immutable ledger of every stock movement: receipts, issues, transfers, adjustments.',
    href: '/reports/stock/ledger',
    icon: BookOpen,
    color: 'text-indigo-600',
    bg: 'bg-indigo-50 dark:bg-indigo-950/40',
  },
  {
    title: 'Low Stock Alert',
    description: 'Items that have fallen at or below their reorder level, ready for replenishment.',
    href: '/reports/stock/low',
    icon: AlertTriangle,
    color: 'text-amber-600',
    bg: 'bg-amber-50 dark:bg-amber-950/40',
  },
  {
    title: 'Employee Assets',
    description: 'All assets currently assigned to employees — asset code, serial, custodian and location.',
    href: '/reports/assets/employee',
    icon: Laptop,
    color: 'text-emerald-600',
    bg: 'bg-emerald-50 dark:bg-emerald-950/40',
  },
  {
    title: 'Warranty Expiry',
    description: 'Assets with warranties expiring within the next 90 days — sorted by urgency.',
    href: '/reports/assets/warranty',
    icon: ShieldAlert,
    color: 'text-red-600',
    bg: 'bg-red-50 dark:bg-red-950/40',
  },
  {
    title: 'Disposal Register',
    description: 'Full record of all disposed assets — disposal method, realized value, and status.',
    href: '/reports/assets/disposal',
    icon: Trash2,
    color: 'text-slate-600',
    bg: 'bg-slate-50 dark:bg-slate-950/40',
  },
  {
    title: 'Returns Report',
    description: 'Summary of all material & asset return documents — filterable by status and date range.',
    href: '/reports/returns',
    icon: PackageCheck,
    color: 'text-teal-600',
    bg: 'bg-teal-50 dark:bg-teal-950/40',
  },
];

export default function ReportsIndexPage() {
  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-indigo-600 shadow-md">
          <BarChart3 className="h-6 w-6 text-white" />
        </div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Reports &amp; Analytics</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Live operational reports powered by PostgreSQL views — no caching, always real-time.
          </p>
        </div>
      </div>

      {/* Report Cards Grid */}
      <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
        {REPORTS.map((report) => {
          const Icon = report.icon;
          return (
            <Card key={report.href} className="group hover:shadow-md transition-shadow duration-200 border-border/60">
              <CardHeader className="pb-3">
                <div className="flex items-start gap-3">
                  <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${report.bg}`}>
                    <Icon className={`h-5 w-5 ${report.color}`} />
                  </div>
                  <div>
                    <CardTitle className="text-base leading-snug">{report.title}</CardTitle>
                    <CardDescription className="mt-1 text-xs leading-relaxed">
                      {report.description}
                    </CardDescription>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <Button asChild variant="outline" size="sm" className="w-full gap-2 group-hover:border-primary/50 transition-colors">
                  <Link href={report.href}>
                    View Report
                    <ArrowRight className="h-3.5 w-3.5" />
                  </Link>
                </Button>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
