# Infrastructure Setup

This directory contains everything you need to run the workshop's backing services locally using Docker Compose.

## Prerequisites

| Tool | Minimum Version | Check |
|---|---|---|
| Docker & Compose | 27+ / v2 | `docker compose version` |
| (Optional) NVIDIA GPU | CUDA 12+ | `nvidia-smi` |

> **No GPU? No problem.** Ollama runs on CPU just fine — responses will simply be slower. The `llama3.2` 3B model is perfectly usable on a modern laptop with 16 GB RAM.

## Services

| Service | URL | Used In | Purpose |
|---|---|---|---|
| Ollama API | http://localhost:11434 | All labs | Local LLM inference |
| Qdrant REST | http://localhost:6333 | Lab 2 | Vector storage for RAG |
| Qdrant Dashboard | http://localhost:6333/dashboard | Lab 2 | Visual collection browser |
| Qdrant gRPC | localhost:6334 | Lab 2 | gRPC alternative |

## Quick Start

```bash
# 1. Start all services
cd infra
docker compose up -d

# 2. Pull the models used in the workshop
docker compose exec ollama ollama pull llama3.2           # Chat model (~2 GB)
docker compose exec ollama ollama pull nomic-embed-text   # Embedding model (~274 MB)

# 3. Verify everything is healthy
docker compose ps                             # Both services should show "healthy"
curl http://localhost:11434/api/tags          # Lists pulled Ollama models
curl http://localhost:6333/readyz             # Returns "ok" when Qdrant is ready
```

## Troubleshooting

**Ollama is slow on the first request:** The model loads into memory on the first inference call. Subsequent calls are fast.

**Port conflicts:** If `11434` or `6333` are already in use, adjust the left-hand port numbers in `docker-compose.yml` and update the matching properties in the affected lab's `application.properties`.

**GPU not detected:** Uncomment the `deploy.resources` block in the `ollama` service definition and ensure the NVIDIA Container Toolkit is installed. Ollama will fall back to CPU automatically if the GPU is unavailable.

**Qdrant data persists between restarts** via the `qdrant-data` Docker volume. If you want a clean slate between runs, use `docker compose down -v`.

## Tearing Down

```bash
docker compose down        # Stop services, keep model cache and vector data
docker compose down -v     # Stop services AND delete all stored data
```
