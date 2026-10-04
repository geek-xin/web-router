import * as React from 'react';
import { Download, LayoutGrid, List, Plus, Trash2, Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Select } from '@/components/ui/input';
import type { RouteSortKey, RouteStatusFilter } from './types';
import type { RouteViewMode } from '@/lib/preferences';
import { cn } from '@/lib/utils';

interface RouteToolbarProps {
  headingId?: string;
  routeCount: number;
  visibleCount: number;
  status: RouteStatusFilter;
  sort: RouteSortKey;
  selectedCount: number;
  viewMode?: RouteViewMode;
  onViewModeChange?: (value: RouteViewMode) => void;
  onStatusChange: (value: RouteStatusFilter) => void;
  onSortChange: (value: RouteSortKey) => void;
  onAdd: () => void;
  onExport: () => void;
  onImport: (file: File) => void;
  onBatchDelete: () => void;
  exporting?: boolean;
  importing?: boolean;
}

export function RouteToolbar({
  headingId,
  routeCount,
  visibleCount,
  status,
  sort,
  selectedCount,
  viewMode = 'card',
  onViewModeChange,
  onStatusChange,
  onSortChange,
  onAdd,
  onExport,
  onImport,
  onBatchDelete,
  exporting = false,
  importing = false,
}: RouteToolbarProps) {
  const importInputRef = useImportInput(onImport);

  return (
    <div className="toolbar">
      <div className="toolbar-heading">
        <h2 id={headingId} className="toolbar-title">路由列表</h2>
        <p className="toolbar-note">共 {routeCount} 条，当前显示 {visibleCount} 条</p>
      </div>

      <Select value={status} onChange={(event) => onStatusChange(event.target.value as RouteStatusFilter)} aria-label="按状态筛选">
        <option value="all">全部状态</option>
        <option value="enabled">仅运行中</option>
        <option value="disabled">仅已停用</option>
      </Select>

      <Select value={sort} onChange={(event) => onSortChange(event.target.value as RouteSortKey)} aria-label="排序方式">
        <option value="recent">最近创建</option>
        <option value="name">按名称</option>
        <option value="traffic">按流量</option>
        <option value="latency">按延迟</option>
      </Select>

      {selectedCount > 0 && (
        <Button variant="danger" onClick={onBatchDelete}>
          <Trash2 className="h-3.5 w-3.5" />
          删除选中（{selectedCount}）
        </Button>
      )}

      {onViewModeChange && (
        <div className="segmented" role="group" aria-label="路由展示形式">
          <button
            type="button"
            className={cn('segmented-option', viewMode === 'card' && 'segmented-option-active')}
            onClick={() => onViewModeChange('card')}
            aria-pressed={viewMode === 'card'}
            title="卡片视图"
          >
            <LayoutGrid className="h-3.5 w-3.5" aria-hidden="true" />
            <span>卡片</span>
          </button>
          <button
            type="button"
            className={cn('segmented-option', viewMode === 'list' && 'segmented-option-active')}
            onClick={() => onViewModeChange('list')}
            aria-pressed={viewMode === 'list'}
            title="列表视图"
          >
            <List className="h-3.5 w-3.5" aria-hidden="true" />
            <span>列表</span>
          </button>
        </div>
      )}

      <Button variant="outline" onClick={onExport} disabled={exporting}>
        <Download className="h-3.5 w-3.5" />
        {exporting ? '导出中' : '导出'}
      </Button>

      <input
        ref={importInputRef}
        className="hidden"
        type="file"
        accept="application/json,.json"
        onChange={(event) => {
          const file = event.currentTarget.files?.[0];
          event.currentTarget.value = '';
          if (file) {
            onImport(file);
          }
        }}
      />
      <Button variant="outline" onClick={() => importInputRef.current?.click()} disabled={importing}>
        <Upload className="h-3.5 w-3.5" />
        {importing ? '导入中' : '导入'}
      </Button>

      <Button variant="primary" onClick={onAdd}>
        <Plus className="h-3.5 w-3.5" />
        新增路由
      </Button>
    </div>
  );
}

function useImportInput(onImport: (file: File) => void) {
  const ref = React.useRef<HTMLInputElement>(null);
  React.useEffect(() => {
    return () => {
      if (ref.current) {
        ref.current.value = '';
      }
    };
  }, [onImport]);
  return ref;
}
