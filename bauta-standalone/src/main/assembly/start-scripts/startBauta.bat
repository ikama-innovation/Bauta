@echo off
title Starting Bauta 
SET SCRIPTPATH=%~dp0
echo %SCRIPTPATH:~0,-1%
if exist %1\ ( 
  echo Directory %1 exists.
) else (
  mkdir %1
  echo Created directory %1 
)

if exist %1\jobs\ (
  echo Directory %1\jobs exists.
) else (
  cd %1
  mkdir jobs
  echo Created directory jobs
  cd ..
)

if exist %1\logs\ (
  echo Directory %1\logs exists.
) else (
  cd %1
  mkdir logs
  echo Created directory logs
  cd ..
)

SET CURRENTDIR=%cd%
cd %SCRIPTPATH:~0,-1%
echo Running command: java -Dbauta.homeDir=%~f1\ -Dbauta.jobBeansDir=%~f1\jobs -Dbauta.logDir=%~f1\logs -Dspring.profiles.active=demo -jar bauta-sample-0.0.60-SNAPSHOT.jar
java -Dbauta.homeDir=%~f1\ -Dbauta.jobBeansDir=%~f1\jobs -Dbauta.logDir=%~f1\logs -Dspring.profiles.active=demo -jar bauta-sample-0.0.60-SNAPSHOT.jar
