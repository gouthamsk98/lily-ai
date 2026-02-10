import client from './client';
import type { ExpenseSummary } from '../types';

export const getDailySummary = (date?: string) =>
  client.get<ExpenseSummary>('/analytics/daily', { params: { date } });

export const getWeeklySummary = (date?: string) =>
  client.get<ExpenseSummary>('/analytics/weekly', { params: { date } });

export const getMonthlySummary = (date?: string) =>
  client.get<ExpenseSummary>('/analytics/monthly', { params: { date } });

export const getCategorySummary = (startDate?: string, endDate?: string) =>
  client.get<ExpenseSummary>('/analytics/category', {
    params: { start_date: startDate, end_date: endDate },
  });
