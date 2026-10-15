(function (root, factory) {
    const api = factory();
    if (typeof module === "object" && module.exports) {
        module.exports = api;
    }
    if (root) {
        root.TradingChart = api;
    }
})(typeof window !== "undefined" ? window : globalThis, function () {
    "use strict";

    const DEFAULT_FILTERS = Object.freeze({
        patterns: true,
        tte: true,
        entries: true,
        stops: true,
        takeProfits: true,
        volume: true,
        indicators: true,
        strategyDebug: false
    });

    const COLORS = Object.freeze({
        bull: "#15803d",
        bear: "#c62828",
        entry: "#2563eb",
        stop: "#dc2626",
        takeProfit: "#16a34a",
        tte: "#d97706",
        p1: "#7c3aed",
        p2: "#0891b2",
        p3: "#9333ea",
        bollingerUpper: "#6366f1",
        bollingerMiddle: "#0284c7",
        bollingerLower: "#6366f1",
        debug: "#64748b",
        exit: "#334155"
    });

    function finiteNumber(value) {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    }

    function toChartTime(value) {
        if (typeof value === "number" && Number.isFinite(value)) {
            return Math.floor(value > 10_000_000_000 ? value / 1000 : value);
        }
        const millis = new Date(value).getTime();
        return Number.isFinite(millis) ? Math.floor(millis / 1000) : null;
    }

    function formatTradeId(trade) {
        const number = finiteNumber(trade?.number);
        return number == null ? "T---" : `T${String(number).padStart(3, "0")}`;
    }

    function normaliseBars(priceSeries) {
        const unique = new Map();
        (priceSeries || []).forEach((point, sourcePosition) => {
            const time = toChartTime(point.timestamp ?? point.time);
            const close = finiteNumber(point.close ?? point.price);
            if (time == null || close == null) {
                return;
            }
            const open = finiteNumber(point.open) ?? close;
            const high = finiteNumber(point.high) ?? close;
            const low = finiteNumber(point.low) ?? close;
            const index = finiteNumber(point.index) ?? sourcePosition;
            unique.set(time, {
                time,
                index,
                timestamp: point.timestamp ?? point.time,
                label: point.label,
                open,
                high,
                low,
                close,
                volume: Math.max(0, finiteNumber(point.volume) ?? 0)
            });
        });
        const source = Array.from(unique.values()).sort((left, right) => left.time - right.time);
        return {
            source,
            priceBars: source.map(({ time, open, high, low, close }) => ({
                time, open, high, low, close
            })),
            volumes: source.map(({ time, volume, open, close }) => ({
                time,
                value: volume,
                color: close >= open ? "rgba(21,128,61,0.52)" : "rgba(198,40,40,0.48)"
            })),
            byIndex: new Map(source.map((bar) => [bar.index, bar])),
            byTime: new Map(source.map((bar) => [bar.time, bar]))
        };
    }

    function calculateBollingerBands(sourceBars, period = 20, multiplier = 2) {
        const resolvedPeriod = Math.max(2, Math.round(finiteNumber(period) ?? 20));
        const resolvedMultiplier = Math.max(0, finiteNumber(multiplier) ?? 2);
        const bars = (sourceBars || []).filter((bar) => finiteNumber(bar?.close) != null);
        const upper = [];
        const middle = [];
        const lower = [];
        let rollingSum = 0;
        let rollingSquares = 0;

        bars.forEach((bar, index) => {
            const close = finiteNumber(bar.close);
            if (close == null) {
                return;
            }
            rollingSum += close;
            rollingSquares += close * close;
            if (index >= resolvedPeriod) {
                const expired = finiteNumber(bars[index - resolvedPeriod]?.close);
                if (expired != null) {
                    rollingSum -= expired;
                    rollingSquares -= expired * expired;
                }
            }
            if (index < resolvedPeriod - 1) {
                return;
            }
            const average = rollingSum / resolvedPeriod;
            const variance = Math.max(0, rollingSquares / resolvedPeriod - average * average);
            const deviation = Math.sqrt(variance) * resolvedMultiplier;
            middle.push({ time: bar.time, value: average });
            upper.push({ time: bar.time, value: average + deviation });
            lower.push({ time: bar.time, value: average - deviation });
        });
        return { period: resolvedPeriod, multiplier: resolvedMultiplier, upper, middle, lower };
    }

    function eventChartTime(event, bars) {
        const index = finiteNumber(event?.index ?? event?.barIndex);
        if (index != null && bars?.byIndex?.has(index)) {
            return bars.byIndex.get(index).time;
        }
        const direct = toChartTime(event?.occurredAt ?? event?.timestamp ?? event?.time);
        if (direct == null || !bars?.source?.length) {
            return direct;
        }
        if (bars.byTime.has(direct)) {
            return direct;
        }
        let nearest = bars.source[0].time;
        let distance = Math.abs(nearest - direct);
        for (const bar of bars.source) {
            const candidateDistance = Math.abs(bar.time - direct);
            if (candidateDistance < distance) {
                nearest = bar.time;
                distance = candidateDistance;
            }
        }
        return nearest;
    }

    function eventType(event) {
        return String(event?.eventType ?? event?.type ?? "").trim().toUpperCase();
    }

    function joeRossLabel(type) {
        return {
            JOE_ROSS_POINT1_FOUND: "P1",
            POINT1_BOLLINGER_VALID: "P1 BB VALID",
            POINT1_BOLLINGER_REJECTED: "P1 BB REJECT",
            JOE_ROSS_POINT2_FOUND: "P2",
            JOE_ROSS_POINT3_FOUND: "P3",
            JOE_ROSS_POINT3_CORRECTION_STARTED: "P3 CORR",
            POINT3_CONFIRMED: "P3",
            TTE_BAR_1_CREATED: "TTE1",
            TTE_BAR_2_CREATED: "TTE2",
            TTE_BAR_3_CREATED: "TTE3",
            TTE_ENTRY_CANDIDATE: "ENTRY CANDIDATE",
            TTE_CANDIDATE_UPDATED: "ENTRY CANDIDATE",
            TTE_ORDER_CANCELLED: "TTE CANCEL",
            TTE_TRIGGERED: "TTE ENTRY",
            TTE_EXPIRED: "TTE EXPIRED",
            SETUP_EXPIRED: "SETUP EXPIRED",
            TTE_MISSED_POINT2_BREAKOUT: "TTE MISSED",
            TTE_REJECTED_INSUFFICIENT_ROOM: "ROOM REJECT",
            TTE_REJECTED_GAP: "GAP REJECT",
            SETUP_INVALIDATED: "INVALID"
        }[type] || null;
    }

    function eventCategory(type) {
        if (type === "PATTERN_DETECTED") return "patterns";
        if (type === "SIGNAL" || type === "TRADE_CLOSED" || type === "TIMEOUT") return "entries";
        if (type.startsWith("TAKE_PROFIT")) return "takeProfits";
        if (type.includes("POINT1") || type.includes("POINT2") || type.includes("POINT3")) {
            return "patterns";
        }
        if (type.includes("TTE") || type === "SETUP_INVALIDATED") {
            return "tte";
        }
        if (type.includes("STOP")) {
            return "stops";
        }
        if (type.includes("TP")) {
            return "takeProfits";
        }
        if (type === "ENTRY" || type === "TRADE_OPENED" || type.startsWith("EXIT")) {
            return "entries";
        }
        return "debug";
    }

    function markerDescriptor(event, trade, debugEvent) {
        const type = eventType(event);
        const side = String(trade?.direction ?? trade?.side ?? event?.direction ?? "LONG").toUpperCase();
        if (type === "PATTERN_DETECTED" || type === "SIGNAL") {
            return {text:type === "PATTERN_DETECTED" ? String(trade?.pattern || "PATTERN") : "SIGNAL",
                color:COLORS.p1,shape:"circle",position:side === "SHORT" ? "aboveBar" : "belowBar"};
        }
        if (["STOP_FILLED", "TAKE_PROFIT_FILLED", "TIMEOUT", "TRADE_CLOSED"].includes(type)) {
            return {text:type.replaceAll("_", " "),color:type === "STOP_FILLED" ? COLORS.stop : type === "TAKE_PROFIT_FILLED" ? COLORS.takeProfit : COLORS.exit,
                shape:"square",position:side === "SHORT" ? "belowBar" : "aboveBar"};
        }
        if (type === "INITIAL_STOP" || type === "TAKE_PROFIT_CREATED") {
            return {text:type.replaceAll("_", " "),color:type === "INITIAL_STOP" ? COLORS.stop : COLORS.takeProfit,
                shape:"circle",position:side === "SHORT" ? "aboveBar" : "belowBar"};
        }
        const joeLabel = joeRossLabel(type);
        if (joeLabel) {
            const isPoint = joeLabel === "P1" || joeLabel === "P2" || joeLabel === "P3";
            return {
                text: joeLabel,
                color: joeLabel === "P1" ? COLORS.p1
                    : joeLabel === "P2" ? COLORS.p2
                        : joeLabel === "P3" ? COLORS.p3 : COLORS.tte,
                shape: type === "TTE_TRIGGERED" ? (side === "SHORT" ? "arrowDown" : "arrowUp") : "circle",
                position: isPoint || side === "SHORT" ? "aboveBar" : "belowBar"
            };
        }
        if (type === "ENTRY") {
            return {
                text: side === "SHORT" ? "SHORT ENTRY" : "LONG ENTRY",
                color: COLORS.entry,
                shape: side === "SHORT" ? "arrowDown" : "arrowUp",
                position: side === "SHORT" ? "aboveBar" : "belowBar"
            };
        }
        if (type.startsWith("EXIT - TP") || type === "TP1_FILLED") {
            return {
                text: type === "TP1_FILLED" ? "TP1 FILLED"
                    : type.replace("EXIT - ", "") + " FILLED",
                color: COLORS.takeProfit,
                shape: "square",
                position: side === "SHORT" ? "belowBar" : "aboveBar"
            };
        }
        if (type.startsWith("EXIT")) {
            const reason = type.replace("EXIT - ", "").replaceAll("_", " ");
            return {
                text: `${reason || "EXIT"} EXIT`,
                color: type.includes("STOP") ? COLORS.stop : COLORS.exit,
                shape: "arrowDown",
                position: side === "SHORT" ? "belowBar" : "aboveBar"
            };
        }
        if (type === "INITIAL STOP") {
            return {
                text: "INITIAL STOP",
                color: COLORS.stop,
                shape: "circle",
                position: side === "SHORT" ? "aboveBar" : "belowBar"
            };
        }
        if (type === "STOP MOVED") {
            return {
                text: "STOP MOVE",
                color: COLORS.stop,
                shape: "square",
                position: side === "SHORT" ? "aboveBar" : "belowBar"
            };
        }
        if (debugEvent) {
            return {
                text: type.replaceAll("_", " "),
                color: COLORS.debug,
                shape: "circle",
                position: "aboveBar"
            };
        }
        return null;
    }

    function buildMarkers(simulation, trade, filters, bars) {
        const resolvedFilters = { ...DEFAULT_FILTERS, ...(filters || {}) };
        const events = [];
        if (trade) {
            (trade.events || []).forEach((event) => events.push({ event, trade, debug: false }));
        }
        if (resolvedFilters.strategyDebug) {
            (simulation?.joeRossEvents || []).forEach((event) =>
                events.push({ event, trade: null, debug: true }));
        }
        const seen = new Set();
        return events.flatMap((item, sequence) => {
            const type = eventType(item.event);
            const category = eventCategory(type);
            if ((category === "debug" && !resolvedFilters.strategyDebug)
                || (category !== "debug" && !resolvedFilters[category])) {
                return [];
            }
            const descriptor = markerDescriptor(item.event, item.trade, item.debug);
            const time = eventChartTime(item.event, bars);
            if (!descriptor || time == null) {
                return [];
            }
            const key = `${type}|${time}|${finiteNumber(item.event.price) ?? ""}`;
            if (seen.has(key)) {
                return [];
            }
            seen.add(key);
            return [{
                ...descriptor,
                time,
                id: `chart-event-${sequence}-${type}-${time}`,
                size: descriptor.text.length > 10 ? 0.75 : 1,
                event: item.event,
                trade: item.trade,
                debug: item.debug
            }];
        }).sort((left, right) => left.time - right.time);
    }

    function buildStopStepLine(trade, bars) {
        if (!trade || !bars?.source?.length) {
            return [];
        }
        const initial = finiteNumber(trade.initialStopPrice);
        const entryTime = eventChartTime({ index: trade.entryIndex, occurredAt: trade.entryAt }, bars);
        if (initial == null || entryTime == null) {
            return [];
        }
        const steps = [{ time: entryTime, value: initial }];
        (trade.events || [])
            .filter((event) => eventType(event) === "STOP MOVED"
                && finiteNumber(event.currentStopPrice ?? event.price) != null)
            .sort((left, right) => {
                const leftTime = eventChartTime(left, bars) ?? 0;
                const rightTime = eventChartTime(right, bars) ?? 0;
                return leftTime - rightTime;
            })
            .forEach((event) => {
                const time = eventChartTime(event, bars);
                const value = finiteNumber(event.currentStopPrice ?? event.price);
                if (time == null || value == null || time < entryTime) {
                    return;
                }
                const last = steps.at(-1);
                if (last.time === time) {
                    last.value = value;
                } else {
                    steps.push({ time, value });
                }
            });
        const exitTime = eventChartTime({ index: trade.exitIndex, occurredAt: trade.exitAt }, bars)
            ?? bars.source.at(-1).time;
        if (exitTime > steps.at(-1).time) {
            steps.push({ time: exitTime, value: steps.at(-1).value });
        }
        return steps;
    }

    function finiteSegment(value, startTime, endTime) {
        const numeric = finiteNumber(value);
        if (numeric == null || startTime == null || endTime == null) {
            return [];
        }
        return startTime === endTime
            ? [{ time: startTime, value: numeric }]
            : [{ time: startTime, value: numeric }, { time: endTime, value: numeric }];
    }

    function buildTteEntryLevelLine(trade, bars) {
        if (!trade || !bars?.source?.length) {
            return [];
        }
        const strategyData = trade.strategyData || {};
        const point3Index = finiteNumber(strategyData.point3BarIndex);
        const entryIndex = finiteNumber(trade.entryIndex);
        const level = finiteNumber(strategyData.tteEntryCandidate);
        if (point3Index == null || entryIndex == null || level == null
            || entryIndex <= point3Index) {
            return [];
        }
        const start = eventChartTime({
            index: point3Index,
            occurredAt: strategyData.point3Time
        }, bars);
        const end = eventChartTime({
            index: entryIndex,
            occurredAt: trade.entryAt
        }, bars);
        return finiteSegment(level, start, end);
    }

    function isCandlestickStrategy(strategy) {
        return strategy === "PATTERN_V1" || strategy === "PATTERN_V2";
    }

    function takeProfitStatus(trade, target) {
        if (isCandlestickStrategy(trade?.strategy)) {
            return (trade.events || []).some(event => eventType(event) === "TAKE_PROFIT_FILLED") ? "FILLED" : "NOT FILLED";
        }
        const number = finiteNumber(target?.targetNumber);
        const matching = (trade?.events || []).filter((event) =>
            eventType(event).includes(`TP${number}`));
        if (matching.some((event) => eventType(event).includes("PARTIAL")
            || String(event.details || "").toUpperCase().includes("PARTIAL"))) {
            return "PARTIAL";
        }
        return matching.some((event) => eventType(event).startsWith("EXIT")
            || eventType(event).endsWith("_FILLED")) ? "FILLED" : "NOT FILLED";
    }

    function currentStopAt(trade, barIndex) {
        let stop = finiteNumber(trade?.initialStopPrice);
        (trade?.events || [])
            .filter((event) => finiteNumber(event.index) != null
                && finiteNumber(event.index) <= barIndex
                && finiteNumber(event.currentStopPrice) != null)
            .sort((left, right) => Number(left.index) - Number(right.index))
            .forEach((event) => { stop = Number(event.currentStopPrice); });
        return stop;
    }

    function navigateTrade(trades, selectedTradeId, direction) {
        const list = trades || [];
        if (!list.length) {
            return null;
        }
        const selected = list.findIndex((trade) => formatTradeId(trade) === selectedTradeId);
        const start = selected < 0 ? 0 : selected;
        const next = Math.max(0, Math.min(list.length - 1, start + direction));
        return formatTradeId(list[next]);
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }

    function formatNumber(value, digits = 2) {
        const numeric = finiteNumber(value);
        return numeric == null ? "-" : numeric.toLocaleString("it-IT", {
            minimumFractionDigits: digits,
            maximumFractionDigits: digits
        });
    }

    function formatInteger(value) {
        const numeric = finiteNumber(value);
        return numeric == null ? "-" : Math.round(numeric).toLocaleString("it-IT");
    }

    function formatDateTime(time) {
        const seconds = toChartTime(time);
        if (seconds == null) {
            return "-";
        }
        return new Date(seconds * 1000).toLocaleString("it-IT", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit"
        });
    }

    class TradingChartController {
        constructor(options) {
            this.host = options.host;
            this.tooltip = options.tooltip;
            this.infoPanel = options.infoPanel;
            this.timeline = options.timeline;
            this.eventDetail = options.eventDetail;
            this.onTradeChange = options.onTradeChange;
            this.simulation = null;
            this.selectedTradeId = null;
            this.selectedTrade = null;
            this.filters = { ...DEFAULT_FILTERS };
            this.bars = normaliseBars([]);
            this.bollingerBands = calculateBollingerBands([]);
            this.bollingerByTime = new Map();
            this.markerDetails = new Map();
            this.overlaySeries = [];
            this.initialized = false;
            this.resizeObserver = null;
            this.themeObserver = null;
            this.boundDoubleClick = () => this.chart?.timeScale().fitContent();
            this.boundCrosshairMove = (param) => this.handleCrosshair(param);
            this.boundChartClick = (param) => this.handleChartClick(param);
        }

        ensureChart() {
            if (this.initialized) {
                return true;
            }
            const library = globalThis.LightweightCharts;
            if (!library || !this.host) {
                if (this.host) {
                    this.host.innerHTML = '<p class="chart-load-error">Impossibile caricare Lightweight Charts.</p>';
                }
                return false;
            }
            this.library = library;
            this.chart = library.createChart(this.host, this.chartOptions());
            this.priceSeries = this.chart.addSeries(library.BarSeries, {
                upColor: COLORS.bull,
                downColor: COLORS.bear,
                openVisible: true,
                thinBars: false,
                priceLineVisible: false,
                lastValueVisible: true
            }, 0);
            this.volumeSeries = this.chart.addSeries(library.HistogramSeries, {
                priceFormat: { type: "volume" },
                priceScaleId: "volume",
                lastValueVisible: false,
                priceLineVisible: false
            }, 1);
            const panes = this.chart.panes();
            panes[0]?.setStretchFactor(4);
            panes[1]?.setStretchFactor(1);
            this.markerPlugin = library.createSeriesMarkers(this.priceSeries, []);
            this.chart.subscribeCrosshairMove(this.boundCrosshairMove);
            this.chart.subscribeClick(this.boundChartClick);
            this.host.addEventListener("dblclick", this.boundDoubleClick);
            this.resizeObserver = new ResizeObserver((entries) => {
                const rectangle = entries[0]?.contentRect;
                if (!rectangle || rectangle.width <= 0) {
                    return;
                }
                this.chart.applyOptions({
                    width: Math.floor(rectangle.width),
                    height: this.chartHeight()
                });
            });
            this.resizeObserver.observe(this.host);
            this.themeObserver = new MutationObserver(() => this.applyTheme());
            this.themeObserver.observe(document.documentElement, {
                attributes: true,
                attributeFilter: ["class", "data-theme"]
            });
            this.themeObserver.observe(document.body, {
                attributes: true,
                attributeFilter: ["class", "data-theme"]
            });
            this.initialized = true;
            return true;
        }

        chartHeight() {
            return window.matchMedia("(max-width: 680px)").matches ? 440 : 590;
        }

        themeColors() {
            const styles = getComputedStyle(document.documentElement);
            return {
                surface: styles.getPropertyValue("--surface").trim() || "#ffffff",
                text: styles.getPropertyValue("--text").trim() || "#172126",
                muted: styles.getPropertyValue("--muted").trim() || "#627077",
                border: styles.getPropertyValue("--border").trim() || "#d6dee0"
            };
        }

        chartOptions() {
            const library = globalThis.LightweightCharts;
            const colors = this.themeColors();
            return {
                width: Math.max(320, this.host?.clientWidth || 320),
                height: this.chartHeight(),
                layout: {
                    attributionLogo: true,
                    background: { type: library.ColorType.Solid, color: colors.surface },
                    textColor: colors.muted,
                    panes: {
                        separatorColor: colors.border,
                        separatorHoverColor: COLORS.entry,
                        enableResize: true
                    }
                },
                grid: {
                    vertLines: { color: colors.border + "88" },
                    horzLines: { color: colors.border + "88" }
                },
                crosshair: {
                    mode: library.CrosshairMode.Normal,
                    vertLine: { color: "#64748b", labelBackgroundColor: "#334155" },
                    horzLine: { color: "#64748b", labelBackgroundColor: "#334155" }
                },
                handleScroll: {
                    mouseWheel: true,
                    pressedMouseMove: true,
                    horzTouchDrag: true,
                    vertTouchDrag: false
                },
                handleScale: {
                    axisPressedMouseMove: true,
                    mouseWheel: true,
                    pinch: true
                },
                rightPriceScale: { borderColor: colors.border },
                timeScale: {
                    borderColor: colors.border,
                    timeVisible: true,
                    secondsVisible: false,
                    rightOffset: 4,
                    barSpacing: 8,
                    minBarSpacing: 0.8
                },
                localization: {
                    locale: "it-IT",
                    timeFormatter: (time) => formatDateTime(time)
                }
            };
        }

        applyTheme() {
            if (!this.chart) {
                return;
            }
            const colors = this.themeColors();
            this.chart.applyOptions({
                layout: {
                    background: { type: this.library.ColorType.Solid, color: colors.surface },
                    textColor: colors.muted,
                    panes: { separatorColor: colors.border }
                },
                grid: {
                    vertLines: { color: colors.border + "88" },
                    horzLines: { color: colors.border + "88" }
                },
                rightPriceScale: { borderColor: colors.border },
                timeScale: { borderColor: colors.border }
            });
        }

        update({ simulation, selectedTradeId, filters, timeframe, autoFit = false }) {
            if (!simulation || !this.ensureChart()) {
                return;
            }
            const dataChanged = simulation !== this.simulation;
            const selectionChanged = selectedTradeId !== this.selectedTradeId;
            this.simulation = simulation;
            this.selectedTradeId = selectedTradeId;
            this.filters = { ...DEFAULT_FILTERS, ...(filters || {}) };
            this.selectedTrade = (simulation.trades || [])
                .find((trade) => formatTradeId(trade) === selectedTradeId) || null;

            this.chart.applyOptions({
                timeScale: {
                    timeVisible: String(timeframe || "DAILY").toUpperCase() !== "DAILY",
                    secondsVisible: false
                }
            });
            if (dataChanged) {
                this.bars = normaliseBars(simulation.priceSeries);
                const bollingerConfig = simulation.bollingerPoint1Filter || {};
                const period = finiteNumber(bollingerConfig.period) ?? 20;
                const multiplier = finiteNumber(
                    bollingerConfig.standardDeviationMultiplier) ?? 2;
                const backendBands = (simulation.bollingerBands || [])
                    .map((value) => ({
                        time: toChartTime(value.timestamp),
                        upper: finiteNumber(value.upper),
                        middle: finiteNumber(value.middle),
                        lower: finiteNumber(value.lower)
                    }))
                    .filter((value) => value.time != null && value.upper != null
                        && value.middle != null && value.lower != null);
                this.bollingerBands = backendBands.length
                    ? {
                        upper: backendBands.map(({ time, upper }) => ({ time, value: upper })),
                        middle: backendBands.map(({ time, middle }) => ({ time, value: middle })),
                        lower: backendBands.map(({ time, lower }) => ({ time, value: lower }))
                    }
                    : calculateBollingerBands(this.bars.source, period, multiplier);
                this.bollingerPeriod = period;
                this.bollingerMultiplier = multiplier;
                this.bollingerByTime = new Map(this.bollingerBands.middle.map((point, index) => [
                    point.time,
                    {
                        upper: this.bollingerBands.upper[index].value,
                        middle: point.value,
                        lower: this.bollingerBands.lower[index].value
                    }
                ]));
                this.bollingerChecksByIndex = new Map();
                (simulation.bollingerPoint1Checks || []).forEach((check) => {
                    const index = finiteNumber(check.barIndex);
                    if (index == null) {
                        return;
                    }
                    const checks = this.bollingerChecksByIndex.get(index) || [];
                    checks.push(check);
                    this.bollingerChecksByIndex.set(index, checks);
                });
                this.priceSeries.setData(this.bars.priceBars);
                this.volumeSeries.setData(this.bars.volumes);
            }
            this.volumeSeries.applyOptions({ visible: this.filters.volume });
            if (isCandlestickStrategy(simulation.strategy)) {
                const start = this.selectedTrade?.patternStartIndex;
                const end = this.selectedTrade?.signalIndex;
                this.priceSeries.setData(this.bars.source.map(bar => ({
                    time:bar.time,open:bar.open,high:bar.high,low:bar.low,close:bar.close,
                    ...(bar.index >= start && bar.index <= end ? {color:COLORS.p1,wickColor:COLORS.p1,borderColor:COLORS.p1} : {})
                })));
            }
            this.updateMarkers();
            this.updateOverlays();
            this.renderInfoPanel();
            this.renderTimeline();
            if ((dataChanged || selectionChanged || autoFit) && this.selectedTrade) {
                this.fitTrade();
            } else if (dataChanged) {
                this.chart.timeScale().fitContent();
            }
        }

        updateMarkers() {
            const markers = buildMarkers(
                this.simulation, this.selectedTrade, this.filters, this.bars);
            this.markerDetails.clear();
            markers.forEach((marker) => this.markerDetails.set(marker.id, marker));
            this.markerPlugin.setMarkers(markers.map(({ event, trade, debug, ...marker }) => marker));
        }

        clearOverlays() {
            this.overlaySeries.forEach((series) => this.chart.removeSeries(series));
            this.overlaySeries = [];
        }

        addLine(data, options) {
            if (!data.length) {
                return;
            }
            const series = this.chart.addSeries(this.library.LineSeries, {
                priceLineVisible: false,
                lastValueVisible: false,
                crosshairMarkerVisible: false,
                lineWidth: 2,
                ...options
            }, 0);
            series.setData(data);
            this.overlaySeries.push(series);
        }

        updateOverlays() {
            this.clearOverlays();
            if (this.filters.indicators
                && this.simulation?.strategy === "JOE_ROSS_123_TTE") {
                this.addLine(this.bollingerBands.upper, {
                    color: COLORS.bollingerUpper,
                    lineStyle: this.library.LineStyle.Dashed,
                    lineWidth: 1,
                    title: `BB Upper (${this.bollingerPeriod}, ${this.bollingerMultiplier})`
                });
                this.addLine(this.bollingerBands.middle, {
                    color: COLORS.bollingerMiddle,
                    lineStyle: this.library.LineStyle.Dotted,
                    lineWidth: 1,
                    title: `BB Media (${this.bollingerPeriod})`
                });
                this.addLine(this.bollingerBands.lower, {
                    color: COLORS.bollingerLower,
                    lineStyle: this.library.LineStyle.Dashed,
                    lineWidth: 1,
                    title: `BB Lower (${this.bollingerPeriod}, ${this.bollingerMultiplier})`
                });
            }
            const trade = this.selectedTrade;
            if (!trade) {
                return;
            }
            const start = eventChartTime({ index: trade.entryIndex, occurredAt: trade.entryAt }, this.bars);
            const end = eventChartTime({ index: trade.exitIndex, occurredAt: trade.exitAt }, this.bars)
                ?? this.bars.source.at(-1)?.time;
            if (this.filters.entries) {
                this.addLine(buildTteEntryLevelLine(trade, this.bars), {
                    color: COLORS.tte,
                    lineStyle: this.library.LineStyle.Dotted,
                    lineWidth: 1,
                    title: "TTE ENTRY LEVEL"
                });
                this.addLine(finiteSegment(trade.entryPrice, start, end), {
                    color: COLORS.entry,
                    lineStyle: this.library.LineStyle.Dashed,
                    title: "ENTRY"
                });
            }
            if (this.filters.stops) {
                this.addLine(buildStopStepLine(trade, this.bars), {
                    color: COLORS.stop,
                    lineType: this.library.LineType.WithSteps,
                    title: "STOP"
                });
            }
            if (this.filters.takeProfits) {
                (trade.takeProfits || []).forEach((target) => {
                    const number = finiteNumber(target.targetNumber) ?? 0;
                    this.addLine(finiteSegment(target.price, start, end), {
                        color: COLORS.takeProfit,
                        lineStyle: number === 1
                            ? this.library.LineStyle.Solid
                            : this.library.LineStyle.Dotted,
                        lineWidth: 1,
                        title: `TP${number}`
                    });
                });
            }
        }

        fitTrade() {
            if (!this.selectedTrade || !this.bars.source.length) {
                return;
            }
            const entryPosition = this.bars.source.findIndex((bar) =>
                bar.index === finiteNumber(this.selectedTrade.entryIndex));
            const exitPosition = this.bars.source.findIndex((bar) =>
                bar.index === finiteNumber(this.selectedTrade.exitIndex));
            const from = Math.max(0, (entryPosition < 0 ? 0 : entryPosition) - 30);
            const to = Math.min(
                this.bars.source.length - 1,
                (exitPosition < 0 ? this.bars.source.length - 1 : exitPosition) + 10);
            this.chart.timeScale().setVisibleLogicalRange({ from, to });
        }

        setRange(range) {
            if (!this.chart || !this.bars.source.length) {
                return;
            }
            if (range === "TRADE") {
                this.fitTrade();
                return;
            }
            if (range === "ALL") {
                this.chart.timeScale().fitContent();
                return;
            }
            const months = { "1M": 1, "3M": 3, "6M": 6, "1Y": 12 }[range];
            if (!months) {
                return;
            }
            const last = this.bars.source.at(-1).time;
            const cutoff = new Date(last * 1000);
            cutoff.setUTCMonth(cutoff.getUTCMonth() - months);
            const firstPosition = Math.max(0, this.bars.source.findIndex((bar) =>
                bar.time >= Math.floor(cutoff.getTime() / 1000)));
            this.chart.timeScale().setVisibleLogicalRange({
                from: firstPosition,
                to: this.bars.source.length - 1
            });
        }

        focusIndex(index) {
            const position = this.bars.source.findIndex((bar) => bar.index === finiteNumber(index));
            if (position < 0) {
                return;
            }
            this.chart.timeScale().setVisibleLogicalRange({
                from: Math.max(0, position - 12),
                to: Math.min(this.bars.source.length - 1, position + 12)
            });
        }

        handleCrosshair(param) {
            if (!this.tooltip || !param?.time || !param.point
                || param.point.x < 0 || param.point.y < 0) {
                this.hideTooltip();
                return;
            }
            const bar = this.bars.byTime.get(Number(param.time));
            const priceBar = param.seriesData?.get(this.priceSeries);
            if (!bar || !priceBar) {
                this.hideTooltip();
                return;
            }
            const trade = this.selectedTrade;
            const inTrade = trade && bar.index >= Number(trade.entryIndex)
                && bar.index <= Number(trade.exitIndex);
            const events = trade ? (trade.events || []).filter((event) =>
                finiteNumber(event.index) === bar.index) : [];
            const lastEvent = events.at(-1);
            const currentQuantity = lastEvent?.remainingQuantity ?? trade?.quantity;
            const stop = inTrade ? currentStopAt(trade, bar.index) : null;
            const takeProfits = inTrade ? (trade.takeProfits || []) : [];
            const realizedPnl = inTrade ? (trade.events || [])
                .filter((event) => finiteNumber(event.index) <= bar.index
                    && (eventType(event).startsWith("EXIT")
                        || eventType(event).endsWith("_FILLED")))
                .reduce((sum, event) => sum + (finiteNumber(event.netPnl) ?? 0), 0) : 0;
            const unrealizedPnl = finiteNumber(lastEvent?.unrealizedPnl);
            const eventDescriptions = events.map((event) => {
                const quantity = finiteNumber(event.quantity);
                const remaining = finiteNumber(event.remainingQuantity);
                const total = quantity != null && remaining != null ? quantity + remaining : null;
                return `${escapeHtml(event.eventType)}${quantity != null
                    ? ` · Qty ${formatInteger(quantity)}${total != null ? `/${formatInteger(total)}` : ""}`
                    : ""}`;
            });
            const bollinger = this.filters.indicators
                ? this.bollingerByTime.get(bar.time) || null
                : null;
            const point1Checks = this.bollingerChecksByIndex?.get(bar.index) || [];
            const strategyData = trade?.strategyData || {};
            const isPoint3Bar = Number(strategyData.point3BarIndex) === bar.index;
            const isPoint2Bar = Number(strategyData.point2BarIndex) === bar.index;
            this.tooltip.innerHTML = `
                <strong>${escapeHtml(this.simulation.symbol || "")}</strong>
                <span>${escapeHtml(formatDateTime(bar.time))}</span>
                <div class="chart-tooltip-ohlc">
                    <span>O <b>${formatNumber(bar.open)}</b></span>
                    <span>H <b>${formatNumber(bar.high)}</b></span>
                    <span>L <b>${formatNumber(bar.low)}</b></span>
                    <span>C <b>${formatNumber(bar.close)}</b></span>
                </div>
                <span>Volume <b>${formatInteger(bar.volume)}</b></span>
                ${bollinger ? `
                    <hr>
                    <span>BB superiore <b>${formatNumber(bollinger.upper)}</b></span>
                    <span>BB media (${formatInteger(this.bollingerPeriod)}) <b>${formatNumber(bollinger.middle)}</b></span>
                    <span>BB inferiore <b>${formatNumber(bollinger.lower)}</b></span>
                ` : ""}
                ${point1Checks.map((check) => `
                    <hr>
                    <span>P1 Price (${escapeHtml(check.direction)}) <b>${formatNumber(check.point1Price)}</b></span>
                    <span>Lower BB <b>${formatNumber(check.bollingerLower)}</b></span>
                    <span>Middle BB <b>${formatNumber(check.bollingerMiddle)}</b></span>
                    <span>Upper BB <b>${formatNumber(check.bollingerUpper)}</b></span>
                    <span>BB Point1 Filter <b>${check.bollingerPoint1Valid ? "VALID" : "REJECTED"}</b></span>
                    ${check.reason ? `<span>Reason <b>${escapeHtml(check.reason)}</b></span>` : ""}
                `).join("")}
                ${isPoint3Bar ? `
                    <hr>
                    <span>P3 Price <b>${formatNumber(strategyData.point3Price)}</b></span>
                    <span>P3 High <b>${formatNumber(strategyData.point3High)}</b></span>
                    <span>P3 Low <b>${formatNumber(strategyData.point3Low)}</b></span>
                    <span>Delta P3 â†’ P2 <b>${formatNumber(strategyData.delta)}</b></span>
                    <span>TTE Entry Level <b>${formatNumber(strategyData.tteEntryCandidate)}</b></span>
                ` : ""}
                ${isPoint2Bar ? `
                    <hr>
                    <span>Point 2 <b>${formatNumber(strategyData.point2Price)}</b></span>
                    <span>TP1 <b>${formatNumber(strategyData.point2Price)}</b></span>
                    <span>Reason <b>JOE_ROSS_POINT2_TARGET</b></span>
                ` : ""}
                ${inTrade ? `
                    <hr>
                    <span>Trade <b>${formatTradeId(trade)} · ${escapeHtml(trade.direction || trade.side)}</b></span>
                    <span>Quantità <b>${formatInteger(currentQuantity)}</b></span>
                    <span>Entry <b>${formatNumber(trade.entryPrice)}</b></span>
                    <span>Stop corrente <b>${formatNumber(stop)}</b></span>
                    ${takeProfits.map((target) => `<span>TP${target.targetNumber} <b>${formatNumber(target.price)}</b></span>`).join("")}
                    <span>PnL realizzato <b>${formatNumber(realizedPnl)}</b></span>
                    <span>PnL non realizzato <b>${unrealizedPnl == null ? "n/d dal backend" : formatNumber(unrealizedPnl)}</b></span>
                    ${eventDescriptions.length ? `<span>Evento <b>${eventDescriptions.join("<br>")}</b></span>` : ""}
                ` : ""}`;
            this.tooltip.hidden = false;
            const width = this.tooltip.offsetWidth || 280;
            const height = this.tooltip.offsetHeight || 220;
            const hostWidth = this.host.clientWidth;
            const hostHeight = this.host.clientHeight;
            const preferRight = param.point.x + width + 28 < hostWidth;
            this.tooltip.style.left = `${preferRight
                ? param.point.x + 18
                : Math.max(8, param.point.x - width - 18)}px`;
            this.tooltip.style.top = `${Math.max(8, Math.min(hostHeight - height - 8, param.point.y - 28))}px`;
        }

        hideTooltip() {
            if (this.tooltip) {
                this.tooltip.hidden = true;
            }
        }

        handleChartClick(param) {
            const marker = param?.hoveredObjectId
                ? this.markerDetails.get(param.hoveredObjectId) : null;
            if (marker) {
                this.renderEventDetail(marker.event, marker.trade);
                return;
            }
            const time = finiteNumber(param?.time);
            if (time == null || !this.selectedTrade) {
                return;
            }
            const bar = this.bars.byTime.get(time);
            const events = (this.selectedTrade.events || []).filter((event) =>
                finiteNumber(event.index) === bar?.index);
            if (events.length) {
                this.renderEventDetail(events.at(-1), this.selectedTrade);
            }
        }

        renderEventDetail(event, trade) {
            if (!this.eventDetail) {
                return;
            }
            const type = eventType(event);
            const strategyData = trade?.strategyData || {};
            this.eventDetail.hidden = false;
            this.eventDetail.innerHTML = `
                <div><span>Evento</span><strong>${escapeHtml(type.replaceAll("_", " "))}</strong></div>
                <div><span>Data/Ora</span><strong>${escapeHtml(formatDateTime(event.occurredAt ?? event.timestamp))}</strong></div>
                <div><span>Prezzo</span><strong>${formatNumber(event.price)}</strong></div>
                ${event.quantity != null ? `<div><span>Quantità</span><strong>${formatInteger(event.quantity)}</strong></div>` : ""}
                ${event.details ? `<div><span>Dettagli</span><strong>${escapeHtml(event.details)}</strong></div>` : ""}
                ${type.includes("TTE") && strategyData.tteBarNumber ? `
                    <div><span>TTE bar</span><strong>${escapeHtml(strategyData.tteBarNumber)}</strong></div>
                    <div><span>Candidate</span><strong>${formatNumber(strategyData.tteEntryCandidate)}</strong></div>
                    <div><span>Stop iniziale</span><strong>${formatNumber(strategyData.initialStopPrice)}</strong></div>
                ` : ""}
                ${type.includes("POINT2") || type === "TP1_FILLED" ? `
                    <div><span>Point 2</span><strong>${formatNumber(strategyData.point2Price)}</strong></div>
                    <div><span>TP1</span><strong>${formatNumber(strategyData.point2Price)}</strong></div>
                    <div><span>Reason</span><strong>JOE_ROSS_POINT2_TARGET</strong></div>
                ` : ""}`;
        }

        renderInfoPanel() {
            if (!this.infoPanel) {
                return;
            }
            const trade = this.selectedTrade;
            if (!trade) {
                this.infoPanel.innerHTML = '<p class="chart-empty-copy">Seleziona un trade per visualizzarne i dettagli.</p>';
                return;
            }
            const isPatternStrategy = isCandlestickStrategy(this.simulation.strategy);
            const displayedPnl = isPatternStrategy && !trade.closed ? trade.netUnrealizedPnl : trade.netPnl;
            const patternDetails = isPatternStrategy ? `
                <div class="trade-info-value"><span>Desired / Final Qty</span><strong>${formatInteger(trade.desiredQuantity)} / ${formatInteger(trade.finalQuantity)}</strong></div>
                <div class="trade-info-value"><span>Quantity limits</span><strong>${escapeHtml((trade.quantityLimitingReasons || []).join(", ") || "NONE")}</strong></div>
                <div class="trade-info-value"><span>Target / Actual Risk %</span><strong>${formatNumber(trade.desiredRiskPercent)}% / ${formatNumber(trade.actualRiskPercent)}%</strong></div>
                <div class="trade-info-value"><span>Order Value</span><strong>${formatNumber(trade.orderValue)}</strong></div>
                <div class="trade-info-value"><span>Raw Bar MFE / MAE</span><strong>${formatNumber(trade.rawBarMfeR)}R / ${formatNumber(trade.rawBarMaeR)}R</strong></div>
                ${!trade.closed ? `<div class="trade-info-value"><span>Mark (OPEN)</span><strong>${escapeHtml(trade.markDate)} · ${formatNumber(trade.markPrice)}</strong></div>` : ""}` : "";
            const rMultiple = isPatternStrategy ? trade.resultR
                : finiteNumber(trade.effectiveRisk) ? Number(trade.netPnl) / Number(trade.effectiveRisk) : null;
            const targets = (trade.takeProfits || []).map((target) => `
                <div class="trade-info-value">
                    <span>${isPatternStrategy ? "Take Profit" : `TP${target.targetNumber}`}</span>
                    <strong>${formatNumber(target.price)}</strong>
                    <em class="tp-status ${takeProfitStatus(trade, target).toLowerCase().replace(" ", "-")}">${takeProfitStatus(trade, target)}</em>
                </div>`).join("");
            this.infoPanel.innerHTML = `
                <div class="trade-info-heading">
                    <div><span>Trade selezionato</span><h4>${formatTradeId(trade)}</h4></div>
                    <span class="trade-direction ${String(trade.direction || trade.side).toLowerCase()}">${escapeHtml(trade.direction || trade.side)}</span>
                </div>
                <div class="trade-info-grid">
                    <div class="trade-info-value"><span>Simbolo</span><strong>${escapeHtml(this.simulation.symbol)}</strong></div>
                    <div class="trade-info-value"><span>Strategia</span><strong>${escapeHtml(this.simulation.strategy)}</strong></div>
                    <div class="trade-info-value"><span>Entry</span><strong>${formatNumber(trade.entryPrice)}</strong></div>
                    <div class="trade-info-value"><span>Stop iniziale</span><strong>${formatNumber(trade.initialStopPrice)}</strong></div>
                    <div class="trade-info-value"><span>Quantità</span><strong>${formatInteger(trade.quantity)}</strong></div>
                    ${targets}
                    <div class="trade-info-value"><span>Exit finale</span><strong>${formatNumber(trade.exitPrice)} · ${escapeHtml(trade.exitReason)}</strong></div>
                    <div class="trade-info-value"><span>${isPatternStrategy && !trade.closed ? "Net Unrealized PnL" : "Net PnL"}</span><strong class="${Number(displayedPnl) >= 0 ? "positive" : "negative"}">${formatNumber(displayedPnl)}</strong></div>
                    <div class="trade-info-value"><span>Return</span><strong>${formatNumber(trade.returnPercent)}%</strong></div>
                    <div class="trade-info-value"><span>R multiple</span><strong>${isPatternStrategy && rMultiple == null ? "n/d" : formatNumber(rMultiple) + "R"}</strong></div>
                    <div class="trade-info-value"><span>${isPatternStrategy ? "Executed MFE" : "MFE"}</span><strong>${formatNumber(trade.mfe)} · ${formatNumber(trade.mfeR)}R</strong></div>
                    <div class="trade-info-value"><span>${isPatternStrategy ? "Executed MAE" : "MAE"}</span><strong>${formatNumber(trade.mae)} · ${formatNumber(trade.maeR)}R</strong></div>
                    ${patternDetails}
                </div>`;
        }

        renderTimeline() {
            if (!this.timeline) {
                return;
            }
            const trade = this.selectedTrade;
            if (!trade) {
                this.timeline.innerHTML = "";
                return;
            }
            const relevant = (trade.events || []).filter((event) => {
                if (isCandlestickStrategy(this.simulation.strategy)) return true;
                const type = eventType(event);
                return type === "ENTRY" || type.startsWith("EXIT") || type === "TP1_FILLED" || type === "INITIAL STOP"
                    || type === "STOP MOVED" || joeRossLabel(type);
            });
            this.timeline.innerHTML = relevant.map((event, index) => `
                <button type="button" class="timeline-event" data-chart-event-index="${index}"
                    data-bar-index="${escapeHtml(event.index)}">
                    <span>${escapeHtml(joeRossLabel(eventType(event)) || eventType(event).replace("EXIT - ", ""))}</span>
                    <small>${escapeHtml(formatDateTime(event.occurredAt))}</small>
                </button>`).join('<span class="timeline-arrow" aria-hidden="true">→</span>');
            this.timeline.querySelectorAll("[data-bar-index]").forEach((button) => {
                button.addEventListener("click", () => {
                    this.focusIndex(button.dataset.barIndex);
                    this.renderEventDetail(relevant[Number(button.dataset.chartEventIndex)], trade);
                });
            });
        }

        destroy() {
            this.hideTooltip();
            this.resizeObserver?.disconnect();
            this.themeObserver?.disconnect();
            this.host?.removeEventListener("dblclick", this.boundDoubleClick);
            this.chart?.unsubscribeCrosshairMove(this.boundCrosshairMove);
            this.chart?.unsubscribeClick(this.boundChartClick);
            this.chart?.remove();
            this.chart = null;
            this.initialized = false;
        }
    }

    function create(options) {
        return new TradingChartController(options);
    }

    return {
        create,
        DEFAULT_FILTERS,
        normaliseBars,
        calculateBollingerBands,
        buildMarkers,
        buildStopStepLine,
        buildTteEntryLevelLine,
        takeProfitStatus,
        navigateTrade,
        toChartTime,
        formatTradeId
    };
});
