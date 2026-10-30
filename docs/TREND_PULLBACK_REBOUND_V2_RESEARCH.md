# Trend Pullback Rebound V2 — Research

Identificativo: `TREND_PULLBACK_REBOUND_V2_RESEARCH`. Versione: `TREND_PULLBACK_REBOUND/2`.
Menu: **Strategie → TREND PULLBACK REBOUND V2 — RESEARCH**.
Pagina: `/trend-pullback-rebound-v2.html`; API: `/api/backtest/trend-pullback-rebound-v2`.

## Separazione dalla V1

Nessun file Java del modulo V1 è stato cambiato. La V2 non usa il registro operativo `StrategyType`, listener di mercato o client IBKR. Configurazioni, dataset, run e indici leggeri di storico sono esclusivi di `data/trend-pullback-rebound-v2-research/`.

Il motore V1 registra già tutti i candidati con condizioni di prezzo valide, compresi quelli bloccati da posizioni/ordini: la V2 riusa **quel generatore originale**, azzerandone soltanto lo stato di esecuzione per il nuovo portafoglio. Non copia i vecchi esiti delle operazioni per costruire le modalità ATR: ricalcola posizioni, cassa, quantità e costi. RECOVERY_ONLY delega invece l'intera simulazione al codice V1 originale, su identici parametri e snapshot.

Condivisi senza modifiche: `TrendReboundIndicators`, `CostAwareLongSizing`, `PositionSizingService`, `TradePricingService`, `Slippage`, `PatternV1Commission`, `PatternV2SplitCosts`, `TrendReboundAnalysis`, calendario USA e parser CSV. La SMA di recupero non produce mai uscite in ATR_TARGET_ONLY; il warm-up del generatore di ingresso originale resta invariato per preservare i candidati V1.

## Configurazione

| Parametro | Default |
| --- | --- |
| ExitMode | ATR_TARGET_ONLY |
| AtrPeriod | 14 |
| StopLossAtrMultiplier | 1,5 |
| TakeProfitEnabled | true |
| TakeProfitAtrMultiplier | 1,5 |
| TrendSmaPeriod / RecoverySmaPeriod | 200 / 5 |
| DecliningCloses / MaxHoldingBars | 3 / 5 |
| InitialCapital | 10.000 USD |
| RiskPercent / MaxOrderValuePercent / MaxExposurePercent | 0,5% / 30% / 100% |
| UseLeverage | false |
| SlippagePercentPerSide | 0,03% |
| Commission | IBKR_US_FIXED |
| TickSize | 0,01 USD, con convenzione 0,0001 sotto 1 USD |
| TickSizes | override per simbolo, nella base quotata prima del fattore split |

La leva è visibile ma non attivabile: `true` è rifiutato dall'API per non modificare il money management V1. I limiti di posizione/esposizione, quantità minime e gestione degli aperti restano quelli originali. Rischio sull'equity e moltiplicatore ATR del prezzo sono parametri distinti. Gap possono perdere più di 1R.

Le tre modalità:

- **ATR_TARGET_ONLY:** stop, target ATR se abilitato, gap e timeout. Nessuna programmazione RECOVERY.
- **RECOVERY_ONLY:** gestione originale V1, incluso RECOVERY alla successiva apertura. Target effettivo sempre OFF; il valore grezzo di `takeProfitEnabled` viene comunque conservato per audit.
- **FIRST_OF_BOTH:** target ATR se abilitato e RECOVERY originale, al primo evento cronologicamente eseguibile.

## Livelli e cronologia

L'ingresso è l'OPEN successivo al segnale più lo slippage BUY originale. ATR è quello Wilder disponibile alla chiusura del segnale; inizializzazione e warm-up rimangono quelli della V1 (primi N true range completi, quindi N+1 barre).

Stop iniziale, senza arrotondamenti aggiuntivi, identico V1:

`entryExecutionPrice − stopLossAtrMultiplier × atrAtSignal`.

Target teorico non arrotondato:

`entryExecutionPrice + takeProfitAtrMultiplier × atrAtSignal`.

Il target viene calcolato soltanto all'ingresso, arrotondato **verso l'alto** al tick configurato e resta fisso. Per dati split-adjusted il tick viene tradotto con il fattore split d'ingresso del modello esistente: arrotondamento sulla quota originale e conversione nella base dello snapshot. `RawTakeProfit` significa target non arrotondato nella base dati, non prezzo di un dataset RAW. Sono esportati target teorico, target valido e tick effettivo. Esempio 100 + 1,5×2 = 103.

Sequenza giornaliera:

1. Per posizioni già aperte: OPEN sotto/allo stop; OPEN sopra/al target; RECOVERY già programmato. I gap hanno priorità nel motivo se coincidono con RECOVERY, ma l'unico prezzo di uscita è sempre lo stesso OPEN.
2. Nuovi ingressi all'OPEN, ordinati alfabeticamente, con sizing e cassa aggiornati soltanto da eventi già avvenuti all'apertura.
3. Stop/target intraday, anche sulla barra d'ingresso dopo il BUY. Se HIGH e LOW toccano entrambi i livelli e nessun evento all'OPEN risolve la sequenza, **STOP_FIRST** e `IntrabarAmbiguous=true`. Un gap noto all'OPEN non diventa ambiguo perché il LOW/HIGH successivo toccherebbe l'altro livello.
4. Timeout al CLOSE della quinta seduta, contando l'ingresso come 1, se ancora aperto.
5. Solo nelle modalità appropriate: valutazione RECOVERY sul CLOSE completato e vendita programmata alla successiva apertura.

Proventi intraday del target non finanziano ingressi già avvenuti all'apertura. Le uscite sono integrali e uniche. Nessun trailing, pareggio, aumento o parziale.

`ExitBasePrice=OPEN` per GAP_TARGET/GAP_STOP; `ExitBasePrice=target` per TAKE_PROFIT_ATR intraday; SELL slippage e commissione originale una sola volta. È un modello DAILY di vendita al tocco con slippage, **non** una promessa di esecuzione limit al target. `GrossPnL` usa i prezzi eseguiti prima delle commissioni (quindi slippage già incluso); `NetPnL` sottrae solo commissioni. Slippage esportato separatamente è informativo. Stop intraday V1 `STOP` è normalizzato a `STOP_LOSS` nell'audit V2; i DTO legacy restano originali per la regressione.

## Run, replay e confronto

La pagina consente RUN BACKTEST, salvataggio, Run History separata, apertura run, replay, Excel e selezione di due/tre run per confronto.

Un riferimento V1 è facoltativo. **CARICA PARAMETRI V1** legge i parametri della run V1 e l'esecuzione usa il suo snapshot congelato: nessun salvataggio/configurazione V1. API GET `/reference/{id}/configuration`, POST `/run?referenceV1RunId={id}`. In assenza di riferimento vengono letti i CSV locali con le stesse convenzioni originali, segnalando esclusioni, duplicati e limiti.

Il confronto accetta solo run salvate, modalità distinte, stesso dataset, stessa versione/fingerprint del motore e stessi parametri salvo nome ed ExitMode. Per i tre confronti standard mantenere i parametri grezzi del target uguali (anche in RECOVERY_ONLY il flag salvato può essere true, mentre l'effetto è OFF). Nessun risultato viene creato per modalità non eseguite.

Endpoint aggiuntivi: POST `/runs/compare`, POST `/runs/compare/excel`; GET `/runs/{id}/export/excel`. Filename individuale `TREND_PULLBACK_REBOUND_V2_<runId>.xlsx`.

Il replay controlla fingerprint del motore originale e del bundle V2 (indicatori, sizing, commissioni, split, slippage e calendario), snapshot e risultati economici/audit prima di salvare. Un motore non corrispondente o una divergenza vengono rifiutati, non corretti retroattivamente.

## Excel e regressione

Conservati tutti i fogli V1: DASHBOARD, CONFIGURATION, SIGNALS, TRADES, OPEN_POSITIONS, EQUITY, ANNUAL_RESULTS, RECONCILIATION, DATA_QUALITY, NOTES. Aggiunti EXIT_ANALYSIS, EXIT_MODE_COMPARISON, EXIT_ANALYSIS_BY_MODE e REGRESSION_AUDIT. Export streaming compatto, senza duplicare l'intero archivio DAILY.

TRADES include ATR al segnale, moltiplicatori, stop, target, reward/risk teorico, flag effettivi, modalità, OPEN dell'uscita, prezzo base/eseguito, gap, ambiguità, PnL lordo/netto, commissioni, slippage e NetR. Configurazione JSON esatta, hash, periodi effettivi, strumenti/esclusioni e fingerprint sono conservati.

EXIT_ANALYSIS usa le sole posizioni chiuse e le sei categorie richieste; zero trade è un conteggio reale, rapporti senza osservazioni sono N/A. Nel confronto il dettaglio trade/configurazione appartiene alla prima run selezionata; confronto, distribuzioni e regressione includono tutte le run selezionate. I workbook individuali contengono il dettaglio delle altre modalità.

REGRESSION_AUDIT confronta trade, decisioni sui segnali ed equity DAILY della RECOVERY_ONLY con il motore V1 e, se fornita, con la run archivio originale. Esclude dal confronto solo gli ID; non esclude prezzi, quantità, costi, motivi o date. Nelle altre modalità non dichiara la parità dei risultati V2: verifica soltanto il riferimento originale e indica che quella run non è RECOVERY_ONLY. Una discrepanza del riferimento blocca il salvataggio.

## File

Nuovo package `backtest/reboundv2`: `TrendReboundV2Configuration`, `TrendReboundV2Run`, `TrendReboundV2Engine`, `TrendReboundV2Audit`, `TrendReboundV2Store`, `TrendReboundV2Service`, `TrendReboundV2ExcelExporter`, `TrendReboundV2Controller`.
Registrazione aggiunta a `WeeklyResearchStrategy` e `ResearchStrategyCatalog`. Nuovi HTML/JS V2; voce aggiunta ai menu esistenti con aggiornamento del token cache della navigazione. Test Java V2 e JavaScript dedicati; test della navigazione aggiornati solo per il token.

## Limiti

Nessuna validazione fuori campione. Universo attuale soggetto a possibile survivorship bias, prezzi/aggiustamenti e catalogo split non certificati integralmente, simulazione DAILY senza sequenza intraday. Dati mancanti su posizioni aperte mantengono la semantica V1: posizione censurata con avviso, nessuna uscita inventata. Cassa e mark-to-market aperti sono separati dai trade chiusi. Non sono stati aggiunti strumenti operativi PAPER/LIVE o ordini IBKR.
