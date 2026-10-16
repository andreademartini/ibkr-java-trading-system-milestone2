# Esito dell'audit storico GAP

Le uscite OPEN_FIRST di V1 Stable 2.2 e V2 Stable 1.1 rispettano la priorità
OPEN → HIGH/LOW. Non è stato necessario riscrivere i motori finanziari. Sono
stati aggiunti audit persistito, colonne Excel e analisi dei gap, conservando
configurazioni e risultati economici.

| Verifica | Esito |
|---|---|
| LONG/SHORT, gap STOP/TARGET | OPEN prima di HIGH/LOW; slippage sfavorevole una sola volta |
| Barra d'ingresso | Attivazione dopo l'ingresso; stop/target intraday non classificati come gap |
| Uguaglianza OPEN/livello | Uscita all'apertura, distanza zero |
| STOP_FIRST | Soltanto senza uscita all'apertura; ambiguità intraday conservata |
| Timeout | Close della tredicesima seduta, contando l'ingresso; controllato LONG e SHORT |
| Gap estremi | Perdita/profitto oltre 5R e insolvenza SHORT senza tagliare il risultato |
| Split sintetici 2:1 / 4:1 | Scala coerente, nessun falso gap e commissione aggiustata una sola volta |
| Vecchie run senza audit | Celle nuove vuote, conteggi non disponibili; nessuna ricostruzione inventata |
| Broker | Nessun invio PAPER/LIVE, nessun avvio IBKR, nessun riavvio operativo |

## Risultati economici: identici prima e dopo questo audit

Capitale iniziale: 10.000 USD. Dataset congelato dal 2010 al 2026-09-08.

| Metrica | V1 Stable 2.2 | V2 Stable 1.1 |
|---|---:|---:|
| Equity finale USD | 57.076,4777241303 | 63.596,2058626152 |
| CAGR % | 11,009807463826649 | 11,732154290994657 |
| Drawdown massimo % | 17,142735509237024 | 16,663199844093274 |
| PF Net | 1,4309117131024092 | 1,5122422491276184 |
| Trade chiusi | 1.393 | 1.108 |
| GAP_STOP | 103 | 77 |
| GAP_TARGET | 64 | 54 |
| Tasso uscite gap % | 11,988513998564 | 11,823104693141 |

V2 mantiene esattamente il portafoglio, le esecuzioni, i costi, l'analisi e la
curva equity della run `5b3f1c64-d042-415b-b291-682389a0f2e2`, normalizzato l'UUID.
V1 mantiene tutte le righe del riepilogo e tutte le colonne economiche
preesistenti dei trade del precedente Excel 2.2. Non sono stati hardcodati i
risultati nelle formule o nelle strategie.

## Excel verificati

- [V1 Stable 2.2 con audit](PATTERN_V1_STABLE_2_2_AUDIT.xlsx)
- [V2 Stable 1.1 con audit](PATTERN_V2_STABLE_1_1_AUDIT.xlsx)

Entrambi contengono `ExitBarOpen`, `GapExitType`, `GapSizePercent`, `GapSizeR`,
le colonne aggiuntive di prezzi/costi/rischio e il foglio `GAP_EXIT_ANALYSIS`.
L'analisi include riepilogo generale, simboli, periodi e anni, PnL, commissioni,
slippage, distribuzioni e copertura dei dati. Tutte le celle dei due workbook
sono state controllate per errori, NaN e Infinity.

`GapSizeR` usa l'ingresso dopo slippage come richiesto; `GapSizeRPriceBasis`
conserva il confronto con gli R esistenti, che usano l'ingresso prima dello
slippage. Le distanze misurano l'eccesso oltre il livello, non l'intero R del trade
né necessariamente il gap rispetto al Close precedente.

## Limitazioni del modello da risolvere separatamente

1. Il backtest applica slippage anche ai target, mentre il PAPER usa ordini LMT.
   Il prezzo risultante può oltrepassare sfavorevolmente il limite. È una
   divergenza documentata; nessun ordine IBKR è stato modificato.
2. Il catalogo congelato dei costi split copre NVDA, non AAPL/TSLA. Il tagging
   STOOQ/RAW non certifica prezzi raw negoziabili; i campioni sulle date di split
   risultano già normalizzati. La copertura storica delle quantità equivalenti
   e dei costi per gli altri simboli richiede un intervento separato.
3. Mancano fattori dei dividendi e prezzi originali di esecuzione. Non è stato
   ricostruito uno storico raw da dati adjusted.
4. Il cap commissionale usa il prezzo base, non quello dopo slippage. La
   convenzione congelata è esposta, non modificata silenziosamente.

## Evidenze

Verifica finale: `mvn verify` superato, 589 test complessivi (576 eseguiti,
13 opzionali esclusi), zero errori/fallimenti. Superati separatamente il test
di regressione offline e tutti i 109 test frontend. I log si trovano in
`target/gap-audit-verify.log`, `target/gap-audit-regression.log` e
`target/gap-audit-frontend.log`.

- [Rapporto tecnico completo e matrice dei 48 scenari](../../docs/GAP_EXECUTION_AUDIT.md)
- [Metriche e SHA-256 degli input protetti](audit-summary.json)
- [Dati di audit persistiti per trade](persisted-audits.json)
- [Campioni originali attorno agli split](split-price-samples.json)

Le nuove run vengono salvate con audit dagli stessi endpoint Stable. Quelle
prodotte per questa verifica usano store temporanei, senza riscrivere Run History
o archivi reali. Per usare la nuova diagnostica nell'app serve il backend
aggiornato e una nuova run; gli export storici privi di audit restano N/D.
