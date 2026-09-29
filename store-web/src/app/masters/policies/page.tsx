'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import {
  ItemStorePolicyResponse,
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
import { Plus, Sliders, RefreshCw, AlertTriangle } from 'lucide-react';
import { toast } from 'sonner';

const VALUATION_METHODS = ['WEIGHTED_AVG', 'FIFO', 'STANDARD_COST'];

export default function PoliciesPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  // Form State
  const [itemId, setItemId] = useState('');
  const [storeId, setStoreId] = useState('');
  const [minStockQty, setMinStockQty] = useState<number>(0);
  const [maxStockQty, setMaxStockQty] = useState<number | ''>('');
  const [reorderLevelQty, setReorderLevelQty] = useState<number>(0);
  const [reorderQty, setReorderQty] = useState<number>(0);
  const [allowNegativeStock, setAllowNegativeStock] = useState(false);
  const [valuationMethod, setValuationMethod] = useState('WEIGHTED_AVG');
  const [defaultLocationId, setDefaultLocationId] = useState<string>('none');

  // Lookups
  const { data: itemsData } = useQuery({
    queryKey: ['items-lookup'],
    queryFn: () => api.get<PageResponse<ItemResponse>>('/api/store/items?size=100'),
  });

  const { data: storesData } = useQuery({
    queryKey: ['stores-lookup'],
    queryFn: () => api.get<PageResponse<StoreSiteResponse>>('/api/store/stores?size=100'),
  });

  const { data: locationsData } = useQuery({
    queryKey: ['locations-lookup', storeId],
    queryFn: () => api.get<PageResponse<StorageLocationResponse>>(`/api/store/locations?storeId=${storeId}&size=100`),
    enabled: !!storeId,
  });

  // Fetch Policies
  const { data, isLoading, refetch } = useQuery({
    queryKey: ['policies', page],
    queryFn: async () => {
      const params = new URLSearchParams({
        page: page.toString(),
        size: '15',
      });
      return api.get<PageResponse<ItemStorePolicyResponse>>(
        `/api/store/item-store-policies?${params.toString()}`
      );
    },
  });

  // Create Policy mutation
  const createMutation = useMutation({
    mutationFn: async () => {
      return api.post<ItemStorePolicyResponse>('/api/store/item-store-policies', {
        itemId,
        storeId,
        minStockQty: Number(minStockQty),
        maxStockQty: maxStockQty !== '' ? Number(maxStockQty) : undefined,
        reorderLevelQty: Number(reorderLevelQty),
        reorderQty: Number(reorderQty),
        allowNegativeStock,
        valuationMethod,
        defaultLocationId: defaultLocationId === 'none' ? undefined : defaultLocationId,
      });
    },
    onSuccess: () => {
      toast.success('Item-store policy configured successfully');
      setIsCreateOpen(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['policies'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to save policy');
    },
  });

  // Toggle status
  const statusMutation = useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      return api.patch<ItemStorePolicyResponse>(`/api/store/item-store-policies/${id}/status`, { active });
    },
    onSuccess: () => {
      toast.success('Policy status updated');
      queryClient.invalidateQueries({ queryKey: ['policies'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to update policy status');
    },
  });

  const resetForm = () => {
    setItemId('');
    setStoreId('');
    setMinStockQty(0);
    setMaxStockQty('');
    setReorderLevelQty(0);
    setReorderQty(0);
    setAllowNegativeStock(false);
    setValuationMethod('WEIGHTED_AVG');
    setDefaultLocationId('none');
  };

  const columns: ColumnDef<ItemStorePolicyResponse>[] = [
    {
      header: 'Item',
      cell: (row) => (
        <div>
          <p className="font-semibold text-foreground text-xs">{row.itemName}</p>
          <p className="font-mono text-[11px] text-muted-foreground">{row.itemCode}</p>
        </div>
      ),
    },
    {
      header: 'Store Site',
      cell: (row) => (
        <div>
          <p className="font-medium text-foreground text-xs">{row.storeName}</p>
          <p className="font-mono text-[11px] text-muted-foreground">{row.storeCode}</p>
        </div>
      ),
    },
    {
      header: 'Stock Levels',
      cell: (row) => (
        <div className="text-[11px] space-y-0.5 font-mono">
          <div>Min: {row.minStockQty} | Max: {row.maxStockQty != null ? row.maxStockQty : '∞'}</div>
          <div className="text-blue-600 dark:text-blue-400">Reorder Level: {row.reorderLevelQty} (Qty: {row.reorderQty})</div>
        </div>
      ),
    },
    {
      header: 'Valuation',
      accessorKey: 'valuationMethod',
      cell: (row) => (
        <span className="rounded bg-muted px-2 py-0.5 text-[10px] font-mono font-medium">
          {row.valuationMethod}
        </span>
      ),
    },
    {
      header: 'Negative Stock',
      cell: (row) => (
        <span className={`text-xs font-semibold ${row.allowNegativeStock ? 'text-amber-600' : 'text-emerald-600'}`}>
          {row.allowNegativeStock ? 'Allowed' : 'Blocked'}
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
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-card p-4 rounded-lg border border-border">
        <div>
          <h2 className="text-base font-semibold flex items-center gap-2">
            <Sliders className="size-4 text-primary" />
            Item–Store Replenishment & Valuation Policies
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Defines store-specific minimum/maximum stock constraints, reorder points, and costing rules.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()} className="gap-1 text-xs">
            <RefreshCw className="size-3.5" />
            Refresh
          </Button>
          <Button size="sm" onClick={() => setIsCreateOpen(true)} className="gap-1 text-xs">
            <Plus className="size-3.5" />
            New Policy
          </Button>
        </div>
      </div>

      {/* Main Table */}
      <DataTable
        columns={columns}
        data={data?.content || []}
        isLoading={isLoading}
        searchPlaceholder="Filter policies..."
        page={page}
        totalPages={data?.totalPages || 1}
        totalElements={data?.totalElements}
        onPageChange={setPage}
      />

      {/* Create Policy Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="sm:max-w-[560px]">
          <DialogHeader>
            <DialogTitle>Configure Item–Store Policy</DialogTitle>
            <DialogDescription className="text-xs">
              Set store-level inventory thresholds, reorder parameters, and valuation methodology.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              // Validation
              if (Number(minStockQty) > Number(reorderLevelQty)) {
                toast.error('Policy validation: Minimum stock quantity cannot exceed Reorder level');
                return;
              }
              if (maxStockQty !== '' && Number(maxStockQty) < Number(reorderLevelQty)) {
                toast.error('Policy validation: Reorder level cannot exceed Maximum stock quantity');
                return;
              }
              createMutation.mutate();
            }}
            className="space-y-4 py-2"
          >
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Catalog Item *</Label>
                <Select value={itemId} onValueChange={setItemId}>
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue placeholder="Select Item" />
                  </SelectTrigger>
                  <SelectContent>
                    {itemsData?.content?.map((it) => (
                      <SelectItem key={it.id} value={it.id} className="text-xs">
                        {it.itemName} ({it.itemCode})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label className="text-xs">Store Site *</Label>
                <Select
                  value={storeId}
                  onValueChange={(val) => {
                    setStoreId(val);
                    setDefaultLocationId('none');
                  }}
                >
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue placeholder="Select Store" />
                  </SelectTrigger>
                  <SelectContent>
                    {storesData?.content?.map((s) => (
                      <SelectItem key={s.id} value={s.id} className="text-xs">
                        {s.storeName} ({s.storeCode})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {/* Thresholds */}
            <div className="grid grid-cols-4 gap-2.5">
              <div className="space-y-1">
                <Label className="text-[11px]">Min Stock Qty *</Label>
                <Input
                  type="number"
                  min="0"
                  step="0.001"
                  required
                  value={minStockQty}
                  onChange={(e) => setMinStockQty(parseFloat(e.target.value) || 0)}
                  className="h-8 text-xs"
                />
              </div>
              <div className="space-y-1">
                <Label className="text-[11px]">Max Stock Qty</Label>
                <Input
                  type="number"
                  min="0"
                  step="0.001"
                  placeholder="Optional"
                  value={maxStockQty}
                  onChange={(e) => setMaxStockQty(e.target.value === '' ? '' : parseFloat(e.target.value))}
                  className="h-8 text-xs"
                />
              </div>
              <div className="space-y-1">
                <Label className="text-[11px]">Reorder Level *</Label>
                <Input
                  type="number"
                  min="0"
                  step="0.001"
                  required
                  value={reorderLevelQty}
                  onChange={(e) => setReorderLevelQty(parseFloat(e.target.value) || 0)}
                  className="h-8 text-xs"
                />
              </div>
              <div className="space-y-1">
                <Label className="text-[11px]">Reorder Qty *</Label>
                <Input
                  type="number"
                  min="0"
                  step="0.001"
                  required
                  value={reorderQty}
                  onChange={(e) => setReorderQty(parseFloat(e.target.value) || 0)}
                  className="h-8 text-xs"
                />
              </div>
            </div>

            {/* Default Location & Valuation */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Valuation Method *</Label>
                <Select value={valuationMethod} onValueChange={setValuationMethod}>
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {VALUATION_METHODS.map((v) => (
                      <SelectItem key={v} value={v} className="text-xs">
                        {v}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label className="text-xs">Default Storage Location</Label>
                <Select
                  value={defaultLocationId}
                  onValueChange={setDefaultLocationId}
                  disabled={!storeId}
                >
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue placeholder="None" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none" className="text-xs font-semibold">
                      None
                    </SelectItem>
                    {locationsData?.content?.map((loc) => (
                      <SelectItem key={loc.id} value={loc.id} className="text-xs">
                        {loc.locationCode} - {loc.locationName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="flex items-center space-x-2 rounded-lg border border-border p-3 bg-muted/20">
              <Switch
                id="allowNegative"
                checked={allowNegativeStock}
                onCheckedChange={setAllowNegativeStock}
              />
              <div>
                <Label htmlFor="allowNegative" className="text-xs font-semibold cursor-pointer">
                  Allow Negative Stock
                </Label>
                <p className="text-[11px] text-muted-foreground">
                  Default is blocked. NICSI ERP policy requires physical stock presence before issue.
                </p>
              </div>
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
                {createMutation.isPending ? 'Saving...' : 'Save Policy'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
