'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { AssetSummaryResponse } from '@/types/asset';
import { PageResponse } from '@/types/master';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { StatusBadge } from '@/components/common/status-badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
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
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Archive,
  Plus,
  Send,
  CheckCircle2,
  FileText,
  DollarSign,
  Trash2,
  Eye,
  Gavel,
  Recycle,
  AlertTriangle,
} from 'lucide-react';
import { toast } from 'sonner';

interface CondemnationRecord {
  id: string;
  condemnationNo: string;
  proposalDate: string;
  committeeReference?: string;
  technicalReason: string;
  status: string;
  itemCount: number;
  createdAt: string;
  items?: {
    id: string;
    assetId: string;
    assetCode: string;
    itemName: string;
    assessedCondition?: string;
    residualValue?: number;
    recommendedMethod?: string;
    remarks?: string;
  }[];
}

interface DisposalRecord {
  id: string;
  disposalNo: string;
  disposalDate: string;
  disposalMethod: string;
  purchaserNameSnapshot?: string;
  saleAmount: number;
  certificateNumber?: string;
  status: string;
  itemCount: number;
  createdAt: string;
  items?: {
    id: string;
    assetId: string;
    assetCode: string;
    itemName: string;
    realizedValue: number;
    remarks?: string;
  }[];
}

export default function DisposalPage() {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState('condemnation');

  // Condemnation state
  const [condemnationOpen, setCondemnationOpen] = useState(false);
  const [viewCondemnationId, setViewCondemnationId] = useState<string | null>(null);
  const [technicalReason, setTechnicalReason] = useState('');
  const [committeeRef, setCommitteeRef] = useState('');
  const [condemnationLines, setCondemnationLines] = useState<
    { assetId: string; assessedCondition: string; residualValue: number; recommendedMethod: string; remarks: string }[]
  >([]);

  // Disposal state
  const [disposalOpen, setDisposalOpen] = useState(false);
  const [viewDisposalId, setViewDisposalId] = useState<string | null>(null);
  const [disposalMethod, setDisposalMethod] = useState('AUCTION');
  const [purchaserName, setPurchaserName] = useState('');
  const [saleAmount, setSaleAmount] = useState<number | ''>('');
  const [certificateNumber, setCertificateNumber] = useState('');
  const [disposalLines, setDisposalLines] = useState<
    { assetId: string; realizedValue: number; remarks: string }[]
  >([]);

  // Query assets for selection
  const { data: assets } = useQuery<PageResponse<AssetSummaryResponse>>({
    queryKey: ['assets-disposal-lookup'],
    queryFn: () => api.get<PageResponse<AssetSummaryResponse>>('/api/assets?size=150'),
  });

  // Query Condemnations
  const { data: condemnationsPage, isLoading: loadingCondemnations } = useQuery<
    PageResponse<CondemnationRecord>
  >({
    queryKey: ['condemnations'],
    queryFn: () => api.get<PageResponse<CondemnationRecord>>('/api/condemnations?size=20'),
  });

  // Query Disposals
  const { data: disposalsPage, isLoading: loadingDisposals } = useQuery<PageResponse<DisposalRecord>>({
    queryKey: ['disposals'],
    queryFn: () => api.get<PageResponse<DisposalRecord>>('/api/disposals?size=20'),
  });

  // Query detail for active view
  const { data: activeCondemnation } = useQuery<CondemnationRecord>({
    queryKey: ['condemnation-detail', viewCondemnationId],
    queryFn: () => api.get<CondemnationRecord>(`/api/condemnations/${viewCondemnationId}`),
    enabled: !!viewCondemnationId,
  });

  const { data: activeDisposal } = useQuery<DisposalRecord>({
    queryKey: ['disposal-detail', viewDisposalId],
    queryFn: () => api.get<DisposalRecord>(`/api/disposals/${viewDisposalId}`),
    enabled: !!viewDisposalId,
  });

  // Mutations for Condemnation
  const createCondemnationMutation = useMutation({
    mutationFn: (body: unknown) => api.post('/api/condemnations', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['condemnations'] });
      toast.success('Condemnation proposal submitted');
      setCondemnationOpen(false);
      resetCondemnationForm();
    },
    onError: (err: ApiError) => toast.error(err.message || 'Failed to submit proposal'),
  });

  const submitCondemnationMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/condemnations/${id}/submit`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['condemnations'] });
      toast.success('Proposal submitted to committee');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const recommendCondemnationMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/condemnations/${id}/recommend`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['condemnations'] });
      toast.success('Technically recommended');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const approveCondemnationMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/condemnations/${id}/approve`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['condemnations'] });
      queryClient.invalidateQueries({ queryKey: ['assets-disposal-lookup'] });
      toast.success('Condemnation approved! Assets marked as CONDEMNED.');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  // Mutations for Disposal
  const createDisposalMutation = useMutation({
    mutationFn: (body: unknown) => api.post('/api/disposals', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['disposals'] });
      toast.success('Disposal order created');
      setDisposalOpen(false);
      resetDisposalForm();
    },
    onError: (err: ApiError) => toast.error(err.message || 'Failed to create disposal order'),
  });

  const submitDisposalMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/disposals/${id}/submit`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['disposals'] });
      toast.success('Disposal order submitted');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const approveDisposalMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/disposals/${id}/approve`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['disposals'] });
      toast.success('Disposal order approved');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const postDisposalMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/disposals/${id}/post`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['disposals'] });
      queryClient.invalidateQueries({ queryKey: ['assets-disposal-lookup'] });
      toast.success('Disposal posted! Assets retired and removed from register.');
    },
    onError: (err: ApiError) => toast.error(err.message),
  });

  const resetCondemnationForm = () => {
    setTechnicalReason('');
    setCommitteeRef('');
    setCondemnationLines([]);
  };

  const resetDisposalForm = () => {
    setDisposalMethod('AUCTION');
    setPurchaserName('');
    setSaleAmount('');
    setCertificateNumber('');
    setDisposalLines([]);
  };

  const addCondemnationLine = () => {
    setCondemnationLines((prev) => [
      ...prev,
      {
        assetId: '',
        assessedCondition: 'UNSERVICEABLE',
        residualValue: 0,
        recommendedMethod: 'AUCTION',
        remarks: '',
      },
    ]);
  };

  const addDisposalLine = () => {
    setDisposalLines((prev) => [
      ...prev,
      {
        assetId: '',
        realizedValue: 0,
        remarks: '',
      },
    ]);
  };

  // Condemnation columns
  const condemnationColumns: ColumnDef<CondemnationRecord>[] = [
    {
      accessorKey: 'condemnationNo',
      header: 'Proposal No',
      cell: (row) => (
        <span className="font-semibold text-blue-600 dark:text-blue-400 font-mono text-xs">
          {row.condemnationNo}
        </span>
      ),
    },
    {
      accessorKey: 'technicalReason',
      header: 'Technical Justification',
      cell: (row) => (
        <p className="text-xs truncate max-w-xs">{row.technicalReason}</p>
      ),
    },
    {
      accessorKey: 'itemCount',
      header: 'Assets',
      cell: (row) => (
        <Badge variant="secondary" className="font-mono text-xs">
          {row.itemCount || 1} items
        </Badge>
      ),
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: (row) => <StatusBadge status={row.status} />,
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: (row) => {
        const item = row;
        return (
          <div className="flex items-center gap-1.5">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setViewCondemnationId(item.id)}
              className="h-8 px-2"
            >
              <Eye className="size-3.5 mr-1" /> View
            </Button>
            {item.status === 'DRAFT' && (
              <Button
                variant="outline"
                size="sm"
                className="h-8 px-2 text-blue-600"
                onClick={() => submitCondemnationMutation.mutate(item.id)}
              >
                <Send className="size-3.5 mr-1" /> Submit
              </Button>
            )}
            {item.status === 'SUBMITTED' && (
              <Button
                variant="outline"
                size="sm"
                className="h-8 px-2 text-purple-600"
                onClick={() => recommendCondemnationMutation.mutate(item.id)}
              >
                Recommend
              </Button>
            )}
            {(item.status === 'SUBMITTED' || item.status === 'TECHNICALLY_RECOMMENDED') && (
              <Button
                variant="outline"
                size="sm"
                className="h-8 px-2 text-emerald-600"
                onClick={() => approveCondemnationMutation.mutate(item.id)}
              >
                <CheckCircle2 className="size-3.5 mr-1" /> Approve
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  // Disposal columns
  const disposalColumns: ColumnDef<DisposalRecord>[] = [
    {
      accessorKey: 'disposalNo',
      header: 'Disposal No',
      cell: (row) => (
        <span className="font-semibold text-blue-600 dark:text-blue-400 font-mono text-xs">
          {row.disposalNo}
        </span>
      ),
    },
    {
      accessorKey: 'disposalMethod',
      header: 'Method',
      cell: (row) => (
        <Badge variant="outline" className="text-xs">
          {row.disposalMethod}
        </Badge>
      ),
    },
    {
      accessorKey: 'purchaserNameSnapshot',
      header: 'Purchaser / Vendor',
      cell: (row) => (
        <span className="text-xs font-medium">{row.purchaserNameSnapshot || 'E-Auction'}</span>
      ),
    },
    {
      accessorKey: 'saleAmount',
      header: 'Realized Value',
      cell: (row) => (
        <span className="font-mono text-xs font-bold text-emerald-600">
          ₹{row.saleAmount?.toLocaleString() || '0.00'}
        </span>
      ),
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: (row) => <StatusBadge status={row.status} />,
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: (row) => {
        const item = row;
        return (
          <div className="flex items-center gap-1.5">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setViewDisposalId(item.id)}
              className="h-8 px-2"
            >
              <Eye className="size-3.5 mr-1" /> View
            </Button>
            {item.status === 'DRAFT' && (
              <Button
                variant="outline"
                size="sm"
                className="h-8 px-2 text-blue-600"
                onClick={() => submitDisposalMutation.mutate(item.id)}
              >
                <Send className="size-3.5 mr-1" /> Submit
              </Button>
            )}
            {item.status === 'SUBMITTED' && (
              <Button
                variant="outline"
                size="sm"
                className="h-8 px-2 text-purple-600"
                onClick={() => approveDisposalMutation.mutate(item.id)}
              >
                <CheckCircle2 className="size-3.5 mr-1" /> Approve
              </Button>
            )}
            {item.status === 'APPROVED' && (
              <Button
                size="sm"
                className="h-8 px-2 bg-emerald-600 hover:bg-emerald-700 text-white"
                onClick={() => postDisposalMutation.mutate(item.id)}
              >
                <Gavel className="size-3.5 mr-1" /> Post & Retire
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-red-500/10 text-red-600 dark:text-red-400">
            <Archive className="size-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Condemnation & Disposal
            </h1>
            <p className="text-sm text-slate-500">
              Phase 10 · Survey committee technical review, condemnation certificate, and e-waste/auction disposal
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {activeTab === 'condemnation' ? (
            <Button
              onClick={() => {
                resetCondemnationForm();
                addCondemnationLine();
                setCondemnationOpen(true);
              }}
              className="gap-2 bg-blue-600 hover:bg-blue-700 text-white shadow-sm"
            >
              <Plus className="size-4" /> New Condemnation Proposal
            </Button>
          ) : (
            <Button
              onClick={() => {
                resetDisposalForm();
                addDisposalLine();
                setDisposalOpen(true);
              }}
              className="gap-2 bg-emerald-600 hover:bg-emerald-700 text-white shadow-sm"
            >
              <Plus className="size-4" /> Create Disposal Order
            </Button>
          )}
        </div>
      </div>

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="grid grid-cols-2 w-72">
          <TabsTrigger value="condemnation">Condemnations</TabsTrigger>
          <TabsTrigger value="disposal">Disposals</TabsTrigger>
        </TabsList>

        <TabsContent value="condemnation" className="space-y-4 mt-4">
          <Card className="border-slate-200 dark:border-slate-800">
            <CardContent className="pt-6">
              <DataTable
                columns={condemnationColumns}
                data={condemnationsPage?.content || []}
                totalPages={condemnationsPage?.totalPages || 1}
                page={0}
                pageSize={20}
                onPageChange={() => {}}
                isLoading={loadingCondemnations}
              />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="disposal" className="space-y-4 mt-4">
          <Card className="border-slate-200 dark:border-slate-800">
            <CardContent className="pt-6">
              <DataTable
                columns={disposalColumns}
                data={disposalsPage?.content || []}
                totalPages={disposalsPage?.totalPages || 1}
                page={0}
                pageSize={20}
                onPageChange={() => {}}
                isLoading={loadingDisposals}
              />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* CREATE CONDEMNATION MODAL */}
      <Dialog open={condemnationOpen} onOpenChange={setCondemnationOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <AlertTriangle className="size-5 text-amber-600" />
              Propose Asset Condemnation
            </DialogTitle>
            <DialogDescription>
              Submit survey committee review for unserviceable hardware.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!technicalReason || condemnationLines.length === 0) {
                toast.error('Technical reason and at least one asset line required');
                return;
              }
              createCondemnationMutation.mutate({
                technicalReason,
                committeeReference: committeeRef || undefined,
                items: condemnationLines,
              });
            }}
            className="space-y-4"
          >
            <div>
              <Label className="text-xs font-semibold">Technical Reason / Survey Report *</Label>
              <Textarea
                placeholder="Reason (e.g. beyond economic repair, obsolete 7-year life completed, irreparable PCB burnout)..."
                value={technicalReason}
                onChange={(e) => setTechnicalReason(e.target.value)}
                rows={3}
                className="mt-1"
                required
              />
            </div>

            <div>
              <Label className="text-xs font-semibold">Committee Reference / Order No.</Label>
              <Input
                placeholder="e.g. NICSI/CONDEMN/2026/02"
                value={committeeRef}
                onChange={(e) => setCommitteeRef(e.target.value)}
                className="mt-1"
              />
            </div>

            {/* Asset Lines */}
            <div className="space-y-3 pt-2">
              <div className="flex items-center justify-between border-b pb-2">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-600">
                  Assets for Condemnation
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={addCondemnationLine}
                  className="h-7 text-xs"
                >
                  <Plus className="size-3 mr-1" /> Add Asset
                </Button>
              </div>

              {condemnationLines.map((line, idx) => (
                <div key={idx} className="p-3 rounded-lg border bg-slate-50 dark:bg-slate-900 space-y-2">
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                    <div className="sm:col-span-2">
                      <Label className="text-xs">Asset *</Label>
                      <Select
                        value={line.assetId}
                        onValueChange={(val) => {
                          const updated = [...condemnationLines];
                          updated[idx].assetId = val;
                          setCondemnationLines(updated);
                        }}
                      >
                        <SelectTrigger className="mt-1 h-8">
                          <SelectValue placeholder="Select Asset" />
                        </SelectTrigger>
                        <SelectContent className="max-h-56">
                          {assets?.content?.map((a) => (
                            <SelectItem key={a.id} value={a.id}>
                              {a.assetCode} - {a.itemName}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <div>
                      <Label className="text-xs">Residual Value (₹)</Label>
                      <Input
                        type="number"
                        min="0"
                        value={line.residualValue}
                        onChange={(e) => {
                          const updated = [...condemnationLines];
                          updated[idx].residualValue = Number(e.target.value);
                          setCondemnationLines(updated);
                        }}
                        className="mt-1 h-8 font-mono"
                      />
                    </div>
                  </div>

                  <div className="flex items-center justify-between">
                    <div className="w-48">
                      <Label className="text-xs">Recommended Disposal Method</Label>
                      <Select
                        value={line.recommendedMethod}
                        onValueChange={(val) => {
                          const updated = [...condemnationLines];
                          updated[idx].recommendedMethod = val;
                          setCondemnationLines(updated);
                        }}
                      >
                        <SelectTrigger className="mt-1 h-8">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="AUCTION">E-Auction</SelectItem>
                          <SelectItem value="E_WASTE">Authorized E-Waste</SelectItem>
                          <SelectItem value="SCRAP">Scrap Metal</SelectItem>
                          <SelectItem value="RETURN_TO_OEM">Return to OEM</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>

                    {condemnationLines.length > 1 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() =>
                          setCondemnationLines(condemnationLines.filter((_, i) => i !== idx))
                        }
                        className="text-red-500 hover:text-red-600 mt-4 h-8"
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCondemnationOpen(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={createCondemnationMutation.isPending}
                className="bg-blue-600 hover:bg-blue-700 text-white"
              >
                Submit Proposal
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* CREATE DISPOSAL ORDER MODAL */}
      <Dialog open={disposalOpen} onOpenChange={setDisposalOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Gavel className="size-5 text-emerald-600" />
              New Disposal Order
            </DialogTitle>
            <DialogDescription>
              Execute sale or authorized recycling for condemned equipment.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (disposalLines.length === 0) {
                toast.error('Add at least one asset to dispose');
                return;
              }
              createDisposalMutation.mutate({
                disposalMethod,
                purchaserNameSnapshot: purchaserName || undefined,
                saleAmount: saleAmount !== '' ? Number(saleAmount) : 0,
                certificateNumber: certificateNumber || undefined,
                items: disposalLines,
              });
            }}
            className="space-y-4"
          >
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label className="text-xs font-semibold">Disposal Method *</Label>
                <Select value={disposalMethod} onValueChange={setDisposalMethod}>
                  <SelectTrigger className="mt-1">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="AUCTION">MSTC / GeM E-Auction</SelectItem>
                    <SelectItem value="E_WASTE">Certified E-Waste Recycler</SelectItem>
                    <SelectItem value="SCRAP">Scrap Destruction</SelectItem>
                    <SelectItem value="RETURN_TO_OEM">OEM Buyback</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div>
                <Label className="text-xs font-semibold">Purchaser / Recycler Name</Label>
                <Input
                  placeholder="e.g. MSTC Bidder #402, GreenWaste Recyclers"
                  value={purchaserName}
                  onChange={(e) => setPurchaserName(e.target.value)}
                  className="mt-1"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label className="text-xs font-semibold">Realized Sale Amount (₹)</Label>
                <Input
                  type="number"
                  min="0"
                  placeholder="0.00"
                  value={saleAmount}
                  onChange={(e) => setSaleAmount(e.target.value === '' ? '' : Number(e.target.value))}
                  className="mt-1 font-mono"
                />
              </div>

              <div>
                <Label className="text-xs font-semibold">Disposal / Destruction Certificate No.</Label>
                <Input
                  placeholder="e.g. CERT-EW-2026-99"
                  value={certificateNumber}
                  onChange={(e) => setCertificateNumber(e.target.value)}
                  className="mt-1"
                />
              </div>
            </div>

            {/* Disposal Lines */}
            <div className="space-y-3 pt-2">
              <div className="flex items-center justify-between border-b pb-2">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-600">
                  Assets to Dispose
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={addDisposalLine}
                  className="h-7 text-xs"
                >
                  <Plus className="size-3 mr-1" /> Add Asset
                </Button>
              </div>

              {disposalLines.map((line, idx) => (
                <div key={idx} className="p-3 rounded-lg border bg-slate-50 dark:bg-slate-900 space-y-2">
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                    <div className="sm:col-span-2">
                      <Label className="text-xs">Asset *</Label>
                      <Select
                        value={line.assetId}
                        onValueChange={(val) => {
                          const updated = [...disposalLines];
                          updated[idx].assetId = val;
                          setDisposalLines(updated);
                        }}
                      >
                        <SelectTrigger className="mt-1 h-8">
                          <SelectValue placeholder="Select Asset" />
                        </SelectTrigger>
                        <SelectContent className="max-h-56">
                          {assets?.content?.map((a) => (
                            <SelectItem key={a.id} value={a.id}>
                              {a.assetCode} - {a.itemName} ({a.assetStatus})
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <div>
                      <Label className="text-xs">Realized Value (₹)</Label>
                      <Input
                        type="number"
                        min="0"
                        value={line.realizedValue}
                        onChange={(e) => {
                          const updated = [...disposalLines];
                          updated[idx].realizedValue = Number(e.target.value);
                          setDisposalLines(updated);
                        }}
                        className="mt-1 h-8 font-mono"
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDisposalOpen(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={createDisposalMutation.isPending}
                className="bg-emerald-600 hover:bg-emerald-700 text-white"
              >
                Create Disposal Order
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
