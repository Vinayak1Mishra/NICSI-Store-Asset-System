'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { UomResponse, PageResponse } from '@/types/master';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { StatusBadge } from '@/components/common/status-badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Plus, Scale, RefreshCw } from 'lucide-react';
import { toast } from 'sonner';

const UOM_TYPES = ['COUNT', 'LENGTH', 'WEIGHT', 'VOLUME', 'TIME', 'LICENSE', 'BULK', 'OTHER'];

export default function UomPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  // Form state
  const [uomCode, setUomCode] = useState('');
  const [uomName, setUomName] = useState('');
  const [uomType, setUomType] = useState('COUNT');
  const [decimalAllowed, setDecimalAllowed] = useState(false);
  const [decimalScale, setDecimalScale] = useState(0);
  const [description, setDescription] = useState('');

  // Fetch UOMs
  const { data, isLoading, refetch } = useQuery({
    queryKey: ['uoms', page, search],
    queryFn: async () => {
      const params = new URLSearchParams({
        page: page.toString(),
        size: '25',
      });
      if (search) params.append('search', search);
      return api.get<PageResponse<UomResponse>>(`/api/store/uoms?${params.toString()}`);
    },
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: async () => {
      return api.post<UomResponse>('/api/store/uoms', {
        uomCode: uomCode.trim().toUpperCase(),
        uomName: uomName.trim(),
        uomType,
        decimalAllowed,
        decimalScale: decimalAllowed ? Number(decimalScale) : 0,
        description: description.trim() || undefined,
      });
    },
    onSuccess: (newUom) => {
      toast.success(`UOM ${newUom.uomCode} created successfully`);
      setIsCreateOpen(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['uoms'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create UOM');
    },
  });

  // Toggle active status mutation
  const statusMutation = useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      return api.patch<UomResponse>(`/api/store/uoms/${id}/status`, { active });
    },
    onSuccess: () => {
      toast.success('UOM status updated');
      queryClient.invalidateQueries({ queryKey: ['uoms'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to update UOM status');
    },
  });

  const resetForm = () => {
    setUomCode('');
    setUomName('');
    setUomType('COUNT');
    setDecimalAllowed(false);
    setDecimalScale(0);
    setDescription('');
  };

  const columns: ColumnDef<UomResponse>[] = [
    {
      header: 'UOM Code',
      accessorKey: 'uomCode',
      cell: (row) => <span className="font-semibold text-foreground">{row.uomCode}</span>,
    },
    {
      header: 'UOM Name',
      accessorKey: 'uomName',
    },
    {
      header: 'Type',
      accessorKey: 'uomType',
      cell: (row) => (
        <span className="rounded bg-muted px-2 py-0.5 text-[11px] font-mono font-medium">
          {row.uomType}
        </span>
      ),
    },
    {
      header: 'Decimals',
      cell: (row) => (
        <span className="text-xs">
          {row.decimalAllowed ? (
            <span className="text-blue-600 font-medium">Allowed (Scale: {row.decimalScale})</span>
          ) : (
            <span className="text-muted-foreground">None (0)</span>
          )}
        </span>
      ),
    },
    {
      header: 'Description',
      accessorKey: 'description',
      cell: (row) => <span className="text-muted-foreground truncate max-w-xs">{row.description || '—'}</span>,
    },
    {
      header: 'Status',
      cell: (row) => <StatusBadge status={row.active} />,
    },
    {
      header: 'Actions',
      cell: (row) => (
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            className="h-7 text-xs"
            onClick={() => statusMutation.mutate({ id: row.id, active: !row.active })}
          >
            {row.active ? 'Deactivate' : 'Activate'}
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      {/* Intro banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-card p-4 rounded-lg border border-border">
        <div>
          <h2 className="text-base font-semibold flex items-center gap-2">
            <Scale className="size-4 text-primary" />
            Units of Measure (UOM Master)
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Pre-seeded with 22 standard government ERP units (COUNT, LENGTH, WEIGHT, VOLUME, LICENSE).
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()} className="gap-1 text-xs">
            <RefreshCw className="size-3.5" />
            Refresh
          </Button>
          <Button size="sm" onClick={() => setIsCreateOpen(true)} className="gap-1 text-xs">
            <Plus className="size-3.5" />
            New UOM
          </Button>
        </div>
      </div>

      {/* Main Table */}
      <DataTable
        columns={columns}
        data={data?.content || []}
        isLoading={isLoading}
        searchPlaceholder="Search UOM code or name..."
        searchValue={search}
        onSearchChange={(v) => {
          setSearch(v);
          setPage(0);
        }}
        page={page}
        totalPages={data?.totalPages || 1}
        totalElements={data?.totalElements}
        onPageChange={setPage}
      />

      {/* Create UOM Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle>Add New Unit of Measure</DialogTitle>
            <DialogDescription className="text-xs">
              Define a new measuring unit with decimal precision constraints.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              createMutation.mutate();
            }}
            className="space-y-4 py-2"
          >
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">UOM Code *</Label>
                <Input
                  required
                  maxLength={20}
                  placeholder="e.g. PACK10"
                  value={uomCode}
                  onChange={(e) => setUomCode(e.target.value)}
                  className="h-8 text-xs font-mono uppercase"
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">UOM Name *</Label>
                <Input
                  required
                  maxLength={80}
                  placeholder="e.g. Pack of 10"
                  value={uomName}
                  onChange={(e) => setUomName(e.target.value)}
                  className="h-8 text-xs"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs">Unit Type *</Label>
              <Select value={uomType} onValueChange={setUomType}>
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue placeholder="Select type" />
                </SelectTrigger>
                <SelectContent>
                  {UOM_TYPES.map((t) => (
                    <SelectItem key={t} value={t} className="text-xs">
                      {t}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-3 items-center rounded-lg border border-border p-3 bg-muted/20">
              <div className="flex items-center space-x-2">
                <Switch
                  id="decimalAllowed"
                  checked={decimalAllowed}
                  onCheckedChange={(val) => {
                    setDecimalAllowed(val);
                    if (!val) setDecimalScale(0);
                  }}
                />
                <Label htmlFor="decimalAllowed" className="text-xs font-medium cursor-pointer">
                  Decimal Allowed
                </Label>
              </div>

              {decimalAllowed && (
                <div className="space-y-1">
                  <Label className="text-xs">Decimal Scale (0-6)</Label>
                  <Input
                    type="number"
                    min={0}
                    max={6}
                    value={decimalScale}
                    onChange={(e) => setDecimalScale(parseInt(e.target.value) || 0)}
                    className="h-7 text-xs"
                  />
                </div>
              )}
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs">Description</Label>
              <Input
                maxLength={255}
                placeholder="Optional description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
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
                {createMutation.isPending ? 'Saving...' : 'Create UOM'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
