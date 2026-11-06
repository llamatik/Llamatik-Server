#!/usr/bin/env bash
# =============================================================================
# scripts/setup-db.sh
#
# Creates the Llamatik PostgreSQL database and schema.
#
# Usage:
#   ./scripts/setup-db.sh [OPTIONS]
#
# Options:
#   -h HOST      PostgreSQL host        (default: localhost)
#   -p PORT      PostgreSQL port        (default: 5432)
#   -U USER      PostgreSQL superuser   (default: postgres)
#   -d DBNAME    Database name to create (default: llamatik)
#
# Examples:
#   ./scripts/setup-db.sh
#   ./scripts/setup-db.sh -U myuser -d mydb
#   ./scripts/setup-db.sh -h 192.168.1.10 -p 5433
#
# The script is idempotent: running it again on an existing database is safe.
# =============================================================================

set -euo pipefail

# ── Defaults ─────────────────────────────────────────────────────────────────
PG_HOST="localhost"
PG_PORT="5432"
PG_USER="postgres"
DB_NAME="llamatik"

# ── Parse flags ──────────────────────────────────────────────────────────────
while getopts ":h:p:U:d:" opt; do
  case $opt in
    h) PG_HOST="$OPTARG" ;;
    p) PG_PORT="$OPTARG" ;;
    U) PG_USER="$OPTARG" ;;
    d) DB_NAME="$OPTARG" ;;
    \?) echo "Unknown option: -$OPTARG" >&2; exit 1 ;;
    :)  echo "Option -$OPTARG requires an argument." >&2; exit 1 ;;
  esac
done

# ── Locate psql ──────────────────────────────────────────────────────────────
# Try standard PATH first, then fall back to Postgres.app on macOS.
if command -v psql &>/dev/null; then
  PSQL="psql"
elif [[ -x "/Applications/Postgres.app/Contents/Versions/latest/bin/psql" ]]; then
  PSQL="/Applications/Postgres.app/Contents/Versions/latest/bin/psql"
else
  echo "ERROR: psql not found. Install PostgreSQL or add it to your PATH." >&2
  exit 1
fi

PSQL_CMD="$PSQL -h $PG_HOST -p $PG_PORT -U $PG_USER"

echo "============================================="
echo " Llamatik — database setup"
echo "============================================="
echo " Host:     $PG_HOST:$PG_PORT"
echo " User:     $PG_USER"
echo " Database: $DB_NAME"
echo "============================================="
echo ""

# ── Create database (skip if it already exists) ───────────────────────────────
DB_EXISTS=$($PSQL_CMD -d postgres -tAc \
  "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME';" 2>/dev/null || true)

if [[ "$DB_EXISTS" == "1" ]]; then
  echo "→ Database '$DB_NAME' already exists, skipping creation."
else
  echo "→ Creating database '$DB_NAME'…"
  $PSQL_CMD -d postgres -c \
    "CREATE DATABASE $DB_NAME
       OWNER    $PG_USER
       ENCODING 'UTF8'
       LC_COLLATE 'en_US.UTF-8'
       LC_CTYPE   'en_US.UTF-8'
       TEMPLATE   template0;"
  echo "  ✓ Database created."
fi

# ── Create schema ─────────────────────────────────────────────────────────────
echo ""
echo "→ Applying schema to '$DB_NAME'…"

$PSQL_CMD -d "$DB_NAME" <<'SQL'

-- ── users ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id              SERIAL,
    email           VARCHAR(128)     NOT NULL,
    name            VARCHAR(256)     NOT NULL,
    password_hash   VARCHAR(64)      NOT NULL,
    CONSTRAINT users_id_unique    UNIQUE (id),
    CONSTRAINT users_email_unique UNIQUE (email)
);

-- ── profiles ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS profiles (
    id          SERIAL,
    "userId"    INT              NOT NULL,
    name        VARCHAR(64)      NOT NULL,
    description VARCHAR(600)     NOT NULL,
    image       VARCHAR(128)     NOT NULL,
    location    VARCHAR(64)      NOT NULL,
    rating      DOUBLE PRECISION NOT NULL,
    CONSTRAINT profiles_id_unique UNIQUE (id),
    CONSTRAINT profiles_userid_fk FOREIGN KEY ("userId") REFERENCES users(id)
);

SQL

echo "  ✓ Schema applied."

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "→ Tables in '$DB_NAME':"
$PSQL_CMD -d "$DB_NAME" -c "\dt"

echo ""
echo "============================================="
echo " Setup complete."
echo ""
echo " Add this to your environment (or local.properties):"
echo ""
echo "   JDBC_DRIVER=org.postgresql.Driver"
echo "   JDBC_DATABASE_URL=jdbc:postgresql://$PG_HOST:$PG_PORT/$DB_NAME?user=$PG_USER"
echo "   JWT_SECRET=<your-secret>"
echo "============================================="
