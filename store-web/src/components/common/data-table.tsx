'use client';

import * as React from 'react';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Search, ChevronLeft, ChevronRight, FileX } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';

export interface ColumnDef<T> {
  header: string;
  accessorKey?: keyof T;
  cell?: (row: T) => React.ReactNode;
  className?: string;
  key?: string;
  id?: string;
  render?: (value: any, row?: T) => React.ReactNode;
}

interface DataTableProps<T> {
  columns: ColumnDef<T>[];
  data: T[];
  isLoading?: boolean;
  searchPlaceholder?: string;
  searchValue?: string;
  onSearchChange?: (val: string) => void;
  page?: number;
  pageSize?: number;
  totalPages?: number;
  totalElements?: number;
  onPageChange?: (newPage: number) => void;
  actionButton?: React.ReactNode;
  extraFilters?: React.ReactNode;
}

export function DataTable<T extends Record<string, any>>({
  columns,
  data,
  isLoading,
  searchPlaceholder = 'Search records...',
  searchValue,
  onSearchChange,
  page = 0,
  totalPages = 1,
  totalElements,
  onPageChange,
  actionButton,
  extraFilters,
}: DataTableProps<T>) {
  return (
    <div className="space-y-4">
      {/* Top toolbar */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        <div className="flex flex-1 items-center gap-2 max-w-lg">
          {onSearchChange && (
            <div className="relative flex-1">
              <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
              <Input
                placeholder={searchPlaceholder}
                value={searchValue || ''}
                onChange={(e) => onSearchChange(e.target.value)}
                className="pl-8 h-9 text-xs"
              />
            </div>
          )}
          {extraFilters}
        </div>
        {actionButton && <div className="shrink-0">{actionButton}</div>}
      </div>

      {/* Main Table */}
      <div className="rounded-lg border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader className="bg-muted/50">
            <TableRow>
              {columns.map((col, idx) => (
                <TableHead
                  key={idx}
                  className={`text-xs font-semibold text-muted-foreground whitespace-nowrap py-3 px-4 ${
                    col.className || ''
                  }`}
                >
                  {col.header}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, rIdx) => (
                <TableRow key={rIdx}>
                  {columns.map((_, cIdx) => (
                    <TableCell key={cIdx} className="py-3 px-4">
                      <Skeleton className="h-4 w-full" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : data.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className="h-48 text-center text-muted-foreground"
                >
                  <div className="flex flex-col items-center justify-center space-y-2">
                    <FileX className="size-8 text-muted-foreground/60" />
                    <p className="text-sm font-medium">No records found</p>
                    <p className="text-xs text-muted-foreground">
                      Try adjusting your search criteria or add a new record.
                    </p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              data.map((row, rIdx) => (
                <TableRow
                  key={row.id ? String(row.id) : rIdx}
                  className="hover:bg-muted/40 transition-colors"
                >
                  {columns.map((col, cIdx) => (
                    <TableCell
                      key={cIdx}
                      className={`text-xs py-3 px-4 ${col.className || ''}`}
                    >
                      {col.cell
                        ? col.cell(row)
                        : col.render
                        ? col.render(col.key ? row[col.key] : (col.accessorKey ? row[col.accessorKey] : undefined), row)
                        : col.accessorKey
                        ? String(row[col.accessorKey] ?? '—')
                        : col.key
                        ? String(row[col.key] ?? '—')
                        : '—'}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination Bar */}
      <div className="flex items-center justify-between text-xs text-muted-foreground px-1">
        <div>
          {totalElements !== undefined ? (
            <span>
              Total records: <strong className="text-foreground">{totalElements}</strong>
            </span>
          ) : (
            <span>Showing {data.length} records</span>
          )}
        </div>

        {totalPages > 1 && onPageChange && (
          <div className="flex items-center gap-2">
            <span>
              Page {page + 1} of {totalPages}
            </span>
            <div className="flex items-center gap-1">
              <Button
                variant="outline"
                size="icon"
                className="size-7"
                disabled={page <= 0}
                onClick={() => onPageChange(page - 1)}
              >
                <ChevronLeft className="size-3.5" />
              </Button>
              <Button
                variant="outline"
                size="icon"
                className="size-7"
                disabled={page >= totalPages - 1}
                onClick={() => onPageChange(page + 1)}
              >
                <ChevronRight className="size-3.5" />
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
