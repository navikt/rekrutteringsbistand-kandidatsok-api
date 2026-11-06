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

## Jackson 2-avhengigheter

Applikasjonen bruker Jackson 3. Jackson 2 finnes fortsatt transitivt via Auth0,
OpenSearch og Javalin OpenAPI. OpenAPI-prosessoren bruker også Jackson 2 i kapt,
og WireMock standalone inneholder relokerte Jackson 2-klasser.

`./gradlew check` kjører `verifyJackson2Baseline`. Kontrollen avviser nye Jackson
2-moduler, endrede Jackson 2-versjoner, nye konsumenter og nye konfigurasjoner
med Jackson 2. Den krever også at unntak fjernes når en avhengighet forsvinner.
Eksisterende unntak ligger i `gradle/jackson2.gradle.kts`. De er midlertidige,
ikke en godkjenning av nye Jackson 2-avhengigheter.

```sh
./gradlew reportJackson2Dependencies reportBundledJackson2
./gradlew verifyNoJackson2
```

Rapportene skrives til `build/reports/jackson2/dependencies.txt` og `bundled.txt`.
Den første viser moduler og konsumenter per konfigurasjon. Den andre viser
Jackson 2-klasser pakket inn i andre avhengigheter, inkludert relokering med
et ekstra pakkeprefiks. Kontrollen dekker prosjektets og buildscriptets
oppløsbare konfigurasjoner og lokale JAR-avhengigheter. Den garanterer ikke
deteksjon av vilkårlig omskrevne pakkenavn, nestede JAR-filer eller separate
verktøyprosesser og konfigurasjoner opprettet internt under andre oppgaver.

`verifyNoJackson2` skal feile så lenge konsumenter eller innpakket Jackson 2
gjenstår. Oppgrader eller erstatt konsumentene én om gangen. Ikke ekskluder
Jackson 2-klasser som bibliotekene fortsatt trenger. Når kontrollen passerer,
fjern Jackson 2-BOM-en, bytt avhengigheten til `check` fra
`verifyJackson2Baseline` til `verifyNoJackson2`, og fjern de midlertidige
unntakene. Kontroller også `lib` i den nybygde distribusjonen.
`com.fasterxml.jackson.core:jackson-annotations` er tillatt fordi Jackson 3
fortsatt bruker denne artefakten.

# Kode generert av GitHub Copilot
Dette repoet bruker GitHub Copilot til å generere kode.

## Henvendelser

### For Nav-ansatte
* Dette Git-repositoriet eies av [team Toi](https://teamkatalog.nav.no/team/76f378c5-eb35-42db-9f4d-0e8197be0131).
* Slack: [#arbeidsgiver-toi-dev](https://nav-it.slack.com/archives/C02HTU8DBSR)

### For folk utenfor Nav
* IT-avdelingen i [Arbeids- og velferdsdirektoratet](https://www.nav.no/no/NAV+og+samfunn/Kontakt+NAV/Relatert+informasjon/arbeids-og-velferdsdirektoratet-kontorinformasjon)
