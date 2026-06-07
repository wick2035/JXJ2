import client from './client';
import type { ClassOptionVO, Result, PageResult, UserImportResult, UserVO } from '../types';

export const getUsers = (params: { page: number; size: number; role?: string; keyword?: string }) =>
  client.get<Result<PageResult<UserVO>>>('/api/users', { params });

export const getClassOptions = () => client.get<Result<ClassOptionVO[]>>('/api/users/classes');

export const getMe = () => client.get<Result<UserVO>>('/api/users/me');

export const updateMe = (data: { email?: string; phone?: string }) =>
  client.put<Result<UserVO>>('/api/users/me', data);

export const createUser = (data: any) => client.post<Result<UserVO>>('/api/users', data);

export const updateUser = (id: string, data: any) => client.put<Result<UserVO>>(`/api/users/${id}`, data);

export const deleteUser = (id: string) => client.delete<Result<void>>(`/api/users/${id}`);

export const resetPassword = (id: string) => client.put<Result<void>>(`/api/users/${id}/reset-password`);

export const setUserStatus = (id: string, status: number) =>
  client.put<Result<void>>(`/api/users/${id}/status`, { status });

export const importUsers = (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return client.post<Result<UserImportResult>>('/api/users/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const downloadUserImportTemplate = () =>
  client.get<Blob>('/api/users/import-template', { responseType: 'blob' });
