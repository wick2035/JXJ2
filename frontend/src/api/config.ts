import client from './client';
import type { Result } from '../types';

export const changeSecondaryPassword = (oldPassword: string, newPassword: string) =>
  client.put<Result<void>>('/api/config/secondary-password', { oldPassword, newPassword });
