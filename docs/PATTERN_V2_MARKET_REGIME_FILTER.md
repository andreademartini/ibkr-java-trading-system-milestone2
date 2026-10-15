# PATTERN_V2 — Market Regime Filter

Il filtro è una funzione di ricerca esclusiva della V2. Non usa broker, servizi ordini, scheduler o esecuzione PAPER.

## Architettura verificata

- `PatternV2Service`: normalizzazione, snapshot dei dati, simulazione, analisi, confronto e persistenza V2.
- `ConfiguredPatternStrategy`: richiama i detector candlestick Research sulla sola storia fino alla candela completata; crea il piano di ingresso alla successiva apertura.
- `PatternPortfolioSimulator`: esegue il portafoglio completo, allocazione sequenziale, sizing, costi, cash, equity e uscite. V1 usa un altro motore.
- `MarketRegimeIndicators`: unico algoritmo Wilder ADX/DI, già usato da `MarketRegimeAnalysis`. Il periodo è ora parametrizzabile mantenendo il costruttore ADX14 dell'analisi. Le EMA e ATR analitiche non servono per ammettere un segnale.
- `MarketRegimeAnalysis`: analisi dei trade reali, classi principali con EMA e classi secondarie ADX/DI. La funzione di relazione LONG/SHORT con la direzione DI è condivisa con il filtro.
- Filtri precedenti: Pattern Range, Close Strength, Bollinger/ATR. Conservano il loro ordine e le loro formule.
- `PatternV2Store`: configurazioni, current/default, run e dataset JSON con checksum. Gli snapshot V2 sono immutabili; non contengono bean V1 modificabili.
- `pattern-v2.html`, `pattern-v2.js`, `pattern-v2-model.js`: form, validazione, risultati, configurazioni salvate e Run History.
- `PatternV2ExcelExporter`: conserva i fogli precedenti e aggiunge diagnostica e confronti del filtro.

## Default e validazione

```json
{
  "marketRegimeFilterEnabled": false,
  "marketRegimeFilterAdxPeriod": 14,
  "marketRegimeFilterAdxThreshold": 25.0,
  "marketRegimeFilterMode": "EXCLUDE_WITH_TREND"
}
```

Il periodo deve essere intero e almeno 2; la soglia deve essere un numero finito in [0,100]. Sono rifiutati null espliciti, stringhe numeriche, periodi frazionari e modalità diverse. I campi assenti nelle configurazioni legacy ricevono i default.

Il progetto usa Jackson 2 per il repository e Jackson 3 per HTTP Spring: entrambi validano i tipi rigorosamente. I valori di default vengono omessi nel JSON per mantenere identico il contratto legacy letto dall'adattatore della V1 congelata. Il loro significato è fisso e viene ricostruito dai default; i valori diversi vengono persistiti. La Run History espone sempre tutti e quattro i parametri e CONFIGURATION nell'Excel li include esplicitamente. Nessun file V1/PAPER è stato modificato per questa compatibilità.

## Indicatori e decisione

I detector e gli indicatori leggono la candela completata che conferma il pattern. Il filtro non legge l'apertura successiva, né prezzi o risultati futuri. Ogni simbolo ha uno stream cronologico indipendente. Lo smoothing ADX/DI mantiene la convenzione di seed TA-Lib già esistente, verificata con il fixture di riferimento e le regressioni JUnit. Con periodo 14 il primo ADX è disponibile all'indice 27. I dati iniziano dalla prima barra del backtest; non è stata aggiunta una storia precedente artificialmente.

Con filtro ON e ADX >= soglia:

- LONG e +DI > −DI: scarto `MARKET_REGIME_WITH_TREND`.
- SHORT e −DI > +DI: scarto `MARKET_REGIME_WITH_TREND`.
- Direzione opposta o DI uguali: questo filtro ammette il segnale.

Sotto soglia il filtro ammette il segnale. Se uno fra ADX/+DI/−DI è mancante o non finito, il filtro ON scarta con `INSUFFICIENT_REGIME_DATA`; OFF conserva il comportamento precedente. Un valore non finito diventa dato mancante, mai zero. Zero calcolato su un mercato piatto è invece un valore valido.

`Enable Market Regime Analysis` resta separata: usa il periodo ADX14 originale e non cambia le decisioni. Il filtro calcola internamente il periodo configurato anche con analisi OFF. Le colonne storiche mantengono il nome ADX14; per periodi personalizzati, il foglio MARKET_REGIME_FILTER_SIGNALS contiene gli indicatori effettivi della decisione, collegabili ai trade tramite simbolo, pattern, direzione e indice del segnale. Le colonne analitiche preesistenti restano ADX14 se l'analisi è ON.

## Pipeline e contatori

```text
Candela completata -> pattern rilevato -> Market Regime Filter
-> Pattern Range -> Close Strength -> Bollinger/ATR
-> controllo posizione -> coda per apertura successiva
-> controllo posizione/direzione/rischio -> capitale/sizing
-> ingresso -> stop/target/timeout normali
```

Ogni segnale rilevato riceve un solo audit del regime. Uno scarto regime interrompe la pipeline: non viene messo in coda, non viene dimensionato e non consuma capitale. Gli altri segnali conservano la priorità temporale e alfabetica esistente.

- Total Pattern Signals: tutti i pattern rilevati, prima dei filtri.
- Signals Evaluated: decisioni con filtro ON; OFF è zero.
- Signals Accepted: ammessi dal solo filtro regime, prima degli altri controlli.
- Rejected WITH_TREND / INSUFFICIENT_DATA: cause distinte e mutuamente esclusive.
- Acceptance/Rejection Rate: sul totale valutato; N/D quando OFF o senza segnali.
- Trades Executed: posizioni effettivamente aperte, incluse eventuali posizioni ancora aperte.
- Capital Skips / Position Open Skips: eventi reali della nuova simulazione, separati dagli scarti del regime.

In ON, Evaluated = Accepted + Rejected WITH_TREND + Rejected INSUFFICIENT_DATA. Accepted può superare Trades Executed + Capital Skips + Position Open Skips: altri filtri, rischio d'ingresso non valido o assenza della barra successiva possono impedire l'esecuzione. Gli eventi e tutti gli audit sono esportati per la riconciliazione. Nessuno scarto riceve PnL o prezzi di esecuzione inventati.

## Confronto e stabilità

Ogni nuova run filtrata genera una baseline con gli stessi dati, date, simboli, target, costi, sizing e altri filtri; differisce solo l'abilitazione del filtro regime. `marketRegimeFilterAnalysis.baselineRunId` identifica questa baseline, anche quando esistono altri confronti di filtri. Il confronto generico delle run continua a usare il checksum del dataset e il controllo dei parametri di trading.

Le statistiche PERIOD/YEAR descrivono il portafoglio continuo: equity alla fine del periodo precedente, picco locale, trade chiusi assegnati per data di uscita; scarti assegnati per data del segnale.

Le righe INDEPENDENT_PERIOD sono vere nuove simulazioni 2010–2013, 2014–2017, 2018–2021 e 2022–ultima barra: ciascuna riparte dal capitale iniziale e azzera anche il warm-up degli indicatori. Le posizioni alla fine di ciascuna finestra restano OPEN se previsto dal motore; i conteggi delle posizioni eseguite possono includerle, mentre PF, win rate e Net Expectancy R usano solo trade chiusi. Le finestre non sono out-of-sample e non costituiscono validazione di una soglia ottimizzata.

Le categorie dei trade accettati sono disgiunte: dati ADX/DI insufficienti; ADX<20 WEAK_TREND; 20<=ADX<25 TRANSITION; altrimenti WITH_TREND, COUNTER_TREND o NEUTRAL_OR_MIXED secondo l'allineamento originale ADX25/DI. La soglia personalizzata del filtro resta una decisione separata da questa tassonomia analitica.

## Export e sicurezza

Nuovi fogli: MARKET_REGIME_FILTER_SUMMARY, MARKET_REGIME_REJECTIONS, MARKET_REGIME_FILTER_SYMBOLS, MARKET_REGIME_FILTER_PERIODS, MARKET_REGIME_FILTER_COMPARISON e MARKET_REGIME_FILTER_SIGNALS. CONFIGURATION contiene i quattro parametri; TRADES contiene indicatori, regime, allineamento e stato del filtro. Tutti i fogli precedenti sono mantenuti. I risultati shadow esistenti restano separati.

La run di riferimento verificata è a 13 barre, con capitale 10.000, rischio 2%, ordine al 30% dell'equity, limiti dinamici al 100%, leva OFF e costi IBKR/slippage originali. Il backtest usa esclusivamente lo snapshot archiviato fino al 2026-09-08. Current e la run storica originale sono verificati byte per byte dopo il confronto. Non viene chiamato alcun provider o broker nell'esperimento offline.

## Verifica riproducibile

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.11'
mvn verify
npm.cmd test
npm.cmd run build
mvn -Dtest=MarketRegimeFilterExperimentTest -DmarketRegimeFilterExperiment=true test
```

JUnit include regole LONG/SHORT, soglie, uguaglianza DI, null/non finiti, periodo configurabile, warm-up causale, parità OFF, capitale liberato che ammette un segnale successivo, costi/equity riconciliati, persistenza, compatibilità JSON V1, HTTP/Run History ed Excel. Le suite esistenti verificano indicatori contro il fixture TA-Lib, pattern senza look-ahead, sizing, costi, timeout e isolamento V1/PAPER. HTTP usa BACKTEST con ordini e auto-esecuzione disabilitati e verifica `placeOrder` mai chiamato. Il frontend verifica raccolta ON/OFF, valori precisi, errori, rendering degli scarti e pulizia di dati obsoleti alla riapertura di una run legacy.

Risultati e lista completa dei file: `outputs/market-regime-filter-evidence/REPORT.md`.
