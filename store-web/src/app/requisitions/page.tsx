'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import {
  RequisitionResponse,
  RequisitionCreateRequest,
  RequisitionLineRequest,
} from '@/types/requisition';
import { ItemResponse, PageResponse } from '@/types/master';
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
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  FileText,
  Plus,
  Send,
  XCircle,
  Eye,
  Trash2,
  Clock,
  CheckCircle2,
  AlertTriangle,
  RotateCcw,
} from 'lucide-react';
import { toast } from 'sonner';

interface FormLine {
  itemId: string;
  itemName: string;
  itemCode: string;
  uomCode: string;
  requestedQty: number;
  estimatedUnitRate: number;
  specification: string;
  justification: string;
}

export default function RequisitionsPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('ALL');
  const [filterPriority, setFilterPriority] = useState<string>('ALL');
  const [page, setPage] = useState(0);

  // Create Requisition Modal State
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [purpose, setPurpose] = useState('');
  const [priority, setPriority] = useState<'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'>('NORMAL');
  const [requiredByDate, setRequiredByDate] = useState('');
  const [lines, setLines] = useState<FormLine[]>([]);

  // Selected Line item selector state
  const [selectedItemId, setSelectedItemId] = useState('');
  const [lineQty, setLineQty] = useState<number | ''>('');
  const [lineSpec, setLineSpec] = useState('');
  const [lineJust, setLineJust] = useState('');

  // View / Detail Modal State
  const [selectedReq, setSelectedReq] = useState<RequisitionResponse | null>(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);

  // Fetch Requisitions list
  const { data: requisitionsData, isLoading } = useQuery({
    queryKey: ['requisitions', page, search, filterStatus, filterPriority],
    queryFn: () => {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', '15');
      if (search) params.append('search', search);
      if (filterStatus !== 'ALL') params.append('status', filterStatus);
      if (filterPriority !== 'ALL') params.append('priority', filterPriority);
      return api.get<PageResponse<RequisitionResponse>>(`/api/store/requisitions?${params.toString()}`);
    },
  });

  // Fetch Items catalog for line item selector
  const { data: itemsData } = useQuery({
    queryKey: ['items-lookup'],
    queryFn: () => api.get<PageResponse<ItemResponse>>('/api/store/items?size=100&active=true'),
  });

  // Create Mutation
  const createMutation = useMutation({
    mutationFn: (req: RequisitionCreateRequest) => api.post<RequisitionResponse>('/api/store/requisitions', req),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['requisitions'] });
      toast.success(`Requisition ${data.requisitionNo} created in DRAFT status`);
      resetCreateForm();
      setIsCreateOpen(false);
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create requisition');
    },
  });

  // Submit Mutation
  const submitMutation = useMutation({
    mutationFn: (id: string) => api.post<RequisitionResponse>(`/api/store/requisitions/${id}/submit`, {}),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['requisitions'] });
      toast.success(`Requisition ${data.requisitionNo} submitted for approval`);
      if (selectedReq?.id === data.id) {
        setSelectedReq(data);
      }
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to submit requisition');
    },
  });

  // Cancel Mutation
  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      api.post<RequisitionResponse>(`/api/store/requisitions/${id}/cancel`, { reason }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['requisitions'] });
      toast.success(`Requisition ${data.requisitionNo} cancelled`);
      if (selectedReq?.id === data.id) {
        setSelectedReq(data);
      }
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to cancel requisition');
    },
  });

  const resetCreateForm = () => {
    setPurpose('');
    setPriority('NORMAL');
    setRequiredByDate('');
    setLines([]);
    setSelectedItemId('');
    setLineQty('');
    setLineSpec('');
    setLineJust('');
  };

  const handleAddLine = () => {
    if (!selectedItemId) {
      toast.error('Please select an item');
      return;
    }
    if (!lineQty || lineQty <= 0) {
      toast.error('Requested quantity must be greater than 0');
      return;
    }
    const item = itemsData?.content.find((i) => i.id === selectedItemId);
    if (!item) return;

    if (lines.some((l) => l.itemId === selectedItemId)) {
      toast.error('Item already added to requisition. Please adjust existing quantity.');
      return;
    }

    setLines([
      ...lines,
      {
        itemId: item.id,
        itemName: item.itemName,
        itemCode: item.itemCode,
        uomCode: item.uomCode,
        requestedQty: Number(lineQty),
        estimatedUnitRate: item.standardRate || 0,
        specification: lineSpec,
        justification: lineJust,
      },
    ]);

    setSelectedItemId('');
    setLineQty('');
    setLineSpec('');
    setLineJust('');
  };

  const handleRemoveLine = (index: number) => {
    setLines(lines.filter((_, i) => i !== index));
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (lines.length === 0) {
      toast.error('Please add at least one item line');
      return;
    }

    const payloadLines: RequisitionLineRequest[] = lines.map((l) => ({
      itemId: l.itemId,
      requestedQty: l.requestedQty,
      estimatedUnitRate: l.estimatedUnitRate,
      specification: l.specification || undefined,
      justification: l.justification || undefined,
    }));

    createMutation.mutate({
      purpose,
      priority,
      requiredByDate: requiredByDate || undefined,
      items: payloadLines,
    });
  };

  const columns: ColumnDef<RequisitionResponse>[] = [
    {
      header: 'Requisition No',
      accessorKey: 'requisitionNo',
      cell: (row) => (
        <span className="font-mono font-medium text-blue-600 dark:text-blue-400">
          {row.requisitionNo}
        </span>
      ),
    },
    {
      header: 'Date',
      accessorKey: 'requisitionDate',
    },
    {
      header: 'Requester',
      accessorKey: 'requesterNameSnapshot',
      cell: (row) => row.requesterNameSnapshot || 'Self',
    },
    {
      header: 'Priority',
      accessorKey: 'priority',
      cell: (row) => {
        let badgeColor = 'bg-slate-100 text-slate-800';
        if (row.priority === 'URGENT') badgeColor = 'bg-rose-100 text-rose-800 border-rose-300';
        if (row.priority === 'HIGH') badgeColor = 'bg-amber-100 text-amber-800 border-amber-300';
        return <Badge variant="outline" className={badgeColor}>{row.priority}</Badge>;
      },
    },
    {
      header: 'Status',
      accessorKey: 'status',
      cell: (row) => <StatusBadge status={row.status} />,
    },
    {
      header: 'Est. Amount',
      accessorKey: 'totalEstimatedAmount',
      cell: (row) => `₹${row.totalEstimatedAmount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`,
    },
    {
      header: 'Items',
      cell: (row) => `${row.items?.length || 0} line(s)`,
    },
    {
      header: 'Actions',
      cell: (row) => (
        <div className="flex items-center gap-1.5">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setSelectedReq(row);
              setIsDetailOpen(true);
            }}
          >
            <Eye className="h-4 w-4 mr-1" /> View
          </Button>
          {row.status === 'DRAFT' && (
            <Button
              variant="outline"
              size="sm"
              className="text-blue-600 hover:text-blue-700"
              onClick={() => submitMutation.mutate(row.id)}
              disabled={submitMutation.isPending}
            >
              <Send className="h-3.5 w-3.5 mr-1" /> Submit
            </Button>
          )}
        </div>
      ),
    },
  ];

  // Calculate high-level stats
  const totalCount = requisitionsData?.totalElements || 0;
  const draftCount = requisitionsData?.content.filter((r) => r.status === 'DRAFT').length || 0;
  const pendingCount = requisitionsData?.content.filter((r) => r.status === 'UNDER_APPROVAL' || r.status === 'SUBMITTED').length || 0;
  const approvedCount = requisitionsData?.content.filter((r) => r.status === 'APPROVED').length || 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
            Material Requisitions
          </h1>
          <p className="text-sm text-slate-500">
            Create, track, and manage internal store indents with automated approval workflows.
          </p>
        </div>
        <Button onClick={() => setIsCreateOpen(true)} className="bg-blue-600 hover:bg-blue-700">
          <Plus className="h-4 w-4 mr-1.5" /> New Requisition
        </Button>
      </div>

      {/* KPI Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="border-slate-200">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-semibold uppercase text-slate-500">Total Indents</CardTitle>
            <FileText className="h-4 w-4 text-blue-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{totalCount}</div>
            <p className="text-xs text-slate-500 mt-1">Across all lifecycle statuses</p>
          </CardContent>
        </Card>

        <Card className="border-slate-200">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-semibold uppercase text-slate-500">Drafts</CardTitle>
            <Clock className="h-4 w-4 text-amber-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{draftCount}</div>
            <p className="text-xs text-slate-500 mt-1">Awaiting submission</p>
          </CardContent>
        </Card>

        <Card className="border-slate-200">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-semibold uppercase text-slate-500">Under Approval</CardTitle>
            <AlertTriangle className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{pendingCount}</div>
            <p className="text-xs text-slate-500 mt-1">With HOD / Store Officer</p>
          </CardContent>
        </Card>

        <Card className="border-slate-200">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-semibold uppercase text-slate-500">Approved</CardTitle>
            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{approvedCount}</div>
            <p className="text-xs text-slate-500 mt-1">Ready for fulfillment / issue</p>
          </CardContent>
        </Card>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row gap-3">
        <Input
          placeholder="Search requisition no, purpose, or requester..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="max-w-sm"
        />
        <Select value={filterStatus} onValueChange={setFilterStatus}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="All Statuses" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Statuses</SelectItem>
            <SelectItem value="DRAFT">Draft</SelectItem>
            <SelectItem value="UNDER_APPROVAL">Under Approval</SelectItem>
            <SelectItem value="APPROVED">Approved</SelectItem>
            <SelectItem value="REJECTED">Rejected</SelectItem>
            <SelectItem value="CANCELLED">Cancelled</SelectItem>
          </SelectContent>
        </Select>

        <Select value={filterPriority} onValueChange={setFilterPriority}>
          <SelectTrigger className="w-[150px]">
            <SelectValue placeholder="All Priorities" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Priorities</SelectItem>
            <SelectItem value="LOW">Low</SelectItem>
            <SelectItem value="NORMAL">Normal</SelectItem>
            <SelectItem value="HIGH">High</SelectItem>
            <SelectItem value="URGENT">Urgent</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Table */}
      <DataTable
        columns={columns}
        data={requisitionsData?.content || []}
        isLoading={isLoading}
        page={page}
        totalPages={requisitionsData?.totalPages || 1}
        totalElements={requisitionsData?.totalElements || 0}
        onPageChange={setPage}
      />

      {/* CREATE REQUISITION DIALOG */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>New Material Requisition</DialogTitle>
            <DialogDescription>
              Create a requisition for material requirement. Draft indents can be reviewed before submission.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="space-y-4 pt-2">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="space-y-1.5 md:col-span-2">
                <Label htmlFor="purpose">Purpose / Justification *</Label>
                <Input
                  id="purpose"
                  placeholder="e.g. Requirement for Project Cloud Migration Team"
                  value={purpose}
                  onChange={(e) => setPurpose(e.target.value)}
                  required
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="priority">Priority</Label>
                <Select value={priority} onValueChange={(val: any) => setPriority(val)}>
                  <SelectTrigger id="priority">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="LOW">Low</SelectItem>
                    <SelectItem value="NORMAL">Normal</SelectItem>
                    <SelectItem value="HIGH">High</SelectItem>
                    <SelectItem value="URGENT">Urgent</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="requiredByDate">Required By Date</Label>
                <Input
                  id="requiredByDate"
                  type="date"
                  value={requiredByDate}
                  onChange={(e) => setRequiredByDate(e.target.value)}
                />
              </div>
            </div>

            {/* Line Items Sub-form */}
            <div className="border border-slate-200 dark:border-slate-800 rounded-lg p-4 space-y-3 bg-slate-50/50 dark:bg-slate-900/50">
              <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">Add Line Item</h3>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                <div className="space-y-1 md:col-span-2">
                  <Label className="text-xs">Select Catalog Item *</Label>
                  <Select value={selectedItemId} onValueChange={setSelectedItemId}>
                    <SelectTrigger className="text-xs">
                      <SelectValue placeholder="Choose an item..." />
                    </SelectTrigger>
                    <SelectContent>
                      {itemsData?.content.map((item) => (
                        <SelectItem key={item.id} value={item.id}>
                          {item.itemCode} — {item.itemName} ({item.uomCode})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1">
                  <Label className="text-xs">Requested Qty *</Label>
                  <Input
                    type="number"
                    step="any"
                    min="0.001"
                    placeholder="e.g. 5"
                    className="text-xs"
                    value={lineQty}
                    onChange={(e) => setLineQty(e.target.value === '' ? '' : Number(e.target.value))}
                  />
                </div>

                <div className="flex items-end">
                  <Button type="button" variant="outline" onClick={handleAddLine} className="w-full text-xs">
                    <Plus className="h-3.5 w-3.5 mr-1" /> Add Item
                  </Button>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <Input
                  placeholder="Specification note (optional)"
                  className="text-xs"
                  value={lineSpec}
                  onChange={(e) => setLineSpec(e.target.value)}
                />
                <Input
                  placeholder="Line justification (optional)"
                  className="text-xs"
                  value={lineJust}
                  onChange={(e) => setLineJust(e.target.value)}
                />
              </div>
            </div>

            {/* Added Lines Table */}
            <div className="border rounded-md overflow-hidden">
              <table className="w-full text-xs">
                <thead className="bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300">
                  <tr>
                    <th className="py-2 px-3 text-left">#</th>
                    <th className="py-2 px-3 text-left">Item Code & Name</th>
                    <th className="py-2 px-3 text-center">UOM</th>
                    <th className="py-2 px-3 text-right">Requested Qty</th>
                    <th className="py-2 px-3 text-right">Rate</th>
                    <th className="py-2 px-3 text-right">Estimated Total</th>
                    <th className="py-2 px-3 text-center">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                  {lines.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-6 text-center text-slate-500">
                        No items added yet. Please add at least one line item above.
                      </td>
                    </tr>
                  ) : (
                    lines.map((l, idx) => (
                      <tr key={l.itemId}>
                        <td className="py-2 px-3">{idx + 1}</td>
                        <td className="py-2 px-3 font-medium">
                          {l.itemName} <span className="text-slate-400 font-normal">({l.itemCode})</span>
                        </td>
                        <td className="py-2 px-3 text-center">{l.uomCode}</td>
                        <td className="py-2 px-3 text-right font-semibold">{l.requestedQty}</td>
                        <td className="py-2 px-3 text-right">₹{l.estimatedUnitRate?.toFixed(2)}</td>
                        <td className="py-2 px-3 text-right font-medium">
                          ₹{(l.requestedQty * (l.estimatedUnitRate || 0)).toFixed(2)}
                        </td>
                        <td className="py-2 px-3 text-center">
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="text-rose-600 h-6 w-6 p-0"
                            onClick={() => handleRemoveLine(idx)}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
                {lines.length > 0 && (
                  <tfoot className="bg-slate-50 dark:bg-slate-900 font-semibold border-t">
                    <tr>
                      <td colSpan={5} className="py-2 px-3 text-right">Total Estimated Value:</td>
                      <td className="py-2 px-3 text-right text-blue-600">
                        ₹{lines.reduce((acc, curr) => acc + curr.requestedQty * (curr.estimatedUnitRate || 0), 0).toFixed(2)}
                      </td>
                      <td />
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>

            <DialogFooter className="pt-2">
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending || lines.length === 0}>
                Create Draft Indent
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* DETAIL & WORKFLOW TIMELINE MODAL */}
      <Dialog open={isDetailOpen} onOpenChange={setIsDetailOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          {selectedReq && (
            <div className="space-y-6">
              <DialogHeader>
                <div className="flex items-center justify-between">
                  <DialogTitle className="font-mono text-xl text-blue-600">
                    {selectedReq.requisitionNo}
                  </DialogTitle>
                  <StatusBadge status={selectedReq.status} />
                </div>
                <DialogDescription>
                  Requisition details, line items, and multi-tier approval workflow timeline.
                </DialogDescription>
              </DialogHeader>

              {/* Summary details */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 bg-slate-50 dark:bg-slate-900 p-3.5 rounded-lg border text-xs">
                <div>
                  <span className="text-slate-500 block">Date</span>
                  <span className="font-medium">{selectedReq.requisitionDate}</span>
                </div>
                <div>
                  <span className="text-slate-500 block">Requester</span>
                  <span className="font-medium">{selectedReq.requesterNameSnapshot || 'Self'}</span>
                </div>
                <div>
                  <span className="text-slate-500 block">Priority</span>
                  <span className="font-medium">{selectedReq.priority}</span>
                </div>
                <div>
                  <span className="text-slate-500 block">Required By</span>
                  <span className="font-medium">{selectedReq.requiredByDate || 'Not specified'}</span>
                </div>
                <div className="col-span-2 md:col-span-4 pt-1">
                  <span className="text-slate-500 block">Purpose</span>
                  <span className="font-medium text-slate-800 dark:text-slate-200">{selectedReq.purpose || '—'}</span>
                </div>
              </div>

              {/* Items List */}
              <div className="space-y-2">
                <h4 className="text-sm font-semibold">Requisition Items</h4>
                <div className="border rounded-md overflow-hidden">
                  <table className="w-full text-xs">
                    <thead className="bg-slate-100 dark:bg-slate-800">
                      <tr>
                        <th className="py-2 px-3 text-left">#</th>
                        <th className="py-2 px-3 text-left">Item Description</th>
                        <th className="py-2 px-3 text-right">Requested Qty</th>
                        <th className="py-2 px-3 text-right">Approved Qty</th>
                        <th className="py-2 px-3 text-right">Unit Rate</th>
                        <th className="py-2 px-3 text-center">Line Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {selectedReq.items?.map((item) => (
                        <tr key={item.id}>
                          <td className="py-2 px-3">{item.lineNo}</td>
                          <td className="py-2 px-3 font-medium">
                            {item.itemName} <span className="text-slate-400 font-normal">({item.itemCode})</span>
                          </td>
                          <td className="py-2 px-3 text-right">{item.requestedQty} {item.uomCode}</td>
                          <td className="py-2 px-3 text-right font-semibold text-emerald-600">
                            {item.approvedQty !== null ? `${item.approvedQty} ${item.uomCode}` : '—'}
                          </td>
                          <td className="py-2 px-3 text-right">₹{item.estimatedUnitRate?.toFixed(2)}</td>
                          <td className="py-2 px-3 text-center">
                            <StatusBadge status={item.lineStatus} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* WORKFLOW TIMELINE */}
              <div className="space-y-3">
                <h4 className="text-sm font-semibold flex items-center gap-1.5">
                  <RotateCcw className="h-4 w-4 text-blue-600" /> Approval Workflow Timeline
                </h4>
                <div className="border rounded-lg p-4 bg-slate-50/50 dark:bg-slate-900/50 space-y-4">
                  {selectedReq.workflow?.steps?.map((step) => {
                    const isCompleted = selectedReq.workflow && selectedReq.workflow.status === 'APPROVED';
                    const isCurrent = selectedReq.workflow?.currentStepNo === step.stepNo && selectedReq.workflow?.status === 'RUNNING';
                    const action = selectedReq.workflow?.actions?.find((a) => a.stepNo === step.stepNo);

                    return (
                      <div key={step.id} className="flex items-start gap-3 relative pb-2">
                        <div
                          className={`h-6 w-6 rounded-full flex items-center justify-center text-xs font-bold shrink-0 mt-0.5 ${
                            isCompleted || (action && action.actionType === 'APPROVE')
                              ? 'bg-emerald-600 text-white'
                              : isCurrent
                              ? 'bg-blue-600 text-white animate-pulse'
                              : 'bg-slate-200 text-slate-600'
                          }`}
                        >
                          {step.stepNo}
                        </div>
                        <div className="flex-1 text-xs">
                          <div className="flex items-center justify-between">
                            <span className="font-semibold text-slate-800 dark:text-slate-200">
                              {step.stepName}
                            </span>
                            <Badge variant="outline" className="text-[10px]">
                              {step.approverRoleCode}
                            </Badge>
                          </div>
                          <p className="text-slate-500 mt-0.5">SLA: {step.slaHours} hours</p>
                          {action && (
                            <div className="mt-1.5 bg-white dark:bg-slate-800 p-2 rounded border border-slate-200 text-[11px]">
                              <div className="flex items-center justify-between font-medium">
                                <span className={action.actionType === 'APPROVE' ? 'text-emerald-600' : 'text-rose-600'}>
                                  Action: {action.actionType}
                                </span>
                                <span className="text-slate-400 font-normal">
                                  {new Date(action.actionTime).toLocaleString()}
                                </span>
                              </div>
                              {action.comments && (
                                <p className="text-slate-600 dark:text-slate-300 mt-1 italic">
                                  "{action.comments}"
                                </p>
                              )}
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                  {!selectedReq.workflow && (
                    <p className="text-xs text-slate-500">
                      Workflow has not started yet. Submit the requisition to start the multi-tier approval.
                    </p>
                  )}
                </div>
              </div>

              {/* Actions Footer */}
              <div className="flex justify-between items-center pt-2 border-t">
                {(selectedReq.status === 'DRAFT' || selectedReq.status === 'UNDER_APPROVAL') && (
                  <Button
                    variant="outline"
                    className="text-rose-600 hover:text-rose-700"
                    size="sm"
                    onClick={() => {
                      cancelMutation.mutate({ id: selectedReq.id, reason: 'Cancelled by user' });
                    }}
                    disabled={cancelMutation.isPending}
                  >
                    <XCircle className="h-4 w-4 mr-1.5" /> Cancel Requisition
                  </Button>
                )}
                <div className="flex gap-2 ml-auto">
                  {selectedReq.status === 'DRAFT' && (
                    <Button
                      size="sm"
                      className="bg-blue-600 hover:bg-blue-700"
                      onClick={() => submitMutation.mutate(selectedReq.id)}
                      disabled={submitMutation.isPending}
                    >
                      <Send className="h-4 w-4 mr-1.5" /> Submit for Approval
                    </Button>
                  )}
                  <Button variant="outline" size="sm" onClick={() => setIsDetailOpen(false)}>
                    Close
                  </Button>
                </div>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
