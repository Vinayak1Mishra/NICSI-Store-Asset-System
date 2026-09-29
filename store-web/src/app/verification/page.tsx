'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { StoreSiteResponse, PageResponse } from '@/types/master';
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
  ClipboardCheck,
  Plus,
  Play,
  CheckCircle2,
  Send,
  Building2,
  ScanBarcode,
  Layers,
  Calendar,
  AlertCircle,
  Eye,
  FileSpreadsheet,
} from 'lucide-react';
import { toast } from 'sonner';

interface VerificationRecord {
  id: string;
  verificationNo: string;
  verificationName: string;
  storeId: string;
  storeName: string;
  verificationType: string;
  startDate: string;
  endDate?: string;
  committeeReference?: string;
  status: string;
  totalItems: number;
  countedItems: number;
  createdAt: string;
}

interface VerificationItem {
  id: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  locationId: string;
  locationCode: string;
  bookQty: number;
  physicalQty?: number;
  varianceQty?: number;
  resultStatus?: string;
  conditionStatus?: string;
  remarks?: string;
}

export default function VerificationPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [storeFilter, setStoreFilter] = useState('ALL');
  const [page, setPage] = useState(0);

  // Modals
  const [createOpen, setCreateOpen] = useState(false);
  const [countModalId, setCountModalId] = useState<string | null>(null);
  const [viewId, setViewId] = useState<string | null>(null);

  // Create Form State
  const [verificationName, setVerificationName] = useState('');
  const [storeId, setStoreId] = useState('');
  const [verificationType, setVerificationType] = useState('ANNUAL');
  const [startDate, setStartDate] = useState(new Date().toISOString().split('T')[0]);
  const [committeeReference, setCommitteeReference] = useState('');

  // Counting Line State
  const [selectedItemToCount, setSelectedItemToCount] = useState<VerificationItem | null>(null);
  const [physicalQty, setPhysicalQty] = useState<number | ''>('');
  const [conditionStatus, setConditionStatus] = useState('GOOD');
  const [remarks, setRemarks] = useState('');

  // Stores Query
  const { data: stores } = useQuery<StoreSiteResponse[]>({
    queryKey: ['stores-lookup'],
    queryFn: () => api.get<StoreSiteResponse[]>('/api/store/store-sites'),
  });

  // Verifications Query
  const { data: verificationsPage, isLoading } = useQuery<PageResponse<VerificationRecord>>({
    queryKey: ['verifications', statusFilter, storeFilter, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '15' });
      if (statusFilter !== 'ALL') params.append('status', statusFilter);
      if (storeFilter !== 'ALL') params.append('storeId', storeFilter);
      return api.get<PageResponse<VerificationRecord>>(`/api/verification?${params.toString()}`);
    },
  });

  // Active Session Detail & Items
  const activeSessionId = countModalId || viewId;
  const { data: activeSession } = useQuery<VerificationRecord>({
    queryKey: ['verification-detail', activeSessionId],
    queryFn: () => api.get<VerificationRecord>(`/api/verification/${activeSessionId}`),
    enabled: !!activeSessionId,
  });

  const { data: sessionItems, refetch: refetchItems } = useQuery<VerificationItem[]>({
    queryKey: ['verification-items', activeSessionId],
    queryFn: () => api.get<VerificationItem[]>(`/api/verification/${activeSessionId}/items`),
    enabled: !!activeSessionId,
  });

  // Mutations
  const createMutation = useMutation({
    mutationFn: (body: unknown) => api.post('/api/verification', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['verifications'] });
      toast.success('Physical verification session created & stock snapshot loaded');
      setCreateOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => toast.error(err.message || 'Failed to create session'),
  });

  const startMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/verification/${id}/start`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['verifications'] });
      toast.success('Session started! Count sheet is active.');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const recordCountMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: unknown }) =>
      api.post(`/api/verification/${id}/count`, body),
    onSuccess: () => {
      refetchItems();
      queryClient.invalidateQueries({ queryKey: ['verifications'] });
      toast.success('Count recorded successfully');
      setSelectedItemToCount(null);
    },
    onError: (err: ApiError) => toast.error(err.message || 'Failed to record count'),
  });

  const submitMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/verification/${id}/submit`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['verifications'] });
      toast.success('Verification report submitted for committee approval');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const approveMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/verification/${id}/approve`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['verifications'] });
      toast.success('Verification approved and reconciled');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const resetForm = () => {
    setVerificationName('');
    setStoreId('');
    setVerificationType('ANNUAL');
    setStartDate(new Date().toISOString().split('T')[0]);
    setCommitteeReference('');
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!verificationName || !storeId) {
      toast.error('Session title and store site are required');
      return;
    }
    createMutation.mutate({
      verificationName,
      storeId,
      verificationType,
      startDate,
      committeeReference: committeeReference || undefined,
    });
  };

  const handleCountSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!countModalId || !selectedItemToCount || physicalQty === '') return;

    recordCountMutation.mutate({
      id: countModalId,
      body: {
        itemId: selectedItemToCount.itemId,
        locationId: selectedItemToCount.locationId,
        physicalQty: Number(physicalQty),
        conditionStatus,
        remarks: remarks || undefined,
      },
    });
  };

  const columns: ColumnDef<VerificationRecord>[] = [
    {
      accessorKey: 'verificationNo',
      header: 'Session No',
      cell: ({ row }) => (
        <span className="font-semibold text-blue-600 dark:text-blue-400 font-mono text-xs">
          {row.original.verificationNo}
        </span>
      ),
    },
    {
      accessorKey: 'verificationName',
      header: 'Session Name',
      cell: ({ row }) => (
        <div>
          <p className="font-medium text-slate-900 dark:text-slate-100">{row.original.verificationName}</p>
          <p className="text-[11px] text-slate-500">{row.original.verificationType} Cycle</p>
        </div>
      ),
    },
    {
      accessorKey: 'storeName',
      header: 'Store Site',
      cell: ({ row }) => (
        <span className="flex items-center gap-1.5 text-xs font-medium">
          <Building2 className="size-3.5 text-slate-400" />
          {row.original.storeName}
        </span>
      ),
    },
    {
      accessorKey: 'progress',
      header: 'Progress',
      cell: ({ row }) => {
        const total = row.original.totalItems || 1;
        const counted = row.original.countedItems || 0;
        const pct = Math.round((counted / total) * 100);
        return (
          <div className="w-28 space-y-1">
            <div className="flex justify-between text-[11px] text-slate-500">
              <span>{counted}/{total}</span>
              <span>{pct}%</span>
            </div>
            <div className="w-full bg-slate-200 dark:bg-slate-700 h-1.5 rounded-full overflow-hidden">
              <div className="bg-blue-600 h-full rounded-full" style={{ width: `${pct}%` }} />
            </div>
          </div>
        );
      },
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => <StatusBadge status={row.original.status} />,
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: ({ row }) => {
        const item = row.original;
        return (
          <div className="flex items-center gap-1.5">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setViewId(item.id)}
              className="h-8 px-2"
            >
              <Eye className="size-3.5 mr-1" /> View
            </Button>
            {item.status === 'DRAFT' && (
              <Button
                variant="outline"
                size="sm"
                className="h-8 px-2 text-blue-600"
                onClick={() => startMutation.mutate(item.id)}
              >
                <Play className="size-3.5 mr-1" /> Start Count
              </Button>
            )}
            {item.status === 'IN_PROGRESS' && (
              <>
                <Button
                  size="sm"
                  className="h-8 px-2 bg-blue-600 hover:bg-blue-700 text-white"
                  onClick={() => setCountModalId(item.id)}
                >
                  <ScanBarcode className="size-3.5 mr-1" /> Count Sheet
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-8 px-2 text-emerald-600"
                  onClick={() => submitMutation.mutate(item.id)}
                >
                  <Send className="size-3.5 mr-1" /> Submit
                </Button>
              </>
            )}
            {item.status === 'SUBMITTED' && (
              <Button
                variant="outline"
                size="sm"
                className="h-8 px-2 text-emerald-600"
                onClick={() => approveMutation.mutate(item.id)}
              >
                <CheckCircle2 className="size-3.5 mr-1" /> Approve
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-teal-500/10 text-teal-600 dark:text-teal-400">
            <ClipboardCheck className="size-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Physical Verification & Audit
            </h1>
            <p className="text-sm text-slate-500">
              Phase 9 · Periodic stock count, barcode verification, book-to-physical variance resolution
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
          <Plus className="size-4" /> New Verification Session
        </Button>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-slate-500">
              Total Sessions
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{verificationsPage?.totalElements || 0}</div>
            <p className="text-xs text-slate-400 mt-1">Audit cycles created</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-blue-500">
              In Progress
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">
              {verificationsPage?.content?.filter((v) => v.status === 'IN_PROGRESS').length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Active physical counts</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-amber-500">
              Under Review
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-amber-600">
              {verificationsPage?.content?.filter((v) => v.status === 'SUBMITTED').length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Awaiting committee approval</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-emerald-500">
              Reconciled & Closed
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-emerald-600">
              {verificationsPage?.content?.filter((v) => v.status === 'APPROVED').length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Formally approved audit</p>
          </CardContent>
        </Card>
      </div>

      {/* Filters & Data Table */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardContent className="pt-6 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-48">
              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger className="h-9">
                  <SelectValue placeholder="All Statuses" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Statuses</SelectItem>
                  <SelectItem value="DRAFT">Draft</SelectItem>
                  <SelectItem value="IN_PROGRESS">In Progress</SelectItem>
                  <SelectItem value="SUBMITTED">Submitted</SelectItem>
                  <SelectItem value="APPROVED">Approved / Reconciled</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="w-56">
              <Select value={storeFilter} onValueChange={setStoreFilter}>
                <SelectTrigger className="h-9">
                  <SelectValue placeholder="All Store Sites" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Stores</SelectItem>
                  {stores?.map((s) => (
                    <SelectItem key={s.id} value={s.id}>
                      {s.storeName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <DataTable
            columns={columns}
            data={verificationsPage?.content || []}
            pageCount={verificationsPage?.totalPages || 1}
            pageIndex={page}
            pageSize={15}
            onPageChange={setPage}
            isLoading={isLoading}
          />
        </CardContent>
      </Card>

      {/* CREATE SESSION MODAL */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ClipboardCheck className="size-5 text-teal-600" />
              New Physical Verification Cycle
            </DialogTitle>
            <DialogDescription>
              Snapshot current store inventory balances and initialize count sheets.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="space-y-4">
            <div>
              <Label className="text-xs font-semibold">Verification Title *</Label>
              <Input
                placeholder="e.g. Annual Store Audit FY 2026-27"
                value={verificationName}
                onChange={(e) => setVerificationName(e.target.value)}
                className="mt-1"
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label className="text-xs font-semibold">Store Site *</Label>
                <Select value={storeId} onValueChange={setStoreId}>
                  <SelectTrigger className="mt-1">
                    <SelectValue placeholder="Select Store" />
                  </SelectTrigger>
                  <SelectContent>
                    {stores?.map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.storeName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div>
                <Label className="text-xs font-semibold">Verification Type *</Label>
                <Select value={verificationType} onValueChange={setVerificationType}>
                  <SelectTrigger className="mt-1">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ANNUAL">Annual Statutory</SelectItem>
                    <SelectItem value="PERIODIC">Periodic (Quarterly)</SelectItem>
                    <SelectItem value="SURPRISE">Surprise Audit</SelectItem>
                    <SelectItem value="SPOT_CHECK">Spot Check</SelectItem>
                  </SelectContent>
                </Select>
              </div>
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
                <Label className="text-xs font-semibold">Committee Ref / Office Order</Label>
                <Input
                  placeholder="e.g. NICSI/AUDIT/2026/08"
                  value={committeeReference}
                  onChange={(e) => setCommitteeReference(e.target.value)}
                  className="mt-1"
                />
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={createMutation.isPending}
                className="bg-teal-600 hover:bg-teal-700 text-white"
              >
                {createMutation.isPending ? 'Initializing...' : 'Generate Count Sheet'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* COUNT SHEET MODAL */}
      <Dialog open={!!countModalId} onOpenChange={() => setCountModalId(null)}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center justify-between">
              <span className="flex items-center gap-2">
                <ScanBarcode className="size-5 text-blue-600" />
                Physical Count Sheet · {activeSession?.verificationNo}
              </span>
              <Badge variant="outline" className="text-xs">
                {activeSession?.storeName}
              </Badge>
            </DialogTitle>
            <DialogDescription>
              Record scanned quantities against book balances. System calculates variances in real time.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="border rounded-md overflow-hidden">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-100 dark:bg-slate-800 font-semibold border-b">
                  <tr>
                    <th className="p-2.5">Item</th>
                    <th className="p-2.5">Location</th>
                    <th className="p-2.5 text-right">Book Qty</th>
                    <th className="p-2.5 text-right">Physical Qty</th>
                    <th className="p-2.5 text-right">Variance</th>
                    <th className="p-2.5">Condition</th>
                    <th className="p-2.5 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {sessionItems?.map((it) => {
                    const isCounted = it.physicalQty !== null && it.physicalQty !== undefined;
                    const variance = it.varianceQty ?? 0;
                    return (
                      <tr key={it.id} className={isCounted ? 'bg-slate-50/50 dark:bg-slate-900/30' : ''}>
                        <td className="p-2.5 font-medium">
                          <p>{it.itemCode}</p>
                          <p className="text-[11px] text-slate-500">{it.itemName}</p>
                        </td>
                        <td className="p-2.5 font-mono">{it.locationCode}</td>
                        <td className="p-2.5 text-right font-mono font-semibold">{it.bookQty}</td>
                        <td className="p-2.5 text-right font-mono font-bold text-blue-600">
                          {isCounted ? it.physicalQty : '—'}
                        </td>
                        <td className="p-2.5 text-right font-mono">
                          {isCounted ? (
                            <span
                              className={
                                variance === 0
                                  ? 'text-emerald-600 font-semibold'
                                  : variance < 0
                                  ? 'text-red-600 font-semibold'
                                  : 'text-amber-600 font-semibold'
                              }
                            >
                              {variance > 0 ? `+${variance}` : variance}
                            </span>
                          ) : (
                            '—'
                          )}
                        </td>
                        <td className="p-2.5">
                          {it.conditionStatus ? (
                            <Badge variant="outline" className="text-[10px]">
                              {it.conditionStatus}
                            </Badge>
                          ) : (
                            '—'
                          )}
                        </td>
                        <td className="p-2.5 text-right">
                          <Button
                            size="sm"
                            variant={isCounted ? 'outline' : 'default'}
                            className={`h-7 px-2 text-xs ${
                              !isCounted ? 'bg-blue-600 hover:bg-blue-700 text-white' : ''
                            }`}
                            onClick={() => {
                              setSelectedItemToCount(it);
                              setPhysicalQty(it.physicalQty ?? it.bookQty);
                              setConditionStatus(it.conditionStatus || 'GOOD');
                              setRemarks(it.remarks || '');
                            }}
                          >
                            {isCounted ? 'Edit' : 'Record'}
                          </Button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setCountModalId(null)}>
              Close Sheet
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* RECORD SINGLE COUNT MODAL */}
      <Dialog open={!!selectedItemToCount} onOpenChange={() => setSelectedItemToCount(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="text-sm font-bold">
              Record Physical Count · {selectedItemToCount?.itemCode}
            </DialogTitle>
            <DialogDescription className="text-xs">
              {selectedItemToCount?.itemName} at Location {selectedItemToCount?.locationCode}
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCountSubmit} className="space-y-3">
            <div className="p-2.5 rounded bg-slate-100 dark:bg-slate-800 text-xs flex justify-between">
              <span className="text-slate-500">Book Quantity (Snapshot):</span>
              <span className="font-mono font-bold text-slate-800 dark:text-slate-200">
                {selectedItemToCount?.bookQty}
              </span>
            </div>

            <div>
              <Label className="text-xs font-semibold">Physical Counted Quantity *</Label>
              <Input
                type="number"
                min="0"
                value={physicalQty}
                onChange={(e) => setPhysicalQty(e.target.value === '' ? '' : Number(e.target.value))}
                className="mt-1 font-mono text-base font-bold"
                required
                autoFocus
              />
            </div>

            <div>
              <Label className="text-xs font-semibold">Physical Condition</Label>
              <Select value={conditionStatus} onValueChange={setConditionStatus}>
                <SelectTrigger className="mt-1">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="GOOD">Good / Serviceable</SelectItem>
                  <SelectItem value="FAIR">Fair (Slight Wear)</SelectItem>
                  <SelectItem value="DAMAGED">Damaged</SelectItem>
                  <SelectItem value="SCRAP">Scrap / Beyond Economic Repair</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label className="text-xs font-semibold">Audit Remarks / Notes</Label>
              <Input
                placeholder="e.g. Serial tag verified, minor scratch on chassis"
                value={remarks}
                onChange={(e) => setRemarks(e.target.value)}
                className="mt-1 text-xs"
              />
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setSelectedItemToCount(null)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={recordCountMutation.isPending}
                className="bg-blue-600 hover:bg-blue-700 text-white"
              >
                Save Count
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* VIEW REPORT MODAL */}
      <Dialog open={!!viewId} onOpenChange={() => setViewId(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle className="flex items-center justify-between">
              <span>Verification Report {activeSession?.verificationNo}</span>
              {activeSession && <StatusBadge status={activeSession.status} />}
            </DialogTitle>
            <DialogDescription>
              {activeSession?.verificationName} · {activeSession?.storeName}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 text-xs">
            <div className="grid grid-cols-3 gap-3 p-3 rounded-lg bg-slate-50 dark:bg-slate-900 border">
              <div>
                <span className="text-slate-500">Cycle Type</span>
                <p className="font-semibold">{activeSession?.verificationType}</p>
              </div>
              <div>
                <span className="text-slate-500">Start Date</span>
                <p className="font-medium">{activeSession?.startDate}</p>
              </div>
              <div>
                <span className="text-slate-500">Committee Reference</span>
                <p className="font-medium">{activeSession?.committeeReference || 'N/A'}</p>
              </div>
            </div>

            <div className="border rounded-md overflow-hidden max-h-72 overflow-y-auto">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-100 dark:bg-slate-800 font-semibold border-b sticky top-0">
                  <tr>
                    <th className="p-2">Item</th>
                    <th className="p-2 text-right">Book</th>
                    <th className="p-2 text-right">Physical</th>
                    <th className="p-2 text-right">Variance</th>
                    <th className="p-2">Result</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {sessionItems?.map((it) => (
                    <tr key={it.id}>
                      <td className="p-2">
                        <p className="font-semibold">{it.itemCode}</p>
                        <p className="text-[10px] text-slate-500 truncate max-w-[200px]">{it.itemName}</p>
                      </td>
                      <td className="p-2 text-right font-mono">{it.bookQty}</td>
                      <td className="p-2 text-right font-mono font-bold">{it.physicalQty ?? '—'}</td>
                      <td className="p-2 text-right font-mono font-bold">
                        {it.varianceQty ?? '—'}
                      </td>
                      <td className="p-2">
                        <Badge
                          variant="outline"
                          className={
                            it.resultStatus === 'FOUND'
                              ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                              : it.resultStatus === 'MISSING'
                              ? 'bg-red-50 text-red-700 border-red-200'
                              : 'bg-amber-50 text-amber-700 border-amber-200'
                          }
                        >
                          {it.resultStatus || 'PENDING'}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

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
