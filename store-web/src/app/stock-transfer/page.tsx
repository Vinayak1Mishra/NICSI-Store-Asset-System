'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { StoreSiteResponse, ItemResponse, StorageLocationResponse, PageResponse } from '@/types/master';
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
  ArrowLeftRight,
  Plus,
  Eye,
  Trash2,
  Building2,
  Calendar,
  Layers,
  Send,
  CheckCircle2,
  Truck,
  PackageCheck,
  AlertCircle,
} from 'lucide-react';
import { toast } from 'sonner';

interface TransferLineItem {
  id?: string;
  itemId: string;
  itemCode?: string;
  itemName?: string;
  sourceLocationId: string;
  sourceLocationCode?: string;
  destinationLocationId: string;
  destinationLocationCode?: string;
  transferQty: number;
  receivedQty?: number;
  remarks?: string;
}

interface TransferRecord {
  id: string;
  transferNo: string;
  sourceStoreId: string;
  sourceStoreName: string;
  destinationStoreId: string;
  destinationStoreName: string;
  transferDate: string;
  status: string;
  itemCount: number;
  dispatchDate?: string;
  receiveDate?: string;
  remarks?: string;
  items?: TransferLineItem[];
  createdAt: string;
}

export default function StockTransferPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [sourceStoreFilter, setSourceStoreFilter] = useState('ALL');
  const [page, setPage] = useState(0);

  // Modals
  const [createOpen, setCreateOpen] = useState(false);
  const [viewId, setViewId] = useState<string | null>(null);
  const [receiveModalId, setReceiveModalId] = useState<string | null>(null);

  // Form State
  const [sourceStoreId, setSourceStoreId] = useState('');
  const [destinationStoreId, setDestinationStoreId] = useState('');
  const [transferDate, setTransferDate] = useState(new Date().toISOString().split('T')[0]);
  const [remarks, setRemarks] = useState('');
  const [lines, setLines] = useState<TransferLineItem[]>([]);

  // Receive line inputs
  const [receiveQuantities, setReceiveQuantities] = useState<Record<string, number>>({});

  // Reference Queries
  const { data: stores } = useQuery<PageResponse<StoreSiteResponse>>({
    queryKey: ['stores-lookup'],
    queryFn: () => api.get<PageResponse<StoreSiteResponse>>('/api/store/stores?size=200'),
  });

  const { data: items } = useQuery<PageResponse<ItemResponse>>({
    queryKey: ['items-lookup'],
    queryFn: () => api.get<PageResponse<ItemResponse>>('/api/store/items?size=200'),
  });

  const { data: sourceLocations } = useQuery<PageResponse<StorageLocationResponse>>({
    queryKey: ['source-locations', sourceStoreId],
    queryFn: () => api.get<PageResponse<StorageLocationResponse>>(`/api/store/locations?storeId=${sourceStoreId}&size=200`),
    enabled: !!sourceStoreId,
  });

  const { data: destLocations } = useQuery<PageResponse<StorageLocationResponse>>({
    queryKey: ['dest-locations', destinationStoreId],
    queryFn: () => api.get<PageResponse<StorageLocationResponse>>(`/api/store/locations?storeId=${destinationStoreId}&size=200`),
    enabled: !!destinationStoreId,
  });

  // Main Transfers Query
  const { data: transfersPage, isLoading } = useQuery<PageResponse<TransferRecord>>({
    queryKey: ['stock-transfers', statusFilter, sourceStoreFilter, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '15' });
      if (statusFilter !== 'ALL') params.append('status', statusFilter);
      if (sourceStoreFilter !== 'ALL') params.append('sourceStoreId', sourceStoreFilter);
      return api.get<PageResponse<TransferRecord>>(`/api/transfers?${params.toString()}`);
    },
  });

  // Single Transfer Query for View
  const { data: activeTransfer } = useQuery<TransferRecord>({
    queryKey: ['stock-transfer-detail', viewId || receiveModalId],
    queryFn: () => api.get<TransferRecord>(`/api/transfers/${viewId || receiveModalId}`),
    enabled: !!(viewId || receiveModalId),
  });

  // Mutations
  const createMutation = useMutation({
    mutationFn: (body: unknown) => api.post('/api/transfers', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock-transfers'] });
      toast.success('Stock transfer order created successfully');
      setCreateOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create transfer');
    },
  });

  const submitMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/transfers/${id}/submit`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock-transfers'] });
      queryClient.invalidateQueries({ queryKey: ['stock-transfer-detail'] });
      toast.success('Transfer submitted for approval');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const approveMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/transfers/${id}/approve`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock-transfers'] });
      queryClient.invalidateQueries({ queryKey: ['stock-transfer-detail'] });
      toast.success('Transfer approved');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const dispatchMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/transfers/${id}/dispatch`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock-transfers'] });
      queryClient.invalidateQueries({ queryKey: ['stock-transfer-detail'] });
      toast.success('Transfer dispatched! Stock ledger updated.');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const receiveMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: unknown }) => api.post(`/api/transfers/${id}/receive`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock-transfers'] });
      queryClient.invalidateQueries({ queryKey: ['stock-transfer-detail'] });
      toast.success('Transfer received! Destination inventory updated.');
      setReceiveModalId(null);
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const resetForm = () => {
    setSourceStoreId('');
    setDestinationStoreId('');
    setTransferDate(new Date().toISOString().split('T')[0]);
    setRemarks('');
    setLines([]);
  };

  const addLine = () => {
    setLines((prev) => [
      ...prev,
      {
        itemId: '',
        sourceLocationId: '',
        destinationLocationId: '',
        transferQty: 1,
        remarks: '',
      },
    ]);
  };

  const removeLine = (idx: number) => {
    setLines((prev) => prev.filter((_, i) => i !== idx));
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!sourceStoreId || !destinationStoreId) {
      toast.error('Select both source and destination stores');
      return;
    }
    if (sourceStoreId === destinationStoreId) {
      toast.error('Source and destination store cannot be the same');
      return;
    }
    if (lines.length === 0) {
      toast.error('Add at least one transfer item line');
      return;
    }
    for (const line of lines) {
      if (!line.itemId || !line.sourceLocationId || !line.destinationLocationId || !line.transferQty || line.transferQty <= 0) {
        toast.error('Please complete all line item fields with valid quantities');
        return;
      }
    }

    createMutation.mutate({
      sourceStoreId,
      destinationStoreId,
      transferDate,
      remarks,
      lines: lines.map((l) => ({
        itemId: l.itemId,
        sourceLocationId: l.sourceLocationId,
        destinationLocationId: l.destinationLocationId,
        transferQty: Number(l.transferQty),
        remarks: l.remarks,
      })),
    });
  };

  const handleReceiveSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!receiveModalId || !activeTransfer?.items) return;

    const payloadLines = activeTransfer.items.map((i) => ({
      lineId: i.id,
      receivedQty: receiveQuantities[i.id!] ?? i.transferQty,
    }));

    receiveMutation.mutate({
      id: receiveModalId,
      body: { lines: payloadLines },
    });
  };

  const columns: ColumnDef<TransferRecord>[] = [
    {
      accessorKey: 'transferNo',
      header: 'Transfer No',
      cell: (row) => (
        <span className="font-semibold text-blue-600 dark:text-blue-400">
          {row.transferNo}
        </span>
      ),
    },
    {
      accessorKey: 'sourceStoreName',
      header: 'Source Store',
      cell: (row) => (
        <span className="flex items-center gap-1.5 font-medium">
          <Building2 className="size-3.5 text-slate-400" />
          {row.sourceStoreName}
        </span>
      ),
    },
    {
      accessorKey: 'destinationStoreName',
      header: 'Destination Store',
      cell: (row) => (
        <span className="flex items-center gap-1.5 font-medium">
          <Building2 className="size-3.5 text-amber-500" />
          {row.destinationStoreName}
        </span>
      ),
    },
    {
      accessorKey: 'transferDate',
      header: 'Date',
      cell: (row) => (
        <span className="text-xs text-slate-500">{row.transferDate}</span>
      ),
    },
    {
      accessorKey: 'itemCount',
      header: 'Lines',
      cell: (row) => (
        <Badge variant="secondary" className="font-mono text-xs">
          {row.itemCount || 1} items
        </Badge>
      ),
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: (row) => <StatusBadge status={row.status} />,
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: (row) => {
        const item = row;
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
                onClick={() => submitMutation.mutate(item.id)}
              >
                <Send className="size-3.5 mr-1" /> Submit
              </Button>
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
            {item.status === 'APPROVED' && (
              <Button
                variant="outline"
                size="sm"
                className="h-8 px-2 text-amber-600"
                onClick={() => dispatchMutation.mutate(item.id)}
              >
                <Truck className="size-3.5 mr-1" /> Dispatch
              </Button>
            )}
            {item.status === 'DISPATCHED' && (
              <Button
                size="sm"
                className="h-8 px-2 bg-emerald-600 hover:bg-emerald-700 text-white"
                onClick={() => {
                  setReceiveModalId(item.id);
                  const initial: Record<string, number> = {};
                  item.items?.forEach((i) => {
                    if (i.id) initial[i.id] = i.transferQty;
                  });
                  setReceiveQuantities(initial);
                }}
              >
                <PackageCheck className="size-3.5 mr-1" /> Receive
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
        <div>
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400">
              <ArrowLeftRight className="size-6" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
                Stock Transfers
              </h1>
              <p className="text-sm text-slate-500">
                Phase 3 & 4 · Store-to-store inventory movement and transit custody
              </p>
            </div>
          </div>
        </div>
        <Button
          onClick={() => {
            resetForm();
            addLine();
            setCreateOpen(true);
          }}
          className="gap-2 bg-blue-600 hover:bg-blue-700 text-white shadow-sm"
        >
          <Plus className="size-4" /> Create Transfer
        </Button>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-slate-500">
              Total Transfers
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{transfersPage?.totalElements || 0}</div>
            <p className="text-xs text-slate-400 mt-1">Movement orders</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-amber-500">
              In Transit
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-amber-600">
              {transfersPage?.content?.filter((t) => t.status === 'DISPATCHED').length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Dispatched, pending receipt</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-emerald-500">
              Completed
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-emerald-600">
              {transfersPage?.content?.filter((t) => t.status === 'RECEIVED').length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Stock received & posted</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-blue-500">
              Pending Action
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">
              {transfersPage?.content?.filter((t) => ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(t.status)).length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Draft or pending approval</p>
          </CardContent>
        </Card>
      </div>

      {/* Filters & Data Table */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardContent className="pt-6 space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <div className="w-48">
              <Label className="text-xs text-slate-500 mb-1 block">Filter Status</Label>
              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger className="h-9">
                  <SelectValue placeholder="All Statuses" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Statuses</SelectItem>
                  <SelectItem value="DRAFT">Draft</SelectItem>
                  <SelectItem value="SUBMITTED">Submitted</SelectItem>
                  <SelectItem value="APPROVED">Approved</SelectItem>
                  <SelectItem value="DISPATCHED">Dispatched (In-Transit)</SelectItem>
                  <SelectItem value="RECEIVED">Received</SelectItem>
                  <SelectItem value="CANCELLED">Cancelled</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="w-56">
              <Label className="text-xs text-slate-500 mb-1 block">Source Store</Label>
              <Select value={sourceStoreFilter} onValueChange={setSourceStoreFilter}>
                <SelectTrigger className="h-9">
                  <SelectValue placeholder="All Source Stores" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Stores</SelectItem>
                  {stores?.content?.map((s) => (
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
            data={transfersPage?.content || []}
            totalPages={transfersPage?.totalPages || 1}
            page={page}
            pageSize={15}
            onPageChange={setPage}
            isLoading={isLoading}
          />
        </CardContent>
      </Card>

      {/* CREATE MODAL */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ArrowLeftRight className="size-5 text-blue-600" />
              New Inter-Store Stock Transfer
            </DialogTitle>
            <DialogDescription>
              Initiate a stock movement request between two authorized NICSI warehouse sites.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="space-y-5">
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div>
                <Label className="text-xs font-semibold">Source Store *</Label>
                <Select value={sourceStoreId} onValueChange={setSourceStoreId}>
                  <SelectTrigger className="mt-1">
                    <SelectValue placeholder="Select Origin Store" />
                  </SelectTrigger>
                  <SelectContent>
                    {stores?.content?.map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.storeName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div>
                <Label className="text-xs font-semibold">Destination Store *</Label>
                <Select value={destinationStoreId} onValueChange={setDestinationStoreId}>
                  <SelectTrigger className="mt-1">
                    <SelectValue placeholder="Select Destination" />
                  </SelectTrigger>
                  <SelectContent>
                    {stores?.content
                      ?.filter((s) => s.id !== sourceStoreId)
                      .map((s) => (
                        <SelectItem key={s.id} value={s.id}>
                          {s.storeName}
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
              </div>

              <div>
                <Label className="text-xs font-semibold">Transfer Date *</Label>
                <Input
                  type="date"
                  value={transferDate}
                  onChange={(e) => setTransferDate(e.target.value)}
                  className="mt-1"
                  required
                />
              </div>
            </div>

            <div>
              <Label className="text-xs font-semibold">Purpose / Reason Remarks</Label>
              <Textarea
                placeholder="Reason for transfer (e.g. project mobilization, balancing regional stores)..."
                value={remarks}
                onChange={(e) => setRemarks(e.target.value)}
                rows={2}
                className="mt-1"
              />
            </div>

            {/* Line Items */}
            <div className="space-y-3 pt-2">
              <div className="flex items-center justify-between border-b pb-2">
                <h4 className="text-sm font-semibold text-slate-800 dark:text-slate-200">
                  Transfer Line Items
                </h4>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={addLine}
                  className="h-8 gap-1 text-xs"
                >
                  <Plus className="size-3.5" /> Add Item Line
                </Button>
              </div>

              {lines.map((line, idx) => (
                <div
                  key={idx}
                  className="p-3 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900/50 space-y-3"
                >
                  <div className="grid grid-cols-1 sm:grid-cols-12 gap-3 items-end">
                    <div className="sm:col-span-4">
                      <Label className="text-xs">Item *</Label>
                      <Select
                        value={line.itemId}
                        onValueChange={(val) => {
                          const updated = [...lines];
                          updated[idx].itemId = val;
                          setLines(updated);
                        }}
                      >
                        <SelectTrigger className="mt-1 h-9">
                          <SelectValue placeholder="Select Catalog Item" />
                        </SelectTrigger>
                        <SelectContent>
                          {items?.content?.map((it) => (
                            <SelectItem key={it.id} value={it.id}>
                              {it.itemCode} - {it.itemName}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="sm:col-span-3">
                      <Label className="text-xs">Origin Location *</Label>
                      <Select
                        value={line.sourceLocationId}
                        onValueChange={(val) => {
                          const updated = [...lines];
                          updated[idx].sourceLocationId = val;
                          setLines(updated);
                        }}
                        disabled={!sourceStoreId}
                      >
                        <SelectTrigger className="mt-1 h-9">
                          <SelectValue placeholder="Source Rack/Bin" />
                        </SelectTrigger>
                        <SelectContent>
                          {sourceLocations?.content?.map((loc) => (
                            <SelectItem key={loc.id} value={loc.id}>
                              {loc.locationCode} - {loc.locationName}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="sm:col-span-3">
                      <Label className="text-xs">Dest Location *</Label>
                      <Select
                        value={line.destinationLocationId}
                        onValueChange={(val) => {
                          const updated = [...lines];
                          updated[idx].destinationLocationId = val;
                          setLines(updated);
                        }}
                        disabled={!destinationStoreId}
                      >
                        <SelectTrigger className="mt-1 h-9">
                          <SelectValue placeholder="Dest Rack/Bin" />
                        </SelectTrigger>
                        <SelectContent>
                          {destLocations?.content?.map((loc) => (
                            <SelectItem key={loc.id} value={loc.id}>
                              {loc.locationCode} - {loc.locationName}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="sm:col-span-2">
                      <Label className="text-xs">Qty *</Label>
                      <div className="flex items-center gap-1.5 mt-1">
                        <Input
                          type="number"
                          min="1"
                          value={line.transferQty}
                          onChange={(e) => {
                            const updated = [...lines];
                            updated[idx].transferQty = Number(e.target.value);
                            setLines(updated);
                          }}
                          className="h-9"
                          required
                        />
                        {lines.length > 1 && (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            onClick={() => removeLine(idx)}
                            className="text-red-500 hover:text-red-600 size-9 shrink-0"
                          >
                            <Trash2 className="size-4" />
                          </Button>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setCreateOpen(false)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={createMutation.isPending}
                className="bg-blue-600 hover:bg-blue-700 text-white"
              >
                {createMutation.isPending ? 'Saving...' : 'Create Transfer Order'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* VIEW MODAL */}
      <Dialog open={!!viewId} onOpenChange={() => setViewId(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle className="flex items-center justify-between">
              <span>Transfer {activeTransfer?.transferNo}</span>
              {activeTransfer && <StatusBadge status={activeTransfer.status} />}
            </DialogTitle>
            <DialogDescription>
              Detailed store movement itinerary and material list
            </DialogDescription>
          </DialogHeader>

          {activeTransfer && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4 p-4 rounded-lg bg-slate-50 dark:bg-slate-900 border text-sm">
                <div>
                  <span className="text-xs text-slate-500">Source Store</span>
                  <p className="font-semibold text-slate-900 dark:text-slate-100">
                    {activeTransfer.sourceStoreName}
                  </p>
                </div>
                <div>
                  <span className="text-xs text-slate-500">Destination Store</span>
                  <p className="font-semibold text-slate-900 dark:text-slate-100">
                    {activeTransfer.destinationStoreName}
                  </p>
                </div>
                <div>
                  <span className="text-xs text-slate-500">Transfer Date</span>
                  <p className="font-medium">{activeTransfer.transferDate}</p>
                </div>
                <div>
                  <span className="text-xs text-slate-500">Dispatched / Received</span>
                  <p className="font-medium text-xs text-slate-600 dark:text-slate-400">
                    {activeTransfer.dispatchDate || 'Not dispatched'} /{' '}
                    {activeTransfer.receiveDate || 'Pending'}
                  </p>
                </div>
              </div>

              {activeTransfer.remarks && (
                <p className="text-xs text-slate-600 italic">
                  Remarks: {activeTransfer.remarks}
                </p>
              )}

              <div className="border rounded-md overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 dark:bg-slate-800 text-slate-600 font-semibold border-b">
                    <tr>
                      <th className="p-2.5">Item</th>
                      <th className="p-2.5">Source Loc</th>
                      <th className="p-2.5">Dest Loc</th>
                      <th className="p-2.5 text-right">Transfer Qty</th>
                      <th className="p-2.5 text-right">Received Qty</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {activeTransfer.items?.map((it, idx) => (
                      <tr key={idx}>
                        <td className="p-2.5 font-medium">
                          {it.itemCode} - {it.itemName}
                        </td>
                        <td className="p-2.5">{it.sourceLocationCode}</td>
                        <td className="p-2.5">{it.destinationLocationCode}</td>
                        <td className="p-2.5 text-right font-mono font-semibold">
                          {it.transferQty}
                        </td>
                        <td className="p-2.5 text-right font-mono text-emerald-600 font-semibold">
                          {it.receivedQty ?? 0}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setViewId(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* RECEIVE MODAL */}
      <Dialog open={!!receiveModalId} onOpenChange={() => setReceiveModalId(null)}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-emerald-600">
              <PackageCheck className="size-5" />
              Confirm Stock Receipt · {activeTransfer?.transferNo}
            </DialogTitle>
            <DialogDescription>
              Verify received quantities at {activeTransfer?.destinationStoreName}.
              Upon confirmation, stock balances will increment automatically.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleReceiveSubmit} className="space-y-4">
            <div className="border rounded-md overflow-hidden">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-100 dark:bg-slate-800 font-semibold border-b">
                  <tr>
                    <th className="p-2.5">Item</th>
                    <th className="p-2.5 text-right">Dispatched Qty</th>
                    <th className="p-2.5 text-right w-32">Received Qty</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {activeTransfer?.items?.map((it) => (
                    <tr key={it.id}>
                      <td className="p-2.5">
                        <p className="font-semibold text-slate-800 dark:text-slate-200">
                          {it.itemCode}
                        </p>
                        <p className="text-[11px] text-slate-500">{it.itemName}</p>
                      </td>
                      <td className="p-2.5 text-right font-mono font-bold">
                        {it.transferQty}
                      </td>
                      <td className="p-2.5 text-right">
                        <Input
                          type="number"
                          min="0"
                          max={it.transferQty}
                          value={receiveQuantities[it.id!] ?? it.transferQty}
                          onChange={(e) =>
                            setReceiveQuantities({
                              ...receiveQuantities,
                              [it.id!]: Number(e.target.value),
                            })
                          }
                          className="h-8 text-right font-mono font-bold"
                          required
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setReceiveModalId(null)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={receiveMutation.isPending}
                className="bg-emerald-600 hover:bg-emerald-700 text-white"
              >
                {receiveMutation.isPending ? 'Posting Receipt...' : 'Confirm & Post to Stock'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
