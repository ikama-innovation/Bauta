#!/bin/bash


while getopts v:d:p flag
do
  # shellcheck disable=SC2220
  case "${flag}" in
    v) releaseVersion=${OPTARG};;
    d) developmentVersion=${OPTARG};;
    p) password=${OPTARG};;
  esac
done

#ta bort SNAPSHOT taggen
git pull
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

git add . 
git commit -m "removed snapshot"
git push

#deploy maven 
mvn -s settings.xml clean deploy -X -Dgpg.passphrase=${password} -P ossrh,release,production -f pom.xml

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
git commit -m "Bumped version"
git push