export function formatCurrency(amount: string | number): string {
  const num = typeof amount === 'string' ? parseFloat(amount) : amount;
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
  }).format(num);
}

export function formatDate(date: string): string {
  return new Date(date).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

export function categoryLabel(category: string): string {
  return category.charAt(0).toUpperCase() + category.slice(1);
}

export function categoryColor(category: string): string {
  const colors: Record<string, string> = {
    food: '#ef4444',
    entertainment: '#8b5cf6',
    travel: '#3b82f6',
    bills: '#f59e0b',
    shopping: '#10b981',
    other: '#6b7280',
  };
  return colors[category] || '#6b7280';
}
