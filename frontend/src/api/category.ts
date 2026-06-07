import client from './client';
import type { CategoryMeta, Result } from '../types';

export const getCategories = () =>
  client.get<Result<CategoryMeta[]>>('/api/categories');

export const createCategory = (data: Omit<CategoryMeta, 'id'>) =>
  client.post<Result<CategoryMeta>>('/api/categories', data);

export const updateCategory = (id: string, data: Omit<CategoryMeta, 'id'>) =>
  client.put<Result<CategoryMeta>>(`/api/categories/${id}`, data);

export const deleteCategory = (id: string) =>
  client.delete<Result<void>>(`/api/categories/${id}`);
