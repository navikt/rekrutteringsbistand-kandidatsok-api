# rekrutteringsbistand-kandidatsok-api
TODO

## Fakedings-autentisering (lokal utikling)
Vi bruker [fakedings](https://github.com/navikt/fakedings?tab=readme-ov-file) for autentisering
i lokal utvikling. For å hente et fake token, kan du gjøre et kall ala
```http request
POST /fake/custom

Host: fakedings.intern.dev.nav.no
Content-Type: application/x-www-form-urlencoded

client_id=someclientid&
aud=dev-gcp:targetteam:targetapp&
acr=1&
pid=12345678910&
NAVident=1234
```
og så legge på svaret som
```
Authorization: Bearer <token>
```


## Henvendelser

### For Nav-ansatte
* Dette Git-repositoriet eies av [Team Toi i Produktområde arbeidsgiver](https://teamkatalog.nav.no/team/76f378c5-eb35-42db-9f4d-0e8197be0131).
* Slack: [#arbeidsgiver-toi-dev](https://nav-it.slack.com/archives/C02HTU8DBSR)

### For folk utenfor Nav
* IT-avdelingen i [Arbeids- og velferdsdirektoratet](https://www.nav.no/no/NAV+og+samfunn/Kontakt+NAV/Relatert+informasjon/arbeids-og-velferdsdirektoratet-kontorinformasjon)
