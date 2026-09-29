'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LayoutDashboard,
  Settings2,
  FileText,
  Truck,
  Boxes,
  PackageCheck,
  Laptop,
  ShieldCheck,
  ClipboardCheck,
  Archive,
  FileBarChart,
  History,
  ChevronDown,
  X,
  type LucideIcon,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';

interface NavItem {
  label: string;
  href: string;
  badge?: string;
}

interface NavGroup {
  label: string;
  icon: LucideIcon;
  href?: string;
  children?: NavItem[];
}

const navigation: NavGroup[] = [
  { label: 'Dashboard', icon: LayoutDashboard, href: '/dashboard' },
  {
    label: 'Masters',
    icon: Settings2,
    children: [
      { label: 'Item Master', href: '/masters/items' },
      { label: 'Category / Subcategory', href: '/masters/categories' },
      { label: 'Units of Measure', href: '/masters/uoms' },
      { label: 'Stores', href: '/masters/stores' },
      { label: 'Storage Locations', href: '/masters/locations' },
      { label: 'Item–Store Policy', href: '/masters/policies' },
    ],
  },
  {
    label: 'Requisitions',
    icon: FileText,
    children: [
      { label: 'My Requisitions', href: '/requisitions' },
      { label: 'Pending Approval', href: '/approvals' },
    ],
  },
  {
    label: 'Procurement Receipt',
    icon: Truck,
    children: [
      { label: 'Purchase References', href: '/purchase' },
      { label: 'Goods Receipt (GRN)', href: '/grn' },
      { label: 'Inspection', href: '/inspection' },
    ],
  },
  {
    label: 'Inventory',
    icon: Boxes,
    children: [
      { label: 'Current Stock', href: '/inventory' },
      { label: 'Stock Adjustment', href: '/inventory/adjustments' },
      { label: 'Stock Transfer', href: '/stock-transfer' },
    ],
  },
  {
    label: 'Issue & Return',
    icon: PackageCheck,
    children: [
      { label: 'Issues', href: '/issues' },
      { label: 'Returns', href: '/returns' },
      { label: 'Acknowledgements', href: '/acknowledgements' },
    ],
  },
  {
    label: 'Assets',
    icon: Laptop,
    children: [
      { label: 'Asset Register', href: '/assets' },
      { label: 'Employee Assets', href: '/employee-assets' },
      { label: 'Project Assets', href: '/project-assets' },
      { label: 'Asset Transfer', href: '/asset-transfer' },
      { label: 'Repair', href: '/repair' },
      { label: 'Warranty / AMC', href: '/warranty' },
    ],
  },
  { label: 'Software Licences', icon: ShieldCheck, href: '/licences' },
  { label: 'Physical Verification', icon: ClipboardCheck, href: '/verification' },
  { label: 'Condemnation & Disposal', icon: Archive, href: '/disposal' },
  {
    label: 'Reports',
    icon: FileBarChart,
    children: [
      { label: 'All Reports', href: '/reports' },
      { label: 'Current Stock', href: '/reports/stock/current' },
      { label: 'Stock Ledger', href: '/reports/stock/ledger' },
      { label: 'Low Stock Alert', href: '/reports/stock/low' },
      { label: 'Employee Assets', href: '/reports/assets/employee' },
      { label: 'Warranty Expiry', href: '/reports/assets/warranty' },
      { label: 'Disposal Register', href: '/reports/assets/disposal' },
    ],
  },
  { label: 'Audit Trail', icon: History, href: '/audit' },
];

interface AppSidebarProps {
  open: boolean;
  onClose: () => void;
}

export function AppSidebar({ open, onClose }: AppSidebarProps) {
  const pathname = usePathname();
  const [expanded, setExpanded] = useState<string[]>(['Masters']);

  const toggleGroup = (label: string) => {
    setExpanded((prev) =>
      prev.includes(label) ? prev.filter((l) => l !== label) : [...prev, label]
    );
  };

  return (
    <>
      {open && (
        <button
          aria-label="Close navigation"
          className="fixed inset-0 z-40 bg-foreground/40 lg:hidden"
          onClick={onClose}
        />
      )}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-72 flex-col bg-slate-900 text-slate-100 transition-transform duration-200 lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        {/* Brand header */}
        <div className="flex h-16 items-center gap-3 border-b border-slate-800 px-5">
          <div className="grid size-10 place-items-center rounded-md bg-amber-500 text-sm font-bold text-slate-950">
            NI
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-base font-semibold leading-tight text-white">NICSI ERP 2.0</p>
            <p className="text-xs text-slate-400">Store & Asset Management</p>
          </div>
          <Button
            variant="ghost"
            size="icon"
            className="text-slate-400 hover:text-white lg:hidden"
            onClick={onClose}
          >
            <X className="size-5" />
          </Button>
        </div>

        {/* Navigation items */}
        <nav className="flex-1 overflow-y-auto py-3 px-2 space-y-1" aria-label="Main navigation">
          {navigation.map((group) => {
            const Icon = group.icon;
            const hasChildren = group.children && group.children.length > 0;
            const isGroupActive =
              group.href === pathname ||
              group.children?.some((child) => pathname.startsWith(child.href));
            const isExpanded = expanded.includes(group.label);

            if (!hasChildren && group.href) {
              const isActive = pathname === group.href;
              return (
                <Link
                  key={group.label}
                  href={group.href}
                  onClick={onClose}
                  className={cn(
                    'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors text-slate-300 hover:bg-slate-800 hover:text-white',
                    isActive && 'bg-blue-600 font-semibold text-white'
                  )}
                >
                  <Icon className="size-4 shrink-0" />
                  <span className="flex-1 truncate">{group.label}</span>
                </Link>
              );
            }

            return (
              <div key={group.label} className="space-y-0.5">
                <button
                  type="button"
                  onClick={() => toggleGroup(group.label)}
                  className={cn(
                    'flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm font-medium transition-colors text-slate-300 hover:bg-slate-800 hover:text-white',
                    isGroupActive && 'text-white'
                  )}
                >
                  <Icon className="size-4 shrink-0" />
                  <span className="flex-1 truncate">{group.label}</span>
                  <ChevronDown
                    className={cn(
                      'size-4 text-slate-400 transition-transform duration-200',
                      isExpanded && 'rotate-180'
                    )}
                  />
                </button>

                {isExpanded && group.children && (
                  <div className="ml-7 space-y-0.5 border-l border-slate-800 pl-3">
                    {group.children.map((child) => {
                      const isChildActive = pathname === child.href;
                      return (
                        <Link
                          key={child.href}
                          href={child.href}
                          onClick={onClose}
                          className={cn(
                            'block rounded-md py-1.5 px-2 text-xs font-medium transition-colors text-slate-400 hover:bg-slate-800/60 hover:text-slate-200',
                            isChildActive && 'bg-blue-600/30 font-semibold text-blue-300 border-l-2 border-blue-500'
                          )}
                        >
                          {child.label}
                        </Link>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </nav>

        {/* Footer info */}
        <div className="border-t border-slate-800 p-4 text-xs text-slate-400">
          <div className="flex items-center justify-between">
            <span className="font-semibold text-slate-300">Phase 1 (Masters)</span>
            <span className="rounded bg-emerald-950 px-1.5 py-0.5 text-[10px] font-bold text-emerald-400 border border-emerald-800">
              Active
            </span>
          </div>
          <p className="mt-1 text-[11px] text-slate-500">FY 2026–27 · NICSI ERP 2.0</p>
        </div>
      </aside>
    </>
  );
}
