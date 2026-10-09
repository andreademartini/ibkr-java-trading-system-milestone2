# MARKET REGIME ANALYSIS — risultati descrittivi

## Uso nell'interfaccia

Aprire `/pattern-v2.html`. In GLOBAL PARAMETERS, sotto **Analysis only**, selezionare **Enable Market Regime Analysis** e avviare una run. La checkbox è OFF per default ed è distinta dai filtri esistenti. Il tab **MARKET REGIME ANALYSIS** mostra overview, classificazione secondaria, confronto ADX winners/losers, fasce ADX e grafici, direzione DI ed EMA, combinazioni simbolo/pattern/direzione/regime/periodo, diagnostica perdite e stabilità storica. La tabella dettagliata è filtrabile. Gli OPEN e i segnali scartati sono separati dagli esiti dei trade chiusi.

Aprendo la run **V2_MARKET_REGIME_ANALYSIS_11_BARS** dalla Run History si possono vedere i risultati già calcolati. L'Excel completo è `V2_MARKET_REGIME_ON.xlsx` in questa directory. Per un nuovo esperimento sui dati esatti, usare la baseline senza filtri e il comando già presente **RUN ON BASELINE DATA**, con parametri di trading identici e analisi ON. Non occorre e non viene creato alcun filtro ADX/EMA.

## Baseline e parità

Run originale: `d92eea67-e743-4be1-bfbe-6da19e129d20`, dataset `190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd`. Timeout V2 attuale **11 barre**, senza override per simbolo. Periodo dati 2010–2026-09-08; segnali dei trade eseguiti 2010-01-05–2026-08-31.

Capitale 10.000, sizing ordine 30% CURRENT_EQUITY_PERCENT, limiti posizione/LONG/SHORT 100% CURRENT_EQUITY_PERCENT, rischio 2%, no leva. Range, CloseStrength e Bollinger ATR OFF. Simboli, pattern, direzioni, Target R, commissioni e slippage sono quelli della run salvata. Nessuna ottimizzazione. Il file `parity.json` contiene l'ID della nuova run ON persistita e le verifiche.

| Metrica | OFF e ON |
|---|---:|
| Trade chiusi | 1.422 |
| Winners / Losers | 615 / 807 |
| Capitale finale | 62.191,3582244565 |
| PnL netto | 52.191,3582244565 |
| CAGR | 11,5825897871% |
| Max Drawdown | 15,9603159073% |
| PF netto | 1,4931407675 |
| Capital Skips | 40 |

Con identico Run ID sono stati confrontati esattamente l'intero portfolio serializzato, tutte le Execution, i costi e l'analisi precedente. Questo include date, prezzi, quantità, stop, target, PnL ed equity completa. La nuova run persistita ha un nuovo ID e identificativi dei trade conseguentemente diversi; summary ed equity completa restano identici. La run originale e la configurazione corrente V2 non vengono riscritte. La V1 congelata non viene modificata.

## Regimi principali

| Regime alla data del segnale | Trade | Losers | Loss Rate | PF netto | Expectancy Net R |
|---|---:|---:|---:|---:|---:|
| SIDEWAYS / WEAK TREND | 545 | 314 | 57,61% | 1,519 | 0,132 |
| TRANSITION | 276 | 164 | 59,42% | 1,240 | 0,073 |
| STRONG UPTREND | 273 | 156 | 57,14% | 1,486 | 0,160 |
| STRONG DOWNTREND | 57 | 32 | 56,14% | 1,459 | 0,003 |
| MIXED / UNCLASSIFIED | 205 | 108 | 52,68% | 1,788 | 0,260 |
| INSUFFICIENT_DATA | 66 | 33 | 50,00% | 1,536 | 0,207 |

La debole direzionalità raccoglie il maggior **numero** di perdite (314), ma anche il maggior campione. TRANSITION ha il maggiore **tasso** di perdita tra i regimi classificabili. Non sono la stessa osservazione. Tutti i regimi classificabili hanno PF netto aggregato >1: un elevato tasso di stop non equivale automaticamente a perdita di redditività.

La classificazione principale richiede EMA200: 66 trade non hanno tutti gli indicatori. Soltanto 12 trade mancano di ADX; gli altri valori disponibili restano esportati e utilizzabili nella classificazione secondaria. Il warm-up non elimina alcun trade.

## ADX, trend e direzione del trade

ADX medio winners **24,7786** (611 osservazioni), losers **23,9739** (799 osservazioni). La differenza descrittiva di circa 0,80 non è una prova di separazione statistica o causalità. Non è stata eseguita una ricerca della soglia ottimale.

| Fascia ADX | Trade | Winners | Losers | PF netto | Expectancy Net R |
|---|---:|---:|---:|---:|---:|
| <15 | 189 | 80 | 109 | 1,588 | 0,121 |
| 15–<20 | 367 | 156 | 211 | 1,489 | 0,137 |
| 20–<25 | 288 | 119 | 169 | 1,260 | 0,076 |
| 25–<30 | 245 | 111 | 134 | 1,422 | 0,230 |
| 30–<40 | 218 | 91 | 127 | 1,483 | 0,083 |
| >=40 | 103 | 54 | 49 | 2,444 | 0,375 |

| Direzione e relazione DI con ADX>=25 | Trade | Losers | Loss Rate | PF netto | Expectancy Net R |
|---|---:|---:|---:|---:|---:|
| LONG WITH_TREND | 292 | 165 | 56,51% | 1,435 | 0,125 |
| LONG COUNTER_TREND | 233 | 115 | 49,36% | 1,976 | 0,325 |
| SHORT WITH_TREND | 12 | 11 | 91,67% | 0,099 | -0,720 |
| SHORT COUNTER_TREND | 29 | 19 | 65,52% | 1,484 | 0,320 |

In questo dataset i LONG contro direzione DI ribassista forte **non** perdono più frequentemente dei LONG con direzione DI rialzista forte. Questa è un'associazione osservata, non una regola prospettica: simbolo, pattern, volatilità, periodo, Target R e sizing possono spiegare parte delle differenze. I gruppi SHORT hanno 12 e 29 trade: campioni piccoli e provenienti dalla configurazione SHORT effettivamente attiva, non evidenza generalizzabile ad altri titoli.

Il confronto EMA50/EMA200 è separato: WITH_TREND 1.065 trade, PF 1,381; COUNTER_TREND 291 trade, PF 1,841. Non confondere questi gruppi con i gruppi DI condizionati da ADX>=25.

## Esempi per simbolo e pattern

QQQ/HAMMER LONG in strong downtrend ha soltanto **4 trade**, 2 perdite: insufficiente per affermare che l'Hammer perda soprattutto in quel regime. In strong uptrend: 34 trade, 23 perdite, PF 1,111, Net R medio -0,043. In weak trend: 74 trade, 42 perdite, PF 1,453, Net R medio 0,187.

TSLA/BULLISH_ENGULFING LONG: weak trend 51 trade, 24 perdite, PF 1,861; strong uptrend 18 trade, 7 perdite, PF 1,001; strong downtrend 10 trade, 3 perdite, PF 5,828. I campioni direzionali sono piccoli: il PF apparentemente elevato non basta a dimostrare robustezza.

PF in valuta e media dei Net R possono divergere perché cambiano quantità, rischio iniziale e peso monetario dei trade. Non vengono forzati a dare lo stesso giudizio.

## Perdite e stabilità

807 losers: **775 STOP_LOSS**, **30 TIMEOUT_LOSS**, **2 OTHER_LOSS**. Nel foglio LOSER_DIAGNOSTICS ogni trade mantiene regime/ADX alla data del segnale, distanza entry-stop divisa per ATR del segnale, Price R, Net R, holding, simbolo e pattern. I segnali totali sono 1.775: 1.422 EXECUTED, 237 SKIPPED_POSITION_OPEN, 40 SKIPPED_CAPITAL, 76 OTHER_SKIPPED. Nessun PnL inventato per gli scarti.

La Net Expectancy di TRANSITION per i quattro periodi è **-0,128 / -0,044 / +0,235 / +0,220**, su **62 / 75 / 52 / 87** trade. Una condizione aggregata meno favorevole non è rimasta negativa in tutti i periodi. Strong downtrend ha campioni **8 / 11 / 5 / 33**, troppo piccoli nei primi tre periodi per conclusioni solide.

Escludere descrittivamente tutti i trade TRANSITION toglierebbe dal campione 164 losers, ma anche **112 winners**, cioè il 18,21% dei winners totali. Questo NON simula un filtro operativo: eliminando trade cambierebbero allocazione, segnali eseguibili ed equity. Nessun risultato del portafoglio filtrato viene attribuito a questa semplice scomposizione.

Le ipotesi da verificare successivamente riguardano differenze per simbolo/pattern, variazione temporale e confondenti. Servirebbero validazione fuori campione e metodi che tengano conto della dipendenza tra trade. Nessun filtro o parametro viene modificato sulla base di queste osservazioni.

## Metodo e verifiche

Metodo dettagliato in `docs/MARKET_REGIME_ANALYSIS.md`. Valori ADX/DI confrontati con la serie pubblica e gli expected values della [suite ufficiale TA-Lib](https://github.com/TA-Lib/ta-lib/blob/main/src/tools/ta_regtest/ta_test_func/test_adx.c); tolleranze delle reference arrotondate 1e-4 per DI e 0,002 per ADX. EMA verificate contro serie lineari analitiche, ATR contro serie con gap e warm-up. Numeric equality del portfolio ON/OFF è esatta a pari identità, non una tolleranza scelta per nascondere differenze.

I file di prova includono log Maven/frontend, `parity.json`, `regime-summary.json`, Excel, manifest delle modifiche e hash di preservazione V1. Nessun ordine IBKR trasmesso. Verifica visuale Browser non disponibile nella sessione; l'interfaccia è verificata con HTTP e test frontend. I risultati sono evidenze descrittive, non una spiegazione causale del motivo per cui ciascun pattern perde.
