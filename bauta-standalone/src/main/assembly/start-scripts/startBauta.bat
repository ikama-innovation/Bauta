@echo off
title Starting Bauta 
SET BAUTA_HOME=%~dp0..\home
java -Dbauta.homeDir=%BAUTA_HOME% -Dspring.profiles.active=dev -jar bauta-standalone.jar
pause
