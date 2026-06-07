import client from './client';
import type { NoticeSaveRequest, NoticeVO, PageResult, Result } from '../types';

export const getMyNotices = (params: { page: number; size: number; unconfirmedOnly?: boolean }) =>
  client.get<Result<PageResult<NoticeVO>>>('/api/notices/my', { params });

export const getMyNotice = (id: string) =>
  client.get<Result<NoticeVO | null>>(`/api/notices/my/${id}`);

export const getUnconfirmedNoticeCount = () =>
  client.get<Result<number>>('/api/notices/my/unconfirmed-count');

export const confirmMyNotice = (id: string) =>
  client.put<Result<void>>(`/api/notices/my/${id}/confirm`);

export const getAdminNotices = (params: { page: number; size: number; status?: string }) =>
  client.get<Result<PageResult<NoticeVO>>>('/api/notices/admin', { params });

export const createNotice = (data: NoticeSaveRequest) =>
  client.post<Result<NoticeVO>>('/api/notices/admin', data);

export const updateNotice = (id: string, data: NoticeSaveRequest) =>
  client.put<Result<NoticeVO>>(`/api/notices/admin/${id}`, data);

export const withdrawNotice = (id: string) =>
  client.put<Result<void>>(`/api/notices/admin/${id}/withdraw`);
