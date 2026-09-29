'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import Link from 'next/link';
import {
  Boxes,
  Laptop,
  CheckCircle2,
  AlertTriangle,
  Clock,
  ArrowRight,
  ShieldAlert,
  Archive,
  Layers,
  Sparkles,
  FileText,
  ClipboardCheck,
  PackageCheck,
  Tag,
  ShoppingBag,
  Building2,
  MapPin,
  Scale,
  Key,
  UserCheck,
  Loader2,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';

interface DashboardStats {
  masters: {
    totalItems: number;
    totalCategories: number;
    totalUoms: number;
    totalStores: number;
    totalLocations: number;
    totalPolicies: number;
  };
  requisitions: {
    totalRequisitions: number;
  };
  procurement: {
    totalPurchaseOrders: number;
    totalGrns: number;
    totalInspections: number;
  };
  inventory: {
    totalStockBalances: number;
  };
  issues: {
    totalIssues: number;
  };
  assets: {
    totalAssets: number;
    totalAssignments: number;
    totalLicenses: number;
  };
}

export default function DashboardPage() {
  const { data: stats, isLoading, isError } = useQuery<DashboardStats>({
    queryKey: ['dashboard-stats'],
    queryFn: () => api.get<DashboardStats>('/api/store/dashboard/stats'),
    refetchInterval: 30000, // Auto-refresh every 30 seconds
  });

  const kpis = [
    {
      label: 'Master Items',
      value: stats ? `${stats.masters.totalItems}` : '—',
      sub: `${stats?.masters.totalCategories ?? '—'} Categories • ${stats?.masters.totalUoms ?? '—'} UOMs`,
      href: '/masters/items',
      icon: Layers,
      highlight: true,
    },
    {
      label: 'Store Sites',
      value: stats ? `${stats.masters.totalStores}` : '—',
      sub: `${stats?.masters.totalLocations ?? '—'} Locations • ${stats?.masters.totalPolicies ?? '—'} Policies`,
      href: '/masters/stores',
      icon: Building2,
    },
    {
      label: 'Requisitions',
      value: stats ? `${stats.requisitions.totalRequisitions}` : '—',
      sub: 'Material requests & workflow approvals',
      href: '/requisitions',
      icon: FileText,
    },
    {
      label: 'Purchase Orders',
      value: stats ? `${stats.procurement.totalPurchaseOrders}` : '—',
      sub: `${stats?.procurement.totalGrns ?? '—'} GRNs • ${stats?.procurement.totalInspections ?? '—'} Inspections`,
      href: '/purchase',
      icon: ShoppingBag,
    },
    {
      label: 'Stock Balances',
      value: stats ? `${stats.inventory.totalStockBalances}` : '—',
      sub: 'Inventory lot & immutable ledger entries',
      href: '/inventory',
      icon: Boxes,
    },
    {
      label: 'Material Issues',
      value: stats ? `${stats.issues.totalIssues}` : '—',
      sub: 'Store issue notes (SIN) posted',
      href: '/issues',
      icon: PackageCheck,
    },
    {
      label: 'Registered Assets',
      value: stats ? `${stats.assets.totalAssets}` : '—',
      sub: `${stats?.assets.totalAssignments ?? '—'} Assignments active`,
      href: '/assets',
      icon: Laptop,
    },
    {
      label: 'Software Licences',
      value: stats ? `${stats.assets.totalLicenses}` : '—',
      sub: 'Licence pools & seat allocations',
      href: '/licences',
      icon: Key,
    },
  ];

  // Phase implementation status — derived from live data
  const phases = [
    {
      phase: 'Phase 0',
      label: 'Foundation & Migrations',
      status: 'Completed',
      color: 'text-emerald-600',
      live: true,
    },
    {
      phase: 'Phase 1',
      label: 'Masters & Catalogs',
      status: stats
        ? `Active (${stats.masters.totalItems + stats.masters.totalCategories + stats.masters.totalUoms + stats.masters.totalStores + stats.masters.totalLocations + stats.masters.totalPolicies} Records)`
        : 'Loading…',
      color: 'text-emerald-600 font-semibold',
      live: true,
    },
    {
      phase: 'Phase 2',
      label: 'Requisitions & Approvals',
      status: stats ? `Active (${stats.requisitions.totalRequisitions} Records)` : 'Loading…',
      color: 'text-emerald-600 font-semibold',
      live: true,
    },
    {
      phase: 'Phase 3',
      label: 'PO, GRN & Inspection',
      status: stats
        ? `Active (${stats.procurement.totalPurchaseOrders + stats.procurement.totalGrns + stats.procurement.totalInspections} Records)`
        : 'Loading…',
      color: 'text-emerald-600 font-semibold',
      live: true,
    },
    {
      phase: 'Phase 4',
      label: 'Inventory, Lots & Ledger',
      status: stats ? `Active (${stats.inventory.totalStockBalances} Balances)` : 'Loading…',
      color: 'text-emerald-600 font-semibold',
      live: true,
    },
    {
      phase: 'Phase 5',
      label: 'Issue, Asset & Custody',
      status: stats
        ? `Active (${stats.issues.totalIssues} Issues • ${stats.assets.totalAssignments} Assignments)`
        : 'Loading…',
      color: 'text-emerald-600 font-semibold',
      live: true,
    },
    {
      phase: 'Phase 6',
      label: 'Software Licences',
      status: stats ? `Active (${stats.assets.totalLicenses} Licences)` : 'Loading…',
      color: 'text-emerald-600 font-semibold',
      live: true,
    },
    {
      phase: 'Phase 7',
      label: 'Transfers, Returns & Repairs',
      status: 'Upcoming',
      color: 'text-muted-foreground',
      live: false,
    },
    {
      phase: 'Phase 8',
      label: 'Condemnation & Disposal',
      status: 'Upcoming',
      color: 'text-muted-foreground',
      live: false,
    },
    {
      phase: 'Phase 9',
      label: 'Physical Verification',
      status: 'Upcoming',
      color: 'text-muted-foreground',
      live: false,
    },
    {
      phase: 'Phase 10',
      label: 'Reports & Audit Trails',
      status: 'Upcoming',
      color: 'text-muted-foreground',
      live: false,
    },
  ];

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      {/* Welcome Banner */}
      <div className="rounded-xl border border-blue-200 bg-gradient-to-r from-blue-900 to-indigo-950 p-6 text-white shadow-sm">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="rounded-md bg-emerald-500/20 px-2 py-0.5 text-xs font-semibold text-emerald-300 border border-emerald-500/30">
                Phases 1–6 Live
              </span>
              <span className="rounded-md bg-blue-500/20 px-2 py-0.5 text-xs font-semibold text-blue-200 border border-blue-400/30">
                Enterprise ERP 2.0
              </span>
              {isLoading && (
                <span className="rounded-md bg-amber-500/20 px-2 py-0.5 text-xs font-semibold text-amber-300 border border-amber-500/30 flex items-center gap-1">
                  <Loader2 className="size-3 animate-spin" />
                  Fetching stats…
                </span>
              )}
            </div>
            <h1 className="text-2xl font-bold text-white tracking-tight">
              NICSI Store, Inventory & Asset Lifecycle Management
            </h1>
            <p className="text-sm text-blue-200 max-w-2xl">
              Centralized platform for material requisition, receipt inspection, immutable stock ledger, and end-to-end asset lifecycle governance. All data shown below is fetched live from the database.
            </p>
          </div>
          <Button asChild className="bg-amber-500 hover:bg-amber-600 text-slate-950 font-semibold shrink-0">
            <Link href="/masters/items">
              <Sparkles className="mr-2 size-4" />
              Manage Item Master
            </Link>
          </Button>
        </div>
      </div>

      {/* Error State */}
      {isError && (
        <div className="rounded-lg border border-red-300 bg-red-50 dark:bg-red-950/20 p-4 text-sm text-red-700 dark:text-red-300">
          <strong>Unable to fetch dashboard stats.</strong> Make sure the backend is running on <code>localhost:8080</code>.
        </div>
      )}

      {/* KPI Cards — All values from live API */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {kpis.map((kpi, idx) => {
          const Icon = kpi.icon;
          return (
            <Link key={idx} href={kpi.href}>
              <Card
                className={`h-full transition-all hover:shadow-md hover:border-primary/50 cursor-pointer ${
                  kpi.highlight ? 'border-primary/40 bg-blue-50/20 dark:bg-blue-950/20' : ''
                }`}
              >
                <CardHeader className="flex flex-row items-center justify-between pb-2">
                  <CardTitle className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    {kpi.label}
                  </CardTitle>
                  <Icon className="size-4 text-primary" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold text-foreground">
                    {isLoading ? (
                      <Loader2 className="size-5 animate-spin text-muted-foreground" />
                    ) : (
                      kpi.value
                    )}
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">{kpi.sub}</p>
                </CardContent>
              </Card>
            </Link>
          );
        })}
      </div>

      {/* Phase Roadmap & Work Queue */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* Phase 1 Master Modules */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-base">Active Modules</CardTitle>
                <CardDescription className="text-xs">
                  All modules with REST APIs, Maker-Checker rules & Audit trails — connected to PostgreSQL
                </CardDescription>
              </div>
              <span className="rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 text-[10px] font-bold px-2 py-0.5 border border-emerald-300">
                6 Phases Live
              </span>
            </div>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-2">
            {[
              {
                title: 'Item Master',
                desc: `${stats?.masters.totalItems ?? '—'} items with tracking rules`,
                href: '/masters/items',
                code: 'store.item',
              },
              {
                title: 'Categories & Subcategories',
                desc: `${stats?.masters.totalCategories ?? '—'} categories with sort orders`,
                href: '/masters/categories',
                code: 'store.item_category',
              },
              {
                title: 'Units of Measure (UOM)',
                desc: `${stats?.masters.totalUoms ?? '—'} units with decimal precision`,
                href: '/masters/uoms',
                code: 'store.uom',
              },
              {
                title: 'Store Sites',
                desc: `${stats?.masters.totalStores ?? '—'} stores (IT, GENERAL, ASSET)`,
                href: '/masters/stores',
                code: 'store.store_site',
              },
              {
                title: 'Storage Locations',
                desc: `${stats?.masters.totalLocations ?? '—'} locations in hierarchy`,
                href: '/masters/locations',
                code: 'store.storage_location',
              },
              {
                title: 'Item–Store Policies',
                desc: `${stats?.masters.totalPolicies ?? '—'} policies with reorder rules`,
                href: '/masters/policies',
                code: 'store.item_store_policy',
              },
              {
                title: 'Requisitions',
                desc: `${stats?.requisitions.totalRequisitions ?? '—'} material requests`,
                href: '/requisitions',
                code: 'store.requisition',
              },
              {
                title: 'Purchase Orders',
                desc: `${stats?.procurement.totalPurchaseOrders ?? '—'} POs with vendor snapshot`,
                href: '/purchase',
                code: 'store.purchase_order_ref',
              },
              {
                title: 'Goods Receipt Notes',
                desc: `${stats?.procurement.totalGrns ?? '—'} GRNs processed`,
                href: '/grn',
                code: 'store.grn_header',
              },
              {
                title: 'Technical Inspections',
                desc: `${stats?.procurement.totalInspections ?? '—'} inspections decided`,
                href: '/inspection',
                code: 'store.technical_inspection',
              },
              {
                title: 'Material Issues',
                desc: `${stats?.issues.totalIssues ?? '—'} issue notes (SIN)`,
                href: '/issues',
                code: 'store.issue_header',
              },
              {
                title: 'Asset Register',
                desc: `${stats?.assets.totalAssets ?? '—'} assets tracked`,
                href: '/assets',
                code: 'store.asset_register',
              },
            ].map((m, i) => (
              <Link
                key={i}
                href={m.href}
                className="group flex flex-col justify-between rounded-lg border border-border p-3.5 hover:border-primary hover:bg-muted/40 transition-colors"
              >
                <div>
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-sm text-foreground group-hover:text-primary">
                      {m.title}
                    </span>
                    <ArrowRight className="size-3.5 text-muted-foreground group-hover:text-primary group-hover:translate-x-0.5 transition-transform" />
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">{m.desc}</p>
                </div>
                <code className="text-[10px] text-muted-foreground font-mono mt-3">
                  {m.code}
                </code>
              </Link>
            ))}
          </CardContent>
        </Card>

        {/* Phase Roadmap Status — Live Counts */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Implementation Roadmap</CardTitle>
            <CardDescription className="text-xs">
              Live record counts from PostgreSQL
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {phases.map((p, idx) => (
              <div
                key={idx}
                className="flex items-center justify-between border-b border-border/50 pb-2 text-xs last:border-0 last:pb-0"
              >
                <div className="flex items-center gap-1.5">
                  {p.live ? (
                    <span className="size-1.5 rounded-full bg-emerald-500 shrink-0" />
                  ) : (
                    <span className="size-1.5 rounded-full bg-muted-foreground/40 shrink-0" />
                  )}
                  <span className="font-semibold text-foreground">{p.phase}:</span>
                  <span className="text-muted-foreground">{p.label}</span>
                </div>
                <span className={`text-[11px] ${p.color} text-right`}>{p.status}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
