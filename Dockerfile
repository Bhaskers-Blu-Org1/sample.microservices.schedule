FROM open-liberty:microProfile1-java8-ibm
COPY server.xml /config/server.xml
COPY target/microservice-schedule-1.0.0-SNAPSHOT.war /config/apps/schedule.war
