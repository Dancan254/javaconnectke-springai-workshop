# Infrastructure Setup

This directory contains the Docker Compose stack for the workshop's backing services.

## Ollama — Local vs Docker

The `ollama` service in `docker-compose.yml` is **commented out by default**.

| Setup | What to do |
|---|---|
| Ollama already installed on your machine | Leave it commented — the app connects to `localhost:11434` automatically |
| No local Ollama install | Uncomment the `ollama` block (and its volume) in `docker-compose.yml` |

Pull the required models:

```bash
# If running Ollama locally
ollama pull llama3.2
ollama pull nomic-embed-text

# If running Ollama via Docker Compose (after docker compose up -d)
docker compose exec ollama ollama pull llama3.2
docker compose exec ollama ollama pull nomic-embed-text
```

## Services

| Service | URL | Used In | Purpose |
|---|---|---|---|
| Ollama | http://localhost:11434 | All labs | Local LLM inference (local or Docker) |
| Qdrant REST | http://localhost:6333 | Lab 2 | Vector storage for RAG |
| Qdrant Dashboard | http://localhost:6333/dashboard | Lab 2 | Visual collection browser |
| Prometheus | http://localhost:9090 | All labs | Metrics scraper |
| Grafana | http://localhost:3000 | All labs | Metrics visualisation (`admin` / `workshop`) |

## Prerequisites

| Tool | Minimum Version | Check |
|---|---|---|
| Docker & Compose | 27+ / v2 | `docker compose version` |
| Ollama (if local) | latest | `ollama --version` |

## Quick Start

```bash
cd infra
docker compose up -d
docker compose ps   # all services should show "healthy"
```

## Troubleshooting

**Ollama is slow on the first request:** The model loads into memory on the first inference call. Subsequent calls are much faster.

**Port conflicts:** Adjust the left-hand port numbers in `docker-compose.yml` and update the corresponding values in the affected lab's `application.yml`.

**Qdrant data persists between restarts** via the `qdrant-data` Docker volume. Use `docker compose down -v` for a clean slate.

## Tearing Down

```bash
docker compose down        # Stop services, keep data
docker compose down -v     # Stop services AND delete all stored data
```
