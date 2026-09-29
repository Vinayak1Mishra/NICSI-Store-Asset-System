'use client';

import { useState } from 'react';
import { usePathname } from 'next/navigation';
import { Menu, Bell, ShieldCheck, LogOut, CheckCircle2, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { RoleSwitcher } from './role-switcher';
import { useAuth } from '@/hooks/use-auth';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

interface TopBarProps {
  onOpenSidebar: () => void;
}

export function TopBar({ onOpenSidebar }: TopBarProps) {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const [showNotifications, setShowNotifications] = useState(false);

  // Compute title from path
  const getBreadcrumbs = () => {
    if (pathname.startsWith('/masters/items')) return 'Masters / Item Master';
    if (pathname.startsWith('/masters/categories')) return 'Masters / Category & Subcategory';
    if (pathname.startsWith('/masters/uoms')) return 'Masters / Units of Measure (UOM)';
    if (pathname.startsWith('/masters/stores')) return 'Masters / Stores & Sites';
    if (pathname.startsWith('/masters/locations')) return 'Masters / Storage Locations';
    if (pathname.startsWith('/masters/policies')) return 'Masters / Item-Store Policy';
    if (pathname.startsWith('/dashboard')) return 'Dashboard';
    if (pathname.startsWith('/requisitions')) return 'Requisitions / My Requisitions';
    if (pathname.startsWith('/approvals')) return 'Requisitions / Pending Approvals';
    if (pathname.startsWith('/purchase')) return 'Procurement / Purchase Orders';
    if (pathname.startsWith('/grn')) return 'Procurement / Goods Receipt Notes (GRN)';
    if (pathname.startsWith('/inspection')) return 'Procurement / Inspection';
    if (pathname.startsWith('/stock')) return 'Inventory / Current Stock';
    if (pathname.startsWith('/ledger')) return 'Inventory / Stock Ledger';
    if (pathname.startsWith('/assets')) return 'Assets / Asset Register';
    if (pathname.startsWith('/reports')) return 'Reports';
    if (pathname.startsWith('/audit')) return 'Audit Trail';
    return 'Store & Asset Management';
  };

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-border bg-card px-4 lg:px-8">
      <div className="flex items-center gap-3">
        <Button
          variant="ghost"
          size="icon"
          className="lg:hidden text-muted-foreground"
          onClick={onOpenSidebar}
          aria-label="Open sidebar"
        >
          <Menu className="size-5" />
        </Button>

        <div className="flex flex-col">
          <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider hidden sm:block">
            NICSI ERP 2.0
          </span>
          <h1 className="text-sm font-semibold text-foreground">{getBreadcrumbs()}</h1>
        </div>
      </div>

      <div className="flex items-center gap-3">
        {/* Role Switcher */}
        <RoleSwitcher />

        {/* Notification Bell */}
        <DropdownMenu open={showNotifications} onOpenChange={setShowNotifications}>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" className="relative text-muted-foreground">
              <Bell className="size-4" />
              <span className="absolute top-2 right-2 size-2 rounded-full bg-blue-600 ring-2 ring-card" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-80 p-2">
            <DropdownMenuLabel className="flex items-center justify-between text-xs font-semibold">
              <span>System Notifications</span>
              <span className="text-[10px] text-muted-foreground font-normal">Phase 1 Live</span>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <div className="space-y-2 py-1 text-xs">
              <div className="flex items-start gap-2 rounded-md p-2 hover:bg-muted/50">
                <CheckCircle2 className="size-4 text-emerald-600 shrink-0 mt-0.5" />
                <div>
                  <p className="font-medium text-foreground">Phase 1 Masters Active</p>
                  <p className="text-muted-foreground text-[11px]">
                    7 master tables operational with optimistic locking & audit logging.
                  </p>
                </div>
              </div>
              <div className="flex items-start gap-2 rounded-md p-2 hover:bg-muted/50">
                <AlertCircle className="size-4 text-blue-600 shrink-0 mt-0.5" />
                <div>
                  <p className="font-medium text-foreground">22 UOMs Pre-seeded</p>
                  <p className="text-muted-foreground text-[11px]">
                    Flyway V8 seed data ready for item cataloging.
                  </p>
                </div>
              </div>
            </div>
          </DropdownMenuContent>
        </DropdownMenu>

        {/* User profile dropdown */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" className="h-8 gap-2 px-2 text-xs font-medium">
              <div className="grid size-6 place-items-center rounded-full bg-primary/10 text-primary font-bold">
                {user?.username?.[0]?.toUpperCase() || 'U'}
              </div>
              <span className="hidden md:inline-block max-w-[100px] truncate">
                {user?.displayName || 'User'}
              </span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel>
              <div className="font-semibold">{user?.displayName}</div>
              <div className="text-[11px] text-muted-foreground font-normal">
                {user?.roles?.[0] || 'User'}
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem className="text-xs">
              <ShieldCheck className="mr-2 size-3.5" />
              Permissions: {user?.permissions?.length || 0}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={logout} className="text-xs text-destructive">
              <LogOut className="mr-2 size-3.5" />
              Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
