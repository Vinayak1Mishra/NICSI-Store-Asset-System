'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api-client';
import { ItemCategoryResponse, ItemSubcategoryResponse, PageResponse } from '@/types/master';
import { DataTable, ColumnDef } from '@/components/common/data-table';
import { StatusBadge } from '@/components/common/status-badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Plus, FolderTree, Layers, RefreshCw } from 'lucide-react';
import { toast } from 'sonner';

export default function CategoriesPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [selectedCategory, setSelectedCategory] = useState<ItemCategoryResponse | null>(null);

  // Dialog states
  const [isCategoryModalOpen, setIsCategoryModalOpen] = useState(false);
  const [isSubcategoryModalOpen, setIsSubcategoryModalOpen] = useState(false);

  // Category form
  const [catCode, setCatCode] = useState('');
  const [catName, setCatName] = useState('');
  const [catDesc, setCatDesc] = useState('');
  const [catSort, setCatSort] = useState(0);

  // Subcategory form
  const [subCode, setSubCode] = useState('');
  const [subName, setSubName] = useState('');
  const [subDesc, setSubDesc] = useState('');
  const [subSort, setSubSort] = useState(0);

  // Fetch Categories
  const { data: categoriesData, isLoading: isCatLoading, refetch: refetchCats } = useQuery({
    queryKey: ['categories', page, search],
    queryFn: async () => {
      const params = new URLSearchParams({ page: page.toString(), size: '20' });
      if (search) params.append('search', search);
      return api.get<PageResponse<ItemCategoryResponse>>(`/api/store/categories?${params.toString()}`);
    },
  });

  // Fetch Subcategories for selected category
  const { data: subcategoriesData, isLoading: isSubLoading, refetch: refetchSubs } = useQuery({
    queryKey: ['subcategories', selectedCategory?.id],
    queryFn: async () => {
      if (!selectedCategory) return null;
      return api.get<PageResponse<ItemSubcategoryResponse>>(
        `/api/store/categories/${selectedCategory.id}/subcategories`
      );
    },
    enabled: !!selectedCategory,
  });

  // Create Category mutation
  const createCategoryMutation = useMutation({
    mutationFn: async () => {
      return api.post<ItemCategoryResponse>('/api/store/categories', {
        categoryCode: catCode.trim().toUpperCase(),
        categoryName: catName.trim(),
        description: catDesc.trim() || undefined,
        sortOrder: Number(catSort),
      });
    },
    onSuccess: (cat) => {
      toast.success(`Category ${cat.categoryName} created`);
      setIsCategoryModalOpen(false);
      setCatCode('');
      setCatName('');
      setCatDesc('');
      setCatSort(0);
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create category');
    },
  });

  // Create Subcategory mutation
  const createSubcategoryMutation = useMutation({
    mutationFn: async () => {
      if (!selectedCategory) throw new Error('No category selected');
      return api.post<ItemSubcategoryResponse>(
        `/api/store/categories/${selectedCategory.id}/subcategories`,
        {
          subcategoryCode: subCode.trim().toUpperCase(),
          subcategoryName: subName.trim(),
          description: subDesc.trim() || undefined,
          sortOrder: Number(subSort),
        }
      );
    },
    onSuccess: (sub) => {
      toast.success(`Subcategory ${sub.subcategoryName} created`);
      setIsSubcategoryModalOpen(false);
      setSubCode('');
      setSubName('');
      setSubDesc('');
      setSubSort(0);
      queryClient.invalidateQueries({ queryKey: ['subcategories', selectedCategory?.id] });
    },
    onError: (err: ApiError) => {
      toast.error(err.message || 'Failed to create subcategory');
    },
  });

  const categoryColumns: ColumnDef<ItemCategoryResponse>[] = [
    {
      header: 'Category Code',
      accessorKey: 'categoryCode',
      cell: (row) => <span className="font-semibold text-foreground">{row.categoryCode}</span>,
    },
    {
      header: 'Category Name',
      accessorKey: 'categoryName',
    },
    {
      header: 'Sort Order',
      accessorKey: 'sortOrder',
      cell: (row) => <span className="font-mono text-muted-foreground">{row.sortOrder}</span>,
    },
    {
      header: 'Status',
      cell: (row) => <StatusBadge status={row.active} />,
    },
    {
      header: 'Subcategories',
      cell: (row) => (
        <Button
          variant={selectedCategory?.id === row.id ? 'default' : 'outline'}
          size="sm"
          className="h-7 text-xs gap-1"
          onClick={() => setSelectedCategory(row)}
        >
          <Layers className="size-3" />
          {selectedCategory?.id === row.id ? 'Selected' : 'View Subcategories'}
        </Button>
      ),
    },
  ];

  const subcategoryColumns: ColumnDef<ItemSubcategoryResponse>[] = [
    {
      header: 'Subcategory Code',
      accessorKey: 'subcategoryCode',
      cell: (row) => <span className="font-semibold text-foreground">{row.subcategoryCode}</span>,
    },
    {
      header: 'Subcategory Name',
      accessorKey: 'subcategoryName',
    },
    {
      header: 'Description',
      accessorKey: 'description',
      cell: (row) => <span className="text-muted-foreground truncate">{row.description || '—'}</span>,
    },
    {
      header: 'Status',
      cell: (row) => <StatusBadge status={row.active} />,
    },
  ];

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-card p-4 rounded-lg border border-border">
        <div>
          <h2 className="text-base font-semibold flex items-center gap-2">
            <FolderTree className="size-4 text-primary" />
            Classification Hierarchy (Category & Subcategory)
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Two-tier controlled classification for consumables, assets, and software licences.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              refetchCats();
              if (selectedCategory) refetchSubs();
            }}
            className="gap-1 text-xs"
          >
            <RefreshCw className="size-3.5" />
            Refresh
          </Button>
          <Button size="sm" onClick={() => setIsCategoryModalOpen(true)} className="gap-1 text-xs">
            <Plus className="size-3.5" />
            New Category
          </Button>
        </div>
      </div>

      {/* 2-Column or Stacked Master Detail */}
      <div className="grid gap-6 lg:grid-cols-12">
        {/* Categories Table */}
        <div className={selectedCategory ? 'lg:col-span-7' : 'lg:col-span-12'}>
          <div className="space-y-2 mb-3">
            <h3 className="text-sm font-semibold text-foreground">Categories (Tier 1)</h3>
          </div>
          <DataTable
            columns={categoryColumns}
            data={categoriesData?.content || []}
            isLoading={isCatLoading}
            searchPlaceholder="Search category code or name..."
            searchValue={search}
            onSearchChange={(v) => {
              setSearch(v);
              setPage(0);
            }}
            page={page}
            totalPages={categoriesData?.totalPages || 1}
            totalElements={categoriesData?.totalElements}
            onPageChange={setPage}
          />
        </div>

        {/* Subcategories Panel */}
        {selectedCategory && (
          <div className="lg:col-span-5 space-y-3">
            <Card className="border-border">
              <CardHeader className="pb-3 flex flex-row items-center justify-between">
                <div>
                  <CardTitle className="text-sm">
                    Subcategories for {selectedCategory.categoryName}
                  </CardTitle>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Category: <code className="font-mono text-primary">{selectedCategory.categoryCode}</code>
                  </p>
                </div>
                <Button
                  size="sm"
                  className="h-8 text-xs gap-1"
                  onClick={() => setIsSubcategoryModalOpen(true)}
                >
                  <Plus className="size-3" />
                  Add Subcategory
                </Button>
              </CardHeader>
              <CardContent className="p-3">
                <DataTable
                  columns={subcategoryColumns}
                  data={subcategoriesData?.content || []}
                  isLoading={isSubLoading}
                  searchPlaceholder="Filter subcategories..."
                />
              </CardContent>
            </Card>
          </div>
        )}
      </div>

      {/* Create Category Dialog */}
      <Dialog open={isCategoryModalOpen} onOpenChange={setIsCategoryModalOpen}>
        <DialogContent className="sm:max-w-[460px]">
          <DialogHeader>
            <DialogTitle>Add Category</DialogTitle>
            <DialogDescription className="text-xs">
              Create a new top-level classification category.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              createCategoryMutation.mutate();
            }}
            className="space-y-3 py-2"
          >
            <div className="space-y-1.5">
              <Label className="text-xs">Category Code *</Label>
              <Input
                required
                maxLength={30}
                placeholder="e.g. IT-HARDWARE"
                value={catCode}
                onChange={(e) => setCatCode(e.target.value)}
                className="h-8 text-xs font-mono uppercase"
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">Category Name *</Label>
              <Input
                required
                maxLength={120}
                placeholder="e.g. Information Technology Hardware"
                value={catName}
                onChange={(e) => setCatName(e.target.value)}
                className="h-8 text-xs"
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">Description</Label>
              <Input
                maxLength={500}
                placeholder="Category scope or notes"
                value={catDesc}
                onChange={(e) => setCatDesc(e.target.value)}
                className="h-8 text-xs"
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">Sort Order</Label>
              <Input
                type="number"
                value={catSort}
                onChange={(e) => setCatSort(parseInt(e.target.value) || 0)}
                className="h-8 text-xs"
              />
            </div>

            <DialogFooter className="pt-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setIsCategoryModalOpen(false)}
              >
                Cancel
              </Button>
              <Button type="submit" size="sm" disabled={createCategoryMutation.isPending}>
                {createCategoryMutation.isPending ? 'Saving...' : 'Create Category'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Create Subcategory Dialog */}
      <Dialog open={isSubcategoryModalOpen} onOpenChange={setIsSubcategoryModalOpen}>
        <DialogContent className="sm:max-w-[460px]">
          <DialogHeader>
            <DialogTitle>Add Subcategory</DialogTitle>
            <DialogDescription className="text-xs">
              Add subcategory under {selectedCategory?.categoryName} ({selectedCategory?.categoryCode})
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              createSubcategoryMutation.mutate();
            }}
            className="space-y-3 py-2"
          >
            <div className="space-y-1.5">
              <Label className="text-xs">Subcategory Code *</Label>
              <Input
                required
                maxLength={30}
                placeholder="e.g. LAPTOPS"
                value={subCode}
                onChange={(e) => setSubCode(e.target.value)}
                className="h-8 text-xs font-mono uppercase"
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">Subcategory Name *</Label>
              <Input
                required
                maxLength={120}
                placeholder="e.g. Laptops & Notebooks"
                value={subName}
                onChange={(e) => setSubName(e.target.value)}
                className="h-8 text-xs"
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">Description</Label>
              <Input
                maxLength={500}
                placeholder="Subcategory scope"
                value={subDesc}
                onChange={(e) => setSubDesc(e.target.value)}
                className="h-8 text-xs"
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">Sort Order</Label>
              <Input
                type="number"
                value={subSort}
                onChange={(e) => setSubSort(parseInt(e.target.value) || 0)}
                className="h-8 text-xs"
              />
            </div>

            <DialogFooter className="pt-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setIsSubcategoryModalOpen(false)}
              >
                Cancel
              </Button>
              <Button type="submit" size="sm" disabled={createSubcategoryMutation.isPending}>
                {createSubcategoryMutation.isPending ? 'Saving...' : 'Add Subcategory'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
