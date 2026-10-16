# V1 e V2: rischio teorico al 4%

Aggiornamento successivo: il rischio della singola run è ora modificabile
dall'interfaccia; vedere [EDITABLE_BACKTEST_RISK.md](EDITABLE_BACKTEST_RISK.md).
Il 4% rimane il valore della versione base.

Su richiesta dell'utente, le nuove versioni predefinite dei backtest sono:

| Strategia | Versione precedente | Nuova versione | Risk Percent |
|---|---|---|---:|
| PATTERN_V1_STABLE | 2.2 | 2.3 | 4% |
| PATTERN_V2 | 1.1 | 1.2 | 4% |

L'unica differenza della configurazione strategica è `global.riskPercent`, da
2 a 4. Versioni precedenti, relativi hash e risultati salvati rimangono intatti.
I controlli di promozione confrontano la nuova configurazione con la precedente
e rifiutano altre differenze. OPEN_FIRST, pattern, filtri, Target R, limiti di
capitale, costi e timeout non cambiano. V3 non viene modificata.

L'interfaccia V1 legge ora il rischio dalla configurazione anziché mostrare un
testo fisso al 2%. V2 usa già il valore configurato; l'etichetta della versione
del risultato è stata resa dinamica. Sono aggiornate le versioni degli script.

Il PAPER continua a richiamare esplicitamente V1 2.1 e V2 1.0: questo intervento
non ne cambia configurazione o comportamento. Nessun ordine IBKR è stato inviato.
Il backend operativo non viene riavviato automaticamente. Il 4% sarà applicato
alle nuove run dopo il riavvio del backend aggiornato; una vecchia run continua
a mostrare il suo rischio originario.

`StableRiskRevisionTest` verifica che cambi soltanto il rischio, che gli hash
siano distinti e che le versioni al 2% rimangano disponibili. I test dei vecchi
benchmark selezionano esplicitamente V1 2.2/V2 1.1. Log di verifica:
`target/stable-risk4-verify.log`, `target/stable-risk4-frontend.log`,
`target/stable-risk4-frontend-build.log`.
