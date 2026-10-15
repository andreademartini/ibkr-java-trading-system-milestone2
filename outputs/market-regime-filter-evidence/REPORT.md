# PATTERN_V2 ? Risultati Market Regime Filter

Confronto offline reale del portafoglio: 2010-01-04?2026-09-08, Max Holding Bars 13. Filtro ADX14 >=25, EXCLUDE_WITH_TREND. Tutti gli altri parametri invariati; nessuna ottimizzazione della soglia.

Baseline storica: `01919b0e-abc3-4a60-8a44-083f6955cd66`. Baseline nuova: `4c9049ac-e1f9-4a1e-aa26-730adb0fe466`. Filtered: `0c3520f6-1ea2-4fb6-8836-8a9347aed81b`. Dataset: `190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd`.

La run OFF riproduce esattamente il JSON dell?intero portafoglio storico, inclusi equity, eventi, sizing, trade e costi. Current e la run originale sono rimasti identici byte per byte. Le nuove run sono nella Run History V2.

## Confronto completo

| Metrica | Baseline | Filtered | Differenza |
| --- | --- | --- | --- |
| Final Equity | 72.226,3573 | 76.243,9396 | 4017,5823 |
| Net PnL | 62.226,3573 | 66.243,9396 | 4017,5823 |
| CAGR | 12,588 | 12,9541 | 0,3661 |
| Max Drawdown % | 15,6856 | 14,7375 | -0,9481 |
| CAGR / Max Drawdown | 0,8025 | 0,879 | 0,0765 |
| Trades | 1403 | 1108 | -295 |
| Win Rate | 43,1932 | 43,9531 | 0,7599 |
| PF Net | 1,5297 | 1,6149 | 0,0852 |
| Expectancy R | 0,3138 | 0,3259 | 0,0122 |
| Average Winner R | 1,9991 | 1,9899 | -0,0092 |
| Average Loser R | -0,9677 | -0,979 | -0,0113 |
| Average Actual Risk % | 0,6069 | 0,641 | 0,034 |
| Exposure % | 34,9864 | 28,6976 | -6,2888 |
| Capital Skips | 38 | 20 | -18 |
| Total Commission | 4257,5631 | 3676,6961 | -580,8671 |
| Total Slippage | 5967,7498 | 5438,8773 | -528,8725 |
| Total Costs | 10.225,313 | 9115,5733 | -1109,7396 |
| Net Expectancy R | 0,1563 | 0,1956 | 0,0393 |

Le differenze di percentuali sono punti percentuali. Expectancy R ? la convenzione prezzo storica; Net Expectancy R include tutti i costi.

## Diagnostica segnali

| Contatore | Valore |
| --- | --- |
| totalPatternSignals | 1775 |
| signalsEvaluated | 1775 |
| signalsAccepted | 1364 |
| rejectedWithTrend | 399 |
| rejectedInsufficientData | 12 |
| acceptanceRate | 76,8451 |
| rejectionRate | 23,1549 |
| tradesExecuted | 1108 |
| capitalSkips | 20 |
| positionOpenSkips | 175 |

1.775 valutati = 1.364 ammessi + 399 WITH_TREND + 12 dati insufficienti. I 411 scarti non sono 411 trade rimossi a posteriori: l?allocazione successiva ? stata realmente rieseguita. Capital Skips scende da 38 a 20. Ammesso dal regime non significa eseguito: gli altri filtri, la posizione, capitale/rischio e disponibilit? della candela successiva restano determinanti.

## Periodi del portafoglio continuo

| Periodo | Trades B / F | CAGR % B / F | Max DD % B / F | PF netto B / F | Net Exp R B / F | Win Rate % B / F | Net PnL B / F | Scarti |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2010–2013 | 305 / 247 | 7,0649 / 11,9244 | 11,4337 / 8,3366 | 1,2393 / 1,5105 | 0,1859 / 0,2258 | 43,9344 / 44,5344 | 3129,9637 / 5673,4088 | 84 |
| 2014–2017 | 377 / 285 | 11,5089 / 10,0769 | 14,3514 / 14,5033 | 1,4391 / 1,515 | -0,0148 / 0,0997 | 39,5225 / 42,1053 | 7164,2159 / 7332,2559 | 131 |
| 2018–2021 | 318 / 254 | 13,4489 / 13,7178 | 15,6856 / 13,5937 | 1,492 / 1,6223 | 0,2112 / 0,279 | 44,9686 / 46,8504 | 13.312,2786 / 15.453,0829 | 83 |
| 2022–2026 | 403 / 322 | 17,7419 / 15,73 | 14,032 / 14,7375 | 1,6305 / 1,6564 | 0,2507 / 0,1915 | 44,665 / 42,8571 | 38.619,8991 / 37.785,1919 | 113 |

Equity portata avanti; picco locale per drawdown. Trade chiusi assegnati alla data di uscita, scarti alla data del segnale.

## Backtest indipendenti dei quattro periodi

| Periodo | Trades B / F | CAGR % B / F | Max DD % B / F | PF netto B / F | Net Exp R B / F | Win Rate % B / F | Net PnL B / F | Scarti |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2010-2013 | 306 / 248 | 7,0649 / 11,9244 | 11,4337 / 8,3366 | 1,2393 / 1,5105 | 0,1859 / 0,2258 | 43,9344 / 44,5344 | 3129,9637 / 5673,4088 | 84 |
| 2014-2017 | 374 / 271 | 11,9182 / 9,7928 | 14,4472 / 14,6396 | 1,4435 / 1,5078 | -0,0423 / 0,1207 | 39,0374 / 42,0664 | 5669,9221 / 4516,1225 | 146 |
| 2018-2021 | 319 / 250 | 12,6899 / 11,7612 | 15,9639 / 13,7445 | 1,4621 / 1,5543 | 0,1731 / 0,2364 | 44,6541 / 46,5863 | 6115,9276 / 5591,9033 | 91 |
| 2022-2026 | 399 / 311 | 16,4353 / 12,8771 | 14,288 / 14,666 | 1,5789 / 1,5749 | 0,2002 / 0,1281 | 44,3609 / 42,7653 | 10.380,2806 / 7625,5175 | 125 |

Ogni finestra riparte da 10.000 e dal warm-up della sua prima barra. Trades indica posizioni eseguite, anche eventuali OPEN a fine finestra; PF, Win Rate e Net Expectancy usano solo chiusi. Il numero di scarti cambia rispetto alle finestre continue per il nuovo warm-up. Non sono out-of-sample. CAGR peggiora in tre finestre indipendenti su quattro: il miglioramento aggregato ? fragile.

## Rendimenti annuali

| Anno | Return % Baseline | Return % Filtered | Differenza pp | Net PnL Baseline | Net PnL Filtered | Scarti |
| --- | --- | --- | --- | --- | --- | --- |
| 2010 | 13,8787 | 25,9255 | 12,0468 | 1387,8662 | 2592,5455 | 38 |
| 2011 | 1,2981 | 0,2025 | -1,0956 | 147,8269 | 25,5003 | 9 |
| 2012 | 6,0394 | 12,7037 | 6,6643 | 696,683 | 1602,9599 | 18 |
| 2013 | 7,3378 | 10,2131 | 2,8753 | 897,5877 | 1452,4031 | 19 |
| 2014 | 11,9813 | 16,4249 | 4,4436 | 1573,1388 | 2574,3441 | 32 |
| 2015 | 8,0834 | 1,0132 | -7,0702 | 1188,5101 | 184,8939 | 25 |
| 2016 | 7,0815 | 5,5422 | -1,5393 | 1125,3647 | 1021,5717 | 26 |
| 2017 | 19,2584 | 18,2554 | -1,003 | 3277,2023 | 3551,4461 | 48 |
| 2018 | 1,4368 | 1,1205 | -0,3163 | 291,5868 | 257,7859 | 13 |
| 2019 | 16,9176 | 19,329 | 2,4114 | 3482,6238 | 4496,5897 | 33 |
| 2020 | 23,1764 | 18,6975 | -4,4789 | 5578,1925 | 5190,4399 | 26 |
| 2021 | 13,3569 | 16,7168 | 3,3599 | 3959,8755 | 5508,2674 | 11 |
| 2022 | 1,2829 | 2,6959 | 1,413 | 431,1385 | 1036,8231 | 4 |
| 2023 | 12,1171 | 11,3595 | -0,7575 | 4124,3551 | 4486,5129 | 47 |
| 2024 | 48,0842 | 38,6213 | -9,4629 | 18.349,8594 | 16.986,4392 | 35 |
| 2025 | 13,04 | 11,2006 | -1,8394 | 7369,1343 | 6828,8641 | 14 |
| 2026 | 13,064 | 12,4585 | -0,6055 | 8345,4119 | 8446,5526 | 13 |

## Simbolo / Pattern / Direzione

| Symbol | Pattern | Direction | Trades B / F | Scarti | Win Rate % B / F | PF netto B / F | Net PnL B / F | Net Expectancy R B / F |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AAPL | BULLISH_ENGULFING | LONG | 124 / 90 | 44 | 44,3548 / 45,5556 | 1,7578 / 1,6572 | 7961,8544 / 5908,4899 | 0,2731 / 0,2459 |
| GOOGL | BULLISH_HARAMI | LONG | 143 / 120 | 26 | 48,2517 / 45 | 1,4832 / 1,447 | 4463,0137 / 4213,0872 | 0,0725 / 0,0108 |
| NFLX | HAMMER | LONG | 181 / 122 | 72 | 41,989 / 45,082 | 1,3247 / 1,3973 | 5334,472 / 5252,5795 | 0,0963 / 0,1866 |
| NVDA | PIERCING_PATTERN | LONG | 152 / 114 | 48 | 38,8158 / 38,5965 | 1,668 / 1,694 | 13.031,9497 / 12.386,9049 | 0,2427 / 0,2737 |
| QQQ | HAMMER | LONG | 173 / 135 | 58 | 41,6185 / 43,7037 | 1,9251 / 2,1596 | 7549,0845 / 8345,5342 | 0,2233 / 0,2947 |
| SPY | BULLISH_HARAMI | LONG | 108 / 93 | 24 | 38,8889 / 40,8602 | 1,2028 / 1,3183 | 1142,5728 / 1675,4825 | 0,0716 / 0,1277 |
| TSLA | BULLISH_ENGULFING | LONG | 124 / 105 | 28 | 58,0645 / 56,1905 | 1,7436 / 1,7665 | 10.908,2171 / 10.494,5031 | 0,1121 / 0,0675 |
| TSLA | SHOOTING_STAR | SHORT | 122 / 110 | 19 | 29,5082 / 31,8182 | 1,3104 / 1,5961 | 5365,9293 / 10.262,5497 | 0,1628 / 0,2949 |
| XLK | HAMMER | LONG | 170 / 122 | 74 | 38,2353 / 39,3443 | 1,6762 / 1,8125 | 6095,0489 / 6195,7486 | 0,1343 / 0,2659 |
| XLY | BULLISH_ENGULFING | LONG | 106 / 97 | 18 | 56,6038 / 55,6701 | 1,0533 / 1,2079 | 374,2151 / 1509,06 | 0,1679 / 0,1617 |

## Interpretazione

Il risultato complessivo migliora equity, CAGR, drawdown, PF netto e Net Expectancy R, con meno trade e minori costi/esposizione. Il CAGR guadagna soltanto 0,3661 punti annui.

Gli SHORT TSLA aumentano il PnL di 4896,6204 (121,8798% del miglioramento totale); gli altri gruppi insieme cambiano di -879,0381. La quota dei profitti attribuita a TSLA LONG+SHORT sale da 26,1531% a 31,3343%. Sono attribuzioni dei trade realmente eseguiti con equity/sizing differenti, non stime degli esiti dei segnali scartati e non un backtest senza TSLA. AAPL e GOOGL peggiorano anche il PF netto.

Nel portafoglio continuo il CAGR peggiora nel 2014?2017 e 2022?2026; nei periodi indipendenti peggiora in tre finestre. Nel 2024 il rendimento annuale scende da 48,0842% a 38,6213%. Questi dati non dimostrano un miglioramento generalizzabile. Il filtro resta OFF per default; nessuna eccezione per simbolo e nessuna soglia ottimizzata ? stata introdotta.

## Verifica

- Maven `verify`: 500 test, 490 eseguiti senza errori, 10 esperimenti opzionali saltati.
- Esperimento offline richiesto: eseguito separatamente, PASS, con parit? esatta OFF e quattro vere simulazioni di periodo.
- Frontend: 74/74 PASS; build PASS.
- Package Maven finale con gli asset aggiornati: PASS.
- Nessuna modifica a V1, motore stable, scheduler PAPER, broker, ordini o dashboard PAPER. Nei test HTTP `placeOrder` ? verificato mai chiamato; l?esperimento offline non ha dipendenze da broker/provider.

## Artefatti

- [Excel baseline](V2_BASELINE_13_BARS.xlsx)
- [Excel filtered](V2_FILTERED_ADX14_25_13_BARS.xlsx)
- [Dati del confronto](comparison.json)
- [Architettura, default e metodologia](../../docs/PATTERN_V2_MARKET_REGIME_FILTER.md)

## Elenco completo dei file modificati o aggiunti

- `docs/PATTERN_V2_MARKET_REGIME_FILTER.md`
- `package.json`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/MarketRegimeAnalysis.java`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/MarketRegimeFilter.java`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/MarketRegimeFilterHttpJson.java`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/MarketRegimeFilterJson.java`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/MarketRegimeIndicators.java`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/PatternPortfolioSimulator.java`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Configuration.java`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Controller.java`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2ExcelExporter.java`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Run.java`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2RunComparison.java`
- `src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Service.java`
- `src/main/resources/static/pattern-v2-filter.js`
- `src/main/resources/static/pattern-v2-model.js`
- `src/main/resources/static/pattern-v2.html`
- `src/main/resources/static/pattern-v2.js`
- `src/test/java/it/andreademartini/trading/backtest/patternv2/MarketRegimeFilterExperimentTest.java`
- `src/test/java/it/andreademartini/trading/backtest/patternv2/MarketRegimeFilterTest.java`
- `src/test/java/it/andreademartini/trading/backtest/patternv2/PatternV2HttpIntegrationTest.java`
- `src/test/java/it/andreademartini/trading/backtest/patternv2/PatternV2Test.java`
- `src/test/js/pattern-v2-filter.test.js`
- `src/test/js/pattern-v2-regime.test.js`
- `src/test/js/pattern-v2.test.js`

Artefatti di ricerca aggiunti:

- `outputs/market-regime-filter-evidence/REPORT.md`
- `outputs/market-regime-filter-evidence/comparison.json`
- `outputs/market-regime-filter-evidence/V2_BASELINE_13_BARS.xlsx`
- `outputs/market-regime-filter-evidence/V2_FILTERED_ADX14_25_13_BARS.xlsx`

Run nuove e snapshot sono salvati dal repository V2 sotto `data/pattern-v2/runs/` e non sono versionati. I log di build/test sono in `outputs/market-regime-filter-*.log`. Nessun problema bloccante rimane; l?efficacia statistica generale resta non dimostrata.
