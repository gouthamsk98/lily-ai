export interface User {
  id: string;
  email: string;
  name: string;
  notification_time: string;
  created_at: string;
}

export interface Expense {
  id: string;
  user_id: string;
  amount: string;
  category: Category;
  note: string | null;
  expense_date: string;
  created_at: string;
  updated_at: string;
}

export type Category = 'food' | 'entertainment' | 'travel' | 'bills' | 'shopping' | 'other';

export const CATEGORIES: Category[] = ['food', 'entertainment', 'travel', 'bills', 'shopping', 'other'];

export interface CreateExpenseInput {
  amount: number;
  category: Category;
  note?: string;
  expense_date?: string;
}

export interface UpdateExpenseInput {
  amount?: number;
  category?: Category;
  note?: string;
  expense_date?: string;
}

export interface ExpenseFilter {
  start_date?: string;
  end_date?: string;
  category?: Category;
  page?: number;
  per_page?: number;
}

export interface CategorySummary {
  category: string;
  total: string;
  count: number;
}

export interface ExpenseSummary {
  total: string;
  count: number;
  by_category: CategorySummary[];
}

export interface DailyStatus {
  submitted: boolean;
  date: string;
}
