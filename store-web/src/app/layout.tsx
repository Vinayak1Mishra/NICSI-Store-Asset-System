'use client';

import { useState } from 'react';
import './globals.css';
import { Providers } from './providers';
import { AppSidebar } from '@/components/layout/app-sidebar';
import { TopBar } from '@/components/layout/top-bar';

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <html lang="en">
      <head>
        <title>NICSI ERP 2.0 - Store & Asset Management</title>
        <meta
          name="description"
          content="NICSI Store, Inventory & Asset Lifecycle Management System"
        />
      </head>
      <body className="bg-background text-foreground antialiased min-h-screen">
        <Providers>
          <div className="flex min-h-screen">
            {/* Sidebar navigation */}
            <AppSidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

            {/* Main content area */}
            <div className="flex flex-1 flex-col lg:pl-72">
              <TopBar onOpenSidebar={() => setSidebarOpen(true)} />
              <main className="flex-1 p-4 lg:p-8 bg-slate-50/50 dark:bg-background">
                {children}
              </main>
            </div>
          </div>
        </Providers>
      </body>
    </html>
  );
}
