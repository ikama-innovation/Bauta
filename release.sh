#!/bin/bash

#deploy maven 
mvn -s settings.xml clean deploy -P release,production -f pom.xml


