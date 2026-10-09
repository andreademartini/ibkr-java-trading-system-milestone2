(function () {
    "use strict";
    const $ = id => document.getElementById(id);
    const escape = value => String(value ?? "—").replace(/[&<>"']/g, ch => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]));
    const num = value => value == null ? "—" : Number(value).toLocaleString("it-IT", {maximumFractionDigits: 3});
    const pct = value => value == null ? "—" : num(Number(value) * 100) + "%";
    const table = (headers, rows, click = false) => `<table><thead><tr>${headers.map(v => `<th>${escape(v)}</th>`).join("")}</tr></thead><tbody>${rows.map((row, i) => `<tr ${click ? `data-row="${i}" tabindex="0"` : ""}>${row.map(v => `<td>${escape(v)}</td>`).join("")}</tr>`).join("")}</tbody></table>`;
    let run, chart, equityChart, shownTrades = [], selectedNumber;
    async function api(url, options) {
        const response = await fetch(url, options);
        if (!response.ok) throw new Error(await response.text());
        return response.json();
    }
    function status(message) { $("v1-status").textContent = message; }
    function updateCommissionFields() {
        const fixed = $("v1-commission-model").value === "FIXED_PER_ORDER";
        $("v1-commission").disabled = !fixed;
        for (const id of ["v1-commission-per-share", "v1-commission-minimum", "v1-commission-maximum"]) $(id).disabled = fixed;
    }
    $("v1-commission-model").addEventListener("change", updateCommissionFields);
    updateCommissionFields();
    $("v1-capital").addEventListener("input", () => {
        $("v1-max-order-value").placeholder = String(Number($("v1-capital").value) * 0.60);
    });
    async function initialize() {
        try {
            const config = await api("/api/backtest/pattern-v1/legacy/configuration");
            $("v1-mapping").querySelector("tbody").innerHTML = Object.entries(config.symbols).sort().map(([symbol, c]) => {
                const l = c.longConfig, s = c.shortConfig;
                return `<tr>${[symbol, l?.enabled ? l.pattern : "DISABLED", l?.enabled ? num(l.targetR) + "R" : "—", s?.enabled ? s.pattern : "DISABLED", s?.enabled ? num(s.targetR) + "R" : "—", l?.enabled || s?.enabled ? "Sì" : "No"].map(v => `<td>${escape(v)}</td>`).join("")}</tr>`;
            }).join("");
            $("v1-rules").textContent = `DAILY · NEXT_BAR_OPEN · PATTERN_EXTREME · buffer 0 · STOP_FIRST · massimo 20 barre · ${"TIMEOUT chiuso al Close della holding bar 20 (entry = 1)"} · tutti gli altri simboli disabilitati.`;
            $("v1-run").disabled = !config.enabled; status(config.enabled ? "Pronto" : "Strategia disabilitata");
            const id = new URLSearchParams(location.search).get("run");
            if (id) show(await api(`/api/backtest/pattern-v1/legacy/${encodeURIComponent(id)}`));
        } catch (error) { status(error.message); $("v1-run").disabled = true; }
    }
    $("v1-form").addEventListener("submit", async event => {
        event.preventDefault(); $("v1-run").disabled = true; status("Backtest in esecuzione…");
        try {
            show(await api("/api/backtest/pattern-v1/legacy/run", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({
                startDate:$("v1-start").value, endDate:$("v1-end").value || null, timeframe:"DAILY",
                initialCapital:Number($("v1-capital").value), slippagePercent:Number($("v1-slippage").value),
                commission:{model:$("v1-commission-model").value, fixedPerOrder:Number($("v1-commission").value),
                    perShare:Number($("v1-commission-per-share").value), minimumPerOrder:Number($("v1-commission-minimum").value),
                    maximumPercentOrderValue:Number($("v1-commission-maximum").value)},
                maxOrderValue:$("v1-max-order-value").value === "" ? null : Number($("v1-max-order-value").value),
                maxQuantity:$("v1-max-quantity").value === "" ? null : Number($("v1-max-quantity").value)
            })}));
        } catch (error) { status(error.message); } finally { $("v1-run").disabled = false; }
    });
    function show(result) {
        run = result; selectedNumber = null;
        const commission = run.settings?.commission;
        const feeDescription = commission?.model === "IBKR_US_FIXED"
            ? `IBKR_US_FIXED: ${num(commission.perShare)} USD/azione, minimo ${num(commission.minimumPerOrder)} USD, massimo ${num(commission.maximumPercentOrderValue)}% del valore ordine`
            : `Importo fisso: ${num(commission?.fixedPerOrder ?? run.settings?.commissionPerOrder)} USD/ordine`;
        $("v1-applied-limits").textContent = `Parametri applicati a questo risultato: maxOrderValue = ${num(run.settings?.maxOrderValue)}; maxQuantity = ${num(run.settings?.maxQuantity)}; commissioni ${feeDescription}; slippage ${num(run.settings?.slippagePercent)}% per esecuzione. Per applicare modifiche ai campi, esegui un nuovo backtest.`;
        if (run.schemaVersion < 2 || !run.schemaVersion) {
            $("v1-results").hidden = true; $("v1-chart-section").hidden = true; $("v1-excel").hidden = true;
            status("Run storica con motore non corretto: riesegui PATTERN V1. I risultati originali restano salvati per il confronto.");
            return;
        }
        $("v1-results").hidden = false; $("v1-chart-section").hidden = true;
        $("v1-excel").hidden = false; $("v1-excel").href = `/api/backtest/pattern-v1/legacy/${run.backtestId}/export/excel`;
        history.replaceState(null, "", `/pattern-v1-legacy.html?run=${run.backtestId}`);
        status(`${run.summary.trades} trade · ${run.backtestId}`);
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
        $("v1-comparison").innerHTML = table(["Symbol","Direction","Pattern","R","Research occurrences","Research PF R","V1 resolved PF R","Research Exp. R","V1 resolved Exp. R","Skipped overlap","Skipped capital","Warnings"],run.symbolResults.map(s => [s.symbol,s.direction,s.pattern,num(s.targetR),s.researchBaseline.occurrences,num(s.researchBaseline.profitFactor),num(s.resolvedProfitFactorR),num(s.researchBaseline.expectancyR),num(s.resolvedExpectancyR),s.metrics.skippedPositionOpen,s.metrics.skippedCapital,s.warnings.join(", ")]));
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
