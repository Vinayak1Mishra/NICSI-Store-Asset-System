'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { BookOpen, RefreshCw, Search, AlertCircle, Loader2, ArrowLeft } from 'lucide-react';
import Link from 'next/link';

interface LedgerRow {
  transaction_id: string;
  transaction_date: string;
  transaction_type: string;
  document_reference: string | null;
  item_id: string;
  item_code: string;
  item_name: string;
  store_id: string;
  store_code: string;
  store_name: string;
  lot_batch_number: string | null;
  quantity: number;
  unit_cost: number;
  total_cost: number;
  created_by: string;
  created_at: string;
}

const TXN_COLORS: Record<string, string> = {
  RECEIPT: 'bg-emerald-100 text-emerald-700 border-emerald-200',
  ISSUE: 'bg-orange-100 text-orange-700 border-orange-200',
  RETURN: 'bg-blue-100 text-blue-700 border-blue-200',
  TRANSFER_IN: 'bg-teal-100 text-teal-700 border-teal-200',
  TRANSFER_OUT: 'bg-rose-100 text-rose-700 border-rose-200',
  ADJUSTMENT_IN: 'bg-violet-100 text-violet-700 border-violet-200',
  ADJUSTMENT_OUT: 'bg-pink-100 text-pink-700 border-pink-200',
  DISPOSAL: 'bg-slate-100 text-slate-700 border-slate-200',
  OPENING: 'bg-cyan-100 text-cyan-700 border-cyan-200',
  REVERSAL: 'bg-red-100 text-red-700 border-red-200',
};

const ALL_TXN_TYPES = [
  'RECEIPT', 'ISSUE', 'RETURN', 'TRANSFER_IN', 'TRANSFER_OUT',
  'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'DISPOSAL', 'OPENING', 'REVERSAL',
];

const columns: ColumnDef<LedgerRow>[] = [
  {
    key: 'transaction_date',
    header: 'Date / Time',
    render: (v) => (
      <div>
        <p className="text-xs font-medium">{new Date(v as string).toLocaleDateString('en-IN')}</p>
        <p className="text-[11px] text-muted-foreground">{new Date(v as string).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}</p>
      </div>
    ),
  },
  {
    key: 'transaction_type',
    header: 'Type',
    render: (v) => (
      <Badge variant="outline" className={`text-[11px] ${TXN_COLORS[v as string] ?? ''}`}>
        {(v as string).replace(/_/g, ' ')}
      </Badge>
    ),
  },
  {
    key: 'document_reference',
    header: 'Reference',
    render: (v) => v ? <span className="font-mono text-xs">{v as string}</span> : <span className="text-muted-foreground text-xs">—</span>,
  },
  {
    key: 'item_code',
    header: 'Item',
    render: (v, row) => (
      <div>
        <p className="font-mono text-xs font-semibold text-primary">{v as string}</p>
        <p className="text-[11px] text-muted-foreground truncate max-w-[180px]">{(row as LedgerRow).item_name}</p>
      </div>
    ),
  },
  {
    key: 'store_code',
    header: 'Store',
    render: (v, row) => (
      <div>
        <p className="font-mono text-xs font-medium">{v as string}</p>
        <p className="text-[11px] text-muted-foreground">{(row as LedgerRow).store_name}</p>
      </div>
    ),
  },
  {
    key: 'lot_batch_number',
    header: 'Lot',
    render: (v) => v ? <span className="font-mono text-xs">{v as string}</span> : <span className="text-muted-foreground text-xs">—</span>,
  },
  {
    key: 'quantity',
    header: 'Quantity',
    render: (v) => {
      const qty = Number(v);
      return (
        <span className={`tabular-nums font-semibold ${qty >= 0 ? 'text-emerald-600' : 'text-red-500'}`}>
          {qty >= 0 ? '+' : ''}{qty.toLocaleString()}
        </span>
      );
    },
  },
  {
    key: 'unit_cost',
    header: 'Unit Cost',
    render: (v) => <span className="tabular-nums text-xs">₹{Number(v).toFixed(2)}</span>,
  },
  {
    key: 'total_cost',
    header: 'Total Cost',
    render: (v) => <span className="tabular-nums font-medium">₹{Number(v).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>,
  },
];

export default function StockLedgerReportPage() {
  const [search, setSearch] = useState('');
  const [txnTypeFilter, setTxnTypeFilter] = useState('ALL');

  const { data, isLoading, isError, error, refetch, isFetching } = useQuery<LedgerRow[]>({
    queryKey: ['report-stock-ledger'],
    queryFn: () => api.get<LedgerRow[]>('/api/reports/stock/ledger'),
  });

  const filtered = (data ?? []).filter((r) => {
    const matchSearch = !search ||
      r.item_code.toLowerCase().includes(search.toLowerCase()) ||
      r.item_name.toLowerCase().includes(search.toLowerCase()) ||
      (r.document_reference ?? '').toLowerCase().includes(search.toLowerCase());
    const matchType = txnTypeFilter === 'ALL' || r.transaction_type === txnTypeFilter;
    return matchSearch && matchType;
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Link href="/reports" className="hover:text-foreground transition-colors flex items-center gap-1">
          <ArrowLeft className="h-3.5 w-3.5" />
          Reports
        </Link>
        <span>/</span>
        <span className="text-foreground font-medium">Stock Ledger</span>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-50 dark:bg-indigo-950/40">
            <BookOpen className="h-5 w-5 text-indigo-600" />
          </div>
          <div>
            <h1 className="text-xl font-bold">Stock Ledger</h1>
            <p className="text-sm text-muted-foreground">Append-only transaction ledger — latest first</p>
          </div>
        </div>
        <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching} className="gap-2">
          <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search item, reference..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select value={txnTypeFilter} onValueChange={setTxnTypeFilter}>
          <SelectTrigger className="w-48">
            <SelectValue placeholder="Transaction Type" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Types</SelectItem>
            {ALL_TXN_TYPES.map((t) => (
              <SelectItem key={t} value={t}>{t.replace(/_/g, ' ')}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {data && (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Total Transactions</p>
            <p className="text-lg font-bold tabular-nums">{filtered.length.toLocaleString()}</p>
          </div>
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Total Value Moved</p>
            <p className="text-lg font-bold tabular-nums text-indigo-600">
              ₹{filtered.reduce((s, r) => s + Number(r.total_cost), 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </p>
          </div>
          <div className="rounded-lg border bg-card p-3 col-span-2 sm:col-span-1">
            <p className="text-xs text-muted-foreground">Unique Item Codes</p>
            <p className="text-lg font-bold tabular-nums">{new Set(filtered.map((r) => r.item_code)).size}</p>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-24 gap-3 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>Loading ledger...</span>
        </div>
      ) : isError ? (
        <div className="flex items-center gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-destructive">
          <AlertCircle className="h-5 w-5 shrink-0" />
          <p className="text-sm">{(error as Error)?.message ?? 'Failed to load ledger report.'}</p>
        </div>
      ) : (
        <DataTable columns={columns} data={filtered} pageSize={25} />
      )}
    </div>
  );
}
