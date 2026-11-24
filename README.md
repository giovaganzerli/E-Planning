# E-PLANNING · Android

Planning personale, mensile e di squadra e ricerca di cambi turno. Progetto indipendente, non un'app ufficiale aziendale.

## Aggiornamenti nell'app

L'icona download in testata diventa verde quando è disponibile una versione più recente. Un tocco mostra versione e note di rilascio; l'app scarica l'APK dalla Release GitHub, controlla dimensione, SHA-256, package ID, versione e firma e apre il programma di installazione Android.

Android richiede il permesso **Consenti da questa fonte** per E-PLANNING e la conferma dell'installazione. Non si tratta di un aggiornamento silenzioso. Al successivo avvio vengono mostrate le novità. Impostazioni e sessione rimangono nell'app.

Per gli APK precedenti all'introduzione dell'updater è necessaria **una sola installazione manuale** della nuova versione. Le successive passano dall'app. Le verifiche automatiche avvengono all'avvio/ritorno nell'app e ogni 5 minuti durante l'utilizzo; è possibile verificarle manualmente dall'icona.

## Pubblicazione automatica

Ogni push su `main` avvia `.github/workflows/publish.yml`:

1. Esegue test e Android Lint.
2. Genera una versione crescente (`versionCode = 1000 + numero esecuzione`, `versionName = 0.4.<numero esecuzione>`).
3. Compila un APK non debuggabile con la stessa firma degli APK già installati.
4. Pubblica APK e `update.json` in una GitHub Release, resa pubblica solo quando entrambi i file sono pronti.

Aggiornare `RELEASE_NOTES.md` insieme al codice: è mostrato sia prima sia dopo l'installazione. La CI aggiunge i titoli dei commit successivi alla Release precedente. Non scrivere credenziali o dati personali nei commit o nelle note.

Secrets richiesti nelle impostazioni GitHub Actions:

| Secret | Contenuto |
|---|---|
| `PORTAL_URL` | Origine HTTPS del portale di login |
| `API_BASE_URL` | Origine HTTPS delle API |
| `ANDROID_KEYSTORE_BASE64` | Keystore di distribuzione codificato in base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Password del keystore |
| `ANDROID_KEY_ALIAS` | Alias della chiave |
| `ANDROID_KEY_PASSWORD` | Password della chiave |

Non cambiare chiave o `applicationId`: Android non aggiornerebbe le installazioni esistenti. Per compatibilità viene mantenuto il certificato storico, proteggendone la chiave come Secret. Il codice pubblico non contiene gli indirizzi operativi; **questi restano recuperabili dall'APK**, non sono credenziali. Nessun bearer aziendale viene inviato a GitHub.

## Sviluppo locale

Richiede JDK 17, SDK Android 35 e le seguenti variabili d'ambiente (oppure un `secrets.properties` locale, ignorato da Git):

```properties
PORTAL_URL=https://login.example.invalid
API_BASE_URL=https://api.example.invalid
```

Per usare la chiave storica locale viene cercato `~/.android/debug.keystore`; si può impostare `ANDROID_KEYSTORE_PATH` e le altre variabili di firma. Non commettere mai chiavi o file di configurazione privati.

```sh
./gradlew :app:lintDebug
./gradlew :app:publishDebugApk
```

L'APK locale resta in `app/build/outputs/apk/debug/`; gli APK precedenti vengono eliminati solo dopo la build riuscita. Dopo l'attivazione della CI **distribuire gli APK delle Releases**, non build locali con versionCode inferiore. Per riprodurre una versione CI impostare `APP_VERSION_CODE` e `APP_VERSION_NAME`.

Test delle regole senza emulatori:

```sh
mkdir -p .gradle/rule-tests
javac -d .gradle/rule-tests app/src/main/java/it/giovaganzerli/planning/{SwapSearch,UpdateRules}.java tests/{SwapSearchTest,UpdateRulesTest}.java
java -cp .gradle/rule-tests it.giovaganzerli.planning.SwapSearchTest
java -cp .gradle/rule-tests it.giovaganzerli.planning.UpdateRulesTest
```

## Limiti della ricerca cambi

Le abilitazioni sono configurate sul dispositivo, separatamente per utente e squadra. Le proposte sono indicative e non modificano il planning: riposi contrattuali, pause e ulteriori vincoli legali richiedono conferma del responsabile.
