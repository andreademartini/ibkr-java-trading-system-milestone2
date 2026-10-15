(function () {
    "use strict";
    const $ = id => document.getElementById(id);
    const escape = value => String(value ?? "—").replace(/[&<>"']/g, ch => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]));
    const num = value => value == null ? "—" : Number(value).toLocaleString("it-IT", {maximumFractionDigits: 3});
    const pct = value => value == null ? "—" : num(Number(value) * 100) + "%";
    const table = (headers, rows, click = false) => `<table><thead><tr>${headers.map(v => `<th>${escape(v)}</th>`).join("")}</tr></thead><tbody>${rows.map((row, i) => `<tr ${click ? `data-row="${i}" tabindex="0"` : ""}>${row.map(v => `<td>${escape(v)}</td>`).join("")}</tr>`).join("")}</tbody></table>`;
    let activeVersion, run, chart, equityChart, shownTrades = [], selectedNumber, versions = new Map();
    async function api(url, options) {
        const isRead = !options?.method || options.method === 'GET';
        const signal = isRead && typeof AbortSignal !== 'undefined' ? AbortSignal.timeout(15000) : undefined;
        let response;
        try { response = await fetch(url, signal ? {...options, signal} : options); }
        catch (error) { throw new Error(signal?.aborted ? 'Timeout nel caricamento: '+url+'. Verifica il backend e ricarica la pagina.' : 'Backend non raggiungibile: '+url+'. '+error.message); }
        if (!response.ok) throw new Error(await response.text());
        return response.json();
    }
    function status(message) { $("v1-status").textContent = message; }
    async function loadHistory() {
        const entries=await api('/api/backtest/pattern-v1/runs');
        versions=new Map(entries.map(r=>[r.runId,r.strategyVersion]));
        $('v1-history').innerHTML='<option value="">Seleziona una run</option>'+entries.map(r=>`<option value="${escape(r.runId)}">${escape(r.strategyVersion)} - ${escape(r.runId)}</option>`).join('');
    }
    async function initialize() {
        try {
            const version=await api('/api/backtest/pattern-v1/configuration');
            activeVersion=version.strategyVersion;
            const config=version.configuration,g=config.global;
            $('v1-frozen-config').textContent=JSON.stringify(version,null,2);
            $('v1-mapping').querySelector('tbody').innerHTML=config.symbols.map(c=>`<tr>${[c.symbol,c.longConfig.enabled?c.longConfig.pattern:'DISABLED',c.longConfig.targetR??'-',c.shortConfig.enabled?c.shortConfig.pattern:'DISABLED',c.shortConfig.targetR??'-',c.enabled?'Si':'No'].map(v=>`<td>${escape(v)}</td>`).join('')}</tr>`).join('');
            const fields={'v1-start':g.startDate,'v1-end':g.endDate,'v1-capital':g.initialCapital,'v1-max-order-value':'','v1-max-quantity':g.maxQuantity,'v1-commission-model':g.commission.model,'v1-commission-per-share':g.commission.perShare,'v1-commission-minimum':g.commission.minimumPerOrder,'v1-commission-maximum':g.commission.maximumPercentOrderValue,'v1-commission':g.commission.fixedPerOrder,'v1-slippage':g.slippagePercentPerSide};
            for(const [id,value] of Object.entries(fields)){$(id).value=value;$(id).disabled=true;}
            $('v1-strategy').disabled=true;
            $('v1-rules').textContent='DAILY - NEXT_BAR_OPEN - PATTERN_EXTREME - buffer '+g.stopBufferTicks+' - '+g.sameBarPolicy+' - '+g.maxHoldingBars+' barre. Rischio 2%; ordine 30% equity; posizione/LONG/SHORT 100% equity per trade; nessuna leva; filtri OFF. Costi e split della run originale.';
            $('v1-run').disabled=false;status('Versione '+activeVersion+' congelata - ordini disabilitati');
            loadHistory().catch(error=>status('Configurazione pronta. Run History non disponibile: '+error.message));
            const id=new URLSearchParams(location.search).get('run');if(id)show(await api('/api/backtest/pattern-v1/'+encodeURIComponent(id)));
        }catch(error){status(error.message);$('v1-run').disabled=true;}
    }
    $('v1-history').addEventListener('change',async()=>{try{if($('v1-history').value)show(await api('/api/backtest/pattern-v1/'+encodeURIComponent($('v1-history').value)));}catch(e){status(e.message);}});
    $('v1-form').addEventListener('submit',async event=>{
        event.preventDefault();$('v1-run').disabled=true;status('Backtest stabile su snapshot storico...');
        try{show(await api('/api/backtest/pattern-v1/run',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'}));await loadHistory();}
        catch(error){status(error.message);}finally{$('v1-run').disabled=false;}
    });
    function show(result) {
        run = result; selectedNumber = null;
        const commission = run.settings?.commission;
        const feeDescription = commission?.model === "IBKR_US_FIXED"
            ? `IBKR_US_FIXED: ${num(commission.perShare)} USD/azione, minimo ${num(commission.minimumPerOrder)} USD, massimo ${num(commission.maximumPercentOrderValue)}% del valore ordine`
            : `Importo fisso: ${num(commission?.fixedPerOrder ?? run.settings?.commissionPerOrder)} USD/ordine`;
        $("v1-applied-limits").textContent = `Parametri applicati a questo risultato: maxOrderValue = ${versions.get(run.backtestId)==="1.0-legacy" ? num(run.settings?.maxOrderValue) : "30% equity (limite dinamico)"}; maxQuantity = ${num(run.settings?.maxQuantity)}; commissioni ${feeDescription}; slippage ${num(run.settings?.slippagePercent)}% per esecuzione. Configurazione congelata: nuova versione richiesta per modifiche.`;
        if (run.schemaVersion < 2 || !run.schemaVersion) {
            $("v1-results").hidden = true; $("v1-chart-section").hidden = true; $("v1-excel").hidden = true;
            status("Run storica con motore non corretto: riesegui PATTERN V1. I risultati originali restano salvati per il confronto.");
            return;
        }
        $("v1-results").hidden = false; $("v1-chart-section").hidden = true;
        $("v1-excel").hidden = false; $("v1-excel").href = `/api/backtest/pattern-v1/${run.backtestId}/export/excel`;
        history.replaceState(null, "", `?run=${run.backtestId}`);
        status(`${versions.get(run.backtestId)??activeVersion} - ${run.summary.trades} trade · ${run.backtestId}`);
        $("v1-coverage").textContent = `Richiesto ${run.startDate} → ${run.endDate}. Copertura effettiva ${run.actualStartDate ?? "—"} → ${run.actualEndDate ?? "—"}. CAGR sulla copertura effettiva.`;
        
        const metrics = PatternV1Metrics.summary(run);
        $("v1-summary").innerHTML = metrics.map(({label,key,value,tooltip}) => {
            const formatted = key === "winRate" ? pct(value) : num(value) + (label.endsWith("%") && value != null ? "%" : "");
            return `<div class="v1-metric" title="${escape(tooltip)}"><span>${escape(label)}</span><strong>${escape(formatted)}</strong></div>`;
        }).join("");
        $("v1-directions").innerHTML = table(["Direction","Signals","Trades","Closed","Open","Wins","Losses","Breakevens","Win Rate","PF (PnL)","PF (R)","Expectancy R","Realized Net PnL","Unrealized Net PnL","Max DD"], Object.entries(run.directionResults).map(([d,m]) =>
            [d,m.signalsDetected,m.trades,m.closedTrades,m.openTrades,m.wins,m.losses,m.breakevens,pct(m.winRate),num(m.profitFactor),num(m.profitFactorR),num(m.expectancyR),num(m.realizedNetPnl),num(m.netUnrealizedPnl),num(m.maxDrawdown)]));
        $("v1-symbols").innerHTML = table(["Symbol","Direction","Pattern","Target R","Signals","Executed","Skipped Open","Skipped Capital","Closed","Open","Wins","Losses","Breakevens","Timeouts","Win Rate","PF (PnL)","PF (R)","Expectancy R","Average R","Avg Winner R","Avg Loser R","Realized Gross PnL","Realized Net PnL","Unrealized PnL","Net Unrealized PnL","Max DD","Actual Risk Avg %","Average Order Value","Executed MFE R","Executed MAE R"], run.symbolResults.map(s => {
            const m=s.metrics; return [s.symbol,s.direction,s.pattern,num(s.targetR),m.signalsDetected,m.trades,m.skippedPositionOpen,m.skippedCapital,m.closedTrades,m.openTrades,m.wins,m.losses,m.breakevens,m.timeouts,pct(m.winRate),num(m.profitFactor),num(m.profitFactorR),num(m.expectancyR),num(m.averageR),num(m.averageWinnerR),num(m.averageLoserR),num(m.realizedGrossPnl),num(m.realizedNetPnl),num(m.unrealizedPnl),num(m.netUnrealizedPnl),num(s.maxDrawdown),num(m.averageActualRiskPercent),num(m.averageOrderValue),num(m.mfeR),num(m.maeR)];
        }));
        $("v1-comparison").innerHTML = table(["Symbol","Direction","Pattern","R","Research occurrences","Research PF R","V1 resolved PF R","Research Exp. R","V1 resolved Exp. R","Skipped overlap","Skipped capital","Warnings"],run.symbolResults.filter(s=>s.researchBaseline).map(s => [s.symbol,s.direction,s.pattern,num(s.targetR),s.researchBaseline.occurrences,num(s.researchBaseline.profitFactor),num(s.resolvedProfitFactorR),num(s.researchBaseline.expectancyR),num(s.resolvedExpectancyR),s.metrics.skippedPositionOpen,s.metrics.skippedCapital,s.warnings.join(", ")]));
        $("v1-trade-symbol").innerHTML = '<option value="">Tutti</option>' + Object.keys(run.priceSeries).map(s=>`<option>${escape(s)}</option>`).join("");
        $("v1-event-filter").innerHTML = '<option value="">Tutti</option>' + [...new Set(run.events.map(e=>e.eventType))].sort().map(s=>`<option>${escape(s)}</option>`).join("");
        $("v1-warnings").innerHTML = run.warnings.map(w=>`<li>${escape(w)}</li>`).join("");
        renderTrades(); renderEvents(); renderEquity();
    }
    function renderTrades() {
        if (!run) return;
        shownTrades = run.trades.filter(t=>!$("v1-trade-symbol").value || t.symbol===$("v1-trade-symbol").value);
        $("v1-trades").innerHTML = table(["ID","Symbol","Pattern","Direction","Target R","Signal","Entry Date","Entry","Stop","Target","Desired Qty","Final Qty","Limiting Reasons","SIZE_CONSTRAINED","Equity At Entry","Cash Before","Cash After","Order Value","Target Risk %","Actual Risk Amount","Actual Risk %","Exit Date","Exit","Reason","Closed","Remaining Qty","Realized Net PnL","Unrealized Net PnL","Mark Date","Mark Price","Result R","Executed MFE R","Executed MAE R","Raw Bar MFE R","Raw Bar MAE R","Holding Bars","Same Bar Ambiguous"], shownTrades.map(t=>[
            t.number,t.symbol,t.pattern,t.direction,num(t.targetR),t.signalDate,t.entryDate,num(t.entryPrice),num(t.stopPrice),num(t.takeProfitPrice),
            t.desiredQuantity,t.finalQuantity,t.quantityLimitingReasons?.join(", ") || "NONE",t.sizeConstrained,num(t.equityAtEntry),
            num(t.availableCashBeforeEntry),num(t.availableCashAfterEntry),num(t.orderValue),num(t.desiredRiskPercent),num(t.actualRiskAmount),num(t.actualRiskPercent),
            t.exitDate,num(t.exitPrice),t.exitReason,t.closed ? "Sì" : "OPEN",t.remainingQuantity,num(t.realizedPnl),num(t.netUnrealizedPnl),
            t.markDate,num(t.markPrice),num(t.resultR),num(t.executedMfeR),num(t.executedMaeR),num(t.rawBarMfeR),num(t.rawBarMaeR),t.holdingBars,t.sameBarAmbiguous]),true);
        $("v1-trades").querySelectorAll("[data-row]").forEach(row=>{
            const select=()=>selectTrade(shownTrades[Number(row.dataset.row)]);
            row.addEventListener("click",select); row.addEventListener("keydown",event=>{if(event.key==="Enter")select();});
        });
    }
    function renderEvents() {
        if (!run) return;
        const filtered = run.events.filter(e=>!$("v1-event-filter").value || e.eventType===$("v1-event-filter").value);
        $("v1-events").innerHTML = table(["Date","Symbol","Direction","Pattern","Event","Trade","Details"],filtered.slice(0,1000).map(e=>[e.date,e.symbol,e.direction,e.pattern,e.eventType,e.tradeId,JSON.stringify(e.details)]));
        if(filtered.length>1000)$("v1-events").insertAdjacentHTML("beforeend",`<p>Prime 1000 di ${filtered.length} righe; tutti gli eventi sono salvati nel risultato JSON.</p>`);
    }
    function selectTrade(t) {
        selectedNumber=t.number; $("v1-chart-section").hidden=false;
        $("v1-chart-title").textContent=`${t.symbol} · ${t.pattern} · ${t.direction} · ${t.targetR}R · T${t.number}`;
        const events=run.events.filter(e=>e.tradeId===t.tradeId || (!e.tradeId && e.symbol===t.symbol && e.pattern===t.pattern && e.direction===t.direction && e.index===t.signalIndex && ["PATTERN_DETECTED","SIGNAL"].includes(e.eventType))).map(e=>({
            ...e,occurredAt:e.date+"T00:00:00Z",price:e.details.entryPrice??e.details.stopPrice??e.details.takeProfitPrice??e.details.exitPrice??t.patternClose,
            quantity:t.quantity,remainingQuantity:e.eventType==="TRADE_CLOSED"?0:t.quantity,currentStopPrice:t.stopPrice,details:JSON.stringify(e.details)
        }));
        const adapted={...t,entryAt:t.entryDate+"T00:00:00Z",exitAt:t.closed?t.exitDate+"T00:00:00Z":null,exitIndex:t.closed?t.exitIndex:t.markIndex,initialStopPrice:t.stopPrice,finalStopPrice:t.stopPrice,
            effectiveRisk:Number(t.riskPerShare)*t.quantity,returnPercent:Number(t.closed?t.netPnl:t.netUnrealizedPnl)/Number(t.orderValue)*100,
            takeProfits:[{targetNumber:1,price:t.takeProfitPrice,quantity:t.quantity}],events};
        chart ||= TradingChart.create({host:$("v1-chart"),tooltip:$("v1-tooltip"),infoPanel:$("v1-info"),timeline:$("v1-timeline"),eventDetail:$("v1-event-detail")});
        chart.update({simulation:{strategy:"PATTERN_V1",symbol:t.symbol,priceSeries:run.priceSeries[t.symbol],trades:[adapted]},
            selectedTradeId:TradingChart.formatTradeId(adapted),filters:{indicators:false},timeframe:"DAILY",autoFit:true});
        $("v1-pattern-info").textContent=`Pattern completato ${t.signalDate}; candele del pattern evidenziate in viola. Entry all’open ${t.entryDate}. Stop ${t.stopPrice}; rischio ${t.riskPerShare}; target ${t.takeProfitPrice}. ${t.closed ? "" : `Posizione OPEN: mark ${t.markDate} a ${t.markPrice}; nessun fill di uscita.`}`;
        const i=shownTrades.findIndex(value=>value.number===t.number); $("v1-previous").disabled=i<=0; $("v1-next").disabled=i<0||i>=shownTrades.length-1;
        $("v1-chart-section").scrollIntoView({behavior:"smooth",block:"start"});
    }
    function renderEquity() {
        equityChart?.remove();
        equityChart=LightweightCharts.createChart($("v1-equity"),{autoSize:true,height:360,layout:{attributionLogo:true},timeScale:{timeVisible:false}});
        const equity=equityChart.addSeries(LightweightCharts.LineSeries,{color:"#0f766e",title:"Equity"});
        const drawdown=equityChart.addSeries(LightweightCharts.HistogramSeries,{color:"#dc2626",priceScaleId:"left",title:"Drawdown"});
        equity.setData(run.equityCurve.map(p=>({time:p.date,value:Number(p.equity)})));
        drawdown.setData(run.equityCurve.map(p=>({time:p.date,value:-Number(p.drawdown)})));
        equityChart.timeScale().fitContent();
        const points=new Map(run.equityCurve.map(p=>[p.date,p]));
        equityChart.subscribeCrosshairMove(param=>{
            const date=typeof param.time==="object"?`${param.time.year}-${String(param.time.month).padStart(2,"0")}-${String(param.time.day).padStart(2,"0")}`:param.time;
            const p=points.get(date); if(p)$("v1-equity-point").textContent=`${date} · Equity ${num(p.equity)} · DD ${num(p.drawdown)} (${num(p.drawdownPercent)}%) · Cash ${num(p.cash)} · Long MV ${num(p.longMarketValue)} · Short liability ${num(p.shortMarketValue)} · Gross Exposure ${num(p.grossExposurePercent)}% · Open Positions ${p.openPositions}`;
        });
    }
    $("v1-trade-symbol").addEventListener("change",renderTrades); $("v1-event-filter").addEventListener("change",renderEvents);
    $("v1-fit").addEventListener("click",()=>chart?.fitTrade());
    for(const [id,delta] of [["v1-previous",-1],["v1-next",1]])$(id).addEventListener("click",()=>{const i=shownTrades.findIndex(t=>t.number===selectedNumber);if(shownTrades[i+delta])selectTrade(shownTrades[i+delta]);});
    initialize();
})();
