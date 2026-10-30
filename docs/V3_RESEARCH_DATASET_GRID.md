# V3: opzione research con griglia numerica del dataset

L'opzione RESEARCH_DATASET_GRID rende possibile una simulazione esplorativa quando non sono disponibili tick di mercato verificati. Non corregge né ricostruisce i metadati mancanti e non deve essere interpretata come esecuzione a un tick realmente negoziabile.

FAIL_FAST rimane il default. Per usare l'opzione occorre selezionarla esplicitamente e impostare researchDatasetGridAcknowledged=true tramite la casella dedicata. Il backend rifiuta la modalità senza questa accettazione. Nessuna configurazione esistente viene migrata automaticamente.

## Regola di prezzo del modello

Per ogni simbolo e data del segnale, viene individuato il massimo numero di decimali significativi osservato negli OHLC disponibili fino a quella data, inclusa la barra del segnale. Gli zeri finali di rappresentazione sono ignorati. Il quanto di prezzo è 10^(-decimali).

Il BUY STOP usa il successore di questa griglia per il buffer configurato. Tutta l'aritmetica degli incrementi resta BigDecimal. La precisione di barre future non può influenzare il trigger di un segnale precedente.

Esempio sintetico: se la precisione osservata è 0.001 e il massimo è 108.005, con buffer 1 il trigger è 108.006. Questo esempio non certifica che tale prezzo sia un tick di mercato valido.

Il motore rifiuta incrementi troppo piccoli per il proprio modello di esecuzione double. Non arrotonda silenziosamente a un valore diverso. Gli override tick manuali devono essere vuoti in questa modalità, perché non vengono applicati.

L'identità di mercato non viene inventata: Exchange, Currency e InstrumentType rimangono UNVERIFIED nell'audit di questo modello. Sorgente: CSV_DECIMAL_GRID_NOT_MARKET_TICK. Stato: RESEARCH_ASSUMPTION. Compatibilità: UNVERIFIED_TRADABILITY_RESEARCH_ONLY.

## Invarianti e limiti

Restano invariati segnale, filtro SMA200, condizione Open quarta barra < High terza barra, validità della sola quarta sessione, slippage, ATR al segnale, stop, RECOVERY SMA5, timeout, sizing, commissioni e gestione conservativa intrabar.

L'ipotesi sull'incremento d'ingresso è diversa da quella della modalità rigorosa. Non avviene una degradazione automatica da FAIL_FAST alla ricerca quando mancano metadati. Il quanto del CSV non elimina i limiti dei prezzi adjusted, non ricostruisce i prezzi raw e non dimostra eseguibilità o redditività.

La run salva scelta e accettazione, il dataset e l'hash della configurazione. Il replay riutilizza lo snapshot e la regola causale. UI, warning, CONFIGURATION, MINIMUM_TICK_AUDIT e confronto V1/V3 identificano il modello come research non verificato.

## Utilizzo

1. In Tick Resolution Policy selezionare Research: precisione CSV (non tick di mercato).
2. Selezionare la casella di accettazione del modello.
3. Premere RUN BACKTEST.

Se l'istanza usa un backend precedente che non espone il parametro di accettazione, l'opzione è disabilitata e indica la necessità di aggiornare/riavviare il backend.

## Istanza locale preparata

URL: http://127.0.0.1:8082/trend-pullback-rebound-v3.html

È stata avviata un'istanza locale solo BACKTEST, con strategia operativa e invio ordini disabilitati, database in memoria e archivio V3 separato in data/trend-pullback-rebound-v3-research-ui. La configurazione iniziale è una copia di quella esistente; la policy resta FAIL_FAST e l'accettazione resta false fino alla scelta dell'utente.

Il percorso dell'archivio è controllabile con la proprietà JVM trading.research.v3.store; senza tale proprietà il comportamento precedente rimane invariato.

Sul dataset attuale è stato eseguito esclusivamente un preflight temporaneo della nuova opzione: superato con zero errori. Le 39 righe research sono regole/precisioni storiche distinte, non 39 strumenti. Non sono state create run o salvate scelte research automaticamente.

## Verifiche

31 test JUnit mirati passati: 4 del modello research, 14 del motore V3, 10 del resolver rigoroso e 3 dell'export. Inclusi consenso obbligatorio, configurazione rigorosa predefinita, assenza di look-ahead, riconciliazione, replay ed etichette Excel. Log: v3-research-option-tests.log.

157 test frontend passati, con verifica di selezione/accettazione esplicite e segnalazione del modello non verificato. Build frontend riuscita. Log: v3-research-ui-tests.log e v3-research-build.log.

Nessuna modifica a V1/V2 o agli altri motori. Nessun ordine IBKR PAPER/LIVE trasmesso.
