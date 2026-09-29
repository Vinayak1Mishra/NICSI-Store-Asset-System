'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import {
  ItemResponse,
  ItemCategoryResponse,
  ItemSubcategoryResponse,
  UomResponse,
  PageResponse,
} from '@/types/master';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { StatusBadge } from '@/components/common/status-badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
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
import { Plus, Package, RefreshCw, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

const ITEM_TYPES = ['CONSUMABLE', 'NON_CONSUMABLE', 'SOFTWARE', 'SERVICE_SUPPORT'];
const TRACKING_TYPES = ['QUANTITY', 'SERIAL', 'LOT', 'LICENSE'];

export default function ItemsPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [filterType, setFilterType] = useState<string>('ALL');
  const [page, setPage] = useState(0);
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  // Form State
  const [itemCode, setItemCode] = useState('');
  const [itemName, setItemName] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [subcategoryId, setSubcategoryId] = useState('');
  const [baseUomId, setBaseUomId] = useState('');
  const [itemType, setItemType] = useState('CONSUMABLE');
  const [trackingType, setTrackingType] = useState('QUANTITY');
  const [standardRate, setStandardRate] = useState<number | ''>('');
  const [hsnSacCode, setHsnSacCode] = useState('');
  const [usefulLifeMonths, setUsefulLifeMonths] = useState<number | ''>('');
  const [warrantyMonths, setWarrantyMonths] = useState<number | ''>('');
  const [returnable, setReturnable] = useState(false);
  const [warrantyApplicable, setWarrantyApplicable] = useState(false);
  const [expiryTracking, setExpiryTracking] = useState(false);
  const [assetRequired, setAssetRequired] = useState(false);
  const [shortDescription, setShortDescription] = useState('');

  // Fetch Categories for dropdown
  const { data: categoriesData } = useQuery({
    queryKey: ['categories-lookup'],
    queryFn: () => api.get<PageResponse<ItemCategoryResponse>>('/api/store/categories?size=100'),
  });

  // Fetch Subcategories when category changes
  const { data: subcategoriesData } = useQuery({
    queryKey: ['subcategories-lookup', categoryId],
    queryFn: () => api.get<PageResponse<ItemSubcategoryResponse>>(`/api/store/categories/${categoryId}/subcategories`),
    enabled: !!categoryId,
  });

  // Fetch UOMs for dropdown
  const { data: uomsData } = useQuery({
    queryKey: ['uoms-lookup'],
    queryFn: () => api.get<PageResponse<UomResponse>>('/api/store/uoms?size=100'),
  });

  // Fetch Items list
  const { data, isLoading, refetch } = useQuery({
    queryKey: ['items', page, search, filterType],
    queryFn: async () => {
      const params = new URLSearchParams({
        page: page.toString(),
        size: '15',
      });
      if (search) params.append('search', search);
      if (filterType !== 'ALL') params.append('itemType', filterType);
      return api.get<PageResponse<ItemResponse>>(`/api/store/items?${params.toString()}`);
    },
  });

  // Create Item mutation
  const createMutation = useMutation({
    mutationFn: async () => {
      return api.post<ItemResponse>('/api/store/items', {
        itemCode: itemCode.trim().toUpperCase(),
        itemName: itemName.trim(),
        categoryId,
        subcategoryId: subcategoryId || undefined,
        baseUomId,
        itemType,
        trackingType,
        shortDescription: shortDescription.trim() || undefined,
        hsnSacCode: hsnSacCode.trim() || undefined,
        standardRate: standardRate !== '' ? Number(standardRate) : undefined,
        usefulLifeMonths: usefulLifeMonths !== '' ? Number(usefulLifeMonths) : undefined,
        warrantyMonths: warrantyMonths !== '' ? Number(warrantyMonths) : undefined,
        returnable,
        warrantyApplicable,
        expiryTracking,
        assetRequired,
      });
    },
    onSuccess: (item) => {
      toast.success(`Item ${item.itemName} (${item.itemCode}) created successfully`);
      setIsCreateOpen(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['items'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create item');
    },
  });

  // Status toggle
  const statusMutation = useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      return api.patch<ItemResponse>(`/api/store/items/${id}/status`, { active });
    },
    onSuccess: () => {
      toast.success('Item status updated');
      queryClient.invalidateQueries({ queryKey: ['items'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to update item status');
    },
  });

  const resetForm = () => {
    setItemCode('');
    setItemName('');
    setCategoryId('');
    setSubcategoryId('');
    setBaseUomId('');
    setItemType('CONSUMABLE');
    setTrackingType('QUANTITY');
    setStandardRate('');
    setHsnSacCode('');
    setUsefulLifeMonths('');
    setWarrantyMonths('');
    setReturnable(false);
    setWarrantyApplicable(false);
    setExpiryTracking(false);
    setAssetRequired(false);
    setShortDescription('');
  };

  const columns: ColumnDef<ItemResponse>[] = [
    {
      header: 'Item Code',
      accessorKey: 'itemCode',
      cell: (row) => <span className="font-semibold text-foreground font-mono">{row.itemCode}</span>,
    },
    {
      header: 'Item Name',
      accessorKey: 'itemName',
      cell: (row) => (
        <div>
          <p className="font-medium text-foreground">{row.itemName}</p>
          <p className="text-[11px] text-muted-foreground">
            {row.categoryName} {row.subcategoryName ? `› ${row.subcategoryName}` : ''}
          </p>
        </div>
      ),
    },
    {
      header: 'Type',
      accessorKey: 'itemType',
      cell: (row) => (
        <span className="rounded bg-muted px-2 py-0.5 text-[10px] font-semibold tracking-wider">
          {row.itemType}
        </span>
      ),
    },
    {
      header: 'Tracking',
      accessorKey: 'trackingType',
      cell: (row) => (
        <span className="rounded bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-300 border border-blue-200 px-1.5 py-0.5 text-[10px] font-mono">
          {row.trackingType}
        </span>
      ),
    },
    {
      header: 'Base UOM',
      accessorKey: 'uomCode',
      cell: (row) => <span className="font-mono text-xs">{row.uomCode}</span>,
    },
    {
      header: 'Std Rate (₹)',
      accessorKey: 'standardRate',
      cell: (row) => (
        <span className="font-mono text-xs">
          {row.standardRate != null ? `₹${Number(row.standardRate).toLocaleString('en-IN')}` : '—'}
        </span>
      ),
    },
    {
      header: 'Status',
      cell: (row) => <StatusBadge status={row.active} />,
    },
    {
      header: 'Actions',
      cell: (row) => (
        <Button
          variant="ghost"
          size="sm"
          className="h-7 text-xs"
          onClick={() => statusMutation.mutate({ id: row.id, active: !row.active })}
        >
          {row.active ? 'Deactivate' : 'Activate'}
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      {/* Intro Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-card p-4 rounded-lg border border-border">
        <div>
          <h2 className="text-base font-semibold flex items-center gap-2">
            <Package className="size-4 text-primary" />
            Item Master Catalog
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Single registry of consumables, assets, software licenses and service contracts.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()} className="gap-1 text-xs">
            <RefreshCw className="size-3.5" />
            Refresh
          </Button>
          <Button size="sm" onClick={() => setIsCreateOpen(true)} className="gap-1 text-xs">
            <Plus className="size-3.5" />
            New Item
          </Button>
        </div>
      </div>

      {/* Main Table */}
      <DataTable
        columns={columns}
        data={data?.content || []}
        isLoading={isLoading}
        searchPlaceholder="Search item code or name..."
        searchValue={search}
        onSearchChange={(v) => {
          setSearch(v);
          setPage(0);
        }}
        page={page}
        totalPages={data?.totalPages || 1}
        totalElements={data?.totalElements}
        onPageChange={setPage}
        extraFilters={
          <Select value={filterType} onValueChange={setFilterType}>
            <SelectTrigger className="h-9 text-xs w-[160px] bg-background">
              <SelectValue placeholder="All Item Types" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL" className="text-xs">
                All Item Types
              </SelectItem>
              {ITEM_TYPES.map((t) => (
                <SelectItem key={t} value={t} className="text-xs">
                  {t}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        }
      />

      {/* Create Item Modal */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="sm:max-w-[650px] max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Register New Catalog Item</DialogTitle>
            <DialogDescription className="text-xs">
              Configure tracking rules, classification, and warranty constraints.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              // Check SQL rule: if assetRequired, trackingType must be SERIAL
              if (assetRequired && trackingType !== 'SERIAL') {
                toast.error('SQL constraint: Asset-required items MUST use SERIAL tracking');
                return;
              }
              createMutation.mutate();
            }}
            className="space-y-4 py-2"
          >
            {/* Codes and Names */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Item Code *</Label>
                <Input
                  required
                  maxLength={60}
                  placeholder="e.g. IT-LAP-001"
                  value={itemCode}
                  onChange={(e) => setItemCode(e.target.value)}
                  className="h-8 text-xs font-mono uppercase"
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">Item Name *</Label>
                <Input
                  required
                  maxLength={200}
                  placeholder="e.g. Dell Latitude 5440 Laptop"
                  value={itemName}
                  onChange={(e) => setItemName(e.target.value)}
                  className="h-8 text-xs"
                />
              </div>
            </div>

            {/* Classification & UOM */}
            <div className="grid grid-cols-3 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Category *</Label>
                <Select value={categoryId} onValueChange={(val) => { setCategoryId(val); setSubcategoryId(''); }}>
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue placeholder="Select Category" />
                  </SelectTrigger>
                  <SelectContent>
                    {categoriesData?.content?.map((c) => (
                      <SelectItem key={c.id} value={c.id} className="text-xs">
                        {c.categoryName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label className="text-xs">Subcategory</Label>
                <Select
                  value={subcategoryId}
                  onValueChange={setSubcategoryId}
                  disabled={!categoryId}
                >
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue placeholder="Optional Subcategory" />
                  </SelectTrigger>
                  <SelectContent>
                    {subcategoriesData?.content?.map((s) => (
                      <SelectItem key={s.id} value={s.id} className="text-xs">
                        {s.subcategoryName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label className="text-xs">Base UOM *</Label>
                <Select value={baseUomId} onValueChange={setBaseUomId}>
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue placeholder="Select UOM" />
                  </SelectTrigger>
                  <SelectContent>
                    {uomsData?.content?.map((u) => (
                      <SelectItem key={u.id} value={u.id} className="text-xs">
                        {u.uomCode} - {u.uomName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {/* Types and Tracking */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Item Type *</Label>
                <Select value={itemType} onValueChange={setItemType}>
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {ITEM_TYPES.map((t) => (
                      <SelectItem key={t} value={t} className="text-xs">
                        {t}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label className="text-xs">Tracking Type *</Label>
                <Select
                  value={trackingType}
                  onValueChange={(val) => {
                    setTrackingType(val);
                    if (val !== 'SERIAL') setAssetRequired(false);
                  }}
                >
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TRACKING_TYPES.map((t) => (
                      <SelectItem key={t} value={t} className="text-xs">
                        {t}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {/* Financial and Warranty */}
            <div className="grid grid-cols-4 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Std Rate (₹)</Label>
                <Input
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  value={standardRate}
                  onChange={(e) => setStandardRate(e.target.value === '' ? '' : parseFloat(e.target.value))}
                  className="h-8 text-xs"
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">HSN/SAC Code</Label>
                <Input
                  maxLength={30}
                  placeholder="e.g. 8471"
                  value={hsnSacCode}
                  onChange={(e) => setHsnSacCode(e.target.value)}
                  className="h-8 text-xs"
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">Useful Life (Mos)</Label>
                <Input
                  type="number"
                  min="0"
                  placeholder="Months"
                  value={usefulLifeMonths}
                  onChange={(e) => setUsefulLifeMonths(e.target.value === '' ? '' : parseInt(e.target.value))}
                  className="h-8 text-xs"
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">Warranty (Mos)</Label>
                <Input
                  type="number"
                  min="0"
                  placeholder="Months"
                  value={warrantyMonths}
                  onChange={(e) => setWarrantyMonths(e.target.value === '' ? '' : parseInt(e.target.value))}
                  className="h-8 text-xs"
                />
              </div>
            </div>

            {/* Boolean Flags */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-3 rounded-lg border border-border bg-muted/20">
              <div className="flex items-center space-x-2">
                <Switch id="assetReq" checked={assetRequired} onCheckedChange={(val) => {
                  if (val && trackingType !== 'SERIAL') {
                    setTrackingType('SERIAL');
                  }
                  setAssetRequired(val);
                }} />
                <Label htmlFor="assetReq" className="text-xs cursor-pointer">
                  Asset Required
                </Label>
              </div>

              <div className="flex items-center space-x-2">
                <Switch id="warrantyApp" checked={warrantyApplicable} onCheckedChange={setWarrantyApplicable} />
                <Label htmlFor="warrantyApp" className="text-xs cursor-pointer">
                  Warranty App
                </Label>
              </div>

              <div className="flex items-center space-x-2">
                <Switch id="returnable" checked={returnable} onCheckedChange={setReturnable} />
                <Label htmlFor="returnable" className="text-xs cursor-pointer">
                  Returnable
                </Label>
              </div>

              <div className="flex items-center space-x-2">
                <Switch id="expiryTracking" checked={expiryTracking} onCheckedChange={setExpiryTracking} />
                <Label htmlFor="expiryTracking" className="text-xs cursor-pointer">
                  Expiry Track
                </Label>
              </div>
            </div>

            {assetRequired && (
              <div className="flex items-center gap-1.5 text-[11px] text-blue-700 dark:text-blue-300 bg-blue-50 dark:bg-blue-950/40 p-2 rounded border border-blue-200">
                <AlertCircle className="size-3.5 shrink-0" />
                <span>Asset-required items will generate digital asset identities on GRN receipt and require SERIAL tracking.</span>
              </div>
            )}

            <div className="space-y-1.5">
              <Label className="text-xs">Short Description</Label>
              <Input
                maxLength={500}
                placeholder="Brief item specification summary"
                value={shortDescription}
                onChange={(e) => setShortDescription(e.target.value)}
                className="h-8 text-xs"
              />
            </div>

            <DialogFooter className="pt-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setIsCreateOpen(false)}
              >
                Cancel
              </Button>
              <Button type="submit" size="sm" disabled={createMutation.isPending}>
                {createMutation.isPending ? 'Saving...' : 'Register Item'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
