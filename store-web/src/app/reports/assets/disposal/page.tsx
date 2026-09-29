'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Trash2, RefreshCw, AlertCircle, Loader2, ArrowLeft } from 'lucide-react';
import Link from 'next/link';

interface DisposalRow {
  disposal_id: string;
  disposal_method: string;
  disposal_date: string | null;
  sale_amount: number;
  certificate_number: string | null;
  status: string;
  asset_id: string;
  asset_code: string;
  serial_number: string | null;
  item_code: string;
  item_name: string;
  realized_value: number;
}

const METHOD_COLORS: Record<string, string> = {
  AUCTION: 'bg-blue-100 text-blue-700 border-blue-200',
  E_WASTE: 'bg-emerald-100 text-emerald-700 border-emerald-200',
  SCRAP: 'bg-slate-100 text-slate-700 border-slate-200',
  RETURN_TO_OEM: 'bg-purple-100 text-purple-700 border-purple-200',
  TRANSFER: 'bg-teal-100 text-teal-700 border-teal-200',
  OTHER: 'bg-gray-100 text-gray-700 border-gray-200',
};

const STATUS_COLORS: Record<string, string> = {
  COMPLETED: 'bg-emerald-100 text-emerald-700 border-emerald-200',
  POSTED: 'bg-blue-100 text-blue-700 border-blue-200',
  APPROVED: 'bg-indigo-100 text-indigo-700 border-indigo-200',
  SUBMITTED: 'bg-amber-100 text-amber-700 border-amber-200',
  DRAFT: 'bg-slate-100 text-slate-700 border-slate-200',
  REJECTED: 'bg-red-100 text-red-700 border-red-200',
  CANCELLED: 'bg-red-100 text-red-600 border-red-200',
};

const columns: ColumnDef<DisposalRow>[] = [
  {
    key: 'disposal_date',
    header: 'Date',
    render: (v) => v ? <span className="text-xs">{new Date(v as string).toLocaleDateString('en-IN')}</span> : <span className="text-muted-foreground text-xs">—</span>,
  },
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
        <p className="text-[11px] text-muted-foreground truncate max-w-[160px]">{(row as DisposalRow).item_name}</p>
      </div>
    ),
  },
  {
    key: 'serial_number',
    header: 'Serial No.',
    render: (v) => v ? <span className="font-mono text-xs">{v as string}</span> : <span className="text-muted-foreground text-xs">—</span>,
  },
  {
    key: 'disposal_method',
    header: 'Method',
    render: (v) => (
      <Badge variant="outline" className={`text-[11px] ${METHOD_COLORS[v as string] ?? ''}`}>
        {(v as string).replace(/_/g, ' ')}
      </Badge>
    ),
  },
  {
    key: 'status',
    header: 'Status',
    render: (v) => (
      <Badge variant="outline" className={`text-[11px] ${STATUS_COLORS[v as string] ?? ''}`}>
        {v as string}
      </Badge>
    ),
  },
  {
    key: 'certificate_number',
    header: 'Certificate',
    render: (v) => v ? <span className="font-mono text-xs">{v as string}</span> : <span className="text-muted-foreground text-xs">—</span>,
  },
  {
    key: 'realized_value',
    header: 'Realized Value',
    render: (v) => <span className="tabular-nums font-semibold text-emerald-600">₹{Number(v).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>,
  },
  {
    key: 'sale_amount',
    header: 'Sale Amount',
    render: (v) => <span className="tabular-nums">₹{Number(v).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>,
  },
];

export default function DisposalRegisterPage() {
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery<DisposalRow[]>({
    queryKey: ['report-disposal'],
    queryFn: () => api.get<DisposalRow[]>('/api/reports/assets/disposal'),
  });

  const totalRealized = (data ?? []).reduce((s, r) => s + Number(r.realized_value), 0);
  const completed = (data ?? []).filter((r) => r.status === 'COMPLETED' || r.status === 'POSTED').length;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Link href="/reports" className="hover:text-foreground transition-colors flex items-center gap-1">
          <ArrowLeft className="h-3.5 w-3.5" />
          Reports
        </Link>
        <span>/</span>
        <span className="text-foreground font-medium">Disposal Register</span>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800">
            <Trash2 className="h-5 w-5 text-slate-600" />
          </div>
          <div>
            <h1 className="text-xl font-bold">Disposal Register</h1>
            <p className="text-sm text-muted-foreground">Full record of disposed assets with realization values</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <div className="text-right hidden sm:block">
            <p className="text-xs text-muted-foreground">Total Realized</p>
            <p className="text-lg font-bold tabular-nums text-emerald-600">
              ₹{totalRealized.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </p>
          </div>
          <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching} className="gap-2">
            <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>
      </div>

      {data && (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Total Disposals</p>
            <p className="text-lg font-bold tabular-nums">{data.length}</p>
          </div>
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Completed / Posted</p>
            <p className="text-lg font-bold tabular-nums text-emerald-600">{completed}</p>
          </div>
          <div className="rounded-lg border bg-card p-3 col-span-2 sm:col-span-1">
            <p className="text-xs text-muted-foreground">Unique Methods</p>
            <p className="text-lg font-bold tabular-nums">{new Set(data.map((r) => r.disposal_method)).size}</p>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-24 gap-3 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>Loading disposal data...</span>
        </div>
      ) : isError ? (
        <div className="flex items-center gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-destructive">
          <AlertCircle className="h-5 w-5 shrink-0" />
          <p className="text-sm">{(error as Error)?.message ?? 'Failed to load disposal register.'}</p>
        </div>
      ) : data?.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 text-muted-foreground gap-2">
          <Trash2 className="h-10 w-10 opacity-30" />
          <p className="font-medium">No disposals recorded yet</p>
        </div>
      ) : (
        <DataTable columns={columns} data={data ?? []} pageSize={20} />
      )}
    </div>
  );
}
