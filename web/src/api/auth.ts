import client from './client';
import type { User } from '../types';

export const getMe = () => client.get<User>('/auth/me');

export const updateProfile = (data: { name?: string; notification_time?: string }) =>
  client.put<User>('/users/profile', data);
