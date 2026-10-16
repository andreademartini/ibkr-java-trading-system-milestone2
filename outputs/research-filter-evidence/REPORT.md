# V3 Research: confronto esplorativo dei filtri

Dataset: `190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd`. Nessuna ottimizzazione automatica. Soglie candidate derivate da analisi già effettuate sugli stessi dati: **risultati esplorativi, non validazione out-of-sample**.

La regressione con i tre filtri OFF è esatta su portafoglio, trade, costi e gap sia DAILY sia WEEKLY. Gli hash dei file V1/V2, PAPER, ordini e configurazioni protette sono invariati.

Verifiche finali: 114 file protetti controllati, nessuna differenza; otto workbook generati e verificati. Maven verify finale: 632 test eseguiti, 19 opzionali esclusi, zero errori/fallimenti. La regressione storica opzionale è passata separatamente nel verify con il relativo flag. Passano anche 116 test frontend e il build frontend.

Configurazione economica comune: capitale 10.000, rischio teorico 4%, ordine 30% equity, max holding 13 sessioni DAILY, leva OFF, slippage 0,03% per lato, commissioni IBKR_US_FIXED. ADX filter OFF, Bollinger/ATR OFF, invariati fra i preset. Intervallo 2010-01-01–2026-09-08.

| Preset | CloseStrength | Pattern Range % | RVOL |
|---|---|---|---|
| BASELINE | OFF | OFF | OFF |
| TEST A — MODERATO | >= 0,906 | >= 1,38; massimo assente | OFF |
| TEST B — RESTRITTIVO | >= 0,906 | >= 3,63; massimo assente | OFF |

## Segnali DAILY; ingresso e gestione DAILY

| Metrica | Baseline | Test A | Test B |
|---|---:|---:|---:|
| Final Equity | 62.846,7229 | 26.961,6572 | 18.578,4318 |
| CAGR % | 11,6528 | 6,128 | 3,7842 |
| Max Drawdown % | 20,3086 | 16,096 | 15,8988 |
| Profit Factor Net | 1,4584 | 1,8999 | 2,0987 |
| Win Rate % | 43,133 | 51,5571 | 57,2917 |
| Closed Trades | 1398 | 289 | 96 |
| Average Net R | 0,131 | 0,36 | 0,3948 |
| Gap Stops | 103 | 15 | 7 |
| Gap Targets | 65 | 15 | 9 |
| Total Costs | 9412,6306 | 1742,4067 | 599,2904 |
| Average Exposure % | 35,8284 | 10,3594 | 3,6063 |
| Capital Skips | 46 | 0 | 0 |
| Negative Years | 3 | 5 | 4 |

| Audit | Baseline | Test A | Test B |
|---|---:|---:|---:|
| Raw Signals | 1775 | 1775 | 1775 |
| Accepted Signals | 1775 | 308 | 103 |
| Executed Signals | 1398 | 289 | 96 |

## Periodi storici DAILY

Portafogli continui, non backtest rifinanziati a ogni periodo. Trade attribuiti per uscita; drawdown e rendimenti dalle equity del rispettivo periodo.

| Periodo | Preset | Trade | Return % | CAGR % | Max DD % | PF Net | Net PnL |
|---|---|---:|---:|---:|---:|---:|---:|
| 2010–2013 | BASELINE | 304 | 15,5698 | 3,6941 | 17,155 | 1,1074 | 1556,976 |
| 2014–2017 | BASELINE | 375 | 57,3725 | 12,0123 | 17,1427 | 1,4078 | 6630,5218 |
| 2018–2021 | BASELINE | 318 | 61,0805 | 12,6669 | 20,3086 | 1,41 | 11.109,0063 |
| 2022–2026 | BASELINE | 401 | 114,5195 | 17,6952 | 16,355 | 1,578 | 33.550,2189 |
| 2010–2013 | A | 59 | 13,7208 | 3,2757 | 7,4292 | 1,4559 | 1372,085 |
| 2014–2017 | A | 75 | 18,6227 | 4,3649 | 8,5446 | 1,5449 | 2117,7838 |
| 2018–2021 | A | 71 | 13,5497 | 3,23 | 16,096 | 1,3274 | 1827,841 |
| 2022–2026 | A | 84 | 76,0162 | 12,8284 | 7,883 | 2,8208 | 11.643,9474 |
| 2010–2013 | B | 16 | 19,62 | 4,5934 | 4,379 | 5,423 | 1962,0049 |
| 2014–2017 | B | 19 | 13,7935 | 3,2854 | 3,7173 | 2,4274 | 1649,9848 |
| 2018–2021 | B | 25 | -0,0497 | -0,0124 | 15,8988 | 0,998 | -6,7606 |
| 2022–2026 | B | 36 | 36,5536 | 6,8768 | 5,1773 | 2,7876 | 4973,2026 |

File: [DAILY BASELINE](PATTERN_V3_DAILY_BASELINE.xlsx) · [DAILY A](PATTERN_V3_DAILY_A.xlsx) · [DAILY B](PATTERN_V3_DAILY_B.xlsx) · [DAILY RVOL](PATTERN_V3_DAILY_RVOL.xlsx).

## Segnali WEEKLY; ingresso e gestione DAILY

| Metrica | Baseline | Test A | Test B |
|---|---:|---:|---:|
| Final Equity | 22.837,7604 | 12.641,3807 | 11.958,5261 |
| CAGR % | 5,0768 | 1,4155 | 1,0783 |
| Max Drawdown % | 20,979 | 17,379 | 19,2566 |
| Profit Factor Net | 1,4688 | 1,381 | 1,3451 |
| Win Rate % | 53,4435 | 57,3913 | 56,4706 |
| Closed Trades | 363 | 115 | 85 |
| Average Net R | 0,1662 | 0,1828 | 0,1615 |
| Gap Stops | 19 | 9 | 4 |
| Gap Targets | 8 | 0 | 0 |
| Total Costs | 2053,664 | 518,0324 | 392,9222 |
| Average Exposure % | 22,2694 | 8,1823 | 6,279 |
| Capital Skips | 5 | 0 | 0 |
| Negative Years | 6 | 7 | 8 |

| Audit | Baseline | Test A | Test B |
|---|---:|---:|---:|
| Raw Signals | 398 | 398 | 398 |
| Accepted Signals | 398 | 118 | 88 |
| Executed Signals | 366 | 115 | 85 |

## Periodi storici WEEKLY

Portafogli continui, non backtest rifinanziati a ogni periodo. Trade attribuiti per uscita; drawdown e rendimenti dalle equity del rispettivo periodo.

| Periodo | Preset | Trade | Return % | CAGR % | Max DD % | PF Net | Net PnL |
|---|---|---:|---:|---:|---:|---:|---:|
| 2010–2013 | BASELINE | 95 | 9,28 | 2,2496 | 20,979 | 1,1358 | 927,9987 |
| 2014–2017 | BASELINE | 91 | 17,8628 | 4,1973 | 9,8874 | 1,4454 | 1952,0447 |
| 2018–2021 | BASELINE | 74 | 12,5292 | 2,9971 | 15,2922 | 1,2912 | 1613,7609 |
| 2022–2026 | BASELINE | 103 | 57,5691 | 10,1931 | 13,6653 | 1,7782 | 8343,9561 |
| 2010–2013 | A | 24 | -1,3812 | -0,348 | 12,7621 | 0,9264 | -138,1181 |
| 2014–2017 | A | 32 | -0,6171 | -0,1547 | 10,388 | 1,0225 | -60,8536 |
| 2018–2021 | A | 23 | 5,3485 | 1,312 | 6,7165 | 1,518 | 524,2099 |
| 2022–2026 | A | 36 | 22,4319 | 4,415 | 8,4654 | 1,8911 | 2316,1425 |
| 2010–2013 | B | 19 | -3,3041 | -0,8388 | 13,5992 | 0,804 | -330,4122 |
| 2014–2017 | B | 21 | -4,3151 | -1,0974 | 10,3702 | 0,7706 | -417,2476 |
| 2018–2021 | B | 15 | 2,0308 | 0,5042 | 6,4899 | 1,1873 | 187,8932 |
| 2022–2026 | B | 30 | 26,6762 | 5,1774 | 8,5326 | 2,3304 | 2518,2927 |

File: [WEEKLY BASELINE](PATTERN_V3_WEEKLY_BASELINE.xlsx) · [WEEKLY A](PATTERN_V3_WEEKLY_A.xlsx) · [WEEKLY B](PATTERN_V3_WEEKLY_B.xlsx) · [WEEKLY RVOL](PATTERN_V3_WEEKLY_RVOL.xlsx).

## Prova RVOL separata: limite dei dati

RVOL è testato separatamente con CloseStrength e Range OFF, periodo 20, minimo 1,20, massimo assente. Il dataset non certifica la base dei volumi. Con `relativeVolumeBasis=UNVERIFIED`, la run non presenta un rapporto non verificato come valido: RVOL è N/D e i segnali che richiedono il filtro sono rifiutati con diagnostica. Il risultato senza operazioni **non misura l’effetto economico di RVOL**.

| Timeframe | Pattern grezzi | RVOL disponibili | Trade chiusi | Equity | Diagnostica |
|---|---:|---:|---:|---:|---|
| DAILY | 1775 | 0 | 0 | 10.000 | CURRENT_VOLUME_NOT_AVAILABLE: 1; HISTORICAL_VOLUME_NOT_AVAILABLE: 1; RVOL_WARMUP: 7; UNVERIFIED_VOLUME_ADJUSTMENT: 1766 |
| WEEKLY | 398 | 0 | 0 | 10.000 | RVOL_WARMUP: 8; UNVERIFIED_VOLUME_ADJUSTMENT: 390 |

Dopo una verifica esterna della fonte, il controllo “Volume basis” permette di dichiarare `CONSISTENT_DATASET_UNITS`. È una dichiarazione dell’utente, registrata nell’hash e nella run, non una certificazione automatica del provider. Non è stata selezionata per rendere artificialmente eseguibile la prova storica. Nessun volume è stato ricostruito o corretto per split. Le settimane festive usano il volume totale delle sessioni effettive, senza normalizzarlo al numero di sessioni.

## Lettura dei risultati e validazione futura

DAILY: A/B aumentano PF ed expectancy netta per trade nel campione ma riducono fortemente il numero di operazioni, l’esposizione e il CAGR. WEEKLY non mostra lo stesso miglioramento del PF. Queste differenze non dimostrano un miglioramento futuro e non selezionano automaticamente un preset. I risultati cambiano anche attraverso equity, sizing e disponibilità di capitale nelle operazioni successive.

FILTER_ANALYSIS è `DESCRIPTIVE_ONLY — NOT A PORTFOLIO BACKTEST`: quartili, classi e combinazioni riguardano trade effettivamente eseguiti. Non sono equity simulate dei sottogruppi e non attribuiscono PnL ipotetico ai segnali rifiutati. I tre preset della tabella sopra sono invece simulazioni complete indipendenti, con la stessa configurazione economica.

Un successivo studio walk-forward deve definire finestre di training/validazione prima del test, scegliere le soglie solo nel training e valutare il periodo successivo senza riottimizzarlo retroattivamente. Poiché queste soglie sono già state esplorate sull’intero storico, non si può chiamare “incontaminato” un holdout già osservato. La conferma richiede anche dati futuri non usati per la selezione e un successivo periodo PAPER separato. Questo intervento non avvia PAPER né scheduler.

## Tracciabilità e verifiche

- [Dati completi, configurazioni, hash e RunId](comparison.json)
- [Manifest dei file protetti](baseline-manifest.json)
- [Rapporto tecnico completo](../../docs/V3_RESEARCH_FILTERS.md)

Gli otto workbook conservano tutti i fogli precedenti e GAP_EXIT_ANALYSIS. SIGNAL_FILTER_AUDIT contiene tutti i pattern grezzi, indicatori noti al segnale, tutti i rifiuti e la disposizione dell’ingresso. TRADES conserva i prezzi/costi/gap e aggiunge le metriche al segnale. FILTER_ANALYSIS include conteggi, dati mancanti e statistiche per LONG/SHORT, simbolo, pattern e periodo. Nessuna run operativa precedente è stata sovrascritta.
