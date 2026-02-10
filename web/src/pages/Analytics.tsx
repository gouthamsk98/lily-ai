import { useState, useEffect } from 'react';
import { getDailySummary, getWeeklySummary, getMonthlySummary } from '../api/analytics';
import type { ExpenseSummary } from '../types';
import SummaryCard from '../components/SummaryCard';
import Charts from '../components/Charts';

type Period = 'daily' | 'weekly' | 'monthly';

export default function AnalyticsPage() {
  const [period, setPeriod] = useState<Period>('monthly');
  const [summary, setSummary] = useState<ExpenseSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    const fetch = period === 'daily'
      ? getDailySummary()
      : period === 'weekly'
        ? getWeeklySummary()
        : getMonthlySummary();

    fetch.then(r => setSummary(r.data)).finally(() => setLoading(false));
  }, [period]);

  const tabStyle = (active: boolean) => ({
    padding: '8px 20px', borderRadius: 6, border: 'none',
    background: active ? '#3b82f6' : '#e5e7eb',
    color: active ? 'white' : '#374151',
    fontWeight: 600 as const, cursor: 'pointer', fontSize: 14,
  });

  return (
    <div>
      <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 24 }}>Analytics</h1>

      <div style={{ display: 'flex', gap: 8, marginBottom: 24 }}>
        {(['daily', 'weekly', 'monthly'] as Period[]).map((p) => (
          <button key={p} onClick={() => setPeriod(p)} style={tabStyle(period === p)}>
            {p.charAt(0).toUpperCase() + p.slice(1)}
          </button>
        ))}
      </div>

      {loading ? (
        <p style={{ textAlign: 'center', color: '#6b7280' }}>Loading...</p>
      ) : summary ? (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 16, marginBottom: 32 }}>
            <SummaryCard title="Total Spent" amount={summary.total} count={summary.count} />
          </div>

          <div style={{ background: 'white', borderRadius: 12, padding: 24, boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
            <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 16 }}>By Category</h2>
            <Charts data={summary.by_category} />
          </div>

          <div style={{ background: 'white', borderRadius: 12, padding: 24, marginTop: 16, boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
            <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 16 }}>Category Breakdown</h2>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #e5e7eb' }}>
                  <th style={{ textAlign: 'left', padding: 8 }}>Category</th>
                  <th style={{ textAlign: 'right', padding: 8 }}>Amount</th>
                  <th style={{ textAlign: 'right', padding: 8 }}>Count</th>
                </tr>
              </thead>
              <tbody>
                {summary.by_category.map((cat) => (
                  <tr key={cat.category} style={{ borderBottom: '1px solid #f3f4f6' }}>
                    <td style={{ padding: 8, textTransform: 'capitalize' }}>{cat.category}</td>
                    <td style={{ padding: 8, textAlign: 'right', fontWeight: 600 }}>₹{parseFloat(cat.total).toFixed(2)}</td>
                    <td style={{ padding: 8, textAlign: 'right', color: '#6b7280' }}>{cat.count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      ) : null}
    </div>
  );
}
