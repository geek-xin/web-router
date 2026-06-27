import { Plus, Search, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

interface RouteToolbarProps {
  headingId?: string;
  search: string;
  selectedCount: number;
  onSearchChange: (value: string) => void;
  onAdd: () => void;
  onBatchDelete: () => void;
}

export function RouteToolbar({ headingId, search, selectedCount, onSearchChange, onAdd, onBatchDelete }: RouteToolbarProps) {
  return (
    <div className="route-toolbar flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
      <div className="route-toolbar-heading">
        <p className="text-xs font-black uppercase tracking-[0.18em] text-clay-primary">ROUTE OPERATIONS</p>
        <h2 id={headingId} className="text-2xl font-black text-clay-ink">路由配置</h2>
      </div>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <label className="relative block min-w-[240px]">
          <span className="sr-only">搜索</span>
          <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-clay-ink/50" />
          <Input value={search} onChange={(event) => onSearchChange(event.target.value)} className="pl-10" type="search" placeholder="按名称或默认地址搜索" />
        </label>
        <Button variant="danger" onClick={onBatchDelete} disabled={selectedCount === 0}>
          <Trash2 className="h-4 w-4" />
          {selectedCount === 0 ? '选择删除' : `删除选中(${selectedCount})`}
        </Button>
        <Button variant="orange" onClick={onAdd}>
          <Plus className="h-4 w-4" />
          新增路由
        </Button>
      </div>
    </div>
  );
}
