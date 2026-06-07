import client from './client';

/**
 * 经鉴权接口拉取附件并转为本地 blob 地址。
 * axios 请求拦截器会自动带上 Authorization 头，因此未登录无法访问。
 */
export const fetchAttachmentObjectUrl = async (id: string): Promise<string> => {
  const res = await client.get(`/api/attachments/${id}/file`, { responseType: 'blob' });
  return URL.createObjectURL(res.data as Blob);
};

/** 拉取附件后在新标签页打开（替代原先直接 window.open 静态直链） */
export const openAttachmentInNewTab = async (id: string): Promise<void> => {
  const objectUrl = await fetchAttachmentObjectUrl(id);
  window.open(objectUrl, '_blank', 'noopener,noreferrer');
};
