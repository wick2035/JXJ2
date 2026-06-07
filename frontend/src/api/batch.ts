import client from './client';
import type {
  AuditAssignmentVO,
  BatchAssignmentGenerateRequest,
  BatchAssignmentGenerateVO,
  BasicAwardImportResult,
  BatchDetailRowVO,
  BatchEvaluationTableVO,
  BatchBasicAwardVO,
  BasicItemVO,
  BatchRankingVO,
  BatchStatsVO,
  BatchVO,
  PageResult,
  Result,
} from '../types';

export const getBatches = (params: { page: number; size: number; status?: string }) =>
  client.get<Result<PageResult<BatchVO>>>('/api/batches', { params });

export const getBatch = (id: string) => client.get<Result<BatchVO>>(`/api/batches/${id}`);

export const createBatch = (data: any) => client.post<Result<BatchVO>>('/api/batches', data);

export const updateBatch = (id: string, data: any) => client.put<Result<BatchVO>>(`/api/batches/${id}`, data);

export const deleteBatch = (id: string) => client.delete<Result<void>>(`/api/batches/${id}`);

export const updateBatchStatus = (id: string, status: string) =>
  client.put<Result<void>>(`/api/batches/${id}/status`, { status });

export const reopenBatch = (id: string, endDate: string) =>
  client.put<Result<void>>(`/api/batches/${id}/status`, { status: 'open_timed', endDate });

export const recalculateBatch = (id: string, reason?: string) =>
  client.post<Result<void>>(`/api/batches/${id}/recalculate`, { reason });

export const getBatchStats = (id: string) =>
  client.get<Result<BatchStatsVO>>(`/api/batches/${id}/stats`);

export const getBatchRanking = (id: string) =>
  client.get<Result<BatchRankingVO[]>>(`/api/batches/${id}/ranking`);

export const getBatchDetails = (id: string) =>
  client.get<Result<BatchDetailRowVO[]>>(`/api/batches/${id}/details`);

export const getBatchEvaluationTable = (id: string) =>
  client.get<Result<BatchEvaluationTableVO>>(`/api/batches/${id}/evaluation-table`);

export const getBatchAssignments = (id: string) =>
  client.get<Result<AuditAssignmentVO[]>>(`/api/batches/${id}/assignments`);

export const generateBatchAssignments = (
  id: string,
  request: boolean | BatchAssignmentGenerateRequest = false,
) => {
  const data = typeof request === 'boolean' ? { replacePending: request } : request;
  return client.post<Result<BatchAssignmentGenerateVO>>(`/api/batches/${id}/assignments/generate`, data);
};

export const exportBatch = (id: string) =>
  client.get<Blob>(`/api/batches/${id}/export`, { responseType: 'blob' });

export const getBatchBasicAwards = (id: string, category?: string) =>
  client.get<Result<BatchBasicAwardVO[]>>(`/api/batches/${id}/basic-awards`, { params: { category } });

export const getMyBatchBasicItems = (id: string) =>
  client.get<Result<BasicItemVO[]>>(`/api/batches/${id}/basic-awards/mine`);

export const importBatchBasicAwards = (id: string, category: string, file: File) => {
  const formData = new FormData();
  formData.append('category', category);
  formData.append('file', file);
  return client.post<Result<BasicAwardImportResult>>(`/api/batches/${id}/basic-awards/import`, formData);
};

export const downloadBasicAwardImportTemplate = (id: string) =>
  client.get<Blob>(`/api/batches/${id}/basic-awards/import-template`, { responseType: 'blob' });
