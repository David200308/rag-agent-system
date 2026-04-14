# ── Stage 1: install dependencies ─────────────────────────────────────────────
FROM node:20-alpine AS deps
RUN corepack enable
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# ── Stage 2: build ────────────────────────────────────────────────────────────
FROM node:20-alpine AS build
RUN corepack enable
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Build args are baked into the standalone bundle at build time
ARG BACKEND_URL=http://backend:8081
ARG AUTH_ENABLED=true
ARG AUTH_JWT_EXPIRY_HOURS=24
ENV BACKEND_URL=$BACKEND_URL \
    AUTH_ENABLED=$AUTH_ENABLED \
    AUTH_JWT_EXPIRY_HOURS=$AUTH_JWT_EXPIRY_HOURS
RUN pnpm build

# ── Stage 3: runtime ──────────────────────────────────────────────────────────
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production

# Copy the standalone output (includes a self-contained server.js + node_modules)
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
# public/ may be empty — copy only if it exists, otherwise create the dir
RUN mkdir -p ./public
COPY --from=build /app/public ./public

EXPOSE 3000
ENV PORT=3000 \
    HOSTNAME=0.0.0.0

CMD ["node", "server.js"]
