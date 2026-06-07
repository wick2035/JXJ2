import client from './client';
import type { Result, PageResult, DeclarationVO } from '../types';

export const getDeclarations = (params: { batchId?: string; status?: string; keyword?: string; page: number; size: number }) =>
  client.get<Result<PageResult<DeclarationVO>>>('/api/declarations', { params });

export const getDeclaration = (id: string) =>
  client.get<Result<DeclarationVO>>(`/api/declarations/${id}`);

const toDeclarationFormData = (data: any, files: File[]) => {
  const formData = new FormData();
  formData.append('payload', JSON.stringify(data));
  files.forEach((file) => formData.append('files', file));
  return formData;
};

export const saveDeclaration = (data: any, files?: File[]) => {
  if (files) {
    return client.post<Result<DeclarationVO>>('/api/declarations', toDeclarationFormData(data, files));
  }
  return client.post<Result<DeclarationVO>>('/api/declarations', data);
};

export const submitDeclaration = (id: string) =>
  client.post<Result<DeclarationVO>>(`/api/declarations/${id}/submit`);

export const withdrawDeclaration = (id: string) =>
  client.post<Result<DeclarationVO>>(`/api/declarations/${id}/withdraw`);

export const submitDeclarationPayload = (data: any, files: File[]) =>
  client.post<Result<DeclarationVO>>('/api/declarations/submit', toDeclarationFormData(data, files));

export const deleteDeclaration = (id: string) =>
  client.delete<Result<void>>(`/api/declarations/${id}`);
