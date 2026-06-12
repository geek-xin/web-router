import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function stripProtocol(value?: string | null): string {
  return (value || '').replace(/^https?:\/\//, '');
}

export function normalizedOptionalText(value?: string | null): string | null {
  const text = (value || '').trim();
  return text || null;
}

export function formatDuration(ms?: number | null): string {
  const value = Number(ms || 0);
  if (value >= 1000) {
    return (value / 1000).toFixed(value >= 10000 ? 0 : 1) + 's';
  }
  return value + 'ms';
}

export function formatTime(value?: string | null): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleTimeString('zh-CN', { hour12: false });
}

export async function copyText(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand('copy');
  textarea.remove();
}
