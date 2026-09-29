'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import {
  SoftwareLicenseResponse,
  SoftwareLicenseSummaryResponse,
  CreateSoftwareLicenseRequest,
  AllocateSoftwareLicenseRequest,
} from '@/types/license';
import { ItemResponse, PageResponse } from '@/types/master';
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
  ShieldCheck,
  Plus,
  Eye,
  Calendar,
  Layers,
  RotateCcw,
  UserCheck,
  Laptop,
  Server,
  Key,
  Users,
  CheckCircle2,
  Trash2,
} from 'lucide-react';
import { toast } from 'sonner';

export default function SoftwareLicensesPage() {
  const queryClient = useQueryClient();
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [typeFilter, setTypeFilter] = useState('ALL');

  // Modals state
  const [createOpen, setCreateOpen] = useState(false);
  const [viewLicenseId, setViewLicenseId] = useState<string | null>(null);
  const [allocateLicenseId, setAllocateLicenseId] = useState<string | null>(null);

  // Create form state
  const [itemId, setItemId] = useState('');
  const [licenseCode, setLicenseCode] = useState('');
  const [licenseType, setLicenseType] = useState('USER');
  const [entitlementQty, setEntitlementQty] = useState<number>(10);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [poNumber, setPoNumber] = useState('');
  const [secretKeyRef, setSecretKeyRef] = useState('');

  // Allocate form state
  const [allocType, setAllocType] = useState<'USER' | 'DEVICE' | 'SERVER' | 'PROJECT'>('USER');
  const [allocUserId, setAllocUserId] = useState('');
  const [allocServerId, setAllocServerId] = useState('');
  const [allocQty, setAllocQty] = useState<number>(1);
  const [allocRemarks, setAllocRemarks] = useState('');

  // Queries
  const { data: licensesPage, isLoading } = useQuery<PageResponse<SoftwareLicenseSummaryResponse>>({
    queryKey: ['licenses', statusFilter, typeFilter, searchTerm],
    queryFn: () => {
      const params = new URLSearchParams();
      if (statusFilter !== 'ALL') params.set('status', statusFilter);
      if (typeFilter !== 'ALL') params.set('licenseType', typeFilter);
      if (searchTerm) params.set('search', searchTerm);
      params.set('size', '50');
      return api.get(`/api/store/licenses?${params.toString()}`);
    },
  });

  const { data: itemsPage } = useQuery<PageResponse<ItemResponse>>({
    queryKey: ['items-software-list'],
    queryFn: () => api.get('/api/store/items?size=100'),
  });

  const { data: selectedLicense, isLoading: loadingDetail } = useQuery<SoftwareLicenseResponse>({
    queryKey: ['license-detail', viewLicenseId],
    queryFn: () => api.get(`/api/store/licenses/${viewLicenseId}`),
    enabled: Boolean(viewLicenseId),
  });

  // Mutations
  const createMutation = useMutation({
    mutationFn: (payload: CreateSoftwareLicenseRequest) => api.post('/api/store/licenses', payload),
    onSuccess: () => {
      toast.success('Software license entitlement registered');
      queryClient.invalidateQueries({ queryKey: ['licenses'] });
      resetCreateForm();
      setCreateOpen(false);
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to register license');
    },
  });

  const allocateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: AllocateSoftwareLicenseRequest }) =>
      api.post(`/api/store/licenses/${id}/allocate`, payload),
    onSuccess: () => {
      toast.success('License seats allocated successfully');
      queryClient.invalidateQueries({ queryKey: ['licenses'] });
      if (viewLicenseId) queryClient.invalidateQueries({ queryKey: ['license-detail', viewLicenseId] });
      setAllocateLicenseId(null);
      resetAllocForm();
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to allocate license');
    },
  });

  const releaseMutation = useMutation({
    mutationFn: ({ licenseId, allocId }: { licenseId: string; allocId: string }) =>
      api.post(`/api/store/licenses/${licenseId}/allocations/${allocId}/release`),
    onSuccess: () => {
      toast.success('License seat released successfully');
      queryClient.invalidateQueries({ queryKey: ['licenses'] });
      if (viewLicenseId) queryClient.invalidateQueries({ queryKey: ['license-detail', viewLicenseId] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to release allocation');
    },
  });

  const resetCreateForm = () => {
    setItemId('');
    setLicenseCode('');
    setLicenseType('USER');
    setEntitlementQty(10);
    setStartDate('');
    setEndDate('');
    setPoNumber('');
    setSecretKeyRef('');
  };

  const resetAllocForm = () => {
    setAllocType('USER');
    setAllocUserId('');
    setAllocServerId('');
    setAllocQty(1);
    setAllocRemarks('');
  };

  const handleCreateSubmit = () => {
    if (!itemId || !licenseCode || entitlementQty <= 0) {
      toast.error('Item, license code, and positive entitlement quantity required');
      return;
    }

    createMutation.mutate({
      itemId,
      licenseCode: licenseCode.trim(),
      licenseType,
      entitlementQty,
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      poNumberSnapshot: poNumber || undefined,
      licenseKeySecretRef: secretKeyRef || undefined,
    });
  };

  const allLicenses = licensesPage?.content || [];
  const totalCount = licensesPage?.totalElements || allLicenses.length;
  const activeCount = allLicenses.filter((l) => l.status === 'ACTIVE').length;
  const totalEntitled = allLicenses.reduce((acc, l) => acc + Number(l.entitlementQty || 0), 0);
  const totalAllocated = allLicenses.reduce((acc, l) => acc + Number(l.allocatedQty || 0), 0);
  const totalAvailable = allLicenses.reduce((acc, l) => acc + Number(l.availableQty || 0), 0);

  const columns: ColumnDef<SoftwareLicenseSummaryResponse>[] = [
    {
      header: 'License Code',
      accessorKey: 'licenseCode',
      cell: (row) => (
        <span className="font-semibold text-primary font-mono text-xs">
          {row.licenseCode}
        </span>
      ),
    },
    {
      header: 'Software Item',
      cell: (row) => (
        <div className="flex flex-col text-xs">
          <span className="font-medium text-foreground">{row.itemName}</span>
          <span className="text-[10px] text-muted-foreground font-mono">{row.itemCode}</span>
        </div>
      ),
    },
    {
      header: 'Type',
      accessorKey: 'licenseType',
      cell: (row) => (
        <Badge variant="outline" className="text-[10px] uppercase font-mono">
          {row.licenseType}
        </Badge>
      ),
    },
    {
      header: 'Entitlement',
      cell: (row) => (
        <span className="font-mono text-xs font-semibold">{row.entitlementQty}</span>
      ),
    },
    {
      header: 'Allocated',
      cell: (row) => (
        <span className="font-mono text-xs text-amber-600 font-semibold">{row.allocatedQty}</span>
      ),
    },
    {
      header: 'Available',
      cell: (row) => (
        <span className="font-mono text-xs text-emerald-600 font-bold">{row.availableQty}</span>
      ),
    },
    {
      header: 'Validity',
      cell: (row) => (
        <div className="text-[11px] text-muted-foreground">
          {row.startDate || '—'} {row.endDate ? `to ${row.endDate}` : ''}
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
            title="View Allocations"
            onClick={() => setViewLicenseId(row.id)}
          >
            <Eye className="h-4 w-4" />
          </Button>

          {row.status === 'ACTIVE' && Number(row.availableQty) > 0 && (
            <Button
              size="sm"
              variant="outline"
              className="h-8 px-2.5 text-xs gap-1 border-primary/30 text-primary hover:bg-primary/10"
              title="Allocate Seats"
              onClick={() => {
                resetAllocForm();
                setAllocateLicenseId(row.id);
              }}
            >
              <UserCheck className="h-3.5 w-3.5" />
              <span>Allocate</span>
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
            <ShieldCheck className="h-7 w-7 text-primary" />
            Software Licences & Entitlements
          </h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Manage software entitlements, license keys, and user/device seat allocations.
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
          <span>New Licence</span>
        </Button>
      </div>

      {/* Metrics Cards */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3.5">
        <Card className="shadow-none border-border/80">
          <CardHeader className="pb-1.5 pt-3.5 px-4">
            <CardTitle className="text-xs font-medium text-muted-foreground uppercase tracking-wider flex items-center justify-between">
              Total Licences
              <ShieldCheck className="h-3.5 w-3.5 text-muted-foreground" />
            </CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3.5">
            <div className="text-xl font-bold text-foreground">{totalCount}</div>
          </CardContent>
        </Card>

        <Card className="shadow-none border-border/80">
          <CardHeader className="pb-1.5 pt-3.5 px-4">
            <CardTitle className="text-xs font-medium text-blue-600 uppercase tracking-wider flex items-center justify-between">
              Active Licences
              <CheckCircle2 className="h-3.5 w-3.5 text-blue-500" />
            </CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3.5">
            <div className="text-xl font-bold text-blue-600">{activeCount}</div>
          </CardContent>
        </Card>

        <Card className="shadow-none border-border/80">
          <CardHeader className="pb-1.5 pt-3.5 px-4">
            <CardTitle className="text-xs font-medium text-purple-600 uppercase tracking-wider flex items-center justify-between">
              Entitlements
              <Layers className="h-3.5 w-3.5 text-purple-500" />
            </CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3.5">
            <div className="text-xl font-bold text-purple-600">{totalEntitled}</div>
          </CardContent>
        </Card>

        <Card className="shadow-none border-border/80">
          <CardHeader className="pb-1.5 pt-3.5 px-4">
            <CardTitle className="text-xs font-medium text-amber-600 uppercase tracking-wider flex items-center justify-between">
              Allocated Seats
              <Users className="h-3.5 w-3.5 text-amber-500" />
            </CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3.5">
            <div className="text-xl font-bold text-amber-600">{totalAllocated}</div>
          </CardContent>
        </Card>

        <Card className="shadow-none border-border/80">
          <CardHeader className="pb-1.5 pt-3.5 px-4">
            <CardTitle className="text-xs font-medium text-emerald-600 uppercase tracking-wider flex items-center justify-between">
              Available Seats
              <Key className="h-3.5 w-3.5 text-emerald-500" />
            </CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3.5">
            <div className="text-xl font-bold text-emerald-600">{totalAvailable}</div>
          </CardContent>
        </Card>
      </div>

      {/* Filter and Table */}
      <Card className="shadow-none border-border/80">
        <CardContent className="p-4 space-y-4">
          <div className="flex flex-col md:flex-row items-center justify-between gap-3">
            <div className="flex items-center gap-2.5 w-full md:w-auto">
              <Input
                placeholder="Search Licence Code / Item..."
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
                  <SelectItem value="ACTIVE">Active</SelectItem>
                  <SelectItem value="EXPIRED">Expired</SelectItem>
                  <SelectItem value="SUSPENDED">Suspended</SelectItem>
                  <SelectItem value="CANCELLED">Cancelled</SelectItem>
                </SelectContent>
              </Select>
              <Select value={typeFilter} onValueChange={setTypeFilter}>
                <SelectTrigger className="w-36 h-9 text-xs">
                  <SelectValue placeholder="Type" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Types</SelectItem>
                  <SelectItem value="USER">User</SelectItem>
                  <SelectItem value="DEVICE">Device</SelectItem>
                  <SelectItem value="SERVER">Server</SelectItem>
                  <SelectItem value="CONCURRENT">Concurrent</SelectItem>
                  <SelectItem value="SUBSCRIPTION">Subscription</SelectItem>
                  <SelectItem value="PERPETUAL">Perpetual</SelectItem>
                  <SelectItem value="SITE">Site</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => queryClient.invalidateQueries({ queryKey: ['licenses'] })}
              className="h-9 px-2.5 text-xs text-muted-foreground gap-1.5"
            >
              <RotateCcw className="h-3.5 w-3.5" />
              <span>Refresh</span>
            </Button>
          </div>

          <DataTable
            columns={columns}
            data={allLicenses}
            isLoading={isLoading}
          />
        </CardContent>
      </Card>

      {/* CREATE LICENCE DIALOG */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base">
              <ShieldCheck className="h-5 w-5 text-primary" />
              Register Software Licence Entitlement
            </DialogTitle>
            <DialogDescription className="text-xs">
              Add a newly procured software product entitlement and license pool.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3 py-2 text-xs">
            <div className="space-y-1">
              <Label className="text-xs font-semibold">Software Item *</Label>
              <Select value={itemId} onValueChange={setItemId}>
                <SelectTrigger className="h-9 text-xs">
                  <SelectValue placeholder="Select software item..." />
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

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label className="text-xs font-semibold">Licence Code *</Label>
                <Input
                  placeholder="e.g. LIC-WIN-2026-001"
                  value={licenseCode}
                  onChange={(e) => setLicenseCode(e.target.value)}
                  className="h-9 text-xs font-mono"
                />
              </div>
              <div className="space-y-1">
                <Label className="text-xs font-semibold">Licence Type *</Label>
                <Select value={licenseType} onValueChange={setLicenseType}>
                  <SelectTrigger className="h-9 text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="USER">User (Named)</SelectItem>
                    <SelectItem value="DEVICE">Device</SelectItem>
                    <SelectItem value="SERVER">Server</SelectItem>
                    <SelectItem value="CONCURRENT">Concurrent</SelectItem>
                    <SelectItem value="SUBSCRIPTION">Subscription</SelectItem>
                    <SelectItem value="PERPETUAL">Perpetual</SelectItem>
                    <SelectItem value="SITE">Site</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label className="text-xs font-semibold">Entitlement Seats / Qty *</Label>
                <Input
                  type="number"
                  min="1"
                  value={entitlementQty}
                  onChange={(e) => setEntitlementQty(Number(e.target.value))}
                  className="h-9 text-xs font-mono"
                />
              </div>
              <div className="space-y-1">
                <Label className="text-xs font-semibold">PO Number Snapshot</Label>
                <Input
                  placeholder="e.g. PO/2026/0014"
                  value={poNumber}
                  onChange={(e) => setPoNumber(e.target.value)}
                  className="h-9 text-xs font-mono"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label className="text-xs font-semibold">Start Date</Label>
                <Input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="h-9 text-xs"
                />
              </div>
              <div className="space-y-1">
                <Label className="text-xs font-semibold">End / Expiry Date</Label>
                <Input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  className="h-9 text-xs"
                />
              </div>
            </div>

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Licence Key Secret Vault Ref</Label>
              <Input
                placeholder="e.g. vault://lic-keys/microsoft-server-key"
                value={secretKeyRef}
                onChange={(e) => setSecretKeyRef(e.target.value)}
                className="h-9 text-xs font-mono"
              />
            </div>
          </div>

          <DialogFooter>
            <Button size="sm" variant="outline" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button
              size="sm"
              onClick={handleCreateSubmit}
              disabled={createMutation.isPending}
            >
              Save Licence
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ALLOCATE SEATS DIALOG */}
      <Dialog open={Boolean(allocateLicenseId)} onOpenChange={(open) => !open && setAllocateLicenseId(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base text-primary">
              <UserCheck className="h-5 w-5" />
              Allocate Licence Seats
            </DialogTitle>
            <DialogDescription className="text-xs">
              Assign software entitlement to a designated user, device, or server.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3 py-2 text-xs">
            <div className="space-y-1">
              <Label className="text-xs font-semibold">Allocation Type *</Label>
              <Select value={allocType} onValueChange={(v) => setAllocType(v as any)}>
                <SelectTrigger className="h-9 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="USER">User (Employee)</SelectItem>
                  <SelectItem value="DEVICE">Device / Laptop</SelectItem>
                  <SelectItem value="SERVER">Server</SelectItem>
                  <SelectItem value="PROJECT">Project Site</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {allocType === 'SERVER' ? (
              <div className="space-y-1">
                <Label className="text-xs font-semibold">Server Hostname / Identifier *</Label>
                <Input
                  placeholder="e.g. srv-cloud-db01.nicsi.internal"
                  value={allocServerId}
                  onChange={(e) => setAllocServerId(e.target.value)}
                  className="h-9 text-xs font-mono"
                />
              </div>
            ) : (
              <div className="space-y-1">
                <Label className="text-xs font-semibold">Assignee User / Target ID</Label>
                <Input
                  placeholder="User UUID or name snapshot..."
                  value={allocUserId}
                  onChange={(e) => setAllocUserId(e.target.value)}
                  className="h-9 text-xs font-mono"
                />
              </div>
            )}

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Seats / Quantity *</Label>
              <Input
                type="number"
                min="1"
                value={allocQty}
                onChange={(e) => setAllocQty(Number(e.target.value))}
                className="h-9 text-xs font-mono"
              />
            </div>

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Allocation Remarks</Label>
              <Textarea
                placeholder="Optional purpose notes..."
                value={allocRemarks}
                onChange={(e) => setAllocRemarks(e.target.value)}
                className="h-16 text-xs"
              />
            </div>
          </div>

          <DialogFooter>
            <Button size="sm" variant="outline" onClick={() => setAllocateLicenseId(null)}>
              Cancel
            </Button>
            <Button
              size="sm"
              onClick={() => {
                if (allocateLicenseId) {
                  allocateMutation.mutate({
                    id: allocateLicenseId,
                    payload: {
                      allocationType: allocType,
                      userId: allocUserId || undefined,
                      serverIdentifier: allocServerId || undefined,
                      quantity: allocQty,
                      remarks: allocRemarks || undefined,
                    },
                  });
                }
              }}
              disabled={allocateMutation.isPending || allocQty <= 0}
            >
              Confirm Allocation
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* VIEW LICENCE ALLOCATIONS DIALOG */}
      <Dialog open={Boolean(viewLicenseId)} onOpenChange={(open) => !open && setViewLicenseId(null)}>
        <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="text-base font-bold flex items-center justify-between">
              <span className="font-mono">{selectedLicense?.licenseCode}</span>
              {selectedLicense?.status && <StatusBadge status={selectedLicense.status} />}
            </DialogTitle>
            <DialogDescription className="text-xs">
              {selectedLicense?.itemName} ({selectedLicense?.itemCode})
            </DialogDescription>
          </DialogHeader>

          {loadingDetail ? (
            <div className="py-8 text-center text-xs text-muted-foreground">Loading allocations...</div>
          ) : (
            selectedLicense && (
              <div className="space-y-4 text-xs">
                <div className="grid grid-cols-3 gap-2.5 p-3 rounded-lg bg-muted/30 border border-border/60">
                  <div>
                    <span className="text-[10px] text-muted-foreground uppercase block">Type</span>
                    <Badge variant="outline" className="text-[10px] uppercase font-mono">{selectedLicense.licenseType}</Badge>
                  </div>
                  <div>
                    <span className="text-[10px] text-muted-foreground uppercase block">Entitlement</span>
                    <span className="font-bold text-sm">{selectedLicense.entitlementQty}</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-muted-foreground uppercase block">Available Seats</span>
                    <span className="font-bold text-sm text-emerald-600">{selectedLicense.availableQty}</span>
                  </div>
                </div>

                <div>
                  <h4 className="font-semibold text-xs mb-1.5 uppercase tracking-wider text-muted-foreground">
                    Active Allocations ({selectedLicense.allocations?.length || 0})
                  </h4>

                  {selectedLicense.allocations && selectedLicense.allocations.length > 0 ? (
                    <div className="border border-border/80 rounded-md overflow-hidden">
                      <table className="w-full text-xs text-left">
                        <thead className="bg-muted text-muted-foreground text-[10px] uppercase">
                          <tr>
                            <th className="p-2">Type</th>
                            <th className="p-2">Target</th>
                            <th className="p-2 text-right">Seats</th>
                            <th className="p-2">Status</th>
                            <th className="p-2 text-right">Action</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                          {selectedLicense.allocations.map((a) => (
                            <tr key={a.id}>
                              <td className="p-2 font-mono text-[11px]">{a.allocationType}</td>
                              <td className="p-2 font-mono text-[11px] text-muted-foreground">
                                {a.serverIdentifier || a.assetCode || a.userId || '—'}
                              </td>
                              <td className="p-2 text-right font-semibold font-mono">{a.quantity}</td>
                              <td className="p-2"><StatusBadge status={a.status} /></td>
                              <td className="p-2 text-right">
                                {a.status === 'ACTIVE' && (
                                  <Button
                                    size="sm"
                                    variant="ghost"
                                    className="h-7 px-2 text-xs text-destructive hover:bg-destructive/10"
                                    onClick={() => releaseMutation.mutate({ licenseId: selectedLicense.id, allocId: a.id })}
                                    disabled={releaseMutation.isPending}
                                  >
                                    Release
                                  </Button>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <p className="text-muted-foreground text-xs italic py-2">No active seat allocations recorded.</p>
                  )}
                </div>
              </div>
            )
          )}

          <DialogFooter>
            <Button size="sm" variant="outline" onClick={() => setViewLicenseId(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
