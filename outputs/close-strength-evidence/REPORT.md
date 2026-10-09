# CloseStrength PATTERN_V2 — esito del primo esperimento

CloseStrength ≤ 0,80 riduce il drawdown, ma peggiora CAGR, PF netto, expectancy, Net PnL e il rapporto CAGR/Max DD complessivi. Il CAGR migliora in un periodo su quattro. Questo test non dimostra un miglioramento stabile; nessuna soglia è stata ottimizzata o selezionata automaticamente.

Baseline: 711463cf-19a7-49ea-9398-c845633074ae, ultima baseline V2 senza filtri disponibile prima della modifica.

Run C: fdbf3ed1-3566-4ad4-a8d2-8337d018cf19, V2_CLOSE_STRENGTH_080.

Dataset identico: 190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd. Periodo richiesto 2010-01-01–2026-09-08, DAILY, stessi nove simboli, pattern, Target R, entry, stop, timeout, capitale, sizing, maxOrderValue/maxQuantity, costi e calendario split. L'unica regola attivata è CloseStrength ≤ 0,80. Pattern Range è OFF.

| Metrica | Baseline | Close ≤ 0,80 | Differenza |
|---|---:|---:|---:|
| Final Equity | 58.256,3275 | 32.819,6391 | -25.436,6884 |
| Net PnL | 48.256,3275 | 22.819,6391 | -25.436,6884 |
| CAGR | 11,1461 | 7,3866 | -3,7595 |
| Max Drawdown % | 23,8976 | 17,5531 | -6,3445 |
| CAGR / Max Drawdown | 0,4664 | 0,4208 | -0,0456 |
| Trades | 1310,00 | 781,00 | -529,00 |
| Win Rate | 41,4504 | 39,3086 | -2,1418 |
| PF Net | 1,5797 | 1,4913 | -0,0883 |
| Expectancy R | 0,3317 | 0,3177 | -0,0139 |
| Average Winner R | 2,1815 | 2,3199 | 0,1384 |
| Average Loser R | -0,9779 | -0,979 | -0,0011 |
| Average Actual Risk % | 0,5609 | 0,5901 | 0,0292 |
| Exposure % | 37,0781 | 24,2116 | -12,8665 |
| Capital Skips | 105,00 | 23,00 | -82,00 |
| Total Commission | 4154,9313 | 2785,2987 | -1369,6326 |
| Total Slippage | 4083,4042 | 2549,5012 | -1533,9029 |
| Total Costs | 8238,3355 | 5334,7999 | -2903,5356 |

Le differenze di CAGR, win rate, drawdown, risk ed exposure sono punti percentuali. Le commissioni/slippage complessivi diminuiscono perché cambiano i trade; le tariffe e i metodi di calcolo sono identici.

Stabilità temporale: segmenti del portafoglio continuo, equity a mercato, picco drawdown locale; PF/expectancy/win rate su trade chiusi assegnati per data di uscita. Non sono simulazioni indipendenti con capitale reinizializzato.

| Periodo | Modalità | Trades | CAGR % | Max DD % | PF netto | Expectancy R | Win rate % | Net PnL |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| 2010–2013 | Baseline | 267 | 2,5726 | 23,8976 | 1,0685 | 0,2637 | 38,9513 | 1066,3397 |
| 2010–2013 | Close 0,80 | 178 | 3,7633 | 17,5531 | 1,1516 | 0,3847 | 39,8876 | 1587,7689 |
| 2014–2017 | Baseline | 332 | 14,1934 | 20,1672 | 1,4846 | 0,2411 | 38,5542 | 7744,59 |
| 2014–2017 | Close 0,80 | 203 | 10,0855 | 12,9889 | 1,5781 | 0,2563 | 36,9458 | 5426,2011 |
| 2018–2021 | Baseline | 310 | 15,7764 | 14,5698 | 1,6498 | 0,383 | 43,871 | 14.973,5035 |
| 2018–2021 | Close 0,80 | 181 | 8,1559 | 10,7799 | 1,5368 | 0,3269 | 40,3315 | 6262,2782 |
| 2022–2026 | Baseline | 401 | 12,3345 | 12,1435 | 1,8383 | 0,4122 | 43,6409 | 24.471,8943 |
| 2022–2026 | Close 0,80 | 219 | 7,6104 | 9,5752 | 1,6385 | 0,3127 | 40,1826 | 9543,3909 |

Segnali rilevati/valutati: 1775; accettati dal filtro: 946; rifiutati per soglia: 829; invalidi: 0; acceptance rate: 53,2958%. Accettare un segnale al filtro non garantisce un trade: restano le normali regole V2.

Gli esiti dei segnali rifiutati provengono esclusivamente dai trade reali della baseline. Missing/open restano N/D. Le fasce ACTUAL RUN e BASELINE REAL TRADES sono separate in Excel e nella dashboard. Expectancy R è il R di prezzo già usato dal progetto, esclusi i costi; PF e vincite/perdite usano PnL netto.

Preset C salvato: 3a080c31-2078-460e-9e67-940e7c100561.

Preset D V2_RANGE_25_CLOSE_080 salvato: 542140cb-7c1a-49a6-91f8-746f7824243e; Range 2,50% ON e Close 0,80 ON. Non eseguito e non selezionato come migliore.

Verifiche: parità OFF sullo snapshot precedente sia Range OFF sia Range ON (summary, equity curve, trade eccetto ID generati, costi, audit Range); 318 file esterni a V2 identici, comprese V1 e classi condivise; current.json, baseline originale e run Range originale conservati byte per byte.

Artefatti: comparison.json (audit, fasce e simboli), run-comparison.json (17 metriche e periodi), V2_CLOSE_STRENGTH_080.xlsx, V2_BASELINE.xlsx, presets.json, off-parity.json, preservation-check.json. Le vecchie run senza audit CloseStrength restano leggibili e mostrano N/D nel pannello; non sono riscritte.

File di questa implementazione (il workspace conteneva già altre modifiche):

**javaCreated**

- src/main/java/it/andreademartini/trading/backtest/patternv2/CloseStrength.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/CloseStrengthAnalysis.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2RunComparison.java

**javaModified**

- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Configuration.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternPortfolioSimulator.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Run.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Service.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Store.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Controller.java
- src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2ExcelExporter.java

**frontendModified**

- src/main/resources/static/pattern-v2.html
- src/main/resources/static/pattern-v2.js
- src/main/resources/static/pattern-v2-model.js

**testsCreated**

- src/test/java/it/andreademartini/trading/backtest/patternv2/CloseStrengthFilterTest.java
- src/test/java/it/andreademartini/trading/backtest/patternv2/CloseStrengthExperimentTest.java

**testsModified**

- src/test/java/it/andreademartini/trading/backtest/patternv2/PatternV2Test.java
- src/test/java/it/andreademartini/trading/backtest/patternv2/PatternV2HttpIntegrationTest.java
- src/test/js/pattern-v2.test.js

**documentationModified**

- docs/PATTERN_V2.md

Test finali: Maven/JUnit 329 casi, 326 eseguiti con successo e 3 opt-in saltati, zero errori/failure; esperimento CloseStrength esplicitamente eseguito e passato separatamente. Frontend 46/46. Backend package e frontend build completati.
