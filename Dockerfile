FROM gcr.io/distroless/java21-debian12:nonroot
ADD build/distributions/rekrutteringsbistand-kandidatsok-api-1.0-SNAPSHOT.tar /
ENTRYPOINT ["java", "-cp", "/rekrutteringsbistand-kandidatsok-api-1.0-SNAPSHOT/lib/*", "no.nav.toi.MainKt"]