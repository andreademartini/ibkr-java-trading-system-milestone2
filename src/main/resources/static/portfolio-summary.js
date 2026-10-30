(function(root){
 'use strict';
 const number=(value,digits)=>typeof value==='number'&&Number.isFinite(value)?value.toLocaleString('it-IT',{useGrouping:'always',minimumFractionDigits:digits,maximumFractionDigits:digits}):'N/A';
 function format(metric){if(metric.value==null||!Number.isFinite(metric.value))return 'N/A';return number(metric.value,metric.format==='count'?0:metric.format==='money'?2:3)+(metric.format==='percent'?'%':'');}
 function text(value){if(value==null)return 'N/A';if(typeof value==='boolean')return value?'Enabled':'Disabled';if(typeof value==='number')return number(value,3);return typeof value==='object'?JSON.stringify(value,null,2):String(value);}
 function clear(host,message='Nessuna run selezionata'){if(!host)return;host.replaceChildren();host.className='portfolio-summary';const p=document.createElement('p');p.textContent=message;p.setAttribute?.('role','status');host.append(p);host._summaryGeneration=(host._summaryGeneration||0)+1;}
 function render(host,run){
  if(!host)return;clear(host);
  const dto=run?.portfolioSummary??(run?.schemaVersion==='PORTFOLIO_SUMMARY/1'?run:null);
  if(!dto){clear(host,'Portfolio Summary non disponibile per questa risposta. Aggiornare il backend di reporting.');return;}
  host.replaceChildren();const title=document.createElement('h2');title.textContent='Portfolio Summary';host.append(title);
  const dates=document.createElement('p');dates.className='portfolio-summary-coverage';dates.textContent=['Requested Start Date','Requested End Date','Effective Start Date','Effective End Date'].map(k=>k+': '+text(dto.context[k])).join(' · ')+'. CAGR calcolato sulla copertura effettiva.';host.append(dates);
  const params=document.createElement('dl');params.className='portfolio-summary-parameters';
  for(const [key,value]of Object.entries(dto.context)){if(key==='Applied Configuration'||key==='Universe'||key==='CAGR Coverage'||/^(Requested|Effective)/.test(key))continue;const dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=key;dd.textContent=text(value);params.append(dt,dd);}host.append(params);
  const grid=document.createElement('div');grid.className='portfolio-summary-grid';
  for(const m of dto.metrics){const card=document.createElement('div');card.className='portfolio-summary-card';(card.dataset??={}).metric=m.key;const label=document.createElement('span'),strong=document.createElement('strong');label.textContent=m.label;strong.textContent=format(m);if(m.reason)card.title=m.reason;card.append(label,strong);grid.append(card);}host.append(grid);
  const details=document.createElement('details'),caption=document.createElement('summary'),pre=document.createElement('pre');caption.textContent='Applied Run Configuration';pre.textContent=text(dto.context['Applied Configuration']);details.append(caption,pre);host.append(details);
 }
 async function load(host,url){clear(host,'Caricamento Portfolio Summary…');const generation=host._summaryGeneration;try{const response=await fetch(url);if(!response.ok)throw Error('HTTP '+response.status);const dto=await response.json();if(host._summaryGeneration===generation)render(host,dto);}catch(e){if(host._summaryGeneration===generation)clear(host,'Portfolio Summary non disponibile: '+e.message);}}
 function compare(host,runs){
  clear(host);host.replaceChildren();const dtos=runs.map(r=>r.portfolioSummary).filter(Boolean);if(dtos.length<2){clear(host,'Riepilogo non disponibile per almeno due run');return;}
  const keys=['Dataset','Requested Start Date','Requested End Date','Effective Start Date','Effective End Date','Universe','Commission Model','Commission Per Share','Commission Minimum Per Order','Commission Maximum Percent Order Value','Slippage Percent Per Execution','Leverage Enabled','Risk Percent','Max Order Value Percent','Max Order Value Mode','Max Quantity','R Convention','Time In Market Convention','Commission Enabled','Slippage Enabled'];
  const differences=keys.filter(k=>dtos.some(d=>d.context[k]==null)||new Set(dtos.map(d=>JSON.stringify(d.context[k]))).size>1);
  const initial=dtos.map(d=>d.metrics.find(m=>m.key==='initialCapital')?.value);if(initial.some(v=>v==null)||new Set(initial).size>1)differences.push('Initial Capital');
  const note=document.createElement('p');note.textContent=differences.length?'Confrontabilità da verificare (differenze o dati mancanti): '+differences.join(', '):'Dataset e principali parametri applicati coincidono.';host.append(note);
  const table=document.createElement('table'),header=document.createElement('tr');for(const caption of ['Metric',...dtos.map(d=>text(d.context['Strategy'])+' · '+text(d.context['Run ID']))]){const th=document.createElement('th');th.textContent=caption;header.append(th);}table.append(header);
  for(const [i,m]of dtos[0].metrics.entries()){const row=document.createElement('tr');for(const value of [m.label,...dtos.map(d=>format(d.metrics[i]))]){const td=document.createElement('td');td.textContent=value;row.append(td);}table.append(row);}host.append(table);
 }
 const api={format,render,clear,load,compare};root.PortfolioSummary=api;if(typeof module!=='undefined')module.exports=api;
 if(typeof document!=='undefined')document.addEventListener?.('portfolio-run-selected',event=>{for(const host of document.querySelectorAll('[data-portfolio-summary]'))render(host,event.detail);});
})(typeof globalThis!=='undefined'?globalThis:this);
