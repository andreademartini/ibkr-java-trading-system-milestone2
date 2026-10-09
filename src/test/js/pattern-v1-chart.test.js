const test = require("node:test");
const assert = require("node:assert/strict");
const chart = require("../../main/resources/static/trading-chart.js");

test("PATTERN V1 marker on completed candle, entry on next open", () => {
    const bars=chart.normaliseBars([{index:0,timestamp:"2020-01-01",open:100,high:101,low:98,close:99},
        {index:1,timestamp:"2020-01-02",open:98.5,high:101,low:98.2,close:100.5},
        {index:2,timestamp:"2020-01-03",open:100,high:107,low:97,close:103}]);
    const trade={pattern:"BULLISH_ENGULFING",direction:"LONG",events:[{eventType:"PATTERN_DETECTED",index:1},
        {eventType:"SIGNAL",index:1},{eventType:"ENTRY",index:2},{eventType:"INITIAL_STOP",index:2},
        {eventType:"TAKE_PROFIT_CREATED",index:2},{eventType:"STOP_FILLED",index:2}]};
    const markers=chart.buildMarkers({strategy:"PATTERN_V1"},trade,{},bars);
    assert.equal(markers.length,6);
    assert.equal(markers.find(m=>m.text==="BULLISH_ENGULFING").time,chart.toChartTime("2020-01-02"));
    assert.equal(markers.find(m=>m.text==="LONG ENTRY").time,chart.toChartTime("2020-01-03"));
    assert.ok(markers.some(m=>m.text==="STOP FILLED"));
});
test("PATTERN V1 take profit markers obey chart filter",()=>{
    const bars=chart.normaliseBars([{index:0,timestamp:"2020-01-01",close:100}]);
    const trade={events:[{eventType:"TAKE_PROFIT_CREATED",index:0},{eventType:"TAKE_PROFIT_FILLED",index:0}]};
    assert.equal(chart.buildMarkers({},trade,{takeProfits:false},bars).length,0);
});
test("PATTERN V1 single target recognizes TAKE_PROFIT_FILLED without partial targets",()=>{
    assert.equal(chart.takeProfitStatus({strategy:"PATTERN_V1",events:[{eventType:"TAKE_PROFIT_FILLED"}]},{targetNumber:1}),"FILLED");
    assert.equal(chart.takeProfitStatus({strategy:"PATTERN_V1",events:[{eventType:"STOP_FILLED"}]},{targetNumber:1}),"NOT FILLED");
});
