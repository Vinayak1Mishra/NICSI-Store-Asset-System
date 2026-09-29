'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Package, RefreshCw, Search, AlertCircle, Loader2 } from 'lucide-react';
import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';

interface StockRow {
  stock_balance_id: string;
  item_id: string;
  item_code: string;
  item_name: string;
  item_type: string;
  store_id: string;
  store_code: string;
  store_name: string;
  lot_batch_number: string | null;
  on_hand_qty: number;
  reserved_qty: number;
  available_qty: number;
  avg_unit_cost: number;
  total_value: number;
  base_uom_id: string;
}

const ITEM_TYPE_COLORS: Record<string, string> = {
  CONSUMABLE: 'bg-blue-100 text-blue-700 border-blue-200',
  NON_CONSUMABLE: 'bg-violet-100 text-violet-700 border-violet-200',
  SOFTWARE: 'bg-cyan-100 text-cyan-700 border-cyan-200',
  SERVICE_SUPPORT: 'bg-orange-100 text-orange-700 border-orange-200',
};

const columns: ColumnDef<StockRow>[] = [
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
    key: 'item_type',
    header: 'Type',
    render: (v) => (
      <Badge variant="outline" className={`text-[11px] ${ITEM_TYPE_COLORS[v as string] ?? ''}`}>
        {(v as string).replace(/_/g, ' ')}
      </Badge>
    ),
  },
  {
    key: 'store_code',
    header: 'Store',
    render: (v, row) => (
      <div>
        <p className="font-mono text-xs font-medium">{v as string}</p>
        <p className="text-[11px] text-muted-foreground">{(row as StockRow).store_name}</p>
      </div>
    ),
  },
  {
    key: 'lot_batch_number',
    header: 'Lot / Batch',
    render: (v) => v ? <span className="font-mono text-xs">{v as string}</span> : <span className="text-muted-foreground text-xs">—</span>,
  },
  {
    key: 'on_hand_qty',
    header: 'On Hand',
    render: (v) => <span className="tabular-nums font-medium">{Number(v).toLocaleString()}</span>,
  },
  {
    key: 'reserved_qty',
    header: 'Reserved',
    render: (v) => <span className="tabular-nums text-amber-600">{Number(v).toLocaleString()}</span>,
  },
  {
    key: 'available_qty',
    header: 'Available',
    render: (v) => (
      <span className={`tabular-nums font-semibold ${Number(v) > 0 ? 'text-emerald-600' : 'text-red-500'}`}>
        {Number(v).toLocaleString()}
      </span>
    ),
  },
  {
    key: 'avg_unit_cost',
    header: 'Avg Cost',
    render: (v) => <span className="tabular-nums text-xs">₹{Number(v).toFixed(2)}</span>,
  },
  {
    key: 'total_value',
    header: 'Total Value',
    render: (v) => <span className="tabular-nums font-semibold">₹{Number(v).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>,
  },
];

export default function CurrentStockReportPage() {
  const [search, setSearch] = useState('');

  const { data, isLoading, isError, error, refetch, isFetching } = useQuery<StockRow[]>({
    queryKey: ['report-current-stock'],
    queryFn: () => api.get<StockRow[]>('/api/reports/stock/current'),
  });

  const filtered = (data ?? []).filter(
    (r) =>
      !search ||
      r.item_code.toLowerCase().includes(search.toLowerCase()) ||
      r.item_name.toLowerCase().includes(search.toLowerCase()) ||
      r.store_code.toLowerCase().includes(search.toLowerCase()) ||
      r.store_name.toLowerCase().includes(search.toLowerCase())
  );

  const totalValue = filtered.reduce((sum, r) => sum + Number(r.total_value), 0);

  return (
    <div className="space-y-6">
      {/* Breadcrumb + Header */}
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Link href="/reports" className="hover:text-foreground transition-colors flex items-center gap-1">
          <ArrowLeft className="h-3.5 w-3.5" />
          Reports
        </Link>
        <span>/</span>
        <span className="text-foreground font-medium">Current Stock</span>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 dark:bg-blue-950/40">
            <Package className="h-5 w-5 text-blue-600" />
          </div>
          <div>
            <h1 className="text-xl font-bold">Current Stock Report</h1>
            <p className="text-sm text-muted-foreground">Live snapshot from all store locations</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <div className="text-right hidden sm:block">
            <p className="text-xs text-muted-foreground">Total Inventory Value</p>
            <p className="text-lg font-bold tabular-nums text-emerald-600">
              ₹{totalValue.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </p>
          </div>
          <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching} className="gap-2">
            <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>
      </div>

      {/* Filter */}
      <div className="relative max-w-sm">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Search item, store..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-9"
        />
      </div>

      {/* Stats row */}
      {data && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {[
            { label: 'Total Rows', value: filtered.length.toString() },
            { label: 'Total On Hand', value: filtered.reduce((s, r) => s + Number(r.on_hand_qty), 0).toLocaleString() },
            { label: 'Total Reserved', value: filtered.reduce((s, r) => s + Number(r.reserved_qty), 0).toLocaleString() },
            { label: 'Total Available', value: filtered.reduce((s, r) => s + Number(r.available_qty), 0).toLocaleString() },
          ].map((stat) => (
            <div key={stat.label} className="rounded-lg border bg-card p-3">
              <p className="text-xs text-muted-foreground">{stat.label}</p>
              <p className="text-lg font-bold tabular-nums">{stat.value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Table */}
      {isLoading ? (
        <div className="flex items-center justify-center py-24 gap-3 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>Loading stock data...</span>
        </div>
      ) : isError ? (
        <div className="flex items-center gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-destructive">
          <AlertCircle className="h-5 w-5 shrink-0" />
          <p className="text-sm">{(error as Error)?.message ?? 'Failed to load stock report. Is the backend running?'}</p>
        </div>
      ) : (
        <DataTable columns={columns} data={filtered} pageSize={20} />
      )}
    </div>
  );
}
