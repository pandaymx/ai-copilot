#!/usr/bin/env bash
# ==============================================================================
# AI-Copilot Rootless One-Click Launcher & Docker Containerization Helper
# Supports: Infrastructure (Postgres, Redis, Ollama) + Backend + Frontend
# ==============================================================================

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/compose.yaml"
INFRA_COMPOSE_FILE="$SCRIPT_DIR/backend/compose.yaml"

# Text styles & Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

log_info()    { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }
log_infra()   { echo -e "${CYAN}[INFRA]${NC} $1"; }
log_backend() { echo -e "${MAGENTA}[BACKEND]${NC} $1"; }
log_front()   { echo -e "${BLUE}[FRONTEND]${NC} $1"; }

# 1. Detect Container Engine (Rootless friendly)
detect_compose_engine() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
  elif command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
    COMPOSE_CMD="podman compose"
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD="docker-compose"
  elif command -v podman-compose >/dev/null 2>&1; then
    COMPOSE_CMD="podman-compose"
  else
    COMPOSE_CMD=""
  fi
}

detect_compose_engine

# Stop Infrastructure / Containers
stop_containers() {
  if [ -n "$COMPOSE_CMD" ]; then
    if [ -f "$COMPOSE_FILE" ]; then
      log_infra "Stopping containers using '$COMPOSE_CMD -f compose.yaml'..."
      $COMPOSE_CMD -f "$COMPOSE_FILE" down
    fi
    if [ -f "$INFRA_COMPOSE_FILE" ]; then
      $COMPOSE_CMD -f "$INFRA_COMPOSE_FILE" down 2>/dev/null || true
    fi
    log_infra "All containers stopped."
  else
    log_warn "No container compose engine found to stop."
  fi
}

# Service Readiness Check
wait_for_port() {
  local host=$1
  local port=$2
  local name=$3
  local max_retries=${4:-25}
  local count=0

  log_infra "Waiting for $name ($host:$port) to be ready..."
  while ! (nc -z "$host" "$port" 2>/dev/null || (exec 3<>/dev/tcp/"$host"/"$port") 2>/dev/null); do
    count=$((count + 1))
    if [ "$count" -ge "$max_retries" ]; then
      log_warn "$name on port $port is not responding after $max_retries attempts. Proceeding..."
      return 0
    fi
    sleep 1
  done
  log_infra "$name ($host:$port) is READY!"
}

# Status Check
check_status() {
  echo "=================================================="
  echo "              AI-Copilot Status Check             "
  echo "=================================================="
  if [ -n "$COMPOSE_CMD" ]; then
    if [ -f "$COMPOSE_FILE" ]; then
      log_infra "Full Container Status ($COMPOSE_CMD):"
      $COMPOSE_CMD -f "$COMPOSE_FILE" ps
    elif [ -f "$INFRA_COMPOSE_FILE" ]; then
      log_infra "Infrastructure Container Status ($COMPOSE_CMD):"
      $COMPOSE_CMD -f "$INFRA_COMPOSE_FILE" ps
    fi
  else
    log_warn "No container engine detected."
  fi

  echo ""
  log_info "Port Availability Check:"
  for port_info in "5432:PostgreSQL" "6379:Redis" "11434:Ollama" "8084:Backend" "3000:Frontend"; do
    IFS=":" read -r port name <<< "$port_info"
    if (nc -z localhost "$port" 2>/dev/null || (exec 3<>/dev/tcp/localhost/"$port") 2>/dev/null); then
      echo -e "  Port $port ($name): ${GREEN}RUNNING / LISTENING${NC}"
    else
      echo -e "  Port $port ($name): ${RED}STOPPED${NC}"
    fi
  done
  echo "=================================================="
}

# Start Infrastructure Only
start_infra() {
  local target_file="$INFRA_COMPOSE_FILE"
  if [ -f "$COMPOSE_FILE" ]; then
    target_file="$COMPOSE_FILE"
  fi

  if [ -n "$COMPOSE_CMD" ] && [ -f "$target_file" ]; then
    log_infra "Starting infrastructure services via '$COMPOSE_CMD'..."
    $COMPOSE_CMD -f "$target_file" up -d postgres redis ollama 2>/dev/null || $COMPOSE_CMD -f "$target_file" up -d
    wait_for_port "localhost" "5432" "PostgreSQL"
    wait_for_port "localhost" "6379" "Redis"
    wait_for_port "localhost" "11434" "Ollama" 5
  else
    log_warn "Container engine not found. Skipping infrastructure auto-boot."
  fi
}

# Start Full Dockerized App (Containers for Infra + Backend + Frontend)
start_docker_all() {
  if [ -z "$COMPOSE_CMD" ]; then
    log_error "No container compose engine found (docker compose / podman compose required)."
    exit 1
  fi
  log_info "Building and starting all full-stack services via $COMPOSE_CMD..."
  $COMPOSE_CMD -f "$COMPOSE_FILE" up -d --build
  check_status
}

# Handle CLI Flags
case "$1" in
  --down|down|stop|--stop)
    stop_containers
    exit 0
    ;;
  --infra-only|infra)
    start_infra
    exit 0
    ;;
  --docker|docker)
    start_docker_all
    exit 0
    ;;
  --status|status|ps)
    check_status
    exit 0
    ;;
  --help|-h)
    echo "Usage: ./start.sh [OPTION]"
    echo ""
    echo "Options:"
    echo "  (no args)     Start Infra in container + Backend & Frontend natively"
    echo "  docker        Start EVERYTHING in Docker containers (Infra + Backend + Frontend)"
    echo "  infra         Start Infrastructure containers only (PostgreSQL, Redis, Ollama)"
    echo "  stop | down   Stop all containers"
    echo "  status | ps   Check status of containers and listening ports"
    echo "  --help, -h    Show this help message"
    exit 0
    ;;
esac

# 2. Main Full-Stack Hybrid Launch Process
BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  echo ""
  log_info "Received shutdown signal. Stopping services gracefully..."

  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    log_backend "Terminating Backend (PID: $BACKEND_PID)..."
    kill -TERM "$BACKEND_PID" 2>/dev/null || true
  fi

  if [ -n "$FRONTEND_PID" ] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    log_front "Terminating Frontend (PID: $FRONTEND_PID)..."
    kill -TERM "$FRONTEND_PID" 2>/dev/null || true
  fi

  # Wait for processes to exit
  if [ -n "$BACKEND_PID" ]; then wait "$BACKEND_PID" 2>/dev/null || true; fi
  if [ -n "$FRONTEND_PID" ]; then wait "$FRONTEND_PID" 2>/dev/null || true; fi

  log_info "All services stopped cleanly. Goodbye!"
  exit 0
}

trap cleanup SIGINT SIGTERM EXIT

log_info "Starting AI-Copilot (Rootless Orchestration)..."

# Step 1: Infrastructure
start_infra

# Step 2: Backend (Spring Boot)
log_backend "Launching Spring Boot Backend..."
(
  cd "$SCRIPT_DIR/backend"
  ./gradlew bootRun
) &
BACKEND_PID=$!

# Step 3: Frontend (Next.js)
log_front "Launching Next.js Frontend..."
(
  cd "$SCRIPT_DIR/frontend"
  if command -v bun >/dev/null 2>&1; then
    bun dev
  elif command -v npm >/dev/null 2>&1; then
    npm run dev
  else
    log_error "Neither bun nor npm found!"
    exit 1
  fi
) &
FRONTEND_PID=$!

log_info "Backend (PID: $BACKEND_PID) and Frontend (PID: $FRONTEND_PID) started."
log_info "Press Ctrl+C to stop all services."

# Wait for background processes
wait $BACKEND_PID $FRONTEND_PID 2>/dev/null || true
