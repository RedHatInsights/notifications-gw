# Deployment Guidelines

## Container Image Build

- Use `src/main/docker/Dockerfile-build.jvm` as the sole Dockerfile for all builds (CI, Tekton, Jenkins). Do not introduce alternative Dockerfiles.
- Keep the multi-stage structure: build stage uses `ubi9/openjdk-21:latest`, runtime stage uses `ubi9/openjdk-21-runtime:latest`. Both from `registry.access.redhat.com`.
- Preserve the four-layer COPY pattern for Quarkus app artifacts (`lib/`, `*.jar`, `app/`, `quarkus/`) to maximize Docker layer caching.
- Keep the `COPY --from=build /home/jboss/LICENSE /licenses/LICENSE` line -- Konflux preflight checks require it.
- Keep the `microdnf upgrade` step in the runtime stage to ensure base image packages are patched at build time.
- The Dockerfile exposes port 8080, but the ClowdApp overrides the Quarkus port to 8000 via `QUARKUS_HTTP_PORT`. Do not change either value independently.

## CI/CD Pipelines

- Four Tekton PipelineRun files exist in `.tekton/`:
  - `notifications-gw-push.yaml` / `notifications-gw-pull-request.yaml` -- target the `main` branch, push to `quay.io/redhat-user-workloads/hcc-integrations-tenant/notifications/notifications-gw`.
  - `notifications-gw-sc-push.yaml` / `notifications-gw-sc-pull-request.yaml` -- target the `security-compliance` branch, push to `quay.io/redhat-user-workloads/hcc-integrations-tenant/notifications-sc/notifications-gw-sc`.
- PR image builds include `image-expires-after: 5d`. Do not remove this expiration on pull-request pipeline files.
- The `main` branch Tekton pipelines reference `docker-build-oci-ta.yaml` from `konflux-pipelines`. The `security-compliance` pipelines reference `docker-build.yaml`. Prefer keeping these pipeline references aligned with upstream `RedHatInsights/konflux-pipelines`.
- Service accounts follow the naming convention `build-pipeline-notifications-gw` (main) and `build-pipeline-notifications-gw-sc` (security-compliance). Keep them consistent with `.tekton/` file names.

## GitHub Actions Workflows

- `build.yml` runs `./mvnw clean package --no-transfer-progress` on push and PR. It caches `~/.m2/repository` keyed on `pom.xml` hash.
- `base-image-auto-update.yml` runs daily, checks the `ubi9/openjdk-21-runtime:latest` digest via `skopeo`, and opens a PR if it changed. The digest is tracked in `.baseimage` at the repo root.
- `platsec-gw.yml` runs Anchore Grype vulnerability scanning and Syft SBOM generation. It points to `dockerfile_path: './src/main/docker'` and `dockerfile_name: 'Dockerfile-build.jvm'`.
- `codeql-analysis.yml` runs CodeQL for Java on push/PR to `main`.

## ClowdApp Deployment (`.rhcicd/clowdapp.yaml`)

- The ClowdApp declares a single deployment named `service` with a public web service at API path `notifications-gw`.
- It depends on `notifications-backend` as a Clowder dependency and provisions the Kafka topic `platform.notifications.ingress` (3 partitions, 3 replicas).
- `ENV_NAME` is required and maps to Clowder environments: `ephemeral`, `stage`, or `prod`.
- When adding new environment variables to the app, add both the `env` entry under `podSpec` and a corresponding `parameters` entry with a default value at the bottom of the template.

## Health Probes

- Readiness and liveness probes hit `/health/ready` and `/health/live` on port 8000 (not the Dockerfile-exposed 8080).
- Both probes use `initialDelaySeconds: 40`, `periodSeconds: 10`, `failureThreshold: 3`. Avoid reducing `initialDelaySeconds` below 40 -- the JVM startup requires it.

## Jenkins Build Script (`.rhcicd/build_deploy.sh`)

- Tags images with the 7-char git SHA, plus `qa` and `latest` tags, and pushes all three to `quay.io/cloudservices/notifications-gw`.
- Requires `QUAY_USER`, `QUAY_TOKEN`, `RH_REGISTRY_USER`, and `RH_REGISTRY_TOKEN` environment variables. These are injected by the CI environment -- do not hardcode credentials.
- Docker config is stored in a temporary directory under `$HOME` (not under `$WORKSPACE`) to prevent leaking credentials into the image.

## Local Development Environment

- `docker-compose.yaml` provides Zookeeper and Kafka (Strimzi 0.19.0, Kafka 2.5.0) for local development. Kafka is exposed on `localhost:9092`.
- `application.properties` defaults `quarkus.http.port` to 8086 for local dev (differs from the deployed port of 8000).
- The `notifications-backend` REST client URL defaults to `http://localhost:8085` when Clowder config is absent.

## Verification

```bash
# Validate the Dockerfile builds successfully
./mvnw clean package -DskipTests --no-transfer-progress
docker build -t notifications-gw-test -f src/main/docker/Dockerfile-build.jvm .

# Verify ClowdApp template is valid YAML
python3 -c "import yaml; yaml.safe_load(open('.rhcicd/clowdapp.yaml'))"

# Check that Tekton pipeline files are valid YAML
for f in .tekton/*.yaml; do python3 -c "import yaml; yaml.safe_load(open('$f'))"; done

# Run the full build (includes tests)
./mvnw clean package --no-transfer-progress

# Start local Kafka for integration testing
docker compose up -d
```
