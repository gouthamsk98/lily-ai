import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDailyStatus } from '../hooks/useDailyStatus';
import { getDailySummary, getWeeklySummary, getMonthlySummary } from '../api/analytics';
import type { ExpenseSummary } from '../types';
import SummaryCard from '../components/SummaryCard';
import DailyReminder from '../components/DailyReminder';
import Charts from '../components/Charts';

export default function Dashboard() {
  const navigate = useNavigate();
  const { status, refetch: refetchStatus } = useDailyStatus();
  const [daily, setDaily] = useState<ExpenseSummary | null>(null);
  const [weekly, setWeekly] = useState<ExpenseSummary | null>(null);
  const [monthly, setMonthly] = useState<ExpenseSummary | null>(null);

  useEffect(() => {
    Promise.all([
      getDailySummary().then(r => setDaily(r.data)),
      getWeeklySummary().then(r => setWeekly(r.data)),
      getMonthlySummary().then(r => setMonthly(r.data)),
    ]);
  }, []);

  const showReminder = status && !status.submitted;

  return (
    <div>
      <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 24 }}>Dashboard</h1>

      {showReminder && (
        <DailyReminder
          onDismiss={refetchStatus}
          onQuickAdd={() => navigate('/add')}
        />
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 16, marginBottom: 32 }}>
        {daily && <SummaryCard title="Today" amount={daily.total} count={daily.count} color="#3b82f6" />}
        {weekly && <SummaryCard title="This Week" amount={weekly.total} count={weekly.count} color="#8b5cf6" />}
        {monthly && <SummaryCard title="This Month" amount={monthly.total} count={monthly.count} color="#10b981" />}
      </div>

      <div style={{ background: 'white', borderRadius: 12, padding: 24, boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
        <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 16 }}>Spending by Category (This Month)</h2>
        {monthly && <Charts data={monthly.by_category} />}
      </div>
    </div>
  );
}
