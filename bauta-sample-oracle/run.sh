# Start server with a spring profile. The profile must be defined in src/main/resources/application.yml
java -Dspring.profiles.active=mattias,productionMode -jar target/bauta-sample-oracle-0.0.2-SNAPSHOT.jar
