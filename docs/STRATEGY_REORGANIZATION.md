# Riorganizzazione strategie e PAPER TRADING

## Stato effettivo

Implementate la separazione delle strategie, le nuove pagine, i namespace persistenti, il router PAPER, il ledger separato V2, il monitoraggio e gli export. La validazione automatica usa mock, database H2 in memoria e snapshot storici offline.

**PAPER_AUTO simultaneo di produzione non è certificato ed è disabilitato dal backend.** Non basta la presenza dei pulsanti AVVIA V1 / AVVIA V2 per considerare conclusa l'abilitazione operativa sul broker. Anche l'avvio singolo resta subordinato ai prerequisiti del broker già esistente, che attualmente restituisce identità PAPER e scrittura non verificate. Questi blocchi non sono stati rimossi o simulati come superati.

Nessun servizio operativo è stato avviato/riavviato, nessuna attivazione PAPER_AUTO è stata effettuata e nessun ordine è stato trasmesso a IBKR durante il lavoro.

## Strategie e routing

| Pagina | Ruolo | API | Persistenza |
|---|---|---|---|
| `/pattern-v1.html` | PATTERN V1 — STABLE / FROZEN / BACKTEST, versione **2.1** | `/api/backtest/pattern-v1` invariata | `data/pattern-v1-stable`, storici legacy invariati |
| `/pattern-v2.html` | PATTERN V2 — STABLE / FROZEN / BACKTEST, versione **1.0** | `/api/backtest/pattern-v2-stable` | `data/pattern-v2-stable` append-only |
| `/pattern-v3.html` | PATTERN V3 — RESEARCH / EXPERIMENTAL | `/api/backtest/pattern-v3` | `data/pattern-v3` |
| `/paper-trading.html` | Unico punto UI per controllo automatico V1/V2 | `/api/paper-trading` | Ledger originale V1 + ledger V2 + metadati separati |

PAPER TRADING è la prima voce dei menu. Le altre sezioni restano disponibili. Il tab e lo script operativo PAPER sono stati rimossi dalla pagina V1; la logica backend V1, il suo store, lo scheduler e gli endpoint legacy restano disponibili per compatibilità. V2 non ha tab operativo PAPER.

Le API Research V2 preesistenti `/api/backtest/pattern-v2` restano compatibili per lo storico e i client esistenti; non sono l'autorità della nuova V2 stabile. La nuova pagina V2 mostra lo storico Research separatamente, con la sua identità originale. Le nuove run V3 non rinominano né copiano le vecchie run V2.

## Congelamento V2

La risorsa `src/main/resources/pattern-v2-stable/1.0.json` contiene la configurazione effettiva della run `a856a14e-4046-4fdb-9940-e84fc4c967a6`, non i suoi risultati hardcodati. Il dataset è verificato tramite checksum. La configurazione ha capitale 10.000, rischio 2%, ordine al 30% dell'equity, limiti dinamici al 100%, leva OFF, 13 barre, STOP_FIRST e ADX14/25 EXCLUDE_WITH_TREND ON. Range, Close Strength e Bollinger/ATR sono OFF. Simboli, pattern, Target R, stop, costi, slippage e split sono quelli del riferimento.

`FrozenPatternV2Service` verifica l'hash contro il riferimento prima del backtest e salva versioni/run in un archivio append-only nuovo. La pagina non accetta override. Un POST al nuovo endpoint con parametri viene rifiutato; la ricerca è in V3. Le nuove risposte includono identità, versione, hash e snapshot; i trade e l'Excel espongono la provenienza della run.

La regressione offline confronta l'intero portafoglio con il riferimento dopo la sola normalizzazione dell'UUID della run. Verifica inoltre byte per byte che il riferimento, current V2 e la risorsa congelata V1 non cambino. L'Excel risultante è in `outputs/strategy-reorganization-evidence/PATTERN_V2_STABLE_1_0.xlsx`.

## Ricerca V3

V3 è uno snapshot del motore, delle analisi e del frontend Research V2 nel proprio package. È separata nei servizi, store, configurazioni, risultati e asset. Le formule iniziali sono copiate senza ottimizzazione; i detector e i contratti stateless comuni restano condivisi, come prima.

Lo snapshot iniziale `pattern-research/v3-initial.json` conserva le impostazioni V2 presenti al momento della separazione, inclusi ADX ON, periodo 14 e soglia 25. Non è una configurazione collegata dinamicamente alla V2. Dopo l'inizializzazione è modificabile solo nel namespace V3. Il comando LOAD V1 BASELINE continua a caricare una copia della baseline V1; RESET V3 torna al suo snapshot iniziale.

Sono mantenuti simboli/pattern/direzioni/Target R, stop basato sugli estremi con buffer configurabile, holding, sizing, money management e costi, filtri Range/Close Strength/Bollinger-ATR/Market Regime, analisi ADX/DI/EMA, capital shadow, comparazioni, replay, Run History ed Excel. Non sono state inventate nuove formule di stop o nuove modalità ADX. Le nuove run dichiarano `PATTERN_V3`, versione `3.0-research`, hash e snapshot. Non esiste una promozione automatica verso V1/V2.

## Migrazione dei dati

Non sono state rinominate/cancellate tabelle, run storiche, ordini, posizioni o configurazioni PAPER. Nessun contenuto preesistente viene convertito in V3.

Il modello JPA PAPER continua a usare la tabella **`pattern_v1_paper_record`**:

| Record / kind | Significato |
|---|---|
| `SESSION`, `EVENT`, `ALERT` | V1 originali, conservati |
| `V2_SESSION`, `V2_EVENT`, `V2_ALERT` | V2 separati, creati se assenti |
| `MULTI_STATE` | ID sessione PAPER comune, instance ID distinti, kill globale persistente |
| `MULTI_EVENT` | Audit a livello di conto |

Ogni ordine appartiene al ledger persistente della propria strategia. Versione/hash/snapshot sono nella sessione; `strategyInstanceId` è persistito in MULTI_STATE; `strategyPositionId` è la composizione stabile instance ID + signal ID. Questi identificativi sono esportati insieme a orderId, permId, executionId, account e commissioni. I vecchi segnali/ordini V1 conservano i loro identificativi; non vengono rigenerati per adeguarli al nuovo schema.

Non serve una migrazione distruttiva DDL. I nuovi record vengono inizializzati dal nuovo backend; V3 crea i propri current/default al primo utilizzo. Le versioni V2 stabili vengono archiviate alla prima run. La vecchia SESSION non viene riscritta da uno script di migrazione; il normale recovery V1 resta quello esistente.

## Broker, ledger e sicurezza

`MultiStrategyPaperRouter` è l'unico consumer di produzione delle code del broker condiviso. Fornisce due viste, assegnate a V1 e V2. Non apre due connessioni IBKR.

- Callback di ordini/esecuzioni/commissioni instradate tramite ordini già persistiti; nessuna attribuzione basata solo sul simbolo.
- Ledger V1 e V2 distinti; contabilità V2 usa le stesse primitive stateless di contabilizzazione delle esecuzioni effettive. Nessun PnL dell'altra strategia entra nell'equity virtuale.
- Prima della vista per strategia, confronto tra quantità aggregate del broker e somma delle quantità dei ledger, includendo esecuzioni appena riconciliate. Le quantità attribuite non vengono presentate come due posizioni fisiche distinte su IBKR.
- Prefissi client order/OCA V1 `PV1`, V2 `PV2`, con signal ID diversi anche per segnali identici. Le protezioni restano legate alla quantità della relativa posizione.
- Rifiuto di uscite oltre la quantità effettivamente attribuita o di ordini appartenenti all'altra strategia.
- Rifiuto dei conflitti LONG/SHORT, anche tramite ordini ENTRY pendenti, con `MULTI_STRATEGY_POSITION_CONFLICT`. Nessun netting automatico.
- Controllo conservativo dei fondi effettivi del conto e delle prenotazioni pendenti di entrambe le strategie. Due capitali virtuali da 10.000 non equivalgono a 20.000 di fondi broker.
- Errori broker/account e riconciliazioni incoerenti bloccano i nuovi ingressi; il blocco globale è persistente e richiede reset esplicito.
- Stop e kill non liquidano automaticamente posizioni. Le cancellazioni riguardano solo aperture/residui verificati; protezioni e timeout restano sotto la rispettiva orchestrazione.
- Recovery conserva outbox/ordini/esecuzioni e sospende gli ingressi in attesa di riconciliazione. Nessun retry cieco di invii incerti.

V1 continua a usare il suo servizio e scheduler originali; V2 ha servizio, planner ADX, shadow, store e scheduler separati. Le operazioni sul broker passano dal router serializzato. La certificazione mock del router può essere attivata solo costruendo l'oggetto nei test; **la configurazione Spring di produzione passa `false`** e non espone un endpoint o un flag UI per aggirarlo.

L'autorizzazione preesistente è riutilizzata anche su `/api/paper-trading`: sessione amministrativa, cookie HttpOnly/SameSite, CSRF, controllo Origin e HTTPS per accessi remoti. Nessun token viene salvato nel browser. V3 viene rifiutata dai comandi e dal router PAPER.

## Dashboard e confronto

Le due schede mostrano stato backend, versione/hash/snapshot congelato, ADX OFF/ON, capitale/equity/PnL/drawdown, connessione, posizioni, ultima esecuzione e controlli di avvio. Stop, kill, reset, SHADOW e limiti operativi sono separati. Il kill globale è distinto.

COMPARISON usa solo osservazioni del ledger PAPER, mai i trade shadow. Richiede attivazione PAPER effettiva, data di attivazione compatibile, capitali iniziali uguali e sovrapposizione di dati effettivi. Le curve mantengono i timestamp disponibili: nessuna data inventata o ribasamento artificiale delle equity. In assenza di dati confrontabili mostra N/D e il motivo. Il periodo può essere filtrato; il capitale storico non viene resettato per rendere comparabili le strategie.

Le commissioni mancanti sono segnalate e il PnL resta provvisorio. Lo slippage misurabile è quello degli ingressi rispetto all'OPEN atteso registrato: non viene dichiarato uno slippage totale di uscita quando il benchmark operativo non è disponibile. Non viene sottratto nuovamente lo slippage teorico del backtest dai prezzi reali PAPER.

Le viste ORDERS, EXECUTIONS, POSITIONS, ACCOUNT RECONCILIATION, SIGNALS, SHADOW e AUDIT mantengono identità e modalità. Lo stato corrente di ordini e conto è distinto dalle osservazioni storiche filtrate.

## Export

Gli export V1 e Research V2 esistenti rimangono; V2 stabile aggiunge STRATEGY_VERSION e provenienza dei trade. V3 conserva i fogli analitici Research.

Il nuovo export `/api/paper-trading/export/excel?strategy=PATTERN_V1&from=...&to=...` usa sessione amministrativa e comprende:

```text
PAPER_CONFIGURATION
PAPER_STRATEGIES
PAPER_SIGNALS
PAPER_ORDERS
PAPER_EXECUTIONS
PAPER_POSITIONS
PAPER_CLOSED_TRADES
PAPER_EQUITY_CURVES
PAPER_COMPARISON
PAPER_ERRORS
PAPER_ACCOUNT_RECONCILIATION
```

Gli eventi sono filtrati per strategia e intervallo. Le posizioni a una data di fine sono ricostruite dai fill: se manca una quotazione storica non vengono inventati mark, unrealized PnL o holding bars. Lo stato ordine corrente è etichettato come tale, con selezione per data di invio. La riconciliazione del conto è sempre l'ultimo snapshot effettivo, indicato chiaramente. Le commissioni non ancora conosciute non diventano zero. I risultati SHADOW non entrano nei fogli PAPER; gli export legacy SHADOW restano disponibili.

## Prerequisiti operativi non completati

I seguenti vincoli sono reali e impediscono di dichiarare pronta l'esecuzione automatica sul broker:

1. Il broker esistente non fornisce ancora una prova indipendente e affidabile dell'identità PAPER legata alla sessione né una verifica dei permessi di scrittura.
2. Il quote adapter non fornisce ancora un OPEN qualificato per sessione: restituisce OPEN mancante e impedisce l'ingresso.
3. Prestito/margine SHORT e recovery bracket/TIMEOUT/OCA con partial fills e riconnessioni richiedono validazione PAPER esplicita.
4. L'isolamento di due strategie su uno stesso conto è verificato con mock, non certificato con esecuzioni IBKR. L'avvio simultaneo resta bloccato sia prima di Start sia prima di trasmettere ENTRY.
5. Prima dell'uso operativo V2 va verificata anche la completezza/coerenza della base prezzi della storia DAILY usata per ADX, in particolare dopo nuove corporate action.
6. `AvailableFunds` può riflettere margine disponibile: da solo non dimostra cassa senza leva o un limite sull'esposizione lorda rispetto alla Net Liquidation. Il budget account cash/net-liquidation non è ancora certificato. Il router di produzione aggiunge `ACCOUNT_CASH_BUDGET_UNVERIFIED` e blocca anche ingressi singoli finché questo controllo non è completato, lasciando distinti i percorsi protettivi delle posizioni esistenti.

Non è disponibile una procedura sicura per avviare **entrambe** in PAPER_AUTO in questo stato. Non rimuovere le limitazioni o forzare i flag a true. Le prove di trasmissione non fanno parte di questa implementazione e richiedono autorizzazione e attivazione PAPER esplicite separate.

## Accesso locale senza chiave

Aprire `http://localhost:8081/paper-trading.html` dal PC del backend: la dashboard si apre senza compilare il campo amministrativo e senza configurare `PATTERN_PAPER_ADMIN_TOKEN`. Anche gli indirizzi loopback `127.0.0.1` e `[::1]` sono supportati. Non occorre modificare il file `.env` per questo accesso.

La pagina legge `GET /api/paper-trading/access` (campo aggiuntivo `localAccess`) e, soltanto per un accesso locale, richiede `POST /api/paper-trading/local-session` con header `X-Paper-Local-Session: 1`. Il backend verifica indirizzo del client, hostname loopback e Origin; le richieste con header Forwarded/X-Forwarded-For/Host/Proto non usufruiscono dell'accesso senza chiave. La sessione locale non e utilizzabile da un client remoto. Nessuna sessione viene creata dalla semplice richiesta GET di discovery.

I comandi mantengono sessione e header CSRF. Aprire la dashboard crea solo la sessione e legge lo stato: non avvia strategie, connessioni o ordini. Il pulsante **Chiudi dashboard** termina la sessione senza fermare le strategie; per riaprire o dopo una scadenza, ricaricare la pagina. Altre schede locali riutilizzano la stessa sessione.

Gli accessi remoti restano protetti dal login con chiave di almeno 32 caratteri e da HTTPS. I blocchi PAPER_AUTO, il divieto LIVE, i kill switch e le conferme operative restano invariati.

## Deploy, backup e ripristino

1. Se ci sono sessioni/ordini esistenti: fermare solo i nuovi ingressi, verificare gli stop/target sul broker e riconciliare eseguiti e quantità. Non cancellare protezioni per distribuire l'applicazione.
2. Concordare una finestra di manutenzione. Arrestare ordinatamente il servizio prima del backup dei file H2; una semplice copia a caldo del database non è un backup consistente.
3. Salvare integralmente database, `data/`, configurazioni operative e versione dell'applicazione. Le credenziali restano protette fuori dal report.
4. Distribuire il JAR nuovo e avviare inizialmente con trasmissione automatica disabilitata. Controllare entrambe le pagine backtest e PAPER TRADING. I nuovi namespace si inizializzano senza cancellare SESSION V1.
5. Riconciliare il conto prima di qualsiasi attivazione. I blocchi di produzione sopra elencati rimangono finché non è completata la relativa validazione.

Per controllare l'interfaccia senza collegarsi o trasmettere al broker si può avviare l'app in `BACKTEST` con `--trading.ibkr.auto-connect=false --trading.order-submission-enabled=false --trading.strategy.auto-execute-signals=false`. Questa modalità non abilita il PAPER_AUTO.

Rollback: il vecchio codice non comprende la proprietà delle posizioni V2. Non tornare al vecchio PAPER V1 mentre esistono esposizioni/ordini V2. Non ripristinare mai un database precedente sopra eseguiti successivi: si perderebbe contabilità reale. Conservare il database corrente e riconciliare; il backup è un punto di ripristino per corruzione, non un modo per annullare trade. Il rollback applicativo va eseguito a ingressi sospesi e dopo verifica di ledger/protezioni.

## Verifica riproducibile

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.11'
mvn verify
npm.cmd test
npm.cmd run build
mvn -Dtest=FrozenV2RegressionTest -DreorganizationRegression=true test
```

I test mock coprono quantità 10+8 sullo stesso titolo, uscita del solo proprietario, partial fills, duplicati, restart senza reinvii, conflitti opposti, fondi effettivi aggregati, disconnessione, LIVE rifiutato e V3 proibita. I test HTTP coprono autenticazione/CSRF, nuovi store, route V2/V3, configurazioni indipendenti ed export. Le regressioni V1/PAPER esistenti continuano a passare. Il frontend verifica separazione delle pagine, menu, comandi distinti, conferme kill e assenza di Start/Connect al caricamento o login.

Risultati finali ed elenco file: `outputs/strategy-reorganization-evidence/REPORT.md`.
