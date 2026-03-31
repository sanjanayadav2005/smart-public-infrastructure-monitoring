FROM openjdk:17

WORKDIR /app

COPY . .

RUN javac -cp "lib/*" -d out src/CivicWatchServer.java

CMD ["java", "-cp", "out:lib/*", "CivicWatchServer"]