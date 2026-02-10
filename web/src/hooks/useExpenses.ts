import { useState, useEffect, useCallback } from 'react';
import type { Expense, ExpenseFilter } from '../types';
import { getExpenses, createExpense, deleteExpense } from '../api/expenses';
import type { CreateExpenseInput } from '../types';

export function useExpenses(filter?: ExpenseFilter) {
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchExpenses = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await getExpenses(filter);
      setExpenses(data);
    } catch (err) {
      console.error('Failed to fetch expenses:', err);
    } finally {
      setLoading(false);
    }
  }, [filter?.start_date, filter?.end_date, filter?.category, filter?.page]);

  useEffect(() => {
    fetchExpenses();
  }, [fetchExpenses]);

  const addExpense = async (input: CreateExpenseInput) => {
    const { data } = await createExpense(input);
    setExpenses((prev) => [data, ...prev]);
    return data;
  };

  const removeExpense = async (id: string) => {
    await deleteExpense(id);
    setExpenses((prev) => prev.filter((e) => e.id !== id));
  };

  return { expenses, loading, refetch: fetchExpenses, addExpense, removeExpense };
}
