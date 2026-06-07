import client from './client';
import type { Result, PageResult, DeclarationVO, AuditQueueStatsVO } from '../types';

export type AuditQueueScope = 'mine' | 'all' | 'assigned' | 'unassigned';
export interface AuditCorrectionPayload {
  action: 'approve' | 'reject' | 'return';
  comment?: string;
  itemScoreAdjustments?: { itemId: string; finalScore: number }[];
}

export const getPendingAudits = (params: { scope?: AuditQueueScope; batchId?: string; keyword?: string; page: number; size: number }) =>
  client.get<Result<PageResult<DeclarationVO>>>('/api/audit/pending', { params });

export const getFinishedAudits = (params: { scope?: AuditQueueScope; batchId?: string; keyword?: string; page: number; size: number }) =>
  client.get<Result<PageResult<DeclarationVO>>>('/api/audit/finished', { params });

export const getAuditQueueStats = (params: { batchId?: string; keyword?: string }) =>
  client.get<Result<AuditQueueStatsVO>>('/api/audit/stats', { params });

export const auditDeclaration = (id: string, data: AuditCorrectionPayload) =>
  client.post<Result<void>>(`/api/audit/declarations/${id}`, data);

export const correctAuditDeclaration = (id: string, data: AuditCorrectionPayload) =>
  client.post<Result<void>>(`/api/audit/declarations/${id}/correction`, data);
