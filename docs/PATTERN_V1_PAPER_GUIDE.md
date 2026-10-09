# PATTERN_V1 PAPER: stato, avvio e limiti

## Stato della consegna

Sono implementati il pannello web, le API amministrative, la persistenza, la macchina a stati, lo scheduler basato sulle sessioni di borsa, il dimensionamento congelato, l'orchestrazione degli ordini, la riconciliazione, il confronto SHADOW e gli export. L'automazione è verificata con broker simulati.

**PAPER_AUTO non è ancora attivabile sul broker reale.** L'adapter di produzione nega ogni trasmissione e cancellazione perché manca una prova indipendente dell'identità PAPER legata alla connessione. Non esiste un interruttore web che aggiri questo controllo. Non considerare completata la messa in servizio del trading automatico.

Altri blocchi espliciti: permesso API di scrittura non verificato; OPEN realtime identificato per sessione non disponibile; prestito e margine SHORT non attestati; bracket, quantità dei partial fill, ripristino protezioni e TIMEOUT/MOC/OCA non validati su un ambiente PAPER reale. Sono visualizzati nella dashboard. Serve una successiva integrazione e validazione specificamente autorizzata, senza usare porta, prefisso DU o una dichiarazione manuale come prova dell'identità del conto.

Non sono stati effettuati collegamenti IBKR né inviati ordini PAPER o LIVE durante questo sviluppo. La sola esecuzione dei test non abilita alcuna trasmissione.

## Configurazione preservata

La versione ufficiale utilizzata è PATTERN_V1_STABLE **2.1**, con timeout **13 barre**, già congelata prima del lavoro PAPER. La barra d'ingresso conta come barra 1. La versione 2.0 resta conservata con timeout 20. Nessuna delle due configurazioni viene modificata dal pannello PAPER.

Il backend salva versione, hash e snapshot completo. Capitale virtuale 10.000, ordine massimo 30% dell'equity, rischio 2%, limiti posizione/LONG/SHORT 100% dell'equity, leva disattivata. DAILY, NEXT_BAR_OPEN, PATTERN_EXTREME, buffer, simboli, pattern, direzioni, Target R, commissioni e slippage provengono dallo snapshot congelato. STOP_FIRST è applicato solamente alla simulazione SHADOW; PAPER usa le esecuzioni reali comunicate dal broker.

Gli ulteriori limiti operativi sono inizialmente non configurati: Daily Loss, Drawdown, numero posizioni, ordini giornalieri ed esposizione aggregata richiedono valori espliciti nella UI. L'esposizione aggregata non può superare il 100% senza leva. Questi limiti non riscrivono il backtest e ogni modifica è tracciata.

## Preparazione di IB Gateway e dell'applicazione

1. Accedere manualmente a IB Gateway scegliendo il conto simulato. L'applicazione non esegue login, secondo fattore o aggiramenti dell'autenticazione.
2. Abilitare la socket API, porta PAPER 4002, indirizzo attendibile 127.0.0.1 e mantenere **Read-Only API** attivo per le verifiche iniziali. Nessun fallback sulla porta LIVE.
3. Configurare una volta le variabili dell'applicazione, tramite ambiente o configurazione di esecuzione IntelliJ. Non occorre modificare `application.yml` per avvio/arresto/limiti dal pannello:

```text
TRADING_MODE=PAPER
LIVE_TRADING_ENABLED=false
IBKR_HOST=127.0.0.1
IBKR_PORT=4002
IBKR_PAPER_ACCOUNT_ID=<ID esatto del proprio conto PAPER>
IBKR_AUTO_CONNECT=false
IBKR_PAPER_ORDERS_ENABLED=false
AUTO_EXECUTE_SIGNALS=false
PATTERN_PAPER_ADMIN_TOKEN=<segreto casuale di almeno 32 caratteri>
```

Il token amministrativo è un segreto applicativo, non una credenziale IBKR. Non inserirlo nel repository o nelle URL. Non riutilizzare credenziali del broker. L'account è deciso nel backend e non selezionabile dal browser. La modalità dati realtime IBKR è denominata `LIVE` nelle configurazioni dei dati di mercato; non è la modalità di trading LIVE. Per osservazioni iniziali sono ammessi dati ritardati, che bloccano comunque PAPER_AUTO.

4. Usare Java 21 ed eseguire `mvn spring-boot:run`, oppure avviare `TradingApplication` da IntelliJ. Per costruire l'artefatto: `mvn verify`; eseguire poi il JAR Spring Boot prodotto in `target`.
5. Aprire `http://localhost:8080/pattern-v1.html`, selezionare **PAPER TRADING**, accedere come amministratore e premere **VERIFICA CONNESSIONE IBKR**. Il comando iniziale è in sola lettura. **AGGIORNA STATO** riconcilia e legge; non invia ordini.

L'accesso remoto ai comandi amministrativi richiede HTTPS. Cookie HttpOnly/SameSite, sessione amministrativa, controllo origine e CSRF proteggono i comandi. Il token non viene salvato nel browser. Alla scadenza della sessione occorre autenticarsi nuovamente; il logout non arresta il backend.

## Comandi e comportamento

- **AVVIA PAPER AUTO**: backend verifica conto, connessione, configurazione, calendario, dati, limiti, riconciliazione e kill switch. Attualmente mostra i blocchi di produzione; non invia ordini.
- **FERMA NUOVI INGRESSI**: inibisce immediatamente nuove aperture, passa a STOPPING, mantiene riconciliazione e gestione delle posizioni. Non disconnette e non liquida.
- **EMERGENZA / KILL SWITCH**: richiede la frase `ATTIVA KILL SWITCH`; blocco persistente, annulla solo residui di ordini ENTRY identificati e verificati, preserva stop/target. Un parent parzialmente eseguito può essere cancellato solo dopo verifica delle protezioni. In produzione la mancanza di prova PAPER nega anche cancellazioni.
- **RESET KILL SWITCH**: richiede amministratore e frase `RESET KILL SWITCH`; il reset non avvia automaticamente PAPER_AUTO.
- **SHADOW**: simula prospettivamente i segnali sulle barre complete con equity e risultati separati. Il confronto si basa sul medesimo Signal ID e periodo; non abbina trade storici estranei.
- **EXPORT EXCEL**: configurazione, conto, segnali, ordini, esecuzioni, commissioni, posizioni, trade chiusi, equity, performance giornaliera, errori, controlli, eventi e confronto PAPER/SHADOW.

## Persistenza e recupero

La nuova tabella JPA `pattern_v1_paper_record` usa il database già configurato nel progetto. Sessione, configurazione, calendari, segnali, intenti, esecuzioni, commissioni anche anticipate rispetto al fill, equity, comandi idempotenti ed eventi sopravvivono al riavvio. Le scritture della sessione usano lock pessimista nel database e versione ottimistica. Nessuna migrazione distruttiva o rinomina di run storiche.

Gli intenti vengono registrati prima dell'invio. Un esito incerto viene segnalato e non ritrasmesso automaticamente. Un riavvio sospende PAPER_AUTO; nessun ingresso riprende prima di verifica dell'account, ordini aperti/completati, esecuzioni, posizioni e protezioni. Quando il ripristino automatico è stato richiesto dalla sessione precedente, la ripresa resta subordinata a tutti i controlli. STOPPING e KILL_SWITCH non vengono convertiti automaticamente in PAPER_AUTO.

Con PC spento o Internet assente il processo Java non può analizzare segnali, riparare protezioni o inviare TIMEOUT. Solo gli ordini già accettati e mantenuti dal broker possono continuare a operare secondo le loro caratteristiche. Il comportamento nativo delle protezioni deve ancora essere validato: non viene promessa protezione server-side per ogni tipo d'ordine. Un TIMEOUT mancato non viene mascherato da un prezzo Close teorico: genera anomalia, preserva le protezioni e richiede riconciliazione/intervento. La riconnessione usa il servizio esistente; l'autenticazione manuale a Gateway può essere nuovamente necessaria. Non è stato installato un servizio Windows per riavviare il PC o l'applicazione.

Commissioni e PnL PAPER derivano dai callback effettivi, non da valori di backtest. Per commissioni non USD manca una conversione valutaria attestata: il sistema segnala il problema. Non utilizzare il saldo totale IBKR come equity della strategia. Posizioni esterne sui simboli congelati bloccano una riconciliazione ambigua; è opportuno un ambiente PAPER dedicato.

## Verifiche e file

I test Java coprono identità LIVE/ignota, API iniziale read-only, comandi autenticati/CSRF, stati persistenti, calendario/festivi/DST, barre complete, idempotenza, sizing, LONG/SHORT, ledger e commissioni, partial fill/protezioni, timeout 13, restart/disconnessione, rifiuti, capitale separato, SHADOW, dashboard ed Excel. Sono test con broker simulati, non una certificazione dell'esecuzione IBKR reale.

Le prove sono in `outputs/pattern-paper-evidence/`: log Maven e frontend, manifest dei file e confronto SHA-256. I 127 file V2 e snapshot/archivi V1 protetti dal baseline sono invariati. La verifica visuale con Browser non è stata possibile perché la sessione non dispone di un Browser; sono stati verificati HTTP, markup, controlli e comportamento frontend.

Componenti nuovi: `paper/patternv1` contiene adapter broker, calendario, planner, ledger, SHADOW, orchestrazione, scheduler, persistenza, autenticazione, API e Excel. I callback aggiunti a `IbkrEventListener`, `IbkrEWrapper` e le richieste aggiunte a `IbkrApiClient` sono riutilizzati senza cambiare le altre strategie. La lista esatta dei file della consegna è in `outputs/pattern-paper-evidence/changed-files.txt`; include esclusivamente questo lavoro, non gli altri file già presenti e non tracciati nel workspace.
