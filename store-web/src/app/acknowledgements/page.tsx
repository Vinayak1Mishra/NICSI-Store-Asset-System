'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import {
  IssueResponse,
  IssueSummaryResponse,
  AcknowledgeIssueRequest,
} from '@/types/issue';
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
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  CheckCheck,
  Eye,
  Building2,
  Calendar,
  Layers,
  Clock,
  ShieldCheck,
  FileCheck,
  UserCheck,
  AlertCircle,
  CheckCircle2,
} from 'lucide-react';
import { toast } from 'sonner';

export default function AcknowledgementsPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<string>('PENDING');
  const [searchTerm, setSearchTerm] = useState<string>('');

  const [ackIssueId, setAckIssueId] = useState<string | null>(null);
  const [ackStatus, setAckStatus] = useState<'ACCEPTED' | 'PARTIAL' | 'REJECTED'>('ACCEPTED');
  const [ackRemarks, setAckRemarks] = useState('');
  const [viewIssueId, setViewIssueId] = useState<string | null>(null);

  // 1. Fetch Issues
  const { data: issuesData, isLoading } = useQuery({
    queryKey: ['acknowledgements-issues', statusFilter, searchTerm],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (statusFilter === 'PENDING') {
        params.append('status', 'POSTED');
      } else if (statusFilter === 'ACKNOWLEDGED') {
        params.append('status', 'ACKNOWLEDGED');
      }
      if (searchTerm) params.append('search', searchTerm);
      params.append('size', '50');

      return api.get<PageResponse<IssueSummaryResponse>>(`/api/store/issues?${params.toString()}`);
    },
  });

  // 2. Fetch single issue for details
  const { data: selectedIssue, isLoading: isViewLoading } = useQuery({
    queryKey: ['issue-detail', viewIssueId || ackIssueId],
    queryFn: () => {
      const id = viewIssueId || ackIssueId;
      if (!id) return null;
      return api.get<IssueResponse>(`/api/store/issues/${id}`);
    },
    enabled: !!(viewIssueId || ackIssueId),
  });

  // 3. Acknowledge Mutation
  const ackMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: AcknowledgeIssueRequest }) =>
      api.post<IssueResponse>(`/api/store/issues/${id}/acknowledge`, body),
    onSuccess: (data) => {
      toast.success(`Custody acknowledged successfully for ${data.issueNo}`);
      queryClient.invalidateQueries({ queryKey: ['acknowledgements-issues'] });
      setAckIssueId(null);
      setAckRemarks('');
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to acknowledge custody');
    },
  });

  const handleAckSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!ackIssueId) return;
    ackMutation.mutate({
      id: ackIssueId,
      body: {
        acknowledgementStatus: ackStatus,
        remarks: ackRemarks || undefined,
      },
    });
  };

  const issuesList = issuesData?.content || [];
  const pendingCount = issuesList.filter((i) => i.status === 'POSTED').length;
  const ackCount = issuesList.filter((i) => i.status === 'ACKNOWLEDGED').length;

  const columns: ColumnDef<IssueSummaryResponse>[] = [
    {
      accessorKey: 'issueNo',
      header: 'Issue Number',
      cell: (row) => (
        <span className="font-mono text-sm font-semibold text-primary">
          {row.issueNo}
        </span>
      ),
    },
    {
      accessorKey: 'issueDate',
      header: 'Issue Date',
      cell: (row) => (
        <span className="text-sm">
          {new Date(row.issueDate).toLocaleDateString()}
        </span>
      ),
    },
    {
      accessorKey: 'storeCode',
      header: 'Issuing Store',
      cell: (row) => (
        <span className="inline-flex items-center gap-1.5 text-sm">
          <Building2 className="h-4 w-4 text-muted-foreground" />
          {row.storeCode}
        </span>
      ),
    },
    {
      accessorKey: 'issuedToNameSnapshot',
      header: 'Recipient / Entity',
      cell: (row) => (
        <div className="flex flex-col">
          <span className="text-sm font-medium">
            {row.issuedToNameSnapshot || 'Direct Issue'}
          </span>
          <span className="text-[11px] text-muted-foreground capitalize">
            {row.issuedToType?.toLowerCase()}
          </span>
        </div>
      ),
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: (row) => <StatusBadge status={row.status} />,
    },
    {
      header: 'Actions',
      cell: (row) => {
        return (
          <div className="flex items-center gap-1.5">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setViewIssueId(row.id)}
              className="h-8 px-2"
              title="View Items"
            >
              <Eye className="h-4 w-4" />
            </Button>
            {row.status === 'POSTED' && (
              <Button
                variant="default"
                size="sm"
                onClick={() => {
                  setAckIssueId(row.id);
                  setAckStatus('ACCEPTED');
                  setAckRemarks('');
                }}
                className="h-8 gap-1.5 bg-primary text-primary-foreground hover:bg-primary/90 text-xs"
              >
                <CheckCheck className="h-3.5 w-3.5" />
                Acknowledge Receipt
              </Button>
            )}
            {row.status === 'ACKNOWLEDGED' && (
              <Badge variant="outline" className="border-emerald-500/50 text-emerald-600 gap-1 text-[11px]">
                <CheckCircle2 className="h-3 w-3" />
                Signed & Verified
              </Badge>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <div className="space-y-6 p-6">
      {/* Page Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <CheckCheck className="h-6 w-6 text-primary" />
            Digital Custody Acknowledgements
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Employee and custodian verification portal for physical receipt of issued IT hardware, assets, and materials.
          </p>
        </div>
      </div>

      {/* Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="border border-border/60 shadow-sm bg-card">
          <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
            <CardTitle className="text-sm font-medium text-muted-foreground">Pending Signature</CardTitle>
            <Clock className="h-4 w-4 text-amber-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-amber-600">{pendingCount}</div>
            <p className="text-xs text-muted-foreground mt-1">Dispatched items awaiting digital confirmation</p>
          </CardContent>
        </Card>

        <Card className="border border-border/60 shadow-sm bg-card">
          <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
            <CardTitle className="text-sm font-medium text-muted-foreground">Acknowledged Custody</CardTitle>
            <ShieldCheck className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-emerald-600">{ackCount}</div>
            <p className="text-xs text-muted-foreground mt-1">Legally bound asset handovers</p>
          </CardContent>
        </Card>

        <Card className="border border-border/60 shadow-sm bg-card">
          <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
            <CardTitle className="text-sm font-medium text-muted-foreground">Total In Scope</CardTitle>
            <FileCheck className="h-4 w-4 text-primary" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{issuesData?.totalElements || 0}</div>
            <p className="text-xs text-muted-foreground mt-1">Store issues requiring digital receipt</p>
          </CardContent>
        </Card>
      </div>

      {/* Filter and Search Bar */}
      <Card className="border border-border/60 shadow-sm">
        <CardContent className="p-4 flex flex-col md:flex-row gap-3 items-center justify-between">
          <div className="flex items-center gap-2">
            {[
              { id: 'PENDING', label: 'Pending Sign-off' },
              { id: 'ACKNOWLEDGED', label: 'Completed Signatures' },
              { id: 'ALL', label: 'All Store Issues' },
            ].map((tab) => (
              <Button
                key={tab.id}
                variant={statusFilter === tab.id ? 'default' : 'outline'}
                size="sm"
                onClick={() => setStatusFilter(tab.id)}
                className="text-xs h-8"
              >
                {tab.label}
              </Button>
            ))}
          </div>

          <Input
            placeholder="Search by issue #, recipient name..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="h-8 text-xs w-full sm:w-[260px]"
          />
        </CardContent>
      </Card>

      {/* Acknowledgements Table */}
      <div className="rounded-md border bg-card shadow-sm">
        <DataTable
          columns={columns}
          data={issuesList}
          isLoading={isLoading}
        />
      </div>

      {/* Modal 1: Sign / Acknowledge Receipt */}
      <Dialog open={!!ackIssueId} onOpenChange={(open) => !open && setAckIssueId(null)}>
        <DialogContent className="max-w-xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-xl font-bold">
              <CheckCheck className="h-5 w-5 text-primary" />
              Digital Custody Acknowledgement
            </DialogTitle>
            <DialogDescription>
              Confirm receipt of physical items/hardware and record acceptance into NICSI records.
            </DialogDescription>
          </DialogHeader>

          {selectedIssue && (
            <form onSubmit={handleAckSubmit} className="space-y-4">
              <div className="p-3 rounded-lg bg-muted/40 border text-xs space-y-1.5">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Issue Reference:</span>
                  <span className="font-mono font-semibold">{selectedIssue.issueNo}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Store:</span>
                  <span className="font-medium">{selectedIssue.storeName}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Issued Date:</span>
                  <span>{new Date(selectedIssue.issueDate).toLocaleDateString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Recipient:</span>
                  <span className="font-medium">{selectedIssue.issuedToNameSnapshot || 'Recipient'}</span>
                </div>
              </div>

              {/* Items in Issue */}
              <div>
                <Label className="text-xs font-semibold mb-2 block">Items Being Acknowledged</Label>
                <div className="border rounded-md overflow-hidden">
                  <table className="w-full text-xs">
                    <thead className="bg-muted/50 border-b">
                      <tr>
                        <th className="py-2 px-3 text-left">Item</th>
                        <th className="py-2 px-3 text-right">Quantity</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {selectedIssue.items?.map((it) => (
                        <tr key={it.id}>
                          <td className="py-2 px-3">
                            <span className="font-medium">{it.itemName}</span>
                            <span className="text-[11px] text-muted-foreground block">{it.itemCode}</span>
                          </td>
                          <td className="py-2 px-3 text-right font-semibold">{it.issueQty}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="space-y-3 pt-2">
                <div>
                  <Label className="text-xs font-medium">Verification Status *</Label>
                  <Select
                    value={ackStatus}
                    onValueChange={(val: any) => setAckStatus(val)}
                  >
                    <SelectTrigger className="h-8 text-xs mt-1">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ACCEPTED">ACCEPTED (All items verified in working condition)</SelectItem>
                      <SelectItem value="PARTIAL">PARTIAL (Some items missing or damaged)</SelectItem>
                      <SelectItem value="REJECTED">REJECTED (Items not received / disputed)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label className="text-xs font-medium">Digital Signature / Acknowledgement Note</Label>
                  <Textarea
                    placeholder="e.g. Received laptop in working condition with charger and accessories..."
                    value={ackRemarks}
                    onChange={(e) => setAckRemarks(e.target.value)}
                    className="mt-1 text-xs resize-none"
                    rows={2}
                  />
                </div>
              </div>

              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setAckIssueId(null)}>
                  Cancel
                </Button>
                <Button
                  type="submit"
                  disabled={ackMutation.isPending}
                  className="bg-primary text-primary-foreground"
                >
                  {ackMutation.isPending ? 'Confirming...' : 'Sign & Complete Acknowledgement'}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>

      {/* Modal 2: View Issue Details */}
      <Dialog open={!!viewIssueId} onOpenChange={(open) => !open && setViewIssueId(null)}>
        <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-xl font-bold">
              <Eye className="h-5 w-5 text-primary" />
              Issue Note Details
            </DialogTitle>
          </DialogHeader>

          {isViewLoading || !selectedIssue ? (
            <div className="py-12 text-center text-sm text-muted-foreground">Loading details...</div>
          ) : (
            <div className="space-y-4">
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-3 rounded-lg bg-muted/30 border text-xs">
                <div>
                  <span className="text-muted-foreground block">Issue #</span>
                  <span className="font-mono font-semibold">{selectedIssue.issueNo}</span>
                </div>
                <div>
                  <span className="text-muted-foreground block">Date</span>
                  <span>{new Date(selectedIssue.issueDate).toLocaleDateString()}</span>
                </div>
                <div>
                  <span className="text-muted-foreground block">Store</span>
                  <span className="font-medium">{selectedIssue.storeName}</span>
                </div>
                <div>
                  <span className="text-muted-foreground block">Status</span>
                  <StatusBadge status={selectedIssue.status} />
                </div>
              </div>

              {selectedIssue.purpose && (
                <div className="p-3 rounded bg-muted/20 border text-xs">
                  <strong>Purpose:</strong> {selectedIssue.purpose}
                </div>
              )}

              <div>
                <Label className="text-xs font-semibold mb-2 block">Line Items</Label>
                <div className="border rounded-md overflow-hidden">
                  <table className="w-full text-xs">
                    <thead className="bg-muted/50 border-b">
                      <tr>
                        <th className="py-2 px-3 text-left">#</th>
                        <th className="py-2 px-3 text-left">Item</th>
                        <th className="py-2 px-3 text-right">Qty</th>
                        <th className="py-2 px-3 text-left">Location</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {selectedIssue.items?.map((it) => (
                        <tr key={it.id}>
                          <td className="py-2 px-3 font-mono">{it.lineNo}</td>
                          <td className="py-2 px-3">
                            <span className="font-medium">{it.itemName}</span>
                            <span className="text-[11px] text-muted-foreground block">{it.itemCode}</span>
                          </td>
                          <td className="py-2 px-3 text-right font-semibold">{it.issueQty}</td>
                          <td className="py-2 px-3">
                            <Badge variant="secondary" className="text-[10px]">
                              {it.locationCode}
                            </Badge>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setViewIssueId(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
