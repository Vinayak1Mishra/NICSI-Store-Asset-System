'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { useRouter } from 'next/navigation';
import {
  GrnResponse,
  GrnSummaryResponse,
  CreateGrnRequest,
  CreateGrnItemRequest,
  GrnPostRequest,
  GrnPostResponse,
  LineSerialRequest,
} from '@/types/grn';
import { useAuth } from '@/hooks/use-auth';
import {
  PurchaseOrderResponse,
  PurchaseOrderSummaryResponse,
} from '@/types/purchase-order';
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
  FileText,
  Plus,
  Send,
  Eye,
  Trash2,
  PackageCheck,
  Building2,
  Calendar,
  Layers,
  MapPin,
  ClipboardCheck,
  Hash,
  FileCheck,
  ShieldAlert,
  CheckCircle2,
} from 'lucide-react';
import { toast } from 'sonner';

interface FormLine {
  poItemRefId?: string | null;
  itemId: string;
  itemCode: string;
  itemName: string;
  uomCode?: string;
  receivedQty: number;
  unitRate: number;
  receivingLocationId: string;
  receivingLocationName: string;
  batchLotNo: string;
  manufactureDate: string;
  expiryDate: string;
  remarks: string;
}

export default function GrnPage() {
  const queryClient = useQueryClient();
  const router = useRouter();
  const { user, hasPermission } = useAuth();
  const canPost = hasPermission('GRN_POST') || hasPermission('STORE_ADMIN');

  const [search, setSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('ALL');
  const [filterStore, setFilterStore] = useState<string>('ALL');
  const [page, setPage] = useState(0);

  // Create GRN State
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [grnDate, setGrnDate] = useState(new Date().toISOString().split('T')[0]);
  const [selectedStoreId, setSelectedStoreId] = useState('');
  const [selectedPoId, setSelectedPoId] = useState('');
  const [vendorName, setVendorName] = useState('');
  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [invoiceDate, setInvoiceDate] = useState('');
  const [challanNumber, setChallanNumber] = useState('');
  const [challanDate, setChallanDate] = useState('');
  const [remarks, setRemarks] = useState('');
  const [lines, setLines] = useState<FormLine[]>([]);

  // Selected Line Item Inputs
  const [selectedItemId, setSelectedItemId] = useState('');
  const [lineLocationId, setLineLocationId] = useState('');
  const [lineQty, setLineQty] = useState<number | ''>('');
  const [lineRate, setLineRate] = useState<number | ''>('');
  const [lineBatch, setLineBatch] = useState('');
  const [lineMfgDate, setLineMfgDate] = useState('');
  const [lineExpDate, setLineExpDate] = useState('');
  const [lineRemarks, setLineRemarks] = useState('');

  // View / Detail Modal State
  const [selectedGrn, setSelectedGrn] = useState<GrnResponse | null>(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);

  // Post to Stock Modal State
  const [isPostOpen, setIsPostOpen] = useState(false);
  const [postTargetGrn, setPostTargetGrn] = useState<GrnResponse | null>(null);
  const [postRemarks, setPostRemarks] = useState('Goods posted to inventory stock');
  const [postSessionKey, setPostSessionKey] = useState('');
  const [lineSerialsInput, setLineSerialsInput] = useState<Record<string, string[]>>({});
  const [postResult, setPostResult] = useState<GrnPostResponse | null>(null);
  const [isPostResultOpen, setIsPostResultOpen] = useState(false);

  // Fetch GRN list
  const { data: grnData, isLoading } = useQuery({
    queryKey: ['grns', page, search, filterStatus, filterStore],
    queryFn: () => {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', '15');
      if (search) params.append('search', search);
      if (filterStatus !== 'ALL') params.append('status', filterStatus);
      if (filterStore !== 'ALL') params.append('storeId', filterStore);
      return api.get<PageResponse<GrnSummaryResponse>>(`/api/store/grns?${params.toString()}`);
    },
  });

  // Fetch Store Sites
  const { data: storesData } = useQuery({
    queryKey: ['stores-selector'],
    queryFn: () => api.get<PageResponse<StoreSiteResponse>>('/api/store/stores?size=50&active=true'),
  });

  // Fetch Locations for chosen store
  const { data: locationsData } = useQuery({
    queryKey: ['locations-for-store', selectedStoreId],
    queryFn: () =>
      selectedStoreId
        ? api.get<PageResponse<StorageLocationResponse>>(`/api/store/locations?storeId=${selectedStoreId}&size=100&active=true`)
        : Promise.resolve({ content: [] } as unknown as PageResponse<StorageLocationResponse>),
    enabled: !!selectedStoreId,
  });

  // Fetch Open POs for lookup
  const { data: openPosData } = useQuery({
    queryKey: ['open-pos-selector'],
    queryFn: () => api.get<PageResponse<PurchaseOrderSummaryResponse>>('/api/store/purchase-orders?size=50&status=OPEN'),
  });

  // Fetch Items master
  const { data: itemsData } = useQuery({
    queryKey: ['items-selector'],
    queryFn: () => api.get<PageResponse<ItemResponse>>('/api/store/items?size=100&active=true'),
  });

  // Load PO details when a PO is selected
  const handleSelectPo = async (poId: string) => {
    setSelectedPoId(poId);
    if (!poId || poId === 'NONE') {
      return;
    }
    try {
      const po = await api.get<PurchaseOrderResponse>(`/api/store/purchase-orders/${poId}`);
      if (po.vendorNameSnapshot) setVendorName(po.vendorNameSnapshot);

      // Pre-populate lines from PO with remaining quantity > 0
      if (po.items && po.items.length > 0) {
        const poLines: FormLine[] = po.items
          .filter((item) => item.remainingQty > 0)
          .map((item) => ({
            poItemRefId: item.id,
            itemId: item.itemId || '',
            itemCode: item.itemCode || 'ITEM',
            itemName: item.itemName || item.itemDescription || 'Item',
            receivedQty: item.remainingQty,
            unitRate: item.unitRate,
            receivingLocationId: '',
            receivingLocationName: '',
            batchLotNo: '',
            manufactureDate: '',
            expiryDate: '',
            remarks: '',
          }));
        setLines(poLines);
      }
    } catch {
      toast.error('Failed to load PO lines');
    }
  };

  // Create Mutation
  const createMutation = useMutation({
    mutationFn: (data: CreateGrnRequest) => api.post<GrnResponse>('/api/store/grns', data),
    onSuccess: (data) => {
      toast.success(`GRN created as draft: ${data.grnNo}`);
      queryClient.invalidateQueries({ queryKey: ['grns'] });
      setIsCreateOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create GRN');
    },
  });

  // Submit Mutation
  const submitMutation = useMutation({
    mutationFn: (id: string) => api.post<GrnResponse>(`/api/store/grns/${id}/submit`),
    onSuccess: (data) => {
      toast.success(`GRN ${data.grnNo} submitted for Inspection!`);
      queryClient.invalidateQueries({ queryKey: ['grns'] });
      if (selectedGrn?.id === data.id) {
        setSelectedGrn(data);
      }
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to submit GRN');
    },
  });

  const openPostDialog = async (grnId: string) => {
    try {
      const grn = await api.get<GrnResponse>(`/api/store/grns/${grnId}`);
      setPostTargetGrn(grn);
      setPostRemarks('Goods posted to inventory stock');
      setPostSessionKey(crypto.randomUUID());

      const initialSerials: Record<string, string[]> = {};
      grn.items.forEach((item) => {
        if (item.acceptedQty > 0) {
          initialSerials[item.id] = Array.from({ length: Math.min(item.acceptedQty, 20) }, () => '');
        }
      });
      setLineSerialsInput(initialSerials);
      setIsPostOpen(true);
    } catch {
      toast.error('Failed to load GRN details for posting');
    }
  };

  const postMutation = useMutation({
    mutationFn: async ({ id, payload, idempotencyKey }: { id: string; payload: GrnPostRequest; idempotencyKey: string }) => {
      return api.post<GrnPostResponse>(`/api/store/grns/${id}/post`, payload, {
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
      });
    },
    onSuccess: (data) => {
      toast.success(`GRN ${data.grnNo} posted to stock successfully! (${data.totalAssetsCreated} assets created)`);
      setIsPostOpen(false);
      setPostResult(data);
      setIsPostResultOpen(true);
      queryClient.invalidateQueries({ queryKey: ['grns'] });
      if (selectedGrn?.id === data.grnId) {
        viewGrnDetail(data.grnId);
      }
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        if (err.code === 'MAKER_CHECKER_VIOLATION') {
          toast.error('Maker-Checker Violation: The officer who received this GRN cannot post it to stock.', {
            icon: <ShieldAlert className="size-5 text-rose-500" />,
          });
          return;
        }
        if (err.code === 'ACCESS_DENIED' || err.status === 403) {
          toast.error('Access Denied: Your role lacks permission (GRN_POST required).');
          return;
        }
        toast.error(`${err.message} (${err.code})`);
      } else {
        toast.error('Failed to post GRN to inventory');
      }
    },
  });

  const resetForm = () => {
    setGrnDate(new Date().toISOString().split('T')[0]);
    setSelectedStoreId('');
    setSelectedPoId('');
    setVendorName('');
    setInvoiceNumber('');
    setInvoiceDate('');
    setChallanNumber('');
    setChallanDate('');
    setRemarks('');
    setLines([]);
    setSelectedItemId('');
    setLineLocationId('');
    setLineQty('');
    setLineRate('');
    setLineBatch('');
    setLineMfgDate('');
    setLineExpDate('');
    setLineRemarks('');
  };

  const handleAddLine = () => {
    if (!selectedItemId || !lineLocationId || !lineQty || Number(lineQty) <= 0) {
      toast.error('Please select an item, location, and valid received quantity');
      return;
    }

    const itemObj = itemsData?.content.find((i) => i.id === selectedItemId);
    const locObj = locationsData?.content.find((l) => l.id === lineLocationId);
    if (!itemObj || !locObj) return;

    const newLine: FormLine = {
      itemId: itemObj.id,
      itemCode: itemObj.itemCode,
      itemName: itemObj.itemName,
      uomCode: itemObj.uomCode,
      receivedQty: Number(lineQty),
      unitRate: lineRate ? Number(lineRate) : (itemObj.standardRate || 0),
      receivingLocationId: locObj.id,
      receivingLocationName: `${locObj.locationCode} (${locObj.locationName})`,
      batchLotNo: lineBatch,
      manufactureDate: lineMfgDate,
      expiryDate: lineExpDate,
      remarks: lineRemarks,
    };

    setLines([...lines, newLine]);
    setSelectedItemId('');
    setLineLocationId('');
    setLineQty('');
    setLineRate('');
    setLineBatch('');
    setLineMfgDate('');
    setLineExpDate('');
    setLineRemarks('');
  };

  const handleRemoveLine = (index: number) => {
    setLines(lines.filter((_, i) => i !== index));
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedStoreId) {
      toast.error('Store Site is required');
      return;
    }
    if (lines.length === 0) {
      toast.error('Add at least one item line');
      return;
    }

    // Check that all lines have a receiving location
    const missingLoc = lines.some((l) => !l.receivingLocationId);
    if (missingLoc) {
      toast.error('All line items must have an assigned receiving storage location');
      return;
    }

    const payload: CreateGrnRequest = {
      grnDate,
      storeId: selectedStoreId,
      poRefId: selectedPoId && selectedPoId !== 'NONE' ? selectedPoId : undefined,
      vendorNameSnapshot: vendorName.trim() || undefined,
      invoiceNumber: invoiceNumber.trim() || undefined,
      invoiceDate: invoiceDate || undefined,
      challanNumber: challanNumber.trim() || undefined,
      challanDate: challanDate || undefined,
      remarks: remarks.trim() || undefined,
      items: lines.map((l) => ({
        poItemRefId: l.poItemRefId || undefined,
        itemId: l.itemId,
        receivedQty: l.receivedQty,
        unitRate: l.unitRate,
        receivingLocationId: l.receivingLocationId,
        batchLotNo: l.batchLotNo || undefined,
        manufactureDate: l.manufactureDate || undefined,
        expiryDate: l.expiryDate || undefined,
        remarks: l.remarks || undefined,
      })),
    };

    createMutation.mutate(payload);
  };

  const viewGrnDetail = async (id: string) => {
    try {
      const detail = await api.get<GrnResponse>(`/api/store/grns/${id}`);
      setSelectedGrn(detail);
      setIsDetailOpen(true);
    } catch (err: unknown) {
      const e = err as ApiError;
      toast.error(e.message || 'Failed to load GRN details');
    }
  };

  const columns: ColumnDef<GrnSummaryResponse>[] = [
    {
      header: 'GRN Number',
      cell: (row) => (
        <div>
          <div className="font-semibold text-primary flex items-center gap-1.5 font-mono">
            <PackageCheck className="w-4 h-4 text-muted-foreground" />
            {row.grnNo}
          </div>
          <div className="text-xs text-muted-foreground">
            {row.grnDate || new Date(row.createdAt).toLocaleDateString()}
          </div>
        </div>
      ),
    },
    {
      header: 'Store Site',
      cell: (row) => (
        <span className="font-medium text-foreground flex items-center gap-1">
          <Building2 className="w-3.5 h-3.5 text-muted-foreground" />
          {row.storeName}
        </span>
      ),
    },
    {
      header: 'PO Reference',
      cell: (row) => (
        row.poNumber ? (
          <Badge variant="outline" className="font-mono text-xs text-primary bg-primary/5">
            {row.poNumber}
          </Badge>
        ) : (
          <span className="text-xs text-muted-foreground">Direct Receipt</span>
        )
      ),
    },
    {
      header: 'Vendor / Delivery Challan',
      cell: (row) => (
        <div>
          <div className="font-medium text-foreground">{row.vendorNameSnapshot || 'Not Specified'}</div>
          <div className="text-xs text-muted-foreground">
            {row.challanNumber ? `Challan: ${row.challanNumber}` : ''}
            {row.invoiceNumber ? ` | Inv: ${row.invoiceNumber}` : ''}
          </div>
        </div>
      ),
    },
    {
      header: 'Received Qty',
      cell: (row) => (
        <Badge variant="secondary" className="font-mono text-xs">
          {row.totalReceivedQty} ({row.itemCount} line{row.itemCount !== 1 ? 's' : ''})
        </Badge>
      ),
    },
    {
      header: 'Status',
      cell: (row) => <StatusBadge status={row.status} />,
    },
    {
      header: 'Actions',
      cell: (row) => (
        <div className="flex items-center gap-1.5">
          <Button
            variant="outline"
            size="sm"
            onClick={() => viewGrnDetail(row.id)}
            className="h-8 gap-1"
          >
            <Eye className="w-3.5 h-3.5" />
            View
          </Button>
          {row.status === 'DRAFT' && (
            <Button
              size="sm"
              onClick={() => submitMutation.mutate(row.id)}
              disabled={submitMutation.isPending}
              className="h-8 gap-1 bg-emerald-600 hover:bg-emerald-700 text-white"
            >
              <Send className="w-3.5 h-3.5" />
              Submit
            </Button>
          )}
          {row.status === 'UNDER_INSPECTION' && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => router.push('/inspection')}
              className="h-8 gap-1 border-amber-500 text-amber-600 hover:bg-amber-50"
            >
              <ClipboardCheck className="w-3.5 h-3.5" />
              Inspect
            </Button>
          )}
          {(row.status === 'ACCEPTED' || row.status === 'PARTIALLY_ACCEPTED') && canPost && (
            <Button
              size="sm"
              onClick={() => openPostDialog(row.id)}
              className="h-8 gap-1 bg-indigo-600 hover:bg-indigo-700 text-white"
            >
              <FileCheck className="w-3.5 h-3.5" />
              Post Stock
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <PackageCheck className="w-6 h-6 text-primary" />
            Goods Receipt Notes (GRN)
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Inward gate entry, delivery challan verification, and technical inspection initiation.
          </p>
        </div>
        <Button onClick={() => setIsCreateOpen(true)} className="gap-2">
          <Plus className="w-4 h-4" />
          Create GRN
        </Button>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="p-4 flex flex-col sm:flex-row gap-3">
          <div className="flex-1">
            <Input
              placeholder="Search by GRN No, Vendor, Challan, Invoice..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </div>
          <div className="w-full sm:w-48">
            <Select
              value={filterStore}
              onValueChange={(val) => {
                setFilterStore(val);
                setPage(0);
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder="Store" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Stores</SelectItem>
                {storesData?.content.map((st) => (
                  <SelectItem key={st.id} value={st.id}>
                    {st.storeName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="w-full sm:w-48">
            <Select
              value={filterStatus}
              onValueChange={(val) => {
                setFilterStatus(val);
                setPage(0);
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Statuses</SelectItem>
                <SelectItem value="DRAFT">Draft</SelectItem>
                <SelectItem value="UNDER_INSPECTION">Under Inspection</SelectItem>
                <SelectItem value="ACCEPTED">Accepted</SelectItem>
                <SelectItem value="PARTIALLY_ACCEPTED">Partially Accepted</SelectItem>
                <SelectItem value="REJECTED">Rejected</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <DataTable
        columns={columns}
        data={grnData?.content || []}
        isLoading={isLoading}
        page={page}
        totalPages={grnData?.totalPages || 0}
        totalElements={grnData?.totalElements || 0}
        onPageChange={setPage}
      />

      {/* Create GRN Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-xl">
              <PackageCheck className="w-5 h-5 text-primary" />
              Create Goods Receipt Note (GRN)
            </DialogTitle>
            <DialogDescription>
              Record physical receipt of consignments against Purchase Orders or direct deliveries.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label htmlFor="storeSelect">Target Store *</Label>
                <Select value={selectedStoreId} onValueChange={setSelectedStoreId}>
                  <SelectTrigger id="storeSelect">
                    <SelectValue placeholder="Select target warehouse" />
                  </SelectTrigger>
                  <SelectContent>
                    {storesData?.content.map((st) => (
                      <SelectItem key={st.id} value={st.id}>
                        {st.storeName} ({st.storeCode})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="poSelect">PO Reference (Optional)</Label>
                <Select value={selectedPoId} onValueChange={handleSelectPo}>
                  <SelectTrigger id="poSelect">
                    <SelectValue placeholder="Link purchase order" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="NONE">-- Direct Receipt (No PO) --</SelectItem>
                    {openPosData?.content.map((po) => (
                      <SelectItem key={po.id} value={po.id}>
                        {po.poNumber} ({po.vendorNameSnapshot || 'Vendor'})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="grnDate">Receipt Date *</Label>
                <Input
                  id="grnDate"
                  type="date"
                  value={grnDate}
                  onChange={(e) => setGrnDate(e.target.value)}
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="vendorName">Vendor Name</Label>
                <Input
                  id="vendorName"
                  placeholder="e.g. Dell India Pvt Ltd"
                  value={vendorName}
                  onChange={(e) => setVendorName(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="challanNumber">Challan Number</Label>
                <Input
                  id="challanNumber"
                  placeholder="e.g. DEL-2026-99"
                  value={challanNumber}
                  onChange={(e) => setChallanNumber(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="invoiceNumber">Invoice Number</Label>
                <Input
                  id="invoiceNumber"
                  placeholder="e.g. INV/2026/0411"
                  value={invoiceNumber}
                  onChange={(e) => setInvoiceNumber(e.target.value)}
                />
              </div>
            </div>

            {/* Line Items Builder Card */}
            <Card className="border-dashed">
              <CardHeader className="pb-3">
                <CardTitle className="text-sm font-semibold flex items-center justify-between">
                  <span className="flex items-center gap-1.5">
                    <Layers className="w-4 h-4 text-primary" />
                    Consignment Received Items ({lines.length})
                  </span>
                  {!selectedStoreId && (
                    <span className="text-xs text-destructive">
                      * Select a target store above to assign locations
                    </span>
                  )}
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {/* Inputs for manual line addition */}
                <div className="grid grid-cols-1 sm:grid-cols-6 gap-3 p-3 bg-muted/40 rounded-lg">
                  <div className="sm:col-span-2 space-y-1">
                    <Label className="text-xs">Catalog Item *</Label>
                    <Select value={selectedItemId} onValueChange={setSelectedItemId}>
                      <SelectTrigger className="h-9 text-xs">
                        <SelectValue placeholder="Choose item" />
                      </SelectTrigger>
                      <SelectContent>
                        {itemsData?.content.map((item) => (
                          <SelectItem key={item.id} value={item.id} className="text-xs">
                            {item.itemCode} - {item.itemName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="sm:col-span-2 space-y-1">
                    <Label className="text-xs">Receiving Location *</Label>
                    <Select
                      value={lineLocationId}
                      onValueChange={setLineLocationId}
                      disabled={!selectedStoreId}
                    >
                      <SelectTrigger className="h-9 text-xs">
                        <SelectValue placeholder={selectedStoreId ? 'Select storage rack/bin' : 'Pick store first'} />
                      </SelectTrigger>
                      <SelectContent>
                        {locationsData?.content.map((loc) => (
                          <SelectItem key={loc.id} value={loc.id} className="text-xs">
                            {loc.locationCode} - {loc.locationName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="space-y-1">
                    <Label className="text-xs">Received Qty *</Label>
                    <Input
                      type="number"
                      placeholder="Qty"
                      className="h-9 text-xs font-mono"
                      value={lineQty}
                      onChange={(e) => setLineQty(e.target.value ? Number(e.target.value) : '')}
                    />
                  </div>

                  <div className="flex items-end">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={handleAddLine}
                      className="w-full h-9 text-xs gap-1"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      Add Item
                    </Button>
                  </div>
                </div>

                {/* Lines Table */}
                {lines.length > 0 && (
                  <div className="border rounded-md overflow-hidden">
                    <table className="w-full text-xs">
                      <thead className="bg-muted text-muted-foreground text-left">
                        <tr>
                          <th className="p-2">#</th>
                          <th className="p-2">Item</th>
                          <th className="p-2 font-mono text-right">Received Qty</th>
                          <th className="p-2">Receiving Location</th>
                          <th className="p-2">Batch / Lot</th>
                          <th className="p-2 text-center">Action</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {lines.map((line, idx) => (
                          <tr key={idx} className="hover:bg-muted/30">
                            <td className="p-2 font-mono text-muted-foreground">{idx + 1}</td>
                            <td className="p-2">
                              <span className="font-semibold">{line.itemCode}</span>
                              <span className="text-muted-foreground ml-1">({line.itemName})</span>
                            </td>
                            <td className="p-2 font-mono text-right font-medium text-primary">
                              {line.receivedQty}
                            </td>
                            <td className="p-2">
                              {line.receivingLocationName || (
                                <Select
                                  value={line.receivingLocationId}
                                  onValueChange={(val) => {
                                    const locObj = locationsData?.content.find((l) => l.id === val);
                                    const updated = [...lines];
                                    updated[idx].receivingLocationId = val;
                                    updated[idx].receivingLocationName = locObj ? `${locObj.locationCode} (${locObj.locationName})` : '';
                                    setLines(updated);
                                  }}
                                >
                                  <SelectTrigger className="h-7 text-xs w-48">
                                    <SelectValue placeholder="Assign location *" />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {locationsData?.content.map((loc) => (
                                      <SelectItem key={loc.id} value={loc.id} className="text-xs">
                                        {loc.locationCode} - {loc.locationName}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                              )}
                            </td>
                            <td className="p-2 font-mono text-muted-foreground">
                              {line.batchLotNo || '-'}
                            </td>
                            <td className="p-2 text-center">
                              <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                onClick={() => handleRemoveLine(idx)}
                                className="h-6 w-6 p-0 text-destructive hover:bg-destructive/10"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </Button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </CardContent>
            </Card>

            <div className="space-y-2">
              <Label htmlFor="remarks">Inward Gate Remarks</Label>
              <Textarea
                id="remarks"
                placeholder="Package seal condition, transporter details, vehicle number..."
                rows={2}
                value={remarks}
                onChange={(e) => setRemarks(e.target.value)}
              />
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending || lines.length === 0}>
                {createMutation.isPending ? 'Saving...' : 'Save Draft GRN'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* GRN Detail Dialog */}
      <Dialog open={isDetailOpen} onOpenChange={setIsDetailOpen}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          {selectedGrn && (
            <div className="space-y-6">
              <DialogHeader>
                <div className="flex items-center justify-between">
                  <DialogTitle className="text-xl font-bold flex items-center gap-2">
                    <PackageCheck className="w-5 h-5 text-primary" />
                    {selectedGrn.grnNo}
                  </DialogTitle>
                  <StatusBadge status={selectedGrn.status} />
                </div>
                <DialogDescription>
                  Inward gate receipt details and inspection status.
                </DialogDescription>
              </DialogHeader>

              {/* Summary Cards */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div className="p-3 bg-muted/30 rounded-lg">
                  <div className="text-xs text-muted-foreground flex items-center gap-1">
                    <Building2 className="w-3.5 h-3.5" /> Store Site
                  </div>
                  <div className="text-sm font-semibold truncate mt-1">
                    {selectedGrn.storeName}
                  </div>
                </div>
                <div className="p-3 bg-muted/30 rounded-lg">
                  <div className="text-xs text-muted-foreground flex items-center gap-1">
                    <Building2 className="w-3.5 h-3.5" /> Vendor
                  </div>
                  <div className="text-sm font-semibold truncate mt-1">
                    {selectedGrn.vendorNameSnapshot || 'N/A'}
                  </div>
                </div>
                <div className="p-3 bg-muted/30 rounded-lg">
                  <div className="text-xs text-muted-foreground flex items-center gap-1">
                    <Calendar className="w-3.5 h-3.5" /> Receipt Date
                  </div>
                  <div className="text-sm font-semibold mt-1">
                    {selectedGrn.grnDate}
                  </div>
                </div>
                <div className="p-3 bg-muted/30 rounded-lg">
                  <div className="text-xs text-muted-foreground flex items-center gap-1">
                    <Hash className="w-3.5 h-3.5" /> PO Reference
                  </div>
                  <div className="text-sm font-semibold font-mono text-primary mt-1">
                    {selectedGrn.poNumber || 'Direct'}
                  </div>
                </div>
              </div>

              {/* Items Table */}
              <div className="border rounded-lg overflow-hidden">
                <table className="w-full text-xs">
                  <thead className="bg-muted text-muted-foreground text-left">
                    <tr>
                      <th className="p-2.5">Line</th>
                      <th className="p-2.5">Item</th>
                      <th className="p-2.5 font-mono text-right">Received</th>
                      <th className="p-2.5 font-mono text-right">Accepted</th>
                      <th className="p-2.5 font-mono text-right">Rejected</th>
                      <th className="p-2.5">Location</th>
                      <th className="p-2.5">Batch / Lot</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {selectedGrn.items.map((line) => (
                      <tr key={line.id} className="hover:bg-muted/20">
                        <td className="p-2.5 font-mono text-muted-foreground">{line.lineNo}</td>
                        <td className="p-2.5">
                          <div className="font-semibold text-foreground">{line.itemCode}</div>
                          <div className="text-muted-foreground text-[11px]">{line.itemName}</div>
                        </td>
                        <td className="p-2.5 font-mono text-right font-medium">{line.receivedQty}</td>
                        <td className="p-2.5 font-mono text-right font-medium text-emerald-600">
                          {line.acceptedQty}
                        </td>
                        <td className="p-2.5 font-mono text-right font-medium text-destructive">
                          {line.rejectedQty}
                        </td>
                        <td className="p-2.5">
                          <span className="flex items-center gap-1">
                            <MapPin className="w-3 h-3 text-muted-foreground" />
                            {line.receivingLocationCode}
                          </span>
                        </td>
                        <td className="p-2.5 font-mono text-muted-foreground">{line.batchLotNo || '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <DialogFooter className="flex items-center justify-between sm:justify-between w-full">
                <div>
                  {selectedGrn.status === 'DRAFT' && (
                    <Button
                      onClick={() => submitMutation.mutate(selectedGrn.id)}
                      disabled={submitMutation.isPending}
                      className="gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white"
                    >
                      <Send className="w-4 h-4" />
                      Submit for Inspection
                    </Button>
                  )}
                  {selectedGrn.status === 'UNDER_INSPECTION' && (
                    <Button
                      onClick={() => router.push('/inspection')}
                      className="gap-1.5 border border-amber-500 bg-amber-50 text-amber-700 hover:bg-amber-100"
                    >
                      <ClipboardCheck className="w-4 h-4" />
                      Go to Technical Inspection
                    </Button>
                  )}
                  {(selectedGrn.status === 'ACCEPTED' || selectedGrn.status === 'PARTIALLY_ACCEPTED') && canPost && (
                    <Button
                      onClick={() => {
                        setIsDetailOpen(false);
                        openPostDialog(selectedGrn.id);
                      }}
                      className="gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white"
                    >
                      <FileCheck className="w-4 h-4" />
                      Post to Inventory
                    </Button>
                  )}
                </div>
                <Button variant="outline" onClick={() => setIsDetailOpen(false)}>
                  Close
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* Post to Inventory Dialog */}
      <Dialog open={isPostOpen} onOpenChange={setIsPostOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <FileCheck className="w-5 h-5 text-indigo-600" />
              Post GRN to Inventory: {postTargetGrn?.grnNo}
            </DialogTitle>
            <DialogDescription>
              Commit accepted items to stock balances and create serialised assets.
            </DialogDescription>
          </DialogHeader>

          {postTargetGrn && (
            <div className="space-y-4">
              <div className="bg-slate-50 p-3 rounded-md border text-xs grid grid-cols-2 sm:grid-cols-4 gap-2">
                <div><span className="text-slate-500">Store:</span> <span className="font-semibold">{postTargetGrn.storeName}</span></div>
                <div><span className="text-slate-500">Vendor:</span> <span className="font-semibold">{postTargetGrn.vendorNameSnapshot || '-'}</span></div>
                <div><span className="text-slate-500">Challan:</span> <span className="font-mono">{postTargetGrn.challanNumber || '-'}</span></div>
                <div><span className="text-slate-500">Invoice:</span> <span className="font-mono">{postTargetGrn.invoiceNumber || '-'}</span></div>
              </div>

              {/* Line items and serial entry */}
              <div className="space-y-3">
                <Label className="text-xs font-bold uppercase tracking-wider text-slate-700">Accepted Lines to Post</Label>
                {postTargetGrn.items
                  .filter((item) => item.acceptedQty > 0)
                  .map((item) => {
                    const serials = lineSerialsInput[item.id] || [];
                    return (
                      <div key={item.id} className="p-3 border rounded-md bg-slate-50/50 space-y-2 text-xs">
                        <div className="flex items-center justify-between">
                          <div>
                            <span className="font-bold text-slate-800">{item.itemCode}</span>
                            <span className="text-slate-600 ml-1.5">{item.itemName}</span>
                          </div>
                          <div className="font-mono">
                            <Badge variant="outline" className="bg-emerald-50 text-emerald-700">
                              Accepted Qty: {item.acceptedQty}
                            </Badge>
                          </div>
                        </div>

                        <div className="text-[11px] text-slate-500">
                          Location: <span className="font-medium text-slate-700">{item.receivingLocationCode}</span>
                          {item.batchLotNo && <span className="ml-2 font-mono">Lot: {item.batchLotNo}</span>}
                        </div>

                        {/* Serials entry for this line */}
                        <div className="pt-2 border-t space-y-1.5">
                          <Label className="text-[11px] text-slate-600">Serial Numbers (for serialised asset items):</Label>
                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                            {serials.map((s, sIdx) => (
                              <Input
                                key={sIdx}
                                placeholder={`Serial #${sIdx + 1}`}
                                value={s}
                                onChange={(e) => {
                                  const updated = [...serials];
                                  updated[sIdx] = e.target.value;
                                  setLineSerialsInput({
                                    ...lineSerialsInput,
                                    [item.id]: updated,
                                  });
                                }}
                                className="h-7 text-xs font-mono"
                              />
                            ))}
                          </div>
                        </div>
                      </div>
                    );
                  })}
              </div>

              <div className="space-y-1.5">
                <Label className="text-xs font-medium text-slate-700">Posting Remarks</Label>
                <Input
                  value={postRemarks}
                  onChange={(e) => setPostRemarks(e.target.value)}
                  placeholder="Optional remarks"
                  className="text-xs"
                />
              </div>

              <div className="text-[11px] text-slate-400 font-mono">
                Idempotency Key: {postSessionKey}
              </div>

              <DialogFooter>
                <Button variant="outline" onClick={() => setIsPostOpen(false)}>
                  Cancel
                </Button>
                <Button
                  onClick={() => {
                    const lineSerialsPayload: LineSerialRequest[] = Object.entries(lineSerialsInput)
                      .map(([grnItemId, serials]) => ({
                        grnItemId,
                        serialNumbers: serials.filter((s) => s.trim().length > 0),
                      }))
                      .filter((ls) => ls.serialNumbers.length > 0);

                    postMutation.mutate({
                      id: postTargetGrn.id,
                      payload: {
                        lineSerials: lineSerialsPayload.length > 0 ? lineSerialsPayload : undefined,
                        remarks: postRemarks,
                      },
                      idempotencyKey: postSessionKey,
                    });
                  }}
                  disabled={postMutation.isPending}
                  className="bg-indigo-600 hover:bg-indigo-700 text-white"
                >
                  {postMutation.isPending ? 'Posting...' : 'Confirm Post to Stock'}
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* Post Result Summary Dialog */}
      <Dialog open={isPostResultOpen} onOpenChange={setIsPostResultOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-emerald-700">
              <CheckCircle2 className="w-5 h-5 text-emerald-600" />
              GRN Successfully Posted!
            </DialogTitle>
            <DialogDescription>
              Inventory stock balances and asset records have been committed.
            </DialogDescription>
          </DialogHeader>

          {postResult && (
            <div className="space-y-3 text-xs">
              <div className="bg-emerald-50 p-3 rounded-md border border-emerald-200 space-y-1">
                <div><span className="text-slate-600">GRN No:</span> <span className="font-mono font-bold text-slate-900">{postResult.grnNo}</span></div>
                <div><span className="text-slate-600">Total Posted Lines:</span> <span className="font-mono font-semibold">{postResult.totalPostedLines}</span></div>
                <div><span className="text-slate-600">Total Assets Created:</span> <span className="font-mono font-bold text-emerald-700">{postResult.totalAssetsCreated}</span></div>
                <div><span className="text-slate-600">Posted At:</span> <span className="font-mono">{new Date(postResult.postedAt).toLocaleString('en-IN')}</span></div>
              </div>

              {postResult.generatedAssetIds?.length > 0 && (
                <div className="space-y-1">
                  <Label className="text-[11px] font-bold text-slate-700">Generated Assets ({postResult.generatedAssetIds.length}):</Label>
                  <div className="max-h-24 overflow-y-auto bg-slate-50 p-2 rounded border font-mono text-[10px] space-y-0.5">
                    {postResult.generatedAssetIds.map((aid) => (
                      <div key={aid} className="text-slate-600">{aid}</div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          <DialogFooter>
            <Button onClick={() => setIsPostResultOpen(false)}>
              Done
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
