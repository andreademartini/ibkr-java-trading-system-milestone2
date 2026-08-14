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

function applyConfigToForm(config) {
    setValue("config-ibkr-host", config.ibkr.host);
    setValue("config-ibkr-port", config.ibkr.port);
    setValue("config-ibkr-client-id", config.ibkr.clientId);
    setValue("config-ibkr-account", config.ibkr.accountId);
    setValue("config-ibkr-auto-connect", String(config.ibkr.autoConnect));
    setValue("config-ibkr-custom-port", String(config.ibkr.allowCustomPaperPort));
    setValue("config-ibkr-history-timeout", config.ibkr.historicalDataTimeoutSeconds);
}

function collectConfig() {
    return {
        ibkr: {
            host: $("config-ibkr-host").value.trim() || "127.0.0.1",
            port: num("config-ibkr-port"),
            clientId: num("config-ibkr-client-id"),
            accountId: $("config-ibkr-account").value.trim(),
            autoConnect: boolValue("config-ibkr-auto-connect"),
            allowCustomPaperPort: boolValue("config-ibkr-custom-port"),
            historicalDataTimeoutSeconds: num("config-ibkr-history-timeout")
        },
        marketData: null,
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

async function saveConfig() {
    return saveConfigWithToast(true);
}

async function saveConfigWithToast(showToast) {
    state.config = await api("/api/workbench/config", {
        method: "PUT",
        body: JSON.stringify(collectConfig())
    });
    applyConfigToForm(state.config);
    state.status = await api("/api/paper/status");
    renderStatus();
    if (showToast) {
        toast("Configurazione IBKR salvata");
    }
}

async function reconnectIbkr() {
    await saveConfigWithToast(false);
    await api("/api/workbench/ibkr/reconnect", { method: "POST" });
    state.status = await api("/api/paper/status");
    renderStatus();
    toast("Connessione IBKR riavviata");
}

async function testIbkrConnection() {
    await saveConfigWithToast(false);
    const result = await api("/api/workbench/ibkr/test", { method: "POST" });
    renderConnectionTest(result);
    toast(result.success ? "Test connessione IBKR riuscito" : "Test connessione IBKR fallito", !result.success);
}

function renderConnectionTest(result) {
    const element = $("connection-test-result");
    if (!result) {
        element.textContent = "";
        element.className = "inline-status";
        return;
    }
    element.className = `inline-status ${result.success ? "success" : "error"}`;
    const details = result.success
        ? `OK: ${result.host}:${result.port}, client test ${result.clientId}, nextValidId ${result.nextValidId}`
        : `Fallita: ${result.host}:${result.port}, client test ${result.clientId}. ${result.message || "Controllare TWS/IB Gateway"}`;
    element.textContent = details;
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
bind("test-ibkr-button", testIbkrConnection);
bind("reconnect-ibkr-button", reconnectIbkr);
refreshAll().catch((error) => toast(error.message, true));
window.setInterval(() => {
    api("/api/paper/status")
        .then((status) => {
            state.status = status;
            renderStatus();
        })
        .catch(() => undefined);
}, 5000);
