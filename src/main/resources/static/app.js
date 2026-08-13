const state = {
    config: null,
    profiles: [],
    providerOptions: [],
    strategyOptions: [],
    selectedProfileId: null,
    status: null,
    signals: [],
    orders: [],
    positions: [],
    history: null,
    simulation: null
};

const $ = (id) => document.getElementById(id);

async function api(path, options = {}) {
    const response = await fetch(path, {
        headers: {
            "Accept": "application/json",
            "Content-Type": "application/json",
            ...(options.headers || {})
        },
        ...options
    });
    if (!response.ok) {
        let detail = `${response.status} ${response.statusText}`;
        try {
            const body = await response.json();
            detail = body.detail || body.title || detail;
        } catch (_ignored) {
            detail = await response.text();
        }
        throw new Error(detail);
    }
    return response.status === 204 ? null : response.json();
}

function fillSelect(select, values) {
    select.innerHTML = "";
    values.forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        select.appendChild(option);
    });
}

function setValue(id, value) {
    $(id).value = value ?? "";
}

function num(id) {
    const value = $(id).value;
    return value === "" ? null : Number(value);
}

function boolValue(id) {
    return $(id).value === "true";
}

function alphaVantageConfigured() {
    return state.config?.marketData?.provider === "ALPHA_VANTAGE"
        && state.config?.marketData?.alphaVantage?.apiKeyConfigured;
}

function selectedProfile() {
    return state.profiles.find((profile) => profile.id === state.selectedProfileId)
        || state.profiles[0];
}

function normalizeListResponse(value) {
    if (Array.isArray(value)) {
        return value;
    }
    return value && Array.isArray(value.value) ? value.value : [];
}

function formatNumber(value) {
    if (value === null || value === undefined || value === "") {
        return "-";
    }
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return String(value);
    }
    return number.toLocaleString("it-IT", { maximumFractionDigits: 4 });
}

function formatDate(value) {
    if (!value) {
        return "-";
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString("it-IT");
}

function emptyRow(columnCount, text) {
    return `<tr><td class="empty-row" colspan="${columnCount}">${text}</td></tr>`;
}

function toast(message, isError = false) {
    const element = $("toast");
    element.textContent = message;
    element.classList.toggle("error", isError);
    element.classList.add("visible");
    window.clearTimeout(toast.timer);
    toast.timer = window.setTimeout(() => element.classList.remove("visible"), 3200);
}

function renderStatus() {
    const status = state.status || {};
    $("status-mode").textContent = status.mode || "-";
    $("status-connection").textContent = status.connectionState || "-";
    $("status-provider").textContent = status.marketDataProvider || "-";
    $("status-orders-enabled").textContent = status.orderSubmissionEnabled ? "Abilitati" : "Bloccati";
    $("status-kill").textContent = status.killSwitchActive ? "Attivo" : "Disattivo";
    $("last-refresh").textContent = `Ultimo refresh ${new Date().toLocaleTimeString("it-IT")}`;
}

function renderProfiles() {
    $("profile-list").innerHTML = state.profiles.map((profile) => `
        <button class="profile-card ${profile.id === state.selectedProfileId ? "selected" : ""}"
                type="button"
                data-profile-id="${profile.id}">
            <strong>${profile.name}</strong>
            <span>${profile.symbol} · ${profile.provider} · ${profile.strategy}</span>
            <small>F${profile.fastPeriod} / S${profile.slowPeriod} · ${profile.timeframe}</small>
        </button>
    `).join("");

    document.querySelectorAll("[data-profile-id]").forEach((button) => {
        button.addEventListener("click", () => {
            saveSelectedFormInMemory();
            state.selectedProfileId = button.dataset.profileId;
            applyProfileToForm(selectedProfile());
            renderProfiles();
        });
    });
}

function applyProfileToForm(profile) {
    if (!profile) {
        return;
    }
    setValue("profile-name", profile.name);
    setValue("profile-symbol", profile.symbol);
    setValue("profile-provider", profile.provider);
    setValue("profile-strategy", profile.strategy);
    setValue("profile-fast", profile.fastPeriod);
    setValue("profile-slow", profile.slowPeriod);
    setValue("profile-quantity", profile.quantity);
    setValue("profile-capital", profile.initialCapital);
    setValue("profile-commission", profile.commissionPerOrder);
    setValue("profile-slippage", profile.slippagePercent);
    setValue("profile-timeframe", profile.timeframe);
    setValue("profile-output-size", profile.outputSize);
    setValue("profile-max-points", profile.maxPoints);
    setValue("manual-price-value", "");
    setValue("signal-price", "");
    setValue("signal-action", "BUY");
    setValue("signal-execute", "false");
    setValue("signal-reason", profile.name);
}

function collectProfileFromForm() {
    const current = selectedProfile();
    return {
        id: current?.id || crypto.randomUUID(),
        name: $("profile-name").value.trim() || "Nuovo profilo",
        symbol: $("profile-symbol").value.trim().toUpperCase() || "SPY",
        provider: $("profile-provider").value,
        strategy: $("profile-strategy").value,
        fastPeriod: num("profile-fast"),
        slowPeriod: num("profile-slow"),
        quantity: num("profile-quantity"),
        initialCapital: num("profile-capital"),
        commissionPerOrder: num("profile-commission"),
        slippagePercent: num("profile-slippage"),
        timeframe: $("profile-timeframe").value,
        outputSize: $("profile-output-size").value,
        maxPoints: num("profile-max-points")
    };
}

function saveSelectedFormInMemory() {
    const profile = selectedProfile();
    if (!profile) {
        return;
    }
    const updated = collectProfileFromForm();
    state.profiles = state.profiles.map((item) => item.id === updated.id ? updated : item);
}

async function saveProfiles() {
    saveSelectedFormInMemory();
    await persistProfiles();
    toast("Profili salvati");
}

async function persistProfiles() {
    const response = await api("/api/workbench/action-profiles", {
        method: "PUT",
        body: JSON.stringify({ profiles: state.profiles })
    });
    state.profiles = response.profiles;
    state.selectedProfileId = selectedProfile()?.id || state.profiles[0]?.id;
    renderProfiles();
    applyProfileToForm(selectedProfile());
}

function addProfile() {
    saveSelectedFormInMemory();
    const base = selectedProfile() || {
        symbol: "SPY",
        provider: state.config?.marketData?.provider || "MANUAL",
        strategy: "MOVING_AVERAGE_CROSSOVER",
        fastPeriod: 5,
        slowPeriod: 20,
        quantity: 1,
        initialCapital: 10000,
        commissionPerOrder: 0,
        slippagePercent: 0,
        timeframe: "DAILY",
        outputSize: "compact",
        maxPoints: 100
    };
    const profile = {
        ...base,
        id: crypto.randomUUID(),
        name: `${base.symbol} strategy ${state.profiles.length + 1}`,
        provider: state.config?.marketData?.provider || base.provider || "MANUAL"
    };
    state.profiles = [...state.profiles, profile];
    state.selectedProfileId = profile.id;
    renderProfiles();
    applyProfileToForm(profile);
}

function deleteProfile() {
    if (state.profiles.length <= 1) {
        toast("Deve restare almeno un profilo", true);
        return;
    }
    state.profiles = state.profiles.filter((profile) => profile.id !== state.selectedProfileId);
    state.selectedProfileId = state.profiles[0].id;
    renderProfiles();
    applyProfileToForm(selectedProfile());
}

function renderSignals() {
    $("signals-body").innerHTML = state.signals.length === 0
        ? emptyRow(7, "Nessun segnale")
        : state.signals.map((row) => `
            <tr>
                <td>${formatDate(row.occurredAt)}</td>
                <td>${row.symbol}</td>
                <td>${row.action}</td>
                <td>${formatNumber(row.price)}</td>
                <td>${formatNumber(row.quantity)}</td>
                <td>${row.source}</td>
                <td>${row.reason || "-"}</td>
            </tr>
        `).join("");
}

function renderMarketData() {
    const rows = state.status?.marketData || [];
    $("market-body").innerHTML = rows.length === 0
        ? emptyRow(8, "Nessun market data")
        : rows.map((row) => `
            <tr>
                <td>${row.symbol}</td>
                <td>${formatNumber(row.bid)}</td>
                <td>${formatNumber(row.ask)}</td>
                <td>${formatNumber(row.last)}</td>
                <td>${formatNumber(row.close)}</td>
                <td>${formatNumber(row.high)}</td>
                <td>${formatNumber(row.low)}</td>
                <td>${formatDate(row.updatedAt)}</td>
            </tr>
        `).join("");
}

function renderOrders() {
    $("orders-body").innerHTML = state.orders.length === 0
        ? emptyRow(6, "Nessun ordine")
        : state.orders.map((row) => `
            <tr>
                <td>${row.orderId ?? row.ibkrOrderId ?? "-"}</td>
                <td>${row.symbol ?? "-"}</td>
                <td>${row.side ?? "-"}</td>
                <td>${formatNumber(row.quantity)}</td>
                <td>${row.status ?? "-"}</td>
                <td>${row.role ?? "-"}</td>
            </tr>
        `).join("");
}

function renderPositions() {
    $("positions-body").innerHTML = state.positions.length === 0
        ? emptyRow(5, "Nessuna posizione")
        : state.positions.map((row) => `
            <tr>
                <td>${row.account ?? "-"}</td>
                <td>${row.symbol ?? "-"}</td>
                <td>${formatNumber(row.quantity)}</td>
                <td>${formatNumber(row.averageCost)}</td>
                <td>${formatDate(row.updatedAt)}</td>
            </tr>
        `).join("");
}

function renderHistory() {
    if (!state.history) {
        $("history-summary").textContent = "Nessuno storico scaricato";
        $("history-body").innerHTML = emptyRow(7, "Nessuna barra storica");
        return;
    }
    const source = state.history.source === "DATABASE" ? "database" : "Alpha Vantage";
    const loadedAt = state.history.loadedAt ? ` - caricato ${formatDate(state.history.loadedAt)}` : "";
    const message = state.history.message ? ` - ${state.history.message}` : "";
    $("history-summary").textContent =
        `${state.history.symbol} ${state.history.timeframe} ${state.history.bars.length} barre - fonte ${source}${loadedAt}${message}`;
    $("history-body").innerHTML = state.history.bars.map((bar) => `
        <tr>
            <td>${bar.index}</td>
            <td>${bar.label}</td>
            <td>${formatNumber(bar.open)}</td>
            <td>${formatNumber(bar.high)}</td>
            <td>${formatNumber(bar.low)}</td>
            <td>${formatNumber(bar.close)}</td>
            <td>${formatNumber(bar.volume)}</td>
        </tr>
    `).join("");
}

function renderSimulation() {
    if (!state.simulation) {
        $("simulation-summary").textContent = "Nessuna simulazione";
        $("simulation-metrics").innerHTML = "";
        $("price-chart").innerHTML = "";
        $("equity-chart").innerHTML = "";
        $("simulation-body").innerHTML = emptyRow(6, "Nessun segnale simulato");
        $("trades-body").innerHTML = emptyRow(7, "Nessun trade chiuso");
        return;
    }
    const simulation = state.simulation;
    $("simulation-summary").textContent =
        `${simulation.symbol} ${simulation.strategy} ${simulation.inputCount} prezzi, segnali ${simulation.signals.length}, trade ${simulation.trades.length}`;
    renderSimulationMetrics(simulation);
    renderPriceChart(simulation);
    renderEquityChart(simulation);
    $("simulation-body").innerHTML = simulation.signals.length === 0
        ? emptyRow(6, "Nessun segnale generato")
        : simulation.signals.map((row) => `
            <tr>
                <td>${formatDate(row.occurredAt)}</td>
                <td>${row.symbol}</td>
                <td>${row.action}</td>
                <td>${formatNumber(row.price)}</td>
                <td>${formatNumber(row.quantity)}</td>
                <td>${row.reason || "-"}</td>
            </tr>
        `).join("");
    $("trades-body").innerHTML = simulation.trades.length === 0
        ? emptyRow(7, "Nessun trade chiuso")
        : simulation.trades.map((row) => `
            <tr>
                <td>${row.number}</td>
                <td>${row.side}</td>
                <td>${formatNumber(row.entryPrice)}</td>
                <td>${formatNumber(row.exitPrice)}</td>
                <td>${formatNumber(row.quantity)}</td>
                <td>${formatNumber(row.netPnl)}</td>
                <td>${formatNumber(row.returnPercent)}%</td>
            </tr>
        `).join("");
}

function renderSimulationMetrics(simulation) {
    const metrics = simulation.metrics;
    const parameters = simulation.evaluationParameters;
    const items = [
        ["Capitale iniziale", formatNumber(metrics.initialCapital)],
        ["Equity finale", formatNumber(metrics.finalEquity)],
        ["Net profit", formatNumber(metrics.netProfit)],
        ["Return", `${formatNumber(metrics.totalReturnPercent)}%`],
        ["Max drawdown", `${formatNumber(metrics.maxDrawdownPercent)}%`],
        ["Win rate", `${formatNumber(metrics.winRatePercent)}%`],
        ["Trade", formatNumber(metrics.tradeCount)],
        ["Profit factor", formatNumber(metrics.profitFactor)],
        ["Avg trade", formatNumber(metrics.averageTrade)],
        ["Exposure", `${formatNumber(metrics.exposurePercent)}%`],
        ["Buy & hold", `${formatNumber(metrics.buyAndHoldReturnPercent)}%`],
        ["Parametri", `F${parameters.fastPeriod} / S${parameters.slowPeriod} / Q${parameters.quantity}`]
    ];
    $("simulation-metrics").innerHTML = items.map(([label, value]) => `
        <div class="mini-metric"><span>${label}</span><strong>${value}</strong></div>
    `).join("");
}

function renderPriceChart(simulation) {
    const points = simulation.priceSeries.map((point) => ({ x: point.index, y: point.price }));
    const markers = simulation.signals.map((signal) => {
        const point = nearestPricePoint(simulation.priceSeries, signal.occurredAt);
        return point ? { x: point.index, y: point.price, type: signal.action } : null;
    }).filter(Boolean);
    $("price-chart").innerHTML = lineChart(points, "line-price", markers);
}

function renderEquityChart(simulation) {
    const points = simulation.equityCurve.map((point) => ({ x: point.index, y: point.equity }));
    $("equity-chart").innerHTML = lineChart(points, "line-equity", []);
}

function nearestPricePoint(priceSeries, occurredAt) {
    const time = new Date(occurredAt).getTime();
    let best = null;
    let bestDistance = Number.POSITIVE_INFINITY;
    priceSeries.forEach((point) => {
        const distance = Math.abs(new Date(point.timestamp).getTime() - time);
        if (distance < bestDistance) {
            best = point;
            bestDistance = distance;
        }
    });
    return best;
}

function lineChart(points, lineClass, markers) {
    if (!points || points.length === 0) {
        return `<div class="empty-row">Nessun dato</div>`;
    }
    const width = 760;
    const height = 260;
    const pad = { left: 58, right: 18, top: 18, bottom: 34 };
    const minX = Math.min(...points.map((point) => point.x));
    const maxX = Math.max(...points.map((point) => point.x));
    const minY = Math.min(...points.map((point) => point.y));
    const maxY = Math.max(...points.map((point) => point.y));
    const spanX = Math.max(1, maxX - minX);
    const spanY = Math.max(1, maxY - minY);
    const lowY = minY - spanY * 0.08;
    const highY = maxY + spanY * 0.08;
    const scaleX = (value) => pad.left + (value - minX) / spanX * (width - pad.left - pad.right);
    const scaleY = (value) => height - pad.bottom - (value - lowY) / (highY - lowY) * (height - pad.top - pad.bottom);
    const path = points.map((point, index) =>
        `${index === 0 ? "M" : "L"}${scaleX(point.x).toFixed(2)},${scaleY(point.y).toFixed(2)}`
    ).join(" ");
    const gridValues = [lowY, lowY + (highY - lowY) / 2, highY];
    const markerSvg = markers.map((marker) => {
        const className = marker.type === "SELL" ? "sell-marker" : "buy-marker";
        const x = scaleX(marker.x).toFixed(2);
        const y = scaleY(marker.y).toFixed(2);
        return marker.type === "SELL"
            ? `<path class="${className}" d="M ${x} ${Number(y) + 6} l 6 -12 h -12 z"></path>`
            : `<path class="${className}" d="M ${x} ${Number(y) - 6} l 6 12 h -12 z"></path>`;
    }).join("");
    return `
        <svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" role="img">
            ${gridValues.map((value) => `
                <line class="grid-line" x1="${pad.left}" y1="${scaleY(value)}" x2="${width - pad.right}" y2="${scaleY(value)}"></line>
                <text x="8" y="${scaleY(value) + 4}">${formatNumber(value)}</text>
            `).join("")}
            <path class="${lineClass}" d="${path}"></path>
            ${markerSvg}
            <text x="${pad.left}" y="${height - 10}">0</text>
            <text x="${width - 62}" y="${height - 10}">${points.length - 1}</text>
        </svg>
    `;
}

function renderAll() {
    renderStatus();
    renderProfiles();
    renderSignals();
    renderMarketData();
    renderOrders();
    renderPositions();
    renderHistory();
    renderSimulation();
}

function parsePrices() {
    const values = $("simulate-prices").value
        .split(/[,\s;]+/)
        .map((value) => value.trim())
        .filter(Boolean)
        .map(Number);
    if (values.length === 0 || values.some((value) => !Number.isFinite(value) || value <= 0)) {
        throw new Error("Serie prezzi non valida");
    }
    return values;
}

async function refreshAll() {
    const [config, profileResponse, status, signals, orders, positions] = await Promise.all([
        api("/api/workbench/config"),
        api("/api/workbench/action-profiles"),
        api("/api/paper/status"),
        api("/api/paper/signals"),
        api("/api/paper/orders"),
        api("/api/paper/positions")
    ]);
    state.config = config;
    state.providerOptions = profileResponse.providerOptions;
    state.strategyOptions = profileResponse.strategyOptions;
    state.profiles = profileResponse.profiles;
    state.selectedProfileId = state.selectedProfileId || state.profiles[0]?.id;
    state.status = status;
    state.signals = normalizeListResponse(signals);
    state.orders = normalizeListResponse(orders);
    state.positions = normalizeListResponse(positions);
    fillSelect($("profile-provider"), state.providerOptions);
    fillSelect($("profile-strategy"), state.strategyOptions);
    applyProfileToForm(selectedProfile());
    renderAll();
}

async function refreshStatusOnly() {
    state.status = await api("/api/paper/status");
    renderStatus();
    renderMarketData();
}

async function publishManualPrice() {
    saveSelectedFormInMemory();
    const profile = selectedProfile();
    const price = num("manual-price-value");
    const snapshot = await api("/api/workbench/market-data/manual-price", {
        method: "POST",
        body: JSON.stringify({
            symbol: profile.symbol,
            price,
            provider: profile.provider,
            strategy: profile.strategy
        })
    });
    await refreshStatusOnly();
    toast(`Prezzo ${snapshot.symbol} pubblicato`);
}

async function publishManualSignal() {
    saveSelectedFormInMemory();
    const profile = selectedProfile();
    const signal = await api("/api/workbench/actions/manual-signal", {
        method: "POST",
        body: JSON.stringify({
            symbol: profile.symbol,
            action: $("signal-action").value,
            price: num("signal-price"),
            quantity: profile.quantity,
            reason: $("signal-reason").value || profile.name,
            execute: boolValue("signal-execute"),
            provider: profile.provider,
            strategy: profile.strategy
        })
    });
    state.signals = [signal, ...state.signals].slice(0, 100);
    renderSignals();
    setResultTab("signals");
    toast(`Segnale ${signal.action} ${signal.symbol} pubblicato`);
}

async function downloadHistory(refresh = false) {
    saveSelectedFormInMemory();
    let profile = selectedProfile();
    const providerUpdated = await ensureHistoricalProvider(profile);
    profile = selectedProfile();
    state.history = await api("/api/workbench/historical/download", {
        method: "POST",
        body: JSON.stringify({
            symbol: profile.symbol,
            provider: profile.provider,
            timeframe: profile.timeframe,
            outputSize: profile.outputSize,
            maxPoints: profile.maxPoints,
            refresh
        })
    });
    $("simulate-prices").value = state.history.closePrices.join(", ");
    setResultTab("history");
    renderHistory();
    const prefix = providerUpdated ? "Profilo aggiornato ad ALPHA_VANTAGE. " : "";
    const source = state.history.source === "DATABASE" ? "database" : "Alpha Vantage";
    const action = refresh ? "Aggiornamento storico" : "Storico";
    const suffix = state.history.message ? `. ${state.history.message}` : "";
    toast(`${prefix}${action}: ${state.history.bars.length} barre ${profile.symbol} da ${source}${suffix}`);
}

async function refreshHistoryFromProvider() {
    await downloadHistory(true);
}

async function ensureHistoricalProvider(profile) {
    if (profile.provider === "ALPHA_VANTAGE") {
        return false;
    }
    if (!alphaVantageConfigured()) {
        throw new Error(
            "Per scaricare lo storico scegli ALPHA_VANTAGE nel Provider azione o configura Alpha Vantage nella pagina Provider dati"
        );
    }

    const updated = { ...profile, provider: "ALPHA_VANTAGE" };
    state.profiles = state.profiles.map((item) => item.id === updated.id ? updated : item);
    setValue("profile-provider", "ALPHA_VANTAGE");
    await persistProfiles();
    return true;
}

async function simulateStrategy() {
    saveSelectedFormInMemory();
    const profile = selectedProfile();
    state.simulation = await api("/api/workbench/strategy/simulate", {
        method: "POST",
        body: JSON.stringify({
            symbol: profile.symbol,
            provider: profile.provider,
            strategy: profile.strategy,
            prices: parsePrices(),
            fastPeriod: profile.fastPeriod,
            slowPeriod: profile.slowPeriod,
            quantity: profile.quantity,
            initialCapital: profile.initialCapital,
            commissionPerOrder: profile.commissionPerOrder,
            slippagePercent: profile.slippagePercent,
            publishSignals: false
        })
    });
    setResultTab("simulation");
    renderSimulation();
    toast("Simulazione completata");
}

async function downloadHistoryAndSimulate() {
    await downloadHistory();
    await simulateStrategy();
}

async function activateKillSwitch() {
    await api("/api/paper/kill-switch/activate", { method: "POST" });
    await refreshStatusOnly();
    toast("Kill switch attivo");
}

async function deactivateKillSwitch() {
    await api("/api/paper/kill-switch/deactivate", { method: "POST" });
    await refreshStatusOnly();
    toast("Kill switch disattivo");
}

function setResultTab(name) {
    document.querySelectorAll("[data-result-tab]").forEach((button) => {
        button.classList.toggle("active", button.dataset.resultTab === name);
    });
    document.querySelectorAll(".result-tab").forEach((panel) => {
        panel.classList.toggle("active", panel.id === `result-${name}`);
    });
}

function bind(id, handler) {
    $(id).addEventListener("click", async () => {
        try {
            await handler();
        } catch (error) {
            toast(error.message, true);
        }
    });
}

function initEvents() {
    bind("refresh-button", refreshAll);
    bind("add-profile-button", addProfile);
    bind("save-profiles-button", saveProfiles);
    bind("delete-profile-button", deleteProfile);
    bind("download-history-button", downloadHistory);
    bind("update-history-button", refreshHistoryFromProvider);
    bind("download-simulate-button", downloadHistoryAndSimulate);
    bind("simulate-button", simulateStrategy);
    bind("publish-price-button", publishManualPrice);
    bind("publish-signal-button", publishManualSignal);
    bind("activate-kill-button", activateKillSwitch);
    bind("deactivate-kill-button", deactivateKillSwitch);
    document.querySelectorAll("[data-result-tab]").forEach((button) => {
        button.addEventListener("click", () => setResultTab(button.dataset.resultTab));
    });
}

initEvents();
refreshAll().catch((error) => toast(error.message, true));
window.setInterval(() => refreshStatusOnly().catch(() => undefined), 5000);
