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
import { Checkbox } from '@/components/ui/checkbox';
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
  Wrench,
  Plus,
  Eye,
  Edit3,
  Calendar,
  DollarSign,
  Truck,
  CheckCircle2,
  AlertTriangle,
  FileText,
  Clock,
} from 'lucide-react';
import { toast } from 'sonner';

interface RepairSummary {
  id: string;
  repairNo: string;
  assetId: string;
  assetCode: string;
  itemName: string;
  complaintDate: string;
  status: string;
  warrantyClaim: boolean;
  repairCost?: number;
  createdAt: string;
}

interface RepairDetail extends RepairSummary {
  complaintDetail: string;
  vendorNameSnapshot?: string;
  dispatchChallanNo?: string;
  sentDate?: string;
  expectedReturnDate?: string;
  receivedDate?: string;
  diagnosis?: string;
  repairAction?: string;
  partsReplaced?: string;
  finalCondition?: string;
}

export default function RepairPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(0);

  // Modals
  const [createOpen, setCreateOpen] = useState(false);
  const [viewId, setViewId] = useState<string | null>(null);
  const [updateId, setUpdateId] = useState<string | null>(null);

  // Create Form State
  const [assetId, setAssetId] = useState('');
  const [complaintDetail, setComplaintDetail] = useState('');
  const [warrantyClaim, setWarrantyClaim] = useState(false);
  const [vendorName, setVendorName] = useState('');
  const [expectedReturnDate, setExpectedReturnDate] = useState('');

  // Update Form State
  const [editStatus, setEditStatus] = useState('IN_REPAIR');
  const [diagnosis, setDiagnosis] = useState('');
  const [repairAction, setRepairAction] = useState('');
  const [partsReplaced, setPartsReplaced] = useState('');
  const [repairCost, setRepairCost] = useState<number | ''>('');
  const [receivedDate, setReceivedDate] = useState('');
  const [finalCondition, setFinalCondition] = useState('GOOD');

  // Query assets for dropdown
  const { data: assets } = useQuery<PageResponse<AssetSummaryResponse>>({
    queryKey: ['assets-for-repair'],
    queryFn: () => api.get<PageResponse<AssetSummaryResponse>>('/api/assets?size=100'),
  });

  // Query repair tickets
  const { data: repairsPage, isLoading } = useQuery<PageResponse<RepairSummary>>({
    queryKey: ['repair-tickets', statusFilter, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '15' });
      if (statusFilter !== 'ALL') params.append('status', statusFilter);
      return api.get<PageResponse<RepairSummary>>(`/api/repairs?${params.toString()}`);
    },
  });

  // Query single ticket for view/update
  const { data: activeTicket } = useQuery<RepairDetail>({
    queryKey: ['repair-ticket-detail', viewId || updateId],
    queryFn: () => api.get<RepairDetail>(`/api/repairs/${viewId || updateId}`),
    enabled: !!(viewId || updateId),
  });

  // Create Mutation
  const createMutation = useMutation({
    mutationFn: (body: unknown) => api.post('/api/repairs', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repair-tickets'] });
      toast.success('Repair ticket created successfully');
      setCreateOpen(false);
      resetCreateForm();
    },
    onError: (err: ApiError) => toast.error(err.message || 'Failed to create ticket'),
  });

  // Update Mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: unknown }) => api.put(`/api/repairs/${id}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repair-tickets'] });
      queryClient.invalidateQueries({ queryKey: ['repair-ticket-detail'] });
      toast.success('Repair ticket updated');
      setUpdateId(null);
    },
    onError: (err: ApiError) => toast.error(err.message || 'Failed to update ticket'),
  });

  const resetCreateForm = () => {
    setAssetId('');
    setComplaintDetail('');
    setWarrantyClaim(false);
    setVendorName('');
    setExpectedReturnDate('');
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!assetId || !complaintDetail) {
      toast.error('Asset and fault details are required');
      return;
    }
    createMutation.mutate({
      assetId,
      complaintDetail,
      warrantyClaim,
      vendorNameSnapshot: vendorName || undefined,
      expectedReturnDate: expectedReturnDate || undefined,
    });
  };

  const handleUpdateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!updateId) return;

    updateMutation.mutate({
      id: updateId,
      body: {
        status: editStatus,
        diagnosis: diagnosis || undefined,
        repairAction: repairAction || undefined,
        partsReplaced: partsReplaced || undefined,
        repairCost: repairCost !== '' ? Number(repairCost) : undefined,
        receivedDate: receivedDate || undefined,
        finalCondition: editStatus === 'COMPLETED' ? finalCondition : undefined,
      },
    });
  };

  const openUpdateModal = (ticket: RepairSummary) => {
    setUpdateId(ticket.id);
    setEditStatus(ticket.status);
    setDiagnosis('');
    setRepairAction('');
    setPartsReplaced('');
    setRepairCost(ticket.repairCost || '');
    setReceivedDate(new Date().toISOString().split('T')[0]);
    setFinalCondition('GOOD');
  };

  const columns: ColumnDef<RepairSummary>[] = [
    {
      accessorKey: 'repairNo',
      header: 'Ticket No',
      cell: (row) => (
        <span className="font-semibold text-blue-600 dark:text-blue-400 font-mono text-xs">
          {row.repairNo}
        </span>
      ),
    },
    {
      accessorKey: 'assetCode',
      header: 'Asset',
      cell: (row) => (
        <div>
          <p className="font-semibold text-slate-900 dark:text-slate-100 font-mono text-xs">
            {row.assetCode}
          </p>
          <p className="text-[11px] text-slate-500 truncate max-w-[200px]">
            {row.itemName}
          </p>
        </div>
      ),
    },
    {
      accessorKey: 'complaintDate',
      header: 'Complaint Date',
      cell: (row) => (
        <span className="text-xs text-slate-500">{row.complaintDate}</span>
      ),
    },
    {
      accessorKey: 'warrantyClaim',
      header: 'Warranty Claim',
      cell: (row) =>
        row.warrantyClaim ? (
          <Badge variant="outline" className="bg-emerald-50 text-emerald-700 border-emerald-200 text-[10px]">
            Warranty
          </Badge>
        ) : (
          <Badge variant="secondary" className="text-[10px]">
            Out-of-Warranty
          </Badge>
        ),
    },
    {
      accessorKey: 'repairCost',
      header: 'Cost',
      cell: (row) => (
        <span className="text-xs font-mono font-medium">
          {row.repairCost ? `₹${row.repairCost.toLocaleString()}` : '—'}
        </span>
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
      cell: (row) => (
        <div className="flex items-center gap-1.5">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setViewId(row.id)}
            className="h-8 px-2"
          >
            <Eye className="size-3.5 mr-1" /> View
          </Button>
          {row.status !== 'COMPLETED' && row.status !== 'UNREPAIRABLE' && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => openUpdateModal(row)}
              className="h-8 px-2 text-blue-600"
            >
              <Edit3 className="size-3.5 mr-1" /> Update
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400">
            <Wrench className="size-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Repair & Maintenance
            </h1>
            <p className="text-sm text-slate-500">
              Phase 7 · Hardware fault ticketing, OEM dispatch, warranty claims & return-to-service
            </p>
          </div>
        </div>
        <Button
          onClick={() => {
            resetCreateForm();
            setCreateOpen(true);
          }}
          className="gap-2 bg-blue-600 hover:bg-blue-700 text-white shadow-sm"
        >
          <Plus className="size-4" /> Log Repair Ticket
        </Button>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-slate-500">
              Total Tickets
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{repairsPage?.totalElements || 0}</div>
            <p className="text-xs text-slate-400 mt-1">Logged maintenance cases</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-amber-500">
              Active In-Repair
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-amber-600">
              {repairsPage?.content?.filter((t) => ['DRAFT', 'SENT_TO_VENDOR', 'IN_REPAIR'].includes(t.status)).length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Equipment under service</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-emerald-500">
              Resolved & Restored
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-emerald-600">
              {repairsPage?.content?.filter((t) => t.status === 'COMPLETED').length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Returned to available stock</p>
          </CardContent>
        </Card>
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium uppercase tracking-wider text-red-500">
              Unrepairable / Condemn
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-red-600">
              {repairsPage?.content?.filter((t) => t.status === 'UNREPAIRABLE').length || 0}
            </div>
            <p className="text-xs text-slate-400 mt-1">Candidate for condemnation</p>
          </CardContent>
        </Card>
      </div>

      {/* Filters & Data Table */}
      <Card className="border-slate-200 dark:border-slate-800">
        <CardContent className="pt-6 space-y-4">
          <div className="w-52">
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="h-9">
                <SelectValue placeholder="All Ticket Statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Statuses</SelectItem>
                <SelectItem value="DRAFT">Draft</SelectItem>
                <SelectItem value="SENT_TO_VENDOR">Sent to Vendor</SelectItem>
                <SelectItem value="IN_REPAIR">In Repair</SelectItem>
                <SelectItem value="COMPLETED">Completed (Restored)</SelectItem>
                <SelectItem value="UNREPAIRABLE">Unrepairable</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <DataTable
            columns={columns}
            data={repairsPage?.content || []}
            totalPages={repairsPage?.totalPages || 1}
            page={page}
            pageSize={15}
            onPageChange={setPage}
            isLoading={isLoading}
          />
        </CardContent>
      </Card>

      {/* CREATE TICKET MODAL */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Wrench className="size-5 text-amber-600" />
              Log Repair Ticket
            </DialogTitle>
            <DialogDescription>
              Report a defective hardware asset and route it for servicing.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="space-y-4">
            <div>
              <Label className="text-xs font-semibold">Select Defective Asset *</Label>
              <Select value={assetId} onValueChange={setAssetId}>
                <SelectTrigger className="mt-1">
                  <SelectValue placeholder="Search by Asset Code or Name" />
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

            <div>
              <Label className="text-xs font-semibold">Fault / Complaint Description *</Label>
              <Textarea
                placeholder="Describe symptom (e.g. motherboard failure, screen flickering, power supply dead)..."
                value={complaintDetail}
                onChange={(e) => setComplaintDetail(e.target.value)}
                rows={3}
                className="mt-1"
                required
              />
            </div>

            <div className="flex items-center gap-2 pt-1">
              <Checkbox
                id="warrantyClaim"
                checked={warrantyClaim}
                onCheckedChange={(checked) => setWarrantyClaim(Boolean(checked))}
              />
              <Label htmlFor="warrantyClaim" className="text-xs font-medium cursor-pointer">
                Covered under active OEM Warranty or AMC
              </Label>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label className="text-xs font-semibold">Service Vendor / OEM</Label>
                <Input
                  placeholder="e.g. Dell Support, HP Service"
                  value={vendorName}
                  onChange={(e) => setVendorName(e.target.value)}
                  className="mt-1"
                />
              </div>

              <div>
                <Label className="text-xs font-semibold">Expected Return Date</Label>
                <Input
                  type="date"
                  value={expectedReturnDate}
                  onChange={(e) => setExpectedReturnDate(e.target.value)}
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
                className="bg-blue-600 hover:bg-blue-700 text-white"
              >
                {createMutation.isPending ? 'Logging Ticket...' : 'Create Ticket'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* UPDATE MODAL */}
      <Dialog open={!!updateId} onOpenChange={() => setUpdateId(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Edit3 className="size-5 text-blue-600" />
              Update Repair Ticket · {activeTicket?.repairNo}
            </DialogTitle>
            <DialogDescription>
              Record vendor diagnosis, action taken, and return inspection.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleUpdateSubmit} className="space-y-4">
            <div>
              <Label className="text-xs font-semibold">Ticket Status *</Label>
              <Select value={editStatus} onValueChange={setEditStatus}>
                <SelectTrigger className="mt-1">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="SENT_TO_VENDOR">Sent to Vendor</SelectItem>
                  <SelectItem value="IN_REPAIR">In Repair</SelectItem>
                  <SelectItem value="COMPLETED">Completed (Restored to Service)</SelectItem>
                  <SelectItem value="UNREPAIRABLE">Unrepairable (Recommend Condemnation)</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label className="text-xs font-semibold">Technician Diagnosis</Label>
              <Input
                placeholder="e.g. Corrupted RAM module replaced"
                value={diagnosis}
                onChange={(e) => setDiagnosis(e.target.value)}
                className="mt-1"
              />
            </div>

            <div>
              <Label className="text-xs font-semibold">Action Taken & Parts Replaced</Label>
              <Textarea
                placeholder="Details of repair action, replacement parts..."
                value={repairAction}
                onChange={(e) => setRepairAction(e.target.value)}
                rows={2}
                className="mt-1"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label className="text-xs font-semibold">Repair Cost (₹)</Label>
                <Input
                  type="number"
                  min="0"
                  placeholder="0.00"
                  value={repairCost}
                  onChange={(e) => setRepairCost(e.target.value === '' ? '' : Number(e.target.value))}
                  className="mt-1 font-mono"
                />
              </div>

              {editStatus === 'COMPLETED' && (
                <div>
                  <Label className="text-xs font-semibold">Post-Repair Condition</Label>
                  <Select value={finalCondition} onValueChange={setFinalCondition}>
                    <SelectTrigger className="mt-1">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="GOOD">Good / Like New</SelectItem>
                      <SelectItem value="FAIR">Fair (Operational)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              )}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setUpdateId(null)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={updateMutation.isPending}
                className="bg-blue-600 hover:bg-blue-700 text-white"
              >
                {updateMutation.isPending ? 'Updating...' : 'Save Updates'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* VIEW MODAL */}
      <Dialog open={!!viewId} onOpenChange={() => setViewId(null)}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle className="flex items-center justify-between">
              <span>Repair Ticket {activeTicket?.repairNo}</span>
              {activeTicket && <StatusBadge status={activeTicket.status} />}
            </DialogTitle>
            <DialogDescription>Full technical service history</DialogDescription>
          </DialogHeader>

          {activeTicket && (
            <div className="space-y-4 text-xs">
              <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900 border space-y-2">
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <span className="text-slate-500">Asset:</span>
                    <p className="font-semibold text-slate-800 dark:text-slate-200">
                      {activeTicket.assetCode} - {activeTicket.itemName}
                    </p>
                  </div>
                  <div>
                    <span className="text-slate-500">Vendor:</span>
                    <p className="font-medium">{activeTicket.vendorNameSnapshot || 'Internal / OEM'}</p>
                  </div>
                  <div>
                    <span className="text-slate-500">Complaint Date:</span>
                    <p className="font-medium">{activeTicket.complaintDate}</p>
                  </div>
                  <div>
                    <span className="text-slate-500">Repair Cost:</span>
                    <p className="font-mono font-bold text-slate-800 dark:text-slate-200">
                      ₹{activeTicket.repairCost?.toLocaleString() || '0.00'}
                    </p>
                  </div>
                </div>
              </div>

              <div>
                <span className="font-semibold text-slate-700 dark:text-slate-300">Complaint Detail:</span>
                <p className="p-2 rounded bg-slate-100 dark:bg-slate-800 mt-1">
                  {activeTicket.complaintDetail}
                </p>
              </div>

              {activeTicket.diagnosis && (
                <div>
                  <span className="font-semibold text-slate-700 dark:text-slate-300">Diagnosis:</span>
                  <p className="p-2 rounded bg-slate-100 dark:bg-slate-800 mt-1">
                    {activeTicket.diagnosis}
                  </p>
                </div>
              )}

              {activeTicket.repairAction && (
                <div>
                  <span className="font-semibold text-slate-700 dark:text-slate-300">Action Taken:</span>
                  <p className="p-2 rounded bg-slate-100 dark:bg-slate-800 mt-1">
                    {activeTicket.repairAction}
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
