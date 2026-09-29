'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { AssetSummaryResponse } from '@/types/asset';
import { PageResponse } from '@/types/master';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { StatusBadge } from '@/components/common/status-badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  ShieldCheck,
  Plus,
  Eye,
  Calendar,
  AlertTriangle,
  Building2,
  Clock,
  Laptop,
} from 'lucide-react';
import { toast } from 'sonner';

interface WarrantyRecord {
  id: string;
  contractNumber: string;
  contractType: string;
  assetId: string;
  assetCode: string;
  itemName: string;
  vendorNameSnapshot: string;
  startDate: string;
  endDate: string;
  status: string;
  coverageDetail?: string;
  cost?: number;
  slaHours?: number;
  createdAt: string;
}

export default function WarrantyPage() {
  const queryClient = useQueryClient();
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(0);

  // Modals
  const [createOpen, setCreateOpen] = useState(false);
  const [viewId, setViewId] = useState<string | null>(null);

  // Create Form State
  const [assetId, setAssetId] = useState('');
  const [contractType, setContractType] = useState('WARRANTY');
  const [contractNumber, setContractNumber] = useState('');
  const [vendorName, setVendorName] = useState('');
  const [startDate, setStartDate] = useState(new Date().toISOString().split('T')[0]);
  const [endDate, setEndDate] = useState('');
  const [coverageDetail, setCoverageDetail] = useState('');
  const [cost, setCost] = useState<number | ''>('');
  const [slaHours, setSlaHours] = useState<number | ''>(24);

  // Query assets for dropdown
  const { data: assets } = useQuery<PageResponse<AssetSummaryResponse>>({
    queryKey: ['assets-for-warranty'],
    queryFn: () => api.get<PageResponse<AssetSummaryResponse>>('/api/assets?size=100'),
  });

  // Query warranties
  const { data: warrantiesPage, isLoading } = useQuery<PageResponse<WarrantyRecord>>({
    queryKey: ['warranties', typeFilter, statusFilter, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '15' });
      if (typeFilter !== 'ALL') params.append('type', typeFilter);
      if (statusFilter !== 'ALL') params.append('status', statusFilter);
      return api.get<PageResponse<WarrantyRecord>>(`/api/warranties?${params.toString()}`);
    },
  });

  // Query single warranty
  const { data: activeRecord } = useQuery<WarrantyRecord>({
    queryKey: ['warranty-detail', viewId],
    queryFn: () => api.get<WarrantyRecord>(`/api/warranties/${viewId}`),
    enabled: !!viewId,
  });

  // Create Mutation
  const createMutation = useMutation({
    mutationFn: (body: unknown) => api.post('/api/warranties', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['warranties'] });
      toast.success('Warranty/AMC contract registered successfully');
      setCreateOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => toast.error(err.message || 'Failed to create contract'),
  });

  const resetForm = () => {
    setAssetId('');
    setContractType('WARRANTY');
    setContractNumber('');
    setVendorName('');
    setStartDate(new Date().toISOString().split('T')[0]);
    setEndDate('');
    setCoverageDetail('');
    setCost('');
    setSlaHours(24);
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!assetId || !contractNumber || !startDate || !endDate) {
      toast.error('Asset, contract number, and valid date range are required');
      return;
    }
    if (new Date(endDate) <= new Date(startDate)) {
      toast.error('End date must be after start date');
      return;
    }

    createMutation.mutate({
      assetId,
      contractType,
      contractNumber,
      vendorNameSnapshot: vendorName || undefined,
      startDate,
      endDate,
      coverageDetail: coverageDetail || undefined,
      cost: cost !== '' ? Number(cost) : undefined,
      slaHours: slaHours !== '' ? Number(slaHours) : undefined,
    });
  };

  const columns: ColumnDef<WarrantyRecord>[] = [
    {
      accessorKey: 'contractNumber',
      header: 'Contract No',
      cell: ({ row }) => (
        <span className="font-semibold text-blue-600 dark:text-blue-400 font-mono text-xs">
          {row.original.contractNumber}
        </span>
      ),
    },
    {
      accessorKey: 'contractType',
      header: 'Type',
      cell: ({ row }) => (
        <Badge
          variant="outline"
          className={
            row.original.contractType === 'AMC'
              ? 'bg-purple-50 text-purple-700 border-purple-200'
              : 'bg-blue-50 text-blue-700 border-blue-200'
          }
        >
          {row.original.contractType}
        </Badge>
      ),
    },
    {
      accessorKey: 'assetCode',
      header: 'Asset',
      cell: ({ row }) => (
        <div>
          <p className="font-semibold text-slate-900 dark:text-slate-100 font-mono text-xs">
            {row.original.assetCode}
          </p>
          <p className="text-[11px] text-slate-500 truncate max-w-[200px]">
            {row.original.itemName}
          </p>
        </div>
      ),
    },
    {
      accessorKey: 'vendorNameSnapshot',
      header: 'Provider / Vendor',
      cell: ({ row }) => (
        <span className="text-xs font-medium">{row.original.vendorNameSnapshot || 'OEM Direct'}</span>
      ),
    },
    {
      accessorKey: 'endDate',
      header: 'Valid Till',
      cell: ({ row }) => {
        const isExpiringSoon =
          new Date(row.original.endDate).getTime() - Date.now() < 30 * 24 * 60 * 60 * 1000;
        return (
          <span
            className={`text-xs font-medium ${
              isExpiringSoon ? 'text-amber-600 font-bold' : 'text-slate-600'
            }`}
          >
            {row.original.endDate}
          </span>
        );
      },
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => <StatusBadge status={row.original.status || 'ACTIVE'} />,
    },
    {
      id: 'actions',
      header: 'Action',
      cell: ({ row }) => (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setViewId(row.original.id)}
          className="h-8 px-2"
        >
          <Eye className="size-3.5 mr-1" /> View
        </Button>
      ),
    },
  ];

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
            <ShieldCheck className="size-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Warranty & AMC Contracts
            </h1>
            <p className="text-sm text-slate-500">
              Phase 7 · Hardware OEM warranties, annual maintenance contracts (AMC), and SLA management
            </p>
          </div>
        </div>
        <Button
          onClick={() => {
            resetForm();
            setCreateOpen(true);
          }}
          className="gap-2 bg-blue-600 hover:bg-blue-700 text-white shadow-sm"
        >
          <Plus className="size-4" /> Register Warranty / AMC
        </Button>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-slate-500">
              Total Contracts
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{warrantiesPage?.totalElements || 0}</div>
            <p className="text-xs text-slate-400 mt-1">Active and archived agreements</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-emerald-500">
              Active Coverage
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-emerald-600">
              {warrantiesPage?.content?.filter((w) => w.status !== 'EXPIRED').length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Protected equipment</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-purple-500">
              Annual Maintenance (AMC)
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-purple-600">
              {warrantiesPage?.content?.filter((w) => w.contractType === 'AMC').length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Vendor SLAs in place</p>
          </CardContent>
        </Card>
      </div>

      {/* Filters & Data Table */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardContent className="pt-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-44">
              <Select value={typeFilter} onValueChange={setTypeFilter}>
                <SelectTrigger className="h-9">
                  <SelectValue placeholder="All Contract Types" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Types</SelectItem>
                  <SelectItem value="WARRANTY">Warranty</SelectItem>
                  <SelectItem value="AMC">AMC</SelectItem>
                  <SelectItem value="SUPPORT">Extended Support</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="w-44">
              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger className="h-9">
                  <SelectValue placeholder="All Statuses" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Statuses</SelectItem>
                  <SelectItem value="ACTIVE">Active</SelectItem>
                  <SelectItem value="EXPIRED">Expired</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <DataTable
            columns={columns}
            data={warrantiesPage?.content || []}
            pageCount={warrantiesPage?.totalPages || 1}
            pageIndex={page}
            pageSize={15}
            onPageChange={setPage}
            isLoading={isLoading}
          />
        </CardContent>
      </Card>

      {/* CREATE MODAL */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ShieldCheck className="size-5 text-emerald-600" />
              Register Warranty / AMC Contract
            </DialogTitle>
            <DialogDescription>
              Link an active OEM warranty or third-party maintenance agreement to an asset.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="space-y-4">
            <div>
              <Label className="text-xs font-semibold">Select Asset *</Label>
              <Select value={assetId} onValueChange={setAssetId}>
                <SelectTrigger className="mt-1">
                  <SelectValue placeholder="Select Asset by Code" />
                </SelectTrigger>
                <SelectContent className="max-h-60">
                  {assets?.content?.map((a) => (
                    <SelectItem key={a.id} value={a.id}>
                      {a.assetCode} - {a.itemName} (SN: {a.serialNumber || 'N/A'})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label className="text-xs font-semibold">Contract Type *</Label>
                <Select value={contractType} onValueChange={setContractType}>
                  <SelectTrigger className="mt-1">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="WARRANTY">OEM Warranty</SelectItem>
                    <SelectItem value="AMC">Comprehensive AMC</SelectItem>
                    <SelectItem value="SUPPORT">Software/OS Support</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div>
                <Label className="text-xs font-semibold">Contract / Policy No. *</Label>
                <Input
                  placeholder="e.g. AMC-2026-DEL-04"
                  value={contractNumber}
                  onChange={(e) => setContractNumber(e.target.value)}
                  className="mt-1"
                  required
                />
              </div>
            </div>

            <div>
              <Label className="text-xs font-semibold">Vendor / Service Provider</Label>
              <Input
                placeholder="e.g. Dell India Pvt Ltd, CMS IT Services"
                value={vendorName}
                onChange={(e) => setVendorName(e.target.value)}
                className="mt-1"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label className="text-xs font-semibold">Start Date *</Label>
                <Input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="mt-1"
                  required
                />
              </div>

              <div>
                <Label className="text-xs font-semibold">End Date *</Label>
                <Input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  className="mt-1"
                  required
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label className="text-xs font-semibold">Contract Amount (₹)</Label>
                <Input
                  type="number"
                  min="0"
                  placeholder="0.00"
                  value={cost}
                  onChange={(e) => setCost(e.target.value === '' ? '' : Number(e.target.value))}
                  className="mt-1 font-mono"
                />
              </div>

              <div>
                <Label className="text-xs font-semibold">Resolution SLA (Hours)</Label>
                <Input
                  type="number"
                  min="1"
                  placeholder="24"
                  value={slaHours}
                  onChange={(e) => setSlaHours(e.target.value === '' ? '' : Number(e.target.value))}
                  className="mt-1 font-mono"
                />
              </div>
            </div>

            <div>
              <Label className="text-xs font-semibold">Coverage Scope & Terms</Label>
              <Textarea
                placeholder="e.g. Comprehensive coverage including motherboard, panel replacement, 24x7 onsite..."
                value={coverageDetail}
                onChange={(e) => setCoverageDetail(e.target.value)}
                rows={2}
                className="mt-1"
              />
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={createMutation.isPending}
                className="bg-blue-600 hover:bg-blue-700 text-white"
              >
                {createMutation.isPending ? 'Registering...' : 'Register Contract'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* VIEW MODAL */}
      <Dialog open={!!viewId} onOpenChange={() => setViewId(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center justify-between">
              <span>Contract {activeRecord?.contractNumber}</span>
              {activeRecord && <StatusBadge status={activeRecord.status || 'ACTIVE'} />}
            </DialogTitle>
            <DialogDescription>Hardware coverage terms and SLA</DialogDescription>
          </DialogHeader>

          {activeRecord && (
            <div className="space-y-4 text-xs">
              <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900 border space-y-2">
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <span className="text-slate-500">Asset:</span>
                    <p className="font-semibold">{activeRecord.assetCode} - {activeRecord.itemName}</p>
                  </div>
                  <div>
                    <span className="text-slate-500">Provider:</span>
                    <p className="font-medium">{activeRecord.vendorNameSnapshot || 'OEM'}</p>
                  </div>
                  <div>
                    <span className="text-slate-500">Period:</span>
                    <p className="font-medium">{activeRecord.startDate} to {activeRecord.endDate}</p>
                  </div>
                  <div>
                    <span className="text-slate-500">SLA:</span>
                    <p className="font-medium">{activeRecord.slaHours || 24} Hours turnaround</p>
                  </div>
                </div>
              </div>

              {activeRecord.coverageDetail && (
                <div>
                  <span className="font-semibold text-slate-700 dark:text-slate-300">Coverage Terms:</span>
                  <p className="p-2 rounded bg-slate-100 dark:bg-slate-800 mt-1">
                    {activeRecord.coverageDetail}
                  </p>
                </div>
              )}
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setViewId(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
