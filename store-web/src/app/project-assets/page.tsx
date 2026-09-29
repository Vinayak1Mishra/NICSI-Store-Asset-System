'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import { AssetSummaryResponse } from '@/types/asset';
import { PageResponse, StoreSiteResponse } from '@/types/master';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { StatusBadge } from '@/components/common/status-badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  FolderGit2,
  Search,
  Building2,
  Laptop,
  ArrowRightLeft,
  RotateCcw,
  Layers,
  HardDrive,
} from 'lucide-react';
import Link from 'next/link';

export default function ProjectAssetsPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [storeFilter, setStoreFilter] = useState('ALL');
  const [conditionFilter, setConditionFilter] = useState('ALL');
  const [page, setPage] = useState(0);

  const { data: stores } = useQuery<StoreSiteResponse[]>({
    queryKey: ['stores-lookup'],
    queryFn: () => api.get<StoreSiteResponse[]>('/api/store/store-sites'),
  });

  const { data: assetsPage, isLoading } = useQuery<PageResponse<AssetSummaryResponse>>({
    queryKey: ['project-assets', searchTerm, storeFilter, conditionFilter, page],
    queryFn: () => {
      const params = new URLSearchParams({
        page: String(page),
        size: '15',
      });
      if (storeFilter !== 'ALL') params.append('storeId', storeFilter);
      if (conditionFilter !== 'ALL') params.append('conditionStatus', conditionFilter);
      if (searchTerm) params.append('search', searchTerm);
      return api.get<PageResponse<AssetSummaryResponse>>(`/api/assets?${params.toString()}`);
    },
  });

  const columns: ColumnDef<AssetSummaryResponse>[] = [
    {
      accessorKey: 'assetCode',
      header: 'Asset Code',
      cell: (row) => (
        <span className="font-mono text-xs font-semibold text-blue-600 dark:text-blue-400">
          {row.assetCode}
        </span>
      ),
    },
    {
      accessorKey: 'itemName',
      header: 'Hardware / Facility Item',
      cell: (row) => (
        <div>
          <p className="font-medium text-slate-900 dark:text-slate-100">{row.itemName}</p>
          <p className="text-[11px] text-slate-500 font-mono">
            {row.itemCode} · SN: {row.serialNumber || 'N/A'}
          </p>
        </div>
      ),
    },
    {
      accessorKey: 'storeCode',
      header: 'Project Store / Site',
      cell: (row) => (
        <span className="flex items-center gap-1.5 text-xs font-medium">
          <Building2 className="size-3.5 text-slate-400" />
          {row.storeCode}
        </span>
      ),
    },
    {
      accessorKey: 'locationCode',
      header: 'Rack / Bay',
      cell: (row) => (
        <Badge variant="outline" className="font-mono text-xs">
          {row.locationCode}
        </Badge>
      ),
    },
    {
      accessorKey: 'conditionStatus',
      header: 'Condition',
      cell: (row) => <StatusBadge status={row.conditionStatus} />,
    },
    {
      accessorKey: 'assetStatus',
      header: 'Status',
      cell: (row) => <StatusBadge status={row.assetStatus} />,
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: (row) => (
        <div className="flex items-center gap-1.5">
          <Link href="/asset-transfer">
            <Button variant="outline" size="sm" className="h-7 text-xs text-blue-600 hover:text-blue-700">
              <ArrowRightLeft className="size-3 mr-1" /> Reallocate
            </Button>
          </Link>
          <Link href={`/returns?assetCode=${encodeURIComponent(row.assetCode)}`}>
            <Button variant="ghost" size="sm" className="h-7 text-xs text-slate-600 hover:text-slate-800">
              <RotateCcw className="size-3 mr-1" /> Demobilize
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
          <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
            <FolderGit2 className="size-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Project & Facility Assets
            </h1>
            <p className="text-sm text-slate-500">
              Phase 7 · Server rooms, data center racks, and project deployment infrastructure
            </p>
          </div>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-slate-500">
              Total Hardware Assets
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{assetsPage?.totalElements || 0}</div>
            <p className="text-xs text-slate-400 mt-1">Capitalized items</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-emerald-500">
              Good Condition
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-emerald-600">
              {assetsPage?.content?.filter((a) => a.conditionStatus === 'GOOD').length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Fully operational</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-blue-500">
              In-Use / Deployed
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">
              {assetsPage?.content?.filter((a) => ['ISSUED', 'IN_SERVICE'].includes(a.assetStatus)).length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Allocated to initiatives</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-amber-500">
              Store Available
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-amber-600">
              {assetsPage?.content?.filter((a) => a.assetStatus === 'AVAILABLE').length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Ready for deployment</p>
          </CardContent>
        </Card>
      </div>

      {/* Filters & Data Table */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardContent className="pt-6 space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative w-64">
              <Search className="absolute left-2.5 top-2.5 size-4 text-slate-400" />
              <Input
                placeholder="Search equipment, code, serial..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-9 h-9"
              />
            </div>

            <div className="w-56">
              <Select value={storeFilter} onValueChange={setStoreFilter}>
                <SelectTrigger className="h-9">
                  <SelectValue placeholder="All Sites & Stores" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Sites & Stores</SelectItem>
                  {stores?.map((s) => (
                    <SelectItem key={s.id} value={s.id}>
                      {s.storeName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="w-44">
              <Select value={conditionFilter} onValueChange={setConditionFilter}>
                <SelectTrigger className="h-9">
                  <SelectValue placeholder="All Conditions" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Conditions</SelectItem>
                  <SelectItem value="GOOD">Good</SelectItem>
                  <SelectItem value="FAIR">Fair</SelectItem>
                  <SelectItem value="POOR">Poor</SelectItem>
                  <SelectItem value="DAMAGED">Damaged</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <DataTable
            columns={columns}
            data={assetsPage?.content || []}
            totalPages={assetsPage?.totalPages || 1}
            page={page}
            pageSize={15}
            onPageChange={setPage}
            isLoading={isLoading}
          />
        </CardContent>
      </Card>
    </div>
  );
}
