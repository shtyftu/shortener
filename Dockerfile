FROM mcr.microsoft.com/java/jdk:11u8-zulu-alpine
COPY build/libs/shortener-1.0-SNAPSHOT-all.jar shortener.jar
CMD ["/usr/bin/java", "-jar", "/shortener.jar"]