FROM gcr.io/distroless/java21:nonroot
ADD build/distributions/rekrutteringsbistand-kandidatsok-api-1.0-SNAPSHOT.tar /

# Asume that logback.xml is located in the project/app root dir.
# The unconventional location is a signal to developers to make them aware that we use this file in an unconventional
# way in the ENTRYPOINT command in this Dockerfile.
COPY logback.xml /

# Set logback.xml explicitly, to avoid accidentally using any logback.xml bundled in the JAR-files of the app's dependencies
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-Dlogback.configurationFile=/logback.xml", "-cp", "/rekrutteringsbistand-kandidatsok-api-1.0-SNAPSHOT/lib/*", "no.nav.toi.MainKt"]
