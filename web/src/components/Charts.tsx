import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import type { CategorySummary } from '../types';
import { categoryLabel, categoryColor } from '../utils/formatters';

interface Props {
  data: CategorySummary[];
}

export default function Charts({ data }: Props) {
  if (data.length === 0) {
    return <p style={{ color: '#6b7280', textAlign: 'center' }}>No data to display.</p>;
  }

  const chartData = data.map((item) => ({
    name: categoryLabel(item.category),
    value: parseFloat(item.total),
    color: categoryColor(item.category),
  }));

  return (
    <ResponsiveContainer width="100%" height={300}>
      <PieChart>
        <Pie
          data={chartData}
          dataKey="value"
          nameKey="name"
          cx="50%"
          cy="50%"
          outerRadius={100}
          label={(props: any) => `${props.name ?? ''} ${((props.percent ?? 0) * 100).toFixed(0)}%`}
        >
          {chartData.map((entry, index) => (
            <Cell key={index} fill={entry.color} />
          ))}
        </Pie>
        <Tooltip formatter={(value: any) => `$${Number(value ?? 0).toFixed(2)}`} />
        <Legend />
      </PieChart>
    </ResponsiveContainer>
  );
}
