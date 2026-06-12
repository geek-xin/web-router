import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog';

interface DeleteConfirmDialogProps {
  open: boolean;
  names: string[];
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
}

export function DeleteConfirmDialog({ open, names, onOpenChange, onConfirm }: DeleteConfirmDialogProps) {
  const single = names.length === 1;
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{single ? '确定要删除这个路由吗？' : `确定要删除 ${names.length} 个路由吗？`}</AlertDialogTitle>
          <AlertDialogDescription>
            {single ? <span className="block text-lg font-black text-clay-error">{names[0]}</span> : <span className="block text-clay-ink">{names.join('、')}</span>}
            <span className="mt-2 block">删除后配置文件将被移除，此操作不可撤销。</span>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction onClick={onConfirm}>确认删除</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
