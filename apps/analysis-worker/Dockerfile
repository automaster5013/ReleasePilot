FROM ghcr.io/astral-sh/uv:0.12.13 AS uv
FROM python:3.14-slim
COPY --from=uv /uv /uvx /bin/
WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy
COPY pyproject.toml uv.lock README.md ./
RUN uv sync --locked --no-dev --no-install-project
COPY src ./src
RUN uv sync --locked --no-dev
RUN useradd --system --uid 10001 releasepilot && chown -R releasepilot:releasepilot /app
USER 10001
ENV PATH="/app/.venv/bin:$PATH"
EXPOSE 8090
CMD ["uvicorn", "releasepilot_analysis_worker.main:app", "--host", "0.0.0.0", "--port", "8090"]
