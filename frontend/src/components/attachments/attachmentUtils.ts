import type { AttachmentVO } from '../../types';

export type AttachmentKind = 'image' | 'pdf' | 'other';

const IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg'];

/** 获取文件扩展名（小写，不含点） */
const getExtension = (fileName?: string): string =>
  fileName?.split('.').pop()?.toLowerCase() ?? '';

/** 判断附件类型：优先看扩展名，再回退到 mimeType */
export const getAttachmentKind = (att: Pick<AttachmentVO, 'fileName' | 'mimeType'>): AttachmentKind => {
  const ext = getExtension(att.fileName);
  const mime = att.mimeType ?? '';
  if (IMAGE_EXTENSIONS.includes(ext) || mime.startsWith('image/')) return 'image';
  if (ext === 'pdf' || mime === 'application/pdf') return 'pdf';
  return 'other';
};

/** 返回大写扩展名角标，如 JPG / PDF；无扩展名时返回 FILE */
export const getFileExtLabel = (fileName?: string): string =>
  (getExtension(fileName) || 'file').toUpperCase();
