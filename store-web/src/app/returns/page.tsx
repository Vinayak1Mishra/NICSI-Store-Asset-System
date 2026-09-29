'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import {
  ReturnResponse,
  ReturnSummaryResponse,
  CreateReturnRequest,
  CreateReturnItemRequest,
  ReceiveReturnRequest,
  ReceiveReturnLineRequest,
  PostReturnRequest,
  ConditionStatus,
  Disposition,
} from '@/types/return';
import {
  ItemResponse,
  StoreSiteResponse,
  StorageLocationResponse,
  PageResponse,
} from '@/types/master';
import { AssetSummaryResponse } from '@/types/asset';
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
  RotateCcw,
  Plus,
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
  Send,
  Boxes,
  ShieldAlert,
  Wrench,
  Archive,
} from 'lucide-react';
import { toast } from 'sonner';

interface FormLine {
  itemId: string;
  itemCode: string;
  itemName: string;
  assetId?: string;
  assetCode?: string;
  locationId: string;
  returnQty: number;
  conditionStatus: ConditionStatus;
  remarks: string;
}

export default function ReturnsPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [storeFilter, setStoreFilter] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');

  // Modals state
  const [createOpen, setCreateOpen] = useState(false);
  const [viewReturnId, setViewReturnId] = useState<string | null>(null);
  const [inspectReturnId, setInspectReturnId] = useState<string | null>(null);
  const [postReturnId, setPostReturnId] = useState<string | null>(null);

  // Form State for Create
  const [storeId, setStoreId] = useState('');
  const [returnType, setReturnType] = useState<'EMPLOYEE' | 'DEPARTMENT' | 'PROJECT'>('EMPLOYEE');
  const [returnedByUserId, setReturnedByUserId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [projectId, setProjectId] = useState('');
  const [remarks, setRemarks] = useState('');
  const [formLines, setFormLines] = useState<FormLine[]>([]);

  // Inspect form state
  const [inspectLines, setInspectLines] = useState<ReceiveReturnLineRequest[]>([]);

  // Post form state
  const [postRemarks, setPostRemarks] = useState('');

  // 1. Fetch Returns List
  const { data: returnsData, isLoading } = useQuery({
    queryKey: ['returns', statusFilter, storeFilter, searchTerm],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (statusFilter && statusFilter !== 'ALL') params.append('status', statusFilter);
      if (storeFilter && storeFilter !== 'ALL') params.append('storeId', storeFilter);
      if (searchTerm) params.append('search', searchTerm);
      params.append('size', '50');

      return api.get<PageResponse<ReturnSummaryResponse>>(`/api/store/returns?${params.toString()}`);
    },
  });

  // 2. Fetch Stores
  const { data: storesData } = useQuery({
    queryKey: ['stores-list'],
    queryFn: () => api.get<PageResponse<StoreSiteResponse>>('/api/store/stores?size=100'),
  });

  // 3. Fetch Items
  const { data: itemsData } = useQuery({
    queryKey: ['items-list'],
    queryFn: () => api.get<PageResponse<ItemResponse>>('/api/store/items?size=100'),
  });

  // 4. Fetch Locations for selected store
  const { data: locationsData } = useQuery({
    queryKey: ['locations-for-store', storeId],
    queryFn: () => {
      if (!storeId) return null;
      return api.get<PageResponse<StorageLocationResponse>>(`/api/store/locations?storeId=${storeId}&size=100`);
    },
    enabled: !!storeId,
  });

  // 5. Fetch Assets for selected store (for serial returns)
  const { data: assetsData } = useQuery({
    queryKey: ['assets-list', storeId],
    queryFn: () => api.get<PageResponse<AssetSummaryResponse>>('/api/store/assets?size=100'),
  });

  // 6. Fetch details for Single Return
  const { data: viewReturn, isLoading: isViewLoading } = useQuery({
    queryKey: ['return-detail', viewReturnId],
    queryFn: () => {
      if (!viewReturnId) return null;
      return api.get<ReturnResponse>(`/api/store/returns/${viewReturnId}`);
    },
    enabled: !!viewReturnId,
  });

  const { data: inspectReturn } = useQuery({
    queryKey: ['return-inspect-detail', inspectReturnId],
    queryFn: async () => {
      if (!inspectReturnId) return null;
      const res = await api.get<ReturnResponse>(`/api/store/returns/${inspectReturnId}`);
      if (res && res.items) {
        setInspectLines(
          res.items.map((i) => ({
            lineId: i.id,
            conditionStatus: i.conditionStatus || 'GOOD',
            disposition: i.disposition || 'RESTOCK',
            returnLocationId: i.returnLocationId,
            remarks: i.remarks || '',
          }))
        );
      }
      return res;
    },
    enabled: !!inspectReturnId,
  });

  // 7. Mutations
  const createMutation = useMutation({
    mutationFn: (body: CreateReturnRequest) => api.post<ReturnResponse>('/api/store/returns', body),
    onSuccess: (data) => {
      toast.success(`Return slip ${data.returnNo} created as Draft`);
      queryClient.invalidateQueries({ queryKey: ['returns'] });
      setCreateOpen(false);
      resetCreateForm();
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create return note');
    },
  });

  const submitMutation = useMutation({
    mutationFn: (id: string) => api.post<ReturnResponse>(`/api/store/returns/${id}/submit`, {}),
    onSuccess: (data) => {
      toast.success(`Return ${data.returnNo} submitted for store receipt & inspection`);
      queryClient.invalidateQueries({ queryKey: ['returns'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to submit return');
    },
  });

  const inspectMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: ReceiveReturnRequest }) =>
      api.post<ReturnResponse>(`/api/store/returns/${id}/receive`, body),
    onSuccess: (data) => {
      toast.success(`Inspection completed for return ${data.returnNo}`);
      queryClient.invalidateQueries({ queryKey: ['returns'] });
      setInspectReturnId(null);
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to complete inspection');
    },
  });

  const postMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: PostReturnRequest }) =>
      api.post<ReturnResponse>(`/api/store/returns/${id}/post`, body),
    onSuccess: (data) => {
      toast.success(`Return ${data.returnNo} posted! Inventory balance & custody updated.`);
      queryClient.invalidateQueries({ queryKey: ['returns'] });
      setPostReturnId(null);
      setPostRemarks('');
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to post return to inventory');
    },
  });

  const resetCreateForm = () => {
    setStoreId('');
    setReturnType('EMPLOYEE');
    setReturnedByUserId('');
    setDepartmentId('');
    setProjectId('');
    setRemarks('');
    setFormLines([]);
  };

  const handleAddLine = () => {
    if (!locationsData?.content?.length) {
      toast.error('Please select a Store with configured storage locations first');
      return;
    }
    const defaultItem = itemsData?.content?.[0];
    const defaultLocation = locationsData.content[0];

    setFormLines((prev) => [
      ...prev,
      {
        itemId: defaultItem ? defaultItem.id : '',
        itemCode: defaultItem ? defaultItem.itemCode : '',
        itemName: defaultItem ? defaultItem.itemName : '',
        locationId: defaultLocation.id,
        returnQty: 1,
        conditionStatus: 'GOOD',
        remarks: '',
      },
    ]);
  };

  const handleRemoveLine = (index: number) => {
    setFormLines((prev) => prev.filter((_, i) => i !== index));
  };

  const handleLineChange = (index: number, field: keyof FormLine, value: any) => {
    setFormLines((prev) => {
      const updated = [...prev];
      updated[index] = { ...updated[index], [field]: value };

      if (field === 'itemId') {
        const sel = itemsData?.content?.find((it) => it.id === value);
        if (sel) {
          updated[index].itemCode = sel.itemCode;
          updated[index].itemName = sel.itemName;
        }
      }
      if (field === 'assetId') {
        const selAsset = assetsData?.content?.find((a) => a.id === value);
        if (selAsset) {
          updated[index].assetCode = selAsset.assetCode;
        }
      }
      return updated;
    });
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!storeId) {
      toast.error('Please select a receiving store');
      return;
    }
    if (formLines.length === 0) {
      toast.error('Add at least one return item line');
      return;
    }
    for (const line of formLines) {
      if (!line.itemId || !line.locationId || line.returnQty <= 0) {
        toast.error('Please ensure all lines have valid items, return locations, and quantity > 0');
        return;
      }
    }

    const payload: CreateReturnRequest = {
      storeId,
      returnedByUserId: returnedByUserId ? returnedByUserId : undefined,
      departmentId: departmentId ? departmentId : undefined,
      projectId: projectId ? projectId : undefined,
      remarks: remarks || undefined,
      items: formLines.map((l) => ({
        itemId: l.itemId,
        assetId: l.assetId || undefined,
        returnQty: Number(l.returnQty),
        returnLocationId: l.locationId,
        conditionStatus: l.conditionStatus,
        remarks: l.remarks || undefined,
      })),
    };

    createMutation.mutate(payload);
  };

  const handleInspectSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inspectReturnId) return;
    inspectMutation.mutate({
      id: inspectReturnId,
      body: { lines: inspectLines },
    });
  };

  // Metrics
  const allReturns = returnsData?.content || [];
  const totalCount = returnsData?.totalElements || 0;
  const draftCount = allReturns.filter((r) => r.status === 'DRAFT').length;
  const submittedCount = allReturns.filter((r) => r.status === 'SUBMITTED').length;
  const inspectedCount = allReturns.filter((r) => r.status === 'INSPECTED').length;
  const postedCount = allReturns.filter((r) => r.status === 'POSTED').length;

  const columns: ColumnDef<ReturnSummaryResponse>[] = [
    {
      accessorKey: 'returnNo',
      header: 'Return Number',
      cell: (row) => (
        <div className="flex flex-col">
          <span className="font-mono text-sm font-semibold text-primary">
            {row.returnNo}
          </span>
          <span className="text-xs text-muted-foreground">
            {row.itemCount} item line{row.itemCount !== 1 ? 's' : ''}
          </span>
        </div>
      ),
    },
    {
      accessorKey: 'returnDate',
      header: 'Date',
      cell: (row) => (
        <span className="text-sm">
          {new Date(row.returnDate).toLocaleDateString()}
        </span>
      ),
    },
    {
      accessorKey: 'storeName',
      header: 'Receiving Store',
      cell: (row) => (
        <span className="inline-flex items-center gap-1.5 text-sm">
          <Building2 className="h-4 w-4 text-muted-foreground" />
          {row.storeName}
        </span>
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
              onClick={() => setViewReturnId(row.id)}
              className="h-8 px-2"
              title="View Details"
            >
              <Eye className="h-4 w-4" />
            </Button>
            {row.status === 'DRAFT' && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => submitMutation.mutate(row.id)}
                className="h-8 gap-1 border-primary/40 text-primary hover:bg-primary/10"
                disabled={submitMutation.isPending}
                title="Submit for Inspection"
              >
                <Send className="h-3.5 w-3.5" />
                Submit
              </Button>
            )}
            {(row.status === 'SUBMITTED' || row.status === 'DRAFT') && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setInspectReturnId(row.id)}
                className="h-8 gap-1 border-amber-500/40 text-amber-600 hover:bg-amber-500/10"
                title="Inspect & Assess Condition"
              >
                <ClipboardCheck className="h-3.5 w-3.5" />
                Inspect
              </Button>
            )}
            {(row.status === 'INSPECTED' || row.status === 'SUBMITTED') && (
              <Button
                variant="default"
                size="sm"
                onClick={() => setPostReturnId(row.id)}
                className="h-8 gap-1 bg-emerald-600 hover:bg-emerald-700 text-white"
                title="Post to Inventory"
              >
                <CheckCircle2 className="h-3.5 w-3.5" />
                Post
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <div className="space-y-6 p-6">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <RotateCcw className="h-6 w-6 text-primary" />
            Material & Asset Returns
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Manage material returns from employees, departments, and projects with condition assessment, restock, or repair routing.
          </p>
        </div>
        <Button
          onClick={() => {
            resetCreateForm();
            setCreateOpen(true);
          }}
          className="gap-2 shadow-sm"
        >
          <Plus className="h-4 w-4" />
          Create Return Slip
        </Button>
      </div>

      {/* KPI Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="border border-border/60 shadow-sm bg-card">
          <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
            <CardTitle className="text-sm font-medium text-muted-foreground">Total Returns</CardTitle>
            <Boxes className="h-4 w-4 text-primary" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{totalCount}</div>
            <p className="text-xs text-muted-foreground mt-1">All lifecycle return documents</p>
          </CardContent>
        </Card>

        <Card className="border border-border/60 shadow-sm bg-card">
          <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
            <CardTitle className="text-sm font-medium text-muted-foreground">Drafts & Submitted</CardTitle>
            <Clock className="h-4 w-4 text-amber-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-amber-600">{draftCount + submittedCount}</div>
            <p className="text-xs text-muted-foreground mt-1">{submittedCount} awaiting store inspection</p>
          </CardContent>
        </Card>

        <Card className="border border-border/60 shadow-sm bg-card">
          <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
            <CardTitle className="text-sm font-medium text-muted-foreground">Inspected / Ready</CardTitle>
            <ClipboardCheck className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-blue-600">{inspectedCount}</div>
            <p className="text-xs text-muted-foreground mt-1">Condition assessed, ready to post</p>
          </CardContent>
        </Card>

        <Card className="border border-border/60 shadow-sm bg-card">
          <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
            <CardTitle className="text-sm font-medium text-muted-foreground">Restocked / Posted</CardTitle>
            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-emerald-600">{postedCount}</div>
            <p className="text-xs text-muted-foreground mt-1">Stock balance & custody completed</p>
          </CardContent>
        </Card>
      </div>

      {/* Filter and Search Bar */}
      <Card className="border border-border/60 shadow-sm">
        <CardContent className="p-4 flex flex-col md:flex-row gap-3 items-center justify-between">
          <div className="flex flex-wrap items-center gap-2 w-full md:w-auto">
            {['ALL', 'DRAFT', 'SUBMITTED', 'INSPECTED', 'POSTED'].map((st) => (
              <Button
                key={st}
                variant={statusFilter === st ? 'default' : 'outline'}
                size="sm"
                onClick={() => setStatusFilter(st)}
                className="text-xs h-8"
              >
                {st}
              </Button>
            ))}
          </div>

          <div className="flex items-center gap-3 w-full md:w-auto">
            <Select value={storeFilter} onValueChange={setStoreFilter}>
              <SelectTrigger className="w-[180px] h-8 text-xs">
                <SelectValue placeholder="All Stores" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Stores</SelectItem>
                {storesData?.content?.map((st) => (
                  <SelectItem key={st.id} value={st.id}>
                    {st.storeName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Input
              placeholder="Search return # or remarks..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="h-8 text-xs w-full sm:w-[220px]"
            />
          </div>
        </CardContent>
      </Card>

      {/* Returns Data Table */}
      <div className="rounded-md border bg-card shadow-sm">
        <DataTable
          columns={columns}
          data={allReturns}
          isLoading={isLoading}
        />
      </div>

      {/* Modal 1: Create Return Slip */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-xl font-bold">
              <RotateCcw className="h-5 w-5 text-primary" />
              Create Return Slip
            </DialogTitle>
            <DialogDescription>
              Record returned consumables or assets back into the store.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <Label className="text-xs font-medium">Receiving Store *</Label>
                <Select value={storeId} onValueChange={setStoreId}>
                  <SelectTrigger className="mt-1">
                    <SelectValue placeholder="Select Store" />
                  </SelectTrigger>
                  <SelectContent>
                    {storesData?.content?.map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.storeName} ({s.storeCode})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div>
                <Label className="text-xs font-medium">Returned By Type *</Label>
                <Select
                  value={returnType}
                  onValueChange={(val: any) => setReturnType(val)}
                >
                  <SelectTrigger className="mt-1">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="EMPLOYEE">Employee</SelectItem>
                    <SelectItem value="DEPARTMENT">Department</SelectItem>
                    <SelectItem value="PROJECT">Project</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div>
                <Label className="text-xs font-medium">
                  {returnType === 'EMPLOYEE'
                    ? 'Employee User UUID'
                    : returnType === 'DEPARTMENT'
                    ? 'Department UUID'
                    : 'Project UUID'}
                </Label>
                <Input
                  className="mt-1 text-xs"
                  placeholder="e.g. 11111111-1111-1111-1111-111111111111"
                  value={
                    returnType === 'EMPLOYEE'
                      ? returnedByUserId
                      : returnType === 'DEPARTMENT'
                      ? departmentId
                      : projectId
                  }
                  onChange={(e) => {
                    if (returnType === 'EMPLOYEE') setReturnedByUserId(e.target.value);
                    else if (returnType === 'DEPARTMENT') setDepartmentId(e.target.value);
                    else setProjectId(e.target.value);
                  }}
                />
              </div>
            </div>

            <div>
              <Label className="text-xs font-medium">Return Remarks / Reason</Label>
              <Textarea
                placeholder="Reason for return (employee handover, project closure, excess material)..."
                value={remarks}
                onChange={(e) => setRemarks(e.target.value)}
                className="mt-1 text-xs resize-none"
                rows={2}
              />
            </div>

            {/* Line Items Section */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <Label className="text-sm font-semibold flex items-center gap-1.5">
                  <Layers className="h-4 w-4 text-primary" />
                  Return Line Items ({formLines.length})
                </Label>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleAddLine}
                  disabled={!storeId}
                  className="h-8 gap-1.5 text-xs"
                >
                  <Plus className="h-3.5 w-3.5" />
                  Add Line Item
                </Button>
              </div>

              {formLines.length === 0 ? (
                <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
                  No line items added yet. Click &quot;Add Line Item&quot; to add materials/assets to return.
                </div>
              ) : (
                <div className="space-y-3">
                  {formLines.map((line, idx) => (
                    <div
                      key={idx}
                      className="rounded-lg border p-3 bg-muted/20 grid grid-cols-1 md:grid-cols-12 gap-3 items-center"
                    >
                      <div className="md:col-span-3">
                        <Label className="text-[11px] text-muted-foreground">Item *</Label>
                        <Select
                          value={line.itemId}
                          onValueChange={(val) => handleLineChange(idx, 'itemId', val)}
                        >
                          <SelectTrigger className="h-8 text-xs mt-1">
                            <SelectValue placeholder="Select item" />
                          </SelectTrigger>
                          <SelectContent>
                            {itemsData?.content?.map((it) => (
                              <SelectItem key={it.id} value={it.id}>
                                {it.itemCode} - {it.itemName}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>

                      <div className="md:col-span-3">
                        <Label className="text-[11px] text-muted-foreground">Asset Tag (Optional)</Label>
                        <Select
                          value={line.assetId || 'NONE'}
                          onValueChange={(val) =>
                            handleLineChange(idx, 'assetId', val === 'NONE' ? undefined : val)
                          }
                        >
                          <SelectTrigger className="h-8 text-xs mt-1">
                            <SelectValue placeholder="Non-serialized item" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="NONE">None (Bulk / Consumable)</SelectItem>
                            {assetsData?.content?.map((a) => (
                              <SelectItem key={a.id} value={a.id}>
                                {a.assetCode} ({a.assetStatus})
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>

                      <div className="md:col-span-1">
                        <Label className="text-[11px] text-muted-foreground">Qty *</Label>
                        <Input
                          type="number"
                          min="1"
                          step="1"
                          className="h-8 text-xs mt-1"
                          value={line.returnQty}
                          onChange={(e) =>
                            handleLineChange(idx, 'returnQty', Number(e.target.value))
                          }
                        />
                      </div>

                      <div className="md:col-span-2">
                        <Label className="text-[11px] text-muted-foreground">Location *</Label>
                        <Select
                          value={line.locationId}
                          onValueChange={(val) => handleLineChange(idx, 'locationId', val)}
                        >
                          <SelectTrigger className="h-8 text-xs mt-1">
                            <SelectValue placeholder="Location" />
                          </SelectTrigger>
                          <SelectContent>
                            {locationsData?.content?.map((loc) => (
                              <SelectItem key={loc.id} value={loc.id}>
                                {loc.locationCode}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>

                      <div className="md:col-span-2">
                        <Label className="text-[11px] text-muted-foreground">Condition</Label>
                        <Select
                          value={line.conditionStatus}
                          onValueChange={(val: any) =>
                            handleLineChange(idx, 'conditionStatus', val)
                          }
                        >
                          <SelectTrigger className="h-8 text-xs mt-1">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="GOOD">Good / Working</SelectItem>
                            <SelectItem value="FAIR">Fair</SelectItem>
                            <SelectItem value="DAMAGED">Damaged</SelectItem>
                            <SelectItem value="UNSERVICEABLE">Unserviceable</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>

                      <div className="md:col-span-1 flex justify-end pt-4">
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() => handleRemoveLine(idx)}
                          className="h-8 w-8 p-0 text-destructive hover:bg-destructive/10"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending ? 'Creating...' : 'Create Return Slip'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Modal 2: Inspect & Receive Dialog */}
      <Dialog open={!!inspectReturnId} onOpenChange={(open) => !open && setInspectReturnId(null)}>
        <DialogContent className="max-w-3xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-xl font-bold">
              <ClipboardCheck className="h-5 w-5 text-amber-600" />
              Receipt Inspection & Disposition
            </DialogTitle>
            <DialogDescription>
              Verify physical receipt of returned goods, assess condition, and decide disposition (Restock, Repair, Quarantine, Condemnation).
            </DialogDescription>
          </DialogHeader>

          {inspectReturn && (
            <form onSubmit={handleInspectSubmit} className="space-y-5">
              <div className="p-3 rounded-md bg-muted/40 text-xs flex justify-between">
                <span>
                  <strong>Return #:</strong> {inspectReturn.returnNo}
                </span>
                <span>
                  <strong>Store:</strong> {inspectReturn.storeName}
                </span>
                <span>
                  <strong>Date:</strong> {new Date(inspectReturn.returnDate).toLocaleDateString()}
                </span>
              </div>

              <div className="space-y-3">
                <Label className="text-sm font-semibold">Inspection Lines</Label>
                {inspectReturn.items.map((item, idx) => {
                  const currentInspect = inspectLines.find((l) => l.lineId === item.id);
                  return (
                    <div
                      key={item.id}
                      className="border rounded-lg p-3 space-y-3 bg-card shadow-xs"
                    >
                      <div className="flex justify-between items-center text-xs">
                        <span className="font-semibold text-primary">
                          #{item.lineNo} {item.itemName} ({item.itemCode})
                        </span>
                        <Badge variant="outline">
                          Qty: {item.returnQty}
                          {item.assetCode ? ` | Asset: ${item.assetCode}` : ''}
                        </Badge>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div>
                          <Label className="text-[11px] text-muted-foreground">Assessed Condition *</Label>
                          <Select
                            value={currentInspect?.conditionStatus || 'GOOD'}
                            onValueChange={(val: any) => {
                              setInspectLines((prev) =>
                                prev.map((l) =>
                                  l.lineId === item.id ? { ...l, conditionStatus: val } : l
                                )
                              );
                            }}
                          >
                            <SelectTrigger className="h-8 text-xs mt-1">
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="GOOD">GOOD (Reusable)</SelectItem>
                              <SelectItem value="FAIR">FAIR (Minor wear)</SelectItem>
                              <SelectItem value="DAMAGED">DAMAGED (Requires repair)</SelectItem>
                              <SelectItem value="UNSERVICEABLE">UNSERVICEABLE</SelectItem>
                              <SelectItem value="SCRAP">SCRAP</SelectItem>
                            </SelectContent>
                          </Select>
                        </div>

                        <div>
                          <Label className="text-[11px] text-muted-foreground">Disposition Decision *</Label>
                          <Select
                            value={currentInspect?.disposition || 'RESTOCK'}
                            onValueChange={(val: any) => {
                              setInspectLines((prev) =>
                                prev.map((l) =>
                                  l.lineId === item.id ? { ...l, disposition: val } : l
                                )
                              );
                            }}
                          >
                            <SelectTrigger className="h-8 text-xs mt-1">
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="RESTOCK">RESTOCK (Return to store balance)</SelectItem>
                              <SelectItem value="REPAIR">REPAIR (Route to repair queue)</SelectItem>
                              <SelectItem value="QUARANTINE">QUARANTINE (Hold for inspection)</SelectItem>
                              <SelectItem value="CONDEMNATION">CONDEMNATION (Mark for write-off)</SelectItem>
                              <SelectItem value="SCRAP">SCRAP (Disposal)</SelectItem>
                            </SelectContent>
                          </Select>
                        </div>
                      </div>

                      <div>
                        <Label className="text-[11px] text-muted-foreground">Line Inspection Remarks</Label>
                        <Input
                          placeholder="e.g. Missing power adapter, screen scratch, tested operational..."
                          className="h-8 text-xs mt-1"
                          value={currentInspect?.remarks || ''}
                          onChange={(e) => {
                            const val = e.target.value;
                            setInspectLines((prev) =>
                              prev.map((l) => (l.lineId === item.id ? { ...l, remarks: val } : l))
                            );
                          }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>

              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setInspectReturnId(null)}>
                  Cancel
                </Button>
                <Button
                  type="submit"
                  disabled={inspectMutation.isPending}
                  className="bg-amber-600 hover:bg-amber-700 text-white"
                >
                  {inspectMutation.isPending ? 'Saving...' : 'Save Inspection'}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>

      {/* Modal 3: Post Return to Stock Dialog */}
      <Dialog open={!!postReturnId} onOpenChange={(open) => !open && setPostReturnId(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-xl font-bold">
              <CheckCircle2 className="h-5 w-5 text-emerald-600" />
              Post Return to Inventory
            </DialogTitle>
            <DialogDescription>
              This will update on-hand stock balances, record RETURN stock ledger transactions, and release active asset assignments.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-xs space-y-1 text-emerald-800 dark:text-emerald-300">
              <p className="font-semibold flex items-center gap-1.5">
                <CheckCircle2 className="h-4 w-4" /> Double-Entry Movement:
              </p>
              <ul className="list-disc list-inside space-y-0.5 ml-1">
                <li>Restocked lines: increment stock balance in target location</li>
                <li>Assets: release custody, return status to AVAILABLE</li>
                <li>Repairs / Scrap: route to corresponding maintenance queues</li>
              </ul>
            </div>

            <div>
              <Label className="text-xs font-medium">Posting Remarks</Label>
              <Textarea
                placeholder="Audit notes or store keeper sign-off..."
                value={postRemarks}
                onChange={(e) => setPostRemarks(e.target.value)}
                className="mt-1 text-xs resize-none"
                rows={2}
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setPostReturnId(null)}>
              Cancel
            </Button>
            <Button
              onClick={() => {
                if (postReturnId) {
                  postMutation.mutate({
                    id: postReturnId,
                    body: { remarks: postRemarks },
                  });
                }
              }}
              disabled={postMutation.isPending}
              className="bg-emerald-600 hover:bg-emerald-700 text-white"
            >
              {postMutation.isPending ? 'Posting...' : 'Confirm & Post Return'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Modal 4: View Details Dialog */}
      <Dialog open={!!viewReturnId} onOpenChange={(open) => !open && setViewReturnId(null)}>
        <DialogContent className="max-w-3xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-xl font-bold">
              <Eye className="h-5 w-5 text-primary" />
              Return Slip Details
            </DialogTitle>
          </DialogHeader>

          {isViewLoading || !viewReturn ? (
            <div className="py-12 text-center text-sm text-muted-foreground">Loading details...</div>
          ) : (
            <div className="space-y-6">
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 p-4 rounded-lg bg-muted/30 border">
                <div>
                  <span className="text-xs text-muted-foreground block">Return Number</span>
                  <span className="font-mono text-sm font-semibold">{viewReturn.returnNo}</span>
                </div>
                <div>
                  <span className="text-xs text-muted-foreground block">Date</span>
                  <span className="text-sm font-medium">
                    {new Date(viewReturn.returnDate).toLocaleDateString()}
                  </span>
                </div>
                <div>
                  <span className="text-xs text-muted-foreground block">Store</span>
                  <span className="text-sm font-medium">{viewReturn.storeName}</span>
                </div>
                <div>
                  <span className="text-xs text-muted-foreground block">Status</span>
                  <StatusBadge status={viewReturn.status} />
                </div>
              </div>

              {viewReturn.remarks && (
                <div className="text-xs p-3 rounded bg-muted/20 border">
                  <strong>Remarks:</strong> {viewReturn.remarks}
                </div>
              )}

              <div>
                <h4 className="text-sm font-semibold mb-3 flex items-center gap-2">
                  <Layers className="h-4 w-4 text-primary" />
                  Item Lines ({viewReturn.items?.length || 0})
                </h4>

                <div className="border rounded-md overflow-hidden">
                  <table className="w-full text-xs">
                    <thead className="bg-muted/50 border-b">
                      <tr>
                        <th className="py-2 px-3 text-left">#</th>
                        <th className="py-2 px-3 text-left">Item Code & Name</th>
                        <th className="py-2 px-3 text-left">Asset</th>
                        <th className="py-2 px-3 text-right">Qty</th>
                        <th className="py-2 px-3 text-left">Location</th>
                        <th className="py-2 px-3 text-left">Condition</th>
                        <th className="py-2 px-3 text-left">Disposition</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {viewReturn.items?.map((item) => (
                        <tr key={item.id} className="hover:bg-muted/20">
                          <td className="py-2 px-3 font-mono">{item.lineNo}</td>
                          <td className="py-2 px-3">
                            <div className="font-medium">{item.itemName}</div>
                            <div className="text-[11px] text-muted-foreground">{item.itemCode}</div>
                          </td>
                          <td className="py-2 px-3">
                            {item.assetCode ? (
                              <Badge variant="outline" className="font-mono text-[10px]">
                                {item.assetCode}
                              </Badge>
                            ) : (
                              <span className="text-muted-foreground">—</span>
                            )}
                          </td>
                          <td className="py-2 px-3 text-right font-semibold">{item.returnQty}</td>
                          <td className="py-2 px-3">
                            <Badge variant="secondary" className="text-[10px]">
                              {item.locationCode}
                            </Badge>
                          </td>
                          <td className="py-2 px-3">
                            <Badge
                              variant="outline"
                              className={
                                item.conditionStatus === 'GOOD'
                                  ? 'border-emerald-500/50 text-emerald-600'
                                  : item.conditionStatus === 'DAMAGED'
                                  ? 'border-amber-500/50 text-amber-600'
                                  : ''
                              }
                            >
                              {item.conditionStatus || 'GOOD'}
                            </Badge>
                          </td>
                          <td className="py-2 px-3">
                            {item.disposition ? (
                              <Badge
                                className={
                                  item.disposition === 'RESTOCK'
                                    ? 'bg-emerald-600 text-white'
                                    : item.disposition === 'REPAIR'
                                    ? 'bg-amber-600 text-white'
                                    : 'bg-slate-600 text-white'
                                }
                              >
                                {item.disposition}
                              </Badge>
                            ) : (
                              <span className="text-muted-foreground text-[11px]">Pending Inspection</span>
                            )}
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
            <Button variant="outline" onClick={() => setViewReturnId(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
