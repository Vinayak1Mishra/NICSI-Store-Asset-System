'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { useAuth } from '@/hooks/use-auth';
import {
  StockAdjustmentSummaryResponse,
  StockAdjustmentResponse,
  CreateStockAdjustmentRequest,
  StockAdjustmentItemRequest,
} from '@/types/adjustment';
import { StoreSiteResponse, StorageLocationResponse, ItemResponse, PageResponse } from '@/types/master';
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
  SlidersHorizontal,
  Plus,
  Send,
  CheckCircle,
  FileCheck,
  RotateCcw,
  Eye,
  Trash2,
  AlertCircle,
  ShieldAlert,
} from 'lucide-react';
import { toast } from 'sonner';

const REASON_CODES = [
  { code: 'PHYSICAL_COUNT_VARIANCE', label: 'Physical Count Variance (Stock-taking)' },
  { code: 'DAMAGE_EXPIRY', label: 'Damaged / Expired Stock' },
  { code: 'CORRECTION_DATA_ENTRY', label: 'Data Entry / Posting Correction' },
  { code: 'RECLASSIFICATION', label: 'Stock Reclassification' },
  { code: 'WRITE_OFF', label: 'Inventory Write-off' },
];

interface FormAdjustmentLine {
  itemId: string;
  itemCode: string;
  itemName: string;
  locationId: string;
  locationCode: string;
  direction: 'IN' | 'OUT';
  quantity: number;
  unitCost: number;
  remarks: string;
}

export default function StockAdjustmentsPage() {
  const queryClient = useQueryClient();
  const { user, hasPermission } = useAuth();

  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [selectedStore, setSelectedStore] = useState<string>('ALL');
  const [page, setPage] = useState(0);

  // Dialogs
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [isReverseOpen, setIsReverseOpen] = useState(false);
  const [selectedAdjId, setSelectedAdjId] = useState<string | null>(null);
  const [reversalReason, setReversalReason] = useState('');

  // Form State
  const [formStoreId, setFormStoreId] = useState('');
  const [formReasonCode, setFormReasonCode] = useState('PHYSICAL_COUNT_VARIANCE');
  const [formRemarks, setFormRemarks] = useState('');
  const [formLines, setFormLines] = useState<FormAdjustmentLine[]>([]);

  // Line item selector state
  const [selectedItemId, setSelectedItemId] = useState('');
  const [selectedLocationId, setSelectedLocationId] = useState('');
  const [selectedDirection, setSelectedDirection] = useState<'IN' | 'OUT'>('IN');
  const [selectedQty, setSelectedQty] = useState<string>('1');
  const [selectedUnitCost, setSelectedUnitCost] = useState<string>('0');
  const [lineRemarks, setLineRemarks] = useState('');

  // Master Data Lookups
  const { data: stores } = useQuery<PageResponse<StoreSiteResponse>>({
    queryKey: ['stores-lookup'],
    queryFn: () => api.get<PageResponse<StoreSiteResponse>>('/api/store/stores?size=100'),
  });

  const { data: items } = useQuery<PageResponse<ItemResponse>>({
    queryKey: ['items-lookup'],
    queryFn: () => api.get<PageResponse<ItemResponse>>('/api/store/items?size=200'),
  });

  const { data: locations } = useQuery<PageResponse<StorageLocationResponse>>({
    queryKey: ['locations-lookup', formStoreId],
    queryFn: () => api.get<PageResponse<StorageLocationResponse>>(`/api/store/locations?storeId=${formStoreId}&size=100`),
    enabled: !!formStoreId,
  });

  // Adjustments List Query
  const { data: adjustmentsData, isLoading } = useQuery<PageResponse<StockAdjustmentSummaryResponse>>({
    queryKey: ['stock-adjustments', statusFilter, selectedStore, page],
    queryFn: () => {
      const params = new URLSearchParams();
      if (statusFilter !== 'ALL') params.append('status', statusFilter);
      if (selectedStore !== 'ALL') params.append('storeId', selectedStore);
      params.append('page', page.toString());
      params.append('size', '15');
      return api.get<PageResponse<StockAdjustmentSummaryResponse>>(`/api/store/adjustments?${params.toString()}`);
    },
  });

  // Detail Query
  const { data: selectedAdjustment, isLoading: isDetailLoading } = useQuery<StockAdjustmentResponse>({
    queryKey: ['stock-adjustment-detail', selectedAdjId],
    queryFn: () => api.get<StockAdjustmentResponse>(`/api/store/adjustments/${selectedAdjId}`),
    enabled: !!selectedAdjId,
  });

  // Action Mutations
  const createMutation = useMutation({
    mutationFn: (req: CreateStockAdjustmentRequest) => api.post<StockAdjustmentResponse>('/api/store/adjustments', req),
    onSuccess: () => {
      toast.success('Stock adjustment created successfully');
      setIsCreateOpen(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['stock-adjustments'] });
    },
    onError: (err: unknown) => {
      handleApiError(err, 'Failed to create adjustment');
    },
  });

  const submitMutation = useMutation({
    mutationFn: (id: string) => api.post<StockAdjustmentResponse>(`/api/store/adjustments/${id}/submit`),
    onSuccess: () => {
      toast.success('Adjustment submitted for approval');
      queryClient.invalidateQueries({ queryKey: ['stock-adjustments'] });
      queryClient.invalidateQueries({ queryKey: ['stock-adjustment-detail'] });
    },
    onError: (err: unknown) => handleApiError(err, 'Failed to submit adjustment'),
  });

  const approveMutation = useMutation({
    mutationFn: (id: string) => api.post<StockAdjustmentResponse>(`/api/store/adjustments/${id}/approve`),
    onSuccess: () => {
      toast.success('Adjustment approved successfully');
      queryClient.invalidateQueries({ queryKey: ['stock-adjustments'] });
      queryClient.invalidateQueries({ queryKey: ['stock-adjustment-detail'] });
    },
    onError: (err: unknown) => handleApiError(err, 'Failed to approve adjustment'),
  });

  const postMutation = useMutation({
    mutationFn: (id: string) => api.post<StockAdjustmentResponse>(`/api/store/adjustments/${id}/post`),
    onSuccess: () => {
      toast.success('Adjustment posted to inventory ledger & balance');
      queryClient.invalidateQueries({ queryKey: ['stock-adjustments'] });
      queryClient.invalidateQueries({ queryKey: ['stock-adjustment-detail'] });
    },
    onError: (err: unknown) => handleApiError(err, 'Failed to post adjustment'),
  });

  const reverseMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      api.post<StockAdjustmentResponse>(`/api/store/adjustments/${id}/reversal`, { reason }),
    onSuccess: () => {
      toast.success('Reversal posted successfully');
      setIsReverseOpen(false);
      setReversalReason('');
      queryClient.invalidateQueries({ queryKey: ['stock-adjustments'] });
      queryClient.invalidateQueries({ queryKey: ['stock-adjustment-detail'] });
    },
    onError: (err: unknown) => handleApiError(err, 'Failed to reverse transaction'),
  });

  const handleApiError = (err: unknown, defaultMsg: string) => {
    if (err instanceof ApiError) {
      if (err.code === 'MAKER_CHECKER_VIOLATION') {
        toast.error('Maker-Checker Violation: You cannot approve, post, or reverse an adjustment you created.', {
          icon: <ShieldAlert className="size-5 text-rose-500" />,
        });
        return;
      }
      if (err.code === 'ACCESS_DENIED' || err.status === 403) {
        toast.error('Access Denied: Your role lacks permission (STOCK_ADJUST required).', {
          icon: <AlertCircle className="size-5 text-amber-500" />,
        });
        return;
      }
      toast.error(`${err.message} (${err.code})`);
    } else {
      toast.error(defaultMsg);
    }
  };

  const resetForm = () => {
    setFormStoreId('');
    setFormReasonCode('PHYSICAL_COUNT_VARIANCE');
    setFormRemarks('');
    setFormLines([]);
    setSelectedItemId('');
    setSelectedLocationId('');
    setSelectedDirection('IN');
    setSelectedQty('1');
    setSelectedUnitCost('0');
    setLineRemarks('');
  };

  const addLine = () => {
    if (!selectedItemId || !selectedLocationId || !selectedQty || Number(selectedQty) <= 0) {
      toast.error('Please select item, location and valid quantity');
      return;
    }
    const item = items?.content?.find((i) => i.id === selectedItemId);
    const loc = locations?.content?.find((l) => l.id === selectedLocationId);
    if (!item || !loc) return;

    setFormLines([
      ...formLines,
      {
        itemId: item.id,
        itemCode: item.itemCode,
        itemName: item.itemName,
        locationId: loc.id,
        locationCode: loc.locationCode,
        direction: selectedDirection,
        quantity: Number(selectedQty),
        unitCost: Number(selectedUnitCost || 0),
        remarks: lineRemarks,
      },
    ]);

    setSelectedItemId('');
    setSelectedQty('1');
    setSelectedUnitCost('0');
    setLineRemarks('');
  };

  const removeLine = (index: number) => {
    setFormLines(formLines.filter((_, i) => i !== index));
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formStoreId) {
      toast.error('Please select a store');
      return;
    }
    if (formLines.length === 0) {
      toast.error('Please add at least one line item');
      return;
    }

    const payload: CreateStockAdjustmentRequest = {
      storeId: formStoreId,
      reasonCode: formReasonCode,
      remarks: formRemarks,
      items: formLines.map((l) => ({
        itemId: l.itemId,
        locationId: l.locationId,
        direction: l.direction,
        quantity: l.quantity,
        unitCost: l.unitCost,
        remarks: l.remarks,
      })),
    };

    createMutation.mutate(payload);
  };

  const isSelfAction = (creatorId?: string) => {
    if (!user || !creatorId) return false;
    return user.userId.toLowerCase() === creatorId.toLowerCase();
  };

  const canCreate = hasPermission('STOCK_ADJUST') || hasPermission('STORE_ADMIN');

  const columns: ColumnDef<StockAdjustmentSummaryResponse>[] = [
    {
      header: 'Adjustment No',
      accessorKey: 'adjustmentNo',
      cell: (row) => (
        <div>
          <div className="font-mono font-semibold text-slate-900">{row.adjustmentNo}</div>
          <div className="text-xs text-slate-500">
            {row.adjustmentDate ? new Date(row.adjustmentDate).toLocaleDateString('en-IN') : '-'}
          </div>
        </div>
      ),
    },
    {
      header: 'Store',
      accessorKey: 'storeCode',
      cell: (row) => (
        <div>
          <div className="font-medium text-slate-800">{row.storeCode}</div>
          <div className="text-xs text-slate-500">{row.storeName}</div>
        </div>
      ),
    },
    {
      header: 'Reason',
      accessorKey: 'reasonCode',
      cell: (row) => (
        <Badge variant="outline" className="text-[11px] font-mono bg-slate-50 text-slate-700">
          {row.reasonCode}
        </Badge>
      ),
    },
    {
      header: 'Lines',
      accessorKey: 'totalLines',
      cell: (row) => <div className="font-mono text-center font-medium">{row.totalLines}</div>,
    },
    {
      header: 'Status',
      accessorKey: 'status',
      cell: (row) => <StatusBadge status={row.status} />,
    },
    {
      header: 'Actions',
      cell: (row) => {
        const isMaker = isSelfAction(row.createdBy);
        return (
          <div className="flex items-center gap-1.5">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setSelectedAdjId(row.id);
                setIsDetailOpen(true);
              }}
              className="h-8 px-2 text-xs"
            >
              <Eye className="size-3.5 mr-1" /> View
            </Button>

            {row.status === 'DRAFT' && canCreate && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => submitMutation.mutate(row.id)}
                disabled={submitMutation.isPending}
                className="h-8 px-2 text-xs text-blue-600 border-blue-200 hover:bg-blue-50"
              >
                <Send className="size-3.5 mr-1" /> Submit
              </Button>
            )}

            {row.status === 'SUBMITTED' && canCreate && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => approveMutation.mutate(row.id)}
                disabled={approveMutation.isPending || isMaker}
                title={isMaker ? 'Maker-checker: creator cannot approve own adjustment' : 'Approve adjustment'}
                className={`h-8 px-2 text-xs text-emerald-600 border-emerald-200 hover:bg-emerald-50 ${isMaker ? 'opacity-50 cursor-not-allowed' : ''}`}
              >
                <CheckCircle className="size-3.5 mr-1" /> Approve
              </Button>
            )}

            {row.status === 'APPROVED' && canCreate && (
              <Button
                variant="default"
                size="sm"
                onClick={() => postMutation.mutate(row.id)}
                disabled={postMutation.isPending || isMaker}
                title={isMaker ? 'Maker-checker: creator cannot post own adjustment' : 'Post to stock'}
                className={`h-8 px-2 text-xs bg-indigo-600 hover:bg-indigo-700 ${isMaker ? 'opacity-50 cursor-not-allowed' : ''}`}
              >
                <FileCheck className="size-3.5 mr-1" /> Post Stock
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 flex items-center gap-2">
            <SlidersHorizontal className="size-6 text-blue-600" />
            Stock Adjustments
          </h1>
          <p className="text-sm text-slate-500">
            Physical variances, damage/expiry write-offs, and data corrections with strict Maker-Checker enforcement.
          </p>
        </div>
        <div>
          <Button
            onClick={() => setIsCreateOpen(true)}
            disabled={!canCreate}
            className="gap-2 bg-blue-600 hover:bg-blue-700 text-white shadow-sm"
          >
            <Plus className="size-4" /> New Adjustment
          </Button>
        </div>
      </div>

      {/* Filters Card */}
      <Card>
        <CardContent className="pt-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <div className="space-y-1.5">
              <Label className="text-xs text-slate-600">Status</Label>
              <Select
                value={statusFilter}
                onValueChange={(val) => {
                  setStatusFilter(val);
                  setPage(0);
                }}
              >
                <SelectTrigger className="text-sm">
                  <SelectValue placeholder="All Statuses" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Statuses</SelectItem>
                  <SelectItem value="DRAFT">DRAFT</SelectItem>
                  <SelectItem value="SUBMITTED">SUBMITTED</SelectItem>
                  <SelectItem value="APPROVED">APPROVED</SelectItem>
                  <SelectItem value="POSTED">POSTED</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs text-slate-600">Store</Label>
              <Select
                value={selectedStore}
                onValueChange={(val) => {
                  setSelectedStore(val);
                  setPage(0);
                }}
              >
                <SelectTrigger className="text-sm">
                  <SelectValue placeholder="All Stores" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Stores</SelectItem>
                  {stores?.content?.map((s) => (
                    <SelectItem key={s.id} value={s.id}>
                      {s.storeCode} - {s.storeName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Adjustments Table */}
      <DataTable
        data={adjustmentsData?.content || []}
        columns={columns}
        isLoading={isLoading}
        totalElements={adjustmentsData?.totalElements || 0}
        totalPages={adjustmentsData?.totalPages || 0}
        page={page}
        onPageChange={setPage}
      />

      {/* Create Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Create Stock Adjustment</DialogTitle>
            <DialogDescription>
              Record manual physical count adjustments or stock corrections.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label className="text-xs font-medium text-slate-700">Store Site *</Label>
                <Select value={formStoreId} onValueChange={setFormStoreId}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select Store" />
                  </SelectTrigger>
                  <SelectContent>
                    {stores?.content?.map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.storeCode} - {s.storeName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label className="text-xs font-medium text-slate-700">Reason Code *</Label>
                <Select value={formReasonCode} onValueChange={setFormReasonCode}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select Reason" />
                  </SelectTrigger>
                  <SelectContent>
                    {REASON_CODES.map((r) => (
                      <SelectItem key={r.code} value={r.code}>
                        {r.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs font-medium text-slate-700">Remarks / Justification</Label>
              <Textarea
                placeholder="Explain the background for this adjustment..."
                value={formRemarks}
                onChange={(e) => setFormRemarks(e.target.value)}
                rows={2}
                className="text-sm"
              />
            </div>

            {/* Line items section */}
            <div className="border border-slate-200 rounded-md p-3 bg-slate-50/50 space-y-3">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">Add Line Items</h3>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="space-y-1">
                  <Label className="text-[11px] text-slate-600">Item</Label>
                  <Select value={selectedItemId} onValueChange={setSelectedItemId}>
                    <SelectTrigger className="text-xs">
                      <SelectValue placeholder="Select Item" />
                    </SelectTrigger>
                    <SelectContent>
                      {items?.content?.map((it) => (
                        <SelectItem key={it.id} value={it.id} className="text-xs">
                          {it.itemCode} - {it.itemName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1">
                  <Label className="text-[11px] text-slate-600">Location</Label>
                  <Select value={selectedLocationId} onValueChange={setSelectedLocationId} disabled={!formStoreId}>
                    <SelectTrigger className="text-xs">
                      <SelectValue placeholder="Select Location" />
                    </SelectTrigger>
                    <SelectContent>
                      {locations?.content?.map((loc) => (
                        <SelectItem key={loc.id} value={loc.id} className="text-xs">
                          {loc.locationCode} - {loc.locationName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1">
                  <Label className="text-[11px] text-slate-600">Direction</Label>
                  <Select value={selectedDirection} onValueChange={(val: 'IN' | 'OUT') => setSelectedDirection(val)}>
                    <SelectTrigger className="text-xs">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="IN" className="text-emerald-700 font-medium">IN (Add Stock)</SelectItem>
                      <SelectItem value="OUT" className="text-rose-700 font-medium">OUT (Deduct Stock)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="space-y-1">
                  <Label className="text-[11px] text-slate-600">Quantity</Label>
                  <Input
                    type="number"
                    min="0.001"
                    step="any"
                    value={selectedQty}
                    onChange={(e) => setSelectedQty(e.target.value)}
                    className="text-xs font-mono"
                  />
                </div>

                <div className="space-y-1">
                  <Label className="text-[11px] text-slate-600">Unit Cost (₹)</Label>
                  <Input
                    type="number"
                    min="0"
                    step="0.01"
                    value={selectedUnitCost}
                    onChange={(e) => setSelectedUnitCost(e.target.value)}
                    className="text-xs font-mono"
                  />
                </div>

                <div className="space-y-1">
                  <Label className="text-[11px] text-slate-600">Line Remarks</Label>
                  <Input
                    placeholder="Optional note"
                    value={lineRemarks}
                    onChange={(e) => setLineRemarks(e.target.value)}
                    className="text-xs"
                  />
                </div>
              </div>

              <Button type="button" variant="outline" size="sm" onClick={addLine} className="w-full text-xs">
                <Plus className="size-3.5 mr-1" /> Add Line to Adjustment
              </Button>
            </div>

            {/* Lines Preview Table */}
            {formLines.length > 0 && (
              <div className="border border-slate-200 rounded-md overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 text-slate-700 font-medium">
                    <tr>
                      <th className="p-2">Item</th>
                      <th className="p-2">Location</th>
                      <th className="p-2">Direction</th>
                      <th className="p-2">Qty</th>
                      <th className="p-2">Unit Cost</th>
                      <th className="p-2">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {formLines.map((l, idx) => (
                      <tr key={idx}>
                        <td className="p-2 font-medium">{l.itemCode}</td>
                        <td className="p-2">{l.locationCode}</td>
                        <td className="p-2">
                          <Badge variant="outline" className={l.direction === 'IN' ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'}>
                            {l.direction}
                          </Badge>
                        </td>
                        <td className="p-2 font-mono">{l.quantity}</td>
                        <td className="p-2 font-mono">₹{l.unitCost}</td>
                        <td className="p-2">
                          <Button variant="ghost" size="sm" onClick={() => removeLine(idx)} className="h-6 w-6 p-0 text-rose-600">
                            <Trash2 className="size-3" />
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending || formLines.length === 0} className="bg-blue-600 text-white">
                Create Adjustment
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Detail Dialog */}
      <Dialog open={isDetailOpen} onOpenChange={setIsDetailOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Adjustment Details</DialogTitle>
            <DialogDescription>
              {selectedAdjustment?.adjustmentNo} · {selectedAdjustment?.storeCode} ({selectedAdjustment?.storeName})
            </DialogDescription>
          </DialogHeader>

          {selectedAdjustment && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-slate-50 p-3 rounded-md text-xs">
                <div>
                  <span className="text-slate-500">Status:</span>
                  <div className="mt-0.5"><StatusBadge status={selectedAdjustment.status} /></div>
                </div>
                <div>
                  <span className="text-slate-500">Reason:</span>
                  <div className="font-semibold text-slate-800 font-mono mt-0.5">{selectedAdjustment.reasonCode}</div>
                </div>
                <div>
                  <span className="text-slate-500">Date:</span>
                  <div className="font-medium text-slate-800 mt-0.5">{new Date(selectedAdjustment.adjustmentDate).toLocaleDateString('en-IN')}</div>
                </div>
                <div>
                  <span className="text-slate-500">Lines:</span>
                  <div className="font-mono font-semibold text-slate-800 mt-0.5">{selectedAdjustment.totalLines}</div>
                </div>
              </div>

              {selectedAdjustment.remarks && (
                <div className="text-xs text-slate-600 bg-slate-50 p-2.5 rounded border border-slate-200">
                  <span className="font-semibold text-slate-700">Remarks: </span>
                  {selectedAdjustment.remarks}
                </div>
              )}

              {/* Items list */}
              <div className="border border-slate-200 rounded-md overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 text-slate-700 font-medium">
                    <tr>
                      <th className="p-2">Line #</th>
                      <th className="p-2">Item</th>
                      <th className="p-2">Location</th>
                      <th className="p-2">Direction</th>
                      <th className="p-2">Quantity</th>
                      <th className="p-2">Unit Cost</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 font-mono">
                    {selectedAdjustment.items?.map((item) => (
                      <tr key={item.id}>
                        <td className="p-2">{item.lineNo}</td>
                        <td className="p-2 font-sans font-medium">{item.itemCode} - {item.itemName}</td>
                        <td className="p-2">{item.locationCode}</td>
                        <td className="p-2">
                          <Badge variant="outline" className={item.direction === 'IN' ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'}>
                            {item.direction}
                          </Badge>
                        </td>
                        <td className="p-2">{Number(item.quantity).toFixed(2)}</td>
                        <td className="p-2">₹{Number(item.unitCost || 0).toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Action buttons inside detail */}
              <div className="flex justify-between items-center pt-2">
                <div className="text-[11px] text-slate-400">
                  Version: {selectedAdjustment.version}
                </div>
                <div className="flex gap-2">
                  {selectedAdjustment.status === 'POSTED' && canCreate && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setIsReverseOpen(true)}
                      disabled={isSelfAction(selectedAdjustment.postedBy)}
                      title={isSelfAction(selectedAdjustment.postedBy) ? 'Maker-checker: poster cannot reverse own transaction' : 'Reverse transaction'}
                      className="text-amber-700 border-amber-300 hover:bg-amber-50 text-xs"
                    >
                      <RotateCcw className="size-3.5 mr-1" /> Reverse Posting
                    </Button>
                  )}
                </div>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* Reversal Confirmation Dialog */}
      <Dialog open={isReverseOpen} onOpenChange={setIsReverseOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Reverse Stock Adjustment</DialogTitle>
            <DialogDescription>
              Post an immutable reversing transaction against {selectedAdjustment?.adjustmentNo}.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1.5">
              <Label className="text-xs font-medium text-slate-700">Reversal Reason *</Label>
              <Input
                placeholder="Reason for reversing this transaction..."
                value={reversalReason}
                onChange={(e) => setReversalReason(e.target.value)}
                className="text-sm"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsReverseOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                if (!selectedAdjustment || !reversalReason.trim()) {
                  toast.error('Please enter a reversal reason');
                  return;
                }
                reverseMutation.mutate({ id: selectedAdjustment.id, reason: reversalReason.trim() });
              }}
              disabled={reverseMutation.isPending || !reversalReason.trim()}
            >
              Confirm Reversal
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
