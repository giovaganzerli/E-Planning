# Novità

- E‑Planning · lettura delle squadre tramite l’API della Pianificazione globale, verificata sul portale; disponibile anche Tutto il negozio in vista giornaliera.
- E‑Planning · messaggio esplicito quando il server nega la consultazione di altre squadre per i permessi del profilo, anche con risposta HTTP 400.
- E‑Planning · elenco componenti delle altre squadre letto tramite l’API dedicata, invece del PlanningManager.
- E‑Planning · diagnostica del caricamento squadra senza dati personali o token.
- Squadra · titolo del giorno sempre visibile durante lo scorrimento orizzontale in tutti i raggruppamenti.
- Squadra · intervallo automatico: settimana per le squadre, giorno per popolazioni/negozi Effiplan; assi trasparenti ancorati durante lo scorrimento.
- E‑Planning · consultazione delle altre squadre tramite planning mensile dei componenti, senza endpoint riservato ai manager.
- Squadra · selettore squadra ripristinato su entrambi i portali; barra fissa con etichette e campi compatti.
- Squadra · comandi compatti fissi sopra al planning; la settimana apre con il giorno corrente come primo contenuto visibile.
- Squadra · preferenze di intervallo e raggruppamento persistenti; apertura e cambio intervallo riportano al giorno/settimana corrente.
- Testata · rimosso il sottotitolo provvisorio prima dell’indicazione del portale.
- Testata · nome del portale in corsivo.
- Testata · il sottotitolo indica il portale selezionato.
- Navigazione · tocco su icona o nome dell’app per tornare alla settimana corrente; portale attivo sempre indicato nella testata.
- Scelta portale · URL dei portali mostrati sotto il nome, in piccolo grigio corsivo.
- E‑Planning · i turni già conclusi della settimana corrente restano visibili.
- Accesso · dopo il login ogni pagina HTTPS sul dominio autorizzato acquisisce subito la sessione, anche con atterraggi diversi dalla home.
- Interfaccia · errore di caricamento spostato dall’intestazione a una schermata dedicata con pulsante Riprova.
- Effiplan · richieste di assenza opzionali: un modello che non le espone non blocca più il caricamento del planning.
- Effiplan · richieste di assenza, come INDISPO, mostrate senza conteggio nel monte ore.
- Effiplan · PAV e tutte le altre voci in Assenze compaiono nel planning e le assenze orarie entrano nel monte ore.
- Effiplan · numeri settimana allineati al portale (domenica–sabato, settimana contenente il 1° gennaio = 1).
- Effiplan · corretto il limite finale della query: i turni del sabato rientrano nel totale domenica–sabato.
- Impostazioni · rimossa la barra di navigazione dei periodi su entrambi i portali.
- Account · configurazione separata per utente e portale, anche sullo stesso dispositivo.
- Cambio portale · nessuna nuova configurazione se l’account del portale selezionato è già configurato.
- Aggiornamento · configurazione richiesta al primo accesso anche agli utenti delle versioni precedenti, mantenendo le preferenze compatibili.
- Squadra manuale · verifica dell’utente tra i membri; scelta annullata con errore se assente.
- Novità versione · popup solo dopo accesso e configurazione completati.
- Effiplan · riconoscimento squadra anche senza permesso di accesso al dossier collaboratore.
- Configurazione · selezione manuale della squadra e pagina semplificata, con colleghi solo nel popup dei ruoli.
- Primo accesso · configurazione guidata per ogni account e portale.
- Squadra · appartenenza e colleghi rilevati dal profilo, ruoli configurabili subito.
- Configurazione · portale modificabile e pulsante fisso “Salva e continua →”.
- Google Calendar · icona sempre disponibile in Settimana e configurazione tramite popup.
- E-Planning · selettore Squadra e categorie dei tipi di turno.
- Accesso · rilevamento trasferimenti e nuova conferma dei ruoli, senza riutilizzare quelli della vecchia squadra.

## Correzioni pubblicazione precedenti

- Effiplan · corretta la configurazione della Release per abilitarne la selezione.
- Pubblicazione · bloccate le build con configurazione di un portale mancante.

## Modifiche precedenti

- Effiplan · settimana da domenica a sabato.
- Ore · riepiloghi e calendario allineati alla settimana Effiplan.
- Cambi turno · ricerca entro la stessa settimana domenica–sabato.
- E-Planning · resta invariata la settimana lunedì–domenica.

## Correzioni accesso e squadra precedenti

- Effiplan · ripristinati nomi e collaboratori della squadra.
- Squadra · selettore popolazione visibile solo in questa sezione.
- Accesso · scelta del portale in una pagina dedicata.
- Layout · testata login e contenuti rispettano le barre Android.

## Integrazione portali precedente

- Portale · scelta iniziale tra E-Planning ed Effiplan.
- Popolazione · squadre e negozio da API, con ricerca e selezione.
- Effiplan · nomi e colori dinamici; planning personale e ricerca cambi.
- Timbratura · supporto Effiplan; preferenze separate per account.

## Dettagli integrazione portali

- Effiplan: planning collettivo con tutte le pagine della squadra e stesse regole di ricerca cambi di E-Planning.
- Effiplan: timbratura solo su richiesta dell’utente, con verifica dei permessi e dei movimenti; nessun reinvio automatico.
- Assenze Effiplan usate come vincoli nella ricerca, non importate nel calendario come turni.
- Modelli e dizionari API determinano colori di sfondo e testo dei turni, senza palette o squadre fisse.
- Popolazione predefinita del profilo, altre sezioni, preferiti e tutta la popolazione autorizzata selezionabili nell’app.
- Classificazione dei tipi di turno configurabile nelle Impostazioni, senza cambiare nomi o colori ufficiali.
- E-Planning: funzioni esistenti mantenute.

## Aggiornamenti precedenti

- Aggiornamenti · controllo automatico e pallini di notifica nel menu.
- Novità · riepiloghi brevi e schematici.
- Timbratura · conferma con data e ora, senza doppi invii.
- Calendario · sincronizzazione settimanale con stato salvato.

## Dettagli e versioni precedenti

- Timbratura attiva solo durante il turno corrente e con autorizzazione del portale.
- Conferma con giorno e ora della registrazione e pulsante bloccato per evitare doppi invii nello stesso turno.
- Riconciliazione degli invii incerti senza ripetere automaticamente la timbratura.
- Sincronizzazione calendario dall’icona accanto a Oggi, con conferma e avviso per settimane già sincronizzate.
- Indicatore Calendar persistente per settimana e calendario, con segnalazione dei dati da aggiornare.
- Aggiornamento dati ePlanning spostato nell’header accanto al menu.
- Notifiche calendario personalizzabili: di default 1 giorno e 1 ora prima del turno.
- Titoli degli eventi nel formato “tipo di turno • E-PLANNING”.
- La sincronizzazione aggiorna titoli e notifiche anche degli eventi già importati nella settimana.
- Aggiornamento calendari con richiesta di sincronizzazione a Google e rilevamento automatico delle nuove disponibilità.
- Indicazioni per attivare i calendari mancanti in Google Calendar.
- Novità al primo avvio riassunte in tre punti.
- Selezione calendario: icona di aggiornamento allineata ad Annulla e Conferma nella barra azioni.
- Pulsante Aggiorna nella selezione calendario per rileggere i calendari disponibili sul dispositivo mantenendo la scelta in corso.
- Corretta l’altezza della barra azioni per mostrare interamente i pulsanti dei popup.
- Popup ridisegnati con intestazioni, colori e pulsanti coerenti con l’app.
- Selezione del calendario con nome, account, scelta attuale e conferma esplicita.
- Dettagli giornalieri con schede colorate dei turni.
- Impostazioni disponibili dal menu in alto, con la nuova sezione “Il mio team”.
- Nuova schermata Impostazioni per configurare Google Calendar e la squadra per la ricerca cambi.
- Il collegamento dalla ricerca cambi rimanda alla configurazione squadra nelle Impostazioni.
- Nuovo menu in testata: aggiornamenti, impostazioni e logout in un’unica voce.
- Configurazione del calendario Google spostata nelle Impostazioni.
- Sincronizzazione dei turni settimanali con un calendario Google selezionato sul dispositivo.
- Selezione e modifica del calendario direttamente dalla schermata Settimana.
- Aggiornamenti disponibili segnalati direttamente nell'app.
- Versione e note di rilascio consultabili prima del download.
- Il pulsante aggiornamento apre direttamente nel browser l'APK della Release GitHub.
- Download e installazione sono gestiti e confermati dall'utente nel browser e da Android.
- Rimossi download e installazione interna all'app.
- Riepilogo delle novità al primo avvio della nuova versione.
- Miglioramenti al planning e ricerca dei cambi turno.
