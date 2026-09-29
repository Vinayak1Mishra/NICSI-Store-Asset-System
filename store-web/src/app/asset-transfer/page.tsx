'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { AssetSummaryResponse } from '@/types/asset';
import { PageResponse, StorageLocationResponse } from '@/types/master';
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
  UserCheck,
  Building,
  FolderGit2,
  Calendar,
  Send,
  Search,
  Laptop,
  ArrowRightLeft,
  CheckCircle2,
} from 'lucide-react';
import { toast } from 'sonner';

export default function AssetTransferPage() {
  const queryClient = useQueryClient();
  const [searchTerm, setSearchTerm] = useState('');
  const [page, setPage] = useState(0);

  // Transfer Dialog State
  const [selectedAsset, setSelectedAsset] = useState<AssetSummaryResponse | null>(null);
  const [targetCustodianUserId, setTargetCustodianUserId] = useState('');
  const [targetDepartmentId, setTargetDepartmentId] = useState('');
  const [targetProjectId, setTargetProjectId] = useState('');
  const [targetLocationId, setTargetLocationId] = useState('');
  const [transferDate, setTransferDate] = useState(new Date().toISOString().split('T')[0]);
  const [reason, setReason] = useState('');

  // Fetch Assets
  const { data: assetsPage, isLoading } = useQuery<PageResponse<AssetSummaryResponse>>({
    queryKey: ['transferable-assets', searchTerm, page],
    queryFn: () => {
      const params = new URLSearchParams({
        page: String(page),
        size: '15',
        assetStatus: 'ISSUED',
      });
      if (searchTerm) params.append('search', searchTerm);
      return api.get<PageResponse<AssetSummaryResponse>>(`/api/assets?${params.toString()}`);
    },
  });

  // Transfer Mutation
  const transferMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: unknown }) =>
      api.post(`/api/assets/${id}/transfer`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transferable-assets'] });
      toast.success('Asset custody transferred successfully');
      setSelectedAsset(null);
      resetForm();
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Custody transfer failed');
    },
  });

  const resetForm = () => {
    setTargetCustodianUserId('');
    setTargetDepartmentId('');
    setTargetProjectId('');
    setTargetLocationId('');
    setReason('');
  };

  const handleTransferSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAsset) return;
    if (!targetCustodianUserId && !targetDepartmentId && !targetProjectId) {
      toast.error('Specify at least a new custodian, department, or project');
      return;
    }

    transferMutation.mutate({
      id: selectedAsset.id,
      body: {
        targetCustodianUserId: targetCustodianUserId || undefined,
        targetDepartmentId: targetDepartmentId || undefined,
        targetProjectId: targetProjectId || undefined,
        targetLocationId: targetLocationId || undefined,
        transferDate,
        reason,
      },
    });
  };

  const columns: ColumnDef<AssetSummaryResponse>[] = [
    {
      accessorKey: 'assetCode',
      header: 'Asset Code',
      cell: ({ row }) => (
        <span className="font-semibold text-blue-600 dark:text-blue-400 font-mono text-xs">
          {row.original.assetCode}
        </span>
      ),
    },
    {
      accessorKey: 'itemName',
      header: 'Equipment',
      cell: ({ row }) => (
        <div>
          <p className="font-medium text-slate-900 dark:text-slate-100">{row.original.itemName}</p>
          <p className="text-[11px] text-slate-500 font-mono">
            SN: {row.original.serialNumber || 'N/A'}
          </p>
        </div>
      ),
    },
    {
      accessorKey: 'currentCustodianName',
      header: 'Current Custodian',
      cell: ({ row }) => (
        <span className="flex items-center gap-1.5 text-xs font-medium">
          <UserCheck className="size-3.5 text-slate-400" />
          {row.original.currentCustodianName || 'Store Custody'}
        </span>
      ),
    },
    {
      accessorKey: 'storeName',
      header: 'Home Store / Site',
      cell: ({ row }) => (
        <span className="text-xs text-slate-600 dark:text-slate-400">
          {row.original.storeName}
        </span>
      ),
    },
    {
      accessorKey: 'conditionStatus',
      header: 'Condition',
      cell: ({ row }) => <StatusBadge status={row.original.conditionStatus} />,
    },
    {
      id: 'actions',
      header: 'Action',
      cell: ({ row }) => (
        <Button
          size="sm"
          className="h-8 gap-1.5 bg-blue-600 hover:bg-blue-700 text-white"
          onClick={() => {
            setSelectedAsset(row.original);
            resetForm();
          }}
        >
          <ArrowRightLeft className="size-3.5" /> Transfer Custody
        </Button>
      ),
    },
  ];

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400">
            <ArrowRightLeft className="size-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Asset Custody Transfer
            </h1>
            <p className="text-sm text-slate-500">
              Phase 7 · Hand-over protocol and custody transfer between employees & projects
            </p>
          </div>
        </div>
      </div>

      {/* Info Banner */}
      <Card className="border-blue-200 bg-blue-50/50 dark:border-blue-900 dark:bg-blue-950/20">
        <CardContent className="p-4 flex items-start gap-3 text-xs text-blue-800 dark:text-blue-200">
          <CheckCircle2 className="size-5 shrink-0 text-blue-600 mt-0.5" />
          <div>
            <p className="font-semibold">Custody Transfer Governance</p>
            <p className="mt-0.5 text-blue-700/80 dark:text-blue-300/80">
              Transferring asset custody terminates prior active assignment and creates an immutable hand-over log in the central audit ledger. The recipient will be notified for digital acceptance.
            </p>
          </div>
        </CardContent>
      </Card>

      {/* Search & Table */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardContent className="pt-6 space-y-4">
          <div className="flex items-center gap-3 max-w-sm">
            <div className="relative w-full">
              <Search className="absolute left-2.5 top-2.5 size-4 text-slate-400" />
              <Input
                placeholder="Search by asset code, serial, or employee..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-9 h-9"
              />
            </div>
          </div>

          <DataTable
            columns={columns}
            data={assetsPage?.content || []}
            pageCount={assetsPage?.totalPages || 1}
            pageIndex={page}
            pageSize={15}
            onPageChange={setPage}
            isLoading={isLoading}
          />
        </CardContent>
      </Card>

      {/* TRANSFER MODAL */}
      <Dialog open={!!selectedAsset} onOpenChange={() => setSelectedAsset(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ArrowRightLeft className="size-5 text-blue-600" />
              Transfer Custody · {selectedAsset?.assetCode}
            </DialogTitle>
            <DialogDescription>
              Assign {selectedAsset?.itemName} to a new responsible officer or project.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleTransferSubmit} className="space-y-4">
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900 border text-xs space-y-1">
              <div className="flex justify-between">
                <span className="text-slate-500">Asset:</span>
                <span className="font-semibold">{selectedAsset?.itemName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Current Custodian:</span>
                <span className="font-semibold text-amber-600">
                  {selectedAsset?.currentCustodianName || 'Store Custody'}
                </span>
              </div>
            </div>

            <div className="space-y-3">
              <div>
                <Label className="text-xs font-semibold">New Custodian User ID (UUID) *</Label>
                <Input
                  placeholder="e.g. 00000000-0000-0000-0000-000000000002"
                  value={targetCustodianUserId}
                  onChange={(e) => setTargetCustodianUserId(e.target.value)}
                  className="mt-1 font-mono text-xs"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label className="text-xs font-semibold">Target Department ID</Label>
                  <Input
                    placeholder="Optional department UUID"
                    value={targetDepartmentId}
                    onChange={(e) => setTargetDepartmentId(e.target.value)}
                    className="mt-1 font-mono text-xs"
                  />
                </div>

                <div>
                  <Label className="text-xs font-semibold">Target Project ID</Label>
                  <Input
                    placeholder="Optional project UUID"
                    value={targetProjectId}
                    onChange={(e) => setTargetProjectId(e.target.value)}
                    className="mt-1 font-mono text-xs"
                  />
                </div>
              </div>

              <div>
                <Label className="text-xs font-semibold">Transfer Effective Date *</Label>
                <Input
                  type="date"
                  value={transferDate}
                  onChange={(e) => setTransferDate(e.target.value)}
                  className="mt-1"
                  required
                />
              </div>

              <div>
                <Label className="text-xs font-semibold">Handover Justification / Remarks *</Label>
                <Textarea
                  placeholder="Reason for transfer (e.g. employee role change, project reassignment)..."
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  rows={3}
                  className="mt-1"
                  required
                />
              </div>
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setSelectedAsset(null)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={transferMutation.isPending}
                className="bg-blue-600 hover:bg-blue-700 text-white"
              >
                {transferMutation.isPending ? 'Transferring...' : 'Execute Transfer'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
