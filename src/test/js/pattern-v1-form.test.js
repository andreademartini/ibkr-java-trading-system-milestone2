const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

async function submitLimits(orderValue, quantity, model = "IBKR_US_FIXED") {
    const elements = new Map();
    const document = {getElementById(id) {
        if (!elements.has(id)) elements.set(id, {value:"", handlers:{},
            addEventListener(event, handler) { this.handlers[event] = handler; },
            querySelector() { return {}; }});
        return elements.get(id);
    }};
    let sent;
    vm.runInNewContext(fs.readFileSync("src/main/resources/static/pattern-v1.js", "utf8"), {
        document, URLSearchParams, location:{search:""},
        async fetch(url, options) {
            if (options) sent = JSON.parse(options.body);
            return {ok:true, async json() { return options
                ? {schemaVersion:1, settings:{maxOrderValue:6000,maxQuantity:10}}
                : {symbols:{},enabled:true}; }};
        }
    });
    await new Promise(resolve => setImmediate(resolve));
    for (const [id,value] of Object.entries({"v1-start":"2010-01-01", "v1-capital":"10000",
        "v1-commission":"1", "v1-slippage":"0.03", "v1-max-order-value":orderValue,
        "v1-commission-model":model, "v1-commission-per-share":"0.005",
        "v1-commission-minimum":"1.00", "v1-commission-maximum":"1",
        "v1-max-quantity":quantity})) document.getElementById(id).value = value;
    document.getElementById("v1-commission-model").handlers.change();
    await document.getElementById("v1-form").handlers.submit({preventDefault() {}});
    return {sent, applied:document.getElementById("v1-applied-limits").textContent,
        fixedDisabled:document.getElementById("v1-commission").disabled,
        perShareDisabled:document.getElementById("v1-commission-per-share").disabled};
}

test("PATTERN V1 form sends explicit limits and shows limits returned by the server", async () => {
    const {sent,applied,fixedDisabled,perShareDisabled} = await submitLimits("6000", "10");
    assert.equal(sent.maxOrderValue,6000);
    assert.equal(sent.maxQuantity,10);
    assert.equal(sent.slippagePercent,0.03);
    assert.deepEqual(sent.commission,{model:"IBKR_US_FIXED",fixedPerOrder:1,perShare:0.005,minimumPerOrder:1,maximumPercentOrderValue:1});
    assert.equal(fixedDisabled,true);
    assert.equal(perShareDisabled,false);
    assert.match(applied,/maxOrderValue = 6\.?000/);
    assert.match(applied,/maxQuantity = 10/);
});

test("PATTERN V1 fixed commission remains selectable", async () => {
    const {sent,fixedDisabled,perShareDisabled} = await submitLimits("", "", "FIXED_PER_ORDER");
    assert.equal(sent.commission.model,"FIXED_PER_ORDER");
    assert.equal(sent.commission.fixedPerOrder,1);
    assert.equal(fixedDisabled,false);
    assert.equal(perShareDisabled,true);
});

test("PATTERN V1 blank limits preserve server defaults", async () => {
    const {sent} = await submitLimits("", "");
    assert.equal(sent.maxOrderValue,null);
    assert.equal(sent.maxQuantity,null);
});
