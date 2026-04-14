#!/usr/bin/env bash
# setup.sh — interactive setup for rag-agent-system
# Supports two modes:
#   local  → writes .env, runs: docker compose (or docker-compose) up --build
#   prod   → writes secrets/, runs: docker compose -f docker-compose.prod.yml up -d --build
set -euo pipefail

# ── colours ───────────────────────────────────────────────────────────────────
BOLD='\033[1m'
DIM='\033[2m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── prerequisites ─────────────────────────────────────────────────────────────
missing=()
command -v docker  >/dev/null 2>&1 || missing+=("docker")
command -v openssl >/dev/null 2>&1 || missing+=("openssl")

# Prefer the modern plugin form; fall back to the standalone binary.
if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  missing+=("docker compose (install Docker Desktop or the Compose plugin)")
fi

if [ ${#missing[@]} -gt 0 ]; then
  echo -e "${RED}Error: missing required tools:${NC}"
  for t in "${missing[@]}"; do echo -e "  ${RED}·${NC} $t"; done
  exit 1
fi

# ── helpers ───────────────────────────────────────────────────────────────────

header() {
  echo ""
  echo -e "${BOLD}${CYAN}$1${NC}"
  printf '%0.s─' $(seq 1 ${#1})
  echo ""
}

# prompt VAR "Label" "default" [secret]
prompt() {
  local __var="$1" label="$2" default="${3:-}" secret="${4:-false}"
  local display_default="" value=""
  [ -n "$default" ] && display_default=" ${DIM}[$default]${NC}"

  if [ "$secret" = "true" ]; then
    printf "  %s%b: " "$label" "$display_default"
    read -rs value; echo ""
  else
    printf "  %s%b: " "$label" "$display_default"
    read -r value
  fi

  [ -z "$value" ] && value="$default"
  printf -v "$__var" '%s' "$value"
}

# confirm "Question" → returns 0 (yes) or 1 (no)
confirm() {
  local answer
  printf "  %s ${DIM}[y/N]${NC}: " "$1"
  read -r answer
  [[ "$answer" =~ ^[Yy]$ ]]
}

check_conflict() {
  local path="$1" kind="$2"
  if [ -e "$path" ]; then
    echo ""
    echo -e "  ${YELLOW}⚠  $path already exists.${NC}"
    if ! confirm "Overwrite?"; then
      echo -e "  ${RED}Aborted.${NC}"
      exit 1
    fi
  fi
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo -e "${RED}Error: '$1' is required but not found.${NC}"; exit 1; }
}

# ── banner ────────────────────────────────────────────────────────────────────
clear
echo ""
echo -e "${BOLD}  RAG Agent System — Setup${NC}"
echo -e "  ${DIM}Spring AI · LangGraph4j · Weaviate · MySQL${NC}"
echo ""

# ── mode selection ────────────────────────────────────────────────────────────
echo -e "  Select a mode:"
echo -e "    ${BOLD}1${NC}) ${GREEN}local${NC}  — .env file          (development)"
echo -e "    ${BOLD}2${NC}) ${YELLOW}prod${NC}   — Docker secrets      (server)"
echo ""
printf "  Choice [1/2]: "
read -r mode_choice

case "$mode_choice" in
  1|local|dev) MODE=local ;;
  2|prod)      MODE=prod  ;;
  *)
    echo -e "${RED}Invalid choice. Enter 1 or 2.${NC}"
    exit 1
    ;;
esac

echo ""
echo -e "  Mode: ${BOLD}$MODE${NC}"

# ══════════════════════════════════════════════════════════════════════════════
# ── LOCAL MODE ────────────────────────────────────────────────────────────────
# ══════════════════════════════════════════════════════════════════════════════
if [ "$MODE" = "local" ]; then
  ENV_FILE="$SCRIPT_DIR/.env"
  check_conflict "$ENV_FILE" "file"

  # ── MySQL ──────────────────────────────────────────────────────────────────
  header "MySQL"
  prompt MYSQL_ROOT_PASSWORD "Root password"  "rootpassword" true
  prompt MYSQL_DB            "Database name"  "ragagent"
  prompt MYSQL_USER          "Database user"  "ragagent"
  prompt MYSQL_PASSWORD      "User password"  "ragagent"    true

  # ── LLM provider ──────────────────────────────────────────────────────────
  header "LLM Provider"
  echo -e "  Options: ${BOLD}openai${NC} · anthropic · openrouter · local"
  prompt LLM_PROVIDER "Provider" "openai"

  case "$LLM_PROVIDER" in
    openai)
      prompt OPENAI_API_KEY "OpenAI API key"   ""        true
      prompt OPENAI_MODEL   "Model"            "gpt-4o"
      ANTHROPIC_API_KEY=""; ANTHROPIC_MODEL="claude-opus-4-6"
      OPENROUTER_API_KEY=""; OPENROUTER_MODEL="openai/gpt-4o"
      LOCAL_LLM_URL="http://host.docker.internal:11434"
      LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
      ;;
    anthropic)
      prompt ANTHROPIC_API_KEY "Anthropic API key" "" true
      prompt ANTHROPIC_MODEL   "Model"             "claude-opus-4-6"
      OPENAI_API_KEY=""; OPENAI_MODEL="gpt-4o"
      OPENROUTER_API_KEY=""; OPENROUTER_MODEL="openai/gpt-4o"
      LOCAL_LLM_URL="http://host.docker.internal:11434"
      LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
      ;;
    openrouter)
      prompt OPENROUTER_API_KEY "OpenRouter API key" "" true
      prompt OPENROUTER_MODEL   "Model"              "openai/gpt-4o"
      OPENAI_API_KEY=""; OPENAI_MODEL="gpt-4o"
      ANTHROPIC_API_KEY=""; ANTHROPIC_MODEL="claude-opus-4-6"
      LOCAL_LLM_URL="http://host.docker.internal:11434"
      LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
      ;;
    local)
      prompt LOCAL_LLM_URL        "LLM base URL"      "http://host.docker.internal:11434"
      prompt LOCAL_LLM_MODEL      "Chat model"        "llama3"
      prompt LOCAL_EMBEDDING_MODEL "Embedding model"  "nomic-embed-text"
      OPENAI_API_KEY=""; OPENAI_MODEL="gpt-4o"
      ANTHROPIC_API_KEY=""; ANTHROPIC_MODEL="claude-opus-4-6"
      OPENROUTER_API_KEY=""; OPENROUTER_MODEL="openai/gpt-4o"
      ;;
    *)
      echo -e "${RED}Unknown provider '$LLM_PROVIDER'. Expected: openai | anthropic | openrouter | local${NC}"
      exit 1
      ;;
  esac

  # ── Auth ───────────────────────────────────────────────────────────────────
  header "Auth"
  AUTH_ENABLED=false; AUTH_JWT_SECRET=""; RESEND_API_KEY=""; RESEND_FROM_EMAIL="noreply@example.com"

  if confirm "Enable email + OTP login?"; then
    AUTH_ENABLED=true
    AUTH_JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
    echo -e "  ${DIM}JWT secret auto-generated.${NC}"
    prompt AUTH_JWT_EXPIRY_HOURS    "JWT expiry (hours)"      "24"
    prompt AUTH_OTP_EXPIRY_MINUTES  "OTP expiry (minutes)"    "10"
    prompt RESEND_API_KEY           "Resend API key"          "" true
    prompt RESEND_FROM_EMAIL        "From email"              "noreply@example.com"
  else
    AUTH_JWT_EXPIRY_HOURS=24; AUTH_OTP_EXPIRY_MINUTES=10
  fi

  [ -z "$AUTH_JWT_SECRET" ] && AUTH_JWT_SECRET="changeme-use-a-long-random-base64-string-in-production"

  # ── Weaviate ───────────────────────────────────────────────────────────────
  header "Weaviate"
  echo -e "  ${DIM}Leave blank for anonymous access (default).${NC}"
  prompt WEAVIATE_API_KEY "Weaviate API key" "" true

  # ── Write .env ─────────────────────────────────────────────────────────────
  cat > "$ENV_FILE" <<EOF
# Generated by setup.sh — $(date)

# ── MySQL ─────────────────────────────────────────────────────────────────────
MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD
MYSQL_DB=$MYSQL_DB
MYSQL_USER=$MYSQL_USER
MYSQL_PASSWORD=$MYSQL_PASSWORD

# ── LLM provider: openai | anthropic | openrouter | local ─────────────────────
LLM_PROVIDER=$LLM_PROVIDER

OPENAI_API_KEY=$OPENAI_API_KEY
OPENAI_MODEL=$OPENAI_MODEL

ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY
ANTHROPIC_MODEL=$ANTHROPIC_MODEL

OPENROUTER_API_KEY=$OPENROUTER_API_KEY
OPENROUTER_MODEL=$OPENROUTER_MODEL

LOCAL_LLM_URL=$LOCAL_LLM_URL
LOCAL_LLM_MODEL=$LOCAL_LLM_MODEL
LOCAL_EMBEDDING_MODEL=$LOCAL_EMBEDDING_MODEL

# ── Auth ──────────────────────────────────────────────────────────────────────
AUTH_ENABLED=$AUTH_ENABLED
AUTH_JWT_SECRET=$AUTH_JWT_SECRET
AUTH_JWT_EXPIRY_HOURS=$AUTH_JWT_EXPIRY_HOURS
AUTH_OTP_EXPIRY_MINUTES=$AUTH_OTP_EXPIRY_MINUTES

# ── Resend ────────────────────────────────────────────────────────────────────
RESEND_API_KEY=$RESEND_API_KEY
RESEND_FROM_EMAIL=$RESEND_FROM_EMAIL

# ── Weaviate ──────────────────────────────────────────────────────────────────
WEAVIATE_API_KEY=$WEAVIATE_API_KEY
EOF

  echo ""
  echo -e "  ${GREEN}✓${NC}  Written: .env"

  # ── Launch ─────────────────────────────────────────────────────────────────
  echo ""
  if confirm "Run '$COMPOSE up --build' now?"; then
    echo ""
    cd "$SCRIPT_DIR"
    $COMPOSE up --build
  else
    echo ""
    echo -e "  Run manually: ${BOLD}$COMPOSE up --build${NC}"
  fi

# ══════════════════════════════════════════════════════════════════════════════
# ── PROD MODE ─────────────────────────────────────────────────────────────────
# ══════════════════════════════════════════════════════════════════════════════
else
  require_cmd openssl

  SECRETS_DIR="$SCRIPT_DIR/secrets"

  PROD_ENV="$SCRIPT_DIR/.env.prod"
  SKIP_PROMPTS=false

  if [ -d "$SECRETS_DIR" ] && [ "$(ls -A "$SECRETS_DIR" 2>/dev/null | grep -v '\.gitkeep')" ]; then
    echo ""
    echo -e "  ${YELLOW}⚠  secrets/ already contains files.${NC}"
    if confirm "Overwrite all secrets?"; then
      SKIP_PROMPTS=false
    else
      # Reuse existing secrets — jump straight to launch if .env.prod also exists
      if [ ! -f "$PROD_ENV" ]; then
        echo -e "  ${RED}Error: secrets/ exists but .env.prod is missing.${NC}"
        echo -e "  ${RED}Re-run setup and overwrite to regenerate both.${NC}"
        exit 1
      fi
      SKIP_PROMPTS=true
    fi
  fi

  mkdir -p "$SECRETS_DIR"

  write_secret() {
    local name="$1" value="$2"
    printf '%s' "$value" > "$SECRETS_DIR/$name"
    chmod 600 "$SECRETS_DIR/$name"
  }

  if [ "$SKIP_PROMPTS" = true ]; then
    echo -e "  ${GREEN}✓${NC}  Using existing secrets and .env.prod"
  else

  # ── MySQL ──────────────────────────────────────────────────────────────────
  header "MySQL"
  prompt MYSQL_ROOT_PASSWORD "Root password"  "" true
  prompt MYSQL_DB            "Database name"  "ragagent"
  prompt MYSQL_USER          "Database user"  "ragagent"
  prompt MYSQL_PASSWORD      "User password"  "" true

  write_secret mysql_root_password "$MYSQL_ROOT_PASSWORD"
  write_secret mysql_password      "$MYSQL_PASSWORD"

  # ── LLM provider ──────────────────────────────────────────────────────────
  header "LLM Provider"
  echo -e "  Options: ${BOLD}openai${NC} · anthropic · openrouter · local"
  prompt LLM_PROVIDER "Provider" "openai"

  # Blank placeholder secrets for unused providers (Docker requires all declared secrets to exist)
  write_secret openai_api_key     ""
  write_secret anthropic_api_key  ""
  write_secret openrouter_api_key ""

  case "$LLM_PROVIDER" in
    openai)
      prompt OPENAI_API_KEY "OpenAI API key" "" true
      prompt OPENAI_MODEL   "Model"          "gpt-4o"
      write_secret openai_api_key "$OPENAI_API_KEY"
      ANTHROPIC_MODEL="claude-opus-4-6"
      OPENROUTER_MODEL="openai/gpt-4o"
      LOCAL_LLM_URL="http://host.docker.internal:11434"
      LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
      ;;
    anthropic)
      prompt ANTHROPIC_API_KEY "Anthropic API key" "" true
      prompt ANTHROPIC_MODEL   "Model"             "claude-opus-4-6"
      write_secret anthropic_api_key "$ANTHROPIC_API_KEY"
      OPENAI_MODEL="gpt-4o"
      OPENROUTER_MODEL="openai/gpt-4o"
      LOCAL_LLM_URL="http://host.docker.internal:11434"
      LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
      ;;
    openrouter)
      prompt OPENROUTER_API_KEY "OpenRouter API key" "" true
      prompt OPENROUTER_MODEL   "Model"              "openai/gpt-4o"
      write_secret openrouter_api_key "$OPENROUTER_API_KEY"
      OPENAI_MODEL="gpt-4o"
      ANTHROPIC_MODEL="claude-opus-4-6"
      LOCAL_LLM_URL="http://host.docker.internal:11434"
      LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
      ;;
    local)
      prompt LOCAL_LLM_URL         "LLM base URL"     "http://host.docker.internal:11434"
      prompt LOCAL_LLM_MODEL       "Chat model"       "llama3"
      prompt LOCAL_EMBEDDING_MODEL "Embedding model"  "nomic-embed-text"
      OPENAI_MODEL="gpt-4o"; ANTHROPIC_MODEL="claude-opus-4-6"; OPENROUTER_MODEL="openai/gpt-4o"
      ;;
    *)
      echo -e "${RED}Unknown provider '$LLM_PROVIDER'. Expected: openai | anthropic | openrouter | local${NC}"
      exit 1
      ;;
  esac

  # ── Auth ───────────────────────────────────────────────────────────────────
  header "Auth"
  AUTH_ENABLED=true; AUTH_JWT_EXPIRY_HOURS=24; AUTH_OTP_EXPIRY_MINUTES=10
  RESEND_FROM_EMAIL="noreply@example.com"

  AUTH_JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
  echo -e "  ${DIM}JWT secret auto-generated.${NC}"
  write_secret auth_jwt_secret "$AUTH_JWT_SECRET"

  prompt AUTH_JWT_EXPIRY_HOURS   "JWT expiry (hours)"   "24"
  prompt AUTH_OTP_EXPIRY_MINUTES "OTP expiry (minutes)" "10"

  if confirm "Enable email + OTP login?"; then
    AUTH_ENABLED=true
    prompt RESEND_API_KEY    "Resend API key" "" true
    prompt RESEND_FROM_EMAIL "From email"     "noreply@example.com"
    write_secret resend_api_key    "$RESEND_API_KEY"
    write_secret resend_from_email "$RESEND_FROM_EMAIL"
  else
    AUTH_ENABLED=false
    write_secret resend_api_key    ""
    write_secret resend_from_email ""
  fi

  # ── Weaviate ───────────────────────────────────────────────────────────────
  header "Weaviate"
  echo -e "  ${DIM}Leave blank for anonymous access (default).${NC}"
  prompt WEAVIATE_API_KEY "Weaviate API key" "" true
  write_secret weaviate_api_key "$WEAVIATE_API_KEY"

  # ── Summary ────────────────────────────────────────────────────────────────
  echo ""
  echo -e "  ${GREEN}✓${NC}  Secrets written to secrets/ (chmod 600)"
  echo -e "  ${DIM}"
  ls "$SECRETS_DIR" | grep -v '\.gitkeep' | while read -r f; do
    size=$(wc -c < "$SECRETS_DIR/$f")
    if [ "$size" -gt 0 ]; then
      echo "     secrets/$f  (${size}B)"
    else
      echo "     secrets/$f  ${DIM}(empty — unused provider)${NC}${DIM}"
    fi
  done
  echo -e "  ${NC}"

  # Non-secret config that still needs to go somewhere for the prod compose
  # Write a minimal .env for non-sensitive overrides (LLM_PROVIDER, model names, etc.)
  cat > "$PROD_ENV" <<EOF
# Non-secret config for docker-compose.prod.yml — generated by setup.sh $(date)
# Pass with: $COMPOSE --env-file .env.prod -f docker-compose.prod.yml up -d --build

MYSQL_DB=$MYSQL_DB
MYSQL_USER=$MYSQL_USER

LLM_PROVIDER=$LLM_PROVIDER
OPENAI_MODEL=${OPENAI_MODEL:-gpt-4o}
ANTHROPIC_MODEL=${ANTHROPIC_MODEL:-claude-opus-4-6}
OPENROUTER_MODEL=${OPENROUTER_MODEL:-openai/gpt-4o}
LOCAL_LLM_URL=${LOCAL_LLM_URL:-http://host.docker.internal:11434}
LOCAL_LLM_MODEL=${LOCAL_LLM_MODEL:-llama3}
LOCAL_EMBEDDING_MODEL=${LOCAL_EMBEDDING_MODEL:-nomic-embed-text}

AUTH_ENABLED=$AUTH_ENABLED
AUTH_JWT_EXPIRY_HOURS=$AUTH_JWT_EXPIRY_HOURS
AUTH_OTP_EXPIRY_MINUTES=$AUTH_OTP_EXPIRY_MINUTES
EOF

  chmod 600 "$PROD_ENV"
  echo -e "  ${GREEN}✓${NC}  Non-secret config written: .env.prod"

  fi # end SKIP_PROMPTS=false block

  # ── Launch ─────────────────────────────────────────────────────────────────
  echo ""
  if confirm "Run '$COMPOSE -f docker-compose.prod.yml up -d --build' now?"; then
    echo ""
    cd "$SCRIPT_DIR"
    $COMPOSE --env-file .env.prod -f docker-compose.prod.yml up -d --build
  else
    echo ""
    echo -e "  Run manually:"
    echo -e "  ${BOLD}$COMPOSE --env-file .env.prod -f docker-compose.prod.yml up -d --build${NC}"
  fi
fi

echo ""
echo -e "  ${GREEN}Done.${NC}"
echo ""
