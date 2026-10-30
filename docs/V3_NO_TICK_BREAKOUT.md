# V3 Research 3.3 — rottura del massimo senza tick

Nuova regola per le run correnti:

- Segnale originale DAILY: tre chiusure consecutive decrescenti, filtro SMA200 e ATR al segnale.
- Open della quarta barra strettamente sotto High della terza.
- High della quarta barra strettamente sopra High della terza: il semplice tocco non basta.
- Prezzo base d'ingresso = High della terza barra; poi applicazione dello slippage BUY esistente.
- Validità limitata alla quarta sessione. Un mancato ingresso non viene rinviato alla quinta.

Non viene aggiunto alcun tick, epsilon o incremento della precisione CSV. Non serve più una prevalidazione dei tick. Stop ATR, RECOVERY SMA5, timeout, sizing, capitale, commissioni, slippage e politica conservativa intrabar sono invariati.

La pagina V3 non contiene più Minimum Tick Resolution, override, accettazioni research o pulsanti di tick audit. RUN avvia direttamente il backtest.

Configurazione effettiva:

    entryMode=FOURTH_BAR_HIGH_BREAKOUT_NO_TICK
    entryBufferMode=NONE
    entryBufferTicks=0
    entryValidityBars=1
    minimumTickResolutionPolicy=NOT_REQUIRED

Le vecchie proprietà tick restano nel modello di serializzazione soltanto per la compatibilità storica. Per le nuove run vengono normalizzate a valori non applicabili; non sono richieste, usate o esposte come controlli nella pagina. Le vecchie modalità restano riproducibili dove i loro dati lo consentono, senza riscrivere le run archiviate.

ENTRY_SIGNAL_AUDIT e TRADES conservano BuyStopTrigger come alias del livello di rottura: nella nuova modalità coincide con ThirdBarHigh. EntryTriggerReached indica High4 > High3. MinimumTick è N/A, non zero. Le nuove esportazioni non creano il foglio MINIMUM_TICK_AUDIT; rimangono Portfolio Summary, analisi ATR, profit space, risultati per simbolo e confronto V1.

## Verifica sul dataset locale

Periodo richiesto 2017-01-01–2026-10-29; copertura effettiva 2017-01-03–2026-09-08. Stessi parametri monetari della configurazione salvata: capitale 10000, rischio 0.5%, limite posizione 30%, stop 1.5 ATR, timeout 5, slippage 0.03% e commissioni IBKR esistenti.

Segnali: 3979. Trade: 1074, di cui 1070 chiusi e 4 aperti. Motivi distinti di audit: 2188 ENTRY_NOT_TRIGGERED, 484 ENTRY_REJECTED_OPEN_AT_OR_ABOVE_PREVIOUS_HIGH, 50 INSUFFICIENT_CAPITAL_OR_RISK_BUDGET, 182 POSITION_ALREADY_OPEN e 1 MISSING_NEXT_OPEN. La priorità dei rifiuti è quella del motore originale: un segnale già rifiutato per posizione aperta non viene classificato nuovamente come ordine respinto all'open.

Riconciliazione superata. Final equity 7990.7927; PnL portafoglio -2009.2073; CAGR -2.2909%; PF netto sui chiusi 0.81948; win rate 56.5421%; max drawdown 24.5366%. Questi risultati non sono stati migliorati modificando rischio, uscite o quantità.

Excel e riepilogo:

- outputs/trend-pullback-rebound-v3-no-tick/TREND_PULLBACK_REBOUND_V3_RESEARCH.xlsx
- outputs/trend-pullback-rebound-v3-no-tick/summary.json
- outputs/trend-pullback-rebound-v3-no-tick/V3_NO_TICK_LIVE.xlsx

È stata verificata anche la chiamata HTTP reale all'istanza BACKTEST locale su 8082: creazione della run 50fbf728-d4b5-4d52-ac6d-5e48efefc837, 3979 segnali, 1074 trade, riconciliazione superata ed export HTTP 200. Il controllo HTML conferma l'assenza della sezione tick.

## Test

91 JUnit mirati passati: 7 nuovi sul breakout senza tick, 14 sulle modalità V3 storiche, 4 sul vecchio modello research, 10 sul resolver storico, 3 sugli export, 1 report locale, 23 regressioni del motore V1 e 29 del motore V2.

151 test frontend passati. Inclusi avvio diretto senza chiamate di prevalidazione, assenza della sezione tick, download della run ed errori di export.

Nessuna modifica alle strategie V1/V2, agli altri trading system o a PAPER/LIVE. Nessun ordine IBKR inviato.

Pagina aggiornata: http://127.0.0.1:8082/trend-pullback-rebound-v3.html — istanza locale esclusivamente BACKTEST, con archivio V3 separato.
