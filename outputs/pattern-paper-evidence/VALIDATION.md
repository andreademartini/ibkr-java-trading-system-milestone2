# Verifica della consegna PAPER — 2026-10-09

| Verifica | Risultato |
|---|---|
| Maven offline `verify`, Java 21 | BUILD SUCCESS: 439 test rilevati, 431 eseguiti, 0 fallimenti, 0 errori, 8 esclusi |
| Test frontend Node | 63 superati, 0 fallimenti |
| Build frontend e verifica sintassi | Superata |
| Pacchetto Spring Boot | `target/ibkr-java-trading-system-0.2.0-SNAPSHOT.jar` prodotto |
| Integrità V2 e snapshot/archivi V1 | 127/127 hash SHA-256 invariati |
| HTTP integrazione, autenticazione, CSRF, dashboard, Excel, database concorrente | Superati con broker simulato e database H2 dedicato |
| Esecuzione IBKR PAPER reale | Non effettuata; PAPER_AUTO bloccato nell'adapter di produzione |
| Ordini LIVE/PAPER durante sviluppo e test | Nessuno |
| Verifica visuale Browser | Non disponibile nella sessione; nessuna ispezione visuale dichiarata |

Gli 8 test esclusi richiedono l'attivazione esplicita di validazioni locali/esperimenti esistenti: PatternStablePromotionTest (2), PatternV1LocalValidationTest (1) e cinque esperimenti PATTERN_V2 (BollingerAtr, CapitalSkipShadow, CloseStrength, PatternRange, CapitalLimits). Non vengono dichiarati superati in questa esecuzione. La promozione storica non è stata ripetuta durante il lavoro PAPER e i suoi dati non sono stati riscritti.

| Ambito richiesto | Evidenza automatica |
|---|---|
| Conto PAPER simulato verificato, LIVE e identità ignota rifiutati, API iniziale read-only | PatternPaperServiceTest, PatternPaperIbkrBrokerTest |
| Avvio, stop, kill/reset, persistenza, amministratore e CSRF | PatternPaperServiceTest, PatternPaperHttpIntegrationTest |
| DAILY, festivi, sessioni ridotte, DST, dati completi e assenza look-ahead | PatternPaperCalendarTest, PatternPaperServiceTest, PatternPaperShadowTest |
| Segnali univoci, intenti prima dell'invio, duplicati e invio incerto | PatternPaperServiceTest, PatternPaperLedgerTest |
| Sizing 30%, rischio 2%, no leva, capitale virtuale separato, LONG/SHORT | PatternPaperPlannerTest, PatternPaperServiceTest, PatternPaperLedgerTest |
| Fill, stop/target, quantità partial fill, commissioni/correzioni anche prima dell'esecuzione | PatternPaperLedgerTest, PatternPaperServiceTest |
| Timeout 13 barre incluse quelle d'ingresso, restart, disconnessione e riconciliazione | PatternPaperServiceTest, PatternPaperShadowTest |
| Dati assenti, stale, account/ordini incoerenti, esito incerto e blocchi operativi | PatternPaperServiceTest, PatternPaperIbkrBrokerTest |
| Dashboard, comandi web, export, confronto SHADOW distinto e database | PatternPaperHttpIntegrationTest, PatternPaperShadowTest, pattern-v1-paper.test.js |

Queste prove verificano il comportamento dell'applicazione in simulazione. Non verificano prestito titoli, disponibilità dei permessi di scrittura, identità simulata indipendente, comportamento nativo bracket/OCA/MOC o esecuzioni reali su IBKR. La guida espone tali limitazioni e il blocco resta attivo. Non è stato installato un servizio di riavvio automatico Windows e non è disponibile una notifica esterna configurata: gli alert sono persistenti e visibili nella UI.

File di riferimento: `changed-files.txt`, `preserved-hashes.json`, `maven-tests.log`, `frontend-tests.log`, `frontend-build.log`. Architettura e guida in `docs/PATTERN_V1_PAPER_ARCHITECTURE.md` e `docs/PATTERN_V1_PAPER_GUIDE.md`.
