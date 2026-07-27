# IBKR Java Trading System — Milestone 2 Paper Trading

Progetto Java 21 importabile direttamente in IntelliJ IDEA per collegarsi a
Interactive Brokers TWS / IB Gateway in modalità **Paper Trading**.

Questa versione aggiunge:

- ricezione dati bid, ask, last, close, high e low;
- generazione di segnali `BUY` e `SELL`;
- strategia dimostrativa con incrocio di medie mobili;
- segnali manuali tramite API REST;
- ordini paper `MARKET`, `LIMIT`, `STOP` e `TRAILING_STOP`;
- bracket order con take profit e stop loss;
- bracket order con take profit e trailing stop;
- stato ordini tramite callback `orderStatus` e `openOrder`;
- posizioni e riepilogo account;
- `RiskManager`;
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
http://localhost:8080/api/paper/status
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
GET http://localhost:8080/api/paper/status
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
  nessuno short, perché `allow-short-selling=false`;
- nessuna inversione long/short automatica nello stesso tick.

---

## 10. RiskManager

Controlli inclusi:

- modalità PAPER;
- account esatto verificato;
- connessione `READY`;
- kill switch;
- quantità massima;
- valore massimo ordine;
- valore massimo posizione;
- numero massimo ordini aperti;
- numero massimo azioni giornaliere;
- perdita realizzata giornaliera;
- short selling disabilitato di default;
- coerenza di take profit e stop rispetto alla direzione;
- trailing percentuale compreso tra 0 e 50.

Configurazione:

```yaml
trading:
  risk:
    max-order-value: 5000
    max-position-value: 10000
    max-quantity-per-order: 10
    max-daily-orders: 20
    max-open-orders: 10
    max-daily-loss: 250
    allow-short-selling: false
```

Gli ordini che riducono una posizione esistente possono essere inviati anche
quando il kill switch è attivo o è stato raggiunto un limite operativo. Questo
evita che una protezione impedisca la chiusura del rischio già presente.

---

## 11. Database e log

Il database H2 viene creato in:

```text
./data/ibkr-paper-trading.mv.db
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

## 12. Limiti attuali

Questa milestone non include ancora:

- backtest storico;
- calcolo commissioni e slippage;
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
2. storico a barre;
3. backtest della stessa interfaccia strategia;
4. riconciliazione ordini/posizioni dopo riavvio;
5. scheduler con orari di mercato;
6. notifiche;
7. dashboard.

---

## Riferimento all'esempio Humbled Trader

L'articolo indicato organizza il bot per componenti: scanner, strategia,
esecuzione, alert e scheduler, mantenendo il paper trading come ambiente di
sviluppo. Questo progetto applica la stessa separazione concettuale in Java,
ma utilizza Spring Boot e il modello asincrono della TWS API.
