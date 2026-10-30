# WEEKLY_HIGH_TTE_RESEARCH — report tecnico, 21 ottobre 2026

Implementazione disponibile nel progetto, compilata e provata su dati locali. Strategia LONG di ricerca ispirata al concetto di ingresso anticipato TTE, senza pretesa di riprodurre ufficialmente le regole originali di Joe Ross. Nessuna ottimizzazione automatica.

## Architettura, isolamento e interfaccia

Il package `backtest.weeklytte` comprende configurazione immutabile autonoma, generazione deterministica dei setup, simulazione portfolio DAILY, statistiche, store, API REST ed exporter POI. La ricognizione completa dei componenti condivisi e le definizioni matematiche sono in [WEEKLY_HIGH_TTE_RESEARCH.md](WEEKLY_HIGH_TTE_RESEARCH.md).

Sono riutilizzati `HistoricalBar`, `WeeklyLevelService`, `ResearchSessionCalendar`, `PositionSizingService`, `TradePricingService`, `Slippage`, la formula commissionale `PatternV1Commission`, `PatternV2SplitCosts` e il contenitore tecnico di snapshot `PatternV2Dataset`. Non vengono letti parametri dei bean/configurazioni PATTERN. Il calendario condiviso è una funzione statica, senza configurazioni modificabili dalla nuova pagina.

BUY_STOP, priorità di esecuzione e uscite parziali sono implementati nel simulatore isolato. La semantica dei gap coincide con OPEN_FIRST delle PATTERN; **non è lo stesso metodo di esecuzione condiviso**, che richiede piani di ingresso PATTERN. Costi, slippage e sizing utilizzano invece le funzioni comuni. Nessuna modifica al motore precedente.

Pagina `/weekly-high-tte.html`, raggiungibile dal menu principale con **WEEKLY HIGH TTE — RESEARCH**. Presenta sezioni Generale, TTE Setup e Trade Management, costi/split modificabili in JSON, salvataggio e caricamento configurazioni, RUN BACKTEST, EXPORT EXCEL, RUN HISTORY, confronto modalità e sensibilità intrabar. Mostra metriche, audit, trade e analisi completa. Le prime 500 righe di audit sono visualizzate, tutte sono esportate. Non contiene comandi PAPER AUTO o LIVE.

API dedicata `/api/backtest/weekly-high-tte`. Configurazioni, snapshot e run sono sotto `data/weekly-high-tte-research`, separati dalle PATTERN; scritture tramite file temporaneo e sostituzione. Su Windows le AccessDenied transitorie durante la sostituzione hanno cinque tentativi limitati; il precedente JSON non viene cancellato prima della sostituzione. Hash SHA-256 di configurazione e dataset, versione calendario, configurazioni uniche sperimentate e replay dello snapshot sono disponibili nello storico. Anche hash dei CSV sorgente e volumi frazionari normalizzati sono registrati nel manifest della run originale.

## Regole operative e assenza di look-ahead nelle decisioni

- Reference = HIGH massimo della **settimana di calendario immediatamente precedente**, lunedì–domenica; devono essere presenti tutte le sessioni attese dal calendario cash equity USA. Le festività non sono dati mancanti. Una reference incompleta comporta esclusione, senza ripiego su una settimana più vecchia.
- Il livello è fisso durante la settimana. Dopo l'ingresso rimane quello originale per tutta la posizione, anche attraversando una nuova settimana.
- Recovery `i`: `Close[i] > Open[i]` e `Close[i] > Close[i-1]`. Si cerca il picco più recente `k=i-n-1`, con `n` tra 1 e 3 per default, `High[k] >= High[k-1]`; ciascuna barra correttiva ha close strettamente decrescente e high non crescente. Tutti i dati terminano alla recovery. Una sola recovery per picco/simbolo.
- Momentum opzionale: `Close[i]/Close[i-5]-1 > 0`. Stop = minimo LOW delle sole barre correttive meno buffer. Trigger = HIGH recovery più buffer. TICKS = numero tick × tick size; PERCENT = prezzo × valore/100.
- First-touch verificato usando solo barre già concluse fino al segnale e le successive informazioni disponibili in ordine cronologico. Il massimo HIGH della settimana corrente calcolato a posteriori serve soltanto alle statistiche descrittive, mai alla decisione.
- BUY_STOP attivo dalla sessione successiva; massimo tre sessioni per default. Scadenza registrata alla chiusura dell'ultima sessione valida, oppure al cambio settimana; nessun rinnovo con vecchia reference. Anche il benchmark conserva nell'audit l'ordine scaduto prima di creare quello nuovo.
- Base ingresso = OPEN se OPEN ≥ trigger, altrimenti trigger se HIGH ≥ trigger. Slippage BUY applicato una volta. OPEN già al livello obiettivo causa `TARGET_REACHED_BEFORE_ENTRY` nei modi TTE.
- Rischio = esecuzione ingresso − stop; reward = target − esecuzione ingresso; R/R ricalcolato **dopo gap e slippage**, minimo 1,5 per default. Stop/rischio invalidi o ingresso al/sopra target vengono rifiutati.
- Sizing comune più vincoli simultanei di rischio, valore ordine, posizione, esposizione LONG e cash, incluse commissioni e slippage stop. Default: capitale USD 10.000, rischio 2%, ordine massimo 30% equity corrente, assenza di leva, esposizione massima 100%. Le percentuali SHORT sono persistite ma non generano posizioni SHORT.
- Ordine alfabetico dei simboli quando manca una sequenza intraday. Exit all'OPEN liberano capitale prima degli ingressi; exit intraday, incluse quelle sulla barra d'ingresso, liberano capitale dopo tutti gli ingressi della giornata. Equity decisionale calcolata usando cash e ultimi close noti, senza il close corrente.
- Per posizioni presenti all'OPEN: gap oltre stop/target eseguito all'OPEN; poi STOP_FIRST se entrambi i livelli sono compresi nella barra. Slippage SELL e commissione per singola esecuzione. Timeout al close della decima sessione per default; barra d'ingresso = sessione 1.

L'assenza di look-ahead è verificata per la logica decisionale, con fixture che cambiano barre future senza cambiare il segnale e con controlli sulla disponibilità cronologica del capitale. Non equivale a garantire che il fornitore storico sia point-in-time: i CSV adjusted possono avere rettifiche retroattive e l'universo scelto può presentare survivorship bias.

## Ambiguità DAILY e modalità di confronto

Entrata all'OPEN: gestione ordinaria della barra. Entrata intrabar: se LOW comprende lo stop oppure HIGH comprende il target, viene registrata l'ambiguità. CONSERVATIVE modella l'estremo avverso con `AMBIGUOUS_ENTRY_ADVERSE`, senza presentarlo come STOP_LOSS cronologicamente osservato. Se è compreso soltanto il target, ne rinvia l'esecuzione. Il close successivo all'attivazione può determinare uscita se sotto stop o per timeout. SENSITIVITY_FAVORABLE modella l'esito favorevole al target, altrimenti prosegue. Sono scenari DAILY espliciti, non ricostruzioni intraday certe né limiti matematici garantiti del risultato portfolio.

Tre modalità autonome:

| Modalità | Ingresso | Stop | Target |
|---|---|---|---|
| WEEKLY_HIGH_BREAKOUT | Massimo precedente + buffer, ordine valido dalla prima sessione | Minimo settimana precedente − buffer | Entry effettiva + 2R configurabili |
| WEEKLY_HIGH_TTE | Massimo recovery + buffer | Minimo pullback − buffer | Massimo settimana precedente |
| WEEKLY_HIGH_TTE_PARTIAL | Come TTE | Come TTE | 50% configurabile al massimo precedente; residuo a target R configurabile, superiore al primo target |

Il breakout è un benchmark indipendente senza filtro pullback/momentum. La variante parziale usa componenti isolati e commissiona ciascuna esecuzione; con una sola azione l'uscita al primo target è completa. Il residuo mantiene lo stop iniziale; trailing stop non introdotto. Il tempo al primo target usa la prima esecuzione al target, non l'uscita finale del residuo.

Il confronto rifiuta snapshot o parametri differenti, salvo nome/modalità. Capitale, periodo, universo, money management, costi, slippage e policy di ambiguità devono coincidere. Nessuna selezione automatica della modalità vincente.

## Excel, statistiche e tracciabilità

Workbook per ogni run con:

- `WEEKLY_LEVEL_STATISTICS`: una riga per simbolo/settimana, reference e OHLC descrittivi, primo tocco e indice sessione, distanze, trend noto dalla settimana precedente, regime non disponibile, qualità dati.
- `TTE_SETUP_AUDIT`: setup riconosciuti, inclusi rifiuti, scadenze, target prima dell'ingresso, gap, R/R insufficiente, capitale e ambiguità; collegamento a TradeId.
- `TRADES`: prezzi base/eseguiti, livelli, quantità, costi, capitale, PnL netto, R, gap e collegamento al setup. `EXIT_FILLS` dettaglia le esecuzioni, incluse le parziali.
- `EQUITY`: mark-to-market, cash, notional LONG ed esposizione.
- `TTE_PERFORMANCE_ANALYSIS`: performance, gruppi R/R, simbolo, anno e periodo, rischio tramite WorstTradeR e gap per gruppo, probabilità e intervalli Wilson 95%, osservazioni censurate e segnalazione campioni <30. Le strutture annidate sono espanse in righe con percorsi metrica/valore.
- `CONFIGURATION` e `RUN_HISTORY`: parametri, identificativi/hash, calendario e avvertenze.

Workbook confronto: `MODE_COMPARISON` ha metriche nelle righe e modalità nelle colonne; `COMPARISON_CONTEXT` conserva run, hash, dataset e regole specifiche.

Le cinque grandezze sono separate: tocco settimanale descrittivo; attivazione BUY_STOP fra segnali ammissibili; tocco dopo ingresso entro l'orizzonte anche ignorando lo stop; target prima dello stop nello scenario simulato; rendimento netto effettivo. L'osservazione shadow continua anche dopo uno stop e non alimenta i segnali. Le analisi condizionate usano solo variabili note al segnale: distanza, momentum, pullback, R/R teorico, weekday, simbolo, volatilità e trend. Regime = UNAVAILABLE; nessun regime inventato. Le osservazioni incompiute restano censurate. Un alto tasso di tocco non implica profittabilità.

## Primo backtest locale e confronto

Fonte: CSV locali STOOQ AAPL, MSFT e NVDA; periodo richiesto 01/01/2017–20/10/2026, effettivamente disponibile **03/01/2017–08/09/2026**. Nessun download eseguito. Default indipendenti, capitale USD 10.000, rischio 2%, massimo ordine 30%, IBKR_US_FIXED, SPLIT_AWARE_V1, slippage 0,03% per lato, CONSERVATIVE. I risultati non coprono le sessioni successive all'ultima barra disponibile; il CAGR usa l'intervallo effettivo.

| Metrica | Breakout | TTE | TTE Partial |
|---|---:|---:|---:|
| Equity finale USD | 33.991,41 | 10.132,81 | 10.107,59 |
| CAGR | 13,476% | 0,136% | 0,111% |
| Max DD | 22,318% | 2,215% | 2,721% |
| PF netto | 1,493 | 1,315 | 1,050 |
| Win rate netto | 56,17% | 38,46% | 33,33% |
| Trade chiusi | 470 | 13 | 12 |
| Posizioni aperte | 2 | 0 | 1 |
| Expectancy netta R | 0,1923 | 0,2122 | 0,0680 |
| Esposizione media | 38,399% | 0,324% | 0,368% |
| Gap stop | 21 | 0 | 0 |

PnL/expectancy/PF/win rate si riferiscono ai trade chiusi; equity finale e drawdown includono il mark-to-market delle posizioni aperte. Per questo la differenza equity-capitale può differire dal PnL chiuso nelle modalità con residui aperti.

Audit TTE: **836 setup**, 13 eseguiti, 756 rifiutati, 46 scaduti, 21 target raggiunti prima dell'ingresso. Rifiuti: 354 target già toccato al segnale, 297 momentum non positivo, 104 R/R effettivo insufficiente, 1 ingresso al/sopra target dopo slippage. Le scadenze comprendono 32 cambi settimana e 14 limiti di validità.

TTE: PnL netto USD **132,81**, commissioni USD 26,00, costo slippage USD 22,83, capitale medio impiegato USD 32,49, worst trade −1,1374R, 2 gap target, 5 trade con ambiguità intrabar. Nello scenario favorevole, equity finale USD **10.161,05**, PF 1,3771, CAGR 0,1652%, worst trade −2,0392R. Lo scenario favorevole può rinviare una perdita a un gap successivo: non è un limite superiore garantito per ciascun trade.

Massimi settimanali: **915 tocchi su 1.516 settimane/simbolo valide, 60,36%**; tre ultime settimane incomplete escluse. Media al primo tocco 1,889 sessioni, mediana 1. AAPL 299/505 = 59,21%; MSFT 307/505 = 60,79%; NVDA 309/506 = 61,07%. Sono frequenze descrittive, includono gap iniziali sopra il livello e non coincidono con la probabilità di profitto di un setup.

Eventi TTE distinti: trigger attivato 118/185 = **63,78%** fra setup ammissibili al segnale, prima dei rifiuti all'esecuzione; tocco target entro l'orizzonte ignorando lo stop 9/13 = **69,23%**; target prima dello stop nello scenario 4/13 = **30,77%**, Wilson 95% circa **12,68–57,63%**. Il win rate netto è 5/13 perché anche un'uscita per timeout può avere PnL positivo. Campione troppo piccolo per conclusioni robuste o scelta di parametri.

Gli artefatti in `outputs/weekly-high-tte-research/` includono `first-backtest.json`, `first-backtest.xlsx`, i tre workbook modalità, `mode-comparison.json/.xlsx`, `intrabar-sensitivity.json/.xlsx` e le evidenze di validazione. RunId, configuration hash e dataset hash esatti sono nel JSON e nei workbook; nessun valore inventato per anni/periodi privi di osservazioni. Il periodo 2010–2013 non è coperto dal backtest iniziale.

## Test, regressioni e limiti della verifica

JUnit deterministici coprono reference, festività, incompletezza, livelli fissi, momentum, pullback/recovery, assenza di pivot futuri, duplicati, first-touch, scadenze, BUY_STOP, gap, R/R effettivo, stop invalidi, cash, sizing, OPEN_FIRST/STOP_FIRST, timeout, slippage BUY/SELL, ambiguità e sensibilità, parziali, costi e riconciliazione capitale, export, hash, storico e replay. Test HTTP copre configurazione, statici, storico, replay, tre modalità, confronto JSON/Excel, sensibilità, errori e immutabilità configurazioni V1/V2/V3; verifica zero chiamate `broker.placeOrder`.

Tre test frontend nuovi verificano apertura senza azioni operative, invio dei parametri indipendenti e blocco dei numeri non finiti. Suite Node: **119 passati, zero errori**. Build IntelliJ riuscita. Maven verify produce il JAR.

Esito finale Maven: **719 test censiti, 700 passati, 19 saltati, zero failure/error**, escluso il solo metodo baseline descritto sotto. Nel nuovo modulo **69 test passati**: 67 deterministici, 1 workflow HTTP, 1 backtest locale. Run History e file export corrispondono per RunId/hash; tutti i sei workbook sono stati riaperti come archivi OOXML e i fogli richiesti verificati. Nel workbook iniziale le righe dati corrispondono a 836 setup, 13 trade e 1.519 settimane/simbolo, delle quali 1.516 complete valide e 3 incomplete.

Comandi riproducibili con JDK 21 e dipendenze Maven installate (nel terminale PowerShell usare `npm.cmd`):

```text
mvn "-Dtest=WeeklyTte*Test" "-Dweeklytte.local=true" test
mvn "-Dtest=*,!ResearchFiltersTest#validationAndCopiesPreserveExplicitBoundsAndLegacyConfigurationHashes" "-Dweeklytte.local=true" verify
npm.cmd test
node --check src/main/resources/static/weekly-high-tte.js
```

L'opzione `weeklytte.local` abilita il backtest sui CSV locali e la rigenerazione degli artefatti; senza tale proprietà il relativo test è intenzionalmente saltato. In questa sessione Maven è stato eseguito offline con percorso esplicito alla cache locale.

**La suite completa non è interamente verde senza esclusioni**: `ResearchFiltersTest.validationAndCopiesPreserveExplicitBoundsAndLegacyConfigurationHashes` fallisce già nella baseline senza modulo weeklytte, perché legge `data/pattern-v3/current.json` e pretende `closeStrengthMin = null`, mentre la configurazione locale salvata contiene `0.906`. Non sono stati modificati né la configurazione dell'utente né il test/strategia V3 per nascondere il problema. Il verify finale esclude soltanto quel metodo; i conteggi e gli esiti esatti sono in `validation-summary.json` e `maven-verify.txt`. La baseline fallita è conservata in `baseline-existing-failure.txt`.

Gli altri test PATTERN V1/V2/V3 e PAPER sono passati; nessun file Java, configurazione congelata o asset PATTERN/PAPER è stato modificato. Inizializzazione dei contesti HTTP in BACKTEST con connessione e invio ordini disabilitati. **Nessun ordine IBKR, PAPER o LIVE trasmesso**.

Verifica visiva nel browser non eseguita: il runtime non offre browser disponibili in questa sessione. Risorse statiche e workflow sono verificati tramite HTTP e test frontend; non viene dichiarata una verifica screenshot.

Limiti: calendario offline USA 2010–2028 con eccezioni note; altre borse non supportate. Catalogo split da verificare se si estende l'universo. Prezzi adjusted del fornitore non necessariamente point-in-time. Sequenze intraday sconosciute, ordine simboli deterministico, campione TTE piccolo, dataset locale non aggiornato alla data richiesta. Trailing residuo e walk-forward automatico non introdotti; disponibili suddivisioni per anno/periodo e configurazioni/dataset persistiti per esperimenti manuali. Nessuna ricerca automatica del massimo CAGR.

## File creati o modificati

| Percorso | Responsabilità |
|---|---|
| `src/main/java/it/andreademartini/trading/backtest/weeklytte/WeeklyTteConfiguration.java` | Parametri e validazione autonoma |
| `.../weeklytte/WeeklyTteLevels.java` | Reference e statistiche settimanali |
| `.../weeklytte/WeeklyTteEngine.java` | Setup, BUY_STOP, portfolio, gap e parziali |
| `.../weeklytte/WeeklyTteRun.java` | Audit, trade, fill ed equity |
| `.../weeklytte/WeeklyTteAnalysis.java` | Performance e probabilità |
| `.../weeklytte/WeeklyTteCsvData.java` | CSV locale, qualità e manifest |
| `.../weeklytte/WeeklyTteStore.java` | Configurazioni, run e snapshot separati |
| `.../weeklytte/WeeklyTteService.java` | Backtest, replay, confronti e sensibilità |
| `.../weeklytte/WeeklyTteController.java` | API REST indipendente |
| `.../weeklytte/WeeklyTteExcelExporter.java` | Workbook run e confronto |
| `src/main/resources/static/weekly-high-tte.html` | Pagina dedicata |
| `src/main/resources/static/weekly-high-tte.js` | Controlli e risultati UI |
| `src/main/resources/static/index.html` | Unica modifica comune: collegamento nel menu |
| `src/test/java/it/andreademartini/trading/backtest/weeklytte/WeeklyTteTest.java` | Fixture deterministiche |
| `.../weeklytte/WeeklyTteHttpIntegrationTest.java` | Workflow HTTP e isolamento |
| `.../weeklytte/WeeklyTteLocalBacktestTest.java` | Backtest offline opt-in e artefatti |
| `src/test/js/weekly-high-tte.test.js` | Raccolta parametri e apertura senza azioni |
| `docs/WEEKLY_HIGH_TTE_RESEARCH.md` | Ricognizione e regole matematiche |
| `docs/WEEKLY_HIGH_TTE_RESEARCH_REPORT.md` | Questo report |
| `outputs/weekly-high-tte-research/*` | Export, risultati ed evidenze |
| `outputs/weekly-tte-baseline-validation/pom.xml` e `.gitignore` | Baseline isolata; compilati esclusi dal versionamento |
