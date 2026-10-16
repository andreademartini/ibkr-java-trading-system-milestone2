# PATTERN_V3: segnali DAILY/WEEKLY con esecuzione DAILY

Intervento del 16 ottobre 2026, limitato alla ricerca V3. Il selettore
**SIGNAL TIMEFRAME** è disponibile nella configurazione, con DAILY predefinito.
La descrizione distingue Signals, Execution e Position Management. Non esiste
un selettore del timeframe di esecuzione: quest'ultimo è DAILY, come la gestione.

## Analisi e architettura

Il percorso resta `PatternV3Controller.run` → `PatternV3Service.run` /
`runOnSnapshot` → `PatternPortfolioSimulator.run`. Il servizio carica e salva
sempre il dataset DAILY originale. `ConfiguredPatternStrategy.onClosedBar`
richiama gli stessi `PatternDetector` esistenti; `onNextOpen` crea il piano
di ingresso. Non sono state modificate definizioni, direzioni o Target R.

È stata aggiunta una sola vista dei segnali, `SignalBars`, tra le serie DAILY
validate e i detector/indicatori. In DAILY conserva le barre e gli indici
originali. In WEEKLY aggrega le sole settimane complete e mantiene una mappa
esplicita dagli indici settimanali agli indici DAILY di inizio e conferma.
Il motore del portafoglio, i grafici dei prezzi e i trade usano indici DAILY.
L'indice iniziale del pattern resta distinto dall'inizio della singola settimana
di segnale: un engulfing può coinvolgere due settimane.

La vista settimanale è interna alla V3: non è un nuovo dataset scaricato, non
aggiunge WEEKLY al modello condiviso `BarInterval` e non passa ai provider o al
motore di esecuzione. I detector e gli indicatori consumano OHLCV senza dipendere
dall'enum del trasporto della barra.

## Aggregazione e calendario

Ogni settimana civile americana, lunedì–venerdì, usa OPEN della prima sessione,
HIGH massimo, LOW minimo, CLOSE dell'ultima e somma dei volumi. Si conservano
provider e convenzione adjusted; provenienze diverse nella stessa settimana
producono un errore, anziché una candela di scala incerta. Nessun prezzo
storico viene riscritto o moltiplicato per fattori di split.

L'unico calendario preesistente era `PatternPaperCalendar`, basato su copertura
IBKR recente, non utilizzabile come calendario storico e lasciato invariato.
È stato aggiunto `ResearchSessionCalendar`, versione
`V3_US_EQUITY_SESSIONS/1`, offline e privato della ricerca V3. Copre 2010–2028:
festività statunitensi cash equity, Good Friday, Juneteenth dal 2022 e chiusure
straordinarie note per Sandy, Bush e Carter. Le chiusure anticipate rimangono
sessioni valide, perché l'input è una barra DAILY già conclusa.

Le regole delle festività e delle sessioni abbreviate sono riscontrate nel
[calendario NYSE](https://www.nyse.com/trade/hours-calendars).
Per le eccezioni: [chiusura Carter](https://ir.theice.com/press/news-details/2024/The-New-York-Stock-Exchange-Will-Close-Markets-on-January-9-to-Honor-the-Passing-of-Former-President-Jimmy-Carter-on-National-Day-of-Mourning/default.aspx)
e [documentazione SEC sulle chiusure Sandy](https://www.sec.gov/file/proposed-rule-release-no-34-69077).

La lista delle sessioni disponibili deve coincidere con quelle attese della
settimana. Una festività non rende incompleta la settimana. Una sessione attesa
mancante, una settimana iniziale troncata o una finale ancora in formazione
escludono la barra settimanale e generano `WEEKLY_INCOMPLETE` con date attese e
disponibili. Le barre complete residue alimentano gli indicatori secondo il
conteggio di barre esistenti, senza inserire candele sintetiche per le lacune.
Date fuori dalla copertura del calendario producono un errore esplicito.

L'aggregazione può essere preparata offline, ma `atDailyIndex` espone una barra
soltanto sull'ultima sessione attesa e presente. Il dataset viene prima limitato
alle date del backtest: un venerdì successivo al limite non può completare una
settimana terminata artificialmente giovedì. La scelta non richiede il prezzo
di una barra futura. I test verificano il confine temporale, la stabilità del
prefisso e l'assenza di segnali anticipati.

## Indicatori, filtri e ordini temporali

`BollingerAtr` e `MarketRegimeIndicators` ricevono le barre della vista dei
segnali una volta per periodo concluso. Periodi, formule, soglie e regole di
warm-up sono invariati. Bollinger, ATR, ADX, DI, EMA20/50/200, classificazione
Market Regime, PatternRange e CloseStrength usano quindi il timeframe scelto.
Anche `MarketRegimeAnalysis.calculate`, che ricalcola gli indicatori dopo il
backtest, usa la stessa vista e li associa all'indice DAILY di conferma.
Non sono emersi filtri che richiedano una distinta conversione in giorni di
calendario: i filtri esistenti operano su periodi di barre e caratteristiche
del pattern. I periodi temporali dei report continuano a essere anni/date.

Dopo la conferma WEEKLY si mette il segnale in attesa. La prima barra DAILY
disponibile successiva esegue `onNextOpen`, slippage e sizing esistenti, sulla
distanza tra ingresso DAILY e stop derivato dal pattern WEEKLY. Un rischio
non valido rifiuta l'ingresso. Non si usano WeeklyOpen o WeeklyClose come fill.

Dopo l'ingresso il ciclo resta DAILY. `PatternV3BarRules` delega alle regole gap
condivise `BarExecution`, senza modifiche in questo intervento. OPEN ha priorità
per le posizioni già aperte; stop/target intrabar e STOP_FIRST restano esistenti.
Sulla barra di ingresso non si anticipa l'esistenza della posizione. Lo stop
intrabar può essere eseguito dopo l'ingresso. Il timeout conta DAILY e mantiene
la convenzione della barra di ingresso come barra 1, in entrambe le modalità.
Sizing, commissioni, slippage, capitale ed esposizioni non sono stati modificati.

## Configurazione, run, replay e Excel

`PatternV3Configuration.signalTimeframe` è un enum DAILY/WEEKLY. Tutti i metodi
di copia conservano il campo. Il backend e il modello frontend rifiutano valori
diversi; la vecchia proprietà `global.timeframe` identifica l'esecuzione ed è
validata DAILY.

Per conservare la leggibilità e gli hash delle vecchie run, la serializzazione
canonica omette il valore predefinito DAILY: assenza e DAILY esplicito hanno lo
stesso significato e hash. WEEKLY viene salvato nella configurazione e cambia
l'hash. Le nuove run espongono comunque esplicitamente `SignalTimeframe`,
`ExecutionTimeframe=DAILY` e `ManagementTimeframe=DAILY` nei metadati. Non è una
migrazione dei file storici, che restano intatti. Modello di esecuzione, dataset,
configurazione dei filtri, intervallo e informazioni split già disponibili
sono conservati.

Run History mostra DAILY SIGNAL o WEEKLY SIGNAL. Il replay senza configurazione
conserva timeframe e modello di esecuzione originali. Si può rieseguire lo stesso
snapshot cambiando soltanto signalTimeframe: vengono ricalcolate le baseline
dei filtri nel timeframe nuovo, senza abbinare una baseline DAILY a WEEKLY.
Il confronto fra timeframe richiede identità degli altri parametri, compresi
filtri, date e costi. Gli esperimenti di capitale restano separati.

L'export conserva tutti i fogli e le colonne precedenti. TRADES aggiunge in coda
`SignalTimeframe`, `SignalPeriodStart`, `SignalPeriodEnd`,
`SignalConfirmationDate`. Per WEEKLY il periodo è la settimana del segnale,
non l'intero pattern multibar; in DAILY le tre date coincidono con la sessione.
RUN_INFO contiene i tre timeframe, versione del calendario e unità del timeout.
Tutte le colonne audit gap e GAP_EXIT_ANALYSIS sono preservate: i gap si
misurano sempre sulle aperture DAILY.

## Regressione e confronto

Prima di modificare i componenti principali sono state acquisite le tre baseline
complete e gli hash dei file protetti. `SignalTimeframeHistoricalTest` riesegue
lo stesso dataset congelato e verifica la parità esatta del portafoglio DAILY
V3, di ogni esecuzione/costo, dei segnali, degli indicatori e dell'audit gap,
normalizzando soltanto l'identificativo casuale della run. L'hash DAILY è uguale.
Sono stati rieseguiti anche V1 e V2: portafogli identici alle rispettive baseline.

Intervallo richiesto 2010-01-01–2026-09-08, effettivo
2010-01-04–2026-09-08. Capitale iniziale 10000, rischio teorico 2%, timeout
13 sessioni DAILY in entrambe le simulazioni.

| Metrica | DAILY | WEEKLY |
|---|---:|---:|
| Final Equity | 63596.2058626152 | 19155.3955101753 |
| CAGR % | 11.732154291 | 3.974714094 |
| Max Drawdown % | 16.663199844 | 15.099988259 |
| Profit Factor Net | 1.512242249 | 1.572935338 |
| Win Rate % | 43.862815884 | 54.646840149 |
| Closed Trades | 1108 | 269 |
| Holding medio, sessioni DAILY | 5.007220217 | 10.137546468 |
| Gap Stop / Target | 77 / 54 | 12 / 7 |
| Total Costs | 8378.1733893848 | 1348.5939708247 |
| Capital Skips | 23 | 2 |
| Esposizione media % | 28.796864455 | 14.925927413 |

Il [confronto completo](../outputs/signal-timeframe-evidence/REPORT.md) contiene
anche durata in giorni di calendario, anni positivi/negativi, trade per anno,
peggiore perdita in R e distribuzioni per simbolo/pattern. Sono disponibili due
Excel completi e `comparison.json`. WEEKLY non viene dichiarata migliore: gli
orizzonti degli indicatori cambiano e i risultati mostrano minore attività ed
esposizione, oltre a un CAGR inferiore. Nessuna ottimizzazione è stata effettuata.

## Test e file modificati

Nuovi file Java sotto `backtest/patternv3/`: `SignalTimeframe`,
`ResearchSessionCalendar`, `SignalBars`.
Estesi: `PatternV3Configuration`, `PatternPortfolioSimulator`,
`MarketRegimeAnalysis`, `PatternV3Run`, `PatternV3Service`, `PatternV3Controller`,
`PatternV3RunComparison`, `PatternV3ExcelExporter`.
Frontend: `pattern-v3.html`, `pattern-v3.js`, `pattern-v3-model.js`.
Nessuna nuova modifica ai file V1/V2, PAPER, ordini o storico dei prezzi.
Il workspace conteneva già modifiche degli interventi precedenti.

Test nuovi: `patternv3.SignalTimeframeTest`,
`execution.SignalTimeframeBaselineTest`, `execution.SignalTimeframeHistoricalTest`,
`js/pattern-v3-timeframe.test.js`. Aggiornati i mock dei controlli in
`js/pattern-v3.test.js` e `js/pattern-v3-filter.test.js`.

| Scenari richiesti | Verifica |
|---|---|
| 1–8, 10: OHLCV, festività, sessioni mancanti, ultima settimana, causalità | `SignalTimeframeTest` aggregazione, calendario e prefisso/troncamento |
| 9: prezzi adjusted/split | Nessun secondo aggiustamento, rifiuto di provenienza mista; suite split preesistente invariata |
| 11–13: pattern WEEKLY | Detector reali HAMMER, ENGULFING e SHOOTING_STAR |
| 14–18: indicatori | Seed/warm-up numerici e confronto dei valori effettivi dei filtri e Market Regime con flussi WEEKLY indipendenti |
| 19–24: conferma, ingresso, stop e R | Venerdì/Good Friday, lunedì festivo, daily open, stop weekly e rifiuto rischio non valido |
| 25–33: gestione | Stop/target intrabar DAILY, quattro gap con movimenti successivi opposti, slippage, STOP_FIRST e timeout DAILY |
| 34–39: regressione/isolamento | Baseline integrale sul dataset, hash protetti, store temporanei senza servizi broker |
| 40–41: export/storico | Round trip delle run, colonne/date, audit gap, storia e confronti; scansione di tutti i valori Excel |

Comandi riproducibili:

```powershell
# Solo sul codice precedente; non sovrascrive baseline esistenti
mvn '-Dtest=SignalTimeframeBaselineTest' -DsignalTimeframeBaseline=true test
# Dopo la modifica
mvn '-Dtest=SignalTimeframeTest' test
mvn -DsignalTimeframeHistorical=true verify
npm.cmd test
npm.cmd run build
```

Log in `target/signal-timeframe-*.log`. Nessun backend operativo è stato
riavviato. Per usare il selettore nell'app è necessario il normale riavvio del
backend aggiornato. Nessun ordine IBKR PAPER/LIVE reale è stato trasmesso.

Esito finale: **Maven verify superato, 638 test complessivi: 622 eseguiti,
16 opzionali esclusi, zero fallimenti/errori**. Il confronto storico è incluso
nel verify; passano anche i 20 casi della suite `SignalTimeframeTest`.
**111 test frontend superati**, build frontend e `git diff --check` superati.
Prodotto il JAR aggiornato. Log principali: `target/signal-timeframe-verify.log`,
`target/signal-timeframe-historical.log`, `target/signal-timeframe-tests.log`,
`target/signal-timeframe-frontend.log`, `target/signal-timeframe-frontend-build.log`.

## Limitazioni esplicite

- Calendario limitato agli strumenti cash equity americani e al 2010–2028;
  nuove chiusure straordinarie richiedono aggiornamento. Non è un servizio di
  calendario operativo PAPER e non viene usato da esso.
- Il dataset reale presenta settimane finali troncate e lacune storiche: sono
  segnalate nei warning, non ricostruite. La copertura non è identica per simbolo.
- Prezzi già adjusted restano in quella scala. La qualità della dichiarazione
  RAW/ADJUSTED del provider, i dividendi e la copertura parziale del catalogo
  split mantengono i limiti descritti in `GAP_EXECUTION_AUDIT.md`.
- Il modello di gap/slippage della V2 rimane invariato, compresi i suoi limiti
  rispetto ai fill di ordini limit reali. Non viene modificato il PAPER.
