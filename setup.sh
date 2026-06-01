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
echo -e "  ${DIM}Spring AI · LangGraph4j · Weaviate · MySQL · Redis (Asynq) · Prometheus · Grafana${NC}"
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

  echo ""
  if confirm "Update environment variables?"; then UPDATE_ENV=true; else UPDATE_ENV=false; fi

  if $UPDATE_ENV; then
    check_conflict "$ENV_FILE" "file"

    # ── MySQL ──────────────────────────────────────────────────────────────────
    header "MySQL"
  prompt MYSQL_ROOT_PASSWORD "Root password"  "rootpassword" true
  prompt MYSQL_DB            "Database name"  "ragagent"
  prompt MYSQL_USER          "Database user"  "ragagent"
  prompt MYSQL_PASSWORD      "User password"  "ragagent"    true

  # ── LLM providers ─────────────────────────────────────────────────────────
  header "LLM Providers"
  echo -e "  Configure API keys for each provider. Users can select any enabled model."
  echo -e "  The default provider is used when a user has no model preference set."
  echo -e "  Leave an API key blank to skip that provider."
  echo ""
  echo -e "  Supported: ${BOLD}openai${NC} · anthropic · openrouter · deepseek · local"
  prompt LLM_PROVIDER "Default provider" "openai"

  echo ""
  echo -e "  ${BOLD}OpenAI${NC}  (api.openai.com)"
  prompt OPENAI_API_KEY "API key" "" true
  prompt OPENAI_MODEL   "Default model" "gpt-4o-mini"

  echo ""
  echo -e "  ${BOLD}Anthropic${NC}  (api.anthropic.com)"
  prompt ANTHROPIC_API_KEY "API key" "" true
  prompt ANTHROPIC_MODEL   "Default model" "claude-opus-4-6"

  echo ""
  echo -e "  ${BOLD}OpenRouter${NC}  (openrouter.ai)"
  prompt OPENROUTER_API_KEY "API key" "" true
  prompt OPENROUTER_MODEL   "Default model" "openai/gpt-4o-mini"

  echo ""
  echo -e "  ${BOLD}DeepSeek${NC}  (api.deepseek.com)"
  echo -e "  ${DIM}Models: deepseek-chat (V3) · deepseek-reasoner (R1)${NC}"
  prompt DEEPSEEK_API_KEY "API key" "" true
  prompt DEEPSEEK_MODEL   "Default model" "deepseek-chat"

  echo ""
  echo -e "  ${BOLD}Local LLM${NC}  (Ollama / LM Studio / llama.cpp)"
  LOCAL_LLM_URL="http://host.docker.internal:11434"
  LOCAL_LLM_MODEL="llama3"
  LOCAL_EMBEDDING_MODEL="nomic-embed-text"
  if confirm "Configure local LLM?"; then
    prompt LOCAL_LLM_URL         "Base URL"        "http://host.docker.internal:11434"
    prompt LOCAL_LLM_MODEL       "Chat model"      "llama3"
    prompt LOCAL_EMBEDDING_MODEL "Embedding model" "nomic-embed-text"
  fi

  # Validate default provider
  case "$LLM_PROVIDER" in
    openai|anthropic|openrouter|deepseek|local) ;;
    *)
      echo -e "${RED}Unknown provider '$LLM_PROVIDER'. Expected: openai | anthropic | openrouter | deepseek | local${NC}"
      exit 1
      ;;
  esac

  echo ""
  echo -e "  ${BOLD}Default model${NC}"
  echo -e "  ${DIM}Optional: display name of a model config (create via POST /api/v1/models after startup).${NC}"
  echo -e "  ${DIM}Used when no user or conversation model preference is set. Leave blank to use the provider default.${NC}"
  prompt DEFAULT_MODEL "Default model display name" ""

  # ── Auth ───────────────────────────────────────────────────────────────────
  header "Auth"
  AUTH_ENABLED=false; AUTH_JWT_SECRET=""; RESEND_API_KEY=""; RESEND_FROM_EMAIL="noreply@example.com"

  AUTH_PASSKEY_RP_ID="localhost"
  AUTH_PASSKEY_RP_NAME="RAG Agent"
  AUTH_PASSKEY_ORIGIN="http://localhost:3000"

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

  # ── Scheduler service key ─────────────────────────────────────────────────
  SCHEDULER_SERVICE_KEY="$(openssl rand -base64 32 | tr -d '\n')"
  echo -e "  ${DIM}Scheduler service key auto-generated.${NC}"

  # ── Financial / Market data ───────────────────────────────────────────────
  header "Financial — Finnhub (optional)"
  echo -e "  ${DIM}Used for live stock prices. Get a free key at https://finnhub.io/register${NC}"
  echo -e "  ${DIM}Leave blank to skip — stock prices will not be fetched.${NC}"
  FINNHUB_API_KEY=""
  prompt FINNHUB_API_KEY "Finnhub API key" "" true

  # ── Connectors (Google Workspace + Figma OAuth + Telegram Bot) ──────────────
  header "Connectors (optional)"
  echo -e "  ${DIM}Connect Google Workspace (Docs, Sheets, Slides), Figma, and Telegram to the agent.${NC}"
  echo -e "  ${DIM}Leave blank to skip — connectors can be configured later in .env.${NC}"
  echo ""
  echo -e "  Google OAuth app → https://console.cloud.google.com/apis/credentials"
  echo -e "    Redirect URI: http://localhost:3000/api/connectors/google/callback"
  echo ""
  GOOGLE_CLIENT_ID=""; GOOGLE_CLIENT_SECRET=""
  if confirm "Configure Google connector?"; then
    prompt GOOGLE_CLIENT_ID     "Google Client ID"     "" false
    prompt GOOGLE_CLIENT_SECRET "Google Client Secret" "" true
  fi
  echo ""
  echo -e "  Figma OAuth app → https://www.figma.com/developers/apps"
  echo -e "    Redirect URI: http://localhost:3000/api/connectors/figma/callback"
  echo ""
  FIGMA_CLIENT_ID=""; FIGMA_CLIENT_SECRET=""
  if confirm "Configure Figma connector?"; then
    prompt FIGMA_CLIENT_ID     "Figma Client ID"     "" false
    prompt FIGMA_CLIENT_SECRET "Figma Client Secret" "" true
  fi
  echo ""
  echo -e "  Telegram Bot → https://t.me/BotFather (send /newbot)"
  echo -e "  ${DIM}After setup, register the webhook:${NC}"
  echo -e "  ${DIM}  POST https://api.telegram.org/bot<TOKEN>/setWebhook?url=http://localhost:8081/api/v1/connectors/telegram/webhook${NC}"
  echo ""
  TELEGRAM_BOT_TOKEN=""; TELEGRAM_BOT_USERNAME=""
  if confirm "Configure Telegram bot?"; then
    prompt TELEGRAM_BOT_TOKEN    "Bot token (from BotFather)" "" true
    prompt TELEGRAM_BOT_USERNAME "Bot username (without @)"   ""
  fi
  CONNECTOR_CALLBACK_BASE_URL="http://localhost:3000"

  # ── Observability ─────────────────────────────────────────────────────────
  header "Observability (Prometheus + Grafana)"
  echo -e "  Prometheus scrapes ${BOLD}/actuator/prometheus${NC} every 15s."
  echo -e "  Grafana auto-loads the RAG Agent dashboard on first boot."
  echo ""
  prompt GRAFANA_USER     "Grafana admin username" "admin"
  prompt GRAFANA_PASSWORD "Grafana admin password" "$(openssl rand -base64 12 | tr -d '\n')" true
  echo ""
  echo -e "  ${BOLD}GitHub OAuth${NC}  ${DIM}(optional — lets you log into Grafana with your GitHub account)${NC}"
  echo -e "  ${DIM}Create an OAuth App at: GitHub → Settings → Developer settings → OAuth Apps${NC}"
  echo -e "  ${DIM}Callback URL: http://localhost:3001/login/github${NC}"
  echo ""
  GRAFANA_GITHUB_CLIENT_ID=""; GRAFANA_GITHUB_CLIENT_SECRET=""
  if confirm "Configure Grafana GitHub OAuth?"; then
    prompt GRAFANA_GITHUB_CLIENT_ID     "GitHub OAuth Client ID"     "" false
    prompt GRAFANA_GITHUB_CLIENT_SECRET "GitHub OAuth Client Secret" "" true
  fi
  echo ""
  echo -e "  ${BOLD}Rate limits${NC}  ${DIM}(requests per user per minute — override with env vars)${NC}"
  prompt RATE_LIMIT_QUERY   "LLM query limit   (RATE_LIMIT_QUERY)"   "20"
  prompt RATE_LIMIT_INGEST  "Ingest limit      (RATE_LIMIT_INGEST)"  "10"
  prompt RATE_LIMIT_DEFAULT "Default API limit (RATE_LIMIT_DEFAULT)" "100"
  echo ""
  echo -e "  ${BOLD}Tracing${NC}  ${DIM}(set to 0.1 in prod to sample 10% of traces)${NC}"
  prompt TRACING_SAMPLE_RATE "Tracing sample rate (0.0–1.0)" "1.0"

  # ── Write .env ─────────────────────────────────────────────────────────────
  cat > "$ENV_FILE" <<EOF
# Generated by setup.sh — $(date)

# ── MySQL ─────────────────────────────────────────────────────────────────────
MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD
MYSQL_DB=$MYSQL_DB
MYSQL_USER=$MYSQL_USER
MYSQL_PASSWORD=$MYSQL_PASSWORD

# ── LLM providers: openai | anthropic | openrouter | deepseek | local ────────
# LLM_PROVIDER is the raw fallback when no model is selected at any level.
# DEFAULT_MODEL (display name of a model_configs entry) sits above LLM_PROVIDER.
# Priority: conversation model → user model → DEFAULT_MODEL → LLM_PROVIDER
LLM_PROVIDER=$LLM_PROVIDER
DEFAULT_MODEL=$DEFAULT_MODEL

OPENAI_API_KEY=$OPENAI_API_KEY
OPENAI_MODEL=$OPENAI_MODEL

ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY
ANTHROPIC_MODEL=$ANTHROPIC_MODEL

OPENROUTER_API_KEY=$OPENROUTER_API_KEY
OPENROUTER_MODEL=$OPENROUTER_MODEL

DEEPSEEK_API_KEY=$DEEPSEEK_API_KEY
DEEPSEEK_MODEL=$DEEPSEEK_MODEL

LOCAL_LLM_URL=$LOCAL_LLM_URL
LOCAL_LLM_MODEL=$LOCAL_LLM_MODEL
LOCAL_EMBEDDING_MODEL=$LOCAL_EMBEDDING_MODEL

# ── Auth ──────────────────────────────────────────────────────────────────────
AUTH_ENABLED=$AUTH_ENABLED
AUTH_JWT_SECRET=$AUTH_JWT_SECRET
AUTH_JWT_EXPIRY_HOURS=$AUTH_JWT_EXPIRY_HOURS
AUTH_OTP_EXPIRY_MINUTES=$AUTH_OTP_EXPIRY_MINUTES

# ── Passkey (WebAuthn) ────────────────────────────────────────────────────────
# For local dev the defaults (localhost / http://localhost:3000) are used.
AUTH_PASSKEY_RP_ID=$AUTH_PASSKEY_RP_ID
AUTH_PASSKEY_RP_NAME=$AUTH_PASSKEY_RP_NAME
AUTH_PASSKEY_ORIGIN=$AUTH_PASSKEY_ORIGIN

# ── Resend ────────────────────────────────────────────────────────────────────
RESEND_API_KEY=$RESEND_API_KEY
RESEND_FROM_EMAIL=$RESEND_FROM_EMAIL

# ── Financial / Market data ───────────────────────────────────────────────────
FINNHUB_API_KEY=$FINNHUB_API_KEY

# ── Weaviate ──────────────────────────────────────────────────────────────────
WEAVIATE_API_KEY=$WEAVIATE_API_KEY

# ── Scheduler microservice ────────────────────────────────────────────────────
SCHEDULER_SERVICE_KEY=$SCHEDULER_SERVICE_KEY

# ── Web fetch ─────────────────────────────────────────────────────────────────
WEB_FETCH_ENABLED=true
WEB_FETCH_TIMEOUT=10
WEB_FETCH_MAX_CHARS=50000

# ── Connectors (Google Workspace + Figma OAuth + Telegram Bot) ───────────────
# Google:   https://console.cloud.google.com/apis/credentials
# Figma:    https://www.figma.com/developers/apps
# Telegram: https://t.me/BotFather
# Register webhook: POST https://api.telegram.org/bot<TOKEN>/setWebhook?url=<BACKEND_URL>/api/v1/connectors/telegram/webhook
GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET=$GOOGLE_CLIENT_SECRET
FIGMA_CLIENT_ID=$FIGMA_CLIENT_ID
FIGMA_CLIENT_SECRET=$FIGMA_CLIENT_SECRET
TELEGRAM_BOT_TOKEN=$TELEGRAM_BOT_TOKEN
TELEGRAM_BOT_USERNAME=$TELEGRAM_BOT_USERNAME
CONNECTOR_CALLBACK_BASE_URL=$CONNECTOR_CALLBACK_BASE_URL

# ── Observability ─────────────────────────────────────────────────────────────
# Grafana UI: http://localhost:3001  Prometheus: http://localhost:9090
GRAFANA_USER=$GRAFANA_USER
GRAFANA_PASSWORD=$GRAFANA_PASSWORD
GRAFANA_GITHUB_CLIENT_ID=$GRAFANA_GITHUB_CLIENT_ID
GRAFANA_GITHUB_CLIENT_SECRET=$GRAFANA_GITHUB_CLIENT_SECRET

# ── Rate limiting (requests per user per minute) ──────────────────────────────
RATE_LIMIT_QUERY=$RATE_LIMIT_QUERY
RATE_LIMIT_INGEST=$RATE_LIMIT_INGEST
RATE_LIMIT_DEFAULT=$RATE_LIMIT_DEFAULT

# ── Distributed tracing (0.0 = off, 1.0 = sample all; lower in high-traffic prod) ──
TRACING_SAMPLE_RATE=$TRACING_SAMPLE_RATE
EOF

  echo ""
  echo -e "  ${GREEN}✓${NC}  Written: .env"

  # ── Sandbox image ──────────────────────────────────────────────────────────
  header "Sandbox Image (for Workflow engine)"
  echo -e "  ${DIM}The workflow engine runs agent tool calls inside an isolated Docker container.${NC}"
  echo -e "  ${DIM}Build the sandbox image once with: docker build -f backend/Dockerfile.sandbox -t ragagent/sandbox:latest ./backend${NC}"
  echo ""
  if confirm "Build sandbox image now?"; then
    echo -e "  Building ragagent/sandbox:latest …"
    docker build -f "$SCRIPT_DIR/backend/Dockerfile.sandbox" -t ragagent/sandbox:latest "$SCRIPT_DIR/backend"
    echo -e "  ${GREEN}✓${NC}  Sandbox image built."
  else
    echo -e "  ${YELLOW}Skipped.${NC} Build later: ${BOLD}docker build -f backend/Dockerfile.sandbox -t ragagent/sandbox:latest ./backend${NC}"
    echo -e "  ${DIM}Set SANDBOX_ENABLED=false in .env to disable the sandbox entirely.${NC}"
  fi

  fi  # end UPDATE_ENV

  # ── Launch ─────────────────────────────────────────────────────────────────
  echo ""
  echo -e "  How would you like to start?"
  echo -e "    ${BOLD}1${NC}) Build all from source          ${DIM}($COMPOSE up --build)${NC}"
  echo -e "    ${BOLD}2${NC}) Start with existing images     ${DIM}($COMPOSE up)${NC}"
  echo -e "    ${BOLD}3${NC}) Rebuild frontend only          ${DIM}($COMPOSE up -d --build --no-deps frontend)${NC}"
  echo -e "    ${BOLD}4${NC}) Rebuild backend only           ${DIM}($COMPOSE up -d --build --no-deps backend)${NC}"
  echo -e "    ${BOLD}5${NC}) Rebuild scheduler only         ${DIM}($COMPOSE up -d --build --no-deps scheduler)${NC}"
  echo -e "    ${BOLD}6${NC}) Restart frontend only  ${DIM}(no build — $COMPOSE restart frontend)${NC}"
  echo -e "    ${BOLD}7${NC}) Restart backend only   ${DIM}(no build — $COMPOSE restart backend)${NC}"
  echo -e "    ${BOLD}8${NC}) Restart scheduler only ${DIM}(no build — $COMPOSE restart scheduler)${NC}"
  echo -e "    ${BOLD}9${NC}) Skip — I'll start manually"
  echo ""
  printf "  Choice [1-9]: "
  read -r launch_choice

  echo ""
  cd "$SCRIPT_DIR"
  case "$launch_choice" in
    1) $COMPOSE up --build ;;
    2) $COMPOSE up ;;
    3) $COMPOSE up -d --build --no-deps frontend ;;
    4) $COMPOSE up -d --build --no-deps backend ;;
    5) $COMPOSE up -d --build --no-deps scheduler ;;
    6) $COMPOSE restart frontend ;;
    7) $COMPOSE restart backend ;;
    8) $COMPOSE restart scheduler ;;
    9)
      echo -e "  Run manually:"
      echo -e "    Build all:          ${BOLD}$COMPOSE up --build${NC}"
      echo -e "    Rebuild frontend:   ${BOLD}$COMPOSE up -d --build --no-deps frontend${NC}"
      echo -e "    Rebuild backend:    ${BOLD}$COMPOSE up -d --build --no-deps backend${NC}"
      echo -e "    Rebuild scheduler:  ${BOLD}$COMPOSE up -d --build --no-deps scheduler${NC}"
      echo -e "    Restart frontend:   ${BOLD}$COMPOSE restart frontend${NC}"
      echo -e "    Restart backend:    ${BOLD}$COMPOSE restart backend${NC}"
      echo -e "    Restart scheduler:  ${BOLD}$COMPOSE restart scheduler${NC}"
      ;;
    *)
      echo -e "  ${YELLOW}Invalid choice — skipping launch.${NC}"
      echo -e "  Run manually: ${BOLD}$COMPOSE up --build${NC}"
      ;;
  esac

# ══════════════════════════════════════════════════════════════════════════════
# ── PROD MODE ─────────────────────────────────────────────────────────────────
# ══════════════════════════════════════════════════════════════════════════════
else
  require_cmd openssl

  SECRETS_DIR="$SCRIPT_DIR/secrets"
  PROD_ENV="$SCRIPT_DIR/.env.prod"
  mkdir -p "$SECRETS_DIR"

  # ── helpers ───────────────────────────────────────────────────────────────

  write_secret() {
    local name="$1" value="$2"
    printf '%s' "$value" > "$SECRETS_DIR/$name"
    chmod 600 "$SECRETS_DIR/$name"
  }

  # Read a value from an existing .env.prod; fall back to default.
  read_prod() {
    local key="$1" default="${2:-}"
    if [ -f "$PROD_ENV" ]; then
      local val
      val=$(grep "^${key}=" "$PROD_ENV" 2>/dev/null | head -1 | sed "s/^${key}=//")
      [ -n "$val" ] && { printf '%s' "$val"; return; }
    fi
    printf '%s' "$default"
  }

  # True if a secret file exists and is non-empty.
  has_secret() { [ -s "$SECRETS_DIR/$1" ]; }

  echo ""
  if confirm "Update environment variables?"; then UPDATE_ENV=true; else UPDATE_ENV=false; fi

  if $UPDATE_ENV; then

  # ── MySQL ──────────────────────────────────────────────────────────────────
  header "MySQL"
  UPDATE_MYSQL=true
  if has_secret mysql_password; then
    echo -e "  ${DIM}MySQL secrets are already configured.${NC}"
    if ! confirm "Update MySQL settings?"; then UPDATE_MYSQL=false; fi
  fi
  if $UPDATE_MYSQL; then
    prompt MYSQL_ROOT_PASSWORD "Root password" "" true
    prompt MYSQL_DB            "Database name" "ragagent"
    prompt MYSQL_USER          "Database user" "ragagent"
    prompt MYSQL_PASSWORD      "User password" "" true
    write_secret mysql_root_password "$MYSQL_ROOT_PASSWORD"
    write_secret mysql_password      "$MYSQL_PASSWORD"
  else
    MYSQL_DB="$(read_prod MYSQL_DB ragagent)"
    MYSQL_USER="$(read_prod MYSQL_USER ragagent)"
    echo -e "  ${DIM}Keeping existing MySQL secrets.${NC}"
  fi

  # ── LLM providers ─────────────────────────────────────────────────────────
  header "LLM Providers"
  UPDATE_LLM=true
  if has_secret openai_api_key || has_secret anthropic_api_key || has_secret openrouter_api_key || has_secret deepseek_api_key; then
    echo -e "  ${DIM}LLM providers already configured (default: $(read_prod LLM_PROVIDER openai)).${NC}"
    if ! confirm "Update LLM provider settings?"; then UPDATE_LLM=false; fi
  fi
  if $UPDATE_LLM; then
    echo -e "  Configure API keys for each provider. Users can select any enabled model."
    echo -e "  The default provider is used when a user has no model preference set."
    echo -e "  Leave an API key blank to skip that provider."
    echo ""
    echo -e "  Supported: ${BOLD}openai${NC} · anthropic · openrouter · deepseek · local"
    prompt LLM_PROVIDER "Default provider" "openai"

    echo ""
    echo -e "  ${BOLD}OpenAI${NC}  (api.openai.com)"
    prompt OPENAI_API_KEY "API key" "" true
    prompt OPENAI_MODEL   "Default model" "gpt-4o-mini"

    echo ""
    echo -e "  ${BOLD}Anthropic${NC}  (api.anthropic.com)"
    prompt ANTHROPIC_API_KEY "API key" "" true
    prompt ANTHROPIC_MODEL   "Default model" "claude-opus-4-6"

    echo ""
    echo -e "  ${BOLD}OpenRouter${NC}  (openrouter.ai)"
    prompt OPENROUTER_API_KEY "API key" "" true
    prompt OPENROUTER_MODEL   "Default model" "openai/gpt-4o-mini"

    echo ""
    echo -e "  ${BOLD}DeepSeek${NC}  (api.deepseek.com)"
    echo -e "  ${DIM}Models: deepseek-chat (V3) · deepseek-reasoner (R1)${NC}"
    prompt DEEPSEEK_API_KEY "API key" "" true
    prompt DEEPSEEK_MODEL   "Default model" "deepseek-chat"

    echo ""
    echo -e "  ${BOLD}Local LLM${NC}  (Ollama / LM Studio / llama.cpp)"
    LOCAL_LLM_URL="http://host.docker.internal:11434"
    LOCAL_LLM_MODEL="llama3"
    LOCAL_EMBEDDING_MODEL="nomic-embed-text"
    if confirm "Configure local LLM?"; then
      prompt LOCAL_LLM_URL         "Base URL"        "http://host.docker.internal:11434"
      prompt LOCAL_LLM_MODEL       "Chat model"      "llama3"
      prompt LOCAL_EMBEDDING_MODEL "Embedding model" "nomic-embed-text"
    fi

    # Validate default provider
    case "$LLM_PROVIDER" in
      openai|anthropic|openrouter|deepseek|local) ;;
      *)
        echo -e "${RED}Unknown provider '$LLM_PROVIDER'. Expected: openai | anthropic | openrouter | deepseek | local${NC}"
        exit 1
        ;;
    esac

    echo ""
    echo -e "  ${BOLD}Default model${NC}"
    echo -e "  ${DIM}Optional: display name of a model config (create via POST /api/v1/models after startup).${NC}"
    echo -e "  ${DIM}Used when no user or conversation model preference is set. Leave blank to use the provider default.${NC}"
    prompt DEFAULT_MODEL "Default model display name" ""

    # Write all provider secrets (blank = provider disabled)
    write_secret openai_api_key     "$OPENAI_API_KEY"
    write_secret anthropic_api_key  "$ANTHROPIC_API_KEY"
    write_secret openrouter_api_key "$OPENROUTER_API_KEY"
    write_secret deepseek_api_key   "$DEEPSEEK_API_KEY"
  else
    LLM_PROVIDER="$(read_prod LLM_PROVIDER openai)"
    DEFAULT_MODEL="$(read_prod DEFAULT_MODEL "")"
    OPENAI_MODEL="$(read_prod OPENAI_MODEL gpt-4o-mini)"
    ANTHROPIC_MODEL="$(read_prod ANTHROPIC_MODEL claude-opus-4-6)"
    OPENROUTER_MODEL="$(read_prod OPENROUTER_MODEL openai/gpt-4o-mini)"
    DEEPSEEK_MODEL="$(read_prod DEEPSEEK_MODEL deepseek-chat)"
    LOCAL_LLM_URL="$(read_prod LOCAL_LLM_URL http://host.docker.internal:11434)"
    LOCAL_LLM_MODEL="$(read_prod LOCAL_LLM_MODEL llama3)"
    LOCAL_EMBEDDING_MODEL="$(read_prod LOCAL_EMBEDDING_MODEL nomic-embed-text)"
    echo -e "  ${DIM}Keeping existing LLM secrets.${NC}"
  fi

  # ── Auth ──────────────────────────────────────────────────────────────────
  header "Auth"
  UPDATE_AUTH=true
  if has_secret auth_jwt_secret; then
    echo -e "  ${DIM}Auth already configured (enabled: $(read_prod AUTH_ENABLED true)).${NC}"
    if ! confirm "Update Auth settings?"; then UPDATE_AUTH=false; fi
  fi
  if $UPDATE_AUTH; then
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
  else
    AUTH_ENABLED="$(read_prod AUTH_ENABLED true)"
    AUTH_JWT_EXPIRY_HOURS="$(read_prod AUTH_JWT_EXPIRY_HOURS 24)"
    AUTH_OTP_EXPIRY_MINUTES="$(read_prod AUTH_OTP_EXPIRY_MINUTES 10)"
    echo -e "  ${DIM}Keeping existing Auth secrets.${NC}"
  fi

  # ── Passkey (WebAuthn) ────────────────────────────────────────────────────
  header "Passkey (WebAuthn)"
  UPDATE_PASSKEY=true
  if grep -q "^AUTH_PASSKEY_RP_ID=" "$PROD_ENV" 2>/dev/null; then
    echo -e "  ${DIM}Passkey already configured (RP ID: $(read_prod AUTH_PASSKEY_RP_ID localhost)).${NC}"
    if ! confirm "Update Passkey settings?"; then UPDATE_PASSKEY=false; fi
  fi
  if $UPDATE_PASSKEY; then
    echo -e "  ${DIM}The RP ID must match the domain the app is served from (no scheme, no port).${NC}"
    echo ""
    prompt AUTH_PASSKEY_DOMAIN "Public domain (e.g. app.example.com)" ""
    if [ -n "$AUTH_PASSKEY_DOMAIN" ]; then
      AUTH_PASSKEY_RP_ID="$AUTH_PASSKEY_DOMAIN"
      AUTH_PASSKEY_RP_NAME="RAG Agent"
      AUTH_PASSKEY_ORIGIN="https://$AUTH_PASSKEY_DOMAIN"
      echo -e "  ${DIM}RP ID:  $AUTH_PASSKEY_RP_ID${NC}"
      echo -e "  ${DIM}Origin: $AUTH_PASSKEY_ORIGIN${NC}"
    else
      echo -e "  ${YELLOW}No domain entered — using localhost defaults.${NC}"
      AUTH_PASSKEY_RP_ID="localhost"
      AUTH_PASSKEY_RP_NAME="RAG Agent"
      AUTH_PASSKEY_ORIGIN="http://localhost:3000"
    fi
  else
    AUTH_PASSKEY_RP_ID="$(read_prod AUTH_PASSKEY_RP_ID localhost)"
    AUTH_PASSKEY_RP_NAME="$(read_prod AUTH_PASSKEY_RP_NAME 'RAG Agent')"
    AUTH_PASSKEY_ORIGIN="$(read_prod AUTH_PASSKEY_ORIGIN http://localhost:3000)"
    echo -e "  ${DIM}Keeping existing Passkey config.${NC}"
  fi

  # ── Weaviate ───────────────────────────────────────────────────────────────
  header "Weaviate"
  UPDATE_WEAVIATE=true
  if has_secret weaviate_api_key; then
    echo -e "  ${DIM}Weaviate already configured.${NC}"
    if ! confirm "Update Weaviate API key?"; then UPDATE_WEAVIATE=false; fi
  fi
  if $UPDATE_WEAVIATE; then
    echo -e "  ${DIM}Leave blank for anonymous access.${NC}"
    prompt WEAVIATE_API_KEY "Weaviate API key" "" true
    write_secret weaviate_api_key "$WEAVIATE_API_KEY"
  else
    echo -e "  ${DIM}Keeping existing Weaviate secret.${NC}"
  fi

  # ── Scheduler ──────────────────────────────────────────────────────────────
  # Only generate a new key on first setup; re-generating would break the running
  # Go scheduler until it is restarted with the new key.
  if ! has_secret scheduler_service_key; then
    SCHEDULER_SERVICE_KEY="$(openssl rand -base64 32 | tr -d '\n')"
    write_secret scheduler_service_key "$SCHEDULER_SERVICE_KEY"
    echo ""
    echo -e "  ${DIM}Scheduler service key auto-generated.${NC}"
  fi

  # ── Financial / Market data ───────────────────────────────────────────────
  header "Financial — Finnhub (optional)"
  UPDATE_FH=true
  if has_secret finnhub_api_key; then
    echo -e "  ${DIM}Finnhub already configured.${NC}"
    if ! confirm "Update Finnhub API key?"; then UPDATE_FH=false; fi
  fi
  if $UPDATE_FH; then
    echo -e "  ${DIM}Get a free key at https://finnhub.io/register${NC}"
    echo -e "  ${DIM}Leave blank to skip — stock prices will not be fetched.${NC}"
    FINNHUB_API_KEY=""
    prompt FINNHUB_API_KEY "Finnhub API key" "" true
    write_secret finnhub_api_key "$FINNHUB_API_KEY"
  else
    echo -e "  ${DIM}Keeping existing Finnhub secret.${NC}"
  fi

  # ── Connectors (Google Workspace + Figma OAuth + Telegram Bot) ──────────────
  header "Connectors (optional)"
  echo -e "  ${DIM}Connect Google Workspace (Docs, Sheets, Slides), Figma, and Telegram to the agent.${NC}"
  echo -e "  ${DIM}Leave blank to skip — secrets can be populated in secrets/ later.${NC}"
  CONNECTORS_ALREADY_SET=false
  if has_secret google_client_id && has_secret figma_client_id; then
    CONNECTORS_ALREADY_SET=true
    echo -e "  ${DIM}Connectors already configured.${NC}"
  fi

  # ── Google ──────────────────────────────────────────────────────────────────
  UPDATE_GOOGLE=true
  if $CONNECTORS_ALREADY_SET && has_secret google_client_id; then
    if ! confirm "Update Google connector credentials?"; then UPDATE_GOOGLE=false; fi
  fi
  if $UPDATE_GOOGLE; then
    echo ""
    echo -e "  Google OAuth app → https://console.cloud.google.com/apis/credentials"
    prompt CONNECTOR_CALLBACK_BASE_URL "Public frontend URL (e.g. https://app.example.com)" "https://app.example.com"
    echo -e "    Redirect URI: ${CONNECTOR_CALLBACK_BASE_URL}/api/connectors/google/callback"
    echo ""
    GOOGLE_CLIENT_ID=""; GOOGLE_CLIENT_SECRET=""
    if confirm "Configure Google connector?"; then
      prompt GOOGLE_CLIENT_ID     "Google Client ID"     "" false
      prompt GOOGLE_CLIENT_SECRET "Google Client Secret" "" true
    fi
    write_secret google_client_id     "$GOOGLE_CLIENT_ID"
    write_secret google_client_secret "$GOOGLE_CLIENT_SECRET"
  else
    CONNECTOR_CALLBACK_BASE_URL="$(read_prod CONNECTOR_CALLBACK_BASE_URL http://localhost:3000)"
    echo -e "  ${DIM}Keeping existing Google secrets.${NC}"
  fi

  # ── Figma ───────────────────────────────────────────────────────────────────
  UPDATE_FIGMA=true
  if $CONNECTORS_ALREADY_SET && has_secret figma_client_id; then
    if ! confirm "Update Figma connector credentials?"; then UPDATE_FIGMA=false; fi
  fi
  if $UPDATE_FIGMA; then
    echo ""
    echo -e "  Figma OAuth app → https://www.figma.com/developers/apps"
    echo -e "    Redirect URI: ${CONNECTOR_CALLBACK_BASE_URL}/api/connectors/figma/callback"
    echo ""
    FIGMA_CLIENT_ID=""; FIGMA_CLIENT_SECRET=""
    if confirm "Configure Figma connector?"; then
      prompt FIGMA_CLIENT_ID     "Figma Client ID"     "" false
      prompt FIGMA_CLIENT_SECRET "Figma Client Secret" "" true
    fi
    write_secret figma_client_id     "$FIGMA_CLIENT_ID"
    write_secret figma_client_secret "$FIGMA_CLIENT_SECRET"
  else
    echo -e "  ${DIM}Keeping existing Figma secrets.${NC}"
  fi

  # ── Telegram ────────────────────────────────────────────────────────────────
  UPDATE_TELEGRAM=true
  if $CONNECTORS_ALREADY_SET && has_secret telegram_bot_token; then
    if ! confirm "Update Telegram connector credentials?"; then UPDATE_TELEGRAM=false; fi
  fi
  if $UPDATE_TELEGRAM; then
    echo ""
    echo -e "  Telegram Bot → https://t.me/BotFather (send /newbot)"
    echo -e "  ${DIM}After setup, register the webhook:${NC}"
    echo -e "  ${DIM}  POST https://api.telegram.org/bot<TOKEN>/setWebhook?url=<BACKEND_PUBLIC_URL>/api/v1/connectors/telegram/webhook${NC}"
    echo ""
    TELEGRAM_BOT_TOKEN=""; TELEGRAM_BOT_USERNAME=""
    if confirm "Configure Telegram bot?"; then
      prompt TELEGRAM_BOT_TOKEN    "Bot token (from BotFather)" "" true
      prompt TELEGRAM_BOT_USERNAME "Bot username (without @)"   ""
    fi
    write_secret telegram_bot_token    "$TELEGRAM_BOT_TOKEN"
    write_secret telegram_bot_username "$TELEGRAM_BOT_USERNAME"
  else
    TELEGRAM_BOT_USERNAME="$(read_prod TELEGRAM_BOT_USERNAME "")"
    echo -e "  ${DIM}Keeping existing Telegram secrets.${NC}"
  fi

  # ── Observability ─────────────────────────────────────────────────────────
  header "Observability (Prometheus + Grafana)"
  echo -e "  Prometheus scrapes ${BOLD}/actuator/prometheus${NC} every 15s."
  echo -e "  Grafana auto-loads the RAG Agent dashboard on first boot."
  echo ""
  UPDATE_OBS=true
  if grep -q "^GRAFANA_USER=" "$PROD_ENV" 2>/dev/null; then
    echo -e "  ${DIM}Observability already configured.${NC}"
    if ! confirm "Update Grafana credentials?"; then UPDATE_OBS=false; fi
  fi
  if $UPDATE_OBS; then
    prompt GRAFANA_USER     "Grafana admin username" "admin"
    prompt GRAFANA_PASSWORD "Grafana admin password" "$(openssl rand -base64 12 | tr -d '\n')" true
    echo ""
    echo -e "  ${BOLD}GitHub OAuth${NC}  ${DIM}(optional — lets you log into Grafana with your GitHub account)${NC}"
    echo -e "  ${DIM}Create an OAuth App at: GitHub → Settings → Developer settings → OAuth Apps${NC}"
    echo -e "  ${DIM}Callback URL: https://<your-grafana-domain>/login/github${NC}"
    echo ""
    GRAFANA_GITHUB_CLIENT_ID=""; GRAFANA_GITHUB_CLIENT_SECRET=""
    if confirm "Configure Grafana GitHub OAuth?"; then
      prompt GRAFANA_GITHUB_CLIENT_ID     "GitHub OAuth Client ID"     "" false
      prompt GRAFANA_GITHUB_CLIENT_SECRET "GitHub OAuth Client Secret" "" true
    fi
    echo ""
    echo -e "  ${BOLD}Rate limits${NC}  ${DIM}(requests per user per minute)${NC}"
    prompt RATE_LIMIT_QUERY   "LLM query limit   (RATE_LIMIT_QUERY)"   "20"
    prompt RATE_LIMIT_INGEST  "Ingest limit      (RATE_LIMIT_INGEST)"  "10"
    prompt RATE_LIMIT_DEFAULT "Default API limit (RATE_LIMIT_DEFAULT)" "100"
    echo ""
    echo -e "  ${BOLD}Tracing${NC}  ${DIM}(0.1 is typical for prod — samples 10% of requests)${NC}"
    prompt TRACING_SAMPLE_RATE "Tracing sample rate (0.0–1.0)" "0.1"
  else
    GRAFANA_USER="$(read_prod GRAFANA_USER admin)"
    GRAFANA_PASSWORD="$(read_prod GRAFANA_PASSWORD admin)"
    GRAFANA_GITHUB_CLIENT_ID="$(read_prod GRAFANA_GITHUB_CLIENT_ID "")"
    GRAFANA_GITHUB_CLIENT_SECRET="$(read_prod GRAFANA_GITHUB_CLIENT_SECRET "")"
    RATE_LIMIT_QUERY="$(read_prod RATE_LIMIT_QUERY 20)"
    RATE_LIMIT_INGEST="$(read_prod RATE_LIMIT_INGEST 10)"
    RATE_LIMIT_DEFAULT="$(read_prod RATE_LIMIT_DEFAULT 100)"
    TRACING_SAMPLE_RATE="$(read_prod TRACING_SAMPLE_RATE 0.1)"
    echo -e "  ${DIM}Keeping existing observability config.${NC}"
  fi

  # ── Summary ────────────────────────────────────────────────────────────────
  echo ""
  echo -e "  ${GREEN}✓${NC}  Secrets in secrets/ (chmod 600)"
  echo -e "  ${DIM}"
  ls "$SECRETS_DIR" | grep -v '\.gitkeep' | while read -r f; do
    size=$(wc -c < "$SECRETS_DIR/$f")
    if [ "$size" -gt 0 ]; then
      echo "     secrets/$f  (${size}B)"
    else
      echo "     secrets/$f  (empty — unused provider)"
    fi
  done
  echo -e "  ${NC}"

  cat > "$PROD_ENV" <<EOF
# Non-secret config for docker-compose.prod.yml — generated by setup.sh $(date)
# Pass with: $COMPOSE --env-file .env.prod -f docker-compose.prod.yml up -d --build

MYSQL_DB=$MYSQL_DB
MYSQL_USER=$MYSQL_USER

LLM_PROVIDER=$LLM_PROVIDER
DEFAULT_MODEL=${DEFAULT_MODEL:-}
OPENAI_MODEL=${OPENAI_MODEL:-gpt-4o-mini}
ANTHROPIC_MODEL=${ANTHROPIC_MODEL:-claude-opus-4-6}
OPENROUTER_MODEL=${OPENROUTER_MODEL:-openai/gpt-4o-mini}
DEEPSEEK_MODEL=${DEEPSEEK_MODEL:-deepseek-chat}
LOCAL_LLM_URL=${LOCAL_LLM_URL:-http://host.docker.internal:11434}
LOCAL_LLM_MODEL=${LOCAL_LLM_MODEL:-llama3}
LOCAL_EMBEDDING_MODEL=${LOCAL_EMBEDDING_MODEL:-nomic-embed-text}

AUTH_ENABLED=$AUTH_ENABLED
AUTH_JWT_EXPIRY_HOURS=$AUTH_JWT_EXPIRY_HOURS
AUTH_OTP_EXPIRY_MINUTES=$AUTH_OTP_EXPIRY_MINUTES

# ── Passkey (WebAuthn) ────────────────────────────────────────────────────────
AUTH_PASSKEY_RP_ID=$AUTH_PASSKEY_RP_ID
AUTH_PASSKEY_RP_NAME=$AUTH_PASSKEY_RP_NAME
AUTH_PASSKEY_ORIGIN=$AUTH_PASSKEY_ORIGIN

# ── Web fetch ─────────────────────────────────────────────────────────────────
WEB_FETCH_ENABLED=true
WEB_FETCH_TIMEOUT=10
WEB_FETCH_MAX_CHARS=50000

# ── Connectors (credentials come from secrets; TELEGRAM_BOT_USERNAME is non-secret)
# Telegram webhook registration: POST https://api.telegram.org/bot<TOKEN>/setWebhook?url=<BACKEND_URL>/api/v1/connectors/telegram/webhook
CONNECTOR_CALLBACK_BASE_URL=${CONNECTOR_CALLBACK_BASE_URL:-http://localhost:3000}
TELEGRAM_BOT_USERNAME=${TELEGRAM_BOT_USERNAME:-}

# ── Observability ─────────────────────────────────────────────────────────────
# Grafana UI: http://<host>:3001  Prometheus: http://<host>:9090
GRAFANA_USER=$GRAFANA_USER
GRAFANA_PASSWORD=$GRAFANA_PASSWORD
GRAFANA_GITHUB_CLIENT_ID=${GRAFANA_GITHUB_CLIENT_ID:-}
GRAFANA_GITHUB_CLIENT_SECRET=${GRAFANA_GITHUB_CLIENT_SECRET:-}

# ── Rate limiting (requests per user per minute) ──────────────────────────────
RATE_LIMIT_QUERY=$RATE_LIMIT_QUERY
RATE_LIMIT_INGEST=$RATE_LIMIT_INGEST
RATE_LIMIT_DEFAULT=$RATE_LIMIT_DEFAULT

# ── Distributed tracing (lower sample rate saves storage in high-traffic prod) ──
TRACING_SAMPLE_RATE=$TRACING_SAMPLE_RATE
EOF

  chmod 600 "$PROD_ENV"
  echo -e "  ${GREEN}✓${NC}  Non-secret config written: .env.prod"

  # ── Sandbox image ──────────────────────────────────────────────────────────
  header "Sandbox Image (for Workflow engine)"
  echo -e "  ${DIM}The workflow engine runs agent tool calls inside an isolated Docker container.${NC}"
  echo -e "  ${DIM}Build the sandbox image once with: docker build -f backend/Dockerfile.sandbox -t ragagent/sandbox:latest ./backend${NC}"
  echo ""
  if confirm "Build sandbox image now?"; then
    echo -e "  Building ragagent/sandbox:latest …"
    docker build -f "$SCRIPT_DIR/backend/Dockerfile.sandbox" -t ragagent/sandbox:latest "$SCRIPT_DIR/backend"
    echo -e "  ${GREEN}✓${NC}  Sandbox image built."
  else
    echo -e "  ${YELLOW}Skipped.${NC} Build later: ${BOLD}docker build -f backend/Dockerfile.sandbox -t ragagent/sandbox:latest ./backend${NC}"
    echo -e "  ${DIM}Set SANDBOX_ENABLED=false in .env.prod to disable the sandbox entirely.${NC}"
  fi

  fi  # end UPDATE_ENV

  # ── Launch ─────────────────────────────────────────────────────────────────
  PROD_COMPOSE="$COMPOSE --env-file .env.prod -f docker-compose.prod.yml"
  echo ""
  echo -e "  How would you like to start?"
  echo -e "    ${BOLD}1${NC}) Build all from source          ${DIM}($PROD_COMPOSE up -d --build)${NC}"
  echo -e "    ${BOLD}2${NC}) Start with existing images     ${DIM}($PROD_COMPOSE up -d)${NC}"
  echo -e "    ${BOLD}3${NC}) Rebuild frontend only          ${DIM}($PROD_COMPOSE up -d --build --no-deps frontend)${NC}"
  echo -e "    ${BOLD}4${NC}) Rebuild backend only           ${DIM}($PROD_COMPOSE up -d --build --no-deps backend)${NC}"
  echo -e "    ${BOLD}5${NC}) Rebuild scheduler only         ${DIM}($PROD_COMPOSE up -d --build --no-deps scheduler)${NC}"
  echo -e "    ${BOLD}6${NC}) Restart frontend only  ${DIM}(no build — $PROD_COMPOSE restart frontend)${NC}"
  echo -e "    ${BOLD}7${NC}) Restart backend only   ${DIM}(no build — $PROD_COMPOSE restart backend)${NC}"
  echo -e "    ${BOLD}8${NC}) Restart scheduler only ${DIM}(no build — $PROD_COMPOSE restart scheduler)${NC}"
  echo -e "    ${BOLD}9${NC}) Skip — I'll start manually"
  echo ""
  printf "  Choice [1-9]: "
  read -r launch_choice

  echo ""
  cd "$SCRIPT_DIR"
  case "$launch_choice" in
    1) $PROD_COMPOSE up -d --build ;;
    2) $PROD_COMPOSE up -d ;;
    3) $PROD_COMPOSE up -d --build --no-deps frontend ;;
    4) $PROD_COMPOSE up -d --build --no-deps backend ;;
    5) $PROD_COMPOSE up -d --build --no-deps scheduler ;;
    6) $PROD_COMPOSE restart frontend ;;
    7) $PROD_COMPOSE restart backend ;;
    8) $PROD_COMPOSE restart scheduler ;;
    9)
      echo -e "  Run manually:"
      echo -e "    Build all:          ${BOLD}$PROD_COMPOSE up -d --build${NC}"
      echo -e "    Rebuild frontend:   ${BOLD}$PROD_COMPOSE up -d --build --no-deps frontend${NC}"
      echo -e "    Rebuild backend:    ${BOLD}$PROD_COMPOSE up -d --build --no-deps backend${NC}"
      echo -e "    Rebuild scheduler:  ${BOLD}$PROD_COMPOSE up -d --build --no-deps scheduler${NC}"
      echo -e "    Restart frontend:   ${BOLD}$PROD_COMPOSE restart frontend${NC}"
      echo -e "    Restart backend:    ${BOLD}$PROD_COMPOSE restart backend${NC}"
      echo -e "    Restart scheduler:  ${BOLD}$PROD_COMPOSE restart scheduler${NC}"
      ;;
    *)
      echo -e "  ${YELLOW}Invalid choice — skipping launch.${NC}"
      echo -e "  Run manually: ${BOLD}$PROD_COMPOSE up -d --build${NC}"
      ;;
  esac
fi

echo ""
echo -e "  ${GREEN}✓  Setup complete.${NC}"
echo ""
echo -e "  ${BOLD}Service URLs${NC}"
echo -e "  ───────────────────────────────────────────────"
echo -e "  Frontend    →  ${CYAN}http://localhost:3000${NC}"
echo -e "  Backend API →  ${CYAN}http://localhost:8081/swagger-ui/index.html${NC}"
echo -e "  Scheduler   →  ${CYAN}http://localhost:8082/health${NC}"
echo -e "  Grafana     →  ${CYAN}http://localhost:3001${NC}  ${DIM}(admin / your password)${NC}"
echo -e "  Prometheus  →  ${CYAN}http://localhost:9090${NC}"
echo -e "  ───────────────────────────────────────────────"
echo -e "  ${DIM}Grafana dashboard: RAG Agent System  (auto-provisioned)${NC}"
echo -e "  ${DIM}Metrics API:       GET /api/v1/metrics  (JWT required)${NC}"
echo ""
