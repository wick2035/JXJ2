import dayjs from 'dayjs';
import type { BatchVO } from '../types';

export type BatchPhase = 'draft' | 'not_started' | 'open' | 'ended' | 'closed';

/**
 * 推导批次的"有效阶段"。批次四态：
 * - draft       未发布
 * - open_timed  发布·按日期：开始日自动开放、截止日过自动截止
 * - open        发布·手动开放（不按日期，由管理员手动开启/截止），发布即一直可提交
 * - closed      管理员手动关闭
 */
export function getBatchPhase(batch: Pick<BatchVO, 'status' | 'startDate' | 'endDate'>): BatchPhase {
  if (batch.status === 'draft') return 'draft';
  if (batch.status === 'closed') return 'closed';
  if (batch.status === 'open') return 'open'; // 手动开放：总是进行中
  // status === 'open_timed'：按日期判开关
  const now = dayjs();
  if (batch.startDate && now.isBefore(dayjs(batch.startDate).startOf('day'))) return 'not_started';
  if (batch.endDate && now.isAfter(dayjs(batch.endDate).endOf('day'))) return 'ended';
  return 'open';
}

/** 退回(returned)的申报任何阶段都可重新提交；否则只有 open 阶段可提交 */
export function canStudentSubmit(phase: BatchPhase, declStatus?: string): boolean {
  return phase === 'open' || declStatus === 'returned';
}

export const PHASE_LABELS: Record<BatchPhase, string> = {
  draft: '未发布',
  not_started: '未开始',
  open: '进行中',
  ended: '已截止',
  closed: '已截止',
};

export const PHASE_COLORS: Record<BatchPhase, string> = {
  draft: 'default',
  not_started: 'warning',
  open: 'processing',
  ended: 'default',
  closed: 'default',
};
