# Validazione V2: tre modalità eseguite sullo stesso snapshot

Data: 29 ottobre 2026. Nessuna ottimizzazione dei parametri, nessuna interpretazione fuori campione.

Riferimento V1 originale: `7cabcb2f-1d3e-4cca-ad80-1cf4c2d57bc5`.
Dataset fingerprint comune: `569e4757614f461d6eec88626eab9505d02d9efcdb413ddf96c1caab34fbe06b`.
Periodo effettivo: **2017-01-03 → 2026-09-02**. Universo configurato 30 simboli, 28 utilizzati: BA/BAC già esclusi nello snapshot V1 per dataset identici. Non sono stati scelti nuovi strumenti o nuove date in base al risultato V2.

Tutte le modalità: capitale 10.000 USD, rischio 0,5%, max posizione 30%, max esposizione 100%, leva OFF, SMA 200/5, ATR14 Wilder, stop 1,5 ATR, tre chiusure decrescenti, timeout 5 sedute, commissioni IBKR, slippage 0,03% per lato, stesso calendario, tick e split. Target 1,5 ATR dove effettivamente abilitato. Il flag grezzo TakeProfitEnabled è true in tutte e tre le configurazioni; RECOVERY_ONLY lo forza effettivamente OFF.

## Confronto completo

| Misura | RECOVERY_ONLY (A) | ATR_TARGET_ONLY (B) | FIRST_OF_BOTH (C) |
| --- | ---: | ---: | ---: |
| Trade chiusi | 2.238 | 2.157 | 2.250 |
| Posizioni aperte | 6 | 7 | 6 |
| Equity finale USD | 13.187,51 | 13.142,98 | 12.457,30 |
| PnL portafoglio USD, inclusi aperti | 3.187,51 | 3.142,98 | 2.457,30 |
| CAGR | 2,9051% | 2,8691% | 2,3002% |
| Max drawdown DAILY | 13,4351% | 19,1292% | 13,5279% |
| Profit factor netto | 1,10257 | 1,08723 | 1,08195 |
| Win rate | 57,9982% | 53,5930% | 58,8889% |
| Expectancy netta R (media per trade) | 0,000526 | −0,001072 | −0,001758 |
| Average Win USD netti | 26,69 | 34,76 | 24,84 |
| Average Loss USD netti (con segno) | −33,43 | −36,92 | −32,88 |
| Commissioni totali USD | 4.416,15 | 4.252,37 | 4.452,99 |
| Slippage totale USD, già nei prezzi | 2.079,65 | 1.976,31 | 2.041,30 |
| Esposizione DAILY media | 29,0733% | 33,4377% | 27,5239% |
| Trade con ambiguità intrabar registrata | 0 | 0 | 0 |

Profit factor/win rate/expectancy/average win-loss usano i trade chiusi. Equity e CAGR includono il mark-to-market separato degli aperti; costi totali includono i costi d'ingresso degli aperti. La media dei risultati R non è un rendimento ponderato per capitale e non è uguale al CAGR. I valori integrali non arrotondati sono nel JSON e nei file di run.

## Distribuzione delle uscite (trade chiusi)

| Motivo | A: numero (%) | B: numero (%) | C: numero (%) |
| --- | ---: | ---: | ---: |
| RECOVERY | 1.524 (68,10%) | 0 (0%) | 1.252 (55,64%) |
| TAKE_PROFIT_ATR | 0 (0%) | 591 (27,40%) | 215 (9,56%) |
| STOP_LOSS | 436 (19,48%) | 510 (23,64%) | 429 (19,07%) |
| GAP_STOP | 97 (4,33%) | 106 (4,91%) | 95 (4,22%) |
| GAP_TARGET | 0 (0%) | 132 (6,12%) | 91 (4,04%) |
| TIMEOUT | 181 (8,09%) | 818 (37,92%) | 168 (7,47%) |

Nel campione osservato l'aggiunta del target non migliora il CAGR rispetto alla baseline; ciò non determina quale modalità funzionerà fuori campione. Nessuna scelta automatica del vincitore: la pagina resta sul default richiesto ATR_TARGET_ONLY.

Il file `EXIT_ANALYSIS_BY_MODE` nel workbook confronta anche PnL netto, win rate, Average NetR e durata media per ogni categoria e modalità. Le 18 righe derivano dalle tre run effettive, senza risultati inventati per modalità non eseguite.

## Identità e artifact

- A: `a4a3cacd-2f46-405e-9100-e1c0c28b740f` — RECOVERY_ONLY.
- B: `5145953a-8ca4-4f22-967a-7fc673bd75dd` — ATR_TARGET_ONLY.
- C: `ebade6be-ad43-4b7f-8383-0866e484ac52` — FIRST_OF_BOTH.

Workbook: `outputs/trend-pullback-rebound-v2-validation/TREND_PULLBACK_REBOUND_V2_COMPARISON.xlsx`, circa 2 MB. Sono presenti anche tre workbook individuali con i rispettivi runId, ciascuno circa 2 MB. Riapertura con Apache POI superata; verificati tre confronti, tre audit di regressione e 18 categorie di uscita.

Evidenza integrale: `outputs/trend-pullback-rebound-v2-validation/three-mode-validation.json`, con hash, metriche, analisi delle uscite, errori/esclusioni, ambiguità e riconciliazione. Generazione locale delle tre run e dei report: circa 28 secondi.

Tutte e tre le riconciliazioni sono superate. RECOVERY_ONLY ha lo stesso hash economico della run V1 archivio: trade, decisioni sui segnali ed equity giornaliera coincidono. Le modalità B/C riportano la verifica del riferimento, non una parità dei loro rendimenti con A.

## Test e integrità

- V2: 29 test JUnit del motore/audit/costi/storico/export/confronti, più un test d'integrazione HTTP completo delle tre modalità e zero `placeOrder` IBKR.
- V1 originale: 23 test del motore e un test HTTP superati; due test HTTP della navigazione superati.
- Regressioni Pattern: accounting V1, portfolio V1, PatternV2Test e PatternStableTest; regressioni WeeklyTteTest e HistoricalWeeklyTteTest superate.
- V3: 20 SignalTimeframeTest e sette metodi mirati ResearchFiltersTest superati (segnali, filtri, indicatori, audit, export, aggregazione settimanale). Non si dichiara l'esecuzione dell'intera suite del repository.
- Totale dei test Java distinti selezionati: **218 superati**, zero fallimenti e zero saltati nelle suite selezionate. Test JavaScript: **138 superati**.
- Verificati gli SHA-256 di **115 file sorgenti protetti** (V1, Pattern, PAPER): zero cambiamenti. Verificati gli SHA-256 di **17 file dello storage V1**: zero cambiamenti. I vecchi file Momentum non sono stati ripristinati o modificati.

Nessun invio ordini IBKR. La V2 è esclusivamente ricerca/backtest. Restano i limiti di universo corrente, qualità dei dati adjusted, catalogo split e simulazione DAILY, descritti nel documento delle regole.
