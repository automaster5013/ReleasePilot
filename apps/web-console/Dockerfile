FROM node:24-alpine AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /app
COPY --from=dependencies /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:24-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 10001 releasepilot && adduser --system --uid 10001 --ingroup releasepilot releasepilot
COPY --from=build --chown=releasepilot:releasepilot /app/.next/standalone ./
COPY --from=build --chown=releasepilot:releasepilot /app/.next/static ./.next/static
COPY --from=build --chown=releasepilot:releasepilot /app/public ./public
USER releasepilot
EXPOSE 3000
ENV PORT=3000 HOSTNAME=0.0.0.0
CMD ["node", "server.js"]
