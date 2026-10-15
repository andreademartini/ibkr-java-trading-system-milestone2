# Inventario prima della riorganizzazione

## Confini attuali

| Modulo | Dipendenze | Persistenza / API | Vincolo |
|---|---|---|---|
| V1 legacy | PatternV1PortfolioEngine, detector, sizing stateless | pattern-v1; API legacy | Non modificare formule o risultati |
| V1 stable 2.1 | PatternStableService -> engine congelato patternstable.engine; DTO V2 immutabili | data/pattern-v1-stable; /api/backtest/pattern-v1 | Hash/configurazione 2.1 invariati |
| V2 research | PatternV2Service -> PatternPortfolioSimulator, Research detector, analisi Range/Close/Bollinger/ADX/shadow | data/pattern-v2; /api/backtest/pattern-v2 | Storico e identificativi originali conservati |
| PAPER V1 | PatternPaperService -> PatternPaperPlanner, Ledger, Shadow, Store, Broker | tabella pattern_v1_paper_record: SESSION, EVENT, ALERT; /api/pattern-v1/paper | Non rinominare né cancellare record |
| Scheduler PAPER V1 | Tick sequenziale, ingressi e protezioni gestiti dal servizio | stato persistente + outbox ordini | Nessun doppio scheduler sullo stesso ledger |
| Broker PAPER | Un PatternPaperIbkrBroker, connessione IbkrConnectionService e callback | orderRef PV1, orderId, permId, executionId | Code drain condivise: duplicare i consumer perde eventi |
| Sicurezza | PatternPaperAdmin, cookie HttpOnly, CSRF, Origin, KillSwitchService | credenziali solo ambiente/sessione | Nessuna attivazione durante lo sviluppo |

## Evidenze critiche

- La versione V1 stabile confermata è **2.1**, timeout 13 barre.
- Il riferimento V2 richiesto `a856a14e-4046-4fdb-9940-e84fc4c967a6` è disponibile come run JSON.
- Il PAPER V1 riconcilia le posizioni broker contro il solo ledger V1: non è valido per due strategie aggregate senza un router/riconciliatore comune.
- Il broker di produzione mantiene false `paperIdentityVerified` e `writesVerified`; elenca inoltre OPEN realtime, SHORT e bracket recovery non certificati. Questi blocchi non vanno rimossi per rendere attivi dei pulsanti.
- Le API di refresh sono in sola lettura rispetto agli ordini; Start e comandi operativi richiedono sessione amministrativa e CSRF.
- Nella sessione precedente l'applicazione era sulla porta 8081. Il controllo finale di questa lavorazione non trova un listener su 8081. Lo sviluppo non ferma né riavvia servizi operativi e non effettua migrazioni a caldo; lo stato di eventuali ordini ancora presenti su IBKR richiede comunque riconciliazione.

## Migrazione incrementale prevista

1. Snapshot delle configurazioni V2 e del motore Research in un namespace/persistenza V3 indipendente; nessuna conversione dello storico V2.
2. V2 stabile con risorsa immutabile versionata, hash e riferimento al dataset originale; nuove run separate dalle vecchie run Research.
3. Trasferimento della sola UI PAPER in pagina principale; backend, sessione SESSION e protezioni V1 conservati.
4. Sessione V2 separata, metadati di provenienza e ledger aggregato; nessun consumo concorrente non coordinato delle callback broker.
5. Auto-esecuzione in produzione sempre subordinata a riconciliazione e prerequisiti broker verificati. Finché l'isolamento multi-strategia non è certificato, simultaneità bloccata esplicitamente dal backend.

## Deploy e ripristino

Nessuna cancellazione o modifica di tabelle/record preesistenti. Nuovi record e directory hanno namespace separati. Conservare un backup consistente del database a servizio fermo e di data/ prima del deploy. Con attività PAPER esistenti occorre una finestra di manutenzione: fermare nuovi ingressi, verificare protezioni broker, riconciliare, salvare backup, distribuire e riconciliare ancora prima di qualsiasi avvio esplicito. Il codice non attiva PAPER_AUTO né invia ordini durante migrazione/test.
