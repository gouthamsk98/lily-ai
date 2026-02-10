import { useState } from 'react';
import { useExpenses } from '../hooks/useExpenses';
import ExpenseList from '../components/ExpenseList';
import type { Category } from '../types';
import { CATEGORIES } from '../types';
import { categoryLabel } from '../utils/formatters';

export default function HistoryPage() {
  const [category, setCategory] = useState<Category | ''>('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const filter = {
    category: category || undefined,
    start_date: startDate || undefined,
    end_date: endDate || undefined,
    per_page: 50,
  };

  const { expenses, loading, removeExpense } = useExpenses(filter);

  const inputStyle = {
    padding: '8px 12px', borderRadius: 6, border: '1px solid #d1d5db', fontSize: 14,
  };

  return (
    <div>
      <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 24 }}>Expense History</h1>

      <div style={{
        display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' as const,
        background: 'white', padding: 16, borderRadius: 8, boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      }}>
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value as Category | '')}
          style={inputStyle}
        >
          <option value="">All Categories</option>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>{categoryLabel(c)}</option>
          ))}
        </select>
        <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} style={inputStyle} />
        <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} style={inputStyle} />
      </div>

      {loading ? (
        <p style={{ textAlign: 'center', color: '#6b7280' }}>Loading...</p>
      ) : (
        <ExpenseList expenses={expenses} onDelete={removeExpense} />
      )}
    </div>
  );
}
