'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { Button } from '@/components/ui/button';
import { ShieldAlert, RefreshCw, AlertCircle, Loader2, ArrowLeft } from 'lucide-react';
import Link from 'next/link';

interface WarrantyRow {
  asset_id: string;
  asset_code: string;
  serial_number: string | null;
  item_code: string;
  item_name: string;
  asset_status: string;
  warranty_start_date: string | null;
  warranty_end_date: string;
  days_to_expiry: number;
  store_code: string | null;
  store_name: string | null;
}

function urgencyClass(days: number): string {
  if (days <= 7) return 'text-red-600 font-bold';
  if (days <= 30) return 'text-orange-600 font-semibold';
  return 'text-amber-600';
}

function urgencyBadge(days: number) {
  if (days <= 0) return <span className="text-[11px] font-semibold px-1.5 py-0.5 rounded bg-red-100 text-red-700 border border-red-200">EXPIRED</span>;
  if (days <= 7) return <span className="text-[11px] font-semibold px-1.5 py-0.5 rounded bg-red-50 text-red-600 border border-red-200">CRITICAL</span>;
  if (days <= 30) return <span className="text-[11px] font-semibold px-1.5 py-0.5 rounded bg-orange-50 text-orange-600 border border-orange-200">URGENT</span>;
  return <span className="text-[11px] font-semibold px-1.5 py-0.5 rounded bg-amber-50 text-amber-600 border border-amber-200">UPCOMING</span>;
}

const columns: ColumnDef<WarrantyRow>[] = [
  {
    key: 'asset_code',
    header: 'Asset Code',
    render: (v) => <span className="font-mono text-xs font-semibold text-primary">{v as string}</span>,
  },
  {
    key: 'item_code',
    header: 'Item',
    render: (v, row) => (
      <div>
        <p className="font-mono text-xs font-medium">{v as string}</p>
        <p className="text-[11px] text-muted-foreground truncate max-w-[160px]">{(row as WarrantyRow).item_name}</p>
      </div>
    ),
  },
  {
    key: 'serial_number',
    header: 'Serial No.',
    render: (v) => v ? <span className="font-mono text-xs">{v as string}</span> : <span className="text-muted-foreground text-xs">—</span>,
  },
  {
    key: 'store_code',
    header: 'Store',
    render: (v, row) => (
      v ? (
        <div>
          <p className="font-mono text-xs font-medium">{v as string}</p>
          <p className="text-[11px] text-muted-foreground">{(row as WarrantyRow).store_name}</p>
        </div>
      ) : <span className="text-muted-foreground text-xs">—</span>
    ),
  },
  {
    key: 'warranty_start_date',
    header: 'Start Date',
    render: (v) => v ? <span className="text-xs">{new Date(v as string).toLocaleDateString('en-IN')}</span> : <span className="text-muted-foreground text-xs">—</span>,
  },
  {
    key: 'warranty_end_date',
    header: 'End Date',
    render: (v) => <span className="text-xs font-medium">{new Date(v as string).toLocaleDateString('en-IN')}</span>,
  },
  {
    key: 'days_to_expiry',
    header: 'Days Left',
    render: (v, row) => (
      <div className="flex items-center gap-2">
        <span className={`tabular-nums text-sm ${urgencyClass(Number(v))}`}>{Number(v)}</span>
        {urgencyBadge(Number((row as WarrantyRow).days_to_expiry))}
      </div>
    ),
  },
  {
    key: 'asset_status',
    header: 'Asset Status',
    render: (v) => <span className="text-xs text-muted-foreground">{v as string}</span>,
  },
];

export default function WarrantyExpiryReportPage() {
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery<WarrantyRow[]>({
    queryKey: ['report-warranty-expiry'],
    queryFn: () => api.get<WarrantyRow[]>('/api/reports/assets/warranty-expiry'),
  });

  const expired = (data ?? []).filter((r) => r.days_to_expiry <= 0).length;
  const critical = (data ?? []).filter((r) => r.days_to_expiry > 0 && r.days_to_expiry <= 7).length;
  const urgent = (data ?? []).filter((r) => r.days_to_expiry > 7 && r.days_to_expiry <= 30).length;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Link href="/reports" className="hover:text-foreground transition-colors flex items-center gap-1">
          <ArrowLeft className="h-3.5 w-3.5" />
          Reports
        </Link>
        <span>/</span>
        <span className="text-foreground font-medium">Warranty Expiry</span>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-red-50 dark:bg-red-950/40">
            <ShieldAlert className="h-5 w-5 text-red-600" />
          </div>
          <div>
            <h1 className="text-xl font-bold">Warranty Expiry Report</h1>
            <p className="text-sm text-muted-foreground">Assets with warranty expiring within 90 days</p>
          </div>
        </div>
        <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching} className="gap-2">
          <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {data && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Total</p>
            <p className="text-lg font-bold tabular-nums">{data.length}</p>
          </div>
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Expired</p>
            <p className="text-lg font-bold tabular-nums text-red-600">{expired}</p>
          </div>
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Critical (≤ 7d)</p>
            <p className="text-lg font-bold tabular-nums text-orange-600">{critical}</p>
          </div>
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Urgent (≤ 30d)</p>
            <p className="text-lg font-bold tabular-nums text-amber-600">{urgent}</p>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-24 gap-3 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>Loading warranty data...</span>
        </div>
      ) : isError ? (
        <div className="flex items-center gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-destructive">
          <AlertCircle className="h-5 w-5 shrink-0" />
          <p className="text-sm">{(error as Error)?.message ?? 'Failed to load warranty report.'}</p>
        </div>
      ) : data?.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-muted-foreground gap-2">
          <ShieldAlert className="h-10 w-10 text-emerald-400" />
          <p className="font-medium text-emerald-600">No warranties expiring in the next 90 days</p>
        </div>
      ) : (
        <DataTable columns={columns} data={data ?? []} pageSize={20} />
      )}
    </div>
  );
}
