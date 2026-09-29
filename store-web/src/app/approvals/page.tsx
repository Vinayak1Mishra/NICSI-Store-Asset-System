'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import {
  RequisitionResponse,
  RequisitionDecisionRequest,
  RequisitionLineDecision,
} from '@/types/requisition';
import { PageResponse } from '@/types/master';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { StatusBadge } from '@/components/common/status-badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
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
  CheckCircle,
  XCircle,
  RotateCcw,
  CheckCircle2,
  Clock,
  AlertCircle,
  FileCheck,
} from 'lucide-react';
import { toast } from 'sonner';

export default function ApprovalsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  // Review Modal State
  const [isDecisionOpen, setIsDecisionOpen] = useState(false);
  const [selectedReq, setSelectedReq] = useState<RequisitionResponse | null>(null);
  const [comments, setComments] = useState('');
  const [lineQuantities, setLineQuantities] = useState<Record<number, number>>({});

  // Fetch Pending Approvals Worklist
  const { data: approvalsData, isLoading } = useQuery({
    queryKey: ['pending-approvals', page],
    queryFn: () => {
      return api.get<PageResponse<RequisitionResponse>>(`/api/store/requisitions/approvals/pending?page=${page}&size=15`);
    },
  });

  // Decision Mutation
  const decisionMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: RequisitionDecisionRequest }) =>
      api.post<RequisitionResponse>(`/api/store/requisitions/${id}/approvals/decision`, body),
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['pending-approvals'] });
      queryClient.invalidateQueries({ queryKey: ['requisitions'] });
      toast.success(`Requisition ${data.requisitionNo} action '${variables.body.action}' processed successfully`);
      setIsDecisionOpen(false);
      setSelectedReq(null);
      setComments('');
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to process approval decision');
    },
  });

  const handleOpenDecision = (req: RequisitionResponse) => {
    setSelectedReq(req);
    // Initialize approved quantities with requested quantities
    const initialQtys: Record<number, number> = {};
    req.items?.forEach((item) => {
      initialQtys[item.lineNo] = item.approvedQty ?? item.requestedQty;
    });
    setLineQuantities(initialQtys);
    setComments('');
    setIsDecisionOpen(true);
  };

  const handleActionSubmit = (action: 'APPROVE' | 'REJECT' | 'RETURN') => {
    if (!selectedReq) return;

    if ((action === 'REJECT' || action === 'RETURN') && !comments.trim()) {
      toast.error(`Please provide review comments when ${action.toLowerCase()}ing a requisition`);
      return;
    }

    const lineDecisions: RequisitionLineDecision[] = selectedReq.items?.map((item) => ({
      lineNo: item.lineNo,
      approvedQty: lineQuantities[item.lineNo] ?? item.requestedQty,
    })) || [];

    decisionMutation.mutate({
      id: selectedReq.id,
      body: {
        action,
        comments: comments.trim() || undefined,
        lineDecisions: action === 'APPROVE' ? lineDecisions : undefined,
      },
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
      header: 'Indent Date',
      accessorKey: 'requisitionDate',
    },
    {
      header: 'Requester',
      accessorKey: 'requesterNameSnapshot',
      cell: (row) => row.requesterNameSnapshot || '—',
    },
    {
      header: 'Department',
      accessorKey: 'departmentNameSnapshot',
      cell: (row) => row.departmentNameSnapshot || '—',
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
      header: 'Estimated Value',
      accessorKey: 'totalEstimatedAmount',
      cell: (row) => `₹${row.totalEstimatedAmount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`,
    },
    {
      header: 'Current Step',
      cell: (row) => {
        const step = row.workflow?.steps?.find((s) => s.stepNo === row.workflow?.currentStepNo);
        return (
          <div className="flex items-center gap-1.5">
            <Badge variant="secondary" className="font-normal text-xs">
              Step {row.workflow?.currentStepNo}: {step?.stepName || 'Approval'}
            </Badge>
          </div>
        );
      },
    },
    {
      header: 'Action',
      cell: (row) => (
        <Button
          size="sm"
          className="bg-blue-600 hover:bg-blue-700 text-xs"
          onClick={() => handleOpenDecision(row)}
        >
          <FileCheck className="h-3.5 w-3.5 mr-1" /> Review & Decide
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
          Pending Approvals Worklist
        </h1>
        <p className="text-sm text-slate-500">
          Review, adjust quantities, and approve material indents pending your departmental or store authorization.
        </p>
      </div>

      {/* Summary KPI */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="border-slate-200">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-semibold uppercase text-slate-500">Pending Authorization</CardTitle>
            <Clock className="h-4 w-4 text-amber-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{approvalsData?.totalElements || 0}</div>
            <p className="text-xs text-slate-500 mt-1">Indents requiring your action</p>
          </CardContent>
        </Card>

        <Card className="border-slate-200">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-semibold uppercase text-slate-500">Governance Policy</CardTitle>
            <AlertCircle className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-sm font-semibold text-slate-700 dark:text-slate-300">Maker-Checker Guard Active</div>
            <p className="text-xs text-slate-500 mt-1">Requesters strictly prohibited from approving own indents</p>
          </CardContent>
        </Card>

        <Card className="border-slate-200">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-semibold uppercase text-slate-500">Audit Trail</CardTitle>
            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-sm font-semibold text-slate-700 dark:text-slate-300">Immutable Event Logging</div>
            <p className="text-xs text-slate-500 mt-1">Every decision logged with role snapshot & correlation ID</p>
          </CardContent>
        </Card>
      </div>

      {/* Approvals Table */}
      <DataTable
        columns={columns}
        data={approvalsData?.content || []}
        isLoading={isLoading}
        page={page}
        totalPages={approvalsData?.totalPages || 1}
        totalElements={approvalsData?.totalElements || 0}
        onPageChange={setPage}
      />

      {/* REVIEW & DECISION MODAL */}
      <Dialog open={isDecisionOpen} onOpenChange={setIsDecisionOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          {selectedReq && (
            <div className="space-y-5">
              <DialogHeader>
                <div className="flex items-center justify-between">
                  <DialogTitle className="font-mono text-xl text-blue-600">
                    Review Requisition: {selectedReq.requisitionNo}
                  </DialogTitle>
                  <StatusBadge status={selectedReq.status} />
                </div>
                <DialogDescription>
                  Inspect line items, verify warehouse stock feasibility, adjust approved quantities, and submit your decision.
                </DialogDescription>
              </DialogHeader>

              {/* Indent Meta */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 bg-slate-50 dark:bg-slate-900 p-3.5 rounded-lg border text-xs">
                <div>
                  <span className="text-slate-500 block">Date</span>
                  <span className="font-medium">{selectedReq.requisitionDate}</span>
                </div>
                <div>
                  <span className="text-slate-500 block">Requester</span>
                  <span className="font-medium">{selectedReq.requesterNameSnapshot || '—'}</span>
                </div>
                <div>
                  <span className="text-slate-500 block">Department</span>
                  <span className="font-medium">{selectedReq.departmentNameSnapshot || '—'}</span>
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

              {/* Editable Line Item Quantities */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <h4 className="text-sm font-semibold">Items Authorization</h4>
                  <span className="text-xs text-slate-500">You may modify approved quantities before authorizing</span>
                </div>

                <div className="border rounded-md overflow-hidden">
                  <table className="w-full text-xs">
                    <thead className="bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300">
                      <tr>
                        <th className="py-2 px-3 text-left">#</th>
                        <th className="py-2 px-3 text-left">Item Code & Name</th>
                        <th className="py-2 px-3 text-center">UOM</th>
                        <th className="py-2 px-3 text-right">Requested Qty</th>
                        <th className="py-2 px-3 text-right w-36">Approved Qty</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                      {selectedReq.items?.map((item) => (
                        <tr key={item.id}>
                          <td className="py-2 px-3">{item.lineNo}</td>
                          <td className="py-2 px-3 font-medium">
                            {item.itemName} <span className="text-slate-400 font-normal">({item.itemCode})</span>
                            {item.specification && (
                              <span className="block text-[11px] text-slate-500 italic mt-0.5">
                                Spec: {item.specification}
                              </span>
                            )}
                          </td>
                          <td className="py-2 px-3 text-center">{item.uomCode}</td>
                          <td className="py-2 px-3 text-right font-medium">{item.requestedQty}</td>
                          <td className="py-2 px-3 text-right">
                            <Input
                              type="number"
                              step="any"
                              min="0"
                              max={item.requestedQty}
                              className="text-xs h-7 text-right font-semibold"
                              value={lineQuantities[item.lineNo] ?? item.requestedQty}
                              onChange={(e) => {
                                const val = Number(e.target.value);
                                setLineQuantities({
                                  ...lineQuantities,
                                  [item.lineNo]: val,
                                });
                              }}
                            />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Review Comments */}
              <div className="space-y-1.5">
                <Label htmlFor="comments" className="text-xs">
                  Review Remarks / Justification <span className="text-slate-400 font-normal">(mandatory if returning or rejecting)</span>
                </Label>
                <Textarea
                  id="comments"
                  rows={3}
                  placeholder="Provide approval justification, reason for modification, or return instructions..."
                  value={comments}
                  onChange={(e) => setComments(e.target.value)}
                  className="text-xs"
                />
              </div>

              {/* Action Buttons */}
              <DialogFooter className="flex flex-col sm:flex-row gap-2 pt-2 border-t">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="text-rose-600 hover:text-rose-700 hover:bg-rose-50 border-rose-200"
                  onClick={() => handleActionSubmit('REJECT')}
                  disabled={decisionMutation.isPending}
                >
                  <XCircle className="h-4 w-4 mr-1.5" /> Reject Indent
                </Button>

                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="text-amber-600 hover:text-amber-700 hover:bg-amber-50 border-amber-200"
                  onClick={() => handleActionSubmit('RETURN')}
                  disabled={decisionMutation.isPending}
                >
                  <RotateCcw className="h-4 w-4 mr-1.5" /> Return for Revision
                </Button>

                <Button
                  type="button"
                  size="sm"
                  className="bg-emerald-600 hover:bg-emerald-700 text-white ml-auto"
                  onClick={() => handleActionSubmit('APPROVE')}
                  disabled={decisionMutation.isPending}
                >
                  <CheckCircle className="h-4 w-4 mr-1.5" /> Authorize & Approve
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
