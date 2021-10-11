#!/bin/bash

while getopts v:d:p flag
do
  # shellcheck disable=SC2220
  case "${flag}" in
    v) releaseVersion=${OPTARG};;
    d) developmentVersion=${OPTARG};;
  esac
done

#ta bort SNAPSHOT taggen
sed -i "s/${releaseVersion}-SNAPSHOT/${releaseVersion}/g" pom.xml
cd bauta-autoconfigure
sed -i "s/${releaseVersion}-SNAPSHOT/${releaseVersion}/g" pom.xml
cd ..
cd bauta-core
sed -i "s/${releaseVersion}-SNAPSHOT/${releaseVersion}/g" pom.xml
cd ..
cd bauta-sample
sed -i "s/${releaseVersion}-SNAPSHOT/${releaseVersion}/g" pom.xml
cd ..
cd bauta-sample-oracle
sed -i "s/${releaseVersion}-SNAPSHOT/${releaseVersion}/g" pom.xml
cd ..
cd bauta-sample-php
sed -i "s/${releaseVersion}-SNAPSHOT/${releaseVersion}/g" pom.xml
cd ..
cd bauta-starter
sed -i "s/${releaseVersion}-SNAPSHOT/${releaseVersion}/g" pom.xml
cd ..
cd bauta-standalone
sed -i "s/${releaseVersion}-SNAPSHOT/${releaseVersion}/g" pom.xml
cd ..

