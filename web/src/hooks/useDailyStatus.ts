import { useState, useEffect } from 'react';
import type { DailyStatus } from '../types';
import { getDailyStatus } from '../api/expenses';

export function useDailyStatus() {
  const [status, setStatus] = useState<DailyStatus | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const { data } = await getDailyStatus();
        setStatus(data);
      } catch (err) {
        console.error('Failed to fetch daily status:', err);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return { status, loading, refetch: async () => {
    const { data } = await getDailyStatus();
    setStatus(data);
  }};
}
