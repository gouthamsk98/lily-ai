import { useState } from 'react';
import type { Category, CreateExpenseInput } from '../types';
import { CATEGORIES } from '../types';
import { categoryLabel } from '../utils/formatters';

interface Props {
  onSubmit: (data: CreateExpenseInput) => Promise<void>;
  loading?: boolean;
}

export default function ExpenseForm({ onSubmit, loading }: Props) {
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState<Category>('food');
  const [note, setNote] = useState('');
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = parseFloat(amount);
    if (isNaN(parsed) || parsed < 0) return;

    await onSubmit({
      amount: parsed,
      category,
      note: note || undefined,
      expense_date: date,
    });

    setAmount('');
    setNote('');
  };

  const inputStyle = {
    width: '100%', padding: '10px 12px', borderRadius: 8,
    border: '1px solid #d1d5db', fontSize: 14, boxSizing: 'border-box' as const,
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column' as const, gap: 16 }}>
      <div>
        <label style={{ fontWeight: 600, fontSize: 14, marginBottom: 4, display: 'block' }}>Amount</label>
        <input
          type="number"
          step="0.01"
          min="0"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          placeholder="0.00"
          required
          style={inputStyle}
        />
      </div>
      <div>
        <label style={{ fontWeight: 600, fontSize: 14, marginBottom: 4, display: 'block' }}>Category</label>
        <select value={category} onChange={(e) => setCategory(e.target.value as Category)} style={inputStyle}>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>{categoryLabel(c)}</option>
          ))}
        </select>
      </div>
      <div>
        <label style={{ fontWeight: 600, fontSize: 14, marginBottom: 4, display: 'block' }}>Date</label>
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} style={inputStyle} />
      </div>
      <div>
        <label style={{ fontWeight: 600, fontSize: 14, marginBottom: 4, display: 'block' }}>Note (optional)</label>
        <input
          type="text"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="What was this expense for?"
          style={inputStyle}
        />
      </div>
      <button
        type="submit"
        disabled={loading}
        style={{
          background: '#3b82f6', color: 'white', border: 'none', padding: '12px 24px',
          borderRadius: 8, fontWeight: 600, cursor: 'pointer', fontSize: 16,
        }}
      >
        {loading ? 'Saving...' : 'Add Expense'}
      </button>
    </form>
  );
}
