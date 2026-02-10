import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ExpenseForm from '../components/ExpenseForm';
import { createExpense } from '../api/expenses';
import type { CreateExpenseInput } from '../types';

export default function AddExpense() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (data: CreateExpenseInput) => {
    setLoading(true);
    try {
      await createExpense(data);
      setSuccess(true);
      setTimeout(() => {
        navigate('/');
      }, 1500);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 500, margin: '0 auto' }}>
      <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 24 }}>Add Expense</h1>

      {success && (
        <div style={{
          background: '#d1fae5', color: '#065f46', padding: 12, borderRadius: 8,
          marginBottom: 16, fontWeight: 600,
        }}>
          ✓ Expense added successfully! Redirecting...
        </div>
      )}

      <div style={{
        background: 'white', borderRadius: 12, padding: 24,
        boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      }}>
        <ExpenseForm onSubmit={handleSubmit} loading={loading} />
      </div>
    </div>
  );
}
