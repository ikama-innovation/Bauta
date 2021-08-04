#!/bin/bash

while getopts v:d:p flag
do
  # shellcheck disable=SC2220
  case "${flag}" in
    v) releaseVersion=${OPTARG};;
    d) developmentVersion=${OPTARG};;
  esac
done


#create tag and release on github
git tag -a "v${releaseVersion}" -m "Version ${releaseVersion}"
git push origin "v${releaseVersion}"


sed -i "s/${releaseVersion}/${developmentVersion}-SNAPSHOT/g" pom.xml
cd bauta-autoconfigure
sed -i "s/${releaseVersion}/${developmentVersion}-SNAPSHOT/g" pom.xml
cd ..
cd bauta-core
sed -i "s/${releaseVersion}/${developmentVersion}-SNAPSHOT/g" pom.xml
cd ..
cd bauta-sample
sed -i "s/${releaseVersion}/${developmentVersion}-SNAPSHOT/g" pom.xml
cd .. 
cd bauta-sample-oracle
sed -i "s/${releaseVersion}/${developmentVersion}-SNAPSHOT/g" pom.xml
cd ..
cd bauta-sample-php
sed -i "s/${releaseVersion}/${developmentVersion}-SNAPSHOT/g" pom.xml
cd ..
cd bauta-starter
sed -i "s/${releaseVersion}/${developmentVersion}-SNAPSHOT/g" pom.xml
cd ..

git add .
git commit -m "Bumped to snapshot version"
git push