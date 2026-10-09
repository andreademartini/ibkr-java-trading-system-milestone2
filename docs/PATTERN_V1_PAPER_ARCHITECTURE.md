Analisi preliminare, 2026-10-09

Il progetto usa Java 21, Spring Boot 4.1.0, Maven, JPA/H2 persistente, Apache POI e TWS API 10.45.01. La V1 ufficiale e PATTERN_V1_STABLE 2.1, timeout 13 barre. Configurazione, motore congelato e archivi di backtest non devono cambiare.

Componenti da riutilizzare: PatternStableService per versione/hash/configurazione; i detector e ConfiguredPatternStrategy congelati per segnali e stop/target; PatternV1PositionSizing per sizing con costi; IbkrConnectionService, IbkrApiClient, IbkrEWrapper, OrderIdSequence, IbkrContractFactory, IbkrOrderFactory e IbkrHistoricalDataClient per protocollo e dati. Le API generiche escludono PATTERN_V1 operativa e conservano tale comportamento: la nuova API PAPER e dedicata alla V1, senza cambiare StrategyEngine o altre strategie.

Il PaperOrderExecutionService esistente applica il rischio globale, inversioni e gestione multi-target destinati alle strategie operative precedenti. Quel percorso non rappresenta la V1 congelata. La nuova orchestrazione usa la factory e il client esistenti, con autorizzazione PAPER distinta, equity virtuale e outbox persistente prima di ogni trasmissione.

L'attuale PaperAccountVerifier verifica la presenza dell'ID in managedAccounts, non l'identita simulata. AccountType identifica la struttura del conto, non dimostra PAPER. Porta, prefisso DU e modalita dichiarata non sono prove sufficienti. Il percorso di produzione deve mantenere PAPER_AUTO bloccato finche non esiste una fonte indipendente attendibile di identita PAPER legata alla sessione corrente. Nessun parametro web o flag dichiarativo deve poter falsificare tale prova.

Gli attuali OrderTracker, PortfolioService e KillSwitchService contengono stato in memoria. La nuova macchina a stati, segnali, ordini, esecuzioni, commissioni, posizioni, equity e audit sono conservati in tabelle dedicate, con lock transazionale e idempotenza. Su riavvio PAPER_AUTO viene sospeso; una trasmissione dall'esito incerto non viene ritentata. La riconciliazione deve completare posizioni, ordini aperti/completati ed esecuzioni prima di autorizzare ingressi.

Il calendario operativo deriva da liquidHours e timeZoneId di IBKR, con chiusure esplicite, sessioni ridotte e fusi IANA. Nessun calendario approssimato con soli giorni feriali. Copertura o dati scaduti bloccano gli ingressi. Barre DAILY incomplete vengono escluse; l'ingresso non legge high/low/close della barra corrente.

Fonti ufficiali consultate:
- Managed accounts: https://interactivebrokers.github.io/tws-api/managed_accounts.html
- Account value keys: https://www.interactivebrokers.com/docs/tws-api/doc/account-portfolio-data/account-updates/account-value-keys
- Bracket/transmit: https://interactivebrokers.github.io/tws-api/bracket_order.html
- Ordini ed execution callbacks: https://www.interactivebrokers.com/docs/tws-api/doc/orders/place-order/order-placement-considerations
- Calendari del contratto: https://interactivebrokers.github.io/tws-api/classIBApi_1_1ContractDetails.html

Lo sviluppo e i test usano broker simulati. Nessuna connessione o trasmissione reale e autorizzata dai test. Gli ostacoli di produzione devono essere elencati nella UI e nel risultato finale.
