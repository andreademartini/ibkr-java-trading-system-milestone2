# Validazione PATTERN_V1 CORE

DEFAULT RESEARCH: i timeout restano contabilmente aperti. PnL e CAGR includono il loro mark-to-market e non misurano una strategia che liquida tutti i trade entro 20 barre.

Fonte: CSV STOOQ locali, caricati attraverso BacktestEngine e HistoricalDataService sostituito con una sorgente locale deterministica. Nessun ordine operativo.

Periodo richiesto: 2010-01-01 → 2026-09-08. Copertura effettiva: 2010-01-04 → 2026-09-08.

Capitale iniziale: 10000; risk per trade: 2%; no leverage; notional massimo ordine 5000; qty massima 1000; commissioni/slippage: 0.

Contabilità TIMEOUT: UNRESOLVED_MARK_TO_MARKET.

| Metrica | Valore |
|---|---:|
| Final Capital | 169447.7867 |
| Gross PnL | 159447.7867 |
| Net PnL | 159447.7867 |
| Return % | 1594.4779 |
| CAGR % | 18.4949 |
| Max Drawdown | 29991.8036 |
| Max Drawdown % | 33.7180 |
| Profit Factor netto (chiusi) | 1.2064 |
| Win Rate % (chiusi) | 34.8837 |
| Expectancy R (chiusi) | 0.2674 |
| Trades | 48 |
| Open Positions | 5 |
| Timeout | 5 |
| Same-bar ambiguity | 1 |
| Skipped overlap | 1063 |
| Skipped capital | 572 |

| Symbol | Direction | Pattern | Target R | Trades | PF netto | Expectancy R | Net PnL (incluso MTM) | Research PF R | V1 resolved PF R | Research Exp R | V1 resolved Exp R | Overlap | Capital |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| AAPL | LONG | BULLISH_ENGULFING | 3.0 | 0 | n/d | n/d | 0.0000 | 1.6154 | n/d | 0.4000 | n/d | 0 | 90 |
| GOOGL | LONG | BULLISH_HARAMI | 1.5 | 3 | 0.0000 | -1.0000 | -177.2750 | 1.4600 | 0.0000 | 0.2331 | -1.0000 | 0 | 149 |
| NFLX | LONG | HAMMER | 2.0 | 6 | 4.2163 | 1.0000 | 1.0202 | 1.4333 | 4.0000 | 0.2524 | 1.0000 | 0 | 202 |
| NVDA | LONG | PIERCING_PATTERN | 3.0 | 30 | 0.4786 | 0.2414 | 881.1945 | 1.4860 | 1.3500 | 0.3250 | 0.2414 | 152 | 0 |
| QQQ | LONG | HAMMER | 3.0 | 2 | n/d | 3.0000 | 43657.2394 | 1.5113 | n/d | 0.3400 | 3.0000 | 234 | 0 |
| SPY | LONG | BULLISH_HARAMI | 3.0 | 1 | 0.0000 | -1.0000 | -75.3183 | 1.2375 | 0.0000 | 0.1681 | -1.0000 | 1 | 129 |
| TSLA | LONG | BULLISH_ENGULFING | 1.0 | 0 | n/d | n/d | 0.0000 | 1.3387 | n/d | 0.1448 | n/d | 150 | 0 |
| TSLA | SHORT | SHOOTING_STAR | 4.0 | 1 | n/d | n/d | -2198.9600 | 1.3704 | n/d | 0.2759 | n/d | 166 | 0 |
| XLK | LONG | HAMMER | 4.0 | 2 | 0.0000 | -1.0000 | 98303.1680 | 1.5035 | 0.0000 | 0.3660 | -1.0000 | 236 | 1 |
| XLY | LONG | BULLISH_ENGULFING | 1.5 | 3 | 3.5072 | 0.2500 | 19056.7179 | 1.5804 | 1.5000 | 0.2826 | 0.2500 | 124 | 1 |

LONG: 47 trades; net PnL 161646.7467; PF 1.2064; expectancy R 0.2674.

SHORT: 1 trades; net PnL -2198.9600; PF n/d; expectancy R n/d.

Verificati 43 trade STOP/TARGET contro PatternOutcomeAnalyzer; prezzi di ingresso/stop/target e MFE/MAE verificati anche per gli irrisolti.

- AAPL: COVERAGE_START=2016-09-07
- TSLA: COVERAGE_START=2010-06-28
- GOOGL LONG: EXPECTANCY_DEGRADED_OVER_0.25R
- GOOGL LONG: PROFIT_FACTOR_DEGRADED_OVER_25_PERCENT
- SPY LONG: EXPECTANCY_DEGRADED_OVER_0.25R
- SPY LONG: PROFIT_FACTOR_DEGRADED_OVER_25_PERCENT
- XLK LONG: EXPECTANCY_DEGRADED_OVER_0.25R
- XLK LONG: PROFIT_FACTOR_DEGRADED_OVER_25_PERCENT
- TIMEOUT observations stay open and marked to market; no realized Result R. Signals remain blocked until end of run.
- MFE/MAE use complete daily exit candle, as Research; no intrabar ordering is inferred.
- Alphabetical symbol priority for same-date capital allocation; proceeds from intrabar exits available next date.

Confronto con il precedente file Pattern Research (periodo terminato 2026-09-22; quello attuale termina all'ultima barra CSV):

- AAPL LONG 3.0R: previous PF 1.55421687, previous expectancy 0.36507936; current resolved PF n/d, current resolved expectancy n/d.
- GOOGL LONG 1.5R: previous PF 1.46, previous expectancy 0.2331081; current resolved PF 0.0000, current resolved expectancy -1.0000.
- NFLX LONG 2.0R: previous PF 1.43333333, previous expectancy 0.25242719; current resolved PF 4.0000, current resolved expectancy 1.0000.
- NVDA LONG 3.0R: previous PF 1.48598131, previous expectancy 0.325; current resolved PF 1.3500, current resolved expectancy 0.2414.
- QQQ LONG 3.0R: previous PF 1.5112782, previous expectancy 0.34; current resolved PF n/d, current resolved expectancy 3.0000.
- SPY LONG 3.0R: previous PF 1.2375, previous expectancy 0.1681416; current resolved PF 0.0000, current resolved expectancy -1.0000.
- TSLA LONG 1.0R: previous PF 1.33870968, previous expectancy 0.14482758; current resolved PF n/d, current resolved expectancy n/d.
- TSLA SHORT 4.0R: previous PF 1.37037037, previous expectancy 0.27586205; current resolved PF n/d, current resolved expectancy n/d.
- XLK LONG 4.0R: previous PF 1.5035461, previous expectancy 0.3659794; current resolved PF 0.0000, current resolved expectancy -1.0000.
- XLY LONG 1.5R: previous PF 1.58035714, previous expectancy 0.2826087; current resolved PF 1.5000, current resolved expectancy 0.2500.
