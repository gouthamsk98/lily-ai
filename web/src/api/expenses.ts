import client from './client';
import type { Expense, CreateExpenseInput, UpdateExpenseInput, ExpenseFilter, DailyStatus } from '../types';

export const createExpense = (data: CreateExpenseInput) =>
  client.post<Expense>('/expenses', data);

export const getExpenses = (filter?: ExpenseFilter) =>
  client.get<Expense[]>('/expenses', { params: filter });

export const getExpense = (id: string) =>
  client.get<Expense>(`/expenses/${id}`);

export const updateExpense = (id: string, data: UpdateExpenseInput) =>
  client.put<Expense>(`/expenses/${id}`, data);

export const deleteExpense = (id: string) =>
  client.delete(`/expenses/${id}`);

export const getDailyStatus = () =>
  client.get<DailyStatus>('/daily-status');

export const submitDay = (date?: string) =>
  client.post<DailyStatus>('/daily-status/submit', { date });
