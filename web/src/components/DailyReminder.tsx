import { useState } from 'react';
import { submitDay } from '../api/expenses';
import { AlertTriangle } from 'lucide-react';

interface Props {
  onDismiss: () => void;
  onQuickAdd: () => void;
}

export default function DailyReminder({ onDismiss, onQuickAdd }: Props) {
  const [submitting, setSubmitting] = useState(false);

  const handleZeroExpense = async () => {
    setSubmitting(true);
    try {
      await submitDay();
      onDismiss();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{
      background: '#fef3c7', border: '1px solid #f59e0b', borderRadius: 8,
      padding: 16, marginBottom: 24, display: 'flex', alignItems: 'center', gap: 12,
    }}>
      <AlertTriangle size={24} color="#d97706" />
      <div style={{ flex: 1 }}>
        <div style={{ fontWeight: 600, color: '#92400e' }}>Daily Expense Entry Required</div>
        <div style={{ fontSize: 14, color: '#78350f' }}>
          You haven't logged any expenses today. Please add your expenses or confirm zero spending.
        </div>
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <button
          onClick={onQuickAdd}
          style={{
            background: '#7c3aed', color: 'white', border: 'none',
            padding: '8px 16px', borderRadius: 6, cursor: 'pointer', fontWeight: 600, fontSize: 13,
          }}
        >
          Add Expense
        </button>
        <button
          onClick={handleZeroExpense}
          disabled={submitting}
          style={{
            background: 'white', color: '#92400e', border: '1px solid #f59e0b',
            padding: '8px 16px', borderRadius: 6, cursor: 'pointer', fontWeight: 600, fontSize: 13,
          }}
        >
          {submitting ? '...' : 'No Expenses Today'}
        </button>
      </div>
    </div>
  );
}
