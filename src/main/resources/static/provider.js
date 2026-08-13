const state = {
    config: null,
    status: null
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

function formatNumber(value) {
    if (value === null || value === undefined || value === "") {
        return "-";
    }
    const number = Number(value);
    return Number.isFinite(number)
        ? number.toLocaleString("it-IT", { maximumFractionDigits: 4 })
        : String(value);
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

function applyConfigToForm(config) {
    fillSelect($("config-provider"), config.providerOptions);
    fillSelect($("config-mode"), config.modeOptions);
    const instrument = config.marketData.instruments[0] || {};
    setValue("config-provider", config.marketData.provider);
    setValue("config-market-enabled", String(config.marketData.enabled));
    setValue("config-mode", config.marketData.mode);
    setValue("config-symbol", instrument.symbol || "SPY");
    setValue("config-exchange", instrument.exchange || "SMART");
    setValue("config-primary-exchange", instrument.primaryExchange || "ARCA");
    setValue("config-currency", instrument.currency || "USD");
    setValue("config-alpha-polling", config.marketData.alphaVantage.pollingIntervalSeconds);
    $("config-alpha-key").placeholder = config.marketData.alphaVantage.apiKeyConfigured
        ? "Chiave Alpha Vantage gia' configurata"
        : "Inserisci API key Alpha Vantage";
}

function collectConfig() {
    const alphaKey = $("config-alpha-key").value.trim();
    return {
        marketData: {
            enabled: boolValue("config-market-enabled"),
            provider: $("config-provider").value,
            mode: $("config-mode").value,
            alphaVantage: {
                apiKey: alphaKey === "" ? null : alphaKey,
                pollingIntervalSeconds: num("config-alpha-polling")
            },
            instruments: [{
                symbol: $("config-symbol").value.trim().toUpperCase() || "SPY",
                securityType: "STK",
                exchange: $("config-exchange").value,
                currency: $("config-currency").value,
                primaryExchange: $("config-primary-exchange").value
            }]
        },
        strategy: null
    };
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

async function refreshAll() {
    const [config, status] = await Promise.all([
        api("/api/workbench/config"),
        api("/api/paper/status")
    ]);
    state.config = config;
    state.status = status;
    applyConfigToForm(config);
    renderStatus();
    renderMarketData();
}

async function saveConfig() {
    state.config = await api("/api/workbench/config", {
        method: "PUT",
        body: JSON.stringify(collectConfig())
    });
    applyConfigToForm(state.config);
    state.status = await api("/api/paper/status");
    renderStatus();
    toast("Configurazione provider salvata");
}

async function restartMarketData() {
    if ($("config-provider").value === "ALPHA_VANTAGE"
            && $("config-alpha-key").value.trim() === ""
            && !state.config?.marketData?.alphaVantage?.apiKeyConfigured) {
        throw new Error("Per Alpha Vantage devi inserire una API key");
    }
    await saveConfig();
    await api("/api/workbench/market-data/restart", { method: "POST" });
    state.status = await api("/api/paper/status");
    renderStatus();
    renderMarketData();
    toast("Provider salvato e market data riavviati");
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

bind("refresh-button", refreshAll);
bind("save-config-button", saveConfig);
bind("restart-market-data-button", restartMarketData);
refreshAll().catch((error) => toast(error.message, true));
window.setInterval(() => {
    api("/api/paper/status")
        .then((status) => {
            state.status = status;
            renderStatus();
            renderMarketData();
        })
        .catch(() => undefined);
}, 5000);
