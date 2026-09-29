'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import {
  StorageLocationResponse,
  StoreSiteResponse,
  LocationTreeNode,
  PageResponse,
} from '@/types/master';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
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
  MapPin,
  Building2,
  FolderTree,
  Plus,
  RefreshCw,
  Box,
  Layers,
  ChevronRight,
} from 'lucide-react';
import { toast } from 'sonner';

const LOCATION_TYPES = ['ROOM', 'ZONE', 'RACK', 'SHELF', 'BIN', 'DESK', 'OTHER'];

export default function LocationsPage() {
  const queryClient = useQueryClient();
  const [selectedStoreId, setSelectedStoreId] = useState<string>('');
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  // Form State
  const [locationCode, setLocationCode] = useState('');
  const [locationName, setLocationName] = useState('');
  const [locationType, setLocationType] = useState('ROOM');
  const [parentLocationId, setParentLocationId] = useState<string>('none');
  const [barcodeValue, setBarcodeValue] = useState('');

  // Fetch all stores for the selector
  const { data: storesData } = useQuery({
    queryKey: ['stores-lookup'],
    queryFn: () => api.get<PageResponse<StoreSiteResponse>>('/api/store/stores?size=100'),
  });

  // Set default selected store once stores load
  const stores = storesData?.content || [];
  const currentStoreId = selectedStoreId || (stores.length > 0 ? stores[0].id : '');

  // Fetch flat locations list for parent dropdown
  const { data: flatLocationsData } = useQuery({
    queryKey: ['locations-flat', currentStoreId],
    queryFn: () => api.get<PageResponse<StorageLocationResponse>>(`/api/store/locations?storeId=${currentStoreId}&size=100`),
    enabled: !!currentStoreId,
  });

  // Fetch hierarchy tree for selected store
  const { data: treeData, isLoading: isTreeLoading, refetch } = useQuery({
    queryKey: ['locations-tree', currentStoreId],
    queryFn: () => api.get<LocationTreeNode[]>(`/api/store/locations/tree?storeId=${currentStoreId}`),
    enabled: !!currentStoreId,
  });

  // Create location mutation
  const createMutation = useMutation({
    mutationFn: async () => {
      return api.post<StorageLocationResponse>('/api/store/locations', {
        storeId: currentStoreId,
        parentLocationId: parentLocationId === 'none' ? undefined : parentLocationId,
        locationCode: locationCode.trim().toUpperCase(),
        locationName: locationName.trim(),
        locationType,
        barcodeValue: barcodeValue.trim() || undefined,
      });
    },
    onSuccess: (loc) => {
      toast.success(`Location ${loc.locationName} (${loc.locationCode}) added to hierarchy`);
      setIsCreateOpen(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['locations-tree', currentStoreId] });
      queryClient.invalidateQueries({ queryKey: ['locations-flat', currentStoreId] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to add location');
    },
  });

  const resetForm = () => {
    setLocationCode('');
    setLocationName('');
    setLocationType('ROOM');
    setParentLocationId('none');
    setBarcodeValue('');
  };

  const currentStore = stores.find((s) => s.id === currentStoreId);

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-card p-4 rounded-lg border border-border">
        <div>
          <h2 className="text-base font-semibold flex items-center gap-2">
            <MapPin className="size-4 text-primary" />
            Storage Locations Hierarchy
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Store → Room → Zone → Rack → Shelf → Bin location management.
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/* Store selector */}
          <Select
            value={currentStoreId}
            onValueChange={(val) => {
              setSelectedStoreId(val);
            }}
          >
            <SelectTrigger className="h-8 text-xs min-w-[200px] bg-background">
              <SelectValue placeholder="Select Store" />
            </SelectTrigger>
            <SelectContent>
              {stores.map((s) => (
                <SelectItem key={s.id} value={s.id} className="text-xs">
                  {s.storeName} ({s.storeCode})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Button variant="outline" size="sm" onClick={() => refetch()} className="gap-1 text-xs">
            <RefreshCw className="size-3.5" />
            Refresh
          </Button>

          <Button
            size="sm"
            onClick={() => setIsCreateOpen(true)}
            disabled={!currentStoreId}
            className="gap-1 text-xs"
          >
            <Plus className="size-3.5" />
            Add Location
          </Button>
        </div>
      </div>

      {/* Main Hierarchy Tree View */}
      <div className="grid gap-6 lg:grid-cols-12">
        <div className="lg:col-span-8">
          <Card className="border-border">
            <CardHeader className="pb-3 flex flex-row items-center justify-between">
              <div>
                <CardTitle className="text-sm flex items-center gap-2">
                  <Building2 className="size-4 text-primary" />
                  {currentStore?.storeName || 'Select a store'}
                </CardTitle>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Type: {currentStore?.storeType} · Code: {currentStore?.storeCode}
                </p>
              </div>
            </CardHeader>
            <CardContent className="p-4">
              {isTreeLoading ? (
                <div className="py-12 text-center text-xs text-muted-foreground">
                  Loading hierarchy...
                </div>
              ) : !treeData || treeData.length === 0 ? (
                <div className="py-12 text-center text-muted-foreground">
                  <FolderTree className="mx-auto size-8 text-muted-foreground/50 mb-2" />
                  <p className="text-xs font-semibold">No storage locations configured for this store</p>
                  <p className="text-[11px] text-muted-foreground mt-1">
                    Click &quot;Add Location&quot; to define rooms, racks, or shelves.
                  </p>
                </div>
              ) : (
                <div className="space-y-1">
                  {treeData.map((node) => (
                    <LocationTreeNodeView key={node.id} node={node} />
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Quick Guide Card */}
        <div className="lg:col-span-4 space-y-4">
          <Card className="border-border">
            <CardHeader>
              <CardTitle className="text-sm">Storage Hierarchy Rules</CardTitle>
            </CardHeader>
            <CardContent className="text-xs space-y-3 text-muted-foreground">
              <div className="flex items-start gap-2">
                <Layers className="size-4 text-blue-600 shrink-0 mt-0.5" />
                <span>
                  <strong>Store Scoped:</strong> Every location belongs to a single store site and must have a unique location code within that store.
                </span>
              </div>
              <div className="flex items-start gap-2">
                <FolderTree className="size-4 text-emerald-600 shrink-0 mt-0.5" />
                <span>
                  <strong>N-Level Nesting:</strong> Rooms can contain Racks, Racks contain Shelves, Shelves contain Bins. Cycle prevention is verified by the backend.
                </span>
              </div>
              <div className="flex items-start gap-2">
                <Box className="size-4 text-amber-600 shrink-0 mt-0.5" />
                <span>
                  <strong>Deactivation Guards:</strong> A parent location cannot be deactivated if it contains active child locations or existing inventory.
                </span>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Add Location Modal */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle>Add Storage Location</DialogTitle>
            <DialogDescription className="text-xs">
              Add a new storage bin, shelf, rack, or room to {currentStore?.storeName}.
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
                <Label className="text-xs">Location Code *</Label>
                <Input
                  required
                  maxLength={50}
                  placeholder="e.g. ROOM-101 or RACK-A1"
                  value={locationCode}
                  onChange={(e) => setLocationCode(e.target.value)}
                  className="h-8 text-xs font-mono uppercase"
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">Location Type *</Label>
                <Select value={locationType} onValueChange={setLocationType}>
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {LOCATION_TYPES.map((t) => (
                      <SelectItem key={t} value={t} className="text-xs">
                        {t}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs">Location Name *</Label>
              <Input
                required
                maxLength={150}
                placeholder="e.g. Server Room A1"
                value={locationName}
                onChange={(e) => setLocationName(e.target.value)}
                className="h-8 text-xs"
              />
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs">Parent Location</Label>
              <Select value={parentLocationId} onValueChange={setParentLocationId}>
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue placeholder="None (Root Level)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none" className="text-xs font-semibold">
                    None (Root Level in Store)
                  </SelectItem>
                  {flatLocationsData?.content?.map((loc) => (
                    <SelectItem key={loc.id} value={loc.id} className="text-xs">
                      {loc.locationCode} - {loc.locationName} ({loc.locationType})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs">Barcode / QR Value</Label>
              <Input
                maxLength={120}
                placeholder="Optional scan barcode"
                value={barcodeValue}
                onChange={(e) => setBarcodeValue(e.target.value)}
                className="h-8 text-xs font-mono"
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
                {createMutation.isPending ? 'Saving...' : 'Add to Hierarchy'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// Recursive Tree Node Component
function LocationTreeNodeView({ node }: { node: LocationTreeNode }) {
  const hasChildren = node.children && node.children.length > 0;

  return (
    <div className="ml-2 border-l border-border pl-3 py-1">
      <div className="flex items-center gap-2 rounded-md p-1.5 hover:bg-muted/50 transition-colors">
        <ChevronRight className="size-3 text-muted-foreground" />
        <span className="font-semibold text-xs font-mono text-primary">{node.locationCode}</span>
        <span className="text-xs text-foreground font-medium">{node.locationName}</span>
        <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-mono text-muted-foreground uppercase">
          {node.locationType}
        </span>
        {node.barcodeValue && (
          <span className="text-[10px] text-muted-foreground font-mono">
            [{node.barcodeValue}]
          </span>
        )}
      </div>

      {hasChildren && (
        <div className="space-y-1">
          {node.children.map((child) => (
            <LocationTreeNodeView key={child.id} node={child} />
          ))}
        </div>
      )}
    </div>
  );
}
