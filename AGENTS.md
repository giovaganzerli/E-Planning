# Pubblicazione Android

- Per pubblicare un APK usare `./gradlew :app:publishDebugApk`.
- L'unico APK condivisibile deve restare in `app/build/outputs/apk/debug/`, con nome `E-PLANNING-<versionName>.apk`.
- Il task elimina gli APK precedenti solo dopo una build riuscita. Non copiare APK nella root del progetto.
- Incrementare `versionCode` e `versionName` per ogni aggiornamento pubblicato.
- Il repository pubblico non deve contenere origini operative, credenziali o chiavi di firma. Usare GitHub Secrets e `secrets.properties` locale (ignorato da Git).
- I push su `main` pubblicano una Release firmata tramite GitHub Actions. Distribuire quella build, con versione CI crescente, e conservarla localmente solo nella directory APK prevista.
- Aggiornare `RELEASE_NOTES.md` con le novità di ogni pubblicazione e lanciare i test in `tests/` e `:app:lintDebug`.
