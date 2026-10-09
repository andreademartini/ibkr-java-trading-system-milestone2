# Validazione PATTERN_V1 CORE

SCENARIO DIAGNOSTICO: liquidazione al Close della ventesima barra. Non è attivato nella configurazione YAML. Pattern e Target R restano invariati.

Fonte: CSV STOOQ locali, caricati attraverso BacktestEngine e HistoricalDataService sostituito con una sorgente locale deterministica. Nessun ordine operativo.

Periodo richiesto: 2010-01-01 → 2026-09-08. Copertura effettiva: 2010-01-04 → 2026-09-08.

Capitale iniziale: 10000; risk per trade: 2%; no leverage; notional massimo ordine 5000; qty massima 1000; commissioni/slippage: 0.

Contabilità TIMEOUT: CLOSE_AT_LAST_CLOSE.

| Metrica | Valore |
|---|---:|
| Final Capital | 59967.8330 |
| Gross PnL | 49967.8330 |
| Net PnL | 49967.8330 |
| Return % | 499.6783 |
| CAGR % | 11.3392 |
| Max Drawdown | 3439.6027 |
| Max Drawdown % | 15.1179 |
| Profit Factor netto (chiusi) | 1.7623 |
| Win Rate % (chiusi) | 41.8045 |
| Expectancy R (chiusi) | 0.3340 |
| Trades | 1330 |
| Open Positions | 0 |
| Timeout | 107 |
| Same-bar ambiguity | 37 |
| Skipped overlap | 302 |
| Skipped capital | 22 |

| Symbol | Direction | Pattern | Target R | Trades | PF netto | Expectancy R | Net PnL (incluso MTM) | Research PF R | V1 resolved PF R | Research Exp R | V1 resolved Exp R | Overlap | Capital |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| AAPL | LONG | BULLISH_ENGULFING | 3.0 | 81 | 1.9700 | 0.4552 | 4286.3067 | 1.6154 | 1.5957 | 0.4000 | 0.3889 | 9 | 0 |
| GOOGL | LONG | BULLISH_HARAMI | 1.5 | 141 | 1.5004 | 0.1888 | 2925.6812 | 1.4600 | 1.3542 | 0.2331 | 0.1861 | 11 | 1 |
| NFLX | LONG | HAMMER | 2.0 | 178 | 1.4236 | 0.2673 | 4475.0693 | 1.4333 | 1.4510 | 0.2524 | 0.2614 | 25 | 5 |
| NVDA | LONG | PIERCING_PATTERN | 3.0 | 150 | 2.0178 | 0.3908 | 8850.0166 | 1.4860 | 1.4667 | 0.3250 | 0.3134 | 30 | 0 |
| QQQ | LONG | HAMMER | 3.0 | 163 | 2.2270 | 0.4045 | 5922.6148 | 1.5113 | 1.4545 | 0.3400 | 0.3061 | 60 | 3 |
| SPY | LONG | BULLISH_HARAMI | 3.0 | 109 | 1.7750 | 0.2849 | 2458.3836 | 1.2375 | 1.2090 | 0.1681 | 0.1489 | 19 | 4 |
| TSLA | LONG | BULLISH_ENGULFING | 1.0 | 120 | 1.7516 | 0.1482 | 6511.6920 | 1.3387 | 1.3400 | 0.1448 | 0.1453 | 27 | 2 |
| TSLA | SHORT | SHOOTING_STAR | 4.0 | 120 | 1.4438 | 0.3262 | 4723.2092 | 1.3704 | 1.3333 | 0.2759 | 0.2500 | 35 | 2 |
| XLK | LONG | HAMMER | 4.0 | 159 | 2.0882 | 0.5836 | 5515.0355 | 1.5035 | 1.4845 | 0.3660 | 0.3534 | 71 | 3 |
| XLY | LONG | BULLISH_ENGULFING | 1.5 | 109 | 2.1796 | 0.2549 | 4299.8241 | 1.5804 | 1.4706 | 0.2826 | 0.2376 | 15 | 2 |

LONG: 1210 trades; net PnL 45244.6237; PF 1.8241; expectancy R 0.3348.

SHORT: 120 trades; net PnL 4723.2092; PF 1.4438; expectancy R 0.3262.

Verificati 1223 trade STOP/TARGET contro PatternOutcomeAnalyzer; prezzi di ingresso/stop/target e MFE/MAE verificati anche per gli irrisolti.

- AAPL: COVERAGE_START=2016-09-07
- TSLA: COVERAGE_START=2010-06-28
- TIMEOUT and insufficient-future-data positions liquidated at last Close; Research leaves their exit/resultR null. Resolved comparison excludes both.
- MFE/MAE use complete daily exit candle, as Research; no intrabar ordering is inferred.
- Alphabetical symbol priority for same-date capital allocation; proceeds from intrabar exits available next date.

Confronto con il precedente file Pattern Research (periodo terminato 2026-09-22; quello attuale termina all'ultima barra CSV):

- AAPL LONG 3.0R: previous PF 1.55421687, previous expectancy 0.36507936; current resolved PF 1.5957, current resolved expectancy 0.3889.
- GOOGL LONG 1.5R: previous PF 1.46, previous expectancy 0.2331081; current resolved PF 1.3542, current resolved expectancy 0.1861.
- NFLX LONG 2.0R: previous PF 1.43333333, previous expectancy 0.25242719; current resolved PF 1.4510, current resolved expectancy 0.2614.
- NVDA LONG 3.0R: previous PF 1.48598131, previous expectancy 0.325; current resolved PF 1.4667, current resolved expectancy 0.3134.
- QQQ LONG 3.0R: previous PF 1.5112782, previous expectancy 0.34; current resolved PF 1.4545, current resolved expectancy 0.3061.
- SPY LONG 3.0R: previous PF 1.2375, previous expectancy 0.1681416; current resolved PF 1.2090, current resolved expectancy 0.1489.
- TSLA LONG 1.0R: previous PF 1.33870968, previous expectancy 0.14482758; current resolved PF 1.3400, current resolved expectancy 0.1453.
- TSLA SHORT 4.0R: previous PF 1.37037037, previous expectancy 0.27586205; current resolved PF 1.3333, current resolved expectancy 0.2500.
- XLK LONG 4.0R: previous PF 1.5035461, previous expectancy 0.3659794; current resolved PF 1.4845, current resolved expectancy 0.3534.
- XLY LONG 1.5R: previous PF 1.58035714, previous expectancy 0.2826087; current resolved PF 1.4706, current resolved expectancy 0.2376.
