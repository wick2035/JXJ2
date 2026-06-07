import client from './client';
import type { Result, PageResult, OperationLogVO } from '../types';

export interface OperationLogQuery {
  page: number;
  size: number;
  role?: string;
  module?: string;
  keyword?: string;
  startDate?: string;
  endDate?: string;
}

export const getOperationLogs = (params: OperationLogQuery) =>
  client.get<Result<PageResult<OperationLogVO>>>('/api/operation-logs', { params });

export const deleteOperationLogs = (ids: string[], secondaryPassword: string) =>
  client.post<Result<void>>('/api/operation-logs/delete', { ids, secondaryPassword });
