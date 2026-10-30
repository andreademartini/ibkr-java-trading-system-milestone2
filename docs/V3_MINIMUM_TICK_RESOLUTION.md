# V3: risoluzione tick minima offline

Aggiornamento 3.2: disponibile una [opzione research esplicita](V3_RESEARCH_DATASET_GRID.md) basata sulla precisione CSV, distinta dai tick di mercato verificati. FAIL_FAST resta il default.

## Causa verificata

La V3 dichiarava minimumTickSizes con default Map.of(), normalizzava null a mappa vuota e calcolava il trigger soltanto consultando quella mappa. Il servizio CSV non caricava alcuna fonte alternativa. JSON, salvataggio e caricamento della run conservavano correttamente il valore: non era un bug di serializzazione.

Il dataset usa nomi come AAPL.US.csv, ma il parser riceve il simbolo AAPL. HistoricalBar non contiene exchange, valuta, tipo strumento o market rules. Non si può ricavare un'identità completa dal suffisso del filename. Il resolver ora gestisce alias dichiarati, senza equiparare automaticamente strumenti diversi.

Nel progetto esistono requestContractDetails e callback IBKR, ma non un archivio storico di griglie prezzi/market rules né un resolver offline dei tick. I minimumTick delle configurazioni Joe Ross e delle entità operative hanno default 0.01 non certificati per questo dataset: non vengono riutilizzati come metadati affidabili.

WeeklyTteCsvData carica Stooq con PriceAdjustment.ADJUSTED. L'enum distingue soltanto RAW/ADJUSTED: non certifica se gli OHLC siano rettificati per split o dividendi e non fornisce fattori data per data. PatternV2SplitCosts è un catalogo per commissioni/quantità: non basta per attestare il dominio di tutti i prezzi Stooq.

## Implementazione

Nuovo marketdata/ticks/MinimumTickResolver: puro componente offline, costruito per lo snapshot della run; non dipende da IBKR.

Catalog contiene versione, identità strumenti, alias espliciti, regole per data e fasce di prezzo, provenienza e basi prezzi legate al fingerprint del dataset. Priorità:

1. DATASET_METADATA validati.
2. INSTRUMENT_MASTER validato.
3. SAVED_MARKET_RULE acquisita e salvata.
4. Override esplicito minimumTickSizes, con identità verificata e dominio dichiarato.
5. Errore se non esiste una risoluzione affidabile.

Due regole attive della medesima priorità producono un errore di conflitto. Exchange, currency e instrumentType devono corrispondere. Nessun collegamento alla rete o al broker è richiesto durante il backtest.

TrendReboundV3Service può leggere il catalogo locale data/instrument-master/minimum-tick-rules.json quando la configurazione non contiene già uno snapshot. Il contenuto viene congelato in minimumTickMetadata dentro la configurazione salvata della run. Il replay usa tale contenuto, non il catalogo corrente. È possibile salvare la stessa struttura tramite la normale configurazione V3.

minimumTickSizes resta una mappa di override opzionali: può correttamente essere vuota se i metadati sono completi. I valori sono BigDecimal, le chiavi sono normalizzate e i valori non positivi sono rifiutati. minimumTickOverrideDomain deve essere RAW oppure DATASET quando si usa un override; UNSPECIFIED non autorizza un'interpretazione implicita.

## Prezzi e griglia

Il trigger è il primo prezzo di griglia strettamente superiore al massimo; buffer N percorre N successori validi. Un massimo 102.005 su griglia 0.01 produce 102.01, non 102.015. Il passaggio a una fascia successiva usa la griglia applicabile al prezzo finale. Tutte le operazioni di griglia usano BigDecimal, incluso il confronto tra HIGH della quarta barra e trigger.

Per regole RAW e OHLC adjusted serve PriceBasis validata, legata al dataset, simbolo e intervallo storico. rawPerDatasetPrice indica il fattore: prezzo raw = prezzo dataset × fattore; il trigger viene calcolato in raw e riportato esattamente nel dominio dataset. I fattori possono rappresentare split o rettifiche totali, ma devono avere provenienza esplicita. Non vengono stimati dagli OHLC o dal catalogo commissionale.

Una conversione che non ammette rappresentazione BigDecimal esatta viene rifiutata con NON_TERMINATING_ADJUSTED_GRID. Una griglia già certificata dal provider nel dominio DATASET può essere usata direttamente, sempre con fingerprint e base dichiarata. Non è una garanzia di eseguibilità intraday né una ricostruzione automatica dei prezzi raw.

I limiti di validità sono DAILY; regole o rettifiche intraday non sono modellate. Il calendario applicato è quello già usato dalla V3. La data di risoluzione del BUY STOP è la quarta sessione.

I decimali della configurazione vengono serializzati come stringhe esatte nei metadati e negli override, per impedire che il passaggio dal browser li arrotondi a JavaScript Number. In Java restano BigDecimal. I tick risolti esportati nelle colonne di analisi Excel restano numerici. La griglia di ogni fascia è ancorata al suo lowEdge: il catalogo deve certificare anche questa convenzione prima dell'uso.

## Prevalidazione e UI

minimumTickResolutionPolicy=FAIL_FAST è il default e l'unica modalità operativa attuale. Prima di generare segnali vengono verificati gli strumenti effettivamente disponibili dopo le esclusioni dati esistenti, lungo la copertura e le fasce effettivamente incontrate. I simboli esclusi dalla preesistente politica dati sono indicati separatamente, senza cambiare tale politica.

Le API V3 espongono POST /minimum-ticks/prevalidate e /minimum-ticks/export/excel. La pagina mostra policy, buffer, risultati per simbolo e una sezione avanzata separata per override e snapshot. Prima di POST /run il frontend esegue la prevalidazione; il motore ripete il controllo per impedire che un chiamante API lo aggiri.

Nessuna modifica a open < previous high, validità della sola quarta barra, slippage, stop, ATR, RECOVERY, timeout, sizing, commissioni o politica conservativa intrabar.

## Excel

CONFIGURATION include policy, buffer, versione, hash delle regole e convenzione prezzi. MINIMUM_TICK_AUDIT distingue regole controllate in preflight e realmente usate per la decisione d'ingresso. ENTRY_SIGNAL_AUDIT e TRADES includono tick numerico, fonte, buffer e trigger, mantenendo le analisi ATR/profit space precedenti.

Un fallimento FAIL_FAST produce soltanto un workbook diagnostico chiaramente marcato BacktestExecuted=false: nessuna equity simulata o performance inventata. PORTFOLIO_SUMMARY contiene le 40 metriche come N/A; TRADES è vuoto. Tutti i fogli di analisi sono conservati.

## Verifica locale della stessa run

Configurazione letta da data/trend-pullback-rebound-v3-v1/current.json: 30 simboli, periodo richiesto 2017-01-01–2026-10-29, capitale 10000, rischio 0.5%, stop 1.5 ATR, SMA200/SMA5, timeout 5, slippage 0.03%, medesimo modello commissionale.

La run precedente reale 6e20ecf7-97d2-4d25-993e-1193bad3088d contiene 3979 segnali, 3476 MINIMUM_TICK_UNAVAILABLE, 502 open non validi, 1 MISSING_NEXT_OPEN e 0 trade.

Il fingerprint del dataset attuale coincide con quello della run precedente:

83cf602f7cdbbd4d70cdcd962369089c19c2f1d6992c4dec3724d13a28cb682c

La nuova prevalidazione restituisce 28 errori di metadati e 2 esclusioni preesistenti (BA/BAC, dati duplicati). Per AAPL, AMD, AMZN, AVGO, CAT, CVX, DIA, DIS, GOOGL, GS, IWM, JPM, KO, META, MSFT, NFLX, NKE, NVDA, QQQ, SMH, SPY, TSLA, XLE, XLF, XLI, XLK, XLY, XOM la fonte è NON DISPONIBILE, non un tick assunto.

Pertanto il nuovo backtest di mercato viene interrotto prima dei segnali. Nuovi conteggi di open non validi, trigger non raggiunti, CAGR, Win Rate, Profit Factor e drawdown sono N/A. Nessuna nuova operazione è stata eseguita. I 28 errori non sono 3476 segnali rifiutati: sono errori preliminari per strumento.

Output:

- outputs/trend-pullback-rebound-v3-tick-resolution/V3_MINIMUM_TICK_PREFLIGHT_BLOCKED.xlsx
- outputs/trend-pullback-rebound-v3-tick-resolution/tick-resolution-summary.json

Per completare una nuova simulazione reale occorrono identità e regole storiche verificate, più fattori/base prezzi compatibili con questo fingerprint. Non è stato creato un catalogo fittizio per ottenere trade positivi.

## File interessati

Creati: MinimumTickResolver.java, V3TickSupport.java, MinimumTickResolverTest.java e trend-pullback-rebound-v3-ticks.test.js.

Modificati esclusivamente nella V3: Configuration, Service, Engine, Controller, ExcelExporter, V3Diagnostics, HTML/JS, TrendReboundV3EngineTest e V3LocalReportTest. Documentazione: questo file.

Le classi V1, V2, PATTERN, Weekly, PAPER e la gestione ordini IBKR non sono state modificate da questo intervento.

## Test eseguiti

- Suite Maven completa: 836 test, 814 passati, 21 saltati, 1 fallimento, 0 errori (tick-all-tests.log).
- Unico fallimento: ResearchFiltersTest.validationAndCopiesPreserveExplicitBoundsAndLegacyConfigurationHashes, già presente prima dell'intervento. Legge la configurazione locale PATTERN V3 e presume closeStrengthMin=null, mentre trova 0.906. Il test e la configurazione non sono stati alterati.
- Verifica finale mirata dopo gli ultimi controlli di precisione: 25/25 passati, ossia 10 MinimumTickResolverTest, 14 TrendReboundV3EngineTest e 1 V3LocalReportTest (tick-final-targeted.log).
- Inclusa una simulazione sintetica con metadati validati, override vuoti e massimo fuori griglia: ingresso 108.01, sorgente DATASET_METADATA, riconciliazione superata e tick numerici verificati in TRADES, ENTRY_SIGNAL_AUDIT e MINIMUM_TICK_AUDIT. Questi risultati non sono una performance sul dataset di mercato.
- Regressioni V1: 23/23 test del motore passati. V2: 29/29 test del motore passati. Passati i test HTTP/regressione esistenti e i controlli sugli ordini IBKR mock.
- Frontend: 152/152 passati, inclusi preflight automatico, blocco di POST /run, parametri invariati e precisione dei decimali (tick-frontend-tests.log).
- Build frontend riuscita (tick-frontend-build.log). Codice Java compilato con JDK 21.

Le run storiche restano consultabili senza riscrittura. Le run precedenti prive di tick verificati non vengono rese operativamente valide dal semplice caricamento: un nuovo replay deve superare FAIL_FAST.

Per l'istanza già in esecuzione occorre riavviare il backend e ricaricare la pagina V3 per registrare le nuove API e caricare lo script tick-grid-1.
