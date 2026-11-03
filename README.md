# rekrutteringsbistand-kandidatsok-api
TODO: Hva skal være ansvaret til denne appen? Håpet var å erstatte rekrutteringsbistand-kandidat-api men det skjedde ikke, så....

## Førerkort B78

Send `B 78 - Personbil med automatgir` i `førerkort` for å søke etter kandidater med B78 eller B.
B78-filteret inkluderer også de øvrige klassene som dagens B-filter finner.
B-filteret er uendret og inkluderer ikke B78. Valg av begge filtrene gir samme treff som B78 alene.

API-et oversetter B78 til indeksverdien `B78 - B 78 - Personbil med automatgir`.
Verdien følger kodekjeden fra [CV-API-ets beskrivelse](https://github.com/navikt/pam-cv-api-gcp/blob/main/src/main/kotlin/no/nav/arbeid/cv/rest/dto/cv/Forerkortklasse.kt)
og [konvertering til CV-hendelse](https://github.com/navikt/pam-cv-api-gcp/blob/2dfceab3dab75fd8917d6939eb17a743d0558ce3/src/main/kotlin/no/nav/arbeid/cv/rest/dto/cv/DriversLicense.kt),
via [konvertering til Avro](https://github.com/navikt/pam-cv-endret-bridge/blob/master/src/main/kotlin/no/nav/cv/dto/cv/CvEndretInternDriversLicence.kt),
til [indekseringen](https://github.com/navikt/toi-rapids-and-rivers/blob/1557564132cf61c6f0d89bc6c5f7775a45009336/apps/toi-kandidat-indekser/src/main/kotlin/no/nav/arbeidsgiver/toi/kandidat/indekser/domene/EsForerkort.kt).
Dette gjelder når CV-ens klasse er `B78`. Kontrakten er utledet fra koden, ikke kontrollert mot produksjonsdata.

Deploy backend-støtten før frontend aktiverer B78-valget, ellers avviser API-et filterverdien.

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

# Kode generert av GitHub Copilot
Dette repoet bruker GitHub Copilot til å generere kode.

## Henvendelser

### For Nav-ansatte
* Dette Git-repositoriet eies av [team Toi](https://teamkatalog.nav.no/team/76f378c5-eb35-42db-9f4d-0e8197be0131).
* Slack: [#arbeidsgiver-toi-dev](https://nav-it.slack.com/archives/C02HTU8DBSR)

### For folk utenfor Nav
* IT-avdelingen i [Arbeids- og velferdsdirektoratet](https://www.nav.no/no/NAV+og+samfunn/Kontakt+NAV/Relatert+informasjon/arbeids-og-velferdsdirektoratet-kontorinformasjon)
