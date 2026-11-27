# E-PLANNING · Android

Planning personale, mensile e di squadra e ricerca di cambi turno. Progetto indipendente, non un'app ufficiale aziendale.

## Scelta del portale

Al primo avvio una pagina dedicata, non un popup, permette di scegliere **E-Planning** oppure **Effiplan**. La scelta resta memorizzata. Da **Menu → Impostazioni → Portale → Cambia portale** puoi tornare alla stessa pagina: confermando un portale diverso, la sessione WebView viene chiusa, cookie e storage di accesso cancellati e l’Activity riavviata per richiedere un nuovo login. Annulla mantiene la sessione corrente. Il pulsante è disponibile anche nella pagina di accesso, se è stato scelto il portale sbagliato. Pagina iniziale, testata login e contenuti rispettano gli spazi delle barre di stato e navigazione Android.

E-Planning mantiene tutte le funzioni esistenti. Effiplan supporta login SSO nel portale ufficiale, settimana, mese, planning collettivo, ricerca cambi e timbratura. Calendario, avvisi e indicatori di sincronizzazione sono separati per portale; le preferenze E-Planning precedenti restano invariate. Anche gli eventi importati nel medesimo calendario hanno marcatori diversi, per non cancellare quelli dell’altro portale.

La settimana **Effiplan va da domenica a sabato**; **E-Planning resta da lunedì a domenica**. Il portale determina gli intervalli API, l’ordine dei giorni nelle viste personali/mensili/di squadra, i riepiloghi ore, Oggi, gli intervalli di sincronizzazione calendario e la selezione/ricerca dei cambi. La ricerca Effiplan include la domenica iniziale ma esclude quella successiva, anche ai confini di mese e anno; restano invariate le altre regole di scambio.

Il planning collettivo Effiplan parte dal raggruppamento predefinito del profilo ufficiale (`GENERAL.COLLECTIVE.GROUPMENT`). Il pulsante **Popolazione**, visibile **solo in Squadra**, permette di cercare e scegliere un’altra sezione, il negozio presente nell’elenco o **Tutta la popolazione autorizzata**. Sezioni e preferiti sono letti dalle API per la sessione corrente; non esistono elenchi di negozi/squadre inclusi nell’app. L’opzione completa rimuove il filtro di sezione, senza aggirare i permessi server; per un ruolo multinegozio può includere più negozi. Il cambio avviene solo nella vista Squadra, senza modificare i preferiti o la selezione sul sito. Se manca il raggruppamento predefinito, scegli una popolazione dal pulsante: non viene selezionato automaticamente l’intero negozio.

Utenti e turni vengono caricati per la popolazione scelta e per tutte le pagine restituite dall’API. Ricerca cambi e configurazione usano invece la squadra predefinita del profilo; la ricerca rilegge separatamente il planning personale. Il parser dei collaboratori Effiplan è distinto da E-Planning e non richiede campi directory/actor; gli omonimi sono distinti tramite identificativo. Dati nascosti o incompleti bloccano la ricerca. Le abilitazioni dei colleghi si configurano nelle Impostazioni, separatamente per account/ruolo/popolazione; l’algoritmo dei cambi è lo stesso di E-Planning. Le assenze impediscono sovrapposizioni con i cambi proposti; quelle espresse a giornate o mezze giornate sono cautelativamente bloccate per l’intera giornata. Non vengono importate come turni nel calendario.

**Nomi e colori Effiplan:** provengono dai dizionari delle API e dal modello di visualizzazione dell’utente, non da un catalogo ricostruito dal primo account. Il modello decide se il colore arriva da qualifica, luogo/bisogno, attività o tipo di turno; vengono normalizzati anche i colori esadecimali senza `#` e mantenuto il colore del testo. I modelli sono memorizzati solo nella sessione e riletti al massimo dopo cinque minuti; i metadati dei turni arrivano con ogni risposta. In assenza di un colore valido viene usato un fondo neutro, senza attribuire una categoria dal colore. Anche nuovi tipi e colori restituiti in futuro sono gestiti con lo stesso percorso.

In **Impostazioni → Tipi di turno** puoi assegnare ai tipi presenti nella popolazione/settimana caricata una categoria per la ricerca cambi (Vendita, Workshop, PFL, Accoglienza, non scambiabile o non assegnato). I codici e nomi dei tipi arrivano dalle API; la classificazione è salvata per account/ruolo, non cambia nomi/colori e non modifica il portale. I tipi sconosciuti non vengono considerati scambiabili finché non classificati. Non è necessario aggiungere sigle specifiche del negozio al codice sorgente.

Logout o cambio portale eliminano la selezione della popolazione e i dati operativi in memoria. Calendario, marcatori degli eventi, categorie dei servizi e registrazioni di timbratura Effiplan sono separati per origine, utente e ruolo; la scelta della popolazione non altera il planning personale o la timbratura.

Il client Effiplan usa il token opaco `x-hq-token`, il ruolo effettivamente selezionato nel portale e il formato JSON previsto dal client ufficiale. Il POST degli eventi è una query di lettura (`realMethod GET` nel client ufficiale), non un aggiornamento del planning. L’unica scrittura implementata è la timbratura richiesta dall’utente tramite il pulsante: `self/clocks`, con gli stessi parametri del client ufficiale. Le letture automatiche non inviano presenze, modifiche del planning o cambi turno.

## Sincronizzazione Google Calendar

Dalla testata apri il **Menu** → **Impostazioni**, tocca **Collega calendario**, consenti l’accesso al calendario e scegli un calendario Google già configurato sul dispositivo. Nella schermata **Settimana**, l’icona calendario accanto a **Oggi** chiede conferma prima di sincronizzare i turni; rieseguirla non duplica gli eventi e rimuove dalla settimana gli eventi creati dall’app che non sono più presenti nel planning.

Dopo una sincronizzazione riuscita, il badge **✓ Calendar** resta memorizzato sul dispositivo per utente, calendario e settimana, anche riavviando l’app. Se i turni o le notifiche cambiano, indica **da aggiornare**. Ricorda l’ultima esportazione riuscita nel provider Android, non verifica le modifiche esterne o l’avvenuto caricamento sui server Google.

Non sono richieste credenziali OAuth o un account aziendale: Android concede l’accesso al provider calendario locale e Google Calendar sincronizza il calendario Google selezionato.

Nelle impostazioni calendario puoi abilitare due avvisi e scegliere gli anticipi (di default 1 giorno e 1 ora). Vengono salvati come promemoria dell’evento, gestiti dall’app calendario; non sono notifiche inviate da E-PLANNING. Alla sincronizzazione della settimana vengono applicati anche agli eventi già importati, con titolo `tipo di turno • E-PLANNING`.

## Timbratura

In **Settimana**, sotto la barra blu, il pulsante registra una presenza reale solo con un turno attualmente in corso (fuso Europe/Rome) e stato `ENTRY` o `DEPARTURE` autorizzato dal portale. Il controllo usa sempre la settimana corrente, anche navigando settimane diverse, e viene aggiornato ogni 30 secondi mentre la schermata è aperta e al ritorno nell’app.

Fuori turno il pulsante è disabilitato con l’indicazione della disponibilità al prossimo turno. Dopo una registrazione confermata mostra giorno e ora e rimane bloccato per quel turno, anche riaprendo l’app. Servizi consecutivi senza interruzione sono considerati un solo turno; il blocco non impedisce l’uso al turno successivo. Anche le registrazioni già visibili sul portale nel turno vengono riconosciute.

Gli invii con esito incerto non vengono ripetuti automaticamente: il pulsante rimane bloccato finché una lettura dello storico conferma la registrazione. In quel caso controllare il portale o il responsabile prima di altre operazioni. L’orario del server è usato quando disponibile; in attesa dello storico, “Confermata il” indica l’ora in cui l’app ha ricevuto l’esito positivo. Nessuna retrodatazione o modifica manuale degli orari.

Su Effiplan lo stato deriva dai movimenti personali `USD/mvt` e dal permesso `U` della voce `clock` del menu ufficiale, non dal campo generico `canStamp`. L’invio usa il formato form previsto dal portale, senza motivo aggiuntivo come il pulsante della home; il server decide entrata/uscita. La registrazione resta separata da quella E-Planning. In sviluppo la scrittura reale non viene eseguita come smoke test.

## Aggiornamenti nell'app

Quando è disponibile una versione più recente, un pallino rosso appare sul pulsante menu nell’header e sulla voce **Aggiornamenti…**. La segnalazione resta attiva fino all’installazione, anche se si apre o chiude il menu. La voce mostra versione e un massimo di quattro brevi novità; il pulsante apre nel browser l’APK della Release GitHub rilevata. Se l’app è aggiornata, il popup mostra la versione corrente e lo stesso riepilogo sintetico, senza lo storico delle versioni.

Android può richiedere il permesso **Consenti da questa fonte** per il browser usato per il download, oltre alla conferma dell'installazione. E-PLANNING non scarica né installa APK e non richiede permessi per installare pacchetti. Al successivo avvio vengono mostrate le novità.

Le verifiche automatiche avvengono all'avvio/ritorno nell'app e ogni 5 minuti durante l'utilizzo; è possibile verificarle manualmente da **Menu → Aggiornamenti…**. Il download e l'installazione restano sempre un'azione esplicita dell'utente nel browser.

## Pubblicazione automatica

Ogni push su `main` avvia `.github/workflows/publish.yml`:

1. Esegue test e Android Lint.
2. Genera una versione crescente (`versionCode = max(2000, versionCode locale) + numero esecuzione`, `versionName = 0.5.<versionCode - 2000>`), superiore anche alle build locali di prova. Il valore locale di base deve restare crescente.
3. Compila un APK non debuggabile con la stessa firma degli APK già installati.
4. Pubblica APK e `update.json` in una GitHub Release, resa pubblica solo quando entrambi i file sono pronti.

Aggiornare `RELEASE_NOTES.md` insieme al codice: è mostrato sia prima sia dopo l'installazione. La CI aggiunge i titoli dei commit successivi alla Release precedente. Non scrivere credenziali o dati personali nei commit o nelle note.

Secrets richiesti nelle impostazioni GitHub Actions:

| Secret | Contenuto |
|---|---|
| `PORTAL_URL` | Origine HTTPS del portale di login |
| `API_BASE_URL` | Origine HTTPS delle API |
| `EFFIPLAN_ORIGIN` | Origine HTTPS di Effiplan, senza percorsi (obbligatoria, anche per le build pubblicate) |
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
EFFIPLAN_ORIGIN=https://legacy.example.invalid
```

Per usare la chiave storica locale viene cercato `~/.android/debug.keystore`; si può impostare `ANDROID_KEYSTORE_PATH` e le altre variabili di firma. Non commettere mai chiavi o file di configurazione privati.

```sh
./gradlew :app:lintDebug
./gradlew :app:publishDebugApk
```

L'APK locale resta in `app/build/outputs/apk/debug/`; gli APK precedenti vengono eliminati solo dopo la build riuscita. Dopo l'attivazione della CI **distribuire gli APK delle Releases**, non build locali con versionCode inferiore. Per riprodurre una versione CI impostare `APP_VERSION_CODE` e `APP_VERSION_NAME`.

Test delle regole senza emulatori:

La pubblicazione verifica subito che entrambi i portali siano configurati nei Secrets, senza scrivere le origini nei log. Anche Gradle rifiuta una configurazione Effiplan assente: non può più essere pubblicato un APK con la relativa scelta disabilitata per un Secret mancante.

```sh
mkdir -p .gradle/rule-tests
javac -d .gradle/rule-tests app/src/main/java/it/giovaganzerli/planning/{SwapSearch,UpdateRules,WorkdayRules,PlanningPortal,EffiplanRules}.java tests/{SwapSearchTest,UpdateRulesTest,WorkdayRulesTest,PortalRulesTest}.java
java -cp .gradle/rule-tests it.giovaganzerli.planning.SwapSearchTest
java -cp .gradle/rule-tests it.giovaganzerli.planning.UpdateRulesTest
java -cp .gradle/rule-tests it.giovaganzerli.planning.WorkdayRulesTest
java -cp .gradle/rule-tests it.giovaganzerli.planning.PortalRulesTest
python3 -m unittest discover -s tests -p 'test_*.py'
./gradlew :app:testEffiplanData
```

## Limiti della ricerca cambi

Le abilitazioni sono configurate sul dispositivo, separatamente per utente e squadra. Le proposte sono indicative e non modificano il planning: riposi contrattuali, pause e ulteriori vincoli legali richiedono conferma del responsabile.
