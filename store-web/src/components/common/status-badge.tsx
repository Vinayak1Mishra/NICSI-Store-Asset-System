import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

interface StatusBadgeProps {
  status: string | boolean;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const displayStatus = typeof status === 'boolean' ? (status ? 'ACTIVE' : 'INACTIVE') : String(status);
  const s = displayStatus.toLowerCase();

  let variantClass = 'bg-amber-100 text-amber-800 border-amber-300';

  if (s.includes('active') || s.includes('approved') || s.includes('posted') || s.includes('healthy') || s.includes('available') || s.includes('accepted')) {
    variantClass = 'bg-emerald-100 text-emerald-800 border-emerald-300 dark:bg-emerald-950 dark:text-emerald-300';
  } else if (s.includes('inactive') || s.includes('reject') || s.includes('missing') || s.includes('cancelled') || s.includes('disposed')) {
    variantClass = 'bg-rose-100 text-rose-800 border-rose-300 dark:bg-rose-950 dark:text-rose-300';
  } else if (s.includes('draft') || s.includes('pending') || s.includes('under')) {
    variantClass = 'bg-amber-100 text-amber-800 border-amber-300 dark:bg-amber-950 dark:text-amber-300';
  }

  return (
    <Badge
      variant="outline"
      className={cn('font-semibold text-[10px] tracking-wide uppercase px-2 py-0.5 rounded-full border', variantClass, className)}
    >
      {displayStatus}
    </Badge>
  );
}
