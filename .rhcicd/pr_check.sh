#!/bin/bash

set -exv

# Clowder config
export APP_NAME="notifications"
export COMPONENT_NAME="notifications-gw"
# The ClowdApp template from stage will be used in ephemeral, meaning that breaking template changes don't have to be deployed in production to fix the ephemeral deployment
export REF_ENV="insights-stage"
export IMAGE="quay.io/cloudservices/notifications-gw"

# Bonfire init
CICD_URL=https://raw.githubusercontent.com/RedHatInsights/bonfire/master/cicd
curl -s $CICD_URL/bootstrap.sh > .cicd_bootstrap.sh && source .cicd_bootstrap.sh

# Build the image and push to Quay
export DOCKERFILE=src/main/docker/Dockerfile-build.jvm
source $CICD_ROOT/build.sh

# Deploy on ephemeral
export COMPONENTS="notifications-gw"
source $CICD_ROOT/deploy_ephemeral_env.sh

# Until test results produce a junit XML file, create a dummy result file so Jenkins will pass
mkdir -p $WORKSPACE/artifacts
cat << EOF > ${WORKSPACE}/artifacts/junit-dummy.xml
<testsuite tests="1">
    <testcase classname="dummy" name="dummytest"/>
</testsuite>
EOF
