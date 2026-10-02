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
    simulation: null,
    chartWindow: { start: 0, size: null },
    selectedTradeId: null,
    chartFilters: {
        patterns: true,
        tte: true,
        entries: true,
        stops: true,
        takeProfits: true,
        volume: true,
        indicators: true,
        strategyDebug: false
    },
    activeStrategyTab: "general",
    dirty: false,
    saving: false,
    savedStocksSnapshot: null,
    pendingTransition: null
};

const $ = (id) => document.getElementById(id);
let tradingChartController = null;

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
        initialStopMode: "TTE_BAR",
        minimumRoomEnabled: false,
        minimumRoomType: "R",
        minimumRoomValue: 0.5,
        allowDoubleTriple: false,
        doubleTripleToleranceTicks: 1,
        rejectGapOverEntry: false,
        fallbackToPoint2Breakout: false,
        bollingerPoint1FilterEnabled: true,
        bollingerPeriod: 20,
        bollingerStandardDeviation: 2.0,
        bollingerSource: "CLOSE",
        point1PriceMode: "EXTREME",
        point2TargetSizingEnabled: true,
        point2TargetProfitPercent: 2.0,
        minimumPoint2Distance: 0,
        tp1PositionPercent: 60,
        tp2PositionPercent: 20,
        tp3PositionPercent: 20
    };
}

const STRATEGY_META = {
    MANUAL_ONLY: {
        label: "Solo segnali manuali",
        family: "manual",
        accent: "#475569",
        soft: "#f1f5f9",
        description: "Non genera segnali automatici: consente esclusivamente la pubblicazione manuale."
    },
    MOVING_AVERAGE_CROSSOVER: {
        label: "Incrocio medie mobili",
        family: "moving-average",
        accent: "#2563eb",
        soft: "#eff6ff",
        description: "Genera segnali quando la media veloce incrocia la media lenta. È indicata per seguire movimenti direzionali."
    },
    JOE_ROSS_123_TTE: {
        label: "Joe Ross 1-2-3 / TTE",
        family: "joe-ross",
        accent: "#0f766e",
        soft: "#ecfdf5",
        description: "Cerca la Trader’s Trick Entry durante la correzione che forma il Point 3, prima del normale breakout del Point 2."
    },
    JOE_ROSS_123_RH_TTE: {
        label: "Joe Ross 1-2-3 / Ross Hook / TTE (legacy)",
        family: "joe-ross",
        accent: "#64748b",
        soft: "#f1f5f9",
        description: "Profilo legacy mantenuto esclusivamente per compatibilità con configurazioni già salvate."
    },
    BREAKOUT: {
        label: "Breakout",
        family: "breakout",
        accent: "#c2410c",
        soft: "#fff7ed",
        description: "Cerca la rottura di livelli tecnici con conferma del movimento."
    },
    MEAN_REVERSION: {
        label: "Mean reversion",
        family: "mean-reversion",
        accent: "#7c3aed",
        soft: "#f5f3ff",
        description: "Cerca il ritorno del prezzo verso un valore medio dopo un movimento esteso."
    }
};

const PROFILE_FIELD_IDS = new Set([
    "profile-name", "profile-symbol", "stock-security-type", "stock-exchange",
    "stock-primary-exchange", "stock-currency", "profile-fast", "profile-slow",
    "profile-pivot-strength", "profile-minimum-tick", "profile-tte-max-bars",
    "profile-bollinger-enabled", "profile-bollinger-period",
    "profile-bollinger-standard-deviation", "profile-point1-price-mode",
    "profile-point2-target-sizing-enabled", "profile-point2-target-profit-percent",
    "profile-minimum-point2-distance", "profile-tp1-position-percent",
    "profile-tp2-position-percent", "profile-tp3-position-percent",
    "profile-entry-buffer-ticks", "profile-stop-buffer-ticks",
    "profile-initial-stop-mode", "profile-fallback-point2",
    "profile-minimum-room-enabled", "profile-minimum-room-type",
    "profile-minimum-room-value", "profile-allow-double-triple",
    "profile-double-triple-tolerance", "profile-reject-gap", "profile-quantity",
    "profile-capital", "profile-commission", "profile-slippage", "profile-timeframe",
    "profile-output-size", "profile-max-points", "profile-trade-direction"
]);

function strategyMeta(strategy) {
    if (STRATEGY_META[strategy]) {
        return STRATEGY_META[strategy];
    }
    const normalized = String(strategy || "").toUpperCase();
    if (normalized.includes("BREAKOUT")) {
        return STRATEGY_META.BREAKOUT;
    }
    if (normalized.includes("MEAN") || normalized.includes("REVERSION")) {
        return STRATEGY_META.MEAN_REVERSION;
    }
    return {
        label: String(strategy || "Strategia non selezionata").replaceAll("_", " "),
        family: "moving-average",
        accent: "#2563eb",
        soft: "#eff6ff",
        description: "Configura i parametri supportati dall’algoritmo selezionato."
    };
}

function fillStrategyTypeSelect(select, values) {
    if (!select) {
        return;
    }
    select.innerHTML = "";
    values.forEach((value) => appendOption(select, value, strategyMeta(value).label));
    if (select.id === "profile-strategy") {
        appendOption(select, "PATTERN_V1", "PATTERN V1 · Solo backtest");
    }
}

function cloneValue(value) {
    return typeof structuredClone === "function"
        ? structuredClone(value)
        : JSON.parse(JSON.stringify(value));
}

function globalStrategyEnabled() {
    return state.config?.strategy?.enabled ?? true;
}

function captureSavedState() {
    state.savedStocksSnapshot = JSON.stringify(state.stocks);
}

function profileNameExists(name, excludedId = null) {
    const normalized = String(name || "").trim().toLocaleLowerCase("it-IT");
    return (selectedStock()?.strategies || []).some((profile) =>
        profile.id !== excludedId
        && String(profile.name || "").trim().toLocaleLowerCase("it-IT") === normalized);
}

function uniqueProfileName(baseName) {
    const base = String(baseName || "Nuova strategia").trim() || "Nuova strategia";
    let candidate = base;
    let suffix = 2;
    while (profileNameExists(candidate)) {
        candidate = `${base} ${suffix}`;
        suffix += 1;
    }
    return candidate;
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
    return option;
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
        appendOption(select, "__PATTERN_V1_BACKTEST__", "PATTERN V1 · Solo backtest");
        select.disabled = false;
        return;
    }
    strategies.forEach((item) => appendOption(
        select,
        item.id,
        `${item.name} · ${strategyMeta(item.strategy).label} · ${globalStrategyEnabled() ? "Attiva" : "Disattiva"}`));
    appendOption(select, "__PATTERN_V1_BACKTEST__", "PATTERN V1 · Solo backtest");
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
    const duplicateStrategyButton = $("duplicate-strategy-button");
    if (duplicateStrategyButton) {
        duplicateStrategyButton.disabled = !selectedProfile();
    }
}

function renderProfiles() {
    renderStockSelect();
    renderStrategySelect();
    renderStrategyProfileList();
    renderStrategyWorkspace();
    renderProfileControls();
}

function renderStrategyProfileList() {
    const container = $("strategy-profile-list");
    if (!container) {
        return;
    }
    const strategies = selectedStock()?.strategies || [];
    container.replaceChildren();
    strategies.forEach((profile) => {
        const meta = strategyMeta(profile.strategy);
        const button = document.createElement("button");
        button.type = "button";
        button.className = `profile-card${profile.id === state.selectedProfileId ? " selected" : ""}`;
        button.dataset.profileId = profile.id;
        button.setAttribute("role", "listitem");
        button.setAttribute("aria-current", profile.id === state.selectedProfileId ? "true" : "false");
        button.style.setProperty("--profile-accent", meta.accent);
        button.style.setProperty("--profile-soft", meta.soft);

        const name = document.createElement("strong");
        name.textContent = profile.name;
        const type = document.createElement("span");
        type.textContent = meta.label;
        const details = document.createElement("span");
        details.className = "profile-card-meta";
        const status = document.createElement("span");
        status.className = `profile-card-status${globalStrategyEnabled() ? "" : " inactive"}`;
        status.textContent = globalStrategyEnabled() ? "Attiva (globale)" : "Disattiva (globale)";
        const symbol = document.createElement("span");
        symbol.textContent = `Simbolo ${profile.symbol || selectedStock()?.symbol || "-"}`;
        details.append(status, symbol);
        button.append(name, type, details);
        button.addEventListener("click", () => requestTransition(() => selectProfileById(profile.id)));
        container.appendChild(button);
    });
    if ($("profile-count")) {
        $("profile-count").textContent = String(strategies.length);
    }
}

function renderStrategyWorkspace() {
    const profile = selectedProfile();
    const strategy = $("profile-strategy")?.value || profile?.strategy;
    const meta = strategyMeta(strategy);
    const workspace = $("strategy-workspace");
    if (workspace) {
        workspace.dataset.strategyFamily = meta.family;
        workspace.classList.add("strategy-ready");
    }
    if ($("selected-profile-name")) {
        $("selected-profile-name").textContent = $("profile-name")?.value.trim() || profile?.name || "Nessun profilo selezionato";
    }
    if ($("strategy-type-badge")) {
        $("strategy-type-badge").textContent = meta.label;
    }
    if ($("strategy-symbol-badge")) {
        $("strategy-symbol-badge").textContent = `Simbolo ${$("profile-symbol")?.value.trim().toUpperCase() || profile?.symbol || "-"}`;
    }
    const activeBadge = $("strategy-active-badge");
    if (activeBadge) {
        activeBadge.textContent = globalStrategyEnabled() ? "Attiva (stato globale)" : "Disattiva (stato globale)";
        activeBadge.classList.toggle("inactive", !globalStrategyEnabled());
    }
    setValue("profile-active-status", String(globalStrategyEnabled()));
    if ($("strategy-description")) {
        $("strategy-description").textContent = meta.description;
    }
    renderGlobalConfigSummaries();
    updateStrategyFieldVisibility();
}

function renderGlobalConfigSummaries() {
    const risk = state.config?.risk || {};
    const riskContainer = $("global-risk-summary");
    if (riskContainer) {
        riskContainer.innerHTML = `
            <h3>Limiti di rischio globali</h3>
            <p class="field-note">Sono controlli condivisi da tutte le strategie e non vengono salvati in questo profilo.</p>
            <div class="global-config-grid">
                <div class="global-config-value"><span>Rischio per trade</span><strong>${formatNumber(risk.riskPerTradePercent)}%</strong></div>
                <div class="global-config-value"><span>Capitale trading</span><strong>${formatNumber(risk.tradingCapital)}</strong></div>
                <div class="global-config-value"><span>Valore max ordine</span><strong>${formatNumber(risk.maxOrderValue)}</strong></div>
                <div class="global-config-value"><span>Quantità max ordine</span><strong>${formatNumber(risk.maxQuantityPerOrder)}</strong></div>
            </div>
            <a class="link-button" href="/config.html">Apri configurazione rischio</a>`;
    }

    const takeProfit = state.config?.takeProfit || {};
    const targetContainer = $("global-target-summary");
    if (targetContainer) {
        const targets = [takeProfit.target1, takeProfit.target2, takeProfit.target3].filter(Boolean);
        const total = targets.reduce((sum, target) => sum + Number(target.positionPercent || 0), 0);
        targetContainer.innerHTML = `
            <h3>Take profit ${takeProfit.enabled ? "attivo" : "disattivo"}</h3>
            <p class="field-note">I target sono globali. La somma delle chiusure configurate è ${formatNumber(total)}%${Math.abs(total - 100) < 0.0001 ? "." : " e richiede verifica."}</p>
            <div class="global-config-grid">
                ${targets.map((target, index) => `<div class="global-config-value"><span>TP${index + 1}</span><strong>+${formatNumber(target.profitPercent)}% · chiudi ${formatNumber(target.positionPercent)}%</strong></div>`).join("")}
                <div class="global-config-value"><span>Stop protettivo</span><strong>Gestito dal motore ordini</strong></div>
            </div>
            <a class="link-button" href="/config.html">Apri configurazione target</a>`;
    }
}

const STRATEGY_TAB_COPY = {
    general: ["Impostazioni generali", "Identità, strumento associato e parametri comuni del profilo."],
    pattern: ["Pattern e segnali", "Parametri usati dall’algoritmo per riconoscere il setup."],
    entry: ["Entrata TTE", "Filtri e distanze specifici per un ingresso Joe Ross valido."],
    risk: ["Stop e rischio", "Dimensionamento, costi di simulazione, stop specifici e limiti globali."],
    exits: ["Uscite e target", "Riepilogo dei target supportati dalla configurazione globale."]
};

function isJoeRossStrategy(strategy) {
    return strategy === "JOE_ROSS_123_TTE" || strategy === "JOE_ROSS_123_RH_TTE";
}

function setStrategyTab(tab) {
    const joeRoss = isJoeRossStrategy($("profile-strategy")?.value);
    const manualOnly = $("profile-strategy")?.value === "MANUAL_ONLY";
    state.activeStrategyTab = (tab === "entry" && !joeRoss) || (tab === "pattern" && manualOnly)
        ? "general"
        : tab;
    updateStrategyFieldVisibility();
}

function updateStrategyFieldVisibility() {
    const strategy = $("profile-strategy")?.value;
    const joeRoss = isJoeRossStrategy(strategy);
    const movingAverage = strategy === "MOVING_AVERAGE_CROSSOVER";
    const manualOnly = strategy === "MANUAL_ONLY";
    if ((!joeRoss && state.activeStrategyTab === "entry")
        || (manualOnly && state.activeStrategyTab === "pattern")) {
        state.activeStrategyTab = "general";
    }
    document.querySelectorAll("[data-strategy-section]").forEach((field) => {
        const correctTab = field.dataset.strategySection === state.activeStrategyTab;
        const joeOnly = field.classList.contains("joe-ross-strategy-field");
        const movingAverageOnly = field.classList.contains("ma-strategy-field");
        const legacyJoeOnly = field.classList.contains("legacy-joe-ross-field");
        const point3TteOnly = field.classList.contains("point3-tte-field");
        const variantMatches = (!legacyJoeOnly || strategy === "JOE_ROSS_123_RH_TTE")
            && (!point3TteOnly || strategy === "JOE_ROSS_123_TTE");
        const correctStrategy = ((!joeOnly && !movingAverageOnly)
            || (joeRoss && joeOnly)
            || (movingAverage && movingAverageOnly)) && variantMatches;
        field.hidden = !(correctTab && correctStrategy);
    });
    document.querySelectorAll("[data-strategy-tab]").forEach((button) => {
        const tab = button.dataset.strategyTab;
        button.hidden = (tab === "entry" && !joeRoss) || (tab === "pattern" && manualOnly);
        const active = button.dataset.strategyTab === state.activeStrategyTab;
        button.classList.toggle("active", active);
        button.setAttribute("aria-selected", String(active));
        button.tabIndex = active ? 0 : -1;
    });
    const copy = STRATEGY_TAB_COPY[state.activeStrategyTab] || STRATEGY_TAB_COPY.general;
    if ($("strategy-tab-intro")) {
        $("strategy-tab-intro").innerHTML = `<h3>${copy[0]}</h3><p>${copy[1]}</p>`;
    }
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
    const strategySelect = $("profile-strategy");
    if (profile.strategy === "JOE_ROSS_123_RH_TTE"
        && !Array.from(strategySelect.options).some((option) => option.value === profile.strategy)) {
        const legacy = appendOption(strategySelect, profile.strategy,
            strategyMeta(profile.strategy).label);
        legacy.disabled = true;
    }
    setValue("profile-strategy", profile.strategy);
    setValue("profile-trade-direction", profile.tradeDirection || "LONG_SHORT");
    setValue("profile-fast", profile.fastPeriod);
    setValue("profile-slow", profile.slowPeriod);
    setValue("profile-pivot-strength", profile.pivotStrength ?? 2);
    setValue("profile-minimum-tick", profile.minimumTick ?? 0.01);
    setValue("profile-tte-max-bars", profile.tteMaxBars ?? 3);
    setValue("profile-entry-buffer-ticks", profile.entryBufferTicks ?? 1);
    setValue("profile-stop-buffer-ticks", profile.stopBufferTicks ?? 1);
    setValue("profile-initial-stop-mode", profile.initialStopMode || "TTE_BAR");
    setValue("profile-minimum-room-enabled", String(profile.minimumRoomEnabled ?? false));
    setValue("profile-minimum-room-type", profile.minimumRoomType || "R");
    setValue("profile-minimum-room-value", profile.minimumRoomValue ?? 0.5);
    setValue("profile-allow-double-triple", String(profile.allowDoubleTriple ?? false));
    setValue("profile-double-triple-tolerance", profile.doubleTripleToleranceTicks ?? 1);
    setValue("profile-reject-gap", String(profile.rejectGapOverEntry ?? false));
    setValue("profile-fallback-point2", String(profile.fallbackToPoint2Breakout ?? false));
    setValue("profile-bollinger-enabled", String(profile.bollingerPoint1FilterEnabled ?? true));
    setValue("profile-bollinger-period", profile.bollingerPeriod ?? 20);
    setValue("profile-bollinger-standard-deviation",
        profile.bollingerStandardDeviation ?? 2.0);
    setValue("profile-point1-price-mode", profile.point1PriceMode || "EXTREME");
    setValue("profile-point2-target-sizing-enabled",
        String(profile.point2TargetSizingEnabled ?? true));
    setValue("profile-point2-target-profit-percent",
        profile.point2TargetProfitPercent ?? 2.0);
    setValue("profile-minimum-point2-distance", profile.minimumPoint2Distance ?? 0);
    setValue("profile-tp1-position-percent", profile.tp1PositionPercent ?? 60);
    setValue("profile-tp2-position-percent", profile.tp2PositionPercent ?? 20);
    setValue("profile-tp3-position-percent", profile.tp3PositionPercent ?? 20);
    setValue("profile-active-status", String(globalStrategyEnabled()));
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
    renderStrategyWorkspace();
    validateProfileForm();
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
        "profile-bollinger-period",
        "profile-bollinger-standard-deviation",
        "profile-point2-target-profit-percent",
        "profile-minimum-point2-distance",
        "profile-tp1-position-percent",
        "profile-tp2-position-percent",
        "profile-tp3-position-percent",
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
    setValue("profile-initial-stop-mode", "TTE_BAR");
    setValue("profile-fallback-point2", "false");
    setValue("profile-bollinger-enabled", "true");
    setValue("profile-point1-price-mode", "EXTREME");
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
        initialStopMode: $("profile-initial-stop-mode").value,
        minimumRoomEnabled: $("profile-minimum-room-enabled").value === "true",
        minimumRoomType: $("profile-minimum-room-type").value,
        minimumRoomValue: num("profile-minimum-room-value"),
        allowDoubleTriple: $("profile-allow-double-triple").value === "true",
        doubleTripleToleranceTicks: num("profile-double-triple-tolerance"),
        rejectGapOverEntry: $("profile-reject-gap").value === "true",
        fallbackToPoint2Breakout: $("profile-fallback-point2").value === "true",
        bollingerPoint1FilterEnabled: $("profile-bollinger-enabled").value === "true",
        bollingerPeriod: num("profile-bollinger-period"),
        bollingerStandardDeviation: num("profile-bollinger-standard-deviation"),
        bollingerSource: "CLOSE",
        point1PriceMode: $("profile-point1-price-mode").value,
        point2TargetSizingEnabled:
            $("profile-point2-target-sizing-enabled").value === "true",
        point2TargetProfitPercent: num("profile-point2-target-profit-percent"),
        minimumPoint2Distance: num("profile-minimum-point2-distance"),
        tp1PositionPercent: num("profile-tp1-position-percent"),
        tp2PositionPercent: num("profile-tp2-position-percent"),
        tp3PositionPercent: num("profile-tp3-position-percent")
    };
}

function setFieldError(id, message) {
    const field = $(id);
    const error = $(`${id}-error`);
    field?.classList.toggle("field-invalid", Boolean(message));
    field?.setAttribute("aria-invalid", String(Boolean(message)));
    if (error) {
        error.textContent = message || "";
    }
}

function validateProfileForm({ focus = false } = {}) {
    if (!selectedProfile()) {
        return false;
    }
    const errors = new Map();
    const name = $("profile-name").value.trim();
    const symbol = $("profile-symbol").value.trim();
    const strategy = $("profile-strategy").value;
    const fast = num("profile-fast");
    const slow = num("profile-slow");

    if (!name) {
        errors.set("profile-name", "Inserisci il nome del profilo.");
    } else if (profileNameExists(name, selectedProfile()?.id)) {
        errors.set("profile-name", "Esiste già un profilo con questo nome per lo stock selezionato.");
    }
    if (!symbol) {
        errors.set("profile-symbol", "Inserisci il simbolo dello strumento.");
    }
    if (!Number.isInteger(fast) || fast <= 0) {
        errors.set("profile-fast", "Inserisci un numero intero maggiore di zero.");
    }
    if (!Number.isInteger(slow) || slow <= fast) {
        errors.set("profile-slow", "La media lenta deve essere un intero maggiore della media veloce.");
    }
    if (!Number.isInteger(num("profile-quantity")) || num("profile-quantity") < 1) {
        errors.set("profile-quantity", "La quantità deve essere un intero di almeno 1.");
    }
    if (!(num("profile-capital") >= 1)) {
        errors.set("profile-capital", "Il capitale iniziale deve essere almeno 1.");
    }
    if (!(num("profile-commission") >= 0)) {
        errors.set("profile-commission", "La commissione non può essere negativa.");
    }
    const slippage = num("profile-slippage");
    if (!(slippage >= 0 && slippage <= 100)) {
        errors.set("profile-slippage", "Inserisci una percentuale compresa tra 0 e 100.");
    }
    const maxPoints = num("profile-max-points");
    if (!Number.isInteger(maxPoints) || maxPoints < 1 || maxPoints > 5000) {
        errors.set("profile-max-points", "Inserisci un numero intero da 1 a 5.000.");
    }

    if (isJoeRossStrategy(strategy)) {
        const positiveIntegers = [
            ["profile-pivot-strength", "La forza del pivot deve essere un intero maggiore di zero."],
            ["profile-tte-max-bars", "Il numero massimo di barre deve essere un intero da 1 a 3."]
        ];
        positiveIntegers.forEach(([id, message]) => {
            if (!Number.isInteger(num(id)) || num(id) <= 0
                || (id === "profile-tte-max-bars" && num(id) > 3)) {
                errors.set(id, message);
            }
        });
        if (!(num("profile-minimum-tick") > 0)) {
            errors.set("profile-minimum-tick", "Il tick minimo deve essere maggiore di zero.");
        }
        ["profile-entry-buffer-ticks", "profile-stop-buffer-ticks", "profile-double-triple-tolerance"]
            .forEach((id) => {
                if (!Number.isInteger(num(id)) || num(id) < 0) {
                    errors.set(id, "Inserisci un numero intero non negativo.");
                }
            });
        if (!(num("profile-minimum-room-value") >= 0)) {
            errors.set("profile-minimum-room-value", "Lo spazio minimo non può essere negativo.");
        }
        if (strategy === "JOE_ROSS_123_TTE") {
            if (!Number.isInteger(num("profile-bollinger-period"))
                || num("profile-bollinger-period") < 2) {
                errors.set("profile-bollinger-period",
                    "Il periodo Bollinger deve essere un intero di almeno 2.");
            }
            if (!(num("profile-bollinger-standard-deviation") >= 0)) {
                errors.set("profile-bollinger-standard-deviation",
                    "La deviazione standard non può essere negativa.");
            }
            if (!(num("profile-point2-target-profit-percent") > 0)) {
                errors.set("profile-point2-target-profit-percent",
                    "La percentuale target al Point 2 deve essere maggiore di zero.");
            }
            if (!(num("profile-minimum-point2-distance") >= 0)) {
                errors.set("profile-minimum-point2-distance",
                    "La distanza minima non può essere negativa.");
            }
            const tpTotal = num("profile-tp1-position-percent")
                + num("profile-tp2-position-percent")
                + num("profile-tp3-position-percent");
            ["profile-tp1-position-percent", "profile-tp2-position-percent",
                "profile-tp3-position-percent"].forEach((id) => {
                if (!(num(id) >= 0 && num(id) <= 100)) {
                    errors.set(id, "Inserisci una percentuale tra 0 e 100.");
                }
            });
            if (Math.abs(tpTotal - 100) > 0.000001) {
                errors.set("profile-tp3-position-percent",
                    "TP1 + TP2 + TP3 devono sommare a 100%.");
            }
        }
    }

    [
        "profile-name", "profile-symbol", "profile-fast", "profile-slow", "profile-quantity",
        "profile-capital", "profile-commission", "profile-slippage", "profile-max-points",
        "profile-pivot-strength", "profile-tte-max-bars", "profile-minimum-tick",
        "profile-entry-buffer-ticks", "profile-stop-buffer-ticks",
        "profile-bollinger-period", "profile-bollinger-standard-deviation",
        "profile-point2-target-profit-percent", "profile-minimum-point2-distance",
        "profile-tp1-position-percent", "profile-tp2-position-percent",
        "profile-tp3-position-percent",
        "profile-double-triple-tolerance", "profile-minimum-room-value"
    ].forEach((id) => setFieldError(id, errors.get(id)));

    const valid = errors.size === 0;
    updateSaveControls(valid);
    if (!valid && focus) {
        const firstInvalid = $(errors.keys().next().value);
        const group = firstInvalid?.closest("[data-strategy-section]");
        if (group?.dataset.strategySection) {
            setStrategyTab(group.dataset.strategySection);
        }
        firstInvalid?.focus();
    }
    return valid;
}

function updateSaveControls(valid = true) {
    const saveButton = $("save-profiles-button");
    const cancelButton = $("cancel-profile-changes-button");
    if (saveButton) {
        saveButton.disabled = !state.dirty || state.saving || !valid;
    }
    if (cancelButton) {
        cancelButton.disabled = !state.dirty || state.saving;
    }
}

function setDirty(dirty) {
    state.dirty = dirty;
    $("strategy-workspace")?.classList.toggle("has-unsaved-changes", dirty);
    const badge = $("save-state-badge");
    if (badge && !state.saving) {
        badge.className = `save-state ${dirty ? "dirty" : "saved"}`;
        badge.textContent = dirty ? "Modifiche non salvate" : "Salvata";
    }
    if ($("save-bar-title")) {
        $("save-bar-title").textContent = dirty
            ? "Il profilo contiene modifiche non salvate"
            : "Nessuna modifica da salvare";
    }
    if (!dirty && $("save-error-message")) {
        $("save-error-message").textContent = "";
    }
    validateProfileForm({ focus: false });
}

function markDirtyFromCurrentState() {
    setDirty(!state.savedStocksSnapshot || JSON.stringify(state.stocks) !== state.savedStocksSnapshot);
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
    if (!state.dirty) {
        return true;
    }
    if (!validateProfileForm({ focus: true })) {
        toast("Correggi i campi evidenziati prima di salvare", true, 7000);
        return false;
    }
    saveSelectedFormInMemory();
    state.saving = true;
    const badge = $("save-state-badge");
    const saveButton = $("save-profiles-button");
    if (badge) {
        badge.className = "save-state saving";
        badge.textContent = "Salvataggio…";
    }
    if (saveButton) {
        saveButton.disabled = true;
        saveButton.textContent = "Salvataggio…";
    }
    if ($("save-error-message")) {
        $("save-error-message").textContent = "";
    }
    try {
        await persistProfiles();
        captureSavedState();
        state.dirty = false;
        $("strategy-workspace")?.classList.remove("has-unsaved-changes");
        if (badge) {
            badge.className = "save-state saved";
            badge.textContent = "Salvata";
        }
        if ($("save-bar-title")) {
            $("save-bar-title").textContent = "Modifiche salvate correttamente";
        }
        toast("Strategie salvate correttamente");
        return true;
    } catch (error) {
        if (badge) {
            badge.className = "save-state error";
            badge.textContent = "Errore di salvataggio";
        }
        if ($("save-error-message")) {
            $("save-error-message").textContent = error.message;
        }
        toast(error.message, true, 9000);
        return false;
    } finally {
        state.saving = false;
        if (saveButton) {
            saveButton.textContent = "Salva modifiche";
        }
        validateProfileForm();
    }
}

async function persistProfiles() {
    const response = await api("/api/workbench/action-profiles", {
        method: "PUT",
        body: JSON.stringify({ stocks: state.stocks })
    });
    state.stocks = response.stocks || stocksFromProfiles(response.profiles || []);
    state.profiles = response.profiles || flattenStocks(state.stocks);
    state.selectedStockId = selectedStock()?.id || state.stocks[0]?.id || null;
    state.selectedProfileId = selectedProfile()?.id || selectedStock()?.strategies[0]?.id || null;
    renderProfiles();
    applyProfileToForm(selectedProfile());
}

function discardProfileChanges() {
    if (!state.savedStocksSnapshot) {
        return;
    }
    const selectedStockId = state.selectedStockId;
    const selectedProfileId = state.selectedProfileId;
    state.stocks = JSON.parse(state.savedStocksSnapshot);
    state.profiles = flattenStocks(state.stocks);
    state.selectedStockId = state.stocks.some((stock) => stock.id === selectedStockId)
        ? selectedStockId
        : state.stocks[0]?.id || null;
    const stock = selectedStock();
    state.selectedProfileId = (stock?.strategies || []).some((profile) => profile.id === selectedProfileId)
        ? selectedProfileId
        : stock?.strategies?.[0]?.id || null;
    applyProfileToForm(selectedProfile());
    renderProfiles();
    setDirty(false);
    toast("Modifiche annullate");
}

function requestTransition(action) {
    if (!state.dirty) {
        return Promise.resolve(action());
    }
    state.pendingTransition = action;
    const dialog = $("unsaved-changes-dialog");
    if (dialog?.showModal) {
        dialog.showModal();
    } else {
        const save = window.confirm("Ci sono modifiche non salvate. Premi OK per salvarle prima di continuare, oppure Annulla per restare nella pagina.");
        if (save) {
            return saveProfiles().then((saved) => saved && action());
        }
    }
    return Promise.resolve(false);
}

async function continuePendingTransition(mode) {
    const action = state.pendingTransition;
    if (!action) {
        $("unsaved-changes-dialog")?.close();
        return;
    }
    if (mode === "stay") {
        state.pendingTransition = null;
        $("unsaved-changes-dialog")?.close();
        renderProfiles();
        return;
    }
    if (mode === "save" && !(await saveProfiles())) {
        return;
    }
    if (mode === "discard") {
        discardProfileChanges();
    }
    state.pendingTransition = null;
    $("unsaved-changes-dialog")?.close();
    await action();
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
    setDirty(true);
}

function addStrategy() {
    const stock = selectedStock();
    const name = uniqueProfileName(`${stock?.symbol || "SPY"} nuova strategia`);
    setValue("new-strategy-name", name);
    fillStrategyTypeSelect($("new-strategy-type"), state.strategyOptions);
    const suggestedType = selectedProfile()?.strategy
        || (state.strategyOptions.includes("MOVING_AVERAGE_CROSSOVER")
            ? "MOVING_AVERAGE_CROSSOVER"
            : state.strategyOptions[0]);
    setValue("new-strategy-type", suggestedType || "MOVING_AVERAGE_CROSSOVER");
    if ($("new-strategy-name-error")) {
        $("new-strategy-name-error").textContent = "";
    }
    $("new-strategy-dialog")?.showModal();
    window.setTimeout(() => $("new-strategy-name")?.focus(), 0);
}

function createStrategy(name, strategy) {
    let stock = selectedStock();
    if (!stock) {
        const created = defaultStock("SPY");
        state.stocks = [created];
        state.selectedStockId = created.id;
        stock = created;
    }
    const profile = {
        ...defaultStrategy(stock.symbol, stock.strategies.length + 1),
        id: crypto.randomUUID(),
        name: name.trim(),
        strategy
    };
    state.stocks = state.stocks.map((item) =>
        item.id === stock.id
            ? { ...item, strategies: [...item.strategies, profile] }
            : item);
    state.profiles = flattenStocks(state.stocks);
    state.selectedProfileId = profile.id;
    state.activeStrategyTab = "general";
    applyProfileToForm(profile);
    renderProfiles();
    setDirty(true);
    $("new-strategy-dialog")?.close();
    toast("Nuova strategia pronta: salva le modifiche per confermarla");
}

function duplicateStrategy() {
    saveSelectedFormInMemory();
    const stock = selectedStock();
    const source = selectedProfile();
    if (!stock || !source) {
        return;
    }
    const copy = {
        ...cloneValue(source),
        id: crypto.randomUUID(),
        name: uniqueProfileName(`${source.name} copia`)
    };
    state.stocks = state.stocks.map((item) =>
        item.id === stock.id ? { ...item, strategies: [...item.strategies, copy] } : item);
    state.profiles = flattenStocks(state.stocks);
    state.selectedProfileId = copy.id;
    applyProfileToForm(copy);
    renderProfiles();
    setDirty(true);
    $("strategy-actions-menu")?.removeAttribute("open");
    toast("Strategia duplicata: salva le modifiche per confermarla");
}

function deleteStock() {
    if (state.stocks.length <= 1) {
        toast("Deve restare almeno uno stock", true);
        return;
    }
    if (!window.confirm(`Eliminare lo stock ${selectedStock()?.symbol || "selezionato"} e tutte le sue strategie? La rimozione sarà definitiva dopo il salvataggio.`)) {
        return;
    }
    state.stocks = state.stocks.filter((stock) => stock.id !== state.selectedStockId);
    state.selectedStockId = state.stocks[0].id;
    state.selectedProfileId = state.stocks[0].strategies[0]?.id || null;
    state.profiles = flattenStocks(state.stocks);
    renderProfiles();
    applyProfileToForm(selectedProfile());
    setDirty(true);
}

function deleteStrategy() {
    const stock = selectedStock();
    if (!stock || stock.strategies.length <= 1) {
        toast("Deve restare almeno una strategia per stock", true);
        return;
    }
    const profile = selectedProfile();
    if (!window.confirm(`Eliminare la strategia “${profile?.name || "selezionata"}”? La rimozione sarà definitiva dopo il salvataggio.`)) {
        return;
    }
    const strategies = stock.strategies.filter((profile) => profile.id !== state.selectedProfileId);
    state.stocks = state.stocks.map((item) =>
        item.id === stock.id ? { ...item, strategies } : item);
    state.selectedProfileId = strategies[0].id;
    state.profiles = flattenStocks(state.stocks);
    renderProfiles();
    applyProfileToForm(selectedProfile());
    setDirty(true);
    $("strategy-actions-menu")?.removeAttribute("open");
}

function changeStrategyType(newType) {
    if (newType === "PATTERN_V1") {
        window.location.assign("/pattern-v1.html");
        return;
    }
    const profile = selectedProfile();
    const previousType = profile?.strategy;
    if (!profile || !newType || newType === previousType) {
        setValue("profile-strategy", previousType || newType);
        return;
    }
    const previous = strategyMeta(previousType).label;
    const next = strategyMeta(newType).label;
    if (!window.confirm(`Passare da “${previous}” a “${next}”? I parametri specifici non compatibili verranno riportati ai valori predefiniti.`)) {
        setValue("profile-strategy", previousType);
        renderStrategyWorkspace();
        return;
    }
    const defaults = defaultStrategy(profile.symbol || selectedStock()?.symbol || "SPY");
    setValue("profile-strategy", newType);
    setValue("profile-fast", defaults.fastPeriod);
    setValue("profile-slow", defaults.slowPeriod);
    setValue("profile-pivot-strength", defaults.pivotStrength);
    setValue("profile-minimum-tick", defaults.minimumTick);
    setValue("profile-tte-max-bars", defaults.tteMaxBars);
    setValue("profile-entry-buffer-ticks", defaults.entryBufferTicks);
    setValue("profile-stop-buffer-ticks", defaults.stopBufferTicks);
    setValue("profile-initial-stop-mode", defaults.initialStopMode);
    setValue("profile-minimum-room-enabled", String(defaults.minimumRoomEnabled));
    setValue("profile-minimum-room-type", defaults.minimumRoomType);
    setValue("profile-minimum-room-value", defaults.minimumRoomValue);
    setValue("profile-allow-double-triple", String(defaults.allowDoubleTriple));
    setValue("profile-double-triple-tolerance", defaults.doubleTripleToleranceTicks);
    setValue("profile-reject-gap", String(defaults.rejectGapOverEntry));
    setValue("profile-fallback-point2", String(defaults.fallbackToPoint2Breakout));
    setValue("profile-bollinger-enabled", String(defaults.bollingerPoint1FilterEnabled));
    setValue("profile-bollinger-period", defaults.bollingerPeriod);
    setValue("profile-bollinger-standard-deviation", defaults.bollingerStandardDeviation);
    setValue("profile-point1-price-mode", defaults.point1PriceMode);
    setValue("profile-point2-target-sizing-enabled",
        String(defaults.point2TargetSizingEnabled));
    setValue("profile-point2-target-profit-percent", defaults.point2TargetProfitPercent);
    setValue("profile-minimum-point2-distance", defaults.minimumPoint2Distance);
    setValue("profile-tp1-position-percent", defaults.tp1PositionPercent);
    setValue("profile-tp2-position-percent", defaults.tp2PositionPercent);
    setValue("profile-tp3-position-percent", defaults.tp3PositionPercent);
    saveSelectedFormInMemory();
    state.activeStrategyTab = "pattern";
    renderProfiles();
    setDirty(true);
    validateProfileForm();
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
        $("equity-chart").innerHTML = "";
        $("chart-controls").hidden = true;
        $("simulation-body").innerHTML = emptyRow(6, "Nessun segnale simulato");
        $("trades-body").innerHTML = emptyRow(8, "Nessun trade chiuso");
        if (exportButton) {
            exportButton.disabled = true;
        }
        return;
    }
    const simulation = state.simulation;
    if (!(simulation.trades || []).some((trade) => tradeId(trade) === state.selectedTradeId)) {
        state.selectedTradeId = simulation.trades?.length ? tradeId(simulation.trades[0]) : null;
    }
    if (exportButton) {
        exportButton.disabled = !simulation.backtestId;
    }
    const executionModel = simulation.evaluationParameters?.executionModel || "";
    $("simulation-summary").textContent =
        `${simulation.symbol} ${simulation.strategy} ${simulation.inputCount} barre, segnali ${simulation.signals.length}, trade ${simulation.trades.length}${executionModel ? ` - ${executionModel}` : ""}`;
    renderSimulationMetrics(simulation);
    renderSimulationCharts();
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
            <tr class="trade-row ${groupClass} ${boundaryClass} ${tradeId(row) === state.selectedTradeId ? "selected-trade" : ""}"
                data-trade-id="${tradeId(row)}" tabindex="0" aria-selected="${tradeId(row) === state.selectedTradeId}">
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
    if (!isJoeRossStrategy(parameters.strategy)) {
        items.push(["Parametri",
            `F${parameters.fastPeriod} / S${parameters.slowPeriod} / Q${parameters.quantity}`]);
    }
    const joe = simulation.joeRossStatistics;
    if (joe) {
        if (Object.hasOwn(joe, "point3CorrectionsStarted")) {
            items.push(
                ["Point1 candidates", formatNumber(joe.point1Candidates)],
                ["Point1 accepted by Bollinger", formatNumber(joe.point1AcceptedByBollinger)],
                ["Point1 rejected by Bollinger", formatNumber(joe.point1RejectedByBollinger)],
                ["1-2-3", formatNumber(joe.patterns123Found)],
                ["Correzioni P3", formatNumber(joe.point3CorrectionsStarted)],
                ["Candidate TTE1", formatNumber(joe.tteBar1Candidates)],
                ["Candidate TTE2", formatNumber(joe.tteBar2Candidates)],
                ["Candidate TTE3", formatNumber(joe.tteBar3Candidates)],
                ["Trigger TTE1", formatNumber(joe.tteTriggeredBar1)],
                ["Trigger TTE2", formatNumber(joe.tteTriggeredBar2)],
                ["Trigger TTE3", formatNumber(joe.tteTriggeredBar3)],
                ["TTE scadute", formatNumber(joe.tteExpired)],
                ["Breakout P2 senza TTE", formatNumber(joe.tteMissedPoint2Breakout)],
                ["Room reject", formatNumber(joe.tteRejectedInsufficientRoom)],
                ["Setup invalidati", formatNumber(joe.setupsInvalidated)],
                ["Trade LONG", formatNumber(joe.longTradesTriggered)],
                ["Trade SHORT", formatNumber(joe.shortTradesTriggered)]
            );
        } else {
            items.push(
                ["1-2-3 legacy", formatNumber(joe.patterns123Found)],
                ["TTE trigger legacy", formatNumber(joe.tteTriggered)],
                ["Setup invalidati", formatNumber(joe.setupsInvalidated)]
            );
        }
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
        if (parameters.strategy === "JOE_ROSS_123_TTE") {
            const strategyAverage = (key) => simulation.trades
                .map((trade) => Number(trade.strategyData?.[key]))
                .filter(Number.isFinite)
                .reduce((sum, value, _index, values) => sum + value / values.length, 0);
            const point2Hits = simulation.trades.filter((trade) =>
                (trade.events || []).some((event) =>
                    event.eventType === "TP1_FILLED"
                    || event.eventType === "EXIT - TP1")).length;
            items.push(
                ["Average Target Profit %", `${formatNumber(strategyAverage("targetProfitPercent"))}%`],
                ["Average Expected Profit at P2", formatNumber(strategyAverage("expectedProfitAtPoint2"))],
                ["Average Actual Risk %", `${formatNumber(strategyAverage("actualStopRiskPercent"))}%`],
                ["Average Quantity", formatNumber(average("quantity"))],
                ["Trade che raggiungono Point2", formatNumber(point2Hits)],
                ["Point2 Hit Rate", `${formatNumber(point2Hits * 100 / simulation.trades.length)}%`]
            );
        }
    }
    $("simulation-metrics").innerHTML = items.map(([label, value]) => `
        <div class="mini-metric"><span>${label}</span><strong>${value}</strong></div>
    `).join("");
}

function ensureTradingChartController() {
    if (tradingChartController || !window.TradingChart) {
        return tradingChartController;
    }
    tradingChartController = window.TradingChart.create({
        host: $("trading-chart-canvas"),
        tooltip: $("trading-chart-tooltip"),
        infoPanel: $("trade-info-panel"),
        timeline: $("trade-event-timeline"),
        eventDetail: $("trade-event-detail")
    });
    return tradingChartController;
}

function chartTimeframe() {
    return selectedProfile()?.timeframe
        || state.history?.timeframe
        || "DAILY";
}

function selectedSimulationTradeIndex() {
    return (state.simulation?.trades || [])
        .findIndex((trade) => tradeId(trade) === state.selectedTradeId);
}

function updateTradeChartHeader() {
    const simulation = state.simulation;
    const trades = simulation?.trades || [];
    const selectedIndex = selectedSimulationTradeIndex();
    $("chart-symbol").textContent = simulation?.symbol || "-";
    $("chart-strategy").textContent = simulation?.strategy || "-";
    $("chart-timeframe").textContent = chartTimeframe();
    $("chart-trade-position").textContent = selectedIndex < 0
        ? "Nessun trade"
        : `${state.selectedTradeId} / ${trades.length}`;
    $("chart-previous-trade").disabled = selectedIndex <= 0;
    $("chart-next-trade").disabled = selectedIndex < 0 || selectedIndex >= trades.length - 1;
    $("chart-fit-trade").disabled = selectedIndex < 0;
}

function updateTradeTableSelection() {
    document.querySelectorAll("#trades-body [data-trade-id]").forEach((row) => {
        const selected = row.dataset.tradeId === state.selectedTradeId;
        row.classList.toggle("selected-trade", selected);
        row.setAttribute("aria-selected", String(selected));
    });
}

function renderSimulationCharts(autoFit = false) {
    if (!state.simulation) {
        return;
    }
    $("chart-controls").hidden = false;
    const showBollinger = state.simulation.strategy === "JOE_ROSS_123_TTE";
    if ($("chart-filter-bollinger-label")) {
        $("chart-filter-bollinger-label").hidden = !showBollinger;
    }
    if ($("chart-bollinger-legend")) {
        $("chart-bollinger-legend").hidden = !showBollinger;
    }
    updateTradeChartHeader();
    ensureTradingChartController()?.update({
        simulation: state.simulation,
        selectedTradeId: state.selectedTradeId,
        filters: state.chartFilters,
        timeframe: chartTimeframe(),
        autoFit
    });
    renderEquityChart(state.simulation, {
        start: 0,
        end: state.simulation.priceSeries?.length || 0
    });
}

function selectSimulationTrade(tradeIdentifier, autoFit = true) {
    if (!(state.simulation?.trades || []).some((trade) => tradeId(trade) === tradeIdentifier)) {
        return;
    }
    state.selectedTradeId = tradeIdentifier;
    updateTradeTableSelection();
    renderSimulationCharts(autoFit);
}

function navigateSimulationTrade(direction) {
    const next = window.TradingChart?.navigateTrade(
        state.simulation?.trades,
        state.selectedTradeId,
        direction);
    if (next) {
        selectSimulationTrade(next, true);
    }
}

function updateChartFilters() {
    state.chartFilters = {
        patterns: $("chart-filter-patterns").checked,
        tte: $("chart-filter-tte").checked,
        entries: $("chart-filter-entries").checked,
        stops: $("chart-filter-stops").checked,
        takeProfits: $("chart-filter-take-profits").checked,
        volume: $("chart-filter-volume").checked,
        indicators: $("chart-filter-indicators").checked,
        strategyDebug: $("chart-filter-debug").checked
    };
    renderSimulationCharts(false);
}

function renderEquityChart(simulation, window) {
    const visiblePrices = simulation.priceSeries.slice(window.start, window.end);
    const firstIndex = visiblePrices.length === 0 ? 0 : visiblePrices[0].index;
    const lastIndex = visiblePrices.length === 0 ? -1 : visiblePrices[visiblePrices.length - 1].index;
    const labelsByIndex = new Map(simulation.priceSeries.map((point) => [point.index, point]));
    const points = simulation.equityCurve
        .filter((point) => point.index >= firstIndex && point.index <= lastIndex)
        .map((point) => ({
            x: point.index,
            y: point.equity,
            timestamp: labelsByIndex.get(point.index)?.timestamp,
            label: labelsByIndex.get(point.index)?.label
        }));
    $("equity-chart").innerHTML = lineChart(points, "line-equity", []);
}

function lineChart(points, lineClass, markers) {
    if (!points || points.length === 0) {
        return `<div class="empty-row">Nessun dato</div>`;
    }
    const width = 760;
    const height = 300;
    const pad = { left: 64, right: 24, top: 24, bottom: 42 };
    const minX = Math.min(...points.map((point) => point.x));
    const maxX = Math.max(...points.map((point) => point.x));
    const yValues = [...points.map((point) => point.y), ...markers.map((marker) => marker.y)];
    const minY = Math.min(...yValues);
    const maxY = Math.max(...yValues);
    const spanX = maxX - minX;
    const spanY = Math.max(1, maxY - minY);
    const lowY = minY - spanY * 0.08;
    const highY = maxY + spanY * 0.08;
    const scaleX = (value) => spanX === 0
        ? (pad.left + width - pad.right) / 2
        : pad.left + (value - minX) / spanX * (width - pad.left - pad.right);
    const scaleY = (value) => height - pad.bottom - (value - lowY) / (highY - lowY) * (height - pad.top - pad.bottom);
    const path = points.map((point, index) =>
        `${index === 0 ? "M" : "L"}${scaleX(point.x).toFixed(2)},${scaleY(point.y).toFixed(2)}`
    ).join(" ");
    const gridValues = [lowY, lowY + (highY - lowY) / 2, highY];
    const pointSvg = points.length <= 120 ? points.map((point) => `
        <circle class="equity-point" cx="${scaleX(point.x)}" cy="${scaleY(point.y)}" r="3">
            <title>${escapeChartText(`${chartAxisLabel(point)} · Equity ${formatNumber(point.y)}`)}</title>
        </circle>
    `).join("") : "";
    const markerSvg = markers.map((marker) => {
        const className = marker.type === "SELL" ? "sell-marker" : "buy-marker";
        const x = scaleX(marker.x).toFixed(2);
        const y = scaleY(marker.y).toFixed(2);
        const shape = marker.type === "SELL"
            ? `<path class="${className}" d="M ${x} ${Number(y) + 6} l 6 -12 h -12 z"></path>`
            : `<path class="${className}" d="M ${x} ${Number(y) - 6} l 6 12 h -12 z"></path>`;
        return `<g><title>${escapeChartText(marker.detail || marker.label || "Segnale")}</title>
            ${shape}${marker.label ? `<text class="marker-label" x="${Number(x) + 7}" y="${Number(y) - 7}">${escapeChartText(marker.label)}</text>` : ""}
        </g>`;
    }).join("");
    return `
        <svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" role="img" aria-label="Andamento del capitale nel periodo selezionato">
            <title>Andamento del capitale nel periodo selezionato</title>
            ${gridValues.map((value) => `
                <line class="grid-line" x1="${pad.left}" y1="${scaleY(value)}" x2="${width - pad.right}" y2="${scaleY(value)}"></line>
                <text x="8" y="${scaleY(value) + 4}">${formatNumber(value)}</text>
            `).join("")}
            <path class="${lineClass}" d="${path}"></path>
            ${pointSvg}
            ${markerSvg}
            <text x="${pad.left}" y="${height - 12}" text-anchor="start">${escapeChartText(chartAxisLabel(points[0]))}</text>
            <text x="${width - pad.right}" y="${height - 12}" text-anchor="end">${escapeChartText(chartAxisLabel(points[points.length - 1]))}</text>
        </svg>
    `;
}

function chartAxisLabel(point) {
    if (point?.timestamp) {
        const date = new Date(point.timestamp);
        if (!Number.isNaN(date.getTime())) {
            return date.toLocaleDateString("it-IT");
        }
    }
    return String(point?.label ?? `Bar ${point?.x ?? "-"}`);
}

function escapeChartText(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
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
    fillStrategyTypeSelect($("profile-strategy"), state.strategyOptions);
    applyProfileToForm(selectedProfile());
    renderAll();
    captureSavedState();
    setDirty(false);
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

async function downloadHistory() {
    saveSelectedFormInMemory();
    const profile = selectedProfile();
    state.history = await api("/api/workbench/historical/download", {
        method: "POST",
        body: JSON.stringify({
            symbol: profile.symbol,
            provider: "IBKR",
            timeframe: profile.timeframe,
            outputSize: profile.outputSize,
            maxPoints: profile.maxPoints,
            refresh: true
        })
    });
    $("simulate-prices").value = state.history.closePrices.join(", ");
    setResultTab("history");
    renderHistory();
    const source = state.history.source === "DATABASE" ? "database" : "IBKR";
    const suffix = state.history.message ? `. ${state.history.message}` : "";
    toast(`Storico aggiornato: ${state.history.bars.length} barre ${profile.symbol} da ${source}${suffix}`);
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
            initialStopMode: profile.initialStopMode,
            minimumRoomEnabled: profile.minimumRoomEnabled,
            minimumRoomType: profile.minimumRoomType,
            minimumRoomValue: profile.minimumRoomValue,
            allowDoubleTriple: profile.allowDoubleTriple,
            doubleTripleToleranceTicks: profile.doubleTripleToleranceTicks,
            rejectGapOverEntry: profile.rejectGapOverEntry,
            fallbackToPoint2Breakout: profile.fallbackToPoint2Breakout,
            bollingerPoint1FilterEnabled: profile.bollingerPoint1FilterEnabled,
            bollingerPeriod: profile.bollingerPeriod,
            bollingerStandardDeviation: profile.bollingerStandardDeviation,
            bollingerSource: profile.bollingerSource || "CLOSE",
            point1PriceMode: profile.point1PriceMode || "EXTREME",
            point2TargetSizingEnabled: profile.point2TargetSizingEnabled ?? true,
            point2TargetProfitPercent: profile.point2TargetProfitPercent ?? 2.0,
            minimumPoint2Distance: profile.minimumPoint2Distance ?? 0,
            tp1PositionPercent: profile.tp1PositionPercent ?? 60,
            tp2PositionPercent: profile.tp2PositionPercent ?? 20,
            tp3PositionPercent: profile.tp3PositionPercent ?? 20
        })
    });
    state.selectedTradeId = state.simulation.trades?.length
        ? tradeId(state.simulation.trades[0])
        : null;
    const totalChartBars = state.simulation.priceSeries?.length || 0;
    const initialChartBars = Math.min(120, totalChartBars);
    state.chartWindow = {
        start: Math.max(0, totalChartBars - initialChartBars),
        size: initialChartBars
    };
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
    bind("refresh-button", () => requestTransition(refreshAll));
    bind("add-stock-button", () => requestTransition(addStock));
    bind("add-strategy-button", () => requestTransition(addStrategy));
    bind("delete-stock-button", () => requestTransition(deleteStock));
    bind("delete-strategy-button", () => requestTransition(deleteStrategy));
    bind("duplicate-strategy-button", () => requestTransition(duplicateStrategy));
    $("save-profiles-button")?.addEventListener("click", () => saveProfiles());
    $("cancel-profile-changes-button")?.addEventListener("click", discardProfileChanges);
    $("stock-select")?.addEventListener("change", (event) => {
        const stockId = event.target.value;
        renderProfiles();
        requestTransition(() => selectStockById(stockId))
            .catch((error) => toast(error.message, true, 9000));
    });
    $("strategy-select")?.addEventListener("change", (event) => {
        const profileId = event.target.value;
        renderProfiles();
        if (profileId === "__PATTERN_V1_BACKTEST__") {
            requestTransition(() => window.location.assign("/pattern-v1.html"))
                .catch((error) => toast(error.message, true, 9000));
            return;
        }
        requestTransition(() => selectProfileById(profileId))
            .catch((error) => toast(error.message, true, 9000));
    });
    $("profile-strategy")?.addEventListener("change", (event) => {
        const newType = event.target.value;
        setValue("profile-strategy", selectedProfile()?.strategy || newType);
        requestTransition(() => changeStrategyType(newType))
            .catch((error) => toast(error.message, true, 9000));
    });
    document.querySelectorAll("[data-strategy-tab]").forEach((button) => {
        button.addEventListener("click", () => setStrategyTab(button.dataset.strategyTab));
        button.addEventListener("keydown", (event) => {
            if (!["ArrowLeft", "ArrowRight"].includes(event.key)) {
                return;
            }
            const tabs = Array.from(document.querySelectorAll("[data-strategy-tab]:not([hidden])"));
            const current = tabs.indexOf(button);
            const direction = event.key === "ArrowRight" ? 1 : -1;
            const next = tabs[(current + direction + tabs.length) % tabs.length];
            next?.focus();
            next?.click();
            event.preventDefault();
        });
    });
    document.addEventListener("input", (event) => {
        if (!PROFILE_FIELD_IDS.has(event.target.id)) {
            return;
        }
        saveSelectedFormInMemory();
        renderStrategySelect();
        renderStrategyProfileList();
        renderStrategyWorkspace();
        markDirtyFromCurrentState();
        validateProfileForm();
    });
    document.addEventListener("change", (event) => {
        if (!PROFILE_FIELD_IDS.has(event.target.id)) {
            return;
        }
        saveSelectedFormInMemory();
        renderStrategySelect();
        renderStrategyProfileList();
        renderStrategyWorkspace();
        markDirtyFromCurrentState();
        validateProfileForm();
    });
    $("new-strategy-form")?.addEventListener("submit", (event) => {
        event.preventDefault();
        const name = $("new-strategy-name").value.trim();
        let error = "";
        if (!name) {
            error = "Inserisci il nome del profilo.";
        } else if (profileNameExists(name)) {
            error = "Esiste già un profilo con questo nome per lo stock selezionato.";
        }
        $("new-strategy-name-error").textContent = error;
        $("new-strategy-name").classList.toggle("field-invalid", Boolean(error));
        if (error) {
            $("new-strategy-name").focus();
            return;
        }
        createStrategy(name, $("new-strategy-type").value);
    });
    $("cancel-new-strategy-button")?.addEventListener("click", () => $("new-strategy-dialog")?.close());
    $("stay-on-page-button")?.addEventListener("click", () => continuePendingTransition("stay"));
    $("discard-and-continue-button")?.addEventListener("click", () => continuePendingTransition("discard"));
    $("save-and-continue-button")?.addEventListener("click", () => continuePendingTransition("save"));
    $("unsaved-changes-dialog")?.addEventListener("cancel", () => {
        state.pendingTransition = null;
    });
    document.addEventListener("click", (event) => {
        const link = event.target.closest("a[href]");
        if (!link || !state.dirty || link.target === "_blank" || link.origin !== window.location.origin) {
            return;
        }
        event.preventDefault();
        requestTransition(() => { window.location.href = link.href; });
    });
    window.addEventListener("beforeunload", (event) => {
        if (!state.dirty) {
            return;
        }
        event.preventDefault();
        event.returnValue = "";
    });
    bindStooqFileImport();
    bind("download-history-button", downloadHistory);
    bind("simulate-button", simulateStrategy);
    bind("export-excel-button", exportBacktestExcel);
    $("chart-previous-trade")?.addEventListener("click", () => navigateSimulationTrade(-1));
    $("chart-next-trade")?.addEventListener("click", () => navigateSimulationTrade(1));
    $("chart-fit-trade")?.addEventListener("click", () => tradingChartController?.fitTrade());
    document.querySelectorAll("[data-chart-range]").forEach((button) => {
        button.addEventListener("click", () => {
            document.querySelectorAll("[data-chart-range]").forEach((candidate) =>
                candidate.classList.toggle("active", candidate === button));
            tradingChartController?.setRange(button.dataset.chartRange);
        });
    });
    [
        "chart-filter-patterns",
        "chart-filter-tte",
        "chart-filter-entries",
        "chart-filter-stops",
        "chart-filter-take-profits",
        "chart-filter-volume",
        "chart-filter-indicators",
        "chart-filter-debug"
    ].forEach((id) => $(id)?.addEventListener("change", updateChartFilters));
    $("trades-body")?.addEventListener("click", (event) => {
        const row = event.target.closest("[data-trade-id]");
        if (row) {
            selectSimulationTrade(row.dataset.tradeId, true);
        }
    });
    $("trades-body")?.addEventListener("keydown", (event) => {
        if (!["Enter", " "].includes(event.key)) {
            return;
        }
        const row = event.target.closest("[data-trade-id]");
        if (row) {
            event.preventDefault();
            selectSimulationTrade(row.dataset.tradeId, true);
        }
    });
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
    window.addEventListener("pagehide", () => tradingChartController?.destroy(), { once: true });
}

initEvents();
refreshAll().catch((error) => toast(error.message, true));
window.setInterval(() => refreshStatusOnly().catch(() => undefined), 5000);
