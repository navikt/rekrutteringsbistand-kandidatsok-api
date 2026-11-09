# rekrutteringsbistand-kandidatsok-api
TODO: Hva skal være ansvaret til denne appen? Håpet var å erstatte rekrutteringsbistand-kandidat-api men det skjedde ikke, så....

## Fakedings-autentisering ved kjøring lokalt 
Vi bruker [fakedings](https://github.com/navikt/fakedings?tab=readme-ov-file) for autentisering når vi kjører lokalt for å teste underveis i utviklingen. For å hente et fake token, kan du gjøre et kall ala
```http request
POST /fake/aad
Host: fakedings.intern.dev.nav.no
Content-Type: application/x-www-form-urlencoded

client_id=someclientid&
aud=dev-gcp:toi:rekrutteringsbistand-kandidatsok-api&
acr=1&
pid=12345678910&
NAVident=1234
```
for eksempel med kommandoen curl:
```sh
curl --data 'client_id=someclientid&aud=dev-gcp:toi:rekrutteringsbistand-kandidatsok-api&acr=1&pid=12345678910&NAVident=1234' https://fakedings.intern.dev.nav.no/fake/aad
```
og så legge på svaret som
``` 
Authorization: Bearer <token>
```

## Jackson 3: status og videre arbeid

Status per 6. november 2026.

Applikasjonens JSON-håndtering bruker Jackson 3.2.4. Javalin er eksplisitt
konfigurert med `JavalinJackson3`, og OpenSearch-klienten bruker Jackson
3-mapperen. Regresjonstestene dekker blant annet norske feltnavn begge veier,
nullverdier i primitive felt, ukjente felt og ugyldig JSON.

Migreringen av applikasjonskoden er gjennomført, men Jackson 2 finnes fortsatt
transitivt. De undersøkte stabile bibliotekversjonene har følgende bindinger:

| Bibliotek | Gjenværende bruk av Jackson 2 |
|---|---|
| Auth0 `java-jwt` 4.6.2 og `jwks-rsa` 0.24.2 | Bruker Jackson 2 internt til parsing av token og nøkler. Se publiserte avhengigheter for [java-jwt](https://repo.maven.apache.org/maven2/com/auth0/java-jwt/4.6.2/java-jwt-4.6.2.pom) og [jwks-rsa](https://repo.maven.apache.org/maven2/com/auth0/jwks-rsa/0.24.2/jwks-rsa-0.24.2.pom). |
| OpenSearch Java 3.10.0 | Har Jackson 3-adapter, men [deklarerer fortsatt Jackson 2 som runtime-avhengighet](https://repo.maven.apache.org/maven2/org/opensearch/client/opensearch-java/3.10.0/opensearch-java-3.10.0.pom). |
| Javalin OpenAPI 7.2.4 | [Generatoren](https://repo.maven.apache.org/maven2/io/javalin/community/openapi/openapi-generator/7.2.4/openapi-generator-7.2.4.pom) og [annotasjonsprosessoren](https://repo.maven.apache.org/maven2/io/javalin/community/openapi/openapi-annotation-processor/7.2.4/openapi-annotation-processor-7.2.4.pom) bruker Jackson 2. |
| WireMock standalone 3.13.2 | [Pakker inn og relokerer Jackson 2.20.1](https://github.com/wiremock/wiremock/blob/3.13.2/build.gradle). Applikasjonens BOM styrer ikke denne kopien. |

Jackson 2-BOM-en på 2.22.4 beholdes for å samordne de ordinære transitive
avhengighetene i applikasjonen. OpenAPI-prosessoren har et separat Jackson
2.21.0-avhengighetssett i `kapt`; applikasjonens BOM samordner ikke dette.

### Anbefalt framgangsmåte

Gjennomgangen fant ingen stabil direkteoppgradering som fjerner de gjenværende
Jackson 2-implementasjonene. Behold derfor dagens oppsett inntil kompatible
bibliotekversjoner finnes. Ikke erstatt eller fork bibliotekene, eller
ekskluder klasser de trenger, bare for å fjerne Jackson 2 fra avhengighetsgrafen.
Ordinære vedlikeholds- og sikkerhetsoppgraderinger må fortsatt vurderes.

1. Undersøk publiserte avhengigheter når nye bibliotekversjoner kommer.
   Oppgrader én bibliotekfamilie om gangen.
2. Kontroller både produksjons-, test- og `kapt`-avhengigheter. Kjør relevante
   regresjonstester og `./gradlew clean build distTar`, og undersøk bibliotekene
   i den ferdige distribusjonen. Innpakket Jackson-kode må kontrolleres
   separat fra avhengighetsgrafen.
3. Fjern Jackson 2-BOM-en når ingen komponent trenger Jackson 2-implementasjonene.
   Kontroller deretter alle berørte avhengighetssett på nytt.

Målet er ingen Jackson 2-implementasjon, heller ikke i tester eller
annotasjonsprosessering. `com.fasterxml.jackson.core:jackson-annotations`
og importene fra `com.fasterxml.jackson.annotation.*` er et tilsiktet unntak:
[Jackson 3 bruker fortsatt disse annotasjonene med 2.x-versjon og gammelt
pakkenavn](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md).

## Avhengigheter og Dependabot

Dependabot ser etter oppdateringer av Gradle-avhengigheter og GitHub Actions hver
dag (se [`.github/dependabot.yml`](.github/dependabot.yml)). Oppdateringer
samles i gruppene `minor-og-patch` og `major`.

[`dependabot-automerge.yaml`](.github/workflows/dependabot-automerge.yaml) bygger og
tester Dependabot-PR-er. Er bygget grønt og PR-en bare inneholder patch- og
minor-oppdateringer, merges den automatisk, og deploy til dev og prod startes.
Major-oppdateringer må gjennomgås og merges manuelt.

# Kode generert av GitHub Copilot
Dette repoet bruker GitHub Copilot til å generere kode.

## Henvendelser

### For Nav-ansatte
* Dette Git-repositoriet eies av [team Toi](https://teamkatalog.nav.no/team/76f378c5-eb35-42db-9f4d-0e8197be0131).
* Slack: [#arbeidsgiver-toi-dev](https://nav-it.slack.com/archives/C02HTU8DBSR)

### For folk utenfor Nav
* IT-avdelingen i [Arbeids- og velferdsdirektoratet](https://www.nav.no/no/NAV+og+samfunn/Kontakt+NAV/Relatert+informasjon/arbeids-og-velferdsdirektoratet-kontorinformasjon)
