const state = {
    config: null,
    stocks: [],
    profiles: [],
    strategyOptions: [],
    selectedStockId: null,
    selectedProfileId: null,
    status: null,
    signals: [],
    orders: [],
    positions: [],
    history: null,
    historyRequestId: 0,
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

async function apiForm(path, formData) {
    const response = await fetch(path, {
        method: "POST",
        headers: {
            "Accept": "application/json"
        },
        body: formData
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
    return response.json();
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
    const element = $(id);
    if (element) {
        element.value = value ?? "";
    }
}

function num(id) {
    const value = $(id).value;
    return value === "" ? null : Number(value);
}

function boolValue(id) {
    return $(id).value === "true";
}

function selectedStock() {
    return state.stocks.find((stock) => stock.id === state.selectedStockId)
        || state.stocks[0];
}

function selectedProfile() {
    const stock = selectedStock();
    if (!stock) {
        return null;
    }
    const strategies = stock.strategies || [];
    return strategies.find((profile) => profile.id === state.selectedProfileId)
        || strategies[0]
        || null;
}

function flattenStocks(stocks) {
    return stocks.flatMap((stock) =>
        (stock.strategies || []).map((strategy) => ({
            ...strategy,
            symbol: stock.symbol
        }))
    );
}

function profileToStock(profile) {
    return {
        id: crypto.randomUUID(),
        symbol: profile.symbol,
        name: profile.symbol,
        securityType: "STK",
        exchange: "SMART",
        currency: "USD",
        primaryExchange: defaultPrimaryExchange(profile.symbol),
        strategies: [profile]
    };
}

function stocksFromProfiles(profiles) {
    const stocksBySymbol = new Map();
    profiles.forEach((profile) => {
        const symbol = (profile.symbol || "SPY").trim().toUpperCase();
        if (!stocksBySymbol.has(symbol)) {
            stocksBySymbol.set(symbol, profileToStock({ ...profile, symbol }));
        } else {
            stocksBySymbol.get(symbol).strategies.push({ ...profile, symbol });
        }
    });
    return Array.from(stocksBySymbol.values());
}

function defaultStrategy(symbol, index = 1) {
    return {
        id: crypto.randomUUID(),
        name: `${symbol} strategy ${index}`,
        symbol,
        provider: "IBKR",
        strategy: "MOVING_AVERAGE_CROSSOVER",
        tradeDirection: "LONG_SHORT",
        fastPeriod: 5,
        slowPeriod: 20,
        quantity: 1,
        initialCapital: 10000,
        commissionPerOrder: 0,
        slippagePercent: 0,
        timeframe: "DAILY",
        outputSize: "compact",
        maxPoints: 100,
        pivotStrength: 2,
        minimumTick: 0.01,
        tteMaxBars: 3,
        entryBufferTicks: 1,
        stopBufferTicks: 1,
        minimumRoomEnabled: true,
        minimumRoomType: "R",
        minimumRoomValue: 0.5,
        allowDoubleTriple: false,
        doubleTripleToleranceTicks: 1,
        rejectGapOverEntry: true
    };
}

function defaultStock(symbol) {
    const normalized = symbol.trim().toUpperCase();
    return {
        id: crypto.randomUUID(),
        symbol: normalized,
        name: normalized,
        securityType: "STK",
        exchange: "SMART",
        currency: "USD",
        primaryExchange: defaultPrimaryExchange(normalized),
        strategies: [defaultStrategy(normalized)]
    };
}

function defaultPrimaryExchange(symbol) {
    const normalized = (symbol || "").trim().toUpperCase();
    const nasdaq = new Set([
        "AAPL", "AMD", "AMZN", "AVGO", "GOOG", "GOOGL", "INTC",
        "META", "MSFT", "NFLX", "NVDA", "QQQ", "SMH", "TSLA"
    ]);
    const nyse = new Set(["BA", "BAC", "CAT", "CVX", "DIS", "GS", "JPM", "KO", "NKE", "XOM"]);
    const arca = new Set(["DIA", "IWM", "SPY", "XLE", "XLF", "XLI", "XLK", "XLY"]);
    if (nasdaq.has(normalized)) {
        return "NASDAQ";
    }
    if (nyse.has(normalized)) {
        return "NYSE";
    }
    if (arca.has(normalized)) {
        return "ARCA";
    }
    return "";
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

function todayIsoDate() {
    return new Date().toISOString().slice(0, 10);
}

function normalizeHistoricalInterval(value) {
    const normalized = (value || "DAILY").trim().toUpperCase();
    if (["DAILY", "DAY", "1D"].includes(normalized)) {
        return "DAILY";
    }
    if (["HOURLY", "HOUR", "1H", "60MIN", "60MINS", "60M"].includes(normalized)) {
        return "HOURLY";
    }
    if (["MINUTES_5", "5MIN", "5MINS", "5MINUTES", "5M"].includes(normalized)) {
        return "MINUTES_5";
    }
    return null;
}

function maxIsoDate(first, second) {
    if (!first) {
        return second;
    }
    if (!second) {
        return first;
    }
    return first > second ? first : second;
}

function minIsoDate(first, second) {
    if (!first) {
        return second;
    }
    if (!second) {
        return first;
    }
    return first < second ? first : second;
}

function emptyRow(columnCount, text) {
    return `<tr><td class="empty-row" colspan="${columnCount}">${text}</td></tr>`;
}

function toast(message, isError = false, duration = 3200) {
    const element = $("toast");
    element.textContent = message;
    element.classList.toggle("error", isError);
    element.classList.add("visible");
    window.clearTimeout(toast.timer);
    toast.timer = window.setTimeout(() => element.classList.remove("visible"), duration);
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

function appendOption(select, value, label) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = label;
    select.appendChild(option);
}

function renderStockSelect() {
    const select = $("stock-select");
    if (!select) {
        return;
    }
    select.innerHTML = "";
    const stock = selectedStock();
    if (state.stocks.length === 0) {
        appendOption(select, "", "Nessuno stock configurato");
        select.disabled = true;
        return;
    }
    state.stocks.forEach((item) => appendOption(select, item.id, item.symbol));
    select.disabled = false;
    select.value = stock?.id || "";
}

function renderStrategySelect() {
    const select = $("strategy-select");
    if (!select) {
        return;
    }
    const stock = selectedStock();
    const strategies = stock?.strategies || [];
    const profile = selectedProfile();
    select.innerHTML = "";
    if (strategies.length === 0) {
        appendOption(select, "", "Nessuna strategia");
        select.disabled = true;
        return;
    }
    strategies.forEach((item) => appendOption(select, item.id, item.name));
    select.disabled = false;
    select.value = profile?.id || "";
}

function renderProfileControls() {
    const emptyState = $("stock-empty-state");
    if (emptyState) {
        emptyState.hidden = state.stocks.length > 0;
    }
    const deleteStockButton = $("delete-stock-button");
    if (deleteStockButton) {
        deleteStockButton.disabled = state.stocks.length === 0;
    }
    const deleteStrategyButton = $("delete-strategy-button");
    if (deleteStrategyButton) {
        deleteStrategyButton.disabled = !selectedProfile();
    }
}

function renderProfiles() {
    renderStockSelect();
    renderStrategySelect();
    renderProfileControls();
}

async function selectStockById(stockId) {
    if (!stockId || stockId === state.selectedStockId) {
        return;
    }
    if (!state.stocks.some((stock) => stock.id === stockId)) {
        renderProfiles();
        return;
    }
    saveSelectedFormInMemory();
    state.selectedStockId = stockId;
    const stock = selectedStock();
    state.selectedProfileId = (stock?.strategies || [])[0]?.id || null;
    applyProfileToForm(selectedProfile());
    renderProfiles();
    await loadStoredHistoryForSelectedProfile({
        silent: true,
        switchTab: true
    });
}

async function selectProfileById(profileId) {
    if (!profileId || profileId === state.selectedProfileId) {
        return;
    }
    const stock = selectedStock();
    if (!(stock?.strategies || []).some((profile) => profile.id === profileId)) {
        renderProfiles();
        return;
    }
    saveSelectedFormInMemory();
    state.selectedProfileId = profileId;
    applyProfileToForm(selectedProfile());
    renderProfiles();
    await loadStoredHistoryForSelectedProfile({
        silent: true,
        switchTab: true
    });
}

function applyProfileToForm(profile) {
    if (!profile) {
        clearProfileForm();
        return;
    }
    const stock = selectedStock();
    setValue("profile-name", profile.name);
    setValue("profile-symbol", stock?.symbol || profile.symbol);
    setValue("stock-security-type", stock?.securityType || "STK");
    setValue("stock-exchange", stock?.exchange || "SMART");
    setValue("stock-primary-exchange",
        stock?.primaryExchange || defaultPrimaryExchange(stock?.symbol || profile.symbol));
    setValue("stock-currency", stock?.currency || "USD");
    setValue("profile-strategy", profile.strategy);
    setValue("profile-trade-direction", profile.tradeDirection || "LONG_SHORT");
    setValue("profile-fast", profile.fastPeriod);
    setValue("profile-slow", profile.slowPeriod);
    setValue("profile-pivot-strength", profile.pivotStrength ?? 2);
    setValue("profile-minimum-tick", profile.minimumTick ?? 0.01);
    setValue("profile-tte-max-bars", profile.tteMaxBars ?? 3);
    setValue("profile-entry-buffer-ticks", profile.entryBufferTicks ?? 1);
    setValue("profile-stop-buffer-ticks", profile.stopBufferTicks ?? 1);
    setValue("profile-minimum-room-enabled", String(profile.minimumRoomEnabled ?? true));
    setValue("profile-minimum-room-type", profile.minimumRoomType || "R");
    setValue("profile-minimum-room-value", profile.minimumRoomValue ?? 0.5);
    setValue("profile-allow-double-triple", String(profile.allowDoubleTriple ?? false));
    setValue("profile-double-triple-tolerance", profile.doubleTripleToleranceTicks ?? 1);
    setValue("profile-reject-gap", String(profile.rejectGapOverEntry ?? true));
    toggleStrategyFields();
    setValue("profile-quantity", profile.quantity);
    setValue("profile-capital", profile.initialCapital);
    setValue("profile-commission", profile.commissionPerOrder);
    setValue("profile-slippage", profile.slippagePercent);
    setValue("profile-timeframe", profile.timeframe);
    setValue("profile-output-size", profile.outputSize);
    setValue("profile-max-points", profile.maxPoints);
    setValue("signal-price", "");
    setValue("signal-action", "BUY");
    setValue("signal-execute", "false");
    setValue("signal-reason", profile.name);
    if (!$("historical-from").value) {
        setValue("historical-from", "2015-01-01");
    }
    if (!$("historical-to").value) {
        setValue("historical-to", todayIsoDate());
    }
}

function clearProfileForm() {
    [
        "profile-name",
        "profile-symbol",
        "stock-security-type",
        "stock-exchange",
        "stock-primary-exchange",
        "stock-currency",
        "profile-fast",
        "profile-slow",
        "profile-pivot-strength",
        "profile-minimum-tick",
        "profile-tte-max-bars",
        "profile-entry-buffer-ticks",
        "profile-stop-buffer-ticks",
        "profile-minimum-room-value",
        "profile-double-triple-tolerance",
        "profile-quantity",
        "profile-capital",
        "profile-commission",
        "profile-slippage",
        "profile-max-points",
        "signal-price",
        "signal-reason",
        "simulate-prices"
    ].forEach((id) => setValue(id, ""));
    setValue("profile-strategy", "");
    setValue("profile-trade-direction", "LONG_SHORT");
    setValue("profile-timeframe", "DAILY");
    setValue("profile-output-size", "compact");
    setValue("signal-action", "BUY");
    setValue("signal-execute", "false");
}

function collectProfileFromForm() {
    const current = selectedProfile();
    const stock = collectStockFromForm();
    return {
        id: current?.id || crypto.randomUUID(),
        name: $("profile-name").value.trim() || "Nuovo profilo",
        symbol: stock.symbol,
        provider: "IBKR",
        strategy: $("profile-strategy").value,
        tradeDirection: $("profile-trade-direction").value,
        fastPeriod: num("profile-fast"),
        slowPeriod: num("profile-slow"),
        quantity: num("profile-quantity"),
        initialCapital: num("profile-capital"),
        commissionPerOrder: num("profile-commission"),
        slippagePercent: num("profile-slippage"),
        timeframe: $("profile-timeframe").value,
        outputSize: $("profile-output-size").value,
        maxPoints: num("profile-max-points"),
        pivotStrength: num("profile-pivot-strength"),
        minimumTick: num("profile-minimum-tick"),
        tteMaxBars: num("profile-tte-max-bars"),
        entryBufferTicks: num("profile-entry-buffer-ticks"),
        stopBufferTicks: num("profile-stop-buffer-ticks"),
        minimumRoomEnabled: $("profile-minimum-room-enabled").value === "true",
        minimumRoomType: "R",
        minimumRoomValue: num("profile-minimum-room-value"),
        allowDoubleTriple: $("profile-allow-double-triple").value === "true",
        doubleTripleToleranceTicks: num("profile-double-triple-tolerance"),
        rejectGapOverEntry: $("profile-reject-gap").value === "true"
    };
}

function toggleStrategyFields() {
    const joeRoss = $("profile-strategy")?.value === "JOE_ROSS_123_RH_TTE";
    document.querySelectorAll(".ma-strategy-field")
        .forEach((field) => field.hidden = joeRoss);
    document.querySelectorAll(".joe-ross-strategy-field")
        .forEach((field) => field.hidden = !joeRoss);
}

function collectStockFromForm() {
    const current = selectedStock();
    const symbol = $("profile-symbol").value.trim().toUpperCase() || current?.symbol || "SPY";
    return {
        id: current?.id || crypto.randomUUID(),
        symbol,
        name: symbol,
        securityType: $("stock-security-type").value.trim().toUpperCase()
            || current?.securityType
            || "STK",
        exchange: $("stock-exchange").value.trim().toUpperCase() || current?.exchange || "SMART",
        currency: $("stock-currency").value.trim().toUpperCase() || current?.currency || "USD",
        primaryExchange: $("stock-primary-exchange").value.trim().toUpperCase()
            || current?.primaryExchange
            || defaultPrimaryExchange(symbol)
    };
}

function saveSelectedFormInMemory() {
    const profile = selectedProfile();
    const stock = selectedStock();
    if (!profile) {
        return;
    }
    const updated = collectProfileFromForm();
    const updatedStock = collectStockFromForm();
    state.stocks = state.stocks.map((item) => {
        if (item.id !== stock.id) {
            return item;
        }
        return {
            ...item,
            symbol: updatedStock.symbol,
            name: updatedStock.name,
            securityType: updatedStock.securityType,
            exchange: updatedStock.exchange,
            currency: updatedStock.currency,
            primaryExchange: updatedStock.primaryExchange,
            strategies: item.strategies.map((strategy) =>
                strategy.id === updated.id
                    ? updated
                    : { ...strategy, symbol: updatedStock.symbol })
        };
    });
    state.profiles = flattenStocks(state.stocks);
}

async function saveProfiles() {
    saveSelectedFormInMemory();
    await persistProfiles();
    toast("Profili salvati");
}

async function persistProfiles() {
    const response = await api("/api/workbench/action-profiles", {
        method: "PUT",
        body: JSON.stringify({ stocks: state.stocks })
    });
    state.stocks = response.stocks || stocksFromProfiles(response.profiles || []);
    state.profiles = response.profiles;
    state.selectedStockId = selectedStock()?.id || state.stocks[0]?.id || null;
    state.selectedProfileId = selectedProfile()?.id || selectedStock()?.strategies[0]?.id || null;
    renderProfiles();
    applyProfileToForm(selectedProfile());
}

function addStock() {
    saveSelectedFormInMemory();
    const values = $("bulk-symbols").value
        .split(/[,\s;]+/)
        .map((value) => value.trim().toUpperCase())
        .filter(Boolean);
    const symbols = values.length === 0 ? ["SPY"] : values;
    const existing = new Set(state.stocks.map((stock) => stock.symbol));
    const added = symbols
        .filter((symbol) => !existing.has(symbol))
        .map(defaultStock);
    if (added.length === 0) {
        toast("Stock gia' presenti", true);
        return;
    }
    state.stocks = [...state.stocks, ...added];
    state.profiles = flattenStocks(state.stocks);
    const stock = added[added.length - 1];
    state.selectedStockId = stock.id;
    state.selectedProfileId = stock.strategies[0].id;
    setValue("bulk-symbols", "");
    renderProfiles();
    applyProfileToForm(selectedProfile());
}

function addStrategy() {
    saveSelectedFormInMemory();
    let stock = selectedStock();
    if (!stock) {
        const created = defaultStock("SPY");
        state.stocks = [created];
        state.selectedStockId = created.id;
        stock = created;
    }
    const base = selectedProfile() || defaultStrategy(stock.symbol);
    const profile = {
        ...base,
        id: crypto.randomUUID(),
        symbol: stock.symbol,
        name: `${stock.symbol} strategy ${stock.strategies.length + 1}`
    };
    state.stocks = state.stocks.map((item) =>
        item.id === stock.id
            ? { ...item, strategies: [...item.strategies, profile] }
            : item);
    state.profiles = flattenStocks(state.stocks);
    state.selectedProfileId = profile.id;
    renderProfiles();
    applyProfileToForm(profile);
}

function deleteStock() {
    if (state.stocks.length <= 1) {
        toast("Deve restare almeno uno stock", true);
        return;
    }
    state.stocks = state.stocks.filter((stock) => stock.id !== state.selectedStockId);
    state.selectedStockId = state.stocks[0].id;
    state.selectedProfileId = state.stocks[0].strategies[0]?.id || null;
    state.profiles = flattenStocks(state.stocks);
    renderProfiles();
    applyProfileToForm(selectedProfile());
}

function deleteStrategy() {
    const stock = selectedStock();
    if (!stock || stock.strategies.length <= 1) {
        toast("Deve restare almeno una strategia per stock", true);
        return;
    }
    const strategies = stock.strategies.filter((profile) => profile.id !== state.selectedProfileId);
    state.stocks = state.stocks.map((item) =>
        item.id === stock.id ? { ...item, strategies } : item);
    state.selectedProfileId = strategies[0].id;
    state.profiles = flattenStocks(state.stocks);
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
        $("history-summary").textContent = "Nessuno storico caricato";
        $("history-body").innerHTML = emptyRow(7, "Nessuna barra storica");
        return;
    }
    const source = state.history.source === "DATABASE"
        ? "database"
        : (state.history.source || state.history.provider || "storico");
    const loadedAt = state.history.loadedAt ? ` - caricato ${formatDate(state.history.loadedAt)}` : "";
    const message = state.history.message ? ` - ${state.history.message}` : "";
    $("history-summary").textContent =
        `${state.history.symbol} ${state.history.timeframe} ${state.history.bars.length} barre - fonte ${source}${loadedAt}${message}`;
    const rows = [...state.history.bars].sort((first, second) => {
        const firstTime = Date.parse(first.timestamp || first.label || "");
        const secondTime = Date.parse(second.timestamp || second.label || "");
        if (Number.isFinite(firstTime) && Number.isFinite(secondTime)) {
            return secondTime - firstTime;
        }
        return String(second.label || "").localeCompare(String(first.label || ""));
    });
    $("history-body").innerHTML = rows.map((bar) => `
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

function historicalBarsToHistory(symbol, interval, barsResponse, message) {
    const bars = (barsResponse || []).map((bar, index) => {
        const label = (bar.dateTime || "").slice(0, 10) || String(index);
        return {
            index,
            timestamp: bar.dateTime ? `${bar.dateTime}Z` : new Date().toISOString(),
            label,
            open: Number(bar.open),
            high: Number(bar.high),
            low: Number(bar.low),
            close: Number(bar.close),
            volume: Number(bar.volume || 0),
            provider: bar.provider,
            interval: bar.interval,
            priceAdjustment: bar.priceAdjustment
        };
    });
    return {
        provider: "STOOQ",
        symbol,
        timeframe: interval,
        outputSize: "range",
        bars,
        closePrices: bars.map((bar) => bar.close),
        source: "STOOQ",
        loadedAt: new Date().toISOString(),
        message
    };
}

function tradeId(row) {
    const number = Number(row.number);
    return Number.isFinite(number) ? `T${String(number).padStart(3, "0")}` : "T---";
}

function tradeBarNumber(value) {
    const number = Number(value);
    return Number.isFinite(number) ? String(number + 1) : "-";
}

function tradeSideClass(side) {
    const normalized = (side || "").toLowerCase();
    return normalized === "short" ? "short" : "long";
}

function tradeEventClass(eventType) {
    const normalized = (eventType || "").toUpperCase();
    if (normalized === "ENTRY") {
        return "entry";
    }
    if (normalized.includes("STOP MOVED")
            || normalized.includes("STOP CANCELLED")
            || normalized.includes("INITIAL STOP")
            || normalized.includes("STOP QUANTITY UPDATED")
            || normalized.includes("STOP UPDATE REJECTED")) {
        return "stop-adjustment";
    }
    if (normalized.includes("STOP_LOSS")) {
        return "stop-loss";
    }
    if (normalized.includes("TP")) {
        return "take-profit";
    }
    if (normalized.includes("SIGNAL")) {
        return "signal";
    }
    return "other";
}

function tradeEvents(row) {
    if (Array.isArray(row.events) && row.events.length > 0) {
        return row.events;
    }
    const exitType = row.exitReason ? `EXIT - ${row.exitReason}` : "EXIT";
    return [
        {
            eventType: "ENTRY",
            index: row.entryIndex,
            occurredAt: row.entryAt,
            price: row.entryPrice,
            quantity: row.quantity,
            netPnl: 0,
            returnPercent: 0
        },
        {
            eventType: exitType,
            index: row.exitIndex,
            occurredAt: row.exitAt,
            price: row.exitPrice,
            quantity: row.quantity,
            netPnl: row.netPnl,
            returnPercent: row.returnPercent
        }
    ];
}

function tradePnlClass(value) {
    const number = Number(value);
    if (number > 0) {
        return "profit";
    }
    if (number < 0) {
        return "loss";
    }
    return "flat";
}

function renderSimulation() {
    const exportButton = $("export-excel-button");
    if (!state.simulation) {
        $("simulation-summary").textContent = "Nessuna simulazione";
        $("simulation-metrics").innerHTML = "";
        $("price-chart").innerHTML = "";
        $("equity-chart").innerHTML = "";
        $("simulation-body").innerHTML = emptyRow(6, "Nessun segnale simulato");
        $("trades-body").innerHTML = emptyRow(8, "Nessun trade chiuso");
        if (exportButton) {
            exportButton.disabled = true;
        }
        return;
    }
    const simulation = state.simulation;
    if (exportButton) {
        exportButton.disabled = !simulation.backtestId;
    }
    const executionModel = simulation.evaluationParameters?.executionModel || "";
    $("simulation-summary").textContent =
        `${simulation.symbol} ${simulation.strategy} ${simulation.inputCount} barre, segnali ${simulation.signals.length}, trade ${simulation.trades.length}${executionModel ? ` - ${executionModel}` : ""}`;
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
        ? emptyRow(8, "Nessun trade chiuso")
        : simulation.trades.map((row, tradeIndex) => {
            const sideClass = tradeSideClass(row.side);
            const groupClass = `trade-group-${(tradeIndex % 6) + 1}`;
            const events = tradeEvents(row);
            return events.map((event, eventIndex) => {
                const eventClass = tradeEventClass(event.eventType);
                const pnlClass = tradePnlClass(event.netPnl);
                const boundaryClass = eventIndex === 0
                    ? "trade-group-start"
                    : (eventIndex === events.length - 1 ? "trade-group-end" : "");
                return `
            <tr class="trade-row ${groupClass} ${boundaryClass}">
                <td><span class="trade-id">${tradeId(row)}</span></td>
                <td><span class="trade-side ${sideClass}">${row.side}</span></td>
                <td>
                    <span class="trade-event ${eventClass}">${event.eventType || "-"}</span>
                    ${event.details ? `<small class="trade-bar">${event.details}</small>` : ""}
                </td>
                <td>
                    <strong>${formatNumber(event.price)}</strong>
                    <small class="trade-bar">Bar #${tradeBarNumber(event.index)}</small>
                </td>
                <td>${formatDate(event.occurredAt)}</td>
                <td>${formatNumber(event.quantity)}</td>
                <td class="trade-pnl ${pnlClass}">${formatNumber(event.netPnl)}</td>
                <td class="trade-pnl ${pnlClass}">${formatNumber(event.returnPercent)}%</td>
            </tr>
        `;
            }).join("");
        }).join("");
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
        ["Direzione", tradeDirectionLabel(parameters.tradeDirection)]
    ];
    if (parameters.strategy !== "JOE_ROSS_123_RH_TTE") {
        items.push(["Parametri",
            `F${parameters.fastPeriod} / S${parameters.slowPeriod} / Q${parameters.quantity}`]);
    }
    const joe = simulation.joeRossStatistics;
    if (joe) {
        items.push(
            ["1-2-3", formatNumber(joe.patterns123Found)],
            ["Breakout P2", formatNumber(joe.point2Breakouts)],
            ["Ross Hook", formatNumber(joe.rossHooksFormed)],
            ["TTE valide", formatNumber(joe.validTteCandidates)],
            ["TTE trigger", formatNumber(joe.tteTriggered)],
            ["TTE scadute", formatNumber(joe.tteExpired)],
            ["Room reject", formatNumber(joe.tteRejectedInsufficientRoom)],
            ["Gap reject", formatNumber(joe.tteRejectedGap)],
            ["RH missed", formatNumber(joe.tteMissedRossHookBroken)],
            ["Setup invalidati", formatNumber(joe.setupsInvalidated)],
            ["Trade LONG", formatNumber(joe.longTradesTriggered)],
            ["Trade SHORT", formatNumber(joe.shortTradesTriggered)]
        );
    }
    if (simulation.trades.length > 0) {
        const average = (field) => simulation.trades
            .map((trade) => Number(trade[field]))
            .filter(Number.isFinite)
            .reduce((sum, value, _index, values) => sum + value / values.length, 0);
        items.push(
            ["Average R", formatNumber(simulation.trades
                .reduce((sum, trade) => sum + (trade.effectiveRisk
                    ? trade.netPnl / trade.effectiveRisk : 0), 0)
                / simulation.trades.length)],
            ["Average MFE R", formatNumber(average("mfeR"))],
            ["Average MAE R", formatNumber(average("maeR"))],
            ["Average bars in trade", formatNumber(simulation.trades
                .reduce((sum, trade) => sum + Math.max(0, trade.exitIndex - trade.entryIndex + 1), 0)
                / simulation.trades.length)]
        );
    }
    $("simulation-metrics").innerHTML = items.map(([label, value]) => `
        <div class="mini-metric"><span>${label}</span><strong>${value}</strong></div>
    `).join("");
}

function renderPriceChart(simulation) {
    const bars = simulation.priceSeries.map((point) => ({
        x: point.index,
        open: point.open ?? point.price,
        high: point.high ?? point.price,
        low: point.low ?? point.price,
        close: point.close ?? point.price
    }));
    const signalMarkers = simulation.signals
        .filter((signal) => signal.action !== "HOLD"
            && signal.source !== "JOE_ROSS_123_RH_TTE")
        .map((signal) => {
        const point = nearestPricePoint(simulation.priceSeries, signal.occurredAt);
        return point ? { x: point.index, y: point.close ?? point.price, type: signal.action } : null;
    }).filter(Boolean);
    const joeMarkers = (simulation.joeRossEvents || []).map((event) => {
        const label = joeRossMarkerLabel(event.type);
        return label ? {
            x: event.barIndex,
            y: event.price,
            type: event.direction === "SHORT" ? "SELL" : "BUY",
            label
        } : null;
    }).filter(Boolean);
    const stopMarkers = (simulation.trades || [])
        .filter((trade) => Number.isFinite(Number(trade.initialStopPrice)))
        .map((trade) => ({
            x: trade.entryIndex,
            y: Number(trade.initialStopPrice),
            type: "STOP",
            label: "STOP"
        }));
    $("price-chart").innerHTML = ohlcChart(
        bars,
        [...signalMarkers, ...joeMarkers, ...stopMarkers]);
}

function joeRossMarkerLabel(type) {
    return {
        JOE_ROSS_POINT1_FOUND: "P1",
        JOE_ROSS_POINT2_FOUND: "P2",
        JOE_ROSS_POINT3_FOUND: "P3",
        POINT2_BREAKOUT: "P2 BO",
        ROSS_HOOK_CREATED: "RH",
        TTE_BAR_1_CREATED: "TTE1",
        TTE_BAR_2_CREATED: "TTE2",
        TTE_BAR_3_CREATED: "TTE3",
        TTE_ENTRY_CANDIDATE: "TTE STOP",
        TTE_TRIGGERED: "ENTRY",
        TTE_EXPIRED: "EXPIRED",
        SETUP_INVALIDATED: "INVALID"
    }[type] || null;
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
        const shape = marker.type === "SELL"
            ? `<path class="${className}" d="M ${x} ${Number(y) + 6} l 6 -12 h -12 z"></path>`
            : `<path class="${className}" d="M ${x} ${Number(y) - 6} l 6 12 h -12 z"></path>`;
        return `${shape}${marker.label ? `<text class="marker-label" x="${Number(x) + 7}" y="${Number(y) - 7}">${marker.label}</text>` : ""}`;
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

function ohlcChart(bars, markers) {
    if (!bars || bars.length === 0) {
        return `<div class="empty-row">Nessun dato</div>`;
    }
    const width = 760;
    const height = 260;
    const pad = { left: 58, right: 18, top: 18, bottom: 34 };
    const minX = Math.min(...bars.map((bar) => bar.x));
    const maxX = Math.max(...bars.map((bar) => bar.x));
    const minY = Math.min(...bars.map((bar) => bar.low));
    const maxY = Math.max(...bars.map((bar) => bar.high));
    const spanX = Math.max(1, maxX - minX);
    const spanY = Math.max(1, maxY - minY);
    const lowY = minY - spanY * 0.08;
    const highY = maxY + spanY * 0.08;
    const scaleX = (value) => pad.left + (value - minX) / spanX * (width - pad.left - pad.right);
    const scaleY = (value) => height - pad.bottom - (value - lowY) / (highY - lowY) * (height - pad.top - pad.bottom);
    const tickWidth = Math.max(3, Math.min(9, (width - pad.left - pad.right) / Math.max(1, bars.length) * 0.35));
    const gridValues = [lowY, lowY + (highY - lowY) / 2, highY];
    const barSvg = bars.map((bar) => {
        const x = scaleX(bar.x);
        const className = bar.close >= bar.open ? "ohlc-up" : "ohlc-down";
        const high = scaleY(bar.high);
        const low = scaleY(bar.low);
        const open = scaleY(bar.open);
        const close = scaleY(bar.close);
        return `
            <g class="${className}">
                <line x1="${x}" y1="${high}" x2="${x}" y2="${low}"></line>
                <line x1="${x - tickWidth}" y1="${open}" x2="${x}" y2="${open}"></line>
                <line x1="${x}" y1="${close}" x2="${x + tickWidth}" y2="${close}"></line>
            </g>
        `;
    }).join("");
    const markerSvg = markers.map((marker) => {
        const x = scaleX(marker.x).toFixed(2);
        const y = scaleY(marker.y).toFixed(2);
        const label = marker.label || "";
        if (marker.type === "STOP") {
            return `<g>
                <circle class="stop-marker" cx="${x}" cy="${y}" r="4"></circle>
                <text class="marker-label stop-label" text-anchor="middle"
                    x="${x}" y="${Number(y) + 15}">${label}</text>
            </g>`;
        }
        const className = marker.type === "SELL" ? "sell-marker" : "buy-marker";
        const shape = marker.type === "SELL"
            ? `<path class="${className}" d="M ${x} ${Number(y) + 6} l 6 -12 h -12 z"></path>`
            : `<path class="${className}" d="M ${x} ${Number(y) - 6} l 6 12 h -12 z"></path>`;
        const labelY = marker.type === "SELL" ? Number(y) + 18 : Number(y) - 10;
        return `<g>${shape}
            ${label ? `<text class="marker-label" text-anchor="middle"
                x="${x}" y="${labelY}">${label}</text>` : ""}
        </g>`;
    }).join("");
    return `
        <svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" role="img">
            ${gridValues.map((value) => `
                <line class="grid-line" x1="${pad.left}" y1="${scaleY(value)}" x2="${width - pad.right}" y2="${scaleY(value)}"></line>
                <text x="8" y="${scaleY(value) + 4}">${formatNumber(value)}</text>
            `).join("")}
            ${barSvg}
            ${markerSvg}
            <text x="${pad.left}" y="${height - 10}">0</text>
            <text x="${width - 62}" y="${height - 10}">${bars.length - 1}</text>
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

function selectedHistorySymbol() {
    const profile = selectedProfile();
    return ($("profile-symbol").value || profile?.symbol || "")
        .trim()
        .toUpperCase();
}

function selectedHistoryInterval() {
    // Stooq CSV import is currently DAILY-only; profile timeframe is still used by IBKR flows.
    return "DAILY";
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

function simulationBars(profile) {
    const historySymbol = (state.history?.symbol || "").toUpperCase();
    const profileSymbol = (profile.symbol || "").toUpperCase();
    const historyTimeframe = (state.history?.timeframe || "").toUpperCase();
    const profileTimeframe = (profile.timeframe || "").toUpperCase();
    if (state.history?.bars?.length
        && historySymbol === profileSymbol
        && (!historyTimeframe || historyTimeframe === profileTimeframe)) {
        return state.history.bars.map((bar, index) => ({
            index,
            timestamp: bar.timestamp,
            label: bar.label,
            open: Number(bar.open),
            high: Number(bar.high),
            low: Number(bar.low),
            close: Number(bar.close),
            volume: Number(bar.volume || 0)
        }));
    }
    const prices = parsePrices();
    return prices.map((price, index) => ({
        index,
        timestamp: new Date(Date.now() - (prices.length - index) * 86400000).toISOString(),
        label: String(index),
        open: price,
        high: price,
        low: price,
        close: price,
        volume: 0
    }));
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
    state.strategyOptions = profileResponse.strategyOptions;
    state.stocks = profileResponse.stocks || stocksFromProfiles(profileResponse.profiles || []);
    state.profiles = flattenStocks(state.stocks);
    state.selectedStockId = state.stocks.some((stock) => stock.id === state.selectedStockId)
        ? state.selectedStockId
        : state.stocks[0]?.id;
    const stock = selectedStock();
    state.selectedProfileId = stock?.strategies.some((profile) => profile.id === state.selectedProfileId)
        ? state.selectedProfileId
        : stock?.strategies[0]?.id;
    state.status = status;
    state.signals = normalizeListResponse(signals);
    state.orders = normalizeListResponse(orders);
    state.positions = normalizeListResponse(positions);
    fillSelect($("profile-strategy"), state.strategyOptions);
    applyProfileToForm(selectedProfile());
    renderAll();
    await loadStoredHistoryForSelectedProfile({ silent: true })
        .catch(() => undefined);
}

async function refreshStatusOnly() {
    state.status = await api("/api/paper/status");
    renderStatus();
    renderMarketData();
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
    await persistProfiles();
    const profile = selectedProfile();
    state.history = await api("/api/workbench/historical/download", {
        method: "POST",
        body: JSON.stringify({
            symbol: profile.symbol,
            provider: "IBKR",
            timeframe: profile.timeframe,
            outputSize: profile.outputSize,
            maxPoints: profile.maxPoints,
            refresh
        })
    });
    $("simulate-prices").value = state.history.closePrices.join(", ");
    setResultTab("history");
    renderHistory();
    const source = state.history.source === "DATABASE" ? "database" : "IBKR";
    const action = refresh ? "Aggiornamento storico" : "Storico";
    const suffix = state.history.message ? `. ${state.history.message}` : "";
    toast(`${action}: ${state.history.bars.length} barre ${profile.symbol} da ${source}${suffix}`);
}

async function loadStoredHistoryForSelectedProfile({
    silent = true,
    force = false,
    switchTab = false,
    showEmpty = false
} = {}) {
    const symbol = selectedHistorySymbol();
    const interval = selectedHistoryInterval();
    if (!symbol || !interval) {
        return;
    }
    if (!force
            && state.history?.symbol === symbol
            && state.history?.timeframe === interval) {
        return;
    }

    const requestId = (state.historyRequestId || 0) + 1;
    state.historyRequestId = requestId;

    const status = await api(`/api/historical/${encodeURIComponent(symbol)}/status?interval=${interval}`);
    if (state.historyRequestId !== requestId) {
        return;
    }

    if (!status.bars || !status.firstDate || !status.lastDate) {
        if (showEmpty || state.history?.symbol !== symbol) {
            state.history = historicalBarsToHistory(
                symbol,
                interval,
                [],
                "Nessuna barra STOOQ salvata nel database"
            );
            $("simulate-prices").value = "";
            renderHistory();
        }
        if (!silent) {
            toast(`Nessuno storico STOOQ salvato per ${symbol}`, true, 5000);
        }
        return;
    }

    const requestedFrom = $("historical-from").value || status.firstDate;
    const requestedTo = $("historical-to").value || status.lastDate;
    const from = maxIsoDate(requestedFrom, status.firstDate);
    const to = minIsoDate(requestedTo, status.lastDate);
    if (from > to) {
        state.history = historicalBarsToHistory(
            symbol,
            interval,
            [],
            `Database STOOQ disponibile ${status.firstDate} - ${status.lastDate}, fuori dal range selezionato`
        );
        $("simulate-prices").value = "";
        renderHistory();
        if (switchTab) {
            setResultTab("history");
        }
        return;
    }

    const bars = await api(`/api/historical/${encodeURIComponent(symbol)}?from=${from}&to=${to}&interval=${interval}`);
    if (state.historyRequestId !== requestId) {
        return;
    }

    state.history = historicalBarsToHistory(
        symbol,
        interval,
        bars,
        `database STOOQ ${bars.length} barre (${from} - ${to})`
    );
    $("simulate-prices").value = state.history.closePrices.join(", ");
    renderHistory();
    if (switchTab) {
        setResultTab("history");
    }
    if (!silent) {
        toast(`Storico STOOQ caricato da DB: ${bars.length} barre ${symbol}`);
    }
}

async function importStooqReportIntoHistory(report) {
    if (!report.from || !report.to) {
        state.history = {
            provider: "STOOQ",
            symbol: report.symbol,
            timeframe: report.interval,
            outputSize: "range",
            bars: [],
            closePrices: [],
            source: "STOOQ",
            loadedAt: new Date().toISOString(),
            message: `${report.rowsInserted} nuove barre, ${report.duplicates} gia' presenti, ${report.rowsRejected} errori`
        };
        setResultTab("history");
        renderHistory();
        return;
    }

    const bars = await api(`/api/historical/${encodeURIComponent(report.symbol)}?from=${report.from}&to=${report.to}&interval=${report.interval}`);
    state.history = historicalBarsToHistory(
        report.symbol,
        report.interval,
        bars,
        `${report.rowsInserted} nuove barre, ${report.duplicates} gia' presenti, ${report.rowsRejected} errori`
    );
    $("simulate-prices").value = state.history.closePrices.join(", ");
    setResultTab("history");
    renderHistory();
}

async function importSelectedStooqFile(file, button) {
    saveSelectedFormInMemory();
    await persistProfiles();
    const profile = selectedProfile();
    const symbol = ($("profile-symbol").value || profile?.symbol || "").trim().toUpperCase();
    if (!symbol) {
        throw new Error("Inserisci il simbolo stock prima di importare il CSV STOOQ");
    }
    if (!file) {
        throw new Error("Seleziona un file CSV STOOQ");
    }
    toast(`Import CSV STOOQ in corso: ${file.name}`, false, 15000);
    if (button) {
        button.textContent = "Importo CSV...";
    }

    const formData = new FormData();
    formData.append("file", file);
    formData.append("symbol", symbol);
    formData.append("interval", "DAILY");
    formData.append("priceAdjustment", "RAW");

    const report = await apiForm("/api/historical/import/stooq/file", formData);
    await importStooqReportIntoHistory(report);
    toast(`STOOQ: ${report.rowsInserted} nuove barre, ${report.duplicates} gia' presenti`);
}

function bindStooqFileImport() {
    const button = $("import-stooq-file-button");
    const input = $("stooq-csv-file");
    if (!button || !input) {
        return;
    }

    button.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            input.value = "";
            input.click();
        }
    });
    input.addEventListener("change", async () => {
        const file = input.files[0];
        if (!file) {
            return;
        }

        const originalText = button.textContent;
        button.classList.add("disabled");
        button.setAttribute("aria-disabled", "true");
        try {
            await importSelectedStooqFile(file, button);
        } catch (error) {
            toast(error.message, true, 9000);
        } finally {
            input.value = "";
            button.classList.remove("disabled");
            button.removeAttribute("aria-disabled");
            button.textContent = originalText;
        }
    });
}

async function importStooqDirectory(button) {
    toast("Import directory STOOQ in corso", false, 15000);
    if (button) {
        button.textContent = "Importo directory...";
    }
    const report = await api("/api/historical/import/stooq/directory?interval=DAILY&priceAdjustment=RAW", {
        method: "POST"
    });
    toast(`STOOQ directory: ${report.filesImported} file, ${report.importedRows} nuove barre, ${report.filesFailed} errori`);
}

async function refreshHistoryFromProvider() {
    await downloadHistory(true);
}

async function simulateStrategy() {
    saveSelectedFormInMemory();
    const profile = selectedProfile();
    const bars = simulationBars(profile);
    state.simulation = await api("/api/workbench/strategy/simulate", {
        method: "POST",
        body: JSON.stringify({
            symbol: profile.symbol,
            provider: profile.provider,
            strategy: profile.strategy,
            tradeDirection: profile.tradeDirection || "LONG_SHORT",
            prices: bars.map((bar) => bar.close),
            bars,
            fastPeriod: profile.fastPeriod,
            slowPeriod: profile.slowPeriod,
            quantity: profile.quantity,
            initialCapital: profile.initialCapital,
            commissionPerOrder: profile.commissionPerOrder,
            slippagePercent: profile.slippagePercent,
            publishSignals: false,
            pivotStrength: profile.pivotStrength,
            minimumTick: profile.minimumTick,
            tteMaxBars: profile.tteMaxBars,
            entryBufferTicks: profile.entryBufferTicks,
            stopBufferTicks: profile.stopBufferTicks,
            minimumRoomEnabled: profile.minimumRoomEnabled,
            minimumRoomValue: profile.minimumRoomValue,
            allowDoubleTriple: profile.allowDoubleTriple,
            doubleTripleToleranceTicks: profile.doubleTripleToleranceTicks,
            rejectGapOverEntry: profile.rejectGapOverEntry
        })
    });
    setResultTab("simulation");
    renderSimulation();
    toast("Simulazione completata");
}

function tradeDirectionLabel(value) {
    return {
        LONG_ONLY: "Solo long",
        SHORT_ONLY: "Solo short",
        LONG_SHORT: "Long e short"
    }[value || "LONG_SHORT"] || "Long e short";
}

async function downloadHistoryAndSimulate() {
    await downloadHistory();
    await simulateStrategy();
}

function exportBacktestExcel() {
    const backtestId = state.simulation?.backtestId;
    if (!backtestId) {
        throw new Error("Esegui una simulazione prima di esportare Excel");
    }
    window.location.href = `/api/backtest/${encodeURIComponent(backtestId)}/export/excel`;
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

function historyTabActive() {
    return $("result-history")?.classList.contains("active");
}

function reloadStoredHistoryIfVisible() {
    if (!historyTabActive()) {
        return;
    }
    loadStoredHistoryForSelectedProfile({
        silent: true,
        force: true,
        showEmpty: true
    }).catch((error) => toast(error.message, true, 9000));
}

function bind(id, handler) {
    const button = $(id);
    if (!button) {
        return;
    }
    button.addEventListener("click", async () => {
        const originalText = button.textContent;
        button.disabled = true;
        try {
            await handler(button);
        } catch (error) {
            toast(error.message, true, 9000);
        } finally {
            button.disabled = false;
            button.textContent = originalText;
        }
    });
}

function initEvents() {
    bind("refresh-button", refreshAll);
    bind("add-stock-button", addStock);
    bind("add-strategy-button", addStrategy);
    bind("save-profiles-button", saveProfiles);
    bind("delete-stock-button", deleteStock);
    bind("delete-strategy-button", deleteStrategy);
    $("stock-select")?.addEventListener("change", (event) => {
        selectStockById(event.target.value)
            .catch((error) => toast(error.message, true, 9000));
    });
    $("strategy-select")?.addEventListener("change", (event) => {
        selectProfileById(event.target.value)
            .catch((error) => toast(error.message, true, 9000));
    });
    $("profile-strategy")?.addEventListener("change", toggleStrategyFields);
    bindStooqFileImport();
    bind("import-stooq-directory-button", importStooqDirectory);
    bind("download-history-button", downloadHistory);
    bind("update-history-button", refreshHistoryFromProvider);
    bind("download-simulate-button", downloadHistoryAndSimulate);
    bind("simulate-button", simulateStrategy);
    bind("export-excel-button", exportBacktestExcel);
    bind("publish-signal-button", publishManualSignal);
    bind("activate-kill-button", activateKillSwitch);
    bind("deactivate-kill-button", deactivateKillSwitch);
    document.querySelectorAll("[data-result-tab]").forEach((button) => {
        button.addEventListener("click", () => {
            const tab = button.dataset.resultTab;
            setResultTab(tab);
            if (tab === "history") {
                loadStoredHistoryForSelectedProfile({
                    silent: false,
                    force: true,
                    showEmpty: true
                }).catch((error) => toast(error.message, true, 9000));
            }
        });
    });
    ["historical-from", "historical-to", "profile-timeframe", "profile-symbol"]
        .forEach((id) => $(id)?.addEventListener("change", reloadStoredHistoryIfVisible));
}

initEvents();
refreshAll().catch((error) => toast(error.message, true));
window.setInterval(() => refreshStatusOnly().catch(() => undefined), 5000);
