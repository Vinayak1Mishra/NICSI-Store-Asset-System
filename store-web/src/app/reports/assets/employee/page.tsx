'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Laptop, RefreshCw, Search, AlertCircle, Loader2, ArrowLeft } from 'lucide-react';
import Link from 'next/link';

interface EmployeeAssetRow {
  asset_id: string;
  asset_code: string;
  serial_number: string | null;
  item_id: string;
  item_code: string;
  item_name: string;
  asset_status: string;
  condition_status: string | null;
  employee_id: string;
  employee_name: string;
  assigned_from: string;
  location_id: string | null;
  location_name: string | null;
}

const columns: ColumnDef<EmployeeAssetRow>[] = [
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
        <p className="text-[11px] text-muted-foreground truncate max-w-[180px]">{(row as EmployeeAssetRow).item_name}</p>
      </div>
    ),
  },
  {
    key: 'serial_number',
    header: 'Serial No.',
    render: (v) => v ? <span className="font-mono text-xs">{v as string}</span> : <span className="text-muted-foreground text-xs">—</span>,
  },
  {
    key: 'employee_name',
    header: 'Custodian',
    render: (v) => <span className="font-medium text-sm">{v as string}</span>,
  },
  {
    key: 'asset_status',
    header: 'Status',
    render: (v) => (
      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-100 text-emerald-700 border border-emerald-200">
        {v as string}
      </span>
    ),
  },
  {
    key: 'condition_status',
    header: 'Condition',
    render: (v) => v ? (
      <span className="text-xs text-muted-foreground">{v as string}</span>
    ) : <span className="text-muted-foreground text-xs">—</span>,
  },
  {
    key: 'assigned_from',
    header: 'Assigned On',
    render: (v) => <span className="text-xs">{new Date(v as string).toLocaleDateString('en-IN')}</span>,
  },
  {
    key: 'location_name',
    header: 'Location',
    render: (v) => v ? <span className="text-xs">{v as string}</span> : <span className="text-muted-foreground text-xs">—</span>,
  },
];

export default function EmployeeAssetsReportPage() {
  const [search, setSearch] = useState('');

  const { data, isLoading, isError, error, refetch, isFetching } = useQuery<EmployeeAssetRow[]>({
    queryKey: ['report-employee-assets'],
    queryFn: () => api.get<EmployeeAssetRow[]>('/api/reports/assets/employee'),
  });

  const filtered = (data ?? []).filter(
    (r) =>
      !search ||
      r.asset_code.toLowerCase().includes(search.toLowerCase()) ||
      r.item_code.toLowerCase().includes(search.toLowerCase()) ||
      r.item_name.toLowerCase().includes(search.toLowerCase()) ||
      r.employee_name.toLowerCase().includes(search.toLowerCase()) ||
      (r.serial_number ?? '').toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Link href="/reports" className="hover:text-foreground transition-colors flex items-center gap-1">
          <ArrowLeft className="h-3.5 w-3.5" />
          Reports
        </Link>
        <span>/</span>
        <span className="text-foreground font-medium">Employee Assets</span>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-50 dark:bg-emerald-950/40">
            <Laptop className="h-5 w-5 text-emerald-600" />
          </div>
          <div>
            <h1 className="text-xl font-bold">Employee Assets</h1>
            <p className="text-sm text-muted-foreground">All assets currently assigned to employees</p>
          </div>
        </div>
        <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching} className="gap-2">
          <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      <div className="relative max-w-sm">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Search asset, employee, item..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-9"
        />
      </div>

      {data && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Assets Assigned</p>
            <p className="text-lg font-bold tabular-nums text-emerald-600">{filtered.length}</p>
          </div>
          <div className="rounded-lg border bg-card p-3">
            <p className="text-xs text-muted-foreground">Unique Employees</p>
            <p className="text-lg font-bold tabular-nums">{new Set(filtered.map((r) => r.employee_id)).size}</p>
          </div>
          <div className="rounded-lg border bg-card p-3 col-span-2 sm:col-span-1">
            <p className="text-xs text-muted-foreground">Unique Item Types</p>
            <p className="text-lg font-bold tabular-nums">{new Set(filtered.map((r) => r.item_code)).size}</p>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-24 gap-3 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>Loading employee assets...</span>
        </div>
      ) : isError ? (
        <div className="flex items-center gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-destructive">
          <AlertCircle className="h-5 w-5 shrink-0" />
          <p className="text-sm">{(error as Error)?.message ?? 'Failed to load report.'}</p>
        </div>
      ) : (
        <DataTable columns={columns} data={filtered} pageSize={20} />
      )}
    </div>
  );
}
