'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { AssetSummaryResponse, AssetResponse, AssetAssignRequest } from '@/types/asset';
import { StoreSiteResponse, ItemResponse, PageResponse } from '@/types/master';
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
import { Card, CardContent } from '@/components/ui/card';
import {
  Laptop,
  Search,
  QrCode,
  Eye,
  Building2,
  Calendar,
  ShieldCheck,
  Tag,
  Copy,
  UserCheck,
} from 'lucide-react';
import { toast } from 'sonner';

export default function AssetsPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [selectedStore, setSelectedStore] = useState<string>('ALL');
  const [selectedItem, setSelectedItem] = useState<string>('ALL');
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL');
  const [selectedCondition, setSelectedCondition] = useState<string>('ALL');
  const [page, setPage] = useState(0);

  // Asset Dialog State
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [isQrOpen, setIsQrOpen] = useState(false);
  const [selectedAssetCode, setSelectedAssetCode] = useState<string | null>(null);
  const [selectedQrAsset, setSelectedQrAsset] = useState<AssetSummaryResponse | null>(null);

  // Assign Dialog State
  const [isAssignOpen, setIsAssignOpen] = useState(false);
  const [selectedAssignAsset, setSelectedAssignAsset] = useState<AssetSummaryResponse | null>(null);
  const [assignType, setAssignType] = useState<'EMPLOYEE' | 'DEPARTMENT' | 'PROJECT' | 'LOCATION'>('EMPLOYEE');
  const [assigneeName, setAssigneeName] = useState('');
  const [assignRemarks, setAssignRemarks] = useState('');

  const assignMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: AssetAssignRequest }) =>
      api.post(`/api/store/assets/${id}/assign`, payload),
    onSuccess: () => {
      toast.success('Asset assigned successfully');
      queryClient.invalidateQueries({ queryKey: ['assets-list'] });
      setIsAssignOpen(false);
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to assign asset');
    },
  });

  // Master lookups
  const { data: stores } = useQuery<PageResponse<StoreSiteResponse>>({
    queryKey: ['stores-lookup'],
    queryFn: () => api.get<PageResponse<StoreSiteResponse>>('/api/store/stores?size=100'),
  });

  const { data: items } = useQuery<PageResponse<ItemResponse>>({
    queryKey: ['items-lookup'],
    queryFn: () => api.get<PageResponse<ItemResponse>>('/api/store/items?size=200'),
  });

  // Assets list query
  const { data: assetsData, isLoading } = useQuery<PageResponse<AssetSummaryResponse>>({
    queryKey: ['assets-list', selectedStore, selectedItem, selectedStatus, selectedCondition, search, page],
    queryFn: () => {
      const params = new URLSearchParams();
      if (selectedStore !== 'ALL') params.append('storeId', selectedStore);
      if (selectedItem !== 'ALL') params.append('itemId', selectedItem);
      if (selectedStatus !== 'ALL') params.append('assetStatus', selectedStatus);
      if (selectedCondition !== 'ALL') params.append('conditionStatus', selectedCondition);
      if (search.trim()) params.append('search', search.trim());
      params.append('page', page.toString());
      params.append('size', '15');
      return api.get<PageResponse<AssetSummaryResponse>>(`/api/store/assets?${params.toString()}`);
    },
  });

  // Single asset query by raw code (NOT encodeURIComponent'd)
  const { data: assetDetail, isLoading: isDetailLoading } = useQuery<AssetResponse>({
    queryKey: ['asset-detail', selectedAssetCode],
    queryFn: () => {
      // Send raw code without encodeURIComponent so {*assetCode} matches cleanly
      return api.get<AssetResponse>(`/api/store/assets/by-code/${selectedAssetCode}`);
    },
    enabled: !!selectedAssetCode,
  });

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    toast.success(`${label} copied to clipboard`);
  };

  const columns: ColumnDef<AssetSummaryResponse>[] = [
    {
      header: 'Asset Code',
      accessorKey: 'assetCode',
      cell: (row) => (
        <div>
          <div className="font-mono font-semibold text-blue-700 hover:underline cursor-pointer" onClick={() => {
            setSelectedAssetCode(row.assetCode);
            setIsDetailOpen(true);
          }}>
            {row.assetCode}
          </div>
          {row.serialNumber && (
            <div className="text-xs text-slate-500 font-mono">SN: {row.serialNumber}</div>
          )}
        </div>
      ),
    },
    {
      header: 'Item',
      accessorKey: 'itemCode',
      cell: (row) => (
        <div>
          <div className="font-medium text-slate-900">{row.itemCode}</div>
          <div className="text-xs text-slate-500 truncate max-w-[200px]">{row.itemName}</div>
        </div>
      ),
    },
    {
      header: 'Store / Location',
      accessorKey: 'storeCode',
      cell: (row) => (
        <div className="text-xs">
          <div className="font-medium text-slate-800">{row.storeCode}</div>
          <div className="text-slate-500">{row.locationCode}</div>
        </div>
      ),
    },
    {
      header: 'Status',
      accessorKey: 'assetStatus',
      cell: (row) => <StatusBadge status={row.assetStatus} />,
    },
    {
      header: 'Condition',
      accessorKey: 'conditionStatus',
      cell: (row) => (
        <Badge variant="outline" className="text-[11px] font-medium bg-slate-50 text-slate-700">
          {row.conditionStatus}
        </Badge>
      ),
    },
    {
      header: 'Purchase Cost',
      accessorKey: 'purchaseCost',
      cell: (row) => (
        <div className="font-mono text-xs text-slate-800">
          {row.purchaseCost != null ? `₹${Number(row.purchaseCost).toLocaleString('en-IN', { minimumFractionDigits: 2 })}` : '-'}
        </div>
      ),
    },
    {
      header: 'Actions',
      cell: (row) => (
        <div className="flex items-center gap-1.5">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setSelectedAssetCode(row.assetCode);
              setIsDetailOpen(true);
            }}
            className="h-8 px-2 text-xs"
          >
            <Eye className="size-3.5 mr-1" /> Profile
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setSelectedQrAsset(row);
              setIsQrOpen(true);
            }}
            className="h-8 px-2 text-xs text-indigo-700 border-indigo-200 hover:bg-indigo-50"
          >
            <QrCode className="size-3.5 mr-1" /> QR
          </Button>

          {row.assetStatus !== 'DISPOSED' && row.assetStatus !== 'CONDEMNED' && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setSelectedAssignAsset(row);
                setAssigneeName('');
                setAssignRemarks('');
                setIsAssignOpen(true);
              }}
              className="h-8 px-2 text-xs text-blue-700 border-blue-200 hover:bg-blue-50"
            >
              <UserCheck className="size-3.5 mr-1" /> Assign
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 flex items-center gap-2">
            <Laptop className="size-6 text-blue-600" />
            Asset Register & Lifecycle
          </h1>
          <p className="text-sm text-slate-500">
            Track serialised enterprise hardware, QR code identifiers, location assignments, and warranty status.
          </p>
        </div>
      </div>

      {/* Filter Card */}
      <Card>
        <CardContent className="pt-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
            <div className="space-y-1">
              <Label className="text-xs text-slate-600">Search</Label>
              <div className="relative">
                <Search className="absolute left-2.5 top-2.5 size-4 text-slate-400" />
                <Input
                  placeholder="Asset code, serial, item..."
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value);
                    setPage(0);
                  }}
                  className="pl-8 text-xs"
                />
              </div>
            </div>

            <div className="space-y-1">
              <Label className="text-xs text-slate-600">Store</Label>
              <Select
                value={selectedStore}
                onValueChange={(val) => {
                  setSelectedStore(val);
                  setPage(0);
                }}
              >
                <SelectTrigger className="text-xs">
                  <SelectValue placeholder="All Stores" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Stores</SelectItem>
                  {stores?.content?.map((s) => (
                    <SelectItem key={s.id} value={s.id} className="text-xs">
                      {s.storeCode} - {s.storeName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <Label className="text-xs text-slate-600">Item</Label>
              <Select
                value={selectedItem}
                onValueChange={(val) => {
                  setSelectedItem(val);
                  setPage(0);
                }}
              >
                <SelectTrigger className="text-xs">
                  <SelectValue placeholder="All Items" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Items</SelectItem>
                  {items?.content?.map((it) => (
                    <SelectItem key={it.id} value={it.id} className="text-xs">
                      {it.itemCode} - {it.itemName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <Label className="text-xs text-slate-600">Status</Label>
              <Select
                value={selectedStatus}
                onValueChange={(val) => {
                  setSelectedStatus(val);
                  setPage(0);
                }}
              >
                <SelectTrigger className="text-xs">
                  <SelectValue placeholder="All Statuses" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Statuses</SelectItem>
                  <SelectItem value="AVAILABLE">AVAILABLE</SelectItem>
                  <SelectItem value="ALLOCATED">ALLOCATED</SelectItem>
                  <SelectItem value="ISSUED">ISSUED</SelectItem>
                  <SelectItem value="UNDER_REPAIR">UNDER_REPAIR</SelectItem>
                  <SelectItem value="CONDEMNED">CONDEMNED</SelectItem>
                  <SelectItem value="DISPOSED">DISPOSED</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <Label className="text-xs text-slate-600">Condition</Label>
              <Select
                value={selectedCondition}
                onValueChange={(val) => {
                  setSelectedCondition(val);
                  setPage(0);
                }}
              >
                <SelectTrigger className="text-xs">
                  <SelectValue placeholder="All Conditions" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Conditions</SelectItem>
                  <SelectItem value="GOOD">GOOD</SelectItem>
                  <SelectItem value="FAIR">FAIR</SelectItem>
                  <SelectItem value="POOR">POOR</SelectItem>
                  <SelectItem value="UNSERVICEABLE">UNSERVICEABLE</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Asset Table */}
      <DataTable
        data={assetsData?.content || []}
        columns={columns}
        isLoading={isLoading}
        totalElements={assetsData?.totalElements || 0}
        totalPages={assetsData?.totalPages || 0}
        page={page}
        onPageChange={setPage}
      />

      {/* Asset Profile Dialog */}
      <Dialog open={isDetailOpen} onOpenChange={setIsDetailOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg">
              <Laptop className="size-5 text-blue-600" />
              Asset Profile: {selectedAssetCode}
            </DialogTitle>
            <DialogDescription>
              Detailed asset lifecycle, financial snapshot, and location tracking.
            </DialogDescription>
          </DialogHeader>

          {isDetailLoading && (
            <div className="py-8 text-center text-sm text-slate-500">Loading asset profile...</div>
          )}

          {assetDetail && (
            <div className="space-y-4">
              {/* Header card */}
              <div className="bg-slate-50 p-4 rounded-lg border border-slate-200 grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                <div>
                  <span className="text-slate-500">Status:</span>
                  <div className="mt-1"><StatusBadge status={assetDetail.assetStatus} /></div>
                </div>
                <div>
                  <span className="text-slate-500">Condition:</span>
                  <div className="font-semibold text-slate-800 mt-1">{assetDetail.conditionStatus}</div>
                </div>
                <div>
                  <span className="text-slate-500">Serial Number:</span>
                  <div className="font-mono font-semibold text-slate-800 mt-1">{assetDetail.serialNumber || '-'}</div>
                </div>
                <div>
                  <span className="text-slate-500">Capitalization Ref:</span>
                  <div className="font-mono text-slate-800 mt-1">{assetDetail.capitalizationRef || '-'}</div>
                </div>
              </div>

              {/* Item Info */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                <div className="p-3 border rounded-md space-y-1">
                  <div className="font-bold text-slate-700 uppercase tracking-wider text-[10px]">Item Specifications</div>
                  <div><span className="text-slate-500">Item: </span><span className="font-medium">{assetDetail.itemCode} - {assetDetail.itemName}</span></div>
                  <div><span className="text-slate-500">Category: </span><span>{assetDetail.categoryCode} ({assetDetail.categoryName})</span></div>
                  <div><span className="text-slate-500">Make / Model: </span><span>{assetDetail.manufacturer || '-'} / {assetDetail.modelNumber || '-'}</span></div>
                </div>

                <div className="p-3 border rounded-md space-y-1">
                  <div className="font-bold text-slate-700 uppercase tracking-wider text-[10px]">Location Tracking</div>
                  <div><span className="text-slate-500">Store: </span><span className="font-medium">{assetDetail.storeCode} - {assetDetail.storeName}</span></div>
                  <div><span className="text-slate-500">Location: </span><span>{assetDetail.locationCode} - {assetDetail.locationName}</span></div>
                </div>
              </div>

              {/* Financial & Warranty */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                <div className="p-3 border rounded-md space-y-1">
                  <div className="font-bold text-slate-700 uppercase tracking-wider text-[10px]">Procurement Details</div>
                  <div><span className="text-slate-500">PO Number: </span><span className="font-mono">{assetDetail.poNumberSnapshot || '-'}</span></div>
                  <div><span className="text-slate-500">Invoice: </span><span className="font-mono">{assetDetail.invoiceNumberSnapshot || '-'}</span></div>
                  <div><span className="text-slate-500">Purchase Date: </span><span>{assetDetail.purchaseDate ? new Date(assetDetail.purchaseDate).toLocaleDateString('en-IN') : '-'}</span></div>
                  <div><span className="text-slate-500">Purchase Cost: </span><span className="font-mono font-semibold">₹{Number(assetDetail.purchaseCost || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span></div>
                </div>

                <div className="p-3 border rounded-md space-y-1">
                  <div className="font-bold text-slate-700 uppercase tracking-wider text-[10px]">Warranty & Compliance</div>
                  <div><span className="text-slate-500">Warranty Start: </span><span>{assetDetail.warrantyStartDate ? new Date(assetDetail.warrantyStartDate).toLocaleDateString('en-IN') : '-'}</span></div>
                  <div><span className="text-slate-500">Warranty End: </span><span>{assetDetail.warrantyEndDate ? new Date(assetDetail.warrantyEndDate).toLocaleDateString('en-IN') : '-'}</span></div>
                  <div><span className="text-slate-500">QR Code: </span><span className="font-mono text-[11px] text-indigo-700">{assetDetail.qrCodeValue}</span></div>
                </div>
              </div>

              {assetDetail.remarks && (
                <div className="text-xs text-slate-600 bg-slate-50 p-2.5 rounded border">
                  <span className="font-semibold text-slate-700">Remarks: </span>{assetDetail.remarks}
                </div>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* QR Code Modal */}
      <Dialog open={isQrOpen} onOpenChange={setIsQrOpen}>
        <DialogContent className="max-w-md text-center">
          <DialogHeader>
            <DialogTitle className="text-center flex justify-center items-center gap-2">
              <QrCode className="size-5 text-indigo-600" />
              Asset QR Identifier
            </DialogTitle>
            <DialogDescription className="text-center">
              Physical tagging barcode/QR payload
            </DialogDescription>
          </DialogHeader>

          {selectedQrAsset && (
            <div className="space-y-4 py-3">
              {/* QR Visual representation */}
              <div className="bg-slate-900 text-white p-6 rounded-lg max-w-[260px] mx-auto shadow-md">
                <div className="font-mono text-xs font-bold text-amber-400 mb-1">NICSI ASSET TAG</div>
                <div className="bg-white p-3 rounded my-2 text-slate-950 font-mono text-center text-xs font-bold break-all border-2 border-slate-700">
                  <div className="grid place-items-center py-4">
                    <QrCode className="size-20 text-slate-900" />
                  </div>
                  {selectedQrAsset.qrCodeValue}
                </div>
                <div className="text-[11px] text-slate-300 font-mono">{selectedQrAsset.assetCode}</div>
                {selectedQrAsset.serialNumber && (
                  <div className="text-[10px] text-slate-400 font-mono">SN: {selectedQrAsset.serialNumber}</div>
                )}
              </div>

              <div className="text-xs text-slate-600 space-y-1 text-left bg-slate-50 p-3 rounded border">
                <div><span className="font-semibold text-slate-700">Item:</span> {selectedQrAsset.itemCode} - {selectedQrAsset.itemName}</div>
                <div><span className="font-semibold text-slate-700">Store:</span> {selectedQrAsset.storeCode} ({selectedQrAsset.locationCode})</div>
                <div className="flex items-center justify-between pt-2">
                  <span className="font-mono text-[11px] text-slate-500 truncate max-w-[280px]">{selectedQrAsset.qrCodeValue}</span>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => copyToClipboard(selectedQrAsset.qrCodeValue, 'QR Value')}
                    className="h-7 text-xs"
                  >
                    <Copy className="size-3.5 mr-1" /> Copy
                  </Button>
                </div>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* ASSIGN ASSET DIALOG */}
      <Dialog open={isAssignOpen} onOpenChange={setIsAssignOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base text-blue-700">
              <UserCheck className="size-5" />
              Assign Asset Custodian
            </DialogTitle>
            <DialogDescription className="text-xs">
              Assign or transfer custody of {selectedAssignAsset?.assetCode} ({selectedAssignAsset?.itemName}).
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3 py-2 text-xs">
            <div className="space-y-1">
              <Label className="text-xs font-semibold">Assignment Type *</Label>
              <Select value={assignType} onValueChange={(v) => setAssignType(v as any)}>
                <SelectTrigger className="h-9 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="EMPLOYEE">Employee Custodian</SelectItem>
                  <SelectItem value="DEPARTMENT">Department Cell</SelectItem>
                  <SelectItem value="PROJECT">Project Site</SelectItem>
                  <SelectItem value="LOCATION">Storage Location</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Assignee / Custodian Name *</Label>
              <Input
                placeholder="e.g. John Doe / Cloud Team"
                value={assigneeName}
                onChange={(e) => setAssigneeName(e.target.value)}
                className="h-9 text-xs"
              />
            </div>

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Remarks / Handover Notes</Label>
              <Input
                placeholder="Optional purpose..."
                value={assignRemarks}
                onChange={(e) => setAssignRemarks(e.target.value)}
                className="h-9 text-xs"
              />
            </div>
          </div>

          <DialogFooter>
            <Button size="sm" variant="outline" onClick={() => setIsAssignOpen(false)}>
              Cancel
            </Button>
            <Button
              size="sm"
              className="bg-blue-600 hover:bg-blue-700 text-white"
              onClick={() => {
                if (selectedAssignAsset) {
                  assignMutation.mutate({
                    id: selectedAssignAsset.id,
                    payload: {
                      assignmentType: assignType,
                      assigneeNameSnapshot: assigneeName || undefined,
                      remarks: assignRemarks || undefined,
                    },
                  });
                }
              }}
              disabled={assignMutation.isPending || !assigneeName.trim()}
            >
              Confirm Assignment
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
