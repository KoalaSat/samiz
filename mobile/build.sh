#!/bin/bash

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <version> <appName>"
  exit 1
fi

version=$1
appName=$2

./gradlew clean bundleRelease --stacktrace
./gradlew assembleRelease --stacktrace
rm ~/release/samiz-*
rm ~/release/manifest-*
mv app/build/outputs/bundle/release/app-release.aab ~/release/
mv app/build/outputs/apk/release/app-* ~/release/
./gradlew --stop
cd ~/release
./generate_manifest.sh ${version} ${appName}
