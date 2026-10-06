'use strict';
const $=s=>document.querySelector(s);let protocol='odata',next=null,busy=false;
const filters=$('#filters');
function addFilter(field='Genre',op='eq',value='RareGenre'){
 const row=document.createElement('div');row.className='filter';
 row.innerHTML='<select class="field"><option>Id</option><option>Genre</option><option>Year</option><option>Rating</option><option>Title</option></select><select class="op"><option>eq</option><option>ge</option><option>le</option></select><input class="value" aria-label="Filter value"><button class="remove" aria-label="Remove filter">×</button>';
 row.querySelector('.field').value=field;row.querySelector('.op').value=op;row.querySelector('.value').value=value;
 row.querySelector('.field').onchange=()=>{const numeric=['Year','Rating'].includes(row.querySelector('.field').value);row.querySelector('.op').innerHTML=numeric?'<option>eq</option><option>ge</option><option>le</option>':'<option>eq</option>';build();};
 row.querySelector('.remove').onclick=()=>{row.remove();build();};row.oninput=build;row.onchange=build;filters.append(row);
}
function collect(){return [...filters.children].map(r=>({field:r.querySelector('.field').value,op:r.querySelector('.op').value,value:r.querySelector('.value').value}));}
function build(){
 next=null;$('#next').disabled=true;const top=Number($('#top').value),order=$('#order').value,desc=$('#direction').value==='desc';
 const f=collect();if(protocol==='odata'){
  const p=new URLSearchParams({'$top':String(top),'$orderby':`${order} ${desc?'desc':'asc'}`,'$select':'Id,Title,Year,Rating,Genre'});
  if(f.length)p.set('$filter',f.map(x=>`${x.field} ${x.op} ${['Genre','Title'].includes(x.field)?"'"+x.value.replaceAll("'","''")+"'":x.value}`).join(' and '));
  if($('#director').checked)p.set('$expand','Director($select=Id,Name)');if($('#count').checked)p.set('$count','true');
  $('#query').value='/odata/Movies?'+p.toString();
 }else{
  let filter={};for(const x of f){const key={Id:'id',Genre:'genre',Title:'title',Year:({eq:'year',ge:'yearFrom',le:'yearTo'})[x.op],Rating:({eq:'rating',ge:'ratingMin',le:'ratingMax'})[x.op]}[x.field];filter[key]=['Year','Rating'].includes(x.field)?Number(x.value):x.value;}
  const args=Object.entries(filter).map(([k,v])=>k+': '+JSON.stringify(v)).join(', ');
  $('#query').value=`query Movies {\n  movies(first: ${top}, orderBy: ${order.toUpperCase()}, descending: ${desc}${args?', filter: { '+args+' }':''}) {\n    nodes { id title year rating genre${$('#director').checked?' director { id name }':''} }\n    ${$('#count').checked?'totalCount\n    ':''}pageInfo { hasNextPage endCursor }\n  }\n}`;
 }
}
function esc(s){return String(s??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');}
function renderRows(rows){
 $('#result-table').className='table-wrap';
 if(!rows.length){$('#result-table').innerHTML='<p class="hint">No matching films.</p>';return;}
 const o=protocol==='odata';$('#result-table').innerHTML='<table><thead><tr><th>ID</th><th>FILM</th><th>YEAR</th><th>RATING</th><th>GENRE / DIRECTOR</th></tr></thead><tbody>'+rows.map(m=>{
  const v=k=>m[o?k[0].toUpperCase()+k.slice(1):k];const d=v('director');
  return `<tr><td>${esc(v('id'))}</td><td>${esc(v('title'))}</td><td>${esc(v('year'))}</td><td class="rating">${esc(v('rating'))}</td><td><span class="genre">${esc(v('genre'))}</span>${d?'<br><span class="muted">'+esc(d[o?'Name':'name'])+'</span>':''}</td></tr>`;
 }).join('')+'</tbody></table>';
}
function renderPlans(trace){
 const plans=trace?.plans||[];$('#plan-status').textContent=trace?.mode||$('#policy').value;
 if(!plans.length){$('#plans').innerHTML='<p class="hint">No SELECT plan was captured. OFF skips inspection; validation errors are rejected before SQL.</p>';return;}
 $('#plans').innerHTML=plans.map((p,i)=>`<div class="plan ${p.accepted?'':'reject'}"><strong>${p.accepted?'Policy accepted':'Policy would reject'}</strong><span class="muted"> · statement ${i+1} · cost ${esc(p.cost??'unavailable')}</span>${(p.scans||[]).map(s=>`<div class="scan"><code>${esc(s.type)}</code><span>${esc(s.relation)}</span><span class="muted">${esc(s.index||'No index')}</span></div>`).join('')}${(p.reasons||[]).map(r=>'<p>'+esc(r)+'</p>').join('')}</div>`).join('');
}
async function run(isNext=false){
 if(busy)return;busy=true;$('#run').disabled=true;$('#notice').classList.add('hidden');const start=performance.now();
 try{
  let url=protocol==='odata'?(isNext?next:$('#query').value.trim()):'/graphql';let query=$('#query').value;
  if(protocol==='odata'){
   const parsed=new URL(url,isNext?location.origin+"/odata/":location.origin);if(parsed.origin!==location.origin||!parsed.pathname.startsWith('/odata/'))throw Error('Use an OData path on this server');url=parsed.pathname+parsed.search;
  }else if(isNext){
   if(!/movies\s*\(/.test(query))throw Error('Use the generated query for continuation');query=query.replace(/movies\s*\(/,`movies(after: ${JSON.stringify(next)}, `);
  }
  const res=await fetch(url,{method:protocol==='odata'?'GET':'POST',headers:{'Content-Type':'application/json','X-Index-Policy':$('#policy').value},...(protocol==='graphql'?{body:JSON.stringify({query})}:{})});
  const text=await res.text();let data;try{data=JSON.parse(text);}catch{throw Error(text||'Empty response');}
  $('#response').textContent=JSON.stringify(data,null,2);const id=res.headers.get('X-Request-Id');$('#request-id').textContent=id?'Request '+id.slice(0,8):'';
  const errors=data.errors||[];const message=data.error?.message||errors.map(e=>e.message).join('; ');
  if(message||!res.ok){$('#notice').textContent=message||`HTTP ${res.status}`;$('#notice').classList.remove('hidden');}
  const conn=data.data?.movies;const rows=protocol==='odata'?(data.value||(data.Id?[data]:[])):(conn?.nodes||[]);
  renderRows(rows);next=protocol==='odata'?data['@odata.nextLink']:(conn?.pageInfo?.hasNextPage?conn.pageInfo.endCursor:null);$('#next').disabled=!next;
  $('#metrics').textContent=`${rows.length} rows · ${Math.round(performance.now()-start)} ms · HTTP ${res.status}`;
  if(id){const tr=await fetch('/api/requests/'+encodeURIComponent(id));renderPlans(tr.ok?await tr.json():null);}else renderPlans(data.extensions?.indexPolicy);
 }catch(e){$('#notice').textContent=e.message;$('#notice').classList.remove('hidden');next=null;$('#next').disabled=true;}
 finally{busy=false;$('#run').disabled=false;}
}
$('#run').onclick=()=>run();$('#next').onclick=()=>run(true);$('#add-filter').onclick=()=>{addFilter();build();};
for(const id of ['top','order','direction','director','count'])$('#'+id).onchange=build;
for(const button of document.querySelectorAll('[data-protocol]'))button.onclick=()=>{protocol=button.dataset.protocol;document.querySelectorAll('[data-protocol]').forEach(b=>b.classList.toggle('active',b===button));build();};
function reset(){filters.innerHTML='';addFilter();$('#top').value=100;$('#order').value='Id';$('#direction').value='asc';$('#director').checked=true;$('#count').checked=false;build();}
$('#reset').onclick=reset;
for(const b of document.querySelectorAll('[data-preset]'))b.onclick=()=>{reset();$('#policy').value='ENFORCE';if(b.dataset.preset==='scan'){filters.innerHTML='';addFilter('Title','eq','Inception');}if(b.dataset.preset==='limit')$('#top').value=501;build();};
async function init(){reset();try{const res=await fetch('/api/capabilities');if(!res.ok)throw Error();const caps=await res.json();const l=caps.limits;$('#top').max=l.maxTop;$('#status').textContent=`Java ${caps.runtime?.javaVersion||'unknown'} · PostgreSQL`;const values=[l.defaultTop,l.maxTop,l.pageSize,l.maxExpandDepth,`${l.requestTimeoutMs/1000}s / ${l.dbTimeoutMs/1000}s`,`${l.ratePerMinute}/min`];document.querySelectorAll('#limit-grid strong').forEach((s,i)=>s.textContent=values[i]);if(!l.indexPolicy.allowClientSelection){$('#policy').value=l.indexPolicy.mode;$('#policy').disabled=true;}}catch{$('#status').textContent='API unavailable';}}
init();
