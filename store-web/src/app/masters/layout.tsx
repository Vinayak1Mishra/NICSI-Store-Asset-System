'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import { Package, FolderTree, Scale, Store, MapPin, Sliders } from 'lucide-react';

const masterTabs = [
  { label: 'Item Master', href: '/masters/items', icon: Package },
  { label: 'Categories & Subcategories', href: '/masters/categories', icon: FolderTree },
  { label: 'Units of Measure (UOM)', href: '/masters/uoms', icon: Scale },
  { label: 'Stores & Sites', href: '/masters/stores', icon: Store },
  { label: 'Storage Locations', href: '/masters/locations', icon: MapPin },
  { label: 'Item–Store Policies', href: '/masters/policies', icon: Sliders },
];

export default function MastersLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header and Master Tabs */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Master Catalogs & Configurations
            </h1>
            <p className="text-xs text-muted-foreground mt-1">
              Phase 1 foundational master data for NICSI ERP 2.0 store, inventory and asset operations.
            </p>
          </div>
          <span className="rounded-full bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300 text-xs font-semibold px-2.5 py-0.5 border border-blue-200 hidden sm:inline-block">
            Phase 1 Active
          </span>
        </div>

        {/* Tab strip */}
        <div className="flex gap-2 overflow-x-auto border-b border-border pb-px">
          {masterTabs.map((tab) => {
            const Icon = tab.icon;
            const isActive = pathname.startsWith(tab.href);
            return (
              <Link
                key={tab.href}
                href={tab.href}
                className={cn(
                  'flex items-center gap-2 whitespace-nowrap border-b-2 px-3 py-2 text-xs font-medium transition-colors',
                  isActive
                    ? 'border-primary text-primary font-semibold'
                    : 'border-transparent text-muted-foreground hover:text-foreground hover:border-muted-foreground/30'
                )}
              >
                <Icon className="size-3.5" />
                <span>{tab.label}</span>
              </Link>
            );
          })}
        </div>
      </div>

      {/* Tab content */}
      <div>{children}</div>
    </div>
  );
}
