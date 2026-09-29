'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { PackageCheck, RefreshCw, Filter } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { api } from '@/lib/api-client';

interface ReturnSummaryRow {
  id: string;
  return_no: string;
  return_date: string;
  status: string;
  store_code: string;
  store_name: string;
  item_count: number;
  total_qty: number;
}

const STATUS_OPTIONS = ['ALL', 'DRAFT', 'SUBMITTED', 'RECEIVED', 'POSTED', 'REJECTED', 'CANCELLED'];

const STATUS_COLORS: Record<string, string> = {
  DRAFT:      'bg-slate-100 text-slate-700',
  SUBMITTED:  'bg-blue-100 text-blue-700',
  RECEIVED:   'bg-amber-100 text-amber-700',
  POSTED:     'bg-emerald-100 text-emerald-700',
  REJECTED:   'bg-red-100 text-red-700',
  CANCELLED:  'bg-gray-100 text-gray-500',
};

export default function ReturnsReportPage() {
  const [status, setStatus] = useState('ALL');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [applied, setApplied] = useState({ status: 'ALL', from: '', to: '' });

  const { data, isLoading, refetch } = useQuery<ReturnSummaryRow[]>({
    queryKey: ['report-returns', applied],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (applied.status !== 'ALL') params.set('status', applied.status);
      if (applied.from) params.set('from', applied.from);
      if (applied.to)   params.set('to',   applied.to);
      return api.get<ReturnSummaryRow[]>(`/api/reports/returns/summary?${params.toString()}`);
    },
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-teal-500 to-emerald-600 shadow-md">
          <PackageCheck className="h-6 w-6 text-white" />
        </div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Returns Report</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Summarised view of all material &amp; asset return documents.
          </p>
        </div>
      </div>

      {/* Filters */}
      <Card className="border-border/60">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm flex items-center gap-2">
            <Filter className="h-4 w-4" /> Filters
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 sm:grid-cols-4">
            <div className="space-y-1.5">
              <Label htmlFor="ret-status">Status</Label>
              <Select value={status} onValueChange={setStatus}>
                <SelectTrigger id="ret-status">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {STATUS_OPTIONS.map((s) => (
                    <SelectItem key={s} value={s}>{s}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ret-from">From Date</Label>
              <Input id="ret-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ret-to">To Date</Label>
              <Input id="ret-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </div>
            <div className="flex items-end gap-2">
              <Button className="flex-1" onClick={() => setApplied({ status, from, to })}>
                Apply
              </Button>
              <Button variant="outline" size="icon" onClick={() => refetch()} title="Refresh">
                <RefreshCw className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <Card className="border-border/60">
        <CardContent className="p-0">
          {isLoading ? (
            <div className="flex items-center justify-center h-32 text-muted-foreground text-sm">
              Loading…
            </div>
          ) : !data || data.length === 0 ? (
            <div className="flex items-center justify-center h-32 text-muted-foreground text-sm">
              No returns match the selected filters.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b bg-muted/30">
                  <tr>
                    {['Return No', 'Date', 'Store', 'Status', 'Items', 'Total Qty'].map((h) => (
                      <th key={h} className="px-4 py-3 text-left font-medium text-muted-foreground whitespace-nowrap">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/50">
                  {data.map((row) => (
                    <tr key={row.id} className="hover:bg-muted/20 transition-colors">
                      <td className="px-4 py-3 font-mono font-medium">{row.return_no}</td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        {row.return_date ? new Date(row.return_date).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : '—'}
                      </td>
                      <td className="px-4 py-3">
                        <span className="font-medium">{row.store_code}</span>
                        <span className="text-muted-foreground ml-1 hidden md:inline">— {row.store_name}</span>
                      </td>
                      <td className="px-4 py-3">
                        <Badge className={`text-xs font-medium ${STATUS_COLORS[row.status] ?? ''}`} variant="outline">
                          {row.status}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-center">{row.item_count}</td>
                      <td className="px-4 py-3 text-right font-mono">{Number(row.total_qty).toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <p className="text-xs text-muted-foreground text-right">
        {data ? `${data.length} record(s) shown` : ''} · Real-time data
      </p>
    </div>
  );
}
