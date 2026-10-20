#!/bin/bash

set -exv

IMAGE="quay.io/cloudservices/notifications-gw"
IMAGE_TAG=$(git rev-parse --short=7 HEAD)

if [[ -z "$QUAY_USER" || -z "$QUAY_TOKEN" ]]; then
    echo "QUAY_USER and QUAY_TOKEN must be set"
    exit 1
fi

if [[ -z "$RH_REGISTRY_USER" || -z "$RH_REGISTRY_TOKEN" ]]; then
    echo "RH_REGISTRY_USER and RH_REGISTRY_TOKEN  must be set"
    exit 1
fi

# Create a temporary directory isolated from $WORKSPACE to store the Docker config and prevent leaking that config in the Docker image.
readonly job_directory_path=$(mktemp --directory -p "${HOME}" -t "jenkins-${JOB_NAME}-${BUILD_NUMBER}.XXXXXX")
echo "Temporary directory location for the Docker config: ${job_directory_path}"

# job_directory_cleanup cleans forcefully and recursively removes the
#                       specified directory path.
# @param directory_path the path of the directory to remove.
function job_directory_cleanup() {
  echo "Cleaning up the temporary directory for the Docker config: ${1}"

  rm --force --recursive "${1}"
}

# Make sure the temporary directory and all of its contents are removed in the
# case where this script does not finish successfully.
trap 'job_directory_cleanup ${job_directory_path}' ERR EXIT SIGINT SIGTERM

# Place our docker configuration in the freshly created temporary directory.
DOCKER_CONF="${job_directory_path}/.docker"
mkdir -p "$DOCKER_CONF"

docker --config="$DOCKER_CONF" login -u="$QUAY_USER" -p="$QUAY_TOKEN" quay.io
docker --config="$DOCKER_CONF" login -u="$RH_REGISTRY_USER" -p="$RH_REGISTRY_TOKEN" registry.redhat.io
docker --config="$DOCKER_CONF" build -t "${IMAGE}:${IMAGE_TAG}" . -f src/main/docker/Dockerfile-build.jvm
docker --config="$DOCKER_CONF" push "${IMAGE}:${IMAGE_TAG}"
docker --config="$DOCKER_CONF" tag "${IMAGE}:${IMAGE_TAG}" "${IMAGE}:qa"
docker --config="$DOCKER_CONF" push "${IMAGE}:qa"
docker --config="$DOCKER_CONF" tag "${IMAGE}:${IMAGE_TAG}" "${IMAGE}:latest"
docker --config="$DOCKER_CONF" push "${IMAGE}:latest"
