'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { Button } from '@/components/ui/button';
import { AlertTriangle, RefreshCw, AlertCircle, Loader2, ArrowLeft } from 'lucide-react';
import Link from 'next/link';

interface LowStockRow {
  item_id: string;
  item_code: string;
  item_name: string;
  store_id: string;
  store_code: string;
  store_name: string;
  total_on_hand_qty: number;
  total_reserved_qty: number;
  total_available_qty: number;
  reorder_level_qty: number;
}

const columns: ColumnDef<LowStockRow>[] = [
  {
    key: 'item_code',
    header: 'Item Code',
    render: (v) => <span className="font-mono text-xs font-semibold text-primary">{v as string}</span>,
  },
  {
    key: 'item_name',
    header: 'Item Name',
    render: (v) => <span className="font-medium">{v as string}</span>,
  },
  {
    key: 'store_code',
    header: 'Store',
    render: (v, row) => (
      <div>
        <p className="font-mono text-xs font-medium">{v as string}</p>
        <p className="text-[11px] text-muted-foreground">{(row as LowStockRow).store_name}</p>
      </div>
    ),
  },
  {
    key: 'reorder_level_qty',
    header: 'Reorder Level',
    render: (v) => <span className="tabular-nums font-semibold text-amber-600">{Number(v).toLocaleString()}</span>,
  },
  {
    key: 'total_on_hand_qty',
    header: 'On Hand',
    render: (v) => <span className="tabular-nums">{Number(v).toLocaleString()}</span>,
  },
  {
    key: 'total_available_qty',
    header: 'Available',
    render: (v) => {
      const qty = Number(v);
      return (
        <span className={`tabular-nums font-bold ${qty <= 0 ? 'text-red-600' : 'text-amber-600'}`}>
          {qty.toLocaleString()}
        </span>
      );
    },
  },
  {
    key: 'total_reserved_qty',
    header: 'Reserved',
    render: (v) => <span className="tabular-nums text-slate-500">{Number(v).toLocaleString()}</span>,
  },
  {
    key: 'item_id',
    header: 'Shortfall',
    render: (_, row) => {
      const r = row as LowStockRow;
      const shortfall = Number(r.reorder_level_qty) - Number(r.total_available_qty);
      return shortfall > 0 ? (
        <span className="tabular-nums font-bold text-red-600">−{shortfall.toLocaleString()}</span>
      ) : (
        <span className="tabular-nums text-muted-foreground">0</span>
      );
    },
  },
];

export default function LowStockReportPage() {
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery<LowStockRow[]>({
    queryKey: ['report-low-stock'],
    queryFn: () => api.get<LowStockRow[]>('/api/reports/stock/low'),
  });

  const criticalCount = (data ?? []).filter((r) => Number(r.total_available_qty) <= 0).length;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Link href="/reports" className="hover:text-foreground transition-colors flex items-center gap-1">
          <ArrowLeft className="h-3.5 w-3.5" />
          Reports
        </Link>
        <span>/</span>
        <span className="text-foreground font-medium">Low Stock Alert</span>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50 dark:bg-amber-950/40">
            <AlertTriangle className="h-5 w-5 text-amber-600" />
          </div>
          <div>
            <h1 className="text-xl font-bold">Low Stock Alert</h1>
            <p className="text-sm text-muted-foreground">Items at or below their configured reorder level</p>
          </div>
        </div>
        <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching} className="gap-2">
          <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {data && data.length > 0 && (
        <div className="flex items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 dark:bg-amber-950/30 dark:border-amber-800 p-4">
          <AlertTriangle className="h-5 w-5 text-amber-600 shrink-0" />
          <div>
            <p className="text-sm font-semibold text-amber-800 dark:text-amber-400">
              {data.length} item{data.length !== 1 ? 's' : ''} below reorder level
              {criticalCount > 0 && ` — ${criticalCount} out of stock`}
            </p>
            <p className="text-xs text-amber-700 dark:text-amber-500 mt-0.5">
              Please raise purchase orders to replenish these items.
            </p>
          </div>
        </div>
      )}

      {data && (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Total Low Stock Lines</p>
            <p className="text-lg font-bold tabular-nums text-amber-600">{data.length}</p>
          </div>
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Out of Stock</p>
            <p className="text-lg font-bold tabular-nums text-red-600">{criticalCount}</p>
          </div>
          <div className="rounded-lg border bg-card p-3 col-span-2 sm:col-span-1">
            <p className="text-xs text-muted-foreground">Affected Stores</p>
            <p className="text-lg font-bold tabular-nums">{new Set(data.map((r) => r.store_id)).size}</p>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-24 gap-3 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>Loading low stock data...</span>
        </div>
      ) : isError ? (
        <div className="flex items-center gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-destructive">
          <AlertCircle className="h-5 w-5 shrink-0" />
          <p className="text-sm">{(error as Error)?.message ?? 'Failed to load low stock report.'}</p>
        </div>
      ) : data?.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-muted-foreground gap-2">
          <AlertTriangle className="h-10 w-10 text-emerald-400" />
          <p className="font-medium text-emerald-600">All items are above reorder level</p>
          <p className="text-sm">No replenishment required at this time.</p>
        </div>
      ) : (
        <DataTable columns={columns} data={data ?? []} pageSize={20} />
      )}
    </div>
  );
}
