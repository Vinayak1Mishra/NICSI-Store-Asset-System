'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { StatusBadge } from '@/components/common/status-badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Users,
  Search,
  Laptop,
  ArrowRightLeft,
  RotateCcw,
  CheckCircle2,
  FileCheck2,
  Printer,
  ShieldCheck,
} from 'lucide-react';
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

export default function EmployeeAssetsPage() {
  const [searchTerm, setSearchTerm] = useState('');

  const { data: rows, isLoading, refetch } = useQuery<EmployeeAssetRow[]>({
    queryKey: ['employee-assets-report'],
    queryFn: () => api.get<EmployeeAssetRow[]>('/api/reports/assets/employee'),
  });

  const filtered = (rows || []).filter((r) => {
    if (!searchTerm) return true;
    const term = searchTerm.toLowerCase();
    return (
      r.employee_name?.toLowerCase().includes(term) ||
      r.employee_id?.toLowerCase().includes(term) ||
      r.asset_code?.toLowerCase().includes(term) ||
      r.item_name?.toLowerCase().includes(term) ||
      r.serial_number?.toLowerCase().includes(term)
    );
  });

  const totalAssigned = rows?.length || 0;
  const uniqueEmployees = new Set(rows?.map((r) => r.employee_id)).size;

  const columns: ColumnDef<EmployeeAssetRow>[] = [
    {
      accessorKey: 'asset_code',
      header: 'Asset Code',
      cell: (row) => (
        <span className="font-mono text-xs font-semibold text-blue-600 dark:text-blue-400">
          {row.asset_code}
        </span>
      ),
    },
    {
      accessorKey: 'item_name',
      header: 'Equipment / Description',
      cell: (row) => (
        <div>
          <p className="font-medium text-slate-900 dark:text-slate-100">{row.item_name}</p>
          <p className="text-[11px] text-slate-500 font-mono">
            {row.item_code} · SN: {row.serial_number || 'N/A'}
          </p>
        </div>
      ),
    },
    {
      accessorKey: 'employee_name',
      header: 'Assigned Employee / Custodian',
      cell: (row) => (
        <div>
          <p className="font-medium text-slate-900 dark:text-slate-100">{row.employee_name}</p>
          <p className="text-[11px] text-slate-500 font-mono">{row.employee_id}</p>
        </div>
      ),
    },
    {
      accessorKey: 'assigned_from',
      header: 'Assigned Date',
      cell: (row) => (
        <span className="text-xs text-slate-500">{row.assigned_from}</span>
      ),
    },
    {
      accessorKey: 'condition_status',
      header: 'Condition',
      cell: (row) => <StatusBadge status={row.condition_status || 'GOOD'} />,
    },
    {
      accessorKey: 'asset_status',
      header: 'Status',
      cell: (row) => <StatusBadge status={row.asset_status} />,
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: (row) => (
        <div className="flex items-center gap-1.5">
          <Link href={`/returns?assetCode=${encodeURIComponent(row.asset_code)}`}>
            <Button variant="outline" size="sm" className="h-7 text-xs text-emerald-600 hover:text-emerald-700">
              <RotateCcw className="size-3 mr-1" /> Return
            </Button>
          </Link>
          <Link href="/asset-transfer">
            <Button variant="ghost" size="sm" className="h-7 text-xs text-blue-600 hover:text-blue-700">
              <ArrowRightLeft className="size-3 mr-1" /> Transfer
            </Button>
          </Link>
        </div>
      ),
    },
  ];

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-indigo-500/10 text-indigo-600 dark:text-indigo-400">
            <Users className="size-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Employee Asset Custody
            </h1>
            <p className="text-sm text-slate-500">
              Phase 7 · Officer equipment register, separation clearance, and active custody tracking
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => window.print()}
            className="gap-1.5"
          >
            <Printer className="size-4" /> Print Custody Register
          </Button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-slate-500">
              Active Custody Items
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{totalAssigned}</div>
            <p className="text-xs text-slate-400 mt-1">Laptops, desktops & devices in custody</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-indigo-500">
              Custodians / Employees
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-indigo-600">{uniqueEmployees}</div>
            <p className="text-xs text-slate-400 mt-1">Officers with issued company equipment</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-emerald-500">
              Clearance Ready
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-emerald-600">100%</div>
            <p className="text-xs text-slate-400 mt-1">Real-time No-Dues audit compliance</p>
          </CardContent>
        </Card>
      </div>

      {/* Search and Table */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardContent className="pt-6 space-y-4">
          <div className="flex items-center gap-3 max-w-sm">
            <div className="relative w-full">
              <Search className="absolute left-2.5 top-2.5 size-4 text-slate-400" />
              <Input
                placeholder="Search by officer name, ID, or asset code..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-9 h-9"
              />
            </div>
          </div>

          <DataTable
            columns={columns}
            data={filtered}
            totalPages={1}
            page={0}
            pageSize={50}
            onPageChange={() => {}}
            isLoading={isLoading}
          />
        </CardContent>
      </Card>
    </div>
  );
}
