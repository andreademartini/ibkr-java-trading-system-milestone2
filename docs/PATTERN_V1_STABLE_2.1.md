PATTERN_V1_STABLE 2.1 e la versione ufficiale con timeout a 13 barre, richiesta il 2026-10-09.

La sola differenza di configurazione rispetto alla 2.0 e `global.maxHoldingBars`: da 20 a 13. La barra di ingresso conta come barra 1. Se stop e target non sono raggiunti prima, la posizione viene chiusa al close della tredicesima barra, secondo le regole esistenti del motore congelato.

La configurazione 2.0, il suo snapshot persistito, le run e gli Excel precedenti restano invariati. Il servizio conserva `version("2.0")` e `run("2.0")` per il replay esplicito della versione originale. La nuova configurazione e in `src/main/resources/pattern-stable/2.1.json`, archiviata separatamente in `data/pattern-v1-stable/versions/2.1.json`.

Le API ufficiali e la pagina PATTERN V1 usano la 2.1. Ogni nuova run viene salvata con versione 2.1 e il proprio hash, senza sovrascrivere run esistenti. PATTERN_V2 e la vecchia V1 legacy restano inalterate. Tutti i controlli di esecuzione PAPER/LIVE restano disabilitati.

L'equivalenza esatta con la run V2 di riferimento riguarda la 2.0 a 20 barre. La 2.1 cambia intenzionalmente il timeout: i suoi risultati richiedono un nuovo backtest e non vanno attribuiti alla run di riferimento.
