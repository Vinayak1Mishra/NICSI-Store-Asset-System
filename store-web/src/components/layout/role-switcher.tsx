'use client';

import { useAuth, MOCK_ROLES } from '@/hooks/use-auth';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { UserCog } from 'lucide-react';
import { toast } from 'sonner';

export function RoleSwitcher() {
  const { user, login } = useAuth();

  const handleRoleChange = async (username: string) => {
    try {
      const newUser = await login(username);
      toast.success(`Switched role to ${newUser.displayName}`);
    } catch {
      toast.error('Failed to switch role');
    }
  };

  return (
    <div className="flex items-center gap-2">
      <UserCog className="size-4 text-muted-foreground hidden sm:block" />
      <Select
        value={user?.username || 'admin'}
        onValueChange={handleRoleChange}
      >
        <SelectTrigger className="h-8 text-xs font-medium w-[170px] bg-background">
          <SelectValue placeholder="Select role" />
        </SelectTrigger>
        <SelectContent>
          {MOCK_ROLES.map((r) => (
            <SelectItem key={r.username} value={r.username} className="text-xs">
              {r.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
