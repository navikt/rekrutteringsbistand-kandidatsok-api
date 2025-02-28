FROM gcr.io/distroless/java21-debian12:nonroot
ADD build/distributions/rekrutteringsbistand-kandidatsok-api-1.0-SNAPSHOT.tar /

# Important for Securelogs: The -cp command explicitly force using the logback.xml file of this project, to avoid accidental use of any logback.xml in it's dependencies, e.g. rapids-and-rivers.
# We did have an issue with logging meant for Securelogs accidentally ending up in ordinary applicaion logs.
ADD build/resources/main/logback.xml /
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-Dlogback.configurationFile=/logback.xml", "-cp", "/rekrutteringsbistand-kandidatsok-api-1.0-SNAPSHOT/lib/*", "no.nav.toi.MainKt"]
