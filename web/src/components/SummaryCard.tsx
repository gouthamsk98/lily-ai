import { formatCurrency } from '../utils/formatters';

interface Props {
  title: string;
  amount: string | number;
  count: number;
  color?: string;
}

export default function SummaryCard({ title, amount, count, color = '#3b82f6' }: Props) {
  return (
    <div style={{
      background: 'white', borderRadius: 12, padding: 20,
      boxShadow: '0 1px 3px rgba(0,0,0,0.1)', borderTop: `3px solid ${color}`,
    }}>
      <div style={{ fontSize: 14, color: '#6b7280', marginBottom: 4 }}>{title}</div>
      <div style={{ fontSize: 28, fontWeight: 700 }}>{formatCurrency(amount)}</div>
      <div style={{ fontSize: 12, color: '#9ca3af' }}>{count} expense{count !== 1 ? 's' : ''}</div>
    </div>
  );
}
