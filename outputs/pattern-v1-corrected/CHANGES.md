# Correzione PATTERN_V1: bug, codice e validazione

Le sole modifiche strategiche sono correzioni della simulazione/contabilità/reportistica V1. Mapping CORE, pattern, Target R, detector, entry, stop nominali e STOP_FIRST restano invariati. Nessuna ottimizzazione, filtro, trailing o modifica al sizing delle altre strategie.

## Bug e cause

| Problema | Causa verificata | Correzione |
|---|---|---|
| TIMEOUT aperti per anni | Il ramo `liquidateTimeouts=false` impostava `frozen=true` e saltava rimozione posizione/realizzazione. L'esportazione successiva assegnava al trade il prezzo finale, `closed=false`. | Ramo rimosso. CLOSE_AT_MAX_HOLDING_BAR obbligatorio: close della holding bar 20, trade CLOSED, remainingQuantity=0, posizione/capitale liberati. |
| Quasi tutto il PnL da quattro TIMEOUT | Posizioni congelate e mantenute nel mark-to-market fino al 2026. | TIMEOUT realizzato alla data effettiva; Result R calcolato dal prezzo di uscita. |
| Overlap/capital skips distorti | Quelle posizioni bloccavano simbolo e capitale per il resto del periodo. | Nuova simulazione completa con contatori nuovi; nessuna statistica OLD riutilizzata. |
| Cash/equity poco distinguibili | `cashEquity` rappresentava capitale più PnL realizzato; disponibilità ottenuta sottraendo notional originari. | Ledger cash con acquisti/vendite, liability SHORT e proventi/collateral vincolati; disponibilità, cash ed equity separati. |
| Rischio 2% ambiguo | Quantità ridotta da notional/cash/maxQuantity senza conservare desired quantity, limiti e rischio effettivo. | Sizing audit: desired/final quantities, motivi, order value, cash, desired/actual risk. Warning quantitativi senza nuovi filtri di default. |
| Exposure errata | Notional originale dell'ordine diviso per equity precedente. | Valore di mercato corrente assoluto delle posizioni EOD / equity dello stesso giorno. Time In Market distinto. |
| MAE oltre 1R su stop | Escursioni dell'intera candela daily di uscita, senza distinzione dei movimenti dopo il fill. | Executed MAE sui STOP <=1R; MFE conservativa con STOP_FIRST. Raw Bar MFE/MAE conservate separatamente per Research. |
| PF/expectancy accostati a PnL non omogenei | PF era già closed-only; il PnL totale includeva i TIMEOUT OPEN. Etichette non chiarivano universo/unità. | PnL realizzato/non realizzato separati; PF PnL lordo, PF netto e PF R distinti. Expectancy/Avg R solo valid closed. |
| Average Winner/Loser ambiguo | Valori monetari netti presentati senza unità chiara. | Average Winner/Loser PnL separati dalle medie in R. |
| Win Rate non esplicitava breakeven | Denominatore includeva tutti i chiusi, senza categoria zero PnL. | Wins/(Wins+Losses), breakevens visibili. Expectancy ponderata usa i pesi di tutti i chiusi, incluso eventuale R prezzo dei net breakeven. |
| OPEN sembrava avere un'uscita | `exitDate/exitPrice` usati anche per il mark finale di posizioni aperte. | Exit null per OPEN; markDate/markPrice separati. Le run legacy sono riconosciute, non rietichettate come corrette. |

Diagnosi runtime con la skill IntelliJ ij-debugger: logpoint prima della correzione `holding=20, liquidate=false, frozenBefore=false, reason=TIMEOUT`; logpoint finale `holding=20, frozen=true, reason=TIMEOUT`. Test sospeso dopo il ritorno: `closed=false`, `openPositions=1`, `resultR=null`. Sessione e breakpoint agent rimossi al termine; breakpoint utente conservati.

## Regole e formule corrette

- Holding bar 1 = barra di entry, come PatternOutcomeAnalyzer. Uscita alla barra `entryIndex+19` se nessun STOP/TARGET. Questo evita di introdurre un diverso conteggio rispetto alla Research. Gli STOP/TARGET prevalgono sul TIMEOUT nella stessa barra.
- TIMEOUT LONG: `(close20-entry)/riskPerShare`; SHORT: `(entry-close20)/riskPerShare`. Non viene assegnato 0R automaticamente.
- Desired risk = equity nota all'open × riskPercent/100; desired quantity = floor(desired risk/rischio prezzo). Poi limiti configurati, sizing standard comprensivo della riserva costi, cash e leva. Le riduzioni ulteriori per costi/notional direzionale sono OTHER.
- Actual risk = rischio prezzo × final quantity; actual risk % = actual risk/equity at entry ×100. Min quantity=1, min order value=0 mantengono il default. Nessun trade piccolo è eliminato automaticamente.
- Equity = cash + long market value − short market value. Cash include proventi SHORT; proventi e margine iniziale 100% sono vincolati: available cash = cash − 2×entry notional SHORT aperto.
- Net realized PnL = realized gross PnL − costi effettivi di entrata/uscita. Net unrealized PnL = unrealized gross PnL − costi di entrata già pagati, senza costi futuri. Total net = realized net + unrealized net.
- PF PnL = sum(gross realized PnL>0)/abs(sum(gross realized PnL<0)), closed only. PF netto usa invece Net PnL; PF R usa Result R. Nessuna perdita: n/d, non zero.
- Expectancy R = Average R = media Result R dei validi chiusi, TIMEOUT inclusi; OPEN esclusi. Win Rate usa Net PnL e ignora i breakeven nel denominatore; la formula ponderata di expectancy usa tutti i chiusi per restare coerente anche in presenza di breakeven/costi.
- Gross exposure = sum(abs(current market value)); percentuale = gross exposure/current equity×100. Media su tutte le sessioni disponibili EOD. Time In Market = sessioni con almeno una posizione durante il giorno/totale sessioni×100.
- Running peak = max(initial capital, equity precedenti/corrente); drawdown = peak−equity; percentuale = drawdown/peak×100. Curva giornaliera, nessuna quotazione futura.
- LONG MFE = maxHigh−entry, MAE = entry−minLow; SHORT MFE = entry−minLow, MAE = maxHigh−entry. Raw Bar misura tutta l'ultima candela; Executed sulla barra STOP usa open noto e stop, sulla barra TARGET open noto e target; TIMEOUT misura fino al close20. Divisione per rischio prezzo per i valori in R. Non si inferisce un percorso intrabar sconosciuto.
- Return = (final equity/initial−1)×100. CAGR = ((final equity/initial)^(1/years)−1)×100, years = giorni tra le date effettive mostrate/365,25. Test 10000→20000, 10 anni: 7,177346%.

## File modificati in questa correzione

- `src/main/java/it/andreademartini/trading/backtest/patternv1/PatternV1PortfolioEngine.java`: timeout, ledger, causalità entry, escursioni, contatori, invarianti, attribution drawdown.
- `src/main/java/it/andreademartini/trading/backtest/patternv1/PatternV1Result.java`: schema 2, campi cash/equity, statistiche, sizing, raw/executed excursions, mark OPEN.
- `src/main/java/it/andreademartini/trading/strategy/patternv1/PatternV1Properties.java`: timeout obbligatorio, minimi opzionali e soglia warning.
- `src/main/java/it/andreademartini/trading/backtest/BacktestEngine.java`: solo overload V1, rimosso parametro che consentiva timeout irrisolti.
- `src/main/java/it/andreademartini/trading/backtest/patternv1/PatternV1ExcelExporter.java`: cinque sheet aggiornati, metriche esplicite, tutti i campi sizing, curve cash/equity/exposure, grafici equity/DD, rifiuto export legacy con nuova semantica.
- `src/main/resources/application.yml`: timeoutExitMode e soglie V1; mapping invariata.
- `src/main/resources/static/pattern-v1.js` e `pattern-v1.html`: summary, breakdown, dettagli rischio/OPEN, tooltip, distinzione legacy.
- `src/main/resources/static/trading-chart.js`: dettagli specifici PATTERN_V1; comportamento delle altre strategie preservato.
- `src/test/java/it/andreademartini/trading/backtest/patternv1/PatternV1PortfolioEngineTest.java`: test TIMEOUT precedente sostituito, raw/exec excursions e contenuti export verificati.
- `src/test/java/it/andreademartini/trading/backtest/patternv1/PatternV1HttpIntegrationTest.java`: API/storage/export e TIMEOUT attraverso loader JPA reale su H2 isolato.
- `src/test/java/it/andreademartini/trading/backtest/patternv1/PatternV1LocalValidationTest.java`: replay esatto OLD, mapping, Research, contabilità giornaliera, quattro regressioni reali e OLD/NEW.
- `package.json`: verifica sintattica nuovo modulo metriche.
- `README.md`: regole, formule, limiti e riproduzione aggiornati.

## File nuovi

- `src/main/java/it/andreademartini/trading/backtest/patternv1/PatternV1PositionSizing.java`.
- `src/main/java/it/andreademartini/trading/backtest/patternv1/PatternV1MetricsCalculator.java`.
- `src/main/resources/static/pattern-v1-metrics.js`.
- `src/test/java/it/andreademartini/trading/backtest/patternv1/PatternV1AccountingTest.java`.
- `src/test/js/pattern-v1-metrics.test.js`.
- Artefatti in `outputs/pattern-v1-corrected`: `VALIDATION.md`, `PATTERN_V1.json`, `PATTERN_V1.xlsx`, `source-sha256.txt`, `database-coverage.json`, `CHANGES.md`, `test-results.json`.

## Test e risultati

217 test Java: zero failure, zero error, zero skipped. 15 test contabili V1, 9 Portfolio Engine, 2 HTTP, 32 Strategy e 1 replay reale inclusi. Pattern Research, Joe Ross e Moving Average passati. Il replay verifica tutti i 1374 chiusi contro Research per entry, stop, target, exit e Result R, con l'opzione di chiusura TIMEOUT già presente in Research. Raw Bar MFE/MAE identici. Detector e Research Engine non modificati.

Nuove coperture: timeout LONG +2,5R senza barra21, timeout SHORT, rilascio capitale/simbolo, quattro simboli incapaci di restare aperti per anni, skip capitale con dettagli, actual risk e catena limiti, cash comprensivo costi, SHORT collateral/liability, breakeven/costi e formule R, PF closed-only, curva MTM/exposure/DD senza futuro, invarianti fail-fast, API e storage, colonne export, mapping frozen, no look-ahead, CAGR.

Comando eseguito con JDK21 (il Maven locale parte su JDK17):

```powershell
mvn test '-DpatternV1.validateLocal=true' '-Dmaven.compiler.fork=true' '-Dmaven.compiler.executable=C:/Program Files/Java/jdk-21.0.11/bin/javac.exe' '-Djvm=C:/Program Files/Java/jdk-21.0.11/bin/java.exe' -q
```

29 test JavaScript passati; `npm.cmd run build` riuscito. JAR aggiornato generato con `mvn package -DskipTests` e compilatore JDK21 dopo il passaggio della suite. `git diff --check` senza errori. Gli ERROR/WARN di alcuni test riguardano i casi negativi IBKR/parser/ordini attesi dalle fixture; nessun ordine operativo è stato inviato.

L'applicazione già avviata su 8081 non è stata fermata o riconfigurata. Per caricare il nuovo codice va riavviata nella normale modalità del progetto. La run corretta è già persistita: aprire `/pattern-v1.html?run=8fcb5a08-8232-4a66-9873-0ad3417e37ee` dopo il riavvio. Test HTTP eseguiti su porta casuale e H2 in-memory isolato. La verifica grafica visuale in browser non è stata effettuata; copertura disponibile via test JS, test HTTP, controlli asset ed export POI.

## Risultati e anomalie residue

Portfolio Summary, OLD/NEW, tutti i symbol/direction, LONG/SHORT, quattro TIMEOUT reali e un esempio actual risk sono in `VALIDATION.md` e nell'Excel. Equity/DD giornalieri e tutti gli eventi sono nel JSON e nell'Excel.

- NEW: initial 10000, final cash/equity 60161,31643, realized net/gross PnL 50161,31643, unrealized 0, 1374 closed, zero open, 111 timeout, 37 same-bar ambiguities, 28 capital skips, 304 position-open skips.
- CAGR 11,3608%, return 501,6132%, max DD 3439,6027 (15,0008%), PF PnL 1,7431, PF R 1,5642, expectancy 0,3259R, win rate 41,5575%.
- LONG 1254 trade, SHORT 120: il blocco dei quattro TIMEOUT non impedisce più l'esecuzione TSLA SHORT.
- Average actual risk 0,40295%, molto inferiore al 2% target: è conseguenza dei vincoli dichiarati, non viene corretta aumentando sizing/limiti.
- Dati CORE nel DB verificati via endpoint status: massimo 2026-09-08; AAPL/GOOGL/NFLX terminano 2026-09-02, TSLA inizia 2010-06-28. Coperture preservate e documentate, nessun import automatico.
- Nessun peggioramento Research oltre le soglie informative -0,25R expectancy o -25% PF R. Sul confronto STOP/TARGET, AAPL e TSLA SHORT hanno cali di expectancy rispetto alla Research non filtrata; dettagli nella dashboard/Excel. Differenze dovute a overlap, capitale e campioni di segnali eseguiti. PF PnL non confrontato direttamente con PF R.
- Quantità piccole e riduzioni marcate restano possibili; warning e SIZE_CONSTRAINED le rendono visibili. Non sono eliminati segnali né modificati target.
- Riconciliazione segnali: 1775 rilevati = 1374 eseguiti + 304 overlap + 28 capitale + 69 piani non validi. Tra questi ultimi, 68 hanno INVALID_RISK_DISTANCE (next open non consente distanza stop positiva), uno INVALID_TARGET_PRICE (target calcolato non positivo). Nessuna entry retroattiva da High/Low/Close. Si osservano 17 warning quantità <=4 e 91 warning di riduzione marcata per capitale.
- Caso da verificare nei dati: TSLA SHOOTING_STAR completata 2021-03-04, High=291,313; Open 2021-03-05=208,687. Risk=82,626, target SHORT 4R=-121,817: ordine non eseguito e INVALID_TARGET_PRICE registrato. Non si può concludere dal solo OHLC che il dato sia errato; merita verifica della fonte. Serie, stop, pattern e Target R non sono stati alterati.
- Fill nominali e priorità alfabetica restano convenzioni dichiarate; zero commissioni/slippage nel replay corrisponde alla run OLD. Il risultato non include costi operativi ulteriori non configurati.
- I TIMEOUT non sono più aperti. AAPL 2010/QQQ 2010/XLK 2010/NVDA 2013 chiudono tutti alla ventesima barra, non nel 2026.
