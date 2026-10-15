# Riorganizzazione PATTERN V1 / V2 / V3 e PAPER TRADING

## Esito e limite operativo

La riorganizzazione delle strategie e la nuova dashboard sono implementate. Sono presenti sessioni PAPER separate, router del broker, contabilità per strategia, audit, confronto ed export.

**L'esecuzione PAPER_AUTO di produzione non è pronta e non è stata attivata.** In particolare, la simultaneità non è certificata e rimane bloccata nel backend. Anche un ingresso singolo è bloccato se non sono verificati identità PAPER, scrittura, OPEN di sessione, protezioni e budget cash effettivo senza leva. I test con mock non vengono presentati come una certificazione IBKR.

Nessun ordine è stato inviato. Non sono stati avviati o riavviati servizi operativi e non è stata eseguita una migrazione a caldo del database PAPER.

## Struttura ottenuta

| Voce | Versione / stato | Pagina |
|---|---|---|
| PAPER TRADING | Controllo centrale V1/V2, stato backend | `/paper-trading.html` |
| PATTERN V1 — BACKTEST | 2.1, STABLE / FROZEN | `/pattern-v1.html` |
| PATTERN V2 — BACKTEST | 1.0, STABLE / FROZEN, ADX14/25 ON | `/pattern-v2.html` |
| PATTERN V3 — RESEARCH | 3.0-research, modificabile e indipendente | `/pattern-v3.html` |

Il tab PAPER è stato rimosso dalla V1. V1 e V2 non contengono controlli operativi embedded. Le altre sezioni del menu restano disponibili.

## V1 preservata

Configurazione congelata 2.1, motore stable, motore legacy, regole finanziarie, store, servizio/scheduler/broker/ledger PAPER originali non sono stati modificati. Le modifiche V1 sono nella pagina/menu e nell'estensione del filtro amministrativo agli endpoint nuovi. Il backend originale viene riutilizzato attraverso una vista broker attribuita alla strategia, senza rinominare ordini o sessione storica.

Le regressioni esistenti V1/PAPER passano. Il controllo Git dei file finanziari V1 e dei servizi PAPER originali non rileva modifiche. La regressione offline verifica anche che la risorsa V1 2.1 rimanga identica byte per byte.

## V2 congelata e regressione

Il riferimento effettivo è `a856a14e-4046-4fdb-9940-e84fc4c967a6`, sul dataset `190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd`, dal 2010-01-04 al 2026-09-08.

La configurazione viene congelata nella risorsa 1.0: capitale 10.000, rischio 2%, ordine al 30% dell'equity, limiti dinamici al 100%, leva OFF, 13 barre, STOP_FIRST, ADX14 >=25 / EXCLUDE_WITH_TREND ON; Range/Close Strength/Bollinger-ATR OFF. Tutti gli altri parametri derivano dal riferimento, senza ottimizzazione.

| Metrica | Riferimento | V2 stabile |
|---|---:|---:|
| Final Equity | 76.243,9396 | identica |
| CAGR | 12,9541% | identico |
| Max DD | 14,7375% | identico |
| PF netto | 1,6149 | identico |
| Trade | 1.108 | identici |
| Capital Skips | 20 | identici |

Il confronto copre l'intero portafoglio JSON, inclusi trade, eventi, sizing, costi ed equity curve, dopo la sola sostituzione dell'UUID della run. Il test non hardcoda i risultati: legge il riferimento. Hash configurazione: `2a536960c38bcca991ce53f01b3bbba29628c1efd70447ebebcb15dfab34f333`.

- [Excel V2 stabile 1.0](PATTERN_V2_STABLE_1_0.xlsx)
- [Evidenza della regressione](frozen-v2-regression.json)

## V3 Research

V3 mantiene tutte le funzionalità Research V2 nel proprio package, frontend e store: configurazioni modificabili, pattern/direzioni/Target R, stop a estremi e relativo buffer, holding, sizing, costi, Range, Close Strength, Bollinger/ATR, analisi ADX/DI/EMA, Market Regime Filter, shadow, comparazioni, replay, storico ed Excel.

Lo snapshot iniziale conserva ADX ON/14/25 e gli altri parametri correnti V2. Le modifiche successive sono salvate esclusivamente nel namespace V3. Le vecchie run V2 non sono convertite in V3. La nuova V2 stabile legge la propria risorsa immutabile, non il current Research. Una futura promozione richiede un nuovo snapshot/versione espliciti.

## PAPER: cosa funziona e cosa rimane bloccato

Implementati e verificati con mock:

- Due schede con dati backend, snapshot/versione/hash e controlli AVVIA / FERMA INGRESSI / KILL / RESET / SHADOW separati.
- Sessione V1 originale e sessione V2 nuova, capitale virtuale separato da 10.000.
- Un solo broker fisico; callback attribuite tramite ordini persistiti, non tramite simbolo.
- Posizioni interne distinte con quantità aggregate riconciliate sul conto.
- Stop/uscite limitati alla quantità del proprietario, partial fills e deduplica esecuzioni.
- Conflitti LONG/SHORT rifiutati con `MULTI_STRATEGY_POSITION_CONFLICT`.
- Controllo delle prenotazioni aggregate rispetto ai fondi osservati, senza assumere disponibilità di 20.000.
- Kill switch separati e globale persistente, senza liquidazione automatica.
- Ordini, esecuzioni, posizioni, segnali, errori e riconciliazione visibili separatamente per strategia.
- Confronto su osservazioni PAPER reali disponibili; SHADOW separato, niente curve o eseguiti inventati.
- Export PAPER con tutti gli undici fogli richiesti e filtri strategia/date; commissioni mancanti N/D.

Non completati/certificati per produzione:

- Prova affidabile dell'identità PAPER e dei permessi di scrittura nel broker esistente.
- OPEN realtime qualificato per sessione: il broker attuale restituisce OPEN mancante.
- Verifica prestito/margine SHORT, partial fills reali, recovery bracket/TIMEOUT/OCA e riconnessioni sul broker.
- Limiti account cash/net-liquidation verificati senza leva: `AvailableFunds` da solo non è sufficiente.
- Certificazione dell'isolamento simultaneo delle due strategie su IBKR e della base prezzi DAILY dopo corporate action.

Questi punti sono blocchi effettivi, non avvisi cosmetici. La configurazione Spring mantiene la certificazione disabilitata; non esiste un pulsante o un endpoint per forzarla. Non è corretto dichiarare conclusa la fase C operativa o fornire istruzioni per avviare entrambe finché questi prerequisiti non sono completati.

## Migrazione, dati e ripristino

Migrazione additiva, senza cancellazioni o rinominazioni:

- `data/pattern-v2-stable`: nuove versioni/run append-only.
- `data/pattern-v3`: nuove configurazioni, dataset e run Research.
- Tabella JPA originale `pattern_v1_paper_record`: SESSION/EVENT/ALERT V1 conservati; aggiunti V2_SESSION/V2_EVENT/V2_ALERT, MULTI_STATE/MULTI_EVENT con chiavi distinte.
- Identificativi di sessione/istanza persistiti; position ID derivabile stabilmente da instance ID e signal ID; provenienza esportata con orderId/permId/executionId/account.

Le inizializzazioni avvengono al primo avvio/utilizzo del nuovo backend e non sovrascrivono i record esistenti. I test usano database in memoria o directory temporanee. Nessuna run storica e nessun current V2 è stato riscritto dall'esperimento di regressione.

Se esistono ordini/posizioni PAPER: serve una finestra di manutenzione con ingressi fermati, protezioni verificate, backup consistente a servizio fermo e riconciliazione prima/dopo il deploy. Non ripristinare un database precedente sopra esecuzioni successive e non tornare al vecchio motore mentre esistono posizioni V2 non gestibili da esso.

## Test finali

- Maven `verify`: **521 test, 510 eseguiti con successo, 11 esperimenti opzionali saltati**, nessun errore/fallimento.
- Verifica aggiuntiva finale di snapshot/versioni V3 e route HTTP isolate: PASS. La versione viene ricaricata dal valore persistito, preservata nelle copie della run; un hash configurazione incoerente viene rifiutato.
- Regressione offline V2 congelata: eseguita separatamente, PASS; portafoglio completo identico al riferimento salvo UUID.
- Frontend: **81/81 PASS**.
- Build frontend e pacchetto Maven: PASS.
- Nessun ordine reale/PAPER durante implementazione e test; API broker mock, operazioni automatiche disabilitate nei contesti HTTP.

## Accesso e avvio

Dopo aver distribuito/riavviato il backend nuovo, aprire `/paper-trading.html` e aggiornare la pagina con Ctrl+F5. L'accesso usa il token amministrativo già previsto dal backend; nessun token è incluso nei report o salvato dal frontend.

Per il solo controllo dell'interfaccia usare BACKTEST con connessione e trasmissione automatica disabilitate. Questo non abilita il PAPER_AUTO. Non sono stati modificati i parametri della connessione IBKR operativa.

**Avvio contemporaneo V1+V2: attualmente non disponibile in sicurezza.** Completare i prerequisiti sopra indicati e la validazione PAPER esplicitamente autorizzata prima di rimuovere qualsiasi blocco. La sola selezione di una porta PAPER non è una verifica dell'account.

## File

- [Inventario delle dipendenze prima delle modifiche](../../docs/STRATEGY_REORGANIZATION_INVENTORY.md)
- [Architettura, migrazione, rollback e istruzioni dettagliate](../../docs/STRATEGY_REORGANIZATION.md)
- [Elenco completo dei file della riorganizzazione](FILES_CHANGED.md)
