#!/bin/bash

while getopts v: flag
do
  # shellcheck disable=SC2220
  case "${flag}" in
    v) releaseVersion=${OPTARG};;
  esac
done
sed -i "s/${releaseVersion}/${releaseVersion}-SNAPSHOT/g" pom.xml
cd bauta-autoconfigure
sed -i "s/${releaseVersion}/${releaseVersion}-SNAPSHOT/g" pom.xml
cd ..
cd bauta-core
sed -i "s/${releaseVersion}/${releaseVersion}-SNAPSHOT/g" pom.xml
cd ..
cd bauta-sample
sed -i "s/${releaseVersion}/${releaseVersion}-SNAPSHOT/g" pom.xml
cd ..
cd bauta-sample-oracle
sed -i "s/${releaseVersion}/${releaseVersion}-SNAPSHOT/g" pom.xml
cd ..
cd bauta-sample-php
sed -i "s/${releaseVersion}/${releaseVersion}-SNAPSHOT/g" pom.xml
cd ..
cd bauta-starter
sed -i "s/${releaseVersion}/${releaseVersion}-SNAPSHOT/g" pom.xml
cd ..