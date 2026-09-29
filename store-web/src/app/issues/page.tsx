'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import {
  IssueResponse,
  IssueSummaryResponse,
  CreateIssueRequest,
  CreateIssueItemRequest,
  PostIssueRequest,
  AcknowledgeIssueRequest,
} from '@/types/issue';
import {
  ItemResponse,
  StoreSiteResponse,
  StorageLocationResponse,
  PageResponse,
} from '@/types/master';
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
  PackageCheck,
  Plus,
  Send,
  Eye,
  Trash2,
  Building2,
  Calendar,
  Layers,
  MapPin,
  ClipboardCheck,
  CheckCircle2,
  AlertCircle,
  Clock,
  RotateCcw,
  CheckCheck,
  FileText,
  User,
} from 'lucide-react';
import { toast } from 'sonner';

interface FormLine {
  itemId: string;
  itemCode: string;
  itemName: string;
  locationId: string;
  issueQty: number;
  remarks: string;
}

export default function IssuesPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');

  // Modals state
  const [createOpen, setCreateOpen] = useState(false);
  const [viewIssueId, setViewIssueId] = useState<string | null>(null);
  const [postIssueId, setPostIssueId] = useState<string | null>(null);
  const [ackIssueId, setAckIssueId] = useState<string | null>(null);

  // Form State for Create
  const [storeId, setStoreId] = useState('');
  const [issuedToType, setIssuedToType] = useState<'EMPLOYEE' | 'DEPARTMENT' | 'PROJECT' | 'LOCATION' | 'OTHER'>('EMPLOYEE');
  const [recipientName, setRecipientName] = useState('');
  const [departmentName, setDepartmentName] = useState('');
  const [projectName, setProjectName] = useState('');
  const [purpose, setPurpose] = useState('');
  const [formLines, setFormLines] = useState<FormLine[]>([]);

  // Post form state
  const [postRemarks, setPostRemarks] = useState('');

  // Acknowledge form state
  const [ackStatus, setAckStatus] = useState<'ACCEPTED' | 'PARTIAL' | 'REJECTED'>('ACCEPTED');
  const [ackRemarks, setAckRemarks] = useState('');

  // Line item input temporary state
  const [selectedItemId, setSelectedItemId] = useState('');
  const [selectedLocationId, setSelectedLocationId] = useState('');
  const [lineQty, setLineQty] = useState<number>(1);
  const [lineRemarks, setLineRemarks] = useState('');

  // Queries
  const { data: issuesPage, isLoading: loadingIssues } = useQuery<PageResponse<IssueSummaryResponse>>({
    queryKey: ['issues', statusFilter, searchTerm],
    queryFn: () => {
      const params = new URLSearchParams();
      if (statusFilter !== 'ALL') params.set('status', statusFilter);
      if (searchTerm) params.set('search', searchTerm);
      params.set('size', '50');
      return api.get(`/api/store/issues?${params.toString()}`);
    },
  });

  const { data: stores } = useQuery<PageResponse<StoreSiteResponse>>({
    queryKey: ['stores-list'],
    queryFn: () => api.get('/api/store/stores?size=200'),
  });

  const { data: itemsPage } = useQuery<PageResponse<ItemResponse>>({
    queryKey: ['items-list'],
    queryFn: () => api.get('/api/store/items?size=100'),
  });

  const { data: locationsPage } = useQuery<PageResponse<StorageLocationResponse>>({
    queryKey: ['locations-by-store', storeId],
    queryFn: () => api.get(`/api/store/locations?storeId=${storeId}&size=200`),
    enabled: Boolean(storeId),
  });

  const { data: selectedIssue, isLoading: loadingDetail } = useQuery<IssueResponse>({
    queryKey: ['issue-detail', viewIssueId],
    queryFn: () => api.get(`/api/store/issues/${viewIssueId}`),
    enabled: Boolean(viewIssueId),
  });

  // Mutations
  const createMutation = useMutation({
    mutationFn: (payload: CreateIssueRequest) => api.post('/api/store/issues', payload),
    onSuccess: () => {
      toast.success('Issue draft created successfully');
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      resetForm();
      setCreateOpen(false);
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create issue');
    },
  });

  const submitMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/store/issues/${id}/submit`),
    onSuccess: () => {
      toast.success('Issue submitted for approval');
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      if (viewIssueId) queryClient.invalidateQueries({ queryKey: ['issue-detail', viewIssueId] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to submit issue');
    },
  });

  const approveMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/store/issues/${id}/approve`),
    onSuccess: () => {
      toast.success('Issue approved successfully');
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      if (viewIssueId) queryClient.invalidateQueries({ queryKey: ['issue-detail', viewIssueId] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to approve issue');
    },
  });

  const postMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: PostIssueRequest }) =>
      api.post(`/api/store/issues/${id}/post`, payload),
    onSuccess: () => {
      toast.success('Stock deducted & assets assigned successfully');
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      setPostIssueId(null);
      setPostRemarks('');
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to post issue to inventory');
    },
  });

  const acknowledgeMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: AcknowledgeIssueRequest }) =>
      api.post(`/api/store/issues/${id}/acknowledge`, payload),
    onSuccess: () => {
      toast.success('Issue acknowledged successfully');
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      setAckIssueId(null);
      setAckRemarks('');
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to acknowledge issue');
    },
  });

  const resetForm = () => {
    setStoreId('');
    setIssuedToType('EMPLOYEE');
    setRecipientName('');
    setDepartmentName('');
    setProjectName('');
    setPurpose('');
    setFormLines([]);
    setSelectedItemId('');
    setSelectedLocationId('');
    setLineQty(1);
    setLineRemarks('');
  };

  const handleAddLine = () => {
    if (!selectedItemId || !selectedLocationId || lineQty <= 0) {
      toast.error('Please select item, location and positive quantity');
      return;
    }
    const item = itemsPage?.content.find((i) => i.id === selectedItemId);
    if (!item) return;

    setFormLines((prev) => [
      ...prev,
      {
        itemId: item.id,
        itemCode: item.itemCode,
        itemName: item.itemName,
        locationId: selectedLocationId,
        issueQty: lineQty,
        remarks: lineRemarks,
      },
    ]);
    setSelectedItemId('');
    setLineQty(1);
    setLineRemarks('');
  };

  const handleRemoveLine = (idx: number) => {
    setFormLines((prev) => prev.filter((_, i) => i !== idx));
  };

  const handleCreateSubmit = () => {
    if (!storeId) {
      toast.error('Store warehouse is required');
      return;
    }
    if (formLines.length === 0) {
      toast.error('At least one line item is required');
      return;
    }

    const payload: CreateIssueRequest = {
      storeId,
      issuedToType,
      issuedToNameSnapshot: recipientName || undefined,
      departmentNameSnapshot: departmentName || undefined,
      projectNameSnapshot: projectName || undefined,
      purpose: purpose || undefined,
      items: formLines.map((line, idx) => ({
        lineNo: idx + 1,
        itemId: line.itemId,
        locationId: line.locationId,
        issueQty: line.issueQty,
        remarks: line.remarks || undefined,
      })),
    };

    createMutation.mutate(payload);
  };

  // Metrics
  const allIssues = issuesPage?.content || [];
  const totalCount = issuesPage?.totalElements || allIssues.length;
  const draftCount = allIssues.filter((i) => i.status === 'DRAFT').length;
  const submittedCount = allIssues.filter((i) => i.status === 'SUBMITTED' || i.status === 'APPROVED').length;
  const postedCount = allIssues.filter((i) => i.status === 'POSTED').length;
  const ackCount = allIssues.filter((i) => i.status === 'ACKNOWLEDGED').length;

  const columns: ColumnDef<IssueSummaryResponse>[] = [
    {
      header: 'Issue No',
      accessorKey: 'issueNo',
      cell: (row) => (
        <span className="font-semibold text-primary font-mono text-xs">
          {row.issueNo}
        </span>
      ),
    },
    {
      header: 'Issue Date',
      accessorKey: 'issueDate',
      cell: (row) => (
        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <Calendar className="h-3.5 w-3.5" />
          <span>{row.issueDate}</span>
        </div>
      ),
    },
    {
      header: 'Store',
      accessorKey: 'storeCode',
      cell: (row) => (
        <Badge variant="outline" className="font-mono text-xs">
          {row.storeCode}
        </Badge>
      ),
    },
    {
      header: 'Issued To',
      cell: (row) => (
        <div className="flex flex-col text-xs">
          <span className="font-medium text-foreground">{row.issuedToNameSnapshot || 'General'}</span>
          <span className="text-[10px] text-muted-foreground uppercase">{row.issuedToType}</span>
        </div>
      ),
    },
    {
      header: 'Items',
      accessorKey: 'lineCount',
      cell: (row) => (
        <div className="flex items-center gap-1 text-xs">
          <Layers className="h-3.5 w-3.5 text-muted-foreground" />
          <span>{row.lineCount} line{row.lineCount !== 1 ? 's' : ''}</span>
        </div>
      ),
    },
    {
      header: 'Status',
      accessorKey: 'status',
      cell: (row) => <StatusBadge status={row.status} />,
    },
    {
      header: 'Actions',
      cell: (row) => (
        <div className="flex items-center gap-1.5 justify-end">
          <Button
            size="sm"
            variant="ghost"
            className="h-8 w-8 p-0"
            title="View Details"
            onClick={() => setViewIssueId(row.id)}
          >
            <Eye className="h-4 w-4" />
          </Button>

          {row.status === 'DRAFT' && (
            <Button
              size="sm"
              variant="outline"
              className="h-8 px-2.5 text-xs gap-1"
              title="Submit for Approval"
              onClick={() => submitMutation.mutate(row.id)}
              disabled={submitMutation.isPending}
            >
              <Send className="h-3.5 w-3.5" />
              <span>Submit</span>
            </Button>
          )}

          {row.status === 'SUBMITTED' && (
            <Button
              size="sm"
              variant="outline"
              className="h-8 px-2.5 text-xs gap-1 border-blue-200 text-blue-700 hover:bg-blue-50"
              title="Approve Issue"
              onClick={() => approveMutation.mutate(row.id)}
              disabled={approveMutation.isPending}
            >
              <CheckCircle2 className="h-3.5 w-3.5 text-blue-600" />
              <span>Approve</span>
            </Button>
          )}

          {(row.status === 'SUBMITTED' || row.status === 'APPROVED') && (
            <Button
              size="sm"
              variant="default"
              className="h-8 px-2.5 text-xs gap-1 bg-emerald-600 hover:bg-emerald-700 text-white"
              title="Post Stock"
              onClick={() => setPostIssueId(row.id)}
            >
              <PackageCheck className="h-3.5 w-3.5" />
              <span>Post Stock</span>
            </Button>
          )}

          {row.status === 'POSTED' && (
            <Button
              size="sm"
              variant="outline"
              className="h-8 px-2.5 text-xs gap-1 border-indigo-200 text-indigo-700 hover:bg-indigo-50"
              title="Acknowledge Receipt"
              onClick={() => setAckIssueId(row.id)}
            >
              <CheckCheck className="h-3.5 w-3.5 text-indigo-600" />
              <span>Acknowledge</span>
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <PackageCheck className="h-7 w-7 text-primary" />
            Material Issue & Allocation
          </h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Issue inventory and assign serialized assets to employees, departments, and project sites.
          </p>
        </div>
        <Button
          onClick={() => {
            resetForm();
            setCreateOpen(true);
          }}
          className="gap-2 shadow-sm"
        >
          <Plus className="h-4 w-4" />
          <span>New Issue Voucher</span>
        </Button>
      </div>

      {/* Metrics Cards */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3.5">
        <Card className="shadow-none border-border/80">
          <CardHeader className="pb-1.5 pt-3.5 px-4">
            <CardTitle className="text-xs font-medium text-muted-foreground uppercase tracking-wider flex items-center justify-between">
              Total Issues
              <FileText className="h-3.5 w-3.5 text-muted-foreground" />
            </CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3.5">
            <div className="text-xl font-bold text-foreground">{totalCount}</div>
          </CardContent>
        </Card>

        <Card className="shadow-none border-border/80">
          <CardHeader className="pb-1.5 pt-3.5 px-4">
            <CardTitle className="text-xs font-medium text-amber-600 uppercase tracking-wider flex items-center justify-between">
              Drafts
              <Clock className="h-3.5 w-3.5 text-amber-500" />
            </CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3.5">
            <div className="text-xl font-bold text-amber-600">{draftCount}</div>
          </CardContent>
        </Card>

        <Card className="shadow-none border-border/80">
          <CardHeader className="pb-1.5 pt-3.5 px-4">
            <CardTitle className="text-xs font-medium text-blue-600 uppercase tracking-wider flex items-center justify-between">
              Submitted / Approved
              <AlertCircle className="h-3.5 w-3.5 text-blue-500" />
            </CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3.5">
            <div className="text-xl font-bold text-blue-600">{submittedCount}</div>
          </CardContent>
        </Card>

        <Card className="shadow-none border-border/80">
          <CardHeader className="pb-1.5 pt-3.5 px-4">
            <CardTitle className="text-xs font-medium text-emerald-600 uppercase tracking-wider flex items-center justify-between">
              Stock Posted
              <PackageCheck className="h-3.5 w-3.5 text-emerald-500" />
            </CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3.5">
            <div className="text-xl font-bold text-emerald-600">{postedCount}</div>
          </CardContent>
        </Card>

        <Card className="shadow-none border-border/80">
          <CardHeader className="pb-1.5 pt-3.5 px-4">
            <CardTitle className="text-xs font-medium text-indigo-600 uppercase tracking-wider flex items-center justify-between">
              Acknowledged
              <CheckCheck className="h-3.5 w-3.5 text-indigo-500" />
            </CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3.5">
            <div className="text-xl font-bold text-indigo-600">{ackCount}</div>
          </CardContent>
        </Card>
      </div>

      {/* Filter and Table Card */}
      <Card className="shadow-none border-border/80">
        <CardContent className="p-4 space-y-4">
          <div className="flex flex-col md:flex-row items-center justify-between gap-3">
            <div className="flex items-center gap-2.5 w-full md:w-auto">
              <Input
                placeholder="Search Issue No / Recipient..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full md:w-72 h-9 text-xs"
              />
              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger className="w-36 h-9 text-xs">
                  <SelectValue placeholder="Status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Statuses</SelectItem>
                  <SelectItem value="DRAFT">Draft</SelectItem>
                  <SelectItem value="SUBMITTED">Submitted</SelectItem>
                  <SelectItem value="APPROVED">Approved</SelectItem>
                  <SelectItem value="POSTED">Posted</SelectItem>
                  <SelectItem value="ACKNOWLEDGED">Acknowledged</SelectItem>
                  <SelectItem value="REJECTED">Rejected</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => queryClient.invalidateQueries({ queryKey: ['issues'] })}
              className="h-9 px-2.5 text-xs text-muted-foreground gap-1.5"
            >
              <RotateCcw className="h-3.5 w-3.5" />
              <span>Refresh</span>
            </Button>
          </div>

          <DataTable
            columns={columns}
            data={allIssues}
            isLoading={loadingIssues}
          />
        </CardContent>
      </Card>

      {/* CREATE ISSUE DIALOG */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg">
              <PackageCheck className="h-5 w-5 text-primary" />
              Create Material Issue Voucher
            </DialogTitle>
            <DialogDescription className="text-xs">
              Prepare a material issue requisition to deduct stock and assign custody.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            {/* Header Form */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div className="space-y-1">
                <Label className="text-xs font-semibold">Store Warehouse *</Label>
                <Select value={storeId} onValueChange={setStoreId}>
                  <SelectTrigger className="h-9 text-xs">
                    <SelectValue placeholder="Select warehouse..." />
                  </SelectTrigger>
                  <SelectContent>
                    {stores?.content.map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.storeName} ({s.storeCode})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1">
                <Label className="text-xs font-semibold">Issued To Type *</Label>
                <Select value={issuedToType} onValueChange={(v) => setIssuedToType(v as any)}>
                  <SelectTrigger className="h-9 text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="EMPLOYEE">Employee</SelectItem>
                    <SelectItem value="DEPARTMENT">Department</SelectItem>
                    <SelectItem value="PROJECT">Project Site</SelectItem>
                    <SelectItem value="LOCATION">Storage Location</SelectItem>
                    <SelectItem value="OTHER">Other</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1">
                <Label className="text-xs font-semibold">Recipient / Custodian Name</Label>
                <Input
                  placeholder="e.g. John Doe / IT Cell"
                  value={recipientName}
                  onChange={(e) => setRecipientName(e.target.value)}
                  className="h-9 text-xs"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label className="text-xs font-semibold">Department / Project Snapshot</Label>
                <Input
                  placeholder="e.g. NICSI Cloud Division / UIDAI Project"
                  value={departmentName || projectName}
                  onChange={(e) => {
                    setDepartmentName(e.target.value);
                    setProjectName(e.target.value);
                  }}
                  className="h-9 text-xs"
                />
              </div>
              <div className="space-y-1">
                <Label className="text-xs font-semibold">Purpose / Justification</Label>
                <Input
                  placeholder="e.g. Laptop replacement for developer"
                  value={purpose}
                  onChange={(e) => setPurpose(e.target.value)}
                  className="h-9 text-xs"
                />
              </div>
            </div>

            {/* Line Items Builder */}
            <div className="border border-border/80 rounded-lg p-3 bg-muted/20 space-y-3">
              <h4 className="text-xs font-bold text-foreground uppercase tracking-wider flex items-center gap-1.5">
                <Layers className="h-3.5 w-3.5 text-primary" />
                Add Item Line
              </h4>

              <div className="grid grid-cols-1 md:grid-cols-4 gap-2.5">
                <div className="md:col-span-2 space-y-1">
                  <Label className="text-[11px]">Item Master *</Label>
                  <Select value={selectedItemId} onValueChange={setSelectedItemId}>
                    <SelectTrigger className="h-8 text-xs">
                      <SelectValue placeholder="Choose item..." />
                    </SelectTrigger>
                    <SelectContent>
                      {itemsPage?.content.map((item) => (
                        <SelectItem key={item.id} value={item.id}>
                          {item.itemCode} - {item.itemName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1">
                  <Label className="text-[11px]">Storage Rack/Bin *</Label>
                  <Select value={selectedLocationId} onValueChange={setSelectedLocationId} disabled={!storeId}>
                    <SelectTrigger className="h-8 text-xs">
                      <SelectValue placeholder={storeId ? 'Select rack...' : 'Select store first'} />
                    </SelectTrigger>
                    <SelectContent>
                      {locationsPage?.content.map((loc) => (
                        <SelectItem key={loc.id} value={loc.id}>
                          {loc.locationCode} - {loc.locationName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1">
                  <Label className="text-[11px]">Quantity *</Label>
                  <Input
                    type="number"
                    min="1"
                    step="1"
                    value={lineQty}
                    onChange={(e) => setLineQty(Number(e.target.value))}
                    className="h-8 text-xs"
                  />
                </div>
              </div>

              <div className="flex items-center gap-2">
                <Input
                  placeholder="Line remarks / Serial hints..."
                  value={lineRemarks}
                  onChange={(e) => setLineRemarks(e.target.value)}
                  className="h-8 text-xs flex-1"
                />
                <Button size="sm" type="button" onClick={handleAddLine} className="h-8 px-3 text-xs gap-1">
                  <Plus className="h-3.5 w-3.5" />
                  <span>Add Line</span>
                </Button>
              </div>
            </div>

            {/* Added Lines List */}
            {formLines.length > 0 && (
              <div className="border border-border/80 rounded-md overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-muted text-muted-foreground uppercase font-semibold text-[10px]">
                    <tr>
                      <th className="p-2">#</th>
                      <th className="p-2">Item Code & Name</th>
                      <th className="p-2 text-right">Qty</th>
                      <th className="p-2">Remarks</th>
                      <th className="p-2 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {formLines.map((line, idx) => (
                      <tr key={idx} className="hover:bg-muted/40">
                        <td className="p-2 font-mono">{idx + 1}</td>
                        <td className="p-2 font-medium">
                          {line.itemCode} - {line.itemName}
                        </td>
                        <td className="p-2 text-right font-mono font-semibold">{line.issueQty}</td>
                        <td className="p-2 text-muted-foreground">{line.remarks || '—'}</td>
                        <td className="p-2 text-right">
                          <Button
                            size="sm"
                            variant="ghost"
                            className="h-7 w-7 p-0 text-destructive hover:bg-destructive/10"
                            onClick={() => handleRemoveLine(idx)}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="outline" size="sm" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button
              size="sm"
              onClick={handleCreateSubmit}
              disabled={createMutation.isPending || formLines.length === 0}
              className="gap-1.5"
            >
              <PackageCheck className="h-4 w-4" />
              <span>Save Issue Draft</span>
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* VIEW ISSUE DETAILS MODAL */}
      <Dialog open={Boolean(viewIssueId)} onOpenChange={(open) => !open && setViewIssueId(null)}>
        <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="text-lg font-bold flex items-center justify-between">
              <span className="font-mono">{selectedIssue?.issueNo}</span>
              {selectedIssue?.status && <StatusBadge status={selectedIssue.status} />}
            </DialogTitle>
            <DialogDescription className="text-xs">
              Material Issue Voucher details and item specifications.
            </DialogDescription>
          </DialogHeader>

          {loadingDetail ? (
            <div className="py-8 text-center text-xs text-muted-foreground">Loading issue details...</div>
          ) : (
            selectedIssue && (
              <div className="space-y-4 text-xs">
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 p-3 rounded-lg bg-muted/30 border border-border/60">
                  <div>
                    <span className="text-[10px] text-muted-foreground uppercase block">Store Site</span>
                    <span className="font-semibold">{selectedIssue.storeName || selectedIssue.storeCode}</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-muted-foreground uppercase block">Issue Date</span>
                    <span className="font-semibold">{selectedIssue.issueDate}</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-muted-foreground uppercase block">Recipient</span>
                    <span className="font-semibold">{selectedIssue.issuedToNameSnapshot || 'General'}</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-muted-foreground uppercase block">Recipient Type</span>
                    <Badge variant="outline" className="text-[10px] uppercase">{selectedIssue.issuedToType}</Badge>
                  </div>
                  <div>
                    <span className="text-[10px] text-muted-foreground uppercase block">Dept / Project</span>
                    <span className="font-semibold">{selectedIssue.departmentNameSnapshot || selectedIssue.projectNameSnapshot || '—'}</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-muted-foreground uppercase block">Acknowledgement</span>
                    <Badge variant="secondary" className="text-[10px]">{selectedIssue.acknowledgementStatus || 'PENDING'}</Badge>
                  </div>
                </div>

                {selectedIssue.purpose && (
                  <div className="p-2.5 rounded bg-muted/20 border border-border/60">
                    <span className="text-[10px] text-muted-foreground uppercase block font-semibold">Purpose</span>
                    <p className="mt-0.5">{selectedIssue.purpose}</p>
                  </div>
                )}

                <div>
                  <h4 className="font-semibold text-xs mb-1.5 uppercase tracking-wider text-muted-foreground">
                    Issued Items ({selectedIssue.items?.length || 0})
                  </h4>
                  <div className="border border-border/80 rounded-md overflow-hidden">
                    <table className="w-full text-xs text-left">
                      <thead className="bg-muted text-muted-foreground text-[10px] uppercase">
                        <tr>
                          <th className="p-2">#</th>
                          <th className="p-2">Item Code</th>
                          <th className="p-2">Description</th>
                          <th className="p-2">Location</th>
                          <th className="p-2 text-right">Quantity</th>
                          <th className="p-2 text-right">Unit Cost</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {selectedIssue.items?.map((item) => (
                          <tr key={item.id}>
                            <td className="p-2 font-mono">{item.lineNo}</td>
                            <td className="p-2 font-mono font-medium text-primary">{item.itemCode}</td>
                            <td className="p-2">{item.itemName}</td>
                            <td className="p-2 font-mono">{item.locationCode}</td>
                            <td className="p-2 text-right font-semibold font-mono">{item.issueQty}</td>
                            <td className="p-2 text-right font-mono">₹{Number(item.unitCost).toFixed(2)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            )
          )}

          <DialogFooter>
            <Button size="sm" variant="outline" onClick={() => setViewIssueId(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* POST STOCK DIALOG */}
      <Dialog open={Boolean(postIssueId)} onOpenChange={(open) => !open && setPostIssueId(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base text-emerald-700">
              <PackageCheck className="h-5 w-5" />
              Post Stock & Assign Assets
            </DialogTitle>
            <DialogDescription className="text-xs">
              Confirm material posting. This creates immutable OUT ledger records, reduces warehouse stock balance, and updates asset custody.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3 py-2 text-xs">
            <div className="space-y-1">
              <Label className="text-xs font-semibold">Posting Remarks</Label>
              <Textarea
                placeholder="Optional notes regarding issue handover..."
                value={postRemarks}
                onChange={(e) => setPostRemarks(e.target.value)}
                className="h-20 text-xs"
              />
            </div>
            <div className="p-2.5 rounded bg-emerald-50 text-emerald-800 border border-emerald-200 text-xs">
              <span className="font-semibold block">Maker-Checker Notice:</span>
              The system requires the user posting this voucher to differ from the creator.
            </div>
          </div>

          <DialogFooter>
            <Button size="sm" variant="outline" onClick={() => setPostIssueId(null)}>
              Cancel
            </Button>
            <Button
              size="sm"
              className="bg-emerald-600 hover:bg-emerald-700 text-white"
              onClick={() => {
                if (postIssueId) {
                  postMutation.mutate({
                    id: postIssueId,
                    payload: { remarks: postRemarks || undefined },
                  });
                }
              }}
              disabled={postMutation.isPending}
            >
              Confirm Post Stock
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ACKNOWLEDGE RECEIPT DIALOG */}
      <Dialog open={Boolean(ackIssueId)} onOpenChange={(open) => !open && setAckIssueId(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base text-indigo-700">
              <CheckCheck className="h-5 w-5" />
              Recipient Acknowledgement
            </DialogTitle>
            <DialogDescription className="text-xs">
              Record recipient digital acknowledgement and physical acceptance status.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3 py-2 text-xs">
            <div className="space-y-1">
              <Label className="text-xs font-semibold">Acknowledgement Status *</Label>
              <Select value={ackStatus} onValueChange={(v) => setAckStatus(v as any)}>
                <SelectTrigger className="h-9 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ACCEPTED">Accepted (Full receipt in good order)</SelectItem>
                  <SelectItem value="PARTIAL">Partially Received</SelectItem>
                  <SelectItem value="REJECTED">Rejected / Not Received</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Acknowledgement Remarks</Label>
              <Textarea
                placeholder="Confirmation remarks by receiver..."
                value={ackRemarks}
                onChange={(e) => setAckRemarks(e.target.value)}
                className="h-20 text-xs"
              />
            </div>
          </div>

          <DialogFooter>
            <Button size="sm" variant="outline" onClick={() => setAckIssueId(null)}>
              Cancel
            </Button>
            <Button
              size="sm"
              className="bg-indigo-600 hover:bg-indigo-700 text-white"
              onClick={() => {
                if (ackIssueId) {
                  acknowledgeMutation.mutate({
                    id: ackIssueId,
                    payload: { acknowledgementStatus: ackStatus, remarks: ackRemarks || undefined },
                  });
                }
              }}
              disabled={acknowledgeMutation.isPending}
            >
              Submit Acknowledgement
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
