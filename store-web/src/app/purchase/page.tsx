'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import {
  PurchaseOrderResponse,
  PurchaseOrderSummaryResponse,
  CreatePurchaseOrderRequest,
  CreatePurchaseOrderItemRequest,
} from '@/types/purchase-order';
import { ItemResponse, PageResponse } from '@/types/master';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { StatusBadge } from '@/components/common/status-badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
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
  Eye,
  Trash2,
  Truck,
  IndianRupee,
  Calendar,
  Building2,
  Tag,
  ShoppingBag,
} from 'lucide-react';
import { toast } from 'sonner';

interface FormLine {
  poLineNo: number;
  itemId: string;
  itemCode: string;
  itemName: string;
  itemDescription: string;
  orderedQty: number;
  unitRate: number;
  taxAmount: number;
  deliveryDueDate: string;
  projectName: string;
}

export default function PurchaseOrdersPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('ALL');
  const [page, setPage] = useState(0);

  // Create PO Modal State
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [poNumber, setPoNumber] = useState('');
  const [poDate, setPoDate] = useState(new Date().toISOString().split('T')[0]);
  const [procurementMode, setProcurementMode] = useState('GEM');
  const [gemOrderNumber, setGemOrderNumber] = useState('');
  const [contractNumber, setContractNumber] = useState('');
  const [vendorName, setVendorName] = useState('');
  const [vendorCode, setVendorCode] = useState('');
  const [lines, setLines] = useState<FormLine[]>([]);

  // Selected item line addition
  const [selectedItemId, setSelectedItemId] = useState('');
  const [lineQty, setLineQty] = useState<number | ''>('');
  const [lineRate, setLineRate] = useState<number | ''>('');
  const [lineTax, setLineTax] = useState<number | ''>(0);
  const [lineDeliveryDate, setLineDeliveryDate] = useState('');
  const [lineProject, setLineProject] = useState('');

  // View / Detail Modal State
  const [selectedPo, setSelectedPo] = useState<PurchaseOrderResponse | null>(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);

  // Fetch PO list
  const { data: poData, isLoading } = useQuery({
    queryKey: ['purchase-orders', page, search, filterStatus],
    queryFn: () => {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', '15');
      if (search) params.append('search', search);
      if (filterStatus !== 'ALL') params.append('status', filterStatus);
      return api.get<PageResponse<PurchaseOrderSummaryResponse>>(`/api/store/purchase-orders?${params.toString()}`);
    },
  });

  // Fetch Items for selector
  const { data: itemsData } = useQuery({
    queryKey: ['items-selector'],
    queryFn: () => api.get<PageResponse<ItemResponse>>('/api/store/items?size=100&active=true'),
  });

  // Create Mutation
  const createMutation = useMutation({
    mutationFn: (data: CreatePurchaseOrderRequest) =>
      api.post<PurchaseOrderResponse>('/api/store/purchase-orders', data),
    onSuccess: (data) => {
      toast.success(`Purchase Order created: ${data.poNumber}`);
      queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
      setIsCreateOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create Purchase Order');
    },
  });

  const resetForm = () => {
    setPoNumber('');
    setPoDate(new Date().toISOString().split('T')[0]);
    setProcurementMode('GEM');
    setGemOrderNumber('');
    setContractNumber('');
    setVendorName('');
    setVendorCode('');
    setLines([]);
    setSelectedItemId('');
    setLineQty('');
    setLineRate('');
    setLineTax(0);
    setLineDeliveryDate('');
    setLineProject('');
  };

  const handleAddLine = () => {
    if (!selectedItemId || !lineQty || Number(lineQty) <= 0) {
      toast.error('Please select an item and enter valid ordered quantity');
      return;
    }

    const itemObj = itemsData?.content.find((i) => i.id === selectedItemId);
    if (!itemObj) return;

    const newLine: FormLine = {
      poLineNo: lines.length + 1,
      itemId: itemObj.id,
      itemCode: itemObj.itemCode,
      itemName: itemObj.itemName,
      itemDescription: itemObj.shortDescription || itemObj.itemName,
      orderedQty: Number(lineQty),
      unitRate: lineRate ? Number(lineRate) : (itemObj.standardRate || 0),
      taxAmount: Number(lineTax) || 0,
      deliveryDueDate: lineDeliveryDate,
      projectName: lineProject,
    };

    setLines([...lines, newLine]);
    setSelectedItemId('');
    setLineQty('');
    setLineRate('');
    setLineTax(0);
    setLineDeliveryDate('');
    setLineProject('');
  };

  const handleRemoveLine = (index: number) => {
    setLines(lines.filter((_, i) => i !== index));
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!poNumber.trim()) {
      toast.error('PO Number is required');
      return;
    }
    if (lines.length === 0) {
      toast.error('Add at least one line item');
      return;
    }

    const payload: CreatePurchaseOrderRequest = {
      sourceSystem: 'NICSI_ERP',
      poNumber: poNumber.trim(),
      poDate,
      procurementMode,
      gemOrderNumber: gemOrderNumber.trim() || undefined,
      contractNumber: contractNumber.trim() || undefined,
      vendorNameSnapshot: vendorName.trim() || undefined,
      vendorCodeSnapshot: vendorCode.trim() || undefined,
      currencyCode: 'INR',
      items: lines.map((l, idx) => ({
        poLineNo: idx + 1,
        itemId: l.itemId,
        itemDescription: l.itemDescription,
        orderedQty: l.orderedQty,
        unitRate: l.unitRate,
        taxAmount: l.taxAmount,
        deliveryDueDate: l.deliveryDueDate || undefined,
        projectNameSnapshot: l.projectName || undefined,
      })),
    };

    createMutation.mutate(payload);
  };

  const viewPoDetail = async (id: string) => {
    try {
      const detail = await api.get<PurchaseOrderResponse>(`/api/store/purchase-orders/${id}`);
      setSelectedPo(detail);
      setIsDetailOpen(true);
    } catch (err: unknown) {
      const e = err as ApiError;
      toast.error(e.message || 'Failed to load PO details');
    }
  };

  const columns: ColumnDef<PurchaseOrderSummaryResponse>[] = [
    {
      header: 'PO Number',
      cell: (row) => (
        <div>
          <div className="font-semibold text-primary flex items-center gap-1.5">
            <FileText className="w-4 h-4 text-muted-foreground" />
            {row.poNumber}
          </div>
          <div className="text-xs text-muted-foreground">
            {row.poDate || new Date(row.createdAt).toLocaleDateString()}
          </div>
        </div>
      ),
    },
    {
      header: 'Procurement Mode',
      cell: (row) => (
        <div className="flex flex-col gap-1">
          <Badge variant="outline" className="w-fit text-xs font-mono">
            {row.procurementMode || 'DIRECT'}
          </Badge>
          {row.gemOrderNumber && (
            <span className="text-xs text-muted-foreground">GeM: {row.gemOrderNumber}</span>
          )}
        </div>
      ),
    },
    {
      header: 'Vendor',
      cell: (row) => (
        <span className="font-medium text-foreground">
          {row.vendorNameSnapshot || 'Not Specified'}
        </span>
      ),
    },
    {
      header: 'Total Value (INR)',
      cell: (row) => (
        <div className="font-mono font-medium">
          ₹{row.totalAmount != null ? row.totalAmount.toLocaleString('en-IN', { minimumFractionDigits: 2 }) : '0.00'}
        </div>
      ),
    },
    {
      header: 'Items',
      cell: (row) => (
        <Badge variant="secondary" className="font-mono text-xs">
          {row.itemCount} line{row.itemCount !== 1 ? 's' : ''}
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
        <Button
          variant="outline"
          size="sm"
          onClick={() => viewPoDetail(row.id)}
          className="h-8 gap-1.5"
        >
          <Eye className="w-3.5 h-3.5" />
          View
        </Button>
      ),
    },
  ];

  const totalCalculated = lines.reduce(
    (acc, l) => acc + l.orderedQty * l.unitRate + l.taxAmount,
    0
  );

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <Truck className="w-6 h-6 text-primary" />
            Purchase Orders & Contract References
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Procurement references, GeM contracts, and ordered lines for receipt verification.
          </p>
        </div>
        <Button onClick={() => setIsCreateOpen(true)} className="gap-2">
          <Plus className="w-4 h-4" />
          New Purchase Order
        </Button>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="p-4 flex flex-col sm:flex-row gap-3">
          <div className="flex-1">
            <Input
              placeholder="Search by PO Number, Vendor, GeM order..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
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
                <SelectItem value="OPEN">Open</SelectItem>
                <SelectItem value="PARTIALLY_RECEIVED">Partially Received</SelectItem>
                <SelectItem value="CLOSED">Closed</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <DataTable
        columns={columns}
        data={poData?.content || []}
        isLoading={isLoading}
        page={page}
        totalPages={poData?.totalPages || 0}
        totalElements={poData?.totalElements || 0}
        onPageChange={setPage}
      />

      {/* Create Purchase Order Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-xl">
              <ShoppingBag className="w-5 h-5 text-primary" />
              Create Purchase Order Reference
            </DialogTitle>
            <DialogDescription>
              Record an external PO, GeM Order, or procurement tender reference for goods receipt matching.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label htmlFor="poNumber">PO Number *</Label>
                <Input
                  id="poNumber"
                  placeholder="e.g. GEM/2026/B/871291"
                  value={poNumber}
                  onChange={(e) => setPoNumber(e.target.value)}
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="poDate">PO Date</Label>
                <Input
                  id="poDate"
                  type="date"
                  value={poDate}
                  onChange={(e) => setPoDate(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="procurementMode">Procurement Mode</Label>
                <Select value={procurementMode} onValueChange={setProcurementMode}>
                  <SelectTrigger id="procurementMode">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="GEM">GeM Portal</SelectItem>
                    <SelectItem value="TENDER">Open Tender</SelectItem>
                    <SelectItem value="DIRECT">Direct Purchase</SelectItem>
                    <SelectItem value="RATE_CONTRACT">Rate Contract</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="gemOrder">GeM Order No</Label>
                <Input
                  id="gemOrder"
                  placeholder="e.g. GEMC-51168772"
                  value={gemOrderNumber}
                  onChange={(e) => setGemOrderNumber(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="contractNo">Contract Number</Label>
                <Input
                  id="contractNo"
                  placeholder="e.g. NICSI/CON/2026/04"
                  value={contractNumber}
                  onChange={(e) => setContractNumber(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="vendorName">Vendor Name</Label>
                <Input
                  id="vendorName"
                  placeholder="e.g. Dell Technologies India"
                  value={vendorName}
                  onChange={(e) => setVendorName(e.target.value)}
                />
              </div>
            </div>

            {/* Line Items Card */}
            <Card className="border-dashed">
              <CardHeader className="pb-3">
                <CardTitle className="text-sm font-semibold flex items-center justify-between">
                  <span className="flex items-center gap-1.5">
                    <Tag className="w-4 h-4 text-primary" />
                    Ordered Line Items ({lines.length})
                  </span>
                  <span className="text-primary font-mono text-sm">
                    Est. Total: ₹{totalCalculated.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  </span>
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {/* Line Input Row */}
                <div className="grid grid-cols-1 sm:grid-cols-6 gap-3 p-3 bg-muted/40 rounded-lg">
                  <div className="sm:col-span-2 space-y-1">
                    <Label className="text-xs">Catalog Item *</Label>
                    <Select value={selectedItemId} onValueChange={setSelectedItemId}>
                      <SelectTrigger className="h-9 text-xs">
                        <SelectValue placeholder="Select catalog item" />
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

                  <div className="space-y-1">
                    <Label className="text-xs">Qty *</Label>
                    <Input
                      type="number"
                      placeholder="Qty"
                      className="h-9 text-xs font-mono"
                      value={lineQty}
                      onChange={(e) => setLineQty(e.target.value ? Number(e.target.value) : '')}
                    />
                  </div>

                  <div className="space-y-1">
                    <Label className="text-xs">Unit Rate (₹)</Label>
                    <Input
                      type="number"
                      placeholder="Rate"
                      className="h-9 text-xs font-mono"
                      value={lineRate}
                      onChange={(e) => setLineRate(e.target.value ? Number(e.target.value) : '')}
                    />
                  </div>

                  <div className="space-y-1">
                    <Label className="text-xs">Tax (₹)</Label>
                    <Input
                      type="number"
                      placeholder="Tax"
                      className="h-9 text-xs font-mono"
                      value={lineTax}
                      onChange={(e) => setLineTax(e.target.value ? Number(e.target.value) : '')}
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
                      Add Line
                    </Button>
                  </div>
                </div>

                {/* Line Items Table */}
                {lines.length > 0 && (
                  <div className="border rounded-md overflow-hidden">
                    <table className="w-full text-xs">
                      <thead className="bg-muted text-muted-foreground text-left">
                        <tr>
                          <th className="p-2">#</th>
                          <th className="p-2">Item</th>
                          <th className="p-2 font-mono text-right">Ordered Qty</th>
                          <th className="p-2 font-mono text-right">Unit Rate</th>
                          <th className="p-2 font-mono text-right">Tax</th>
                          <th className="p-2 font-mono text-right">Line Total</th>
                          <th className="p-2 text-center">Action</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {lines.map((line, idx) => {
                          const lineTotal = line.orderedQty * line.unitRate + line.taxAmount;
                          return (
                            <tr key={idx} className="hover:bg-muted/30">
                              <td className="p-2 font-mono text-muted-foreground">{idx + 1}</td>
                              <td className="p-2">
                                <span className="font-semibold">{line.itemCode}</span>
                                <span className="text-muted-foreground ml-1">({line.itemName})</span>
                              </td>
                              <td className="p-2 font-mono text-right">{line.orderedQty}</td>
                              <td className="p-2 font-mono text-right">₹{line.unitRate.toFixed(2)}</td>
                              <td className="p-2 font-mono text-right">₹{line.taxAmount.toFixed(2)}</td>
                              <td className="p-2 font-mono text-right font-medium text-primary">
                                ₹{lineTotal.toFixed(2)}
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
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </CardContent>
            </Card>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending || lines.length === 0}>
                {createMutation.isPending ? 'Saving...' : 'Create Purchase Order'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* PO Detail Dialog */}
      <Dialog open={isDetailOpen} onOpenChange={setIsDetailOpen}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          {selectedPo && (
            <div className="space-y-6">
              <DialogHeader>
                <div className="flex items-center justify-between">
                  <DialogTitle className="text-xl font-bold flex items-center gap-2">
                    <FileText className="w-5 h-5 text-primary" />
                    {selectedPo.poNumber}
                  </DialogTitle>
                  <StatusBadge status={selectedPo.status} />
                </div>
                <DialogDescription>
                  Procurement contract details and fulfillment progress.
                </DialogDescription>
              </DialogHeader>

              {/* Summary Cards */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div className="p-3 bg-muted/30 rounded-lg">
                  <div className="text-xs text-muted-foreground flex items-center gap-1">
                    <Building2 className="w-3.5 h-3.5" /> Vendor
                  </div>
                  <div className="text-sm font-semibold truncate mt-1">
                    {selectedPo.vendorNameSnapshot || 'N/A'}
                  </div>
                </div>
                <div className="p-3 bg-muted/30 rounded-lg">
                  <div className="text-xs text-muted-foreground flex items-center gap-1">
                    <Calendar className="w-3.5 h-3.5" /> PO Date
                  </div>
                  <div className="text-sm font-semibold mt-1">
                    {selectedPo.poDate || new Date(selectedPo.createdAt).toLocaleDateString()}
                  </div>
                </div>
                <div className="p-3 bg-muted/30 rounded-lg">
                  <div className="text-xs text-muted-foreground flex items-center gap-1">
                    <IndianRupee className="w-3.5 h-3.5" /> Total Amount
                  </div>
                  <div className="text-sm font-semibold font-mono text-primary mt-1">
                    ₹{selectedPo.totalAmount?.toLocaleString('en-IN', { minimumFractionDigits: 2 }) || '0.00'}
                  </div>
                </div>
                <div className="p-3 bg-muted/30 rounded-lg">
                  <div className="text-xs text-muted-foreground flex items-center gap-1">
                    <Tag className="w-3.5 h-3.5" /> Mode
                  </div>
                  <div className="text-sm font-semibold mt-1">
                    {selectedPo.procurementMode || 'DIRECT'}
                  </div>
                </div>
              </div>

              {/* Items Table */}
              <div className="border rounded-lg overflow-hidden">
                <table className="w-full text-xs">
                  <thead className="bg-muted text-muted-foreground text-left">
                    <tr>
                      <th className="p-2.5">Line</th>
                      <th className="p-2.5">Item Code / Name</th>
                      <th className="p-2.5 font-mono text-right">Ordered</th>
                      <th className="p-2.5 font-mono text-right">Received</th>
                      <th className="p-2.5 font-mono text-right">Remaining</th>
                      <th className="p-2.5 font-mono text-right">Unit Rate</th>
                      <th className="p-2.5 font-mono text-right">Tax</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {selectedPo.items.map((line) => (
                      <tr key={line.id} className="hover:bg-muted/20">
                        <td className="p-2.5 font-mono text-muted-foreground">{line.poLineNo}</td>
                        <td className="p-2.5">
                          <div className="font-semibold text-foreground">{line.itemCode}</div>
                          <div className="text-muted-foreground text-[11px]">{line.itemName || line.itemDescription}</div>
                        </td>
                        <td className="p-2.5 font-mono text-right font-medium">{line.orderedQty}</td>
                        <td className="p-2.5 font-mono text-right font-medium text-emerald-600">
                          {line.receivedQty}
                        </td>
                        <td className="p-2.5 font-mono text-right font-medium text-amber-600">
                          {line.remainingQty}
                        </td>
                        <td className="p-2.5 font-mono text-right">₹{line.unitRate.toFixed(2)}</td>
                        <td className="p-2.5 font-mono text-right">₹{line.taxAmount.toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <DialogFooter>
                <Button variant="outline" onClick={() => setIsDetailOpen(false)}>
                  Close
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
