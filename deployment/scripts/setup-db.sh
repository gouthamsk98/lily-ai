#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-budget_tracker}"
DB_USER="${DB_USER:-budget_user}"
DB_PASS="${DB_PASS:-budget_pass}"

echo "Setting up database: ${DB_NAME} on ${DB_HOST}:${DB_PORT}"

export PGPASSWORD="$DB_PASS"

# Create database if it doesn't exist
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -tc \
  "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" | grep -q 1 || \
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "CREATE DATABASE ${DB_NAME}"

echo "Database ${DB_NAME} is ready."
echo "Migrations will be run automatically by the backend on startup."
