#!/bin/bash

#deploy maven 
mvn -s settings.xml clean deploy -X -P ossrh,release,production -f pom.xml


