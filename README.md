# E-PLANNING · Android

Planning personale, mensile e di squadra e ricerca di cambi turno. Progetto indipendente, non un'app ufficiale aziendale.

## Sincronizzazione Google Calendar

Dalla testata apri il **Menu** → **Impostazioni**, tocca **Collega calendario**, consenti l’accesso al calendario e scegli un calendario Google già configurato sul dispositivo. Nella schermata **Settimana**, l’icona calendario accanto a **Oggi** chiede conferma prima di sincronizzare i turni; rieseguirla non duplica gli eventi e rimuove dalla settimana gli eventi creati dall’app che non sono più presenti nel planning.

Dopo una sincronizzazione riuscita, il badge **✓ Calendar** resta memorizzato sul dispositivo per utente, calendario e settimana, anche riavviando l’app. Se i turni o le notifiche cambiano, indica **da aggiornare**. Ricorda l’ultima esportazione riuscita nel provider Android, non verifica le modifiche esterne o l’avvenuto caricamento sui server Google.

Non sono richieste credenziali OAuth o un account aziendale: Android concede l’accesso al provider calendario locale e Google Calendar sincronizza il calendario Google selezionato.

Nelle impostazioni calendario puoi abilitare due avvisi e scegliere gli anticipi (di default 1 giorno e 1 ora). Vengono salvati come promemoria dell’evento, gestiti dall’app calendario; non sono notifiche inviate da E-PLANNING. Alla sincronizzazione della settimana vengono applicati anche agli eventi già importati, con titolo `tipo di turno • E-PLANNING`.

## Timbratura

In **Settimana**, sotto la barra blu, il pulsante registra una presenza reale solo con un turno attualmente in corso (fuso Europe/Rome) e stato `ENTRY` o `DEPARTURE` autorizzato dal portale. Il controllo usa sempre la settimana corrente, anche navigando settimane diverse, e viene aggiornato ogni 30 secondi mentre la schermata è aperta e al ritorno nell’app.

Fuori turno il pulsante è disabilitato con l’indicazione della disponibilità al prossimo turno. Dopo una registrazione confermata mostra giorno e ora e rimane bloccato per quel turno, anche riaprendo l’app. Servizi consecutivi senza interruzione sono considerati un solo turno; il blocco non impedisce l’uso al turno successivo. Anche le registrazioni già visibili sul portale nel turno vengono riconosciute.

Gli invii con esito incerto non vengono ripetuti automaticamente: il pulsante rimane bloccato finché una lettura dello storico conferma la registrazione. In quel caso controllare il portale o il responsabile prima di altre operazioni. L’orario del server è usato quando disponibile; in attesa dello storico, “Confermata il” indica l’ora in cui l’app ha ricevuto l’esito positivo. Nessuna retrodatazione o modifica manuale degli orari.

## Aggiornamenti nell'app

L'icona download in testata diventa verde quando è disponibile una versione più recente. Un tocco mostra versione e note di rilascio; il pulsante apre nel browser l'URL HTTPS dell'APK della Release GitHub rilevata. Il browser scarica il file e Android gestisce la successiva conferma di installazione.

Android può richiedere il permesso **Consenti da questa fonte** per il browser usato per il download, oltre alla conferma dell'installazione. E-PLANNING non scarica né installa APK e non richiede permessi per installare pacchetti. Al successivo avvio vengono mostrate le novità.

Le verifiche automatiche avvengono all'avvio/ritorno nell'app e ogni 5 minuti durante l'utilizzo; è possibile verificarle manualmente dall'icona. Il download e l'installazione restano sempre un'azione esplicita dell'utente nel browser.

## Pubblicazione automatica

Ogni push su `main` avvia `.github/workflows/publish.yml`:

1. Esegue test e Android Lint.
2. Genera una versione crescente (`versionCode = 2000 + numero esecuzione`, `versionName = 0.5.<numero esecuzione>`), superiore alle build locali 0.4.x.
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
javac -d .gradle/rule-tests app/src/main/java/it/giovaganzerli/planning/{SwapSearch,UpdateRules,WorkdayRules}.java tests/{SwapSearchTest,UpdateRulesTest,WorkdayRulesTest}.java
java -cp .gradle/rule-tests it.giovaganzerli.planning.SwapSearchTest
java -cp .gradle/rule-tests it.giovaganzerli.planning.UpdateRulesTest
java -cp .gradle/rule-tests it.giovaganzerli.planning.WorkdayRulesTest
```

## Limiti della ricerca cambi

Le abilitazioni sono configurate sul dispositivo, separatamente per utente e squadra. Le proposte sono indicative e non modificano il planning: riposi contrattuali, pause e ulteriori vincoli legali richiedono conferma del responsabile.
