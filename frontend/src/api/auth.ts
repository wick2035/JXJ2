import client from './client';
import type { Result, LoginResponse } from '../types';

export const login = (loginId: string, password: string) =>
  client.post<Result<LoginResponse>>('/api/auth/login', { loginId, password });

export const changePassword = (oldPassword: string, newPassword: string) =>
  client.put<Result<void>>('/api/auth/password', { oldPassword, newPassword });
