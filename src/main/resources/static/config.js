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

function applyConfigToForm(config) {
    fillSelect($("runtime-mode"), config.tradingModeOptions || ["PAPER"]);
    fillSelect($("market-mode"), config.modeOptions || []);

    setValue("runtime-mode", config.runtime?.mode || "PAPER");
    setValue("runtime-live", String(config.runtime?.liveTradingEnabled || false));
    setValue("runtime-orders-enabled", String(config.runtime?.orderSubmissionEnabled || false));
    setValue("runtime-paper-confirmation", config.runtime?.paperAccountConfirmation || "");
    $("paper-confirmation-note").textContent =
        `Frase richiesta: ${config.runtime?.requiredPaperConfirmation || "I_CONFIRM_THIS_IS_A_PAPER_ACCOUNT"}`;

    setValue("market-enabled", String(config.marketData?.enabled ?? true));
    setValue("market-mode", config.marketData?.mode || "DELAYED");

    setValue("risk-max-order-value", config.risk?.maxOrderValue);
    setValue("risk-max-position-value", config.risk?.maxPositionValue);
    setValue("risk-max-quantity", config.risk?.maxQuantityPerOrder);
    setValue("risk-max-daily-orders", config.risk?.maxDailyOrders);
    setValue("risk-max-open-orders", config.risk?.maxOpenOrders);
    setValue("risk-max-daily-loss", config.risk?.maxDailyLoss);
    setValue("risk-allow-short", String(config.risk?.allowShortSelling ?? false));

    setValue("orders-entry-tif", config.orders?.entryTimeInForce || "DAY");
    setValue("orders-protective-tif", config.orders?.protectiveTimeInForce || "GTC");
    setValue("orders-outside-rth", String(config.orders?.outsideRegularTradingHours ?? false));
    setValue("orders-delay", config.orders?.interOrderDelayMillis);
}

function collectConfig() {
    return {
        runtime: {
            mode: $("runtime-mode").value,
            liveTradingEnabled: boolValue("runtime-live"),
            orderSubmissionEnabled: boolValue("runtime-orders-enabled"),
            paperAccountConfirmation: $("runtime-paper-confirmation").value.trim()
        },
        marketData: {
            enabled: boolValue("market-enabled"),
            provider: "IBKR",
            mode: $("market-mode").value
        },
        strategy: null,
        risk: {
            maxOrderValue: num("risk-max-order-value"),
            maxPositionValue: num("risk-max-position-value"),
            maxQuantityPerOrder: num("risk-max-quantity"),
            maxDailyOrders: num("risk-max-daily-orders"),
            maxOpenOrders: num("risk-max-open-orders"),
            maxDailyLoss: num("risk-max-daily-loss"),
            allowShortSelling: boolValue("risk-allow-short")
        },
        orders: {
            entryTimeInForce: $("orders-entry-tif").value.trim().toUpperCase() || "DAY",
            protectiveTimeInForce: $("orders-protective-tif").value.trim().toUpperCase() || "GTC",
            outsideRegularTradingHours: boolValue("orders-outside-rth"),
            interOrderDelayMillis: num("orders-delay")
        }
    };
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
}

async function refreshStatusOnly() {
    state.status = await api("/api/paper/status");
    renderStatus();
}

async function saveConfig(showToast = true) {
    state.config = await api("/api/workbench/config", {
        method: "PUT",
        body: JSON.stringify(collectConfig())
    });
    applyConfigToForm(state.config);
    await refreshStatusOnly();
    if (showToast) {
        toast("Configurazione salvata");
    }
}

async function restartMarketData() {
    await saveConfig(false);
    await api("/api/workbench/market-data/restart", { method: "POST" });
    await refreshStatusOnly();
    toast("Configurazione salvata e market data riavviati");
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
window.setInterval(() => refreshStatusOnly().catch(() => undefined), 5000);
