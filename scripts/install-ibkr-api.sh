#!/usr/bin/env bash
set -euo pipefail

JAR_PATH="${1:-./IBJts/source/JavaClient/TwsApi.jar}"
VERSION="${2:-10.45.01}"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "TwsApi.jar non trovato in '$JAR_PATH'." >&2
  echo "Uso: $0 /percorso/IBJts/source/JavaClient/TwsApi.jar [versione]" >&2
  exit 1
fi

mvn org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
  -Dfile="$JAR_PATH" \
  -DgroupId=com.interactivebrokers \
  -DartifactId=tws-api \
  -Dversion="$VERSION" \
  -Dpackaging=jar \
  -DgeneratePom=true

echo "TwsApi.jar installato come com.interactivebrokers:tws-api:$VERSION"
