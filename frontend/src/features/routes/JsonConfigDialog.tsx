import * as React from 'react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';

interface JsonConfigDialogProps {
  open: boolean;
  title: string;
  content: string;
  onOpenChange: (open: boolean) => void;
  onSave: (content: string) => Promise<void>;
}

export function JsonConfigDialog({ open, title, content, onOpenChange, onSave }: JsonConfigDialogProps) {
  const [value, setValue] = React.useState(content);
  const [editing, setEditing] = React.useState(false);
  const [error, setError] = React.useState('');
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    setValue(content);
    setEditing(false);
    setError('');
  }, [content, open]);

  async function handleSave() {
    try {
      JSON.parse(value);
    } catch (parseError) {
      setError('JSON 格式不正确，请检查后再保存');
      return;
    }
    setSaving(true);
    try {
      await onSave(value);
      setEditing(false);
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[min(96vw,860px)]">
        <DialogHeader>
          <DialogTitle>{title || '查看配置'}</DialogTitle>
          <DialogDescription>原始 JSON 配置文件内容。</DialogDescription>
        </DialogHeader>
        {error && <div className="rounded-2xl border border-clay-error/60 bg-clay-error/15 p-3 text-sm font-bold text-[#FFBDAD]">{error}</div>}
        <Textarea value={value} onChange={(event) => setValue(event.target.value)} readOnly={!editing} rows={16} />
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>关闭</Button>
          {editing ? <Button variant="orange" onClick={handleSave} disabled={saving}>{saving ? '保存中...' : '保存 JSON'}</Button> : <Button variant="primary" onClick={() => setEditing(true)}>编辑此文件</Button>}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
