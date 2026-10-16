# Confronto V3: segnali DAILY e WEEKLY, esecuzione DAILY

Dataset: `190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd`. Stessa configurazione economica, stessi dati e intervallo; cambia soltanto signalTimeframe.

Regressione DAILY integrale superata; V1 e V2 invariate.

Intervallo richiesto: 2010-01-01–2026-09-08; effettivo: 2010-01-04–2026-09-08.
Capitale iniziale 10000, rischio teorico 2%, timeout 13 sessioni DAILY.
Maven verify: 622 test eseguiti, 16 opzionali esclusi, zero errori/fallimenti.
Passano 111 test frontend e il build frontend. JAR aggiornato prodotto.

| Metrica | DAILY | WEEKLY |
|---|---:|---:|
| Final Equity | 63.596,2059 | 19.155,3955 |
| CAGR % | 11,7322 | 3,9747 |
| Max Drawdown % | 16,6632 | 15,1 |
| Profit Factor Net | 1,5122 | 1,5729 |
| Win Rate % | 43,8628 | 54,6468 |
| Closed Trades | 1108 | 269 |
| Average Holding Days | 6,8691 | 13,368 |
| Average Holding DAILY Sessions | 5,0072 | 10,1375 |
| Gap Stop Count | 77 | 12 |
| Gap Target Count | 54 | 7 |
| Total Costs | 8378,1734 | 1348,594 |
| Capital Skips | 23 | 2 |
| Average Exposure % | 28,7969 | 14,9259 |
| Worst Price R | -8,1111 | -2,94 |
| Worst Net R | -8,4335 | -3,1239 |

Average Holding Days indica giorni di calendario inclusivi; Average Holding DAILY Sessions indica barre DAILY, con ingresso come barra 1. Exposure è la media del controvalore lordo a fine giornata/equity. R di prezzo e Net R seguono le convenzioni esistenti.

## Operazioni e rendimento per anno

Le operazioni sono chiuse nell’anno; il rendimento deriva dall’equity continua, inclusi gli effetti delle posizioni a cavallo dell’anno. Il 2026 è parziale.

| Anno | Trade DAILY | Trade WEEKLY | Rendimento DAILY % | Rendimento WEEKLY % |
|---|---:|---:|---:|---:|
| 2010 | 53 | 9 | 26,4502 | 8,0701 |
| 2011 | 67 | 21 | -5,8665 | -11,6573 |
| 2012 | 66 | 22 | 9,816 | 10,4389 |
| 2013 | 61 | 8 | 6,7856 | -1,9828 |
| 2014 | 73 | 11 | 21,7246 | 1,2174 |
| 2015 | 58 | 25 | 2,1713 | 4,3251 |
| 2016 | 80 | 16 | 2,0998 | 5,2295 |
| 2017 | 74 | 10 | 19,1291 | 4,294 |
| 2018 | 72 | 15 | -0,4376 | -2,3654 |
| 2019 | 62 | 22 | 19,5978 | 15,1628 |
| 2020 | 53 | 14 | 16,9159 | 2,5724 |
| 2021 | 70 | 10 | 16,8072 | 0,7124 |
| 2022 | 78 | 19 | -0,3993 | 7,2218 |
| 2023 | 52 | 12 | 12,4481 | 19,3709 |
| 2024 | 70 | 16 | 37,8652 | -2,9632 |
| 2025 | 75 | 21 | 7,3692 | 1,3123 |
| 2026 | 44 | 18 | 11,7196 | 9,431 |

DAILY: 14 anni positivi, 3 negativi, 0 nulli (2026 parziale incluso).

WEEKLY: 13 anni positivi, 4 negativi, 0 nulli (2026 parziale incluso).

## Distribuzione per simbolo

| Gruppo | Chiusi DAILY | Chiusi WEEKLY | PnL netto realizzato DAILY | PnL netto realizzato WEEKLY |
|---|---:|---:|---:|---:|
| AAPL | 89 | 21 | 3624,5925 | 1463,1479 |
| GOOGL | 120 | 26 | 3730,6942 | 1568,3949 |
| NFLX | 121 | 16 | 3923,3408 | 2371,0714 |
| NVDA | 115 | 23 | 9556,6578 | 1132,3308 |
| QQQ | 135 | 29 | 7149,4495 | 593,1351 |
| SPY | 93 | 22 | 1355,6538 | 254,6321 |
| TSLA | 217 | 68 | 17.350,088 | -1044,6959 |
| XLK | 121 | 32 | 5111,066 | 2099,9856 |
| XLY | 97 | 32 | 1794,6632 | 923,7233 |

## Distribuzione per pattern

| Gruppo | Chiusi DAILY | Chiusi WEEKLY | PnL netto realizzato DAILY | PnL netto realizzato WEEKLY |
|---|---:|---:|---:|---:|
| BULLISH_ENGULFING|LONG | 292 | 82 | 15.074,6758 | 3506,5772 |
| BULLISH_HARAMI|LONG | 213 | 48 | 5086,348 | 1823,027 |
| HAMMER|LONG | 377 | 77 | 16.183,8563 | 5064,1921 |
| PIERCING_PATTERN|LONG | 115 | 23 | 9556,6578 | 1132,3308 |
| SHOOTING_STAR|SHORT | 111 | 39 | 7694,6679 | -2164,4019 |

## Interpretazione e limiti

WEEKLY non è presentata come una strategia migliore. Nel campione produce meno trade e minore esposizione media, con CAGR inferiore. Gli indicatori con gli stessi periodi numerici rappresentano orizzonti diversi: EMA20 WEEKLY descrive circa 20 settimane, non 20 giorni. Nessun parametro è stato ottimizzato.

Il calendario offline copre 2010–2028, con festività programmate ed eccezioni storiche note; eventuali nuove chiusure straordinarie richiedono un aggiornamento versionato. Le sessioni abbreviate contano come una sessione. Le settimane incomplete o con dati mancanti sono escluse, senza inventare OHLC. Le barre complete residue alimentano gli indicatori secondo il conteggio di barre già esistente.

Sono preservati i limiti del dataset adjusted, del catalogo split e del modello di slippage sui target descritti nel rapporto tecnico. Non sono ricostruiti prezzi raw o dividendi.

## File e verifiche

- [Excel DAILY](PATTERN_V3_DAILY.xlsx)
- [Excel WEEKLY](PATTERN_V3_WEEKLY.xlsx)
- [Dati completi del confronto](comparison.json)
- [Manifest della baseline e hash protetti](baseline-manifest.json)
- [Rapporto tecnico](../../docs/V3_SIGNAL_TIMEFRAME.md)

Le vecchie run non sono state sovrascritte. I backtest usano store temporanei e nessun oggetto broker. PAPER, ordini, configurazioni V1/V2 e prezzi storici non sono stati modificati. Backend operativo non riavviato.
