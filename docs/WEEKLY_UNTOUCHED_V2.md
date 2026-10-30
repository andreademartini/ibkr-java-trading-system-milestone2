# Weekly High TTE / Breakout: livelli storici LONG e SHORT

Regole `WEEKLY_UNTOUCHED/2`, introdotte il 28 ottobre 2026. Percorso di ricerca offline, senza invio ordini. Pattern V1, V2 e V3 restano separati. La pagina è `/weekly-high-tte.html`.

## Livelli e cronologia

Ogni settimana USA completa genera un HIGH e un LOW indipendenti. Diventano disponibili dalla prima sessione successiva. Il primo HIGH DAILY >= livello consuma un HIGH; il primo LOW DAILY <= livello consuma un LOW. Non occorre la chiusura oltre il livello. La settimana d'origine non può consumare i propri estremi.

I livelli non scadono al cambio di settimana e non hanno una durata massima. Sono ricostruiti usando tutto lo storico locale supportato, dal 2010 alla fine del backtest, anche prima dell'inizio del periodo operativo. Non si presume l'esistenza di livelli antecedenti ai dati disponibili. Settimane incomplete, comprese quelle iniziali/finali troncate, non generano livelli. Le festività sono considerate dal calendario esistente.

L'inventario `analysis.historicalLevels` mostra prezzo, tipo, settimane d'origine, data di disponibilità, estremi della settimana d'origine, stato e data del primo tocco. Le origini disponibili contemporaneamente allo stesso prezzo/tipo vengono raggruppate. Una nuova settimana può generare un nuovo livello allo stesso prezzo di un vecchio livello già consumato: conserva una propria identità temporale e non riattiva il vecchio livello. Non ci sono ordini duplicati per le origini raggruppate.

Le barre vengono elaborate cronologicamente. Aggregare in anticipo una settimana non la rende disponibile prima della chiusura. Le decisioni e il grafico DAILY mantengono le date effettive.

## Ingressi e livelli multipli

LONG e SHORT sono abilitabili separatamente, entrambi attivi nelle nuove impostazioni predefinite. Si sceglie il livello attivo più vicino nella direzione del trade al momento del setup. Il riferimento di un setup pendente resta fisso fino a ingresso, scadenza o invalidazione. Una sola posizione per simbolo; più simboli possono essere trattati rispettando capitale e limiti.

| Modalità | LONG | SHORT |
|---|---|---|
| TTE | Pullback con chiusure in discesa e massimi non crescenti; recovery rialzista; buy stop sopra il massimo della recovery. Target sul HIGH attivo. Stop sotto il minimo del pullback. | Rimbalzo con chiusure in salita e minimi non decrescenti; recovery ribassista; sell stop sotto il minimo della recovery. Target sul LOW attivo. Stop sopra il massimo del rimbalzo. |
| Breakout | Buy stop sopra il HIGH attivo + buffer. | Sell stop sotto il LOW attivo - buffer. |
| TTE partial | Prima uscita parziale al livello HIGH; residuo con stop iniziale e target R oltre il primo livello. | Prima uscita parziale al livello LOW; residuo con stop iniziale e target R sotto il primo livello. |

Nel Breakout lo stop usa **l'estremo opposto della stessa settimana che ha generato il livello scelto**, anche se tale estremo è già stato raggiunto. Non viene scelto un minimo/massimo attivo appartenente a un'altra settimana. Per origini raggruppate si usa la prima origine attiva. Il target è ingresso effettivo +/- `breakoutTargetR` volte il rischio effettivo. Prezzi, rischio e R/R non validi scartano l'operazione.

I setup TTE si attivano soltanto dalla sessione successiva alla recovery. Il Breakout può essere predisposto prima dell'apertura usando livelli già noti dalla chiusura precedente. Tutti i setup rispettano `entryValidityBars`; la loro scadenza non elimina il livello ancora intatto. Un successivo setup Breakout può essere predisposto sullo stesso livello intatto. Il momentum, se abilitato per TTE, è positivo per LONG e negativo per SHORT.

L'ordine pendente viene valutato prima di consumare i livelli toccati dalla barra, così il primo attraversamento può attivare il Breakout. Se il livello viene toccato senza raggiungere il trigger con buffer, il setup viene annullato e il livello consumato. Se all'apertura il prezzo ha già toccato il livello ma non il trigger, l'ordine è annullato prima di qualsiasi trigger intraday. Per TTE, un'apertura già al target o oltre annulla l'ingresso. Un trade aperto e il suo residuo continuano a essere gestiti dopo il consumo del livello di origine.

## Ambiguità, gap e dati mancanti

OPEN_FIRST e STOP_FIRST restano le regole di gestione. Slippage e gap sono speculari: lo SHORT vende con slippage negativo e ricompra con slippage positivo. Il timeout conta sessioni DAILY, con barra d'ingresso = 1.

Per un ingresso intraday, gli estremi della barra non rivelano la sequenza: lo scenario CONSERVATIVE applica l'eventuale stop avverso, mentre SENSITIVITY_FAVORABLE può applicare il target. Se entrambe le uscite sono raggiunte su una barra di una posizione già attiva, viene applicato STOP_FIRST e segnalata l'ambiguità. Un tocco di più livelli nella barra d'ingresso viene registrato in `touchedLevelIds` e `multipleLevelsTouched`.

Gli ingressi già attivati all'OPEN precedono quelli intraday. Se due trigger opposti sullo stesso simbolo sono entrambi intraday e l'ordine temporale è ignoto, CONSERVATIVE scarta il conflitto con `AMBIGUOUS_OPPOSING_INTRABAR_TRIGGERS`; la sensibilità usa lo scenario deterministico LONG-first, esplicitamente ambiguo. Non è una ricostruzione intraday né un limite matematico garantito dei risultati.

Una sessione DAILY mancante rende incerto lo stato dei livelli preesistenti: vengono esclusi dai nuovi segnali e classificati `UNKNOWN_DATA_GAP`, senza inventare una data di primo tocco. `levelDataGaps` riporta il motivo. Anche il percorso di una posizione aperta durante la lacuna è segnalato ambiguo. Sono necessari dati completi per valutazioni attendibili.

## Capitale e SHORT

Senza leva il motore limita esposizione lorda, esposizioni LONG/SHORT, valore per ordine, posizione e rischio con commissioni. Per ogni SHORT riserva l'intero nozionale iniziale più la commissione. I proventi della vendita non vengono aggiunti alla liquidità spendibile.

La copertura rilascia la garanzia proporzionale e il P&L realizzato, al netto delle commissioni. Il mark-to-market include il P&L non realizzato SHORT. Gli incassi intraday, comprese le coperture, diventano disponibili dopo la valutazione degli ingressi della giornata. Gap avversi possono produrre perdite superiori al rischio previsto e alla garanzia.

Non vengono simulati disponibilità del prestito titoli, costi borrow, interessi, dividendi dovuti dallo SHORT o requisiti di margine specifici del broker. Gli SHORT sono scenari di ricerca, non una certificazione di eseguibilità reale.

## Interfaccia, export e riproducibilità

La pagina mostra l'intero inventario dei livelli, audit dei setup, trade con direzione/livello/origini e risultati LONG/SHORT separati dal totale. Il grafico mostra le ultime 250 barre del periodo operativo e i livelli compresi nella scala prezzi: HIGH verde, LOW rosso, tratteggio per livelli non più attivi. La tabella e l'Excel contengono anche i livelli fuori scala e fuori dalla finestra del grafico.

L'Excel aggiunge `HISTORICAL_LEVELS` e `DIRECTION_PERFORMANCE`; trade, setup, equity e analisi includono i nuovi campi. `weeklyTouchOverall` e `WEEKLY_LEVEL_STATISTICS` conservano il significato descrittivo precedente (HIGH della settimana immediatamente precedente); non sono statistiche cumulative dei nuovi livelli. Per il nuovo modello usare `historicalLevelCounts` e `historicalLevelOutcomes`, con separazione di raggiunti, ancora attivi/censurati e incerti.

Le nuove configurazioni salvano versione delle regole e direzioni nel checksum. Le vecchie configurazioni senza questi campi usano il motore v1 LONG sulla settimana precedente. La loro serializzazione canonica omette i nuovi campi null, conservando gli hash precedenti. Replay e confronto operano sullo snapshot salvato; versioni diverse non possono essere confrontate come se avessero le stesse regole. UUID e timestamp di una nuova esecuzione cambiano, i risultati economici restano riproducibili.

Aprendo una vecchia configurazione nel modulo di modifica, l'interfaccia propone esplicitamente le nuove regole con LONG/SHORT attivi; il salvataggio o la nuova esecuzione crea una configurazione v2. La visualizzazione e il replay di una vecchia run non ne modificano i dati.

## Verifica

La suite sintetica copre persistenza dei livelli per mesi, tocco per uguaglianza, indipendenza HIGH/LOW, calendario, duplicati, priorità del livello, LONG/SHORT, primo attraversamento, scadenze, gap, ambiguità, capitale SHORT, prefissi senza look-ahead, replay ed Excel. I test HTTP verificano disponibilità dei nuovi campi e assenza di scritture broker. I test frontend verificano controlli direzionali, tabella e grafico.

Il test offline opzionale `HistoricalWeeklyTteLocalTest`, attivato con `-Dweeklytte.historical.local=true`, usa AAPL/MSFT/NVDA e produce le tre modalità in `outputs/weekly-untouched-v2-validation/`, senza sovrascrivere gli artefatti v1.
