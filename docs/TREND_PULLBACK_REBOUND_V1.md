# Trend Pullback Rebound — ricerca sperimentale

Identificativo `TREND_PULLBACK_REBOUND_V1`, versione regole `TREND_PULLBACK_REBOUND/1`.
Voce **Strategie → Trend Pullback Rebound** su tutte le pagine.
Descrizione: Acquisto dopo tre chiusure consecutive in discesa, dentro un trend rialzista, per cercare un recupero entro cinque sedute.
Non è una raccomandazione d'investimento né una strategia dichiarata redditizia/validata.

## Architettura e riuso

Il catalogo `/api/backtest/research-strategies` registra la nuova voce tramite l'enum di ricerca esistente, separato da `StrategyType` e dalle factory operative. Non vengono registrati listener di mercato o ordini broker.

`BacktestEngine` riceve un nuovo overload per eseguire uno snapshot congelato: gli overload esistenti restano invariati. Il nuovo percorso DAILY è in `TrendReboundEngine`; i motori Pattern hanno regole specifiche che non supportano l'uscita SMA alla successiva apertura. Non vengono riutilizzati né ritoccati i loro vecchi trade. Riutilizzati `PositionSizingService`, `TradePricingService`, `Slippage`, `PatternV1Commission`, `PatternV2SplitCosts`, `PatternV2Dataset`, `ResearchSessionCalendar`, il parser locale `WeeklyTteCsvData`, hash/configurazioni secondo le convenzioni esistenti e Apache POI streaming per Excel. `CostAwareLongSizing` adatta il sizing condiviso ai vincoli di commissioni, cassa ed esposizione di apertura.

Endpoint dedicati: `/api/backtest/trend-pullback-rebound/configuration` GET/PUT, `/run` POST, `/runs` GET, `/runs/{id}` GET, `/runs/{id}/replay` POST, `/runs/{id}/export/excel` GET.

Persistenza separata: `data/trend-pullback-rebound-v1/`. Lo storico usa file riassuntivi leggeri; snapshot e configurazione sono salvati con fingerprint/hash. Il replay riusa esattamente input e parametri salvati: cambia identità/timestamp della run, non gli esiti economici.

## Parametri iniziali

SMA trend 200, SMA recupero 5, ATR Wilder 14, stop 1,5 ATR, tre chiusure consecutive in discesa (quattro prezzi), massimo cinque sedute, capitale 10.000 USD, rischio 0,5% equity, valore posizione 30%, esposizione massima 100%, slippage 0,03% per lato, commissione IBKR_US_FIXED e catalogo split esistente. Quantità intere, LONG, niente leva, aumenti, trailing o uscite parziali. Universo, esclusioni, date, costi, limiti e tutti i periodi sono modificabili. Nessuna ottimizzazione automatica.

## Indicatori e cronologia

La SMA include il close della barra appena completata. True range = massimo tra HIGH−LOW, abs(HIGH−close precedente), abs(LOW−close precedente), riusando il calcolo esistente. La prima barra fornisce soltanto il close precedente; ATR(N) è inizializzato con la media dei primi N true range completi (servono N+1 barre), poi `(ATR precedente × (N−1) + TR) / N`. Sono lette tutte le barre locali disponibili dal 2010, fino alla fine richiesta; il warm-up precede le performance. Il primo giorno di equity attende almeno uno strumento con indicatori pronti. Una discontinuità di calendario azzera il warm-up di quello strumento.

Alla chiusura t: close > SMA trend e C[t] < C[t−1] < ... < C[t−decliningCloses]. Le chiusure nell'audit sono in ordine dal passato al presente. Non si confrontano open/close delle candele. I candidati validi dal punto di vista dei prezzi ma bloccati da posizione/ordine esistente sono registrati come REJECTED, senza ordini.

Il pending entry è valido soltanto per l'apertura della successiva seduta del calendario USA. In assenza di barra o capitale viene scartato, mai rinviato. `plannedEntryDate` è la data prevista, `attemptDate` la data di valutazione, `entryDate` esiste solo se eseguito. Stop fisso = prezzo BUY effettivo − moltiplicatore × ATR del segnale; stop non positivo/non finito invalida l'ingresso.

Per ogni seduta:

1. Uscite delle posizioni preesistenti all'apertura: gap stop e recuperi già programmati. Se coincidono, il prezzo è lo stesso OPEN e il motivo stop ha priorità. Nessun doppio fill.
2. Pending entry in ordine alfabetico del simbolo, usando soltanto open disponibili e chiusure precedenti per equity/esposizione. Il costo di ogni ingresso riduce la cassa prima del successivo.
3. Stop intraday su tutte le posizioni, comprese quelle appena entrate. Un open già sotto lo stop (possibile con slippage BUY elevato) usa OPEN, altrimenti usa lo stop quando LOW lo tocca. Slippage SELL applicato una sola volta.
4. Alla chiusura della quinta seduta (ingresso = 1), uscita temporale preprogrammata a CLOSE, salvo stop già eseguito.
5. Close > SMA recupero sulle posizioni ancora aperte programma la vendita all'apertura seguente, anche se il close è quello della seduta d'ingresso. Infine si valutano i nuovi candidati.

Solo le uscite all'apertura finanziano ingressi nella stessa apertura. Proventi degli stop intraday o delle scadenze di chiusura arrivano dopo tutti gli ingressi. Non si presume alcun ordine HIGH/LOW favorevole: essendoci ingresso noto all'OPEN e un unico stop fisso, non serve ricostruire una sequenza intraday arbitraria.

## Contabilità

Sizing condiviso arrotondato per difetto, poi ricerca binaria monotona della quantità che rispetta simultaneamente budget di rischio (comprensivo dei costi stimati allo stop), cassa con commissione, limite posizione e limite di esposizione. I limiti sono verificati sul patrimonio dopo i costi dell'ingresso; non si forza il ribilanciamento successivo per movimenti dei prezzi. Il rischio iniziale esportato è quantità × (entry effettiva − stop); `sizingRiskWithCosts` include le riserve per commissioni/slippage allo stop. Gap e costi effettivi possono superare il budget.

PnL chiuso = quantità × (exit effettiva − entry effettiva) − commissioni entry/exit. Lo slippage è già nei prezzi e non viene sottratto nuovamente. R = PnL netto / rischio iniziale. Posizioni aperte: PnL netto realizzato e R sono N/A, mark-to-market al close disponibile meno costo ingresso è separato. Riconciliazione: capitale iniziale + PnL trade chiusi + PnL aperto = equity finale.

## Dati, esclusioni e limiti

Sono richieste barre DAILY coerenti con calendario azionario USA 2010–2028. OHLC invalidi, duplicati giornalieri, simbolo errato o base prezzi mista escludono lo strumento con motivazione. Il caricamento locale verifica SHA sorgente e dataset prezzo/volume identico fra simboli: esclude tutti i simboli del gruppo sospetto, compreso BA/BAC se presenti, senza scegliere arbitrariamente quale identità sia corretta. Le esclusioni sono visibili in UI/Excel; una run con errori è esplicitamente segnalata. Input insufficienti per ogni simbolo fanno fallire la run con motivazione.

Prezzi adjusted mantenuti nella loro base originale, senza correzioni silenziose. Commissioni adattate al fattore split tramite il modello esistente. Dati RAW che attraversano split catalogati sono esclusi; per split ignoti/dividendi non certificati sono riportati limiti. Volumi frazionari adjusted sono arrotondati dal parser esistente, con conteggio; non entrano nei segnali.

Se manca una barra mentre una posizione è aperta, il percorso stop/recupero non è osservabile: posizione `DATA_GAP_UNRESOLVED`, nessuna chiusura inventata, equity `dataIncomplete=true` e avviso. La scadenza di cinque sedute non può essere verificata in questo caso. Mark-to-market e aggregati sono condizionati da questa incompletezza. Un dataset che finisce normalmente lascia separatamente le posizioni ancora aperte e i pending censurati.

Universo di titoli attuali potenzialmente soggetto a survivorship bias; non sono certificati componenti storici, delisting e dividendi. Nessuna disponibilità intraday o ordini IBKR. Il calendario può richiedere aggiornamenti per nuove chiusure straordinarie.

## Excel compatto e verifiche

Fogli DASHBOARD, CONFIGURATION (JSON originale per precisione), SIGNALS (quattro close separati più array completo), TRADES, OPEN_POSITIONS, EQUITY, ANNUAL_RESULTS, RECONCILIATION, DATA_QUALITY, NOTES. Identità run/segnale/trade, prezzi teorici ed effettivi, ATR/SMA al segnale, stop/rischio/quantità, costi, R, durata, motivi e posizioni aperte sono conservati. Nessuna duplicazione dei CSV DAILY nel workbook; snapshot esatto rimane nello storage della run. N/A spiega rapporti non definiti; non è uno zero inventato.

Test mirati coprono warm-up/ATR, chiusure corrette, assenza di look-ahead, next open, stop e gap anche all'ingresso, recupero, cinque sedute, priorità/cassa, costi, dati mancanti, replay e Excel. Test HTTP coprono catalogo, menu, configurazione, risultati, storico, replay, download e assenza di ordini broker; configurazioni Pattern confrontate prima/dopo. Test JavaScript coprono parametri, avvio, export, errori e storico lento.
