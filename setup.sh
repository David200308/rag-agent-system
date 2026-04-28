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
      prompt OPENAI_MODEL   "Model"            "gpt-4o-mini"
      ANTHROPIC_API_KEY=""; ANTHROPIC_MODEL="claude-opus-4-6"
      OPENROUTER_API_KEY=""; OPENROUTER_MODEL="openai/gpt-4o-mini"
      LOCAL_LLM_URL="http://host.docker.internal:11434"
      LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
      ;;
    anthropic)
      prompt ANTHROPIC_API_KEY "Anthropic API key" "" true
      prompt ANTHROPIC_MODEL   "Model"             "claude-opus-4-6"
      OPENAI_API_KEY=""; OPENAI_MODEL="gpt-4o-mini"
      OPENROUTER_API_KEY=""; OPENROUTER_MODEL="openai/gpt-4o-mini"
      LOCAL_LLM_URL="http://host.docker.internal:11434"
      LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
      ;;
    openrouter)
      prompt OPENROUTER_API_KEY "OpenRouter API key" "" true
      prompt OPENROUTER_MODEL   "Model"              "openai/gpt-4o-mini"
      OPENAI_API_KEY=""; OPENAI_MODEL="gpt-4o-mini"
      ANTHROPIC_API_KEY=""; ANTHROPIC_MODEL="claude-opus-4-6"
      LOCAL_LLM_URL="http://host.docker.internal:11434"
      LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
      ;;
    local)
      prompt LOCAL_LLM_URL        "LLM base URL"      "http://host.docker.internal:11434"
      prompt LOCAL_LLM_MODEL      "Chat model"        "llama3"
      prompt LOCAL_EMBEDDING_MODEL "Embedding model"  "nomic-embed-text"
      OPENAI_API_KEY=""; OPENAI_MODEL="gpt-4o-mini"
      ANTHROPIC_API_KEY=""; ANTHROPIC_MODEL="claude-opus-4-6"
      OPENROUTER_API_KEY=""; OPENROUTER_MODEL="openai/gpt-4o-mini"
      ;;
    *)
      echo -e "${RED}Unknown provider '$LLM_PROVIDER'. Expected: openai | anthropic | openrouter | local${NC}"
      exit 1
      ;;
  esac

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

# ── Passkey (WebAuthn) ────────────────────────────────────────────────────────
# For local dev the defaults (localhost / http://localhost:3000) are used.
AUTH_PASSKEY_RP_ID=$AUTH_PASSKEY_RP_ID
AUTH_PASSKEY_RP_NAME=$AUTH_PASSKEY_RP_NAME
AUTH_PASSKEY_ORIGIN=$AUTH_PASSKEY_ORIGIN

# ── Resend ────────────────────────────────────────────────────────────────────
RESEND_API_KEY=$RESEND_API_KEY
RESEND_FROM_EMAIL=$RESEND_FROM_EMAIL

# ── Weaviate ──────────────────────────────────────────────────────────────────
WEAVIATE_API_KEY=$WEAVIATE_API_KEY

# ── Scheduler microservice ────────────────────────────────────────────────────
SCHEDULER_SERVICE_KEY=$SCHEDULER_SERVICE_KEY

# ── Web fetch ─────────────────────────────────────────────────────────────────
WEB_FETCH_ENABLED=true
WEB_FETCH_TIMEOUT=10
WEB_FETCH_MAX_CHARS=50000
EOF

  echo ""
  echo -e "  ${GREEN}✓${NC}  Written: .env"

  # ── Sandbox image ──────────────────────────────────────────────────────────
  header "Sandbox Image (for Workflow engine)"
  echo -e "  ${DIM}The workflow engine runs agent tool calls inside an isolated Docker container.${NC}"
  echo -e "  ${DIM}Build the sandbox image once with: docker build -f Dockerfile.sandbox -t ragagent/sandbox:latest .${NC}"
  echo ""
  if confirm "Build sandbox image now?"; then
    echo -e "  Building ragagent/sandbox:latest …"
    docker build -f "$SCRIPT_DIR/Dockerfile.sandbox" -t ragagent/sandbox:latest "$SCRIPT_DIR"
    echo -e "  ${GREEN}✓${NC}  Sandbox image built."
  else
    echo -e "  ${YELLOW}Skipped.${NC} Build later: ${BOLD}docker build -f Dockerfile.sandbox -t ragagent/sandbox:latest .${NC}"
    echo -e "  ${DIM}Set SANDBOX_ENABLED=false in .env to disable the sandbox entirely.${NC}"
  fi

  # ── Launch ─────────────────────────────────────────────────────────────────
  echo ""
  echo -e "  How would you like to start?"
  echo -e "    ${BOLD}1${NC}) Build images from source  ${DIM}($COMPOSE up --build)${NC}"
  echo -e "    ${BOLD}2${NC}) Start with existing images ${DIM}($COMPOSE up)${NC}"
  echo -e "    ${BOLD}3${NC}) Skip — I'll start manually"
  echo ""
  printf "  Choice [1/2/3]: "
  read -r launch_choice

  echo ""
  cd "$SCRIPT_DIR"
  case "$launch_choice" in
    1) $COMPOSE up --build ;;
    2) $COMPOSE up ;;
    3)
      echo -e "  Run manually:"
      echo -e "    Build:  ${BOLD}$COMPOSE up --build${NC}"
      echo -e "    Start:  ${BOLD}$COMPOSE up${NC}"
      ;;
    *)
      echo -e "  ${YELLOW}Invalid choice — skipping launch.${NC}"
      echo -e "  Run manually:"
      echo -e "    Build:  ${BOLD}$COMPOSE up --build${NC}"
      echo -e "    Start:  ${BOLD}$COMPOSE up${NC}"
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

  # ── LLM provider ──────────────────────────────────────────────────────────
  header "LLM Provider"
  UPDATE_LLM=true
  if has_secret openai_api_key || has_secret anthropic_api_key || has_secret openrouter_api_key; then
    echo -e "  ${DIM}LLM provider already configured ($(read_prod LLM_PROVIDER openai)).${NC}"
    if ! confirm "Update LLM provider settings?"; then UPDATE_LLM=false; fi
  fi
  if $UPDATE_LLM; then
    echo -e "  Options: ${BOLD}openai${NC} · anthropic · openrouter · local"
    prompt LLM_PROVIDER "Provider" "openai"
    # Blank placeholders keep Docker happy when a provider's secret isn't used
    write_secret openai_api_key     ""
    write_secret anthropic_api_key  ""
    write_secret openrouter_api_key ""
    case "$LLM_PROVIDER" in
      openai)
        prompt OPENAI_API_KEY "OpenAI API key" "" true
        prompt OPENAI_MODEL   "Model"          "gpt-4o-mini"
        write_secret openai_api_key "$OPENAI_API_KEY"
        ANTHROPIC_MODEL="claude-opus-4-6"
        OPENROUTER_MODEL="openai/gpt-4o-mini"
        LOCAL_LLM_URL="http://host.docker.internal:11434"
        LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
        ;;
      anthropic)
        prompt ANTHROPIC_API_KEY "Anthropic API key" "" true
        prompt ANTHROPIC_MODEL   "Model"             "claude-opus-4-6"
        write_secret anthropic_api_key "$ANTHROPIC_API_KEY"
        OPENAI_MODEL="gpt-4o-mini"
        OPENROUTER_MODEL="openai/gpt-4o-mini"
        LOCAL_LLM_URL="http://host.docker.internal:11434"
        LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
        ;;
      openrouter)
        prompt OPENROUTER_API_KEY "OpenRouter API key" "" true
        prompt OPENROUTER_MODEL   "Model"              "openai/gpt-4o-mini"
        write_secret openrouter_api_key "$OPENROUTER_API_KEY"
        OPENAI_MODEL="gpt-4o-mini"
        ANTHROPIC_MODEL="claude-opus-4-6"
        LOCAL_LLM_URL="http://host.docker.internal:11434"
        LOCAL_LLM_MODEL="llama3"; LOCAL_EMBEDDING_MODEL="nomic-embed-text"
        ;;
      local)
        prompt LOCAL_LLM_URL         "LLM base URL"    "http://host.docker.internal:11434"
        prompt LOCAL_LLM_MODEL       "Chat model"      "llama3"
        prompt LOCAL_EMBEDDING_MODEL "Embedding model" "nomic-embed-text"
        OPENAI_MODEL="gpt-4o-mini"; ANTHROPIC_MODEL="claude-opus-4-6"; OPENROUTER_MODEL="openai/gpt-4o-mini"
        ;;
      *)
        echo -e "${RED}Unknown provider '$LLM_PROVIDER'. Expected: openai | anthropic | openrouter | local${NC}"
        exit 1
        ;;
    esac
  else
    LLM_PROVIDER="$(read_prod LLM_PROVIDER openai)"
    OPENAI_MODEL="$(read_prod OPENAI_MODEL gpt-4o-mini)"
    ANTHROPIC_MODEL="$(read_prod ANTHROPIC_MODEL claude-opus-4-6)"
    OPENROUTER_MODEL="$(read_prod OPENROUTER_MODEL openai/gpt-4o-mini)"
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
OPENAI_MODEL=${OPENAI_MODEL:-gpt-4o-mini}
ANTHROPIC_MODEL=${ANTHROPIC_MODEL:-claude-opus-4-6}
OPENROUTER_MODEL=${OPENROUTER_MODEL:-openai/gpt-4o-mini}
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
EOF

  chmod 600 "$PROD_ENV"
  echo -e "  ${GREEN}✓${NC}  Non-secret config written: .env.prod"

  # ── Sandbox image ──────────────────────────────────────────────────────────
  header "Sandbox Image (for Workflow engine)"
  echo -e "  ${DIM}The workflow engine runs agent tool calls inside an isolated Docker container.${NC}"
  echo -e "  ${DIM}Build the sandbox image once with: docker build -f Dockerfile.sandbox -t ragagent/sandbox:latest .${NC}"
  echo ""
  if confirm "Build sandbox image now?"; then
    echo -e "  Building ragagent/sandbox:latest …"
    docker build -f "$SCRIPT_DIR/Dockerfile.sandbox" -t ragagent/sandbox:latest "$SCRIPT_DIR"
    echo -e "  ${GREEN}✓${NC}  Sandbox image built."
  else
    echo -e "  ${YELLOW}Skipped.${NC} Build later: ${BOLD}docker build -f Dockerfile.sandbox -t ragagent/sandbox:latest .${NC}"
    echo -e "  ${DIM}Set SANDBOX_ENABLED=false in .env.prod to disable the sandbox entirely.${NC}"
  fi

  # ── Launch ─────────────────────────────────────────────────────────────────
  echo ""
  echo -e "  How would you like to start?"
  echo -e "    ${BOLD}1${NC}) Build images from source  ${DIM}($COMPOSE -f docker-compose.prod.yml up -d --build)${NC}"
  echo -e "    ${BOLD}2${NC}) Start with existing images ${DIM}($COMPOSE -f docker-compose.prod.yml up -d)${NC}"
  echo -e "    ${BOLD}3${NC}) Skip — I'll start manually"
  echo ""
  printf "  Choice [1/2/3]: "
  read -r launch_choice

  echo ""
  cd "$SCRIPT_DIR"
  case "$launch_choice" in
    1) $COMPOSE --env-file .env.prod -f docker-compose.prod.yml up -d --build ;;
    2) $COMPOSE --env-file .env.prod -f docker-compose.prod.yml up -d ;;
    3)
      echo -e "  Run manually:"
      echo -e "    Build:  ${BOLD}$COMPOSE --env-file .env.prod -f docker-compose.prod.yml up -d --build${NC}"
      echo -e "    Start:  ${BOLD}$COMPOSE --env-file .env.prod -f docker-compose.prod.yml up -d${NC}"
      ;;
    *)
      echo -e "  ${YELLOW}Invalid choice — skipping launch.${NC}"
      echo -e "  Run manually:"
      echo -e "    Build:  ${BOLD}$COMPOSE --env-file .env.prod -f docker-compose.prod.yml up -d --build${NC}"
      echo -e "    Start:  ${BOLD}$COMPOSE --env-file .env.prod -f docker-compose.prod.yml up -d${NC}"
      ;;
  esac
fi

echo ""
echo -e "  ${GREEN}Done.${NC}"
echo ""
