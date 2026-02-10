import type { Expense } from '../types';
import { formatCurrency, formatDate, categoryLabel, categoryColor } from '../utils/formatters';
import { Trash2 } from 'lucide-react';

interface Props {
  expenses: Expense[];
  onDelete?: (id: string) => void;
}

export default function ExpenseList({ expenses, onDelete }: Props) {
  if (expenses.length === 0) {
    return <p style={{ color: '#6b7280', textAlign: 'center', padding: 24 }}>No expenses found.</p>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {expenses.map((expense) => (
        <div
          key={expense.id}
          style={{
            background: 'white', borderRadius: 8, padding: '12px 16px',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span
              style={{
                background: categoryColor(expense.category), color: 'white',
                padding: '4px 10px', borderRadius: 12, fontSize: 12, fontWeight: 600,
              }}
            >
              {categoryLabel(expense.category)}
            </span>
            <div>
              <div style={{ fontWeight: 600 }}>{formatCurrency(expense.amount)}</div>
              <div style={{ fontSize: 12, color: '#6b7280' }}>
                {formatDate(expense.expense_date)}
                {expense.note && ` — ${expense.note}`}
              </div>
            </div>
          </div>
          {onDelete && (
            <button
              onClick={() => onDelete(expense.id)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#ef4444' }}
            >
              <Trash2 size={16} />
            </button>
          )}
        </div>
      ))}
    </div>
  );
}
