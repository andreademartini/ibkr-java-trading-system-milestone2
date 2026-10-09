# Capital Skipped Signals — Shadow Analysis

Giudizio globale: **Shadow signals outperform executed trades**, esclusivamente descrittivo. Non è significatività statistica dimostrata e non autorizza una nuova allocazione.

Baseline originaria: 711463cf-19a7-49ea-9398-c845633074ae. Nuovo replay storico ON: ab508dfb-e79c-48fb-b8c9-6f0a3794ee7a. Dataset identico: 190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd. Stessa configurazione di trading, senza filtri Range/CloseStrength, periodo 2010-01-01–2026-09-08, DAILY, stessi nove simboli e costi split-aware.

Conteggi calcolati dai nuovi eventi: 1775 segnali rilevati; 1310 trade eseguiti; 105 capital skips; 290 scarti per posizione già aperta; 70 scarti per rischio invalido. Nessuno di questi valori è hardcodato nel programma.

Shadow completati: 105; incompleti: 0; non eseguibili: 0.

| Metrica | Executed Trades | Shadow Capital Skips |
|---|---:|---:|
| Number of Trades | 1310,00 | 105,00 |
| Wins | 543,00 | 46,00 |
| Losses | 767,00 | 59,00 |
| Win Rate % | 41,4504 | 43,8095 |
| PF Gross | 1,72 | 2,2043 |
| PF Net | 1,5797 | 1,9238 |
| Gross Expectancy R | 0,3317 | 0,4322 |
| Net Expectancy R | 0,131 | 0,2449 |
| Average Winner NET R | 2,0572 | 2,0235 |
| Average Loser NET R | -1,2327 | -1,1418 |
| Average Net PnL per Trade | 36,8369 | 43,437 |
| Average Actual Risk % | 0,5609 | 0,7974 |
| Average Holding Bars | 5,7244 | 6,5429 |
| Timeout % | 8,0153 | 11,4286 |

**Gross R e Net R non sono la stessa metrica.** Il Net Expectancy R del confronto è ricalcolato per ogni trade come Net PnL / rischio monetario iniziale effettivo; il precedente Expectancy R della dashboard principale resta invariato e misura il R di prezzo prima dei costi. Average Winner/Loser R della nuova tabella sono Net R. Vincite e perdite dipendono dal PnL netto.

## Stabilità temporale

Cohort per Signal Date: il trade rimane nel periodo in cui è nato il segnale, anche se termina dopo.

| Periodo | Skipped / completati | Win Rate % | PF netto | Net Expectancy R | Average Net PnL |
|---|---:|---:|---:|---:|---:|
| 2010–2013 | 51 / 51 | 49,0196 | 2,2284 | 0,4592 | 53,3649 |
| 2014–2017 | 45 / 45 | 35,5556 | 1,8221 | -0,0216 | 37,5759 |
| 2018–2021 | 9 / 9 | 55,5556 | 1,2232 | 0,3629 | 16,4838 |
| 2022–end | 0 / 0 | N/D | N/D | N/D | N/D |

96 dei 105 shadow ricadono nel 2010–2017, solo 9 nel 2018–2021 e nessuno dal 2022. Nel 2014–2017 il PF monetario è positivo ma la media Net R è negativa: le quantità e i rischi monetari differiscono fra trade. Non c’è evidenza di un vantaggio persistente lungo tutti e quattro i periodi.

## Per simbolo, direzione, pattern e Target R

| Symbol | Direction | Pattern | Target R | Executed closed | Skipped | Shadow wins / losses | Win Rate % | PF netto | Net Expectancy R | Average Net PnL | Total Net PnL |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| AAPL | LONG | BULLISH_ENGULFING | 3 | 115 | 11 | 5 / 6 | 45,4545 | 2,6564 | 0,4096 | 79,6554 | 876,2094 |
| GOOGL | LONG | BULLISH_HARAMI | 1.5 | 137 | 5 | 1 / 4 | 20,00 | 0,6458 | -0,6496 | -20,3991 | -101,9955 |
| NFLX | LONG | HAMMER | 2 | 177 | 7 | 6 / 1 | 85,7143 | 5,7368 | 1,1906 | 126,7148 | 887,0039 |
| NVDA | LONG | PIERCING_PATTERN | 3 | 149 | 1 | 1 / 0 | 100,00 | N/D | 2,9701 | 574,3169 | 574,3169 |
| QQQ | LONG | HAMMER | 3 | 152 | 17 | 5 / 12 | 29,4118 | 1,3307 | -0,0312 | 12,8879 | 219,0935 |
| SPY | LONG | BULLISH_HARAMI | 3 | 98 | 19 | 8 / 11 | 42,1053 | 1,5274 | 0,3697 | 20,1068 | 382,0299 |
| TSLA | LONG | BULLISH_ENGULFING | 1 | 115 | 8 | 5 / 3 | 62,50 | 1,6368 | 0,1859 | 54,8147 | 438,5177 |
| TSLA | SHORT | SHOOTING_STAR | 4 | 115 | 8 | 3 / 5 | 37,50 | 1,9083 | 0,4358 | 98,8307 | 790,6459 |
| XLK | LONG | HAMMER | 4 | 152 | 16 | 5 / 11 | 31,25 | 1,4997 | -0,1943 | 16,2438 | 259,9013 |
| XLY | LONG | BULLISH_ENGULFING | 1.5 | 100 | 13 | 7 / 6 | 53,8462 | 1,504 | 0,3687 | 18,0891 | 235,1584 |

I campioni per combinazione sono piccoli: per esempio NVDA ha un solo shadow e QQQ/XLK presentano Net Expectancy R negativa. Un trade ombra favorevole non dimostra che avrebbe dovuto sostituire una posizione reale.

## Invarianti e metodologia

Il confronto OFF/ON usa la stessa identità di simulazione: portfolio intero, trade e loro ID, equity curve, CAGR, drawdown, PF principale, cash, allocazione, costi e conteggio capital skips risultano identici. La parità rispetto alla baseline originale è verificata separatamente, ignorando soltanto gli UUID generati per la nuova run. La baseline originale non viene riscritta.

EquityReferenceAtSignal è l’equity alla chiusura della signal bar, prima delle decisioni del prossimo open. Il contesto all’open è registrato separatamente, prima di ogni scarto, usando open disponibili e ultimi close già noti. La quantità ombra usa l’equity del segnale e le stesse policy di sizing/costi/limiti; il cash residuo è sostituito da liquidità virtuale sufficiente solo per il singolo trade. La equity finale non entra nel sizing.

Stop/target, buffer, holding e policy della barra ambigua sono le regole V2, estratte in un helper deterministico riusato dai due motori. La barra d’ingresso conta come 1. Uscite e MFE/MAE conservano la convenzione OHLC deterministica e conservativa V2, senza ricostruzione intrabar o nuove regole per gap.

Gross PnL è calcolato sui prezzi teorici; Net PnL sottrae commissioni, slippage e altri costi esattamente una volta. I prezzi dopo slippage sono esposti e la riconciliazione è testata. Shadow incompleti/non eseguibili non ricevono risultati economici inventati.

Candidati concorrenti = ingressi pendenti allo stesso timestamp, nell’ordine reale del motore, inclusi quelli poi scartati per altri motivi. Invested Capital = valore lordo delle posizioni agli open disponibili. Aggregate Open Risk = somma dei rischi monetari iniziali, esclusi i costi. Il file Excel conserva liste di posizioni e candidati con ordine ed esito per ogni scarto.

Ogni shadow è indipendente nel sizing; i rendimenti possono essere correlati fra loro e ai trade eseguiti. La regola descrittiva richiede almeno 30 completati per gruppo e concordanza stretta di PF netto, Net Expectancy R e win rate. Non costituisce un test inferenziale. Nessun capitale aggiunto al portafoglio, nessun CAGR ombra, nessun ordine IBKR/PAPER/LIVE, nessuna modifica automatica all’allocazione.

## Artefatti

- V2_CAPITAL_SKIPS_SHADOW.xlsx: Excel completo con tutti i fogli precedenti e cinque fogli shadow.
- shadow-analysis.json: risultati, audit e contesti completi.
- V2_SHADOW_OFF.json / V2_SHADOW_ON.json: confronto identico salvo configurazione ON e risultati shadow.
- parity.json: conferma dei regression check storici.
- preservation-check.json: hash V1/shared/PAPER/LIVE e conservazione current/baseline.
- files-changed.json e test-results.json: inventario e verifiche finali.

## File creati e modificati in questa implementazione

**javaCreated**

- src/main/java/it/andreademartini/trading/backtest/patternv2/CapitalSkipAudit.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/CapitalSkipShadowAnalysis.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2BarRules.java

**javaModified**

- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternPortfolioSimulator.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Configuration.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Run.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Service.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Store.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Controller.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2RunComparison.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2ExcelExporter.java

**frontendModified**

- src/main/resources/static/pattern-v2.html
- src/main/resources/static/pattern-v2.js
- src/main/resources/static/pattern-v2-model.js

**testsCreated**

- src/test/java/it/andreademartini/trading/backtest/patternv2/CapitalSkipShadowTest.java
- src/test/java/it/andreademartini/trading/backtest/patternv2/CapitalSkipShadowExperimentTest.java

**testsModified**

- src/test/java/it/andreademartini/trading/backtest/patternv2/PatternV2HttpIntegrationTest.java
- src/test/js/pattern-v2.test.js

**documentationCreated**

- docs/CAPITAL_SKIP_SHADOW.md


Verifiche: 349 casi Maven/JUnit, 345 eseguiti senza errori e 4 opt-in saltati. Esperimento storico eseguito separatamente e passato. Frontend 48/48. Hash verificati su 318 file esterni a V2: nessuna modifica, inclusi V1, classi condivise e PAPER/LIVE. Configurazione corrente e baseline originale conservate byte per byte.

Backend build (Maven package) e frontend build completati con exit code 0.
