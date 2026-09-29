'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { useAuth } from '@/hooks/use-auth';
import {
  InspectionResponse,
  InspectionSummaryResponse,
  DecideInspectionRequest,
  DecideInspectionItemRequest,
} from '@/types/inspection';
import { GrnResponse } from '@/types/grn';
import { PageResponse } from '@/types/master';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { StatusBadge } from '@/components/common/status-badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
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
  ClipboardCheck,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Eye,
  ShieldAlert,
  ShieldCheck,
  PackageCheck,
  Calendar,
  Layers,
  Wrench,
  Check,
} from 'lucide-react';
import { toast } from 'sonner';

interface ItemDecideState {
  inspectionItemId: string;
  itemCode: string;
  itemName: string;
  inspectedQty: number;
  acceptedQty: number;
  rejectedQty: number;
  quarantineQty: number;
  specificationMatch: boolean;
  physicalCondition: string;
  warrantyVerified: boolean;
  accessoryVerified: boolean;
  remarks: string;
}

export default function InspectionPage() {
  const queryClient = useQueryClient();
  const { user } = useAuth();

  const [search, setSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('ALL');
  const [page, setPage] = useState(0);

  // Decision Modal State
  const [selectedInspection, setSelectedInspection] = useState<InspectionResponse | null>(null);
  const [linkedGrn, setLinkedGrn] = useState<GrnResponse | null>(null);
  const [isDecisionOpen, setIsDecisionOpen] = useState(false);
  const [overallRemarks, setOverallRemarks] = useState('');
  const [itemDecisions, setItemDecisions] = useState<ItemDecideState[]>([]);

  // Fetch Inspections list
  const { data: inspectionsData, isLoading } = useQuery({
    queryKey: ['inspections', page, search, filterStatus],
    queryFn: () => {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', '15');
      if (search) params.append('search', search);
      if (filterStatus !== 'ALL') params.append('status', filterStatus);
      return api.get<PageResponse<InspectionSummaryResponse>>(`/api/store/inspections?${params.toString()}`);
    },
  });

  // Decision Mutation
  const decideMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: DecideInspectionRequest }) =>
      api.post<InspectionResponse>(`/api/store/inspections/${id}/decide`, data),
    onSuccess: (data) => {
      toast.success(`Inspection decision recorded: ${data.status}`);
      queryClient.invalidateQueries({ queryKey: ['inspections'] });
      queryClient.invalidateQueries({ queryKey: ['grns'] });
      setIsDecisionOpen(false);
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to record inspection decision');
    },
  });

  const openInspection = async (id: string) => {
    try {
      const inspection = await api.get<InspectionResponse>(`/api/store/inspections/${id}`);
      setSelectedInspection(inspection);
      setOverallRemarks(inspection.overallRemarks || '');

      // Fetch linked GRN to check maker-checker
      if (inspection.grnId) {
        const grn = await api.get<GrnResponse>(`/api/store/grns/${inspection.grnId}`);
        setLinkedGrn(grn);
      } else {
        setLinkedGrn(null);
      }

      // Initialize decisions for each line
      const initialDecisions: ItemDecideState[] = inspection.items.map((it) => ({
        inspectionItemId: it.id,
        itemCode: it.itemCode,
        itemName: it.itemName,
        inspectedQty: it.inspectedQty,
        acceptedQty: it.acceptedQty || it.inspectedQty, // default accept all if untouched
        rejectedQty: it.rejectedQty || 0,
        quarantineQty: it.quarantineQty || 0,
        specificationMatch: it.specificationMatch !== null ? it.specificationMatch : true,
        physicalCondition: it.physicalCondition || 'GOOD',
        warrantyVerified: it.warrantyVerified !== null ? it.warrantyVerified : true,
        accessoryVerified: it.accessoryVerified !== null ? it.accessoryVerified : true,
        remarks: it.remarks || '',
      }));
      setItemDecisions(initialDecisions);
      setIsDecisionOpen(true);
    } catch (err: unknown) {
      const e = err as ApiError;
      toast.error(e.message || 'Failed to load inspection details');
    }
  };

  // Check if current user is the maker of the GRN (Maker-Checker violation)
  const isMaker = Boolean(user && linkedGrn && user.userId === linkedGrn.receivedByUserId);
  const isDecided = Boolean(selectedInspection && selectedInspection.status !== 'IN_PROGRESS' && selectedInspection.status !== 'DRAFT');

  const handleLineQtyChange = (index: number, field: 'acceptedQty' | 'rejectedQty' | 'quarantineQty', value: number) => {
    const updated = [...itemDecisions];
    updated[index][field] = value;
    setItemDecisions(updated);
  };

  const handleChecklistChange = (
    index: number,
    field: 'specificationMatch' | 'warrantyVerified' | 'accessoryVerified',
    checked: boolean
  ) => {
    const updated = [...itemDecisions];
    updated[index][field] = checked;
    setItemDecisions(updated);
  };

  const handleConditionChange = (index: number, val: string) => {
    const updated = [...itemDecisions];
    updated[index].physicalCondition = val;
    setItemDecisions(updated);
  };

  const handleDecisionSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedInspection) return;

    if (isMaker) {
      toast.error('Maker-Checker Guard: GRN receiver cannot approve technical inspection');
      return;
    }

    // Validate quantities for all items
    for (let i = 0; i < itemDecisions.length; i++) {
      const item = itemDecisions[i];
      const sum = Number(item.acceptedQty) + Number(item.rejectedQty) + Number(item.quarantineQty);
      if (sum > item.inspectedQty) {
        toast.error(
          `Line ${i + 1} (${item.itemCode}): Sum of accepted (${item.acceptedQty}) + rejected (${item.rejectedQty}) + quarantine (${item.quarantineQty}) exceeds inspected quantity (${item.inspectedQty})`
        );
        return;
      }
    }

    const payload: DecideInspectionRequest = {
      overallRemarks: overallRemarks.trim() || undefined,
      version: selectedInspection.version,
      items: itemDecisions.map((it) => ({
        inspectionItemId: it.inspectionItemId,
        acceptedQty: Number(it.acceptedQty),
        rejectedQty: Number(it.rejectedQty),
        quarantineQty: Number(it.quarantineQty),
        specificationMatch: it.specificationMatch,
        physicalCondition: it.physicalCondition,
        warrantyVerified: it.warrantyVerified,
        accessoryVerified: it.accessoryVerified,
        technicalResult: JSON.stringify({
          specificationMatch: it.specificationMatch,
          physicalCondition: it.physicalCondition,
          warrantyVerified: it.warrantyVerified,
          accessoryVerified: it.accessoryVerified,
        }),
        remarks: it.remarks || undefined,
      })),
    };

    decideMutation.mutate({ id: selectedInspection.id, data: payload });
  };

  const columns: ColumnDef<InspectionSummaryResponse>[] = [
    {
      header: 'Inspection No',
      cell: (row) => (
        <div>
          <div className="font-semibold text-primary flex items-center gap-1.5 font-mono">
            <ClipboardCheck className="w-4 h-4 text-muted-foreground" />
            {row.inspectionNo}
          </div>
          <div className="text-xs text-muted-foreground">
            {row.inspectionDate || new Date(row.createdAt).toLocaleDateString()}
          </div>
        </div>
      ),
    },
    {
      header: 'Linked GRN',
      cell: (row) => (
        <Badge variant="outline" className="font-mono text-xs text-primary bg-primary/5">
          {row.grnNo}
        </Badge>
      ),
    },
    {
      header: 'Items Inspected',
      cell: (row) => (
        <Badge variant="secondary" className="font-mono text-xs">
          {row.itemCount} line{row.itemCount !== 1 ? 's' : ''}
        </Badge>
      ),
    },
    {
      header: 'Remarks',
      cell: (row) => (
        <span className="text-xs text-muted-foreground truncate max-w-xs block">
          {row.overallRemarks || 'Pending technical review'}
        </span>
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
          onClick={() => openInspection(row.id)}
          className="h-8 gap-1.5"
        >
          {row.status === 'IN_PROGRESS' || row.status === 'DRAFT' ? (
            <>
              <Wrench className="w-3.5 h-3.5 text-amber-600" />
              Inspect & Decide
            </>
          ) : (
            <>
              <Eye className="w-3.5 h-3.5" />
              View Decision
            </>
          )}
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <ClipboardCheck className="w-6 h-6 text-primary" />
            Technical Inspection & Quality Verification
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Maker-Checker protected technical verification, physical condition checks, and consignment clearance.
          </p>
        </div>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="p-4 flex flex-col sm:flex-row gap-3">
          <div className="flex-1">
            <Input
              placeholder="Search by Inspection No, GRN No..."
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
                <SelectItem value="IN_PROGRESS">In Progress</SelectItem>
                <SelectItem value="ACCEPTED">Accepted</SelectItem>
                <SelectItem value="PARTIALLY_ACCEPTED">Partially Accepted</SelectItem>
                <SelectItem value="REJECTED">Rejected</SelectItem>
                <SelectItem value="QUARANTINE">Quarantine</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <DataTable
        columns={columns}
        data={inspectionsData?.content || []}
        isLoading={isLoading}
        page={page}
        totalPages={inspectionsData?.totalPages || 0}
        totalElements={inspectionsData?.totalElements || 0}
        onPageChange={setPage}
      />

      {/* Decision / Review Dialog */}
      <Dialog open={isDecisionOpen} onOpenChange={setIsDecisionOpen}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          {selectedInspection && (
            <form onSubmit={handleDecisionSubmit} className="space-y-6">
              <DialogHeader>
                <div className="flex items-center justify-between">
                  <DialogTitle className="text-xl font-bold flex items-center gap-2">
                    <ClipboardCheck className="w-5 h-5 text-primary" />
                    {selectedInspection.inspectionNo}
                  </DialogTitle>
                  <StatusBadge status={selectedInspection.status} />
                </div>
                <DialogDescription>
                  Linked GRN: <strong className="font-mono text-primary">{selectedInspection.grnNo}</strong> | Inspected Date: {selectedInspection.inspectionDate}
                </DialogDescription>
              </DialogHeader>

              {/* Maker-Checker Warning Alert */}
              {isMaker && !isDecided && (
                <div className="p-4 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive flex items-start gap-3">
                  <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
                  <div className="text-xs space-y-1">
                    <div className="font-semibold text-sm">Maker-Checker Policy Violation Detected</div>
                    <div>
                      You are logged in as the officer who received goods for GRN <strong>{selectedInspection.grnNo}</strong>.
                      According to NICSI store governance rules, the receiving officer cannot inspect or approve their own consignment.
                    </div>
                    <div className="text-muted-foreground italic">
                      Please switch to Store Manager or Technical Inspector role to conduct and record this inspection.
                    </div>
                  </div>
                </div>
              )}

              {/* Non-maker or decided state */}
              {!isMaker && !isDecided && (
                <div className="p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-800 dark:text-emerald-300 flex items-center gap-2 text-xs">
                  <ShieldCheck className="w-4 h-4 text-emerald-600" />
                  <span>
                    Maker-Checker Verified: You are inspecting a consignment received by another store officer.
                  </span>
                </div>
              )}

              {/* Items Quality Verification Cards */}
              <div className="space-y-4">
                <Label className="text-sm font-semibold flex items-center gap-1.5">
                  <Layers className="w-4 h-4 text-primary" />
                  Consignment Line Items Quality Checklist ({itemDecisions.length})
                </Label>

                {itemDecisions.map((line, idx) => (
                  <Card key={line.inspectionItemId} className="p-4 space-y-4 bg-muted/20">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-2 border-b">
                      <div>
                        <span className="font-semibold text-sm text-foreground">{line.itemCode}</span>
                        <span className="text-muted-foreground text-xs ml-2">({line.itemName})</span>
                      </div>
                      <Badge variant="outline" className="font-mono text-xs w-fit">
                        Total Inspected: {line.inspectedQty}
                      </Badge>
                    </div>

                    {/* Quantity Allocations */}
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                      <div className="space-y-1">
                        <Label className="text-xs text-emerald-700 font-medium">Accepted Qty</Label>
                        <Input
                          type="number"
                          step="any"
                          className="h-8 text-xs font-mono border-emerald-500/30"
                          value={line.acceptedQty}
                          disabled={isDecided || isMaker}
                          onChange={(e) => handleLineQtyChange(idx, 'acceptedQty', Number(e.target.value))}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-xs text-destructive font-medium">Rejected Qty</Label>
                        <Input
                          type="number"
                          step="any"
                          className="h-8 text-xs font-mono border-destructive/30"
                          value={line.rejectedQty}
                          disabled={isDecided || isMaker}
                          onChange={(e) => handleLineQtyChange(idx, 'rejectedQty', Number(e.target.value))}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-xs text-amber-700 font-medium">Quarantine Qty</Label>
                        <Input
                          type="number"
                          step="any"
                          className="h-8 text-xs font-mono border-amber-500/30"
                          value={line.quarantineQty}
                          disabled={isDecided || isMaker}
                          onChange={(e) => handleLineQtyChange(idx, 'quarantineQty', Number(e.target.value))}
                        />
                      </div>
                    </div>

                    {/* Technical Checklist */}
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-3 bg-background rounded-md border text-xs">
                      <div className="flex items-center space-x-2">
                        <Checkbox
                          id={`spec-${idx}`}
                          checked={line.specificationMatch}
                          disabled={isDecided || isMaker}
                          onCheckedChange={(checked) =>
                            handleChecklistChange(idx, 'specificationMatch', checked as boolean)
                          }
                        />
                        <Label htmlFor={`spec-${idx}`} className="text-xs cursor-pointer">
                          Spec Match
                        </Label>
                      </div>

                      <div className="flex items-center space-x-2">
                        <Checkbox
                          id={`war-${idx}`}
                          checked={line.warrantyVerified}
                          disabled={isDecided || isMaker}
                          onCheckedChange={(checked) =>
                            handleChecklistChange(idx, 'warrantyVerified', checked as boolean)
                          }
                        />
                        <Label htmlFor={`war-${idx}`} className="text-xs cursor-pointer">
                          Warranty Verified
                        </Label>
                      </div>

                      <div className="flex items-center space-x-2">
                        <Checkbox
                          id={`acc-${idx}`}
                          checked={line.accessoryVerified}
                          disabled={isDecided || isMaker}
                          onCheckedChange={(checked) =>
                            handleChecklistChange(idx, 'accessoryVerified', checked as boolean)
                          }
                        />
                        <Label htmlFor={`acc-${idx}`} className="text-xs cursor-pointer">
                          Accessories OK
                        </Label>
                      </div>

                      <div className="space-y-1">
                        <Select
                          value={line.physicalCondition}
                          disabled={isDecided || isMaker}
                          onValueChange={(val) => handleConditionChange(idx, val)}
                        >
                          <SelectTrigger className="h-7 text-xs">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="GOOD">Good Condition</SelectItem>
                            <SelectItem value="WORKING">Working / Fair</SelectItem>
                            <SelectItem value="DAMAGED">Damaged</SelectItem>
                            <SelectItem value="DEFECTIVE">Defective</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                    </div>
                  </Card>
                ))}
              </div>

              {/* Overall Remarks */}
              <div className="space-y-2">
                <Label htmlFor="overallRemarks">Overall Inspection Remarks & Recommendations</Label>
                <Textarea
                  id="overallRemarks"
                  rows={2}
                  placeholder="Detail test results, diagnostics, or rejection reasons..."
                  value={overallRemarks}
                  disabled={isDecided || isMaker}
                  onChange={(e) => setOverallRemarks(e.target.value)}
                />
              </div>

              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setIsDecisionOpen(false)}>
                  Close
                </Button>
                {!isDecided && (
                  <Button
                    type="submit"
                    disabled={decideMutation.isPending || isMaker}
                    className="gap-1.5 bg-primary"
                  >
                    <Check className="w-4 h-4" />
                    {decideMutation.isPending ? 'Recording Decision...' : 'Submit Inspection Decision'}
                  </Button>
                )}
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
