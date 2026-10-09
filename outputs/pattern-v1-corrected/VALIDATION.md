# PATTERN_V1 corretto: mapping frozen

OLD 72ea9407-0a48-40f9-bd5c-3214bf360d03; NEW 8fcb5a08-8232-4a66-9873-0ad3417e37ee.

Replay delle stesse serie prezzi salvate in OLD, tramite BacktestEngine e HistoricalDataService mock deterministico. SHA256: f7f4c2ce464036c3b4a072e301d86251cda2c948d5a3b3ff6c11b9faddef8d08. Nessun download, nessun ordine operativo.

Periodo richiesto 2010-01-01 → 2026-09-08; effettivo 2010-01-04 → 2026-09-08.

Capitale 10000; rischio target 2%; max order 5000; max position 10000; max qty 1000; no leverage; costi identici a OLD.

Entry = holding bar 1; TIMEOUT al Close della holding bar 20 (entryIndex+19). STOP/TARGET hanno priorità nella stessa barra.

| METRIC | OLD | NEW |
|---|---:|---:|
| Final Equity | 265960.7415 | 60161.3164 |
| Return % | 2559.6074 | 501.6132 |
| CAGR % | 21.7419 | 11.3608 |
| Max Drawdown | 54501.1730 | 3439.6027 |
| Max Drawdown % | 31.3693 | 15.0008 |
| Profit Factor PnL | 1.4556 | 1.7431 |
| Expectancy R | 0.2614 | 0.3259 |
| Win Rate % | 34.0909 | 41.5575 |
| Closed Trades | 44.0000 | 1374.0000 |
| Open Trades | 4.0000 | 0.0000 |
| Timeouts | 4.0000 | 111.0000 |
| Capital Skips | 910.0000 | 28.0000 |
| Position Open Skips | 761.0000 | 304.0000 |
| Exposure % (OLD entry notional; NEW EOD MV) | 32.3466 | 30.0624 |
| Time In Market % (OLD reconstructed) | 99.5709 | 83.1704 |

OLD values from the stored JSON. Exposure definitions differ; OLD time in market reconstructed from trade intervals.

## Portfolio Summary

Stato del DB verificato tramite GET /api/historical/{symbol}/status: ultima data CORE 2026-09-08, coincide con le serie OLD. Copertura in database-coverage.json.

- trades: 1374.0000
- wins: 571.0000
- losses: 803.0000
- timeouts: 111.0000
- grossPnl: 50161.3164
- netPnl: 50161.3164
- winRate: 0.4156
- profitFactor: 1.7431
- expectancyR: 0.3259
- averageR: 0.3259
- averageWinner: 206.0669
- averageLoser: -84.0634
- mfe: 2.7729
- mae: 1.4914
- mfeR: 1.2868
- maeR: 0.7456
- ambiguities: 37.0000
- skippedPositionOpen: 304.0000
- skippedCapital: 28.0000
- closedTrades: 1374.0000
- openTrades: 0.0000
- breakevens: 0.0000
- signalsDetected: 1775.0000
- realizedGrossPnl: 50161.3164
- realizedNetPnl: 50161.3164
- unrealizedPnl: 0.0000
- netUnrealizedPnl: 0.0000
- profitFactorR: 1.5642
- profitFactorNet: 1.7431
- averageWinnerR: 2.1738
- averageLoserR: -0.9882
- averageActualRiskPercent: 0.4029
- averageOrderValue: 4344.6641
- maxDrawdown: 3439.6027
- weightedExpectancyR: 0.3259
- Final Cash: 60161.3164
- Long MV: 0.0000
- Short MV liability: 0.0000
- Final Equity: 60161.3164

## Symbol Breakdown

| Symbol | Direction | Pattern | R | Signals | Trades | Closed | Open | Timeouts | PF PnL | Exp R | Realized net PnL | Unrealized net PnL | Actual risk avg % | Open skips | Capital skips |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| AAPL | LONG | BULLISH_ENGULFING | 3.0 | 143 | 124 | 124 | 0 | 15 | 1.5722 | 0.3747 | 4177.0489 | 0.0000 | 0.4709 | 16 | 2 |
| GOOGL | LONG | BULLISH_HARAMI | 1.5 | 163 | 139 | 139 | 0 | 4 | 1.5001 | 0.1880 | 2924.2107 | 0.0000 | 0.3959 | 11 | 3 |
| NFLX | LONG | HAMMER | 2.0 | 222 | 180 | 180 | 0 | 2 | 1.4403 | 0.2699 | 4573.6927 | 0.0000 | 0.4466 | 24 | 4 |
| NVDA | LONG | PIERCING_PATTERN | 3.0 | 183 | 150 | 150 | 0 | 16 | 2.0175 | 0.3908 | 8839.8265 | 0.0000 | 0.3538 | 30 | 0 |
| QQQ | LONG | HAMMER | 3.0 | 236 | 165 | 165 | 0 | 16 | 2.2689 | 0.4118 | 5990.4355 | 0.0000 | 0.2785 | 60 | 1 |
| SPY | LONG | BULLISH_HARAMI | 3.0 | 144 | 108 | 108 | 0 | 14 | 1.7869 | 0.2905 | 2476.8101 | 0.0000 | 0.2289 | 19 | 5 |
| TSLA | LONG | BULLISH_ENGULFING | 1.0 | 150 | 121 | 121 | 0 | 3 | 1.6942 | 0.1552 | 6038.0588 | 0.0000 | 0.7935 | 27 | 1 |
| TSLA | SHORT | SHOOTING_STAR | 4.0 | 167 | 120 | 120 | 0 | 8 | 1.5372 | 0.2846 | 5389.8355 | 0.0000 | 0.4888 | 34 | 3 |
| XLK | LONG | HAMMER | 4.0 | 239 | 159 | 159 | 0 | 25 | 2.0920 | 0.5466 | 5512.0674 | 0.0000 | 0.2493 | 69 | 5 |
| XLY | LONG | BULLISH_ENGULFING | 1.5 | 128 | 108 | 108 | 0 | 8 | 2.1668 | 0.2665 | 4239.3304 | 0.0000 | 0.3868 | 14 | 4 |

## LONG vs SHORT

- LONG: signals 1608, trades 1254, wins 538, losses 716, BE 0, WR 42.9027%, PF 1.7790, Exp 0.3298R, realized net PnL 44771.4810, marked contribution DD 2843.5910.
- SHORT: signals 167, trades 120, wins 33, losses 87, BE 0, WR 27.5000%, PF 1.5372, Exp 0.2846R, realized net PnL 5389.8355, marked contribution DD 2118.2690.

## I quattro vecchi TIMEOUT

Verifica isolata degli stessi ingressi. PnL a quantità OLD per isolare il timeout; nella nuova run completa esecuzione/quantità possono cambiare per overlap e capitale.

| Symbol | Entry Date | Timeout Date | Timeout Exit Price | Holding Bars | OLD Qty | Corrected PnL (OLD Qty) | Result R | NEW full run |
|---|---|---|---:|---:|---:|---:|---:|---|
| XLK | 2010-03-05 | 2010-04-01 | 9.4363 | 20 | 550 | 193.8860 | 2.2647 | TIMEOUT 2010-04-01 qty=550 PnL=193.8860 |
| QQQ | 2010-05-07 | 2010-06-04 | 39.3821 | 20 | 65 | -63.8885 | -0.3150 | TIMEOUT 2010-06-04 qty=65 PnL=-63.8885 |
| AAPL | 2010-05-24 | 2010-06-21 | 8.0956 | 20 | 354 | 243.2122 | 1.4419 | TIMEOUT 2010-06-21 qty=422 PnL=289.9309 |
| NVDA | 2013-01-17 | 2013-02-14 | 0.2938 | 20 | 4 | 0.0554 | 2.7216 | TIMEOUT 2013-02-14 qty=1000 PnL=13.8610 |

## Actual risk example

XLK 2010-01-06: equity 10000.0000, target risk 2.0000%, desired risk 200.0000, desired qty 3589, final qty 525, limits [MAX_QUANTITY, MAX_ORDER_VALUE], order 4991.9573, actual risk 29.2530 = 0.2925%.

## Coerenza e anomalie

Riconciliazione segnali: 1775 = 1374 eseguiti + 304 overlap + 28 capitale + 69 piani non validi (68 INVALID_RISK_DISTANCE e uno INVALID_TARGET_PRICE). I dettagli sono negli eventi JSON.

Caso dati da verificare senza correggerlo automaticamente: TSLA SHOOTING_STAR 2021-03-04 High=291,313, entry open 2021-03-05=208,687; risk=82,626 e target SHORT 4R=-121,817. Ordine scartato con INVALID_TARGET_PRICE. Nessuna modifica alla fonte, ai pattern o alla mapping. Il solo OHLC non dimostra che il dato sorgente sia errato.

Verificati 1374 chiusi contro PatternOutcomeAnalyzer con l'opzione close-timeout già esistente. Entry/stop/target/exit/Result R identici; raw MFE/MAE identici anche per gli aperti. Detector e Research Engine invariati.

AAPL/GOOGL/NFLX terminano 2026-09-02, altri 2026-09-08; TSLA inizia 2010-06-28. Date portfolio: unione delle sessioni. Per simboli senza barra si usa l'ultima quotazione nota, senza look-ahead.

Il confronto Research PF R usa STOP/TARGET; il PF PnL portfolio include TIMEOUT chiusi. Desired qty/actual risk sono rischio prezzo; il servizio standard riserva costi, eventuale ulteriore limitazione è OTHER.

- TSLA: COVERAGE_START=2010-06-28
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-10 desired=11877 final=39
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-13 desired=16211 final=6
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-26 desired=4310 final=8
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-27 desired=2059 final=7
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-29 desired=101 final=6
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-30 XLK qty=2 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-30 desired=3073 final=2
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-35 desired=2383 final=362
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-44 XLK qty=3 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-44 desired=1557 final=3
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-51 desired=1899 final=29
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-52 desired=383 final=5
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-54 QQQ qty=4 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-54 desired=616 final=4
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-57 desired=538 final=41
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-58 TSLA qty=3 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-58 desired=4852 final=3
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-60 desired=1918 final=215
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-64 desired=1487 final=198
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-70 desired=1361 final=145
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-71 desired=648 final=46
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-72 NFLX qty=4 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-72 desired=4783 final=4
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-79 desired=168 final=25
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-80 NFLX qty=3 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-80 desired=15291 final=3
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-81 desired=1348 final=322
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-89 desired=4408 final=534
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-96 desired=1743 final=361
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-97 desired=2630 final=264
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-98 desired=1144 final=263
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-99 desired=54301 final=19
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-100 desired=989 final=204
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-109 desired=613 final=32
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-113 desired=9773 final=38
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-117 desired=452 final=37
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-127 desired=604 final=62
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-133 desired=203 final=11
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-143 desired=250 final=48
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-146 desired=9715 final=215
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-162 desired=7940 final=971
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-163 desired=3054 final=119
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-167 desired=1780 final=395
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-213 desired=601 final=87
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-220 desired=2573 final=78
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-250 desired=181 final=21
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-252 desired=1209 final=201
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-253 NFLX qty=1 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-253 desired=9903 final=1
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-259 desired=70527 final=27
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-272 desired=9054 final=142
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-273 desired=2024 final=143
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-274 desired=1334 final=119
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-297 desired=511 final=25
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-299 desired=323 final=41
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-300 XLK qty=4 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-300 desired=2465 final=4
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-306 desired=1179 final=143
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-307 NFLX qty=3 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-307 desired=3830 final=3
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-323 desired=2746 final=263
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-324 TSLA qty=1 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-324 desired=349 final=1
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-328 desired=214 final=28
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-329 desired=300 final=9
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-335 TSLA qty=3 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-335 desired=1232 final=3
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-339 QQQ qty=3 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-339 desired=1398 final=3
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-342 desired=679 final=27
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-343 desired=41240 final=931
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-344 desired=804 final=25
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-376 desired=3298 final=273
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-394 desired=1450 final=85
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-405 desired=433 final=23
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-415 desired=639 final=76
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-417 QQQ qty=3 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-417 desired=777 final=3
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-459 desired=6392 final=213
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-461 desired=726 final=85
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-497 desired=21726 final=93
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-508 desired=638 final=14
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-509 TSLA qty=3 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-509 desired=766 final=3
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-515 desired=1288 final=5
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-521 desired=3106 final=153
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-522 desired=10431 final=10
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-530 desired=3418 final=80
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-532 desired=239 final=8
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-550 desired=1386 final=263
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-560 SPY qty=3 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-560 desired=426 final=3
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-561 desired=1231 final=16
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-562 desired=2108 final=46
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-569 desired=1206 final=137
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-576 desired=3089 final=22
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-599 desired=1148 final=50
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-613 desired=11261 final=75
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-635 desired=319 final=79
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-645 desired=695 final=56
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-669 TSLA qty=1 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-669 desired=502 final=1
- SMALL_QUANTITY: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-670 TSLA qty=1 reason=INSUFFICIENT_CAPITAL
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-670 desired=882 final=1
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-723 desired=1020 final=8
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-758 desired=478 final=29
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-792 desired=3856 final=104
- SIZE_STRONGLY_CAPITAL_CONSTRAINED: 8fcb5a08-8232-4a66-9873-0ad3417e37ee-793 desired=319 final=29
- Holding bar 1 is the entry bar, as Pattern Research; TIMEOUT closes at bar 20 Close. Only incomplete final windows remain OPEN.
- STOP_FIRST executable excursions use earlier bars, the known open and the fill; raw exit-candle excursions are reported separately.
- Alphabetical entry priority; intrabar exit proceeds fund orders from the next session. Entry equity uses known opens, never today's Close.
- PF PnL uses realized gross closed-trade PnL; PF net is separate. Win rate uses net wins/(wins+losses), breakevens excluded. Price R excludes costs.
