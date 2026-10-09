# V2_PATTERN_RANGE_2_5 - confronto controllato

Il filtro al 2,50% peggiora CAGR, Net PnL e Max Drawdown complessivi. PF netto ed Expectancy R migliorano, ma il CAGR migliora in un solo periodo su quattro e il drawdown peggiora in tre periodi. Questo singolo test non mostra un miglioramento stabile del portafoglio. Nessuna soglia o altro parametro è stato ottimizzato.

Baseline: `d5dbe9d6-4b6a-4af3-b407-364a3e0dce73`; filtrata: `b75f358a-3482-4256-ad06-63eb8d439471`. Dataset SHA-256: `741645928d0ece53a0ea2231fec80baca6551778353a9366e036766170555591`.

Configurazione: snapshot della V2 corrente, capitale 10.000 USD, risk 2%, nove simboli AAPL, GOOGL, NFLX, NVDA, QQQ, SPY, TSLA, XLK, XLY; DAILY dal 2010-01-01 al 2026-09-08. Pattern, Target R di ogni direzione, stop, entry, holding, sizing, maxOrderValue/maxQuantity e costi split-aware sono quelli dello snapshot iniziale. La sola differenza operativa è Enabled: OFF vs ON; entrambi gli snapshot salvano soglia 2,50.

## Confronto complessivo

| Metric | V2 Baseline | Range >= 2.5% | Difference |
| --- | --- | --- | --- |
| Final Equity | 61.061,9098 | 47.399,5015 | -13.662,4084 |
| Net PnL | 51.061,9098 | 37.399,5015 | -13.662,4084 |
| CAGR | 11,46 | 9,78 | -1,68 |
| Max Drawdown % | 19,2455 | 22,0863 | 2,8408 |
| CAGR / Max Drawdown | 0,5955 | 0,4428 | -0,1527 |
| Trades | 1290,00 | 595,00 | -695,00 |
| Win Rate | 41,7829 | 46,3866 | 4,6036 |
| PF Net | 1,6135 | 1,6818 | 0,0683 |
| Expectancy R | 0,3292 | 0,358 | 0,0287 |
| Average Winner R | 2,1603 | 1,9132 | -0,247 |
| Average Loser R | -0,9849 | -0,9877 | -0,0028 |
| Average Actual Risk % | 0,5417 | 0,966 | 0,4244 |
| Exposure % | 33,9928 | 18,1861 | -15,8067 |
| Capital Skips | 75,00 | 6,00 | -69,00 |
| Total Commission | 4235,52 | 2661,0323 | -1574,4877 |
| Total Slippage | 4131,2424 | 1926,9444 | -2204,2981 |
| Total Costs | 8366,7624 | 4587,9766 | -3778,7858 |

Difference = filtrata meno baseline. CAGR, drawdown, win rate, rischio ed esposizione sono percentuali; le rispettive differenze sono punti percentuali. PnL, equity e costi sono USD. Expectancy e Average Winner/Loser R usano il R di prezzo esistente, esclusi i costi. PF Net usa gli esiti dopo i costi. Net PnL complessivo include il non realizzato secondo il calcolatore esistente; in queste due run tutti i trade sono chiusi.

## Stabilità temporale

### 2010–2013

| Metric | Baseline | Filtrata | Difference |
| --- | --- | --- | --- |
| Trades | 250,00 | 128,00 | -122,00 |
| Win Rate % | 38,80 | 38,2813 | -0,5188 |
| PF Net | 1,1028 | 0,9607 | -0,1422 |
| Expectancy R | 0,2391 | 0,1776 | -0,0615 |
| Net PnL | 1472,3594 | -416,4536 | -1888,813 |
| CAGR % | 3,5033 | -1,0607 | -4,564 |
| Max Drawdown % | 19,2296 | 22,0863 | 2,8567 |

### 2014–2017

| Metric | Baseline | Filtrata | Difference |
| --- | --- | --- | --- |
| Trades | 324,00 | 116,00 | -208,00 |
| Win Rate % | 39,5062 | 53,4483 | 13,9421 |
| PF Net | 1,5613 | 2,0494 | 0,4881 |
| Expectancy R | 0,2431 | 0,538 | 0,2949 |
| Net PnL | 9223,1805 | 9406,9087 | 183,7281 |
| CAGR % | 15,9044 | 18,6597 | 2,7553 |
| Max Drawdown % | 19,2455 | 10,898 | -8,3475 |

### 2018–2021

| Metric | Baseline | Filtrata | Difference |
| --- | --- | --- | --- |
| Trades | 315,00 | 142,00 | -173,00 |
| Win Rate % | 44,127 | 47,1831 | 3,0561 |
| PF Net | 1,6673 | 1,7619 | 0,0946 |
| Expectancy R | 0,3838 | 0,3869 | 0,0031 |
| Net PnL | 15.765,5326 | 11.406,0452 | -4359,4874 |
| CAGR % | 15,2206 | 12,4882 | -2,7324 |
| Max Drawdown % | 13,6776 | 14,6142 | 0,9366 |

### 2022–2026

| Metric | Baseline | Filtrata | Difference |
| --- | --- | --- | --- |
| Trades | 401,00 | 209,00 | -192,00 |
| Win Rate % | 43,6409 | 46,89 | 3,2491 |
| PF Net | 1,8416 | 1,8352 | -0,0064 |
| Expectancy R | 0,4122 | 0,3489 | -0,0633 |
| Net PnL | 24.600,8373 | 17.003,0012 | -7597,8361 |
| CAGR % | 11,6362 | 9,9485 | -1,6877 |
| Max Drawdown % | 11,4071 | 12,7854 | 1,3783 |

Periodi del portafoglio continuo, senza reinizializzare il capitale. Net PnL del periodo = variazione dell'equity; CAGR calcolato sull'equity iniziale/finale del periodo; drawdown con picco locale. Metriche degli esiti assegnate per data di uscita.

## Filtro e gruppi di ricerca

Segnali prima del filtro: 1723; accepted: 705; rejected: 1018; acceptance rate: 40,917%.

| Group | Signals | Trades | Wins | Losses | Win Rate % | PF Net | Expectancy R | Net PnL |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ALL / BASELINE | 1723 | 1290 | 539 | 751 | 41,7829 | 1,6135 | 0,3292 | 51.061,9098 |
| ACCEPTED BY FILTER | 705 | 577 | 265 | 312 | 45,9272 | 1,738 | 0,3432 | 37.920,7736 |
| REJECTED BY FILTER | 1018 | 713 | 274 | 439 | 38,4292 | 1,4127 | 0,3179 | 13.141,1363 |

Questi gruppi classificano i trade realmente eseguiti dalla baseline secondo la soglia. I 577 trade accepted della baseline e i 595 trade della run filtrata sono insiemi diversi: eliminare segnali cambia disponibilità di capitale e posizioni aperte mantenendo identiche le regole. I 713 trade rejected della baseline hanno esiti affidabili; gli altri segnali rejected senza trade baseline hanno esito N/D e motivo dello scarto nel foglio audit. Nessun esito teorico indipendente è inventato.

## Results by Pattern Range - BASELINE

| Bucket | Signals | Trades | Win Rate % | PF Net | Expectancy R | Net PnL |
| --- | --- | --- | --- | --- | --- | --- |
| < 1.0% | 283 | 159 | 33,3333 | 1,4307 | 0,266 | 1714,0048 |
| 1.0 - 1.5% | 264 | 199 | 41,7085 | 1,7394 | 0,484 | 5611,663 |
| 1.5 - 2.0% | 241 | 182 | 39,011 | 1,2825 | 0,2563 | 2840,3645 |
| 2.0 - 2.5% | 230 | 173 | 38,7283 | 1,291 | 0,2394 | 2975,1039 |
| 2.5 - 3.0% | 160 | 129 | 44,9612 | 1,7131 | 0,386 | 5371,8778 |
| 3.0 - 4.0% | 218 | 177 | 45,7627 | 2,0252 | 0,4834 | 13.199,8584 |
| 4.0 - 5.0% | 126 | 106 | 42,4528 | 1,9882 | 0,2958 | 9380,8078 |
| >= 5.0% | 201 | 165 | 49,0909 | 1,4641 | 0,1899 | 9968,2296 |

## Results by Pattern Range - ACTUAL RUN

| Bucket | Signals | Trades | Win Rate % | PF Net | Expectancy R | Net PnL |
| --- | --- | --- | --- | --- | --- | --- |
| < 1.0% | 283 | 0 | N/D | N/D | N/D | N/D |
| 1.0 - 1.5% | 264 | 0 | N/D | N/D | N/D | N/D |
| 1.5 - 2.0% | 241 | 0 | N/D | N/D | N/D | N/D |
| 2.0 - 2.5% | 230 | 0 | N/D | N/D | N/D | N/D |
| 2.5 - 3.0% | 160 | 133 | 43,609 | 1,5225 | 0,357 | 4658,4374 |
| 3.0 - 4.0% | 218 | 183 | 45,9016 | 1,9354 | 0,4894 | 13.390,8014 |
| 4.0 - 5.0% | 126 | 107 | 43,9252 | 1,8096 | 0,3397 | 8397,356 |
| >= 5.0% | 201 | 172 | 50,5814 | 1,5155 | 0,2302 | 10.952,9067 |

## Risultati per simbolo/direzione - run filtrata

| Symbol | Direction | Average Range % | Winners % | Losers % | Rejected | Accepted | PF Net Accepted | Expectancy Accepted |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AAPL | LONG | 2,5318 | 4,3323 | 3,3167 | 56 | 35 | 1,872 | 0,4664 |
| GOOGL | LONG | 2,8163 | 3,5364 | 3,6312 | 76 | 87 | 1,3969 | 0,1593 |
| NFLX | LONG | 2,7318 | 3,9898 | 3,9161 | 127 | 95 | 1,4905 | 0,2776 |
| NVDA | LONG | 4,4886 | 5,3014 | 4,9171 | 32 | 151 | 1,8408 | 0,4627 |
| QQQ | LONG | 1,3413 | 5,6933 | 5,7963 | 224 | 12 | 3,3361 | 0,9626 |
| SPY | LONG | 1,7305 | 3,6401 | 3,1491 | 115 | 29 | 1,6677 | 0,2798 |
| TSLA | LONG | 5,6071 | 5,8573 | 5,8877 | 7 | 143 | 1,6283 | 0,1982 |
| TSLA | SHORT | 4,0548 | 5,095 | 4,6621 | 50 | 117 | 1,4937 | 0,4708 |
| XLK | LONG | 1,2578 | 3,4328 | 4,9279 | 228 | 11 | 1,2303 | 0,6893 |
| XLY | LONG | 1,9867 | 4,1908 | 3,1875 | 103 | 25 | 5,3751 | 0,6837 |

Media range su tutti i segnali rilevati; medie winner/loser sui trade effettivi chiusi della run filtrata. PF/expectancy Accepted sui trade effettivi, non su tutti i segnali. QQQ e XLK hanno rispettivamente solo 12 e 11 segnali accepted: i loro risultati non dimostrano da soli una soglia robusta.

## Copertura dei dati

| Symbol | First available in run | Last available in run |
| --- | --- | --- |
| AAPL | 2016-09-07T00:00:00Z | 2026-09-02T00:00:00Z |
| GOOGL | 2010-01-04T00:00:00Z | 2026-09-02T00:00:00Z |
| NFLX | 2010-01-04T00:00:00Z | 2026-09-02T00:00:00Z |
| NVDA | 2010-01-04T00:00:00Z | 2026-09-08T00:00:00Z |
| QQQ | 2010-01-04T00:00:00Z | 2026-09-08T00:00:00Z |
| SPY | 2010-01-04T00:00:00Z | 2026-09-08T00:00:00Z |
| TSLA | 2010-06-28T00:00:00Z | 2026-09-08T00:00:00Z |
| XLK | 2010-01-04T00:00:00Z | 2026-09-08T00:00:00Z |
| XLY | 2010-01-04T00:00:00Z | 2026-09-08T00:00:00Z |

CSV locali STOOQ, identici fra i due replay, senza download o sostituzioni. AAPL è disponibile solo dal settembre 2016; i periodi iniziali non includono suoi trade. Tutti i simboli sono mantenuti e le coperture diverse sono dichiarate. La configurazione corrente resta OFF; l'esperimento ON è salvato nella history.

## Verifiche

Parità filtro OFF con il motore V2 precedente: tutti i campi dei trade salvo ID, equity curve completa, metriche, cash e costi identici. Hash di 318 file sorgente esterni a V2 invariati, inclusi V1 e detector/componenti condivisi.

Maven/JUnit: 293 test, 0 fallimenti/errori, 2 opzionali saltati; esperimento opt-in: 1 test passato. Frontend: 43 test passati. Build backend e frontend completate. Evidenze nei log e in `off-parity.json`/`freeze-audit.json`.

## Artefatti

- [Excel filtrata](V2_PATTERN_RANGE_2_5.xlsx)
- [Excel baseline](V2_BASELINE.xlsx)
- [Confronto e analisi JSON](comparison.json)

Le run sono apribili nella pagina V2 con `?run=b75f358a-3482-4256-ad06-63eb8d439471` e `?run=d5dbe9d6-4b6a-4af3-b407-364a3e0dce73`. Il dataset esatto è salvato in `data/pattern-v2/datasets/741645928d0ece53a0ea2231fec80baca6551778353a9366e036766170555591.json`; `POST /api/backtest/pattern-v2/runs/{id}/replay` ripete la run da quel dataset. Le soglie 2,0% e 3,0% rimangono esperimenti manuali futuri.
