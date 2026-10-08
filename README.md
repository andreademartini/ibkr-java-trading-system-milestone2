# IBKR Java Trading System — Milestone 2 Paper Trading

Progetto Java 21 importabile direttamente in IntelliJ IDEA per collegarsi a
Interactive Brokers TWS / IB Gateway in modalità **Paper Trading**.

Questa versione aggiunge:

- ricezione dati bid, ask, last, close, high e low;
- generazione di segnali `BUY` e `SELL`;
- strategia dimostrativa con incrocio di medie mobili;
- segnali manuali tramite API REST;
- ordini paper `MARKET`, `LIMIT`, `STOP` e `TRAILING_STOP`;
- bracket order con tre take profit, stop loss e sizing risk-based;
- bracket order con take profit e trailing stop;
- stato ordini tramite callback `orderStatus` e `openOrder`;
- posizioni e riepilogo account;
- `RiskManager` con controlli LONG/SHORT;
- kill switch;
- salvataggio di segnali ed eventi ordine su database H2;
- endpoint REST e file `http/paper-trading.http` per testarli da IntelliJ.

> La strategia inclusa serve esclusivamente a verificare il funzionamento
> tecnico del sistema. Non costituisce una strategia redditizia né un consiglio
> finanziario.

## Sicurezza: il live trading rimane bloccato

Il codice rifiuta sempre:

- `TRADING_MODE=LIVE`;
- `LIVE_TRADING_ENABLED=true`;
- le porte live predefinite `7496` e `4001`.

Gli ordini paper sono disabilitati per default e vengono accettati soltanto se:

1. `IBKR_PAPER_ORDERS_ENABLED=true`;
2. è configurato l'account esatto in `IBKR_PAPER_ACCOUNT_ID`;
3. TWS restituisce lo stesso account tramite `managedAccounts`;
4. è presente la conferma:
   `PAPER_ACCOUNT_CONFIRMATION=I_CONFIRM_THIS_IS_A_PAPER_ACCOUNT`;
5. la porta è `7497` o `4002`, salvo esplicita autorizzazione di una porta paper personalizzata;
6. il `RiskManager` approva l'ordine;
7. il kill switch non blocca nuove esposizioni.

Il kill switch lascia comunque possibile la riduzione o chiusura di una
posizione già aperta.

---

## Struttura principale

```text
src/main/java/it/andreademartini/trading
├── TradingApplication.java
├── config
├── ibkr
│   ├── IbkrConnectionService
│   ├── IbkrApiClient
│   ├── IbkrEWrapper
│   ├── OrderIdSequence
│   └── PaperAccountVerifier
├── marketdata
│   ├── MarketDataService
│   ├── MarketSnapshot
│   └── IbkrContractFactory
├── strategy
│   ├── MovingAverageCrossoverStrategy
│   ├── StrategyEngine
│   ├── SignalService
│   └── TradingSignal
├── orders
│   ├── PaperOrderExecutionService
│   ├── IbkrOrderFactory
│   ├── OrderTracker
│   └── PaperOrderRequest
├── risk
│   ├── RiskManager
│   ├── TradingSafetyGuard
│   └── KillSwitchService
├── portfolio
├── persistence
├── paper
└── web
```

---

## 1. Prerequisiti

- IntelliJ IDEA;
- Java 21;
- Maven;
- TWS oppure IB Gateway;
- account Interactive Brokers Paper Trading;
- TWS API Java ufficiale.

Controllo da PowerShell:

```powershell
java -version
mvn -version
```

---

## 2. Importare il progetto in IntelliJ

1. Decomprimi il file ZIP.
2. Apri IntelliJ IDEA.
3. Seleziona **File → Open**.
4. Seleziona la cartella che contiene `pom.xml`.
5. Scegli **Open as Project**.
6. Attendi l'importazione Maven.
7. In **File → Project Structure → Project**, imposta **SDK 21**.
8. In **Settings → Build Tools → Maven**, verifica che Maven usi Java 21.

Non è necessario copiare il codice in un progetto vuoto: la cartella estratta
è già un progetto Maven completo.

---

## 3. Installare `TwsApi.jar`

Per ragioni di licenza il JAR IBKR non è incluso.

Dopo aver scaricato e installato la TWS API, il file si trova normalmente in:

```text
C:\TWS API\source\JavaClient\TwsApi.jar
```

Dalla cartella del progetto:

```powershell
.\scripts\install-ibkr-api.ps1
```

Percorso personalizzato:

```powershell
.\scripts\install-ibkr-api.ps1 `
  -JarPath "D:\IBJts\source\JavaClient\TwsApi.jar"
```

Lo script installa il JAR nel repository Maven locale con coordinate:

```text
com.interactivebrokers:tws-api:10.45.01
```

Se installi una versione differente, passa la stessa versione allo script e
aggiorna `ibkr-api.version` nel `pom.xml`.

---

## 4. Configurare TWS Paper

Accedi a TWS selezionando esplicitamente **Paper Trading**.

Apri:

```text
Global Configuration → API → Settings
```

Imposta:

- **Enable ActiveX and Socket Clients**: attivo;
- **Socket Port**: `7497`;
- **Allow connections from localhost only**: attivo se tutto gira sullo stesso PC;
- **Read-Only API**:
  - attivo durante i primi test di connessione e market data;
  - disattivato soltanto quando vuoi provare gli ordini paper.

Per IB Gateway Paper usa normalmente la porta `4002`.

Porte predefinite:

| Applicazione | Live bloccato | Paper |
|---|---:|---:|
| TWS | 7496 | **7497** |
| IB Gateway | 4001 | **4002** |

---

## 5. Prima esecuzione senza ordini

Lascia:

```text
IBKR_PAPER_ORDERS_ENABLED=false
AUTO_EXECUTE_SIGNALS=false
```

Avvia `TradingApplication` da IntelliJ oppure:

```powershell
mvn clean test
mvn spring-boot:run
```

Con IB Gateway:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=ibgateway
```

Apri nel browser:

```text
http://localhost:8081/api/paper/status
```

Dovresti vedere:

- stato connessione `READY`;
- account verificato oppure `false` se non hai ancora inserito l'account;
- prezzi ricevuti;
- posizioni;
- ordini aperti;
- impostazioni di sicurezza.

Con market data `DELAYED`, i dati possono essere ritardati. La disponibilità
dei dati dipende anche dalle autorizzazioni e sottoscrizioni dell'account.

---

### Dashboard e storico IBKR

Interactive Brokers e' l'unica fonte dati configurata dal progetto.
La dashboard locale e' disponibile su:

```text
http://localhost:8081/
```

La pagina di configurazione TWS / IB Gateway e' disponibile su:

```text
http://localhost:8081/provider.html
```

Per scaricare storico da IBKR, TWS o IB Gateway deve essere connesso e lo
strumento deve avere permessi dati compatibili con l'account:

```powershell
$env:IBKR_AUTO_CONNECT="true"
$env:IBKR_MARKET_DATA_MODE="DELAYED"

mvn spring-boot:run
```

---

## 6. Configurare l'account paper e abilitare gli ordini

In PowerShell, usando **l'account paper mostrato in TWS**:

```powershell
$env:IBKR_PAPER_ACCOUNT_ID="IL_TUO_ACCOUNT_PAPER"
$env:IBKR_PAPER_ORDERS_ENABLED="true"
$env:PAPER_ACCOUNT_CONFIRMATION="I_CONFIRM_THIS_IS_A_PAPER_ACCOUNT"
$env:AUTO_EXECUTE_SIGNALS="false"

mvn spring-boot:run
```

Non inserire l'account live.

Controlla:

```text
GET http://localhost:8081/api/paper/status
```

Prima di inviare ordini deve risultare:

```json
{
  "mode": "PAPER",
  "connectionState": "READY",
  "paperAccountVerified": true,
  "orderSubmissionEnabled": true,
  "killSwitchActive": false
}
```

---

## 7. Testare da IntelliJ

Apri:

```text
http/paper-trading.http
```

IntelliJ mostra un pulsante verde accanto a ogni richiesta HTTP.

Nota: gli ordini che aumentano esposizione devono nascere da un `TradePlan`
calcolato dal sistema. Le chiamate dirette a `/api/paper/orders` restano utili
per test tecnici e per ordini che riducono o chiudono una posizione; un nuovo
ingresso manuale senza `TradePlan` viene respinto dal `RiskManager`.

Il file contiene esempi per:

- stato sistema;
- segnale manuale;
- market order;
- limit order;
- stop order;
- trailing stop;
- bracket con stop loss;
- bracket con trailing stop;
- chiusura posizione;
- kill switch.

I prezzi negli esempi sono segnaposto. Prima di eseguire un bracket, sostituiscili
con valori coerenti con il prezzo corrente e con il tick minimo dello strumento.

---

## 8. Tipi di ordine disponibili

### MARKET

```json
{
  "symbol": "SPY",
  "side": "BUY",
  "type": "MARKET",
  "quantity": 1,
  "reason": "test-market-paper"
}
```

### LIMIT

```json
{
  "symbol": "SPY",
  "side": "BUY",
  "type": "LIMIT",
  "quantity": 1,
  "limitPrice": 590.00,
  "reason": "test-limit-paper"
}
```

### STOP

Esempio di stop protettivo per una posizione long:

```json
{
  "symbol": "SPY",
  "side": "SELL",
  "type": "STOP",
  "quantity": 1,
  "stopPrice": 580.00,
  "reason": "protective-stop-paper"
}
```

### TRAILING_STOP

Il trailing è espresso in percentuale:

```json
{
  "symbol": "SPY",
  "side": "SELL",
  "type": "TRAILING_STOP",
  "quantity": 1,
  "trailingPercent": 1.0,
  "reason": "protective-trailing-paper"
}
```

### BRACKET

Invia tre ordini collegati:

1. ordine di ingresso;
2. take profit;
3. stop loss.

```json
{
  "symbol": "SPY",
  "side": "BUY",
  "type": "BRACKET",
  "quantity": 1,
  "takeProfitPrice": 612.00,
  "stopPrice": 588.00,
  "reason": "bracket-paper"
}
```

### BRACKET_WITH_TRAILING_STOP

Invia:

1. ordine di ingresso;
2. take profit;
3. trailing stop.

```json
{
  "symbol": "SPY",
  "side": "BUY",
  "type": "BRACKET_WITH_TRAILING_STOP",
  "quantity": 1,
  "takeProfitPrice": 612.00,
  "trailingPercent": 1.0,
  "reason": "bracket-trailing-paper"
}
```

La factory usa la catena `Transmit`:

- parent: `false`;
- take profit: `false`;
- ultimo ordine protettivo: `true`.

In questo modo TWS riceve il gruppo prima di trasmetterlo al simulatore.

---

## 9. Segnali BUY e SELL

La strategia dimostrativa usa due medie mobili:

```yaml
trading:
  strategy:
    enabled: true
    auto-execute-signals: false
    fast-period: 5
    slow-period: 20
```

Con `auto-execute-signals=false`:

- i segnali vengono calcolati;
- vengono scritti nei log;
- vengono salvati su H2;
- non generano ordini.

Per abilitare l'esecuzione automatica paper:

```powershell
$env:AUTO_EXECUTE_SIGNALS="true"
```

Serve inoltre tutta la configurazione di sicurezza degli ordini.

Comportamento predefinito:

- segnale `BUY` a posizione vuota:
  bracket con take profit e trailing stop;
- segnale `SELL` con posizione long:
  cancellazione ordini protettivi e chiusura a mercato;
- segnale `SELL` a posizione vuota:
  apertura SHORT se `allow-short-selling=true` e il risk manager approva il piano;
- nessuna inversione long/short automatica nello stesso tick.

---

## 10. RiskManager

Controlli inclusi:

- modalità `PAPER` per invio ordini e `BACKTEST` per simulazione;
- account esatto verificato;
- connessione `READY`;
- kill switch;
- sizing basato su capitale e rischio percentuale;
- blocco leva se `use-leverage=false`;
- limite nozionale separato per long e short;
- quantità massima;
- valore massimo ordine;
- valore massimo posizione;
- numero massimo ordini aperti;
- numero massimo azioni giornaliere;
- perdita realizzata giornaliera;
- short selling configurabile;
- coerenza di entry, take profit e stop rispetto alla direzione `LONG` o `SHORT`;
- trailing percentuale compreso tra 0 e 50.

Configurazione:

```yaml
trading:
  risk:
    trading-capital: 10000
    risk-per-trade-percent: 2.0
    allow-long: true
    allow-short-selling: true
    use-leverage: false
    max-long-notional: 10000
    max-short-notional: 10000
    max-order-value: 5000
    max-position-value: 10000
    max-quantity-per-order: 1000
    max-daily-orders: 20
    max-open-orders: 10
    max-daily-loss: 250
    entry-ticket: 0.01
    commission-per-order: 0
    slippage-percent: 0
    transaction-costs-per-share: 0

  take-profit:
    enabled: true
    target-1:
      profit-percent: 2.0
      position-percent: 50.0
    target-2:
      profit-percent: 4.0
      position-percent: 30.0
    target-3:
      profit-percent: 8.0
      position-percent: 20.0
```

Per un LONG il sistema genera `BUY STOP` in ingresso, `SELL LIMIT` sui take
profit e `SELL STOP` sullo stop loss. Per uno SHORT genera `SELL STOP` in
ingresso, `BUY LIMIT` sui take profit e `BUY STOP` sullo stop loss.

Gli ordini che riducono una posizione esistente possono essere inviati anche
quando il kill switch è attivo o è stato raggiunto un limite operativo. Questo
evita che una protezione impedisca la chiusura del rischio già presente.

---

## 11. Database e log

Il database H2 viene creato in:

```text
./data/ibkr-paper-trading.mv.db
```

La URL predefinita usa H2 `AUTO_SERVER=TRUE`:

```text
jdbc:h2:file:./data/ibkr-paper-trading;AUTO_SERVER=TRUE
```

Questo evita l'errore `Database may be already in use` quando due processi
locali, per esempio IntelliJ e Maven, aprono lo stesso database. Se un processo
era gia' avviato con la vecchia URL senza `AUTO_SERVER=TRUE`, chiuderlo una
volta e riavviare l'applicazione.

La URL puo' essere sovrascritta con:

```text
DB_URL=jdbc:h2:file:./data/ibkr-paper-trading;AUTO_SERVER=TRUE
```

Tabelle:

- `TRADING_SIGNAL`;
- `ORDER_EVENT`.

Il database salva una cronologia append-only dei segnali e degli aggiornamenti
degli ordini.

I log applicativi sono configurati in:

```text
src/main/resources/logback-spring.xml
```

---

## 12. Dati storici STOOQ

Stooq automatic download is intentionally disabled.
Historical Stooq data must be downloaded manually through a browser and
imported as CSV.

Flusso operativo:

1. Aprire Stooq nel browser.
2. Cercare il titolo, per esempio `AAPL.US`.
3. Aprire i dati storici.
4. Usare `Download data in csv file`.
5. Salvare il file nella directory configurata:

```text
./data/stooq
```

Esempio:

```text
./data/stooq/AAPL.US.csv
./data/stooq/MSFT.US.csv
./data/stooq/NVDA.US.csv
```

Configurazione:

```yaml
stooq:
  enabled: true
  import-directory: ./data/stooq
  accept-zero-volume: true
  default-market-suffix: US
  price-adjustment: RAW
```

Import singolo file gia' presente nella directory:

```http
POST /api/historical/import/stooq
Content-Type: application/json

{
  "file": "AAPL.US.csv",
  "interval": "DAILY"
}
```

Import tramite upload dalla UI:

1. Aprire la pagina `Azioni e strategie`.
2. Selezionare il file con il campo `CSV STOOQ`.
3. Premere `Importa CSV STOOQ`.

Import di tutti i CSV nella directory:

```http
POST /api/historical/import/stooq/directory?interval=DAILY&priceAdjustment=RAW
```

Verifica dei dati:

```http
GET /api/historical/AAPL/status?interval=DAILY
GET /api/historical/AAPL?from=2020-01-01&to=2026-08-01&interval=DAILY
```

Il ticker viene ricavato dal nome file. `AAPL.US.csv` viene importato come
simbolo interno `AAPL`, usando il suffisso configurato in
`stooq.default-market-suffix`.

I duplicati non vengono reinseriti. Se una barra con stesso symbol, data,
intervallo e provider e' gia' presente, viene conteggiata come record
saltato. I valori esistenti non vengono sovrascritti automaticamente.

---

## 13. Strategia Joe Ross 1-2-3 / TTE sul Point 3

La strategia `JOE_ROSS_123_TTE` usa esclusivamente barre OHLC chiuse e segue
la sequenza `P1 -> P2 -> correzione che forma P3 -> TTE -> ingresso`. Non
attende il breakout di P2 e non usa il Ross Hook: l'ingresso TTE è anticipato
e può avvenire mentre il Point 3 è ancora in formazione.

I pivot P1 e P2 sono causali: con `pivot-strength: N`, il pivot della barra
`i` viene emesso una sola volta alla chiusura della barra `i + N`. Dopo la
conferma di P2, ogni candidato viene calcolato soltanto alla chiusura della
relativa barra TTE ed è attivo esclusivamente sulla barra successiva. Se non
viene eseguito, il candidato viene cancellato e sostituito alla chiusura della
nuova barra, fino a un massimo di tre barre TTE. P3 è esattamente la barra TTE
il cui candidato viene rotto; il marker P3 resta quindi sulla barra precedente
rispetto all'ENTRY, senza revisioni con barre future.

```yaml
trading:
  strategy:
    type: JOE_ROSS_123_TTE
    joe-ross-123-tte:
      enabled: true
      direction: LONG_SHORT
      minimum-tick: 0.01
      swing:
        pivot-strength: 2
      tte:
        enabled: true
        max-correction-bars: 3
        entry-buffer-ticks: 1
        stop-buffer-ticks: 1
        initial-stop-mode: TTE_BAR
        fallback-to-point2-breakout: false
        minimum-room-to-point2:
          enabled: false
          type: R
          value: 0.5
        allow-after-three-bars-on-double-triple: false
        double-triple-tolerance-ticks: 1
```

Il backtest usa `STOP_FIRST` come politica conservativa predefinita nelle
barre ambigue. Un gap oltre il candidato viene eseguito all'apertura, con lo
slippage configurato e un nuovo calcolo del position sizing. Nel percorso
PAPER ogni candidato TTE sostituisce il bracket non eseguito precedente; dopo
un fill, stop e take profit protettivi non vengono cancellati.
`StrategyEngine.onBar(...)` continua a passare da `SignalService`, position
sizing, `RiskManager` e dalle protezioni PAPER.

Il sizing di `JOE_ROSS_123_TTE` usa esclusivamente
`floor(capital * targetPercent / delta)`, dove il delta SHORT è
`P3 low - P2 low` e il delta LONG è `P2 high - P3 high`. Il tick del prezzo
di ingresso non entra nel delta. Il rischio della barra P3 e quello effettivo
tra entry e stop sono esportati come metriche separate e non riducono la
quantity calcolata. Le altre strategie conservano il position sizing
preesistente.

Il tipo `JOE_ROSS_123_RH_TTE` rimane nell'enum soltanto come compatibilità
deprecata per i profili già persistiti. Non compare tra le opzioni per creare
nuovi profili e non viene convertito automaticamente nella nuova strategia.

## 14. Pattern Research OHLC

La pagina `/research.html` esegue analisi quantitative separate dal trading
operativo. `PatternResearchEngine` legge serie daily complete tramite
`HistoricalDataService`, applica detector OHLC causali e simula l'ingresso
all'open della barra successiva. Il modulo non usa `StrategyEngine`, non crea
segnali operativi e non chiama servizi PAPER o LIVE.

Sono disponibili Bullish/Bearish Engulfing, Hammer, Shooting Star,
Bullish/Bearish Harami, Piercing Pattern e Dark Cloud Cover. La strategia
`JOE_ROSS_123_TTE` può essere aggiunta come benchmark tramite un adapter che
consuma la state machine esistente senza modificarla.

Ogni run salva configurazione, warning e occorrenze nel database. Gli endpoint
sono:

- `POST /api/research/patterns/run`;
- `GET /api/research/patterns/{id}`;
- `GET /api/research/patterns/{id}/summary`;
- `GET /api/research/patterns/{id}/trades`;
- `GET /api/research/patterns/{id}/export/excel`.

Lo split IS/OOS è cronologico. La risoluzione predefinita delle barre che
toccano stop e target è `STOP_FIRST`. Win rate, profit factor, expectancy,
sample size e stability rimangono metriche separate; non viene prodotto un
pattern score arbitrario. L'Excel contiene `PATTERN_SUMMARY`,
`SYMBOL_BREAKDOWN`, `IN_SAMPLE`, `OUT_OF_SAMPLE`, `TRADES` e `CONFIGURATION`.

## 15. Limiti attuali

### PATTERN_V1 CORE (solo backtest)

La pagina /pattern-v1.html esegue il portafoglio DAILY con la mapping frozen
in pattern-v1.symbols di application.yml: AAPL, GOOGL, NFLX, NVDA, QQQ, SPY,
TSLA, XLK, XLY. Solo TSLA abilita SHORT. BA/BAC e tutti gli altri simboli
restano esclusi. Nessun pattern, Target R o parametro del detector viene
ottimizzato durante il backtest.

BacktestEngine carica le barre attraverso HistoricalDataService. I detector
e i loro parametri sono gli stessi della Pattern Research; il detector riceve
solo il prefisso delle barre chiuse. Entry = NEXT_BAR_OPEN, stop =
PATTERN_EXTREME, buffer = 0; target = Entry ± RiskPerShare × TargetR.
Una sola posizione per symbol + strategy, un TP, STOP_FIRST, stop invariato.

Il timeout obbligatorio è CLOSE_AT_MAX_HOLDING_BAR: la barra di ingresso
conta come holding bar 1, come in Research. Se STOP/TARGET non sono stati
raggiunti, il trade chiude al Close della holding bar 20 (entryIndex + 19),
con Result R prezzo effettivo, remainingQuantity = 0 e capitale liberato.
STOP/TARGET hanno precedenza sul timeout nella stessa barra. Non esiste più
il ramo V1 UNRESOLVED_MARK_TO_MARKET. Le sole finestre finali incomplete
(<20 barre) restano OPEN: exitDate/exitPrice sono null; markDate/markPrice
contengono la valorizzazione, separata dal fill.

PatternV1PositionSizing rende tracciabile il servizio PositionSizingService
standard, che rimane invariato per tutte le altre strategie. Desired Quantity
= floor(equity nota all'open × riskPercent / 100 / rischio prezzo). Seguono
maxQuantity e maxOrderValue, modificabili nella schermata PATTERN V1 e fissi per
la simulazione. Se vuoti, maxQuantity usa 1.000.000 e
maxOrderValue usa il 60% del capitale iniziale del backtest (maxOrderValuePercent). Seguono
maxPositionValue, notional direzionale, riserva
standard per costi, cash disponibile e leva. Limiting reasons, quantità finale,
cash prima/dopo, order value, desired/actual risk sono conservati nei trade e
negli eventi. Min quantity = 1 e min order value = 0 conservano il comportamento
attuale; un warning segnala quantità <=4 e forti riduzioni dovute al capitale.
Actual Risk % = rischio prezzo × quantità finale / equity all'entry × 100,
spesso inferiore al rischio target 2%. Non viene usato il sizing Joe Ross.

La schermata PATTERN V1 usa di default slippage 0,03% per esecuzione (3 basis
point) e commissioni IBKR_US_FIXED: 0,005 USD per azione, minimo 1 USD e
massimo 1% del valore di ogni ordine. La formula per entrata e uscita e'
`min(max(quantity * perShare, minimumPerOrder), orderValue * maximumPercentOrderValue / 100)`.
I tre parametri sono modificabili nella schermata; il modello FIXED_PER_ORDER
mantiene l'alternativa di una commissione fissa. Il sizing riserva le commissioni
di entrata e stop nel budget di rischio e include il costo di entrata nel capitale
richiesto, anche per il margine SHORT. Le posizioni OPEN addebitano solo l'entrata.
Parametri ed eventi conservano commissioni e slippage per verificare i costi;
le impostazioni sono esportate anche in Excel. Le richieste API con il precedente
campo `commissionPerOrder` mantengono il modello fisso; se omesso viene usato
IBKR_US_FIXED. Uno slippage esplicito di zero permette un confronto senza slippage.

Cash registra acquisti/vendite e costi. I proventi SHORT sono vincolati insieme
a un margine iniziale pari al 100% del notional: available cash = cash −
2 × entry notional SHORT aperto. Equity = cash + Long Market Value − Short
Market Value (passività). Tutti gli open precedono le uscite intrabar; i
proventi di queste uscite finanziano ordini dalla sessione seguente. Ingressi
simultanei: priorità alfabetica. L'equity di sizing usa soltanto open correnti
noti e quotazioni precedenti. Le protezioni operative PAPER/LIVE non vengono
chiamate dalla simulazione.

PatternV1MetricsCalculator separa Realized Gross/Net PnL da Unrealized/Net
Unrealized PnL. PF PnL usa solo i PnL lordi dei CHIUSI; PF netto e PF R sono
separati. Win rate = net wins/(net wins + net losses), breakevens esclusi e
visibili. Expectancy/Average R sono la media dei R prezzo dei CHIUSI validi,
TIMEOUT inclusi. I costi possono cambiare la categoria PnL senza cambiare il
R prezzo; la formula ponderata usa tutti i chiusi e il contributo breakeven.
PF senza perdite = null (n/d).

Daily equity usa l'ultima quotazione disponibile del giorno per ciascun simbolo,
senza dati futuri. Drawdown e running peak sono calcolati su questa curva,
partendo dal capitale iniziale. Average Gross Exposure % è la media del
valore di mercato lordo corrente delle posizioni EOD/equity. Time In Market %
è la percentuale di sessioni con almeno una posizione durante la giornata,
compresi ingressi/uscite nella stessa sessione. I drawdown per simbolo e
direzione sono attribuzioni del PnL a mercato, non portafogli indipendenti.

I fill STOP/TARGET restano nominali anche sui gap, per coerenza con Research.
Executed MFE/MAE terminano al fill e non inferiscono un percorso daily
sconosciuto: sulla barra STOP usano open noto e stop, con MAE <=1R; sulla barra
TARGET usano open noto e target. Raw Bar MFE/MAE riportano separatamente
l'intera candela di uscita e sono confrontabili con Research. I valori della
dashboard sono quelli eseguibili. Il confronto statistico Research standard
usa STOP/TARGET in R; i test di coerenza dei timeout attivano l'opzione di
chiusura già esistente in PatternOutcomeAnalyzer, senza modificarlo.

Endpoint in http/pattern-v1.http. Risultati JSON persistiti in
data/pattern-v1/backtests/<id>.json. Schema versione 2 distingue le run corrette:
i file legacy sono conservati, la nuova dashboard li identifica e richiede
un nuovo backtest; l'export evita di rietichettare una run legacy come corretta.
Excel: SUMMARY, SYMBOL_RESULTS, TRADES, EQUITY_CURVE, CONFIGURATION; curve
Equity/Drawdown integrate. Le metriche frontend hanno tooltip esplicativi.
Il grafico TradingView mostra pattern, signal, entry, stop, target, exit,
sizing e raw/executed excursions; gli OPEN mostrano il mark separatamente.

Validazione della run originale, senza import/download o ordini operativi:

    mvn test -DpatternV1.validateLocal=true

Il test rigioca il market snapshot di
72ea9407-0a48-40f9-bd5c-3214bf360d03 attraverso BacktestEngine, conserva
l'artefatto OLD e salva report, JSON, Excel e hash SHA-256 in
outputs/pattern-v1-corrected. Verifica la mapping, la contabilità giornaliera,
tutti i trade contro PatternOutcomeAnalyzer e i quattro vecchi TIMEOUT
AAPL/QQQ/XLK/NVDA. I test ordinari usano fixture indipendenti dai dati locali.
Le run diagnostiche precedenti in outputs/pattern-v1 sono storiche e non
rappresentano il nuovo motore.

### Limiti operativi

Questa milestone non include ancora:

- scanner di mercato;
- calendario di borsa;
- controllo del tick minimo tramite `ContractDetails`;
- riconciliazione completa dopo riavvio;
- attesa asincrona della cancellazione prima della chiusura;
- gestione multi-account;
- opzioni, futures e forex con regole specifiche;
- notifiche Telegram o email.

La prossima milestone consigliata è:

1. richiesta `ContractDetails` e tick minimo;
2. backtest piu' completo della stessa interfaccia strategia;
3. scheduler con orari di mercato;
4. riconciliazione ordini/posizioni dopo riavvio;
5. notifiche;

---

## Riferimento all'esempio Humbled Trader

L'articolo indicato organizza il bot per componenti: scanner, strategia,
esecuzione, alert e scheduler, mantenendo il paper trading come ambiente di
sviluppo. Questo progetto applica la stessa separazione concettuale in Java,
ma utilizza Spring Boot e il modello asincrono della TWS API.
