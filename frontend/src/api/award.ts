import client from './client';
import type { Result, AwardVO, AwardLevelDef } from '../types';

export const getAwards = (category?: string) =>
  client.get<Result<AwardVO[]>>('/api/awards', { params: { category } });

export const getAward = (id: string) => client.get<Result<AwardVO>>(`/api/awards/${id}`);

export const createAward = (data: any) => client.post<Result<AwardVO>>('/api/awards', data);

export const updateAward = (id: string, data: any) => client.put<Result<AwardVO>>(`/api/awards/${id}`, data);

export const deleteAward = (id: string) => client.delete<Result<void>>(`/api/awards/${id}`);

export const getLevels = () => client.get<Result<AwardLevelDef[]>>('/api/award-levels');

export const getBatchAwards = (batchId: string, category?: string) =>
  client.get<Result<AwardVO[]>>(`/api/batches/${batchId}/awards`, { params: { category } });
