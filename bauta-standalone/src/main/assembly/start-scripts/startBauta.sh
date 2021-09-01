#!/usr/bin/env bash

while getopts h: flag
do
    case "${flag}" in
        h) homedir=${OPTARG}
    esac
done

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
rel="absolute"

if  [[ ! $homedir == /* ]] ;
then
    rel="relative"
fi

if [ $# -eq 0 ]; then
    bautaHome="$DIR/home"
    echo "home is $bautaHome"
    cd $DIR
    java -Dbauta.homeDir=$bautaHome -Dbauta.jobBeansDir=$bautaHome/jobs -Dbauta.logDir=$bautaHome/logs -Dbauta.scriptDir=$bautaHome/scripts -Dbauta.reportDir=$bautaHome/reports -Dspring.profiles.active=demo -jar bauta-standalone-0.0.1-SNAPSHOT.jar
fi

if [ -d "${homedir}" ]; then
   echo "Directory ${homedir} exists."
   if [ ! -d "${homedir}/jobs" ]; then
    cd $homedir
    mkdir jobs
    echo "jobs directory created under $homedir"
    cd ..
    fi
    if [ ! -d "${homedir}/logs" ]; then
    cd $homedir
    mkdir logs
    echo "logs directory created under $homedir"
    cd ..
    fi
else
   if [[ $rel == "relative" ]] ; 
   then
   mkdir $homedir
   cd $homedir
   mkdir jobs
   mkdir logs
   echo "Directory $homedir with required subdirectories created in $DIR"
   cd ..
   else
   echo "Directory does not exist. Please create it yourself."
   exit 1
   fi
fi

if [[ $rel == "relative" ]] ; 
   then
   bautaHome="$DIR/$homedir"
   java -Dbauta.homeDir=$bautaHome -Dbauta.jobBeansDir=$bautaHome/jobs -Dbauta.logDir=$bautaHome/logs -Dbauta.scriptDir=$bautaHome/scripts -Dbauta.reportDir=$bautaHome/reports -Dspring.profiles.active=demo -jar bauta-standalone-0.0.1-SNAPSHOT.jar
else
   java -Dbauta.homeDir=$homedir -Dbauta.jobBeansDir=$homedir/jobs -Dbauta.logDir=$homedir/logs -Dbauta.scriptDir=$homedir/scripts -Dbauta.reportDir=$homedir/reports -Dspring.profiles.active=demo -jar bauta-standalone-0.0.1-SNAPSHOT.jar
fi