# Gap di apertura nei backtest Stable

V1 Stable **2.2** e V2 Stable **1.1** utilizzano `exitFillPolicy: OPEN_FIRST`.
Le versioni precedenti mantengono la regola storica `LEVEL_ONLY`, implicita quando
il campo manca. I loro snapshot e risultati archiviati non vengono riscritti.

## Ordine delle verifiche

| Direzione | Condizione all'apertura | Uscita prima dello slippage |
|---|---|---|
| LONG | OPEN <= stop | OPEN, motivo STOP |
| LONG | OPEN >= target | OPEN, motivo TARGET |
| SHORT | OPEN >= stop | OPEN, motivo STOP |
| SHORT | OPEN <= target | OPEN, motivo TARGET |

Un trade chiuso all'apertura non subisce i movimenti successivi della candela e
non viene etichettato come ambiguo, anche se massimo/minimo attraversano entrambi
i livelli. Un'apertura interna ai livelli conserva il controllo intraday ai
livelli e la precedenza STOP_FIRST. Stop e target precedono il timeout, anche
nella tredicesima seduta. La seduta d'ingresso resta la prima barra di holding.

Commissioni e slippage di uscita vengono calcolati sul prezzo effettivo. PnL,
R di prezzo e cassa/equity seguono lo stesso prezzo. Un gap allo stop può produrre
una perdita maggiore di 1R; un gap al target può produrre un profitto maggiore
del Target R. Il budget iniziale di sizing non garantisce la perdita massima in
presenza di gap. Un gap estremo può rendere negativa l'equity; il backtest conserva
la perdita e non apre nuovi trade senza equity e liquidità sufficienti.

MFE/MAE eseguibili includono l'apertura e le sedute precedenti, escludendo i
movimenti successivi all'uscita. Gli estremi grezzi dell'intera candela restano
disponibili separatamente. L'evento aggiuntivo `EXIT_EXECUTION` indica `atOpen`,
`exitPrice`, `levelPrice` ed `exitFillPolicy`. STOP/TARGET/TIMEOUT mantengono i
nomi precedenti. La configurazione esportata in Excel contiene la politica.

## Versioni e isolamento

Le pagine Stable e gli endpoint esistenti usano le nuove versioni per le nuove
run. Gli overload `version(String)` e `run(String)` dei servizi permettono di
riprodurre V1 2.0/2.1 e V2 1.0. Il nuovo campo viene omesso dai JSON storici
quando assente, mantenendone gli hash.

PAPER conserva V1 2.1 e V2 1.0: l'avanzamento dei default del backtest non cambia
snapshot, identificativi o controlli delle sessioni PAPER. V1 legacy, Research
esistente e V3 mantengono il comportamento precedente. Nessun ordine broker
viene trasmesso dalla verifica.

## Verifiche riproducibili

```powershell
mvn verify
npm.cmd test
npm.cmd run build
mvn '-Dtest=GapStableDatasetComparisonTest' -DgapDatasetComparison=true test
```

Il confronto opzionale legge i dataset originali, simula in archivi temporanei e
scrive soltanto gli artefatti di verifica in `outputs/gap-execution-evidence/`:
`comparison.json`, `PATTERN_V1_STABLE_2_2.xlsx` e
`PATTERN_V2_STABLE_1_1.xlsx`. Verifica la parità completa della vecchia V2 1.0
con il riferimento e l'integrità dei file originali.

I test ordinari coprono gap LONG/SHORT favorevoli e sfavorevoli, uguaglianza ai
livelli, precedenza sull'intraday e sul timeout, ambiguità senza gap, escursioni,
commissioni/slippage, R effettivo, insolvenza da gap SHORT e compatibilità degli
hash/versioni precedenti.
