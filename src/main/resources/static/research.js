const state = { run: null, summary: [], trades: [] };
const $ = id => document.getElementById(id);
const number = (id, fallback = 0) => Number($(id).value || fallback);
const decimals = id => $(id).value.split(",").map(value => Number(value.trim())).filter(Number.isFinite);
const percent = value => value == null ? "-" : `${(Number(value) * 100).toFixed(2)}%`;
const fixed = value => value == null ? "-" : Number(value).toFixed(3);
const cell = value => `<td>${value ?? "-"}</td>`;

$("end-date").value = new Date().toISOString().slice(0, 10);
$("is-percent").addEventListener("input", () => {
    $("oos-percent").value = Math.max(0, 100 - number("is-percent"));
});

$("research-form").addEventListener("submit", async event => {
    event.preventDefault();
    const patterns = [...document.querySelectorAll('input[name="pattern"]:checked')]
        .map(input => input.value);
    if (!patterns.length) return setStatus("Seleziona almeno un pattern", true);
    const body = {
        startDate: $("start-date").value,
        endDate: $("end-date").value,
        symbols: $("symbols").value.split(/[\s,;]+/).filter(Boolean),
        patterns,
        direction: $("direction").value,
        targetRs: decimals("target-rs"),
        forwardBars: [1, 2, 3, 5, 10, 20],
        maxHoldingBars: number("max-holding", 20),
        sameBarResolution: $("same-bar").value,
        inSamplePercent: number("is-percent", 70),
        minimumOccurrences: number("minimum-occurrences", 100),
        entryMethod: $("entry-method").value,
        stopMethod: "PATTERN_EXTREME",
        timeframe: "DAILY",
        tickSize: 0.01,
        stopBufferTicks: 0,
        closeTimeoutAtLastClose: false
    };
    $("run-button").disabled = true;
    setStatus("Research in esecuzione…");
    try {
        const response = await fetch("/api/research/patterns/run", {
            method: "POST", headers: {"Content-Type": "application/json"},
            body: JSON.stringify(body)
        });
        if (!response.ok) throw new Error(await response.text());
        state.run = await response.json();
        const id = state.run.researchRunId;
        const [summaryResponse, tradesResponse] = await Promise.all([
            fetch(`/api/research/patterns/${id}/summary`),
            fetch(`/api/research/patterns/${id}/trades`)
        ]);
        if (!summaryResponse.ok || !tradesResponse.ok) throw new Error("Errore caricamento risultati");
        state.summary = await summaryResponse.json();
        state.trades = await tradesResponse.json();
        $("run-id").textContent = id;
        $("excel-link").href = `/api/research/patterns/${id}/export/excel`;
        $("excel-link").hidden = false;
        $("results-panel").hidden = false;
        populatePatternFilter();
        renderSummary();
        setStatus(`${state.run.status}: ${state.run.occurrenceRecords} record`);
    } catch (error) {
        setStatus(error.message, true);
    } finally {
        $("run-button").disabled = false;
    }
});

function setStatus(message, error = false) {
    $("run-status").textContent = message;
    $("run-status").className = error ? "degraded" : "";
}

function populatePatternFilter() {
    const select = $("filter-pattern");
    select.innerHTML = '<option value="">Tutti</option>';
    [...new Set(state.summary.map(row => row.pattern))].sort().forEach(pattern => {
        select.insertAdjacentHTML("beforeend", `<option>${pattern}</option>`);
    });
}

function renderSummary() {
    const win = number("filter-win") / 100;
    const pf = number("filter-pf");
    const expectancy = number("filter-expectancy", -99);
    const occurrences = number("filter-occurrences");
    const direction = $("filter-direction").value;
    const pattern = $("filter-pattern").value;
    const stability = $("filter-stability").value;
    const rows = state.summary.filter(row => row.symbol === "ALL_SYMBOLS")
        .filter(row => (row.overall.winRate ?? -1) >= win)
        .filter(row => (row.overall.profitFactor ?? -1) >= pf)
        .filter(row => (row.overall.expectancyR ?? -999) >= expectancy)
        .filter(row => row.overall.occurrences >= occurrences)
        .filter(row => !direction || row.direction === direction)
        .filter(row => !pattern || row.pattern === pattern)
        .filter(row => !stability || row.stabilityStatus === stability);
    $("summary-table").querySelector("tbody").innerHTML = rows.map((row, index) => {
        const warning = row.overall.winRate >= .60
            && ((row.overall.profitFactor ?? 0) <= 1 || (row.overall.expectancyR ?? 0) <= 0);
        return `<tr data-row="${index}" class="${warning ? "edge-warning" : ""}">
            ${cell(row.pattern)}${cell(row.direction)}${cell(fixed(row.targetR))}
            ${cell(row.overall.occurrences)}${cell(row.overall.validTrades)}
            ${cell(percent(row.overall.winRate))}${cell(fixed(row.overall.profitFactor))}
            ${cell(fixed(row.overall.expectancyR))}${cell(fixed(row.overall.averageR))}
            ${cell(fixed(row.overall.mfeAverage))}${cell(fixed(row.overall.maeAverage))}
            ${cell(percent(row.inSample.winRate))}${cell(percent(row.outOfSample.winRate))}
            ${cell(fixed(row.inSample.profitFactor))}${cell(fixed(row.outOfSample.profitFactor))}
            ${cell(fixed(row.inSample.expectancyR))}${cell(fixed(row.outOfSample.expectancyR))}
            <td class="${row.stabilityStatus.toLowerCase().replaceAll("_", "-")}">${row.stabilityStatus}</td>
            ${cell(row.warnings.join(", "))}</tr>`;
    }).join("");
    [...$("summary-table").querySelectorAll("tbody tr")].forEach((element, index) =>
        element.addEventListener("click", () => renderDetail(rows[index])));
}

function renderDetail(selected) {
    $("detail-panel").hidden = false;
    $("detail-title").textContent = `${selected.pattern} · ${selected.direction} · ${selected.targetR}R`;
    const symbols = state.summary.filter(row => row.symbol !== "ALL_SYMBOLS"
        && row.pattern === selected.pattern && row.direction === selected.direction
        && Number(row.targetR) === Number(selected.targetR));
    $("symbol-table").querySelector("tbody").innerHTML = symbols.map(row => `<tr>
        ${cell(row.symbol)}${cell(row.overall.occurrences)}${cell(percent(row.overall.winRate))}
        ${cell(fixed(row.overall.profitFactor))}${cell(fixed(row.overall.expectancyR))}
        ${cell(fixed(row.overall.mfeAverage))}${cell(fixed(row.overall.maeAverage))}
        ${cell(row.stabilityStatus)}</tr>`).join("");
    const trades = state.trades.filter(trade => trade.pattern === selected.pattern
        && trade.direction === selected.direction && Number(trade.targetR) === Number(selected.targetR));
    const distribution = trades.reduce((map, trade) => {
        const key = trade.resultR == null ? trade.exitReason : `${Number(trade.resultR).toFixed(2)}R`;
        map[key] = (map[key] || 0) + 1; return map;
    }, {});
    $("r-distribution").innerHTML = Object.entries(distribution)
        .map(([key, count]) => `<span>${key}: <strong>${count}</strong></span>`).join("");
    $("trade-table").querySelector("tbody").innerHTML = trades.map(trade => `<tr>
        ${cell(trade.symbol)}${cell(trade.signalDate)}${cell(trade.entryDate)}
        ${cell(fixed(trade.entryPrice))}${cell(fixed(trade.stopPrice))}${cell(fixed(trade.targetPrice))}
        ${cell(trade.exitDate)}${cell(fixed(trade.exitPrice))}${cell(trade.exitReason)}
        ${cell(fixed(trade.resultR))}${cell(fixed(trade.mfe))}${cell(fixed(trade.mae))}
        ${cell(trade.datasetSegment)}</tr>`).join("");
    $("detail-panel").scrollIntoView({behavior: "smooth", block: "start"});
}

["filter-win", "filter-pf", "filter-expectancy", "filter-occurrences",
 "filter-direction", "filter-pattern", "filter-stability"].forEach(id =>
    $(id).addEventListener("input", renderSummary));
