const test = require("node:test");
const assert = require("node:assert/strict");

const {
    normaliseBars,
    calculateBollingerBands,
    buildMarkers,
    buildStopStepLine,
    buildTteEntryLevelLine,
    takeProfitStatus,
    navigateTrade,
    toChartTime,
    formatTradeId
} = require("../../main/resources/static/trading-chart.js");

function bars() {
    return normaliseBars([
        { index: 2, timestamp: "2026-01-03T10:00:00Z", open: 106, high: 109, low: 105, close: 108, volume: 300 },
        { index: 0, timestamp: "2026-01-01T10:00:00Z", open: 100, high: 102, low: 99, close: 101, volume: 100 },
        { index: 1, timestamp: "2026-01-02T10:00:00Z", open: 101, high: 110, low: 100, close: 107, volume: 200 }
    ]);
}

function longTrade() {
    return {
        number: 27,
        direction: "LONG",
        entryIndex: 3,
        exitIndex: 6,
        entryAt: "2026-01-04T10:00:00Z",
        exitAt: "2026-01-07T10:00:00Z",
        entryPrice: 108.51,
        initialStopPrice: 104.99,
        quantity: 100,
        takeProfits: [
            { targetNumber: 1, price: 110.68, quantity: 30 },
            { targetNumber: 2, price: 112.85, quantity: 30 },
            { targetNumber: 3, price: 117.19, quantity: 40 }
        ],
        events: [
            { eventType: "JOE_ROSS_POINT1_FOUND", index: 0, occurredAt: "2026-01-01T10:00:00Z", price: 100 },
            { eventType: "JOE_ROSS_POINT2_FOUND", index: 1, occurredAt: "2026-01-02T10:00:00Z", price: 110 },
            { eventType: "TTE_BAR_2_CREATED", index: 2, occurredAt: "2026-01-03T10:00:00Z", price: 108.5 },
            { eventType: "POINT3_CONFIRMED", index: 2, occurredAt: "2026-01-03T10:00:00Z", price: 105 },
            { eventType: "TTE_TRIGGERED", index: 3, occurredAt: "2026-01-04T10:00:00Z", price: 108.51 },
            { eventType: "ENTRY", index: 3, occurredAt: "2026-01-04T10:00:00Z", price: 108.51 },
            { eventType: "INITIAL STOP", index: 3, occurredAt: "2026-01-04T10:00:00Z", price: 104.99, currentStopPrice: 104.99 },
            { eventType: "TP1_FILLED", index: 4, occurredAt: "2026-01-05T10:00:00Z", price: 110.68, quantity: 30, remainingQuantity: 70, details: "JOE_ROSS_POINT2_TARGET" },
            { eventType: "STOP MOVED", index: 4, occurredAt: "2026-01-05T10:00:00Z", price: 108.51, currentStopPrice: 108.51 },
            { eventType: "EXIT - TP2", index: 5, occurredAt: "2026-01-06T10:00:00Z", price: 112.85, quantity: 30, remainingQuantity: 40 },
            { eventType: "STOP MOVED", index: 5, occurredAt: "2026-01-06T10:00:00Z", price: 110.68, currentStopPrice: 110.68 },
            { eventType: "EXIT - STOP_LOSS", index: 6, occurredAt: "2026-01-07T10:00:00Z", price: 110.68, quantity: 40, remainingQuantity: 0 }
        ],
        strategyData: {
            point3BarIndex: "2",
            point3Time: "2026-01-03T10:00:00Z",
            point3Price: "105",
            point2BarIndex: "1",
            point2Price: "110.68",
            tteEntryCandidate: "108.51",
            tp1Source: "POINT2",
            tp1Reason: "JOE_ROSS_POINT2_TARGET"
        }
    };
}

function completeBars() {
    return normaliseBars(Array.from({ length: 7 }, (_, index) => ({
        index,
        timestamp: `2026-01-0${index + 1}T10:00:00Z`,
        open: 100 + index,
        high: 102 + index,
        low: 99 + index,
        close: 101 + index,
        volume: 1000 + index
    })));
}

test("converte HistoricalBar in barre OHLC e volume senza modificare i prezzi", () => {
    const result = bars();
    assert.deepEqual(result.priceBars[0], {
        time: toChartTime("2026-01-01T10:00:00Z"),
        open: 100,
        high: 102,
        low: 99,
        close: 101
    });
    assert.equal(result.volumes[0].value, 100);
});

test("ordina temporalmente le barre", () => {
    const result = bars();
    assert.deepEqual(result.source.map((bar) => bar.index), [0, 1, 2]);
});

test("rimuove timestamp duplicati mantenendo l'ultimo dato", () => {
    const result = normaliseBars([
        { index: 0, timestamp: "2026-01-01T00:00:00Z", close: 1 },
        { index: 1, timestamp: "2026-01-01T00:00:00Z", close: 2 }
    ]);
    assert.equal(result.priceBars.length, 1);
    assert.equal(result.priceBars[0].close, 2);
});

test("ignora dati vuoti o con timestamp non valido", () => {
    assert.equal(normaliseBars([]).priceBars.length, 0);
    assert.equal(normaliseBars([{ timestamp: "non-data", close: 10 }]).priceBars.length, 0);
});

test("calcola le Bande di Bollinger 20 periodi e deviazione standard x2", () => {
    const source = Array.from({ length: 20 }, (_, index) => ({
        time: index + 1,
        close: 100
    }));
    const result = calculateBollingerBands(source);
    assert.equal(result.upper.length, 1);
    assert.deepEqual(result.upper[0], { time: 20, value: 100 });
    assert.deepEqual(result.middle[0], { time: 20, value: 100 });
    assert.deepEqual(result.lower[0], { time: 20, value: 100 });
});

test("calcola correttamente media e deviazione delle Bande di Bollinger", () => {
    const result = calculateBollingerBands([
        { time: 1, close: 1 },
        { time: 2, close: 2 },
        { time: 3, close: 3 }
    ], 3, 2);
    const deviation = Math.sqrt(2 / 3) * 2;
    assert.equal(result.middle[0].value, 2);
    assert.ok(Math.abs(result.upper[0].value - (2 + deviation)) < 1e-12);
    assert.ok(Math.abs(result.lower[0].value - (2 - deviation)) < 1e-12);
});

test("non crea Bande di Bollinger prima del periodo minimo", () => {
    const result = calculateBollingerBands([
        { time: 1, close: 100 },
        { time: 2, close: 101 }
    ], 20, 2);
    assert.deepEqual(result.upper, []);
    assert.deepEqual(result.middle, []);
    assert.deepEqual(result.lower, []);
});

test("crea marker P1 P2 P3 sulle barre corrette", () => {
    const markers = buildMarkers({}, longTrade(), {}, completeBars());
    const setup = markers.filter((marker) => ["P1", "P2", "P3"].includes(marker.text));
    assert.deepEqual(setup.map((marker) => marker.text), ["P1", "P2", "P3"]);
    assert.deepEqual(setup.map((marker) => marker.time), [
        toChartTime("2026-01-01T10:00:00Z"),
        toChartTime("2026-01-02T10:00:00Z"),
        toChartTime("2026-01-03T10:00:00Z")
    ]);
});

test("crea il marker TTE corretto", () => {
    const markers = buildMarkers({}, longTrade(), {}, completeBars());
    assert.ok(markers.some((marker) => marker.text === "TTE2"));
});

test("crea marker entry LONG sotto la barra", () => {
    const marker = buildMarkers({}, longTrade(), {}, completeBars())
        .find((candidate) => candidate.text === "LONG ENTRY");
    assert.equal(marker.position, "belowBar");
    assert.equal(marker.shape, "arrowUp");
    const point3 = buildMarkers({}, longTrade(), {}, completeBars())
        .find((candidate) => candidate.text === "P3");
    assert.ok(point3.time < marker.time, "P3 deve precedere la barra di entry");
});

test("disegna il livello TTE dalla barra P3 alla barra di entry successiva", () => {
    const line = buildTteEntryLevelLine(longTrade(), completeBars());
    assert.deepEqual(line, [
        { time: toChartTime("2026-01-03T10:00:00Z"), value: 108.51 },
        { time: toChartTime("2026-01-04T10:00:00Z"), value: 108.51 }
    ]);
});

test("non disegna un livello TTE se entry e P3 risultano sulla stessa barra", () => {
    const trade = longTrade();
    trade.entryIndex = 2;
    assert.deepEqual(buildTteEntryLevelLine(trade, completeBars()), []);
});

test("crea marker entry SHORT sopra la barra", () => {
    const trade = { ...longTrade(), direction: "SHORT" };
    const marker = buildMarkers({}, trade, {}, completeBars())
        .find((candidate) => candidate.text === "SHORT ENTRY");
    assert.equal(marker.position, "aboveBar");
    assert.equal(marker.shape, "arrowDown");
});

test("crea marker exit da stop", () => {
    const marker = buildMarkers({}, longTrade(), {}, completeBars())
        .find((candidate) => candidate.text === "STOP LOSS EXIT");
    assert.ok(marker);
});

test("costruisce la step line dello stop progressivo dai soli eventi backend", () => {
    const result = buildStopStepLine(longTrade(), completeBars());
    assert.deepEqual(result.map((point) => point.value), [104.99, 108.51, 110.68, 110.68]);
    assert.deepEqual(result.map((point) => point.time), [
        toChartTime("2026-01-04T10:00:00Z"),
        toChartTime("2026-01-05T10:00:00Z"),
        toChartTime("2026-01-06T10:00:00Z"),
        toChartTime("2026-01-07T10:00:00Z")
    ]);
});

test("mantiene un solo valore se più stop move avvengono sulla stessa barra", () => {
    const trade = longTrade();
    trade.events.push({
        eventType: "STOP MOVED",
        index: 4,
        occurredAt: "2026-01-05T10:00:00Z",
        currentStopPrice: 111
    });
    const result = buildStopStepLine(trade, completeBars());
    assert.equal(result.filter((point) => point.time === toChartTime("2026-01-05T10:00:00Z")).length, 1);
    assert.equal(result.find((point) => point.time === toChartTime("2026-01-05T10:00:00Z")).value, 111);
});

test("gestisce trade senza stop", () => {
    assert.deepEqual(buildStopStepLine({ entryIndex: 0 }, completeBars()), []);
});

test("riconosce TP filled e non filled", () => {
    const trade = longTrade();
    assert.equal(takeProfitStatus(trade, trade.takeProfits[0]), "FILLED");
    assert.equal(takeProfitStatus(trade, trade.takeProfits[2]), "NOT FILLED");
});

test("riconosce partial fill senza trasformarlo in filled", () => {
    const trade = longTrade();
    trade.events.push({ eventType: "TP3 PARTIAL", details: "partial", quantity: 20 });
    assert.equal(takeProfitStatus(trade, trade.takeProfits[2]), "PARTIAL");
});

test("filtra marker take profit immediatamente", () => {
    const markers = buildMarkers({}, longTrade(), { takeProfits: false }, completeBars());
    assert.equal(markers.some((marker) => marker.text.includes("TP1")), false);
});

test("mostra eventi setup senza trade solo in Strategy Debug", () => {
    const simulation = {
        joeRossEvents: [{
            type: "TTE_EXPIRED",
            barIndex: 1,
            timestamp: "2026-01-02T10:00:00Z",
            price: 105
        }]
    };
    assert.equal(buildMarkers(simulation, null, {}, completeBars()).length, 0);
    assert.equal(buildMarkers(simulation, null, { strategyDebug: true }, completeBars())[0].text, "TTE EXPIRED");
});

test("naviga al trade precedente e successivo", () => {
    const trades = [{ number: 1 }, { number: 2 }, { number: 3 }];
    assert.equal(navigateTrade(trades, "T002", -1), "T001");
    assert.equal(navigateTrade(trades, "T002", 1), "T003");
});

test("la navigazione resta entro i limiti", () => {
    const trades = [{ number: 1 }, { number: 2 }];
    assert.equal(navigateTrade(trades, "T001", -1), "T001");
    assert.equal(navigateTrade(trades, "T002", 1), "T002");
    assert.equal(navigateTrade([], null, 1), null);
});

test("formatta un identificativo trade stabile", () => {
    assert.equal(formatTradeId({ number: 7 }), "T007");
    assert.equal(formatTradeId({}), "T---");
});
