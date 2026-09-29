'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import {
  BalanceResponse,
  LedgerResponse,
  LowStockResponse,
  ReconciliationResponse,
} from '@/types/inventory';
import { StoreSiteResponse, ItemCategoryResponse, PageResponse } from '@/types/master';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Switch } from '@/components/ui/switch';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Boxes,
  Layers,
  AlertTriangle,
  Scale,
  Search,
  CheckCircle2,
  XCircle,
  Building2,
  ArrowDownLeft,
  ArrowUpRight,
  RotateCcw,
} from 'lucide-react';

export default function InventoryPage() {
  const [activeTab, setActiveTab] = useState('balances');

  // Balances filter state
  const [balanceSearch, setBalanceSearch] = useState('');
  const [selectedStore, setSelectedStore] = useState<string>('ALL');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [balancePage, setBalancePage] = useState(0);

  // Ledger filter state
  const [ledgerSearch, setLedgerSearch] = useState('');
  const [ledgerStore, setLedgerStore] = useState<string>('ALL');
  const [ledgerTxnType, setLedgerTxnType] = useState<string>('ALL');
  const [ledgerPage, setLedgerPage] = useState(0);

  // Low stock page state
  const [lowStockPage, setLowStockPage] = useState(0);

  // Master queries for filters
  const { data: stores } = useQuery<PageResponse<StoreSiteResponse>>({
    queryKey: ['stores-lookup'],
    queryFn: () => api.get<PageResponse<StoreSiteResponse>>('/api/store/stores?size=100'),
  });

  const { data: categories } = useQuery<PageResponse<ItemCategoryResponse>>({
    queryKey: ['categories-lookup'],
    queryFn: () => api.get<PageResponse<ItemCategoryResponse>>('/api/store/categories?size=100'),
  });

  // Query 1: Balances
  const { data: balancesData, isLoading: isBalancesLoading } = useQuery<PageResponse<BalanceResponse>>({
    queryKey: ['inventory-balances', selectedStore, selectedCategory, balanceSearch, lowStockOnly, balancePage],
    queryFn: () => {
      const params = new URLSearchParams();
      if (selectedStore !== 'ALL') params.append('storeId', selectedStore);
      if (selectedCategory !== 'ALL') params.append('categoryId', selectedCategory);
      if (balanceSearch.trim()) params.append('search', balanceSearch.trim());
      if (lowStockOnly) params.append('lowStockOnly', 'true');
      params.append('page', balancePage.toString());
      params.append('size', '15');
      return api.get<PageResponse<BalanceResponse>>(`/api/store/inventory/balances?${params.toString()}`);
    },
    enabled: activeTab === 'balances',
  });

  // Query 2: Ledger
  const { data: ledgerData, isLoading: isLedgerLoading } = useQuery<PageResponse<LedgerResponse>>({
    queryKey: ['inventory-ledger', ledgerStore, ledgerTxnType, ledgerSearch, ledgerPage],
    queryFn: () => {
      const params = new URLSearchParams();
      if (ledgerStore !== 'ALL') params.append('storeId', ledgerStore);
      if (ledgerTxnType !== 'ALL') params.append('transactionType', ledgerTxnType);
      if (ledgerSearch.trim()) params.append('search', ledgerSearch.trim());
      params.append('page', ledgerPage.toString());
      params.append('size', '15');
      return api.get<PageResponse<LedgerResponse>>(`/api/store/inventory/ledger?${params.toString()}`);
    },
    enabled: activeTab === 'ledger',
  });

  // Query 3: Low Stock
  const { data: lowStockData, isLoading: isLowStockLoading } = useQuery<PageResponse<LowStockResponse>>({
    queryKey: ['inventory-low-stock', lowStockPage],
    queryFn: () => {
      const params = new URLSearchParams();
      params.append('page', lowStockPage.toString());
      params.append('size', '15');
      return api.get<PageResponse<LowStockResponse>>(`/api/store/inventory/low-stock?${params.toString()}`);
    },
    enabled: activeTab === 'low-stock',
  });

  // Query 4: Reconciliation
  const { data: reconData, isLoading: isReconLoading, refetch: refetchRecon } = useQuery<ReconciliationResponse>({
    queryKey: ['inventory-reconciliation'],
    queryFn: () => api.get<ReconciliationResponse>('/api/store/inventory/reconciliation'),
    enabled: activeTab === 'reconciliation',
  });

  // Balances Table Columns
  const balanceColumns: ColumnDef<BalanceResponse>[] = [
    {
      header: 'Item',
      accessorKey: 'itemCode',
      cell: (row) => (
        <div>
          <div className="font-semibold text-slate-900">{row.itemCode}</div>
          <div className="text-xs text-slate-500">{row.itemName}</div>
          {row.categoryName && (
            <span className="inline-block mt-0.5 text-[10px] bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">
              {row.categoryName}
            </span>
          )}
        </div>
      ),
    },
    {
      header: 'Store / Location',
      accessorKey: 'storeCode',
      cell: (row) => (
        <div>
          <div className="font-medium text-slate-800">{row.storeCode}</div>
          <div className="text-xs text-slate-500">{row.locationCode} - {row.locationName}</div>
          {row.lotNumber && (
            <span className="text-[10px] text-indigo-700 font-mono">Lot: {row.lotNumber}</span>
          )}
        </div>
      ),
    },
    {
      header: 'On Hand',
      accessorKey: 'onHandQty',
      cell: (row) => (
        <div className="font-mono font-medium text-slate-900">
          {Number(row.onHandQty).toFixed(2)} <span className="text-xs text-slate-500">{row.uomCode}</span>
        </div>
      ),
    },
    {
      header: 'Reserved',
      accessorKey: 'reservedQty',
      cell: (row) => (
        <div className="font-mono text-slate-600">
          {Number(row.reservedQty || 0).toFixed(2)}
        </div>
      ),
    },
    {
      header: 'Available',
      accessorKey: 'availableQty',
      cell: (row) => (
        <div className="font-mono font-semibold text-emerald-700">
          {Number(row.availableQty).toFixed(2)}
        </div>
      ),
    },
    {
      header: 'Avg Cost / Valuation',
      accessorKey: 'avgUnitCost',
      cell: (row) => (
        <div>
          <div className="text-xs text-slate-600 font-mono">₹{Number(row.avgUnitCost || 0).toFixed(2)}</div>
          <div className="font-medium text-slate-900 font-mono">₹{Number(row.inventoryValue || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</div>
        </div>
      ),
    },
    {
      header: 'Status',
      accessorKey: 'isLowStock',
      cell: (row) => (
        row.isLowStock ? (
          <Badge variant="destructive" className="text-[11px] gap-1 bg-amber-600 hover:bg-amber-600">
            <AlertTriangle className="size-3" /> Low Stock
          </Badge>
        ) : (
          <Badge variant="outline" className="text-[11px] text-emerald-700 bg-emerald-50 border-emerald-200">
            Normal
          </Badge>
        )
      ),
    },
  ];

  // Ledger Table Columns
  const ledgerColumns: ColumnDef<LedgerResponse>[] = [
    {
      header: 'Txn No / Time',
      accessorKey: 'transactionNo',
      cell: (row) => (
        <div>
          <div className="font-mono font-semibold text-slate-900">{row.transactionNo}</div>
          <div className="text-xs text-slate-500">
            {row.transactionTime ? new Date(row.transactionTime).toLocaleString('en-IN') : '-'}
          </div>
        </div>
      ),
    },
    {
      header: 'Type',
      accessorKey: 'transactionType',
      cell: (row) => {
        const type = row.transactionType;
        let color = 'bg-slate-100 text-slate-800 border-slate-200';
        let icon = null;
        if (type === 'RECEIPT' || type === 'ADJUSTMENT_IN' || type === 'TRANSFER_IN') {
          color = 'bg-emerald-50 text-emerald-700 border-emerald-200';
          icon = <ArrowDownLeft className="size-3 inline mr-1" />;
        } else if (type === 'ISSUE' || type === 'ADJUSTMENT_OUT' || type === 'TRANSFER_OUT') {
          color = 'bg-blue-50 text-blue-700 border-blue-200';
          icon = <ArrowUpRight className="size-3 inline mr-1" />;
        } else if (type === 'REVERSAL') {
          color = 'bg-amber-50 text-amber-700 border-amber-200';
          icon = <RotateCcw className="size-3 inline mr-1" />;
        }
        return (
          <Badge variant="outline" className={`font-mono text-[11px] ${color}`}>
            {icon}
            {type}
          </Badge>
        );
      },
    },
    {
      header: 'Item',
      accessorKey: 'itemCode',
      cell: (row) => (
        <div>
          <div className="font-semibold text-slate-900">{row.itemCode}</div>
          <div className="text-xs text-slate-500 truncate max-w-[180px]">{row.itemName}</div>
        </div>
      ),
    },
    {
      header: 'Store / Location',
      accessorKey: 'storeCode',
      cell: (row) => (
        <div className="text-xs">
          <span className="font-medium text-slate-800">{row.storeCode}</span>
          <span className="text-slate-400 mx-1">/</span>
          <span className="text-slate-600">{row.locationCode}</span>
        </div>
      ),
    },
    {
      header: 'In / Out',
      accessorKey: 'quantityIn',
      cell: (row) => (
        <div className="font-mono text-xs">
          {row.quantityIn != null && Number(row.quantityIn) > 0 && (
            <span className="text-emerald-700 font-semibold">+{Number(row.quantityIn).toFixed(2)}</span>
          )}
          {row.quantityOut != null && Number(row.quantityOut) > 0 && (
            <span className="text-rose-700 font-semibold">-{Number(row.quantityOut).toFixed(2)}</span>
          )}
        </div>
      ),
    },
    {
      header: 'Cost / Value',
      accessorKey: 'totalCost',
      cell: (row) => (
        <div className="text-xs font-mono">
          <div className="text-slate-500">@ ₹{Number(row.unitCost || 0).toFixed(2)}</div>
          <div className="font-semibold text-slate-900">₹{Number(row.totalCost || 0).toFixed(2)}</div>
        </div>
      ),
    },
    {
      header: 'Reference',
      accessorKey: 'referenceNo',
      cell: (row) => (
        <div className="text-xs">
          <div className="font-medium text-slate-800">{row.referenceNo || '-'}</div>
          {row.referenceType && <div className="text-[10px] text-slate-500">{row.referenceType}</div>}
        </div>
      ),
    },
  ];

  // Low Stock Table Columns
  const lowStockColumns: ColumnDef<LowStockResponse>[] = [
    {
      header: 'Item',
      accessorKey: 'itemCode',
      cell: (row) => (
        <div>
          <div className="font-semibold text-slate-900">{row.itemCode}</div>
          <div className="text-xs text-slate-600">{row.itemName}</div>
          {row.categoryName && (
            <span className="text-[10px] text-slate-500">{row.categoryName}</span>
          )}
        </div>
      ),
    },
    {
      header: 'Store',
      accessorKey: 'storeCode',
      cell: (row) => (
        <div>
          <div className="font-medium text-slate-800">{row.storeCode}</div>
          <div className="text-xs text-slate-500">{row.storeName}</div>
        </div>
      ),
    },
    {
      header: 'Current Stock',
      accessorKey: 'totalOnHand',
      cell: (row) => (
        <div className="font-mono font-bold text-amber-700">
          {Number(row.totalOnHand).toFixed(2)} {row.uomCode}
        </div>
      ),
    },
    {
      header: 'Min Level',
      accessorKey: 'minStockLevel',
      cell: (row) => (
        <div className="font-mono text-slate-600">{Number(row.minStockLevel || 0).toFixed(2)}</div>
      ),
    },
    {
      header: 'Reorder Level',
      accessorKey: 'reorderLevel',
      cell: (row) => (
        <div className="font-mono text-slate-600">{Number(row.reorderLevel || 0).toFixed(2)}</div>
      ),
    },
    {
      header: 'Deficit Qty',
      accessorKey: 'deficitQty',
      cell: (row) => (
        <div className="font-mono font-bold text-rose-700">
          {Number(row.deficitQty || 0).toFixed(2)}
        </div>
      ),
    },
    {
      header: 'Suggested Reorder',
      accessorKey: 'suggestedReorderQty',
      cell: (row) => (
        <div className="font-mono font-bold text-blue-700">
          {Number(row.suggestedReorderQty || 0).toFixed(2)}
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
            <Boxes className="size-6 text-blue-600" />
            Inventory & Stock Management
          </h1>
          <p className="text-sm text-slate-500">
            Real-time stock balance, ledger audit trail, replenishment alerts, and reconciliation.
          </p>
        </div>
      </div>

      {/* Main Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <TabsList className="bg-slate-100 p-1 border border-slate-200">
          <TabsTrigger value="balances" className="flex items-center gap-2 data-[state=active]:bg-white data-[state=active]:text-blue-700 data-[state=active]:shadow-sm">
            <Boxes className="size-4" /> Current Stock
          </TabsTrigger>
          <TabsTrigger value="ledger" className="flex items-center gap-2 data-[state=active]:bg-white data-[state=active]:text-blue-700 data-[state=active]:shadow-sm">
            <Layers className="size-4" /> Stock Ledger
          </TabsTrigger>
          <TabsTrigger value="low-stock" className="flex items-center gap-2 data-[state=active]:bg-white data-[state=active]:text-blue-700 data-[state=active]:shadow-sm">
            <AlertTriangle className="size-4 text-amber-500" /> Low Stock Alerts
          </TabsTrigger>
          <TabsTrigger value="reconciliation" className="flex items-center gap-2 data-[state=active]:bg-white data-[state=active]:text-blue-700 data-[state=active]:shadow-sm">
            <Scale className="size-4" /> Stock Reconciliation
          </TabsTrigger>
        </TabsList>

        {/* Tab 1: Balances */}
        <TabsContent value="balances" className="space-y-4">
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base font-semibold text-slate-800">Filter Balances</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="space-y-1.5">
                  <Label className="text-xs text-slate-600">Search Items</Label>
                  <div className="relative">
                    <Search className="absolute left-2.5 top-2.5 size-4 text-slate-400" />
                    <Input
                      placeholder="Code, name, location..."
                      value={balanceSearch}
                      onChange={(e) => {
                        setBalanceSearch(e.target.value);
                        setBalancePage(0);
                      }}
                      className="pl-8 text-sm"
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <Label className="text-xs text-slate-600">Store</Label>
                  <Select
                    value={selectedStore}
                    onValueChange={(val) => {
                      setSelectedStore(val);
                      setBalancePage(0);
                    }}
                  >
                    <SelectTrigger className="text-sm">
                      <SelectValue placeholder="All Stores" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ALL">All Stores</SelectItem>
                      {stores?.content?.map((s) => (
                        <SelectItem key={s.id} value={s.id}>
                          {s.storeCode} - {s.storeName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5">
                  <Label className="text-xs text-slate-600">Category</Label>
                  <Select
                    value={selectedCategory}
                    onValueChange={(val) => {
                      setSelectedCategory(val);
                      setBalancePage(0);
                    }}
                  >
                    <SelectTrigger className="text-sm">
                      <SelectValue placeholder="All Categories" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ALL">All Categories</SelectItem>
                      {categories?.content?.map((c) => (
                        <SelectItem key={c.id} value={c.id}>
                          {c.categoryCode} - {c.categoryName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="flex items-center gap-3 pt-6">
                  <Switch
                    id="low-stock-toggle"
                    checked={lowStockOnly}
                    onCheckedChange={(checked) => {
                      setLowStockOnly(checked);
                      setBalancePage(0);
                    }}
                  />
                  <Label htmlFor="low-stock-toggle" className="text-xs font-medium text-slate-700 cursor-pointer">
                    Show low stock only
                  </Label>
                </div>
              </div>
            </CardContent>
          </Card>

          <DataTable
            data={balancesData?.content || []}
            columns={balanceColumns}
            isLoading={isBalancesLoading}
            totalElements={balancesData?.totalElements || 0}
            totalPages={balancesData?.totalPages || 0}
            page={balancePage}
            onPageChange={setBalancePage}
          />
        </TabsContent>

        {/* Tab 2: Ledger */}
        <TabsContent value="ledger" className="space-y-4">
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base font-semibold text-slate-800">Filter Ledger Transactions</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="space-y-1.5">
                  <Label className="text-xs text-slate-600">Search Txn / Item / Ref</Label>
                  <div className="relative">
                    <Search className="absolute left-2.5 top-2.5 size-4 text-slate-400" />
                    <Input
                      placeholder="Transaction #, Item, Ref #..."
                      value={ledgerSearch}
                      onChange={(e) => {
                        setLedgerSearch(e.target.value);
                        setLedgerPage(0);
                      }}
                      className="pl-8 text-sm"
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <Label className="text-xs text-slate-600">Store</Label>
                  <Select
                    value={ledgerStore}
                    onValueChange={(val) => {
                      setLedgerStore(val);
                      setLedgerPage(0);
                    }}
                  >
                    <SelectTrigger className="text-sm">
                      <SelectValue placeholder="All Stores" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ALL">All Stores</SelectItem>
                      {stores?.content?.map((s) => (
                        <SelectItem key={s.id} value={s.id}>
                          {s.storeCode} - {s.storeName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5">
                  <Label className="text-xs text-slate-600">Transaction Type</Label>
                  <Select
                    value={ledgerTxnType}
                    onValueChange={(val) => {
                      setLedgerTxnType(val);
                      setLedgerPage(0);
                    }}
                  >
                    <SelectTrigger className="text-sm">
                      <SelectValue placeholder="All Types" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ALL">All Types</SelectItem>
                      <SelectItem value="RECEIPT">RECEIPT</SelectItem>
                      <SelectItem value="ISSUE">ISSUE</SelectItem>
                      <SelectItem value="ADJUSTMENT_IN">ADJUSTMENT_IN</SelectItem>
                      <SelectItem value="ADJUSTMENT_OUT">ADJUSTMENT_OUT</SelectItem>
                      <SelectItem value="TRANSFER_IN">TRANSFER_IN</SelectItem>
                      <SelectItem value="TRANSFER_OUT">TRANSFER_OUT</SelectItem>
                      <SelectItem value="REVERSAL">REVERSAL</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </CardContent>
          </Card>

          <DataTable
            data={ledgerData?.content || []}
            columns={ledgerColumns}
            isLoading={isLedgerLoading}
            totalElements={ledgerData?.totalElements || 0}
            totalPages={ledgerData?.totalPages || 0}
            page={ledgerPage}
            onPageChange={setLedgerPage}
          />
        </TabsContent>

        {/* Tab 3: Low Stock */}
        <TabsContent value="low-stock" className="space-y-4">
          <DataTable
            data={(lowStockData?.content || []).map((row) => ({ ...row, id: `${row.itemId}-${row.storeId}` }))}
            columns={lowStockColumns}
            isLoading={isLowStockLoading}
            totalElements={lowStockData?.totalElements || 0}
            totalPages={lowStockData?.totalPages || 0}
            page={lowStockPage}
            onPageChange={setLowStockPage}
          />
        </TabsContent>

        {/* Tab 4: Reconciliation */}
        <TabsContent value="reconciliation" className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-bold text-slate-900">Physical vs Ledger Consistency</h2>
              <p className="text-xs text-slate-500">
                Audits stock_balance totals against immutable stock_transactions and serialised asset counts.
              </p>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => refetchRecon()}
              disabled={isReconLoading}
              className="gap-2"
            >
              <RotateCcw className="size-4" /> Re-run Reconciliation
            </Button>
          </div>

          {reconData && (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <Card className={reconData.reconciled ? 'border-emerald-200 bg-emerald-50/40' : 'border-rose-200 bg-rose-50/40'}>
                <CardHeader className="pb-2">
                  <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-600">
                    System State
                  </CardTitle>
                </CardHeader>
                <CardContent className="flex items-center gap-3">
                  {reconData.reconciled ? (
                    <>
                      <CheckCircle2 className="size-8 text-emerald-600" />
                      <div>
                        <div className="text-lg font-bold text-emerald-800">100% Reconciled</div>
                        <div className="text-xs text-emerald-600">Zero ledger or asset discrepancies</div>
                      </div>
                    </>
                  ) : (
                    <>
                      <XCircle className="size-8 text-rose-600" />
                      <div>
                        <div className="text-lg font-bold text-rose-800">Discrepancy Detected</div>
                        <div className="text-xs text-rose-600">Immediate investigation recommended</div>
                      </div>
                    </>
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-600">
                    Ledger Mismatches
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold font-mono text-slate-900">
                    {reconData.totalLedgerMismatchCount}
                  </div>
                  <p className="text-xs text-slate-500">Balance table vs ledger sum variance</p>
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-600">
                    Asset Count Mismatches
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold font-mono text-slate-900">
                    {reconData.totalAssetMismatchCount}
                  </div>
                  <p className="text-xs text-slate-500">Available stock vs AVAILABLE asset rows</p>
                </CardContent>
              </Card>
            </div>
          )}

          {reconData && reconData.ledgerBalanceDiscrepancies?.length > 0 && (
            <Card className="border-amber-200">
              <CardHeader>
                <CardTitle className="text-sm font-semibold text-amber-900">
                  Ledger vs Stored Balance Discrepancies ({reconData.ledgerBalanceDiscrepancies.length})
                </CardTitle>
              </CardHeader>
              <CardContent className="overflow-x-auto">
                <table className="w-full text-xs text-left">
                  <thead className="bg-amber-50 text-amber-900 uppercase font-semibold">
                    <tr>
                      <th className="p-2">Item</th>
                      <th className="p-2">Store / Location</th>
                      <th className="p-2">Ledger Sum</th>
                      <th className="p-2">Stored Balance</th>
                      <th className="p-2">Variance</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-amber-100">
                    {reconData.ledgerBalanceDiscrepancies.map((d, i) => (
                      <tr key={i} className="font-mono">
                        <td className="p-2 font-sans">
                          <span className="font-semibold">{d.itemCode}</span> - {d.itemName}
                        </td>
                        <td className="p-2">{d.storeCode} / {d.locationCode}</td>
                        <td className="p-2">{Number(d.ledgerBalance).toFixed(2)}</td>
                        <td className="p-2">{Number(d.storedBalance).toFixed(2)}</td>
                        <td className="p-2 font-bold text-rose-700">{Number(d.variance).toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent>
            </Card>
          )}

          {reconData && reconData.assetCountDiscrepancies?.length > 0 && (
            <Card className="border-amber-200">
              <CardHeader>
                <CardTitle className="text-sm font-semibold text-amber-900">
                  Stock Balance vs Serialised Asset Discrepancies ({reconData.assetCountDiscrepancies.length})
                </CardTitle>
              </CardHeader>
              <CardContent className="overflow-x-auto">
                <table className="w-full text-xs text-left">
                  <thead className="bg-amber-50 text-amber-900 uppercase font-semibold">
                    <tr>
                      <th className="p-2">Item</th>
                      <th className="p-2">Store / Location</th>
                      <th className="p-2">Available Balance</th>
                      <th className="p-2">Available Assets</th>
                      <th className="p-2">Variance</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-amber-100">
                    {reconData.assetCountDiscrepancies.map((d, i) => (
                      <tr key={i} className="font-mono">
                        <td className="p-2 font-sans">
                          <span className="font-semibold">{d.itemCode}</span> - {d.itemName}
                        </td>
                        <td className="p-2">{d.storeCode} / {d.locationCode}</td>
                        <td className="p-2">{Number(d.availableStockBalance).toFixed(2)}</td>
                        <td className="p-2">{d.availableAssetCount}</td>
                        <td className="p-2 font-bold text-rose-700">{Number(d.variance).toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent>
            </Card>
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}
