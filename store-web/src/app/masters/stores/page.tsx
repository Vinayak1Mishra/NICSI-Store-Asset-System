'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { StoreSiteResponse, PageResponse } from '@/types/master';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { StatusBadge } from '@/components/common/status-badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
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
import { Plus, Store, RefreshCw } from 'lucide-react';
import { toast } from 'sonner';

const STORE_TYPES = ['GENERAL', 'IT', 'CONSUMABLE', 'ASSET', 'QUARANTINE', 'SCRAP', 'OTHER'];

export default function StoresPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [storeTypeFilter, setStoreTypeFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  // Form State
  const [storeCode, setStoreCode] = useState('');
  const [storeName, setStoreName] = useState('');
  const [storeType, setStoreType] = useState('GENERAL');
  const [address, setAddress] = useState('');
  const [officeCode, setOfficeCode] = useState('');
  const [officeName, setOfficeName] = useState('');

  // Fetch Stores
  const { data, isLoading, refetch } = useQuery({
    queryKey: ['stores', page, search, storeTypeFilter],
    queryFn: async () => {
      const params = new URLSearchParams({
        page: page.toString(),
        size: '15',
      });
      if (search) params.append('search', search);
      if (storeTypeFilter !== 'ALL') params.append('storeType', storeTypeFilter);
      return api.get<PageResponse<StoreSiteResponse>>(`/api/store/stores?${params.toString()}`);
    },
  });

  // Create Store mutation
  const createMutation = useMutation({
    mutationFn: async () => {
      return api.post<StoreSiteResponse>('/api/store/stores', {
        storeCode: storeCode.trim().toUpperCase(),
        storeName: storeName.trim(),
        storeType,
        address: address.trim() || undefined,
        officeCodeSnapshot: officeCode.trim() || undefined,
        officeNameSnapshot: officeName.trim() || undefined,
      });
    },
    onSuccess: (store) => {
      toast.success(`Store ${store.storeName} (${store.storeCode}) created successfully`);
      setIsCreateOpen(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['stores'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create store site');
    },
  });

  // Toggle active status
  const statusMutation = useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      return api.patch<StoreSiteResponse>(`/api/store/stores/${id}/status`, { active });
    },
    onSuccess: () => {
      toast.success('Store status updated');
      queryClient.invalidateQueries({ queryKey: ['stores'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to update store status');
    },
  });

  const resetForm = () => {
    setStoreCode('');
    setStoreName('');
    setStoreType('GENERAL');
    setAddress('');
    setOfficeCode('');
    setOfficeName('');
  };

  const columns: ColumnDef<StoreSiteResponse>[] = [
    {
      header: 'Store Code',
      accessorKey: 'storeCode',
      cell: (row) => <span className="font-semibold text-foreground font-mono">{row.storeCode}</span>,
    },
    {
      header: 'Store Name',
      accessorKey: 'storeName',
      cell: (row) => (
        <div>
          <p className="font-medium text-foreground">{row.storeName}</p>
          <p className="text-[11px] text-muted-foreground">{row.address || 'No address specified'}</p>
        </div>
      ),
    },
    {
      header: 'Store Type',
      accessorKey: 'storeType',
      cell: (row) => (
        <span className="rounded bg-muted px-2 py-0.5 text-[10px] font-mono font-medium">
          {row.storeType}
        </span>
      ),
    },
    {
      header: 'Office / Campus',
      cell: (row) => (
        <span className="text-xs text-muted-foreground">
          {row.officeNameSnapshot || row.officeCodeSnapshot || 'HQ Central'}
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
            <Store className="size-4 text-primary" />
            Physical Stores & Operating Sites
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Physical stores across NICSI headquarter, data centers, regional offices and project facilities.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()} className="gap-1 text-xs">
            <RefreshCw className="size-3.5" />
            Refresh
          </Button>
          <Button size="sm" onClick={() => setIsCreateOpen(true)} className="gap-1 text-xs">
            <Plus className="size-3.5" />
            New Store
          </Button>
        </div>
      </div>

      {/* Main Table */}
      <DataTable
        columns={columns}
        data={data?.content || []}
        isLoading={isLoading}
        searchPlaceholder="Search store code or name..."
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
          <Select value={storeTypeFilter} onValueChange={setStoreTypeFilter}>
            <SelectTrigger className="h-9 text-xs w-[160px] bg-background">
              <SelectValue placeholder="All Store Types" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL" className="text-xs">
                All Store Types
              </SelectItem>
              {STORE_TYPES.map((t) => (
                <SelectItem key={t} value={t} className="text-xs">
                  {t}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        }
      />

      {/* Create Store Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle>Register Store Site</DialogTitle>
            <DialogDescription className="text-xs">
              Add a new physical storage facility or operating site.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              createMutation.mutate();
            }}
            className="space-y-3 py-2"
          >
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Store Code *</Label>
                <Input
                  required
                  maxLength={30}
                  placeholder="e.g. HQ-IT-01"
                  value={storeCode}
                  onChange={(e) => setStoreCode(e.target.value)}
                  className="h-8 text-xs font-mono uppercase"
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">Store Type *</Label>
                <Select value={storeType} onValueChange={setStoreType}>
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {STORE_TYPES.map((t) => (
                      <SelectItem key={t} value={t} className="text-xs">
                        {t}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs">Store Name *</Label>
              <Input
                required
                maxLength={150}
                placeholder="e.g. NICSI HQ Central IT Store"
                value={storeName}
                onChange={(e) => setStoreName(e.target.value)}
                className="h-8 text-xs"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Office Code</Label>
                <Input
                  maxLength={50}
                  placeholder="e.g. HQ-DELHI"
                  value={officeCode}
                  onChange={(e) => setOfficeCode(e.target.value)}
                  className="h-8 text-xs font-mono uppercase"
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">Office Name</Label>
                <Input
                  maxLength={200}
                  placeholder="e.g. NBCC Tower, Bhikaji Cama Place"
                  value={officeName}
                  onChange={(e) => setOfficeName(e.target.value)}
                  className="h-8 text-xs"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs">Address / Physical Location</Label>
              <Input
                placeholder="Floor, wing, room number, campus address"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
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
                {createMutation.isPending ? 'Saving...' : 'Register Store'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
