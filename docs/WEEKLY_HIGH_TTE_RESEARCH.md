# WEEKLY_HIGH_TTE_RESEARCH — architettura e regole v1

> Le regole attuali LONG/SHORT e i livelli storici intatti sono documentati in [WEEKLY_UNTOUCHED_V2.md](WEEKLY_UNTOUCHED_V2.md). Il documento seguente conserva le regole v1 per il replay delle run precedenti.

## Ricognizione del progetto

| Area | Componente trovato | Decisione |
|---|---|---|
| Strategie | `StrategyEngine`, servizi `patternv1`, `patternstable`, `patternv2stable`, `patternv3` | Nuovo package `backtest.weeklytte`; nessuna registrazione nel router PAPER/LIVE |
| Backtest | `BacktestEngine` e simulatori portfolio PATTERN | Simulatore portfolio DAILY isolato per BUY_STOP e parziali; nessuna modifica dei simulatori precedenti |
| DAILY | `HistoricalBar`, `HistoricalDataService`, `StooqCsvParser` | Riutilizzo modello e parser; caricamento esclusivamente CSV locali, senza refresh provider |
| WEEKLY | `WeeklyLevelService.previousWeekLevels` | Riutilizzo aggregazione pura, con controllo aggiuntivo delle sessioni nel nuovo modulo |
| Sessioni | `ResearchSessionCalendar` | Calendario USA cash equity offline 2010–2028, festività/chiusure eccezionali già censite |
| Money management | `risk.PositionSizingService`, `TradePricingService`, adattatori PATTERN | Riutilizzo del servizio numerico di sizing con configurazione autonoma e vincoli cash/esposizione aggregata |
| Commissioni | `PatternV1Commission` | Stessa formula IBKR_US_FIXED / FIXED_PER_ORDER per ciascuna esecuzione |
| Split | `PatternV2SplitCosts` | Stesso calcolo SPLIT_AWARE_V1; catalogo proprio per AAPL + NVDA; altri simboli richiedono verifica |
| Slippage | `backtest.execution.Slippage` | Stessa funzione per-side, BUY additivo, SELL sottrattivo, una sola applicazione |
| Gap/STOP_FIRST | `BarExecution`, `ExitFillPolicy.OPEN_FIRST` | Semantica OPEN_FIRST/STOP_FIRST riprodotta in componente LONG isolato: `BarExecution` richiede EntryPlan PATTERN, inadatto a BUY_STOP/parziali |
| Ordini simulati | Simulatori PATTERN / ExecutionModel | BUY_STOP locale; nessun oggetto ordine broker e nessuna dipendenza dai servizi ordini |
| Excel | Apache POI, exporter PATTERN, ExcelStyleFactory | Workbook separato tramite POI; tabelle, filtri, freeze header, tipi numerici |
| Run History | Store JSON atomici e snapshot hash PATTERN | Store separato `data/weekly-high-tte-research`; riutilizzo contenitore tecnico immutabile `PatternV2Dataset` |
| Configurazioni | Bean congelati V1/V2, store V3 | Record autonomo con default propri, configurazioni JSON e hash SHA-256 |
| Frontend | Static HTML/JS, `app.css`, `research.css` | Pagina `/weekly-high-tte.html`, link nel menu principale, CSS comune |
| Test | JUnit 5/Surefire, HTTP Spring Boot, Node test runner | Fixture deterministiche, regressioni esistenti, test HTTP senza broker, backtest locale opt-in |

## Matematica e cronologia

Settimana: lunedì–domenica. Reference = massimo HIGH di tutte le sessioni attese nella settimana di calendario immediatamente precedente. Nessuna sostituzione con settimane precedenti più vecchie. Una festività regolare non è una barra mancante. La reference è fissa per tutta la settimana; dentro un trade è fissata all'ingresso anche dopo il cambio di settimana.

Per una recovery al giorno `i`, si cerca il più recente picco `k=i-n-1`, con `n` fra MinPullbackBars e MaxPullbackBars e `i-k <= PullbackLookbackBars+1`. Il picco deve avere `High[k] >= High[k-1]`. Per ogni barra `j=k+1..i-1` occorrono `Close[j] < Close[j-1]` e `High[j] <= High[j-1]`. La recovery richiede `Close[i] > Open[i]` e `Close[i] > Close[i-1]`. PullbackLow = minimo LOW di `k+1..i-1`, escludendo la recovery. Il primo candidato valido viene usato; una sola recovery per picco e simbolo. Nessuna conferma da barre future.

MomentumValue = `Close[i]/Close[i-MomentumLookback] - 1`. Con filtro ON deve essere positivo; non ci sono EMA o ADX obbligatori. VolatilityPct descrittiva condizionata al segnale = media di `(High-Low)/Close * 100` sulle ultime 20 barre disponibili, inclusa la recovery. Trend del segnale = segno del momentum; MarketRegime = UNAVAILABLE.

TTE: EntryTrigger = RecoveryHigh + buffer. TICKS = valore × TickSize; PERCENT = prezzo × valore/100. InitialStop = PullbackLow − stop buffer. Il segnale è valido solo dalla successiva sessione, fino a EntryValidityBars, senza rollover della reference. Il controllo first-touch usa soltanto massimi delle barre concluse fino al segnale e successive informazioni cronologicamente disponibili.

EntryBase = Open se Open >= trigger, altrimenti trigger se High >= trigger. BUY slippage si aggiunge al prezzo base. Se l'apertura ha già raggiunto il target, il setup TTE è TARGET_REACHED_BEFORE_ENTRY. EntryExecution deve essere < target. Rischio = EntryExecution − InitialStop > 0; reward = target − EntryExecution; R/R = reward/rischio, verificato di nuovo dopo gap e slippage. Sizing e capitale vengono controllati dopo tale verifica.

Stop, target, timeout sono DAILY. La barra d'ingresso conta come sessione 1. Per posizioni esistenti all'OPEN: gap stop o gap target al prezzo OPEN, poi STOP_FIRST quando la barra raggiunge entrambi i livelli. Timeout alla CLOSE della sessione massima. Il rischio percentuale è iniziale teorico: gap e costi possono produrre perdite maggiori.

Le entrate sono elaborate in ordine alfabetico dei simboli quando manca una cronologia intraday. I proventi degli exit OPEN sono disponibili agli ingressi; i proventi intraday vengono accreditati dopo gli ingressi. L'equity decisionale usa cash e ultimi close noti delle posizioni; non usa CLOSE della giornata in corso. Limiti ordine, posizione, esposizione LONG e liquidità sono simultanei. La quantità usa il servizio comune e una verifica monotona di rischio con costi e cash. Nessun costo viene sottratto due volte.

## Ambiguità DAILY

Se l'ingresso è intrabar, LOW <= stop o HIGH >= target comportano un flag esplicito. CONSERVATIVE applica una perdita al livello stop solo come scenario `AMBIGUOUS_ENTRY_ADVERSE`, non come STOP_LOSS osservato con certezza; se è raggiunto soltanto il target rinvia quell'uscita alla barra seguente. Se il close è sotto stop esce a close, informazione successiva all'ingresso. SENSITIVITY_FAVORABLE usa il target come scenario `AMBIGUOUS_ENTRY_FAVORABLE` quando raggiunto, altrimenti prosegue. Non sono limiti matematici garantiti del portafoglio: decisioni e disponibilità di capitale possono divergere nei giorni successivi. Entrambi i casi sono persistiti e contati. Un ingresso all'OPEN usa la gestione normale della barra.

## Modalità distinte

- WEEKLY_HIGH_BREAKOUT: ordine valido dalla prima sessione della settimana, reference già nota dalla chiusura precedente; trigger = PreviousWeekHigh + buffer; stop = PreviousWeekLow − buffer; target = entry effettiva + BreakoutTargetR × rischio. Non richiede pullback/recovery/momentum. È un benchmark separato, non un'anticipazione del primo tocco.
- WEEKLY_HIGH_TTE: setup descritto sopra, target PreviousWeekHigh.
- WEEKLY_HIGH_TTE_PARTIAL: stessi segnali TTE, prima uscita parziale configurabile al livello; residuo con stop iniziale e target `max(PreviousWeekHigh+TickSize, EntryExecution+ResidualTargetR*risk)`. La quantità deve consentire almeno una azione per lato; con una azione l'uscita al primo target è completa. Ogni esecuzione ha la propria commissione. Nessun trailing nella v1.

Il confronto modalità usa stesso snapshot, universo, capitale, costi, periodo e tutti gli altri parametri. La policy di ambiguità deve coincidere. Non c'è selezione automatica del vincitore né ottimizzazione automatica.

## Persistenza, Excel e analisi

Configurazioni e run sono JSON separati dalle PATTERN. Run e configurazione hanno identificativi/hash; lo snapshot include OHLC, provider, adjusted basis e volume. File temporaneo + sostituzione atomica per salvataggi. Replay verifica il checksum del dataset. Lo storico conta configurazioni uniche effettivamente eseguite.

Fogli: WEEKLY_LEVEL_STATISTICS, TTE_SETUP_AUDIT, TRADES, EXIT_FILLS, EQUITY, TTE_PERFORMANCE_ANALYSIS, CONFIGURATION, RUN_HISTORY. Il confronto ha un workbook MODE_COMPARISON. Le strutture annidate delle analisi sono espanse in righe metrica/valore con percorso esplicito. Tutti i setup riconosciuti, anche esclusi, restano nell'audit; le barre che non costituiscono strutturalmente un pullback/recovery non sono setup.

Le settimane correnti incomplete sono esportate ma escluse dalle probabilità settimanali. TrendContext settimanale = rialzo/ribasso della settimana precedente completata, noto all'apertura, mai segno della settimana futura. Tassi per simbolo/anno/periodo/trend e distanze, media/mediana del primo tocco. I gruppi trade includono R/R, simbolo, anno, periodo; rischio di gruppo indicato da WorstTradeR. Max DD portfolio calcolato sull'equity mark-to-market. Profit Factor senza perdite e statistiche senza osservazioni sono null, non infinito o zero fittizio.

DistanceFromLevelAtWeekOpenPct = `(PreviousWeekHigh − CurrentWeekOpen) / PreviousWeekHigh × 100`: positivo per apertura sotto il livello, negativo per apertura sopra. WeekRangePct = `(CurrentWeekHigh − CurrentWeekLow) / CurrentWeekOpen × 100`, esclusivamente descrittivo a posteriori. La distanza del setup usa la stessa convenzione con il close del segnale al posto dell'open settimanale. Il tempo al primo target dei parziali usa la prima esecuzione al target. Il confronto Excel dispone le modalità in colonne e conserva gli hash nel foglio COMPARISON_CONTEXT.

I cinque eventi sono separati. L'osservazione shadow del target dopo ingresso continua per MaxHoldingDays anche dopo l'uscita simulata; non è un rendimento e non alimenta i segnali. ConditionalAnalysis raggruppa solo i candidati ammissibili al segnale; variabili di condizionamento note al segnale. Campione <30 segnalato, probabilità con intervallo Wilson 95%. Nessuna inferenza automatica di profittabilità dal tasso di tocco. Posizioni aperte e ordini pendenti a fine dati sono censurati. Analisi annuali e periodi 2010–2013, 2014–2017, 2018–2021, 2022–ultima data; nessun walk-forward automatico introdotto.

## Dati e limiti espliciti

L'adattatore CSV isolato conserva gli OHLC e arrotonda per difetto soltanto i volumi frazionari, non usati nei segnali. Registra conteggio e hash SHA-256 del sorgente. Non modifica i file originali o il parser comune. Sono accettati solo DAILY positivi, coerenti, senza duplicati e con date di sessione valide. Il calendario dipende dal catalogo offline già esistente: nuove chiusure eccezionali richiedono aggiornamento.

I CSV STOOQ vengono dichiarati ADJUSTED; prezzi adjusted non costituiscono una ricostruzione point-in-time del feed originale e possono includere rettifiche diverse dai soli split. Cataloghi split e convenzioni del dataset devono essere verificati prima di estendere l'universo. Il catalogo AAPL usa [dividend/split history Apple](https://investor.apple.com/dividend-history/default.aspx) e [comunicato Apple 2020](https://www.apple.com/newsroom/2020/07/apple-reports-third-quarter-results/) per le date di negoziazione rettificata. NVDA riutilizza il catalogo comune, senza modificarlo.

Il percorso è offline; non scarica dati da IBKR. Non ci sono SHORT, integrazione PAPER, LIVE, ottimizzatore o ordini reali. L'assenza di look-ahead riguarda le regole e la cronologia della simulazione, non elimina survivorship bias dell'universo scelto o revisioni storiche del fornitore.
