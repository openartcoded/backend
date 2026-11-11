<#--
  Business plan / plan financier / financieel plan (type BUSINESS_PLAN), rendered to a pdf. Bundled FreeMarker version
  of templates/v2/business-plan.html (the default once installed): used when no default BUSINESS_PLAN template exists.
  Model: plan (tech.artcoded.websitev2.pages.businessplan.BusinessPlanReport), every value formatted by the backend.
-->
<#macro chart c>
<section class="card chart">
  <div class="chart-head">
    <div>
      <h3>${c.title?html}</h3>
      <#if c.subtitle?has_content><p class="muted">${c.subtitle?html}</p></#if>
    </div>
    <div class="legend"><#list c.legend as l><span><i style="background: ${l.color}"></i>${l.name?html}</span></#list></div>
  </div>
  <#if c.empty>
  <p class="empty">${plan.t.noData?html}</p>
  <#else>
  <svg viewBox="0 0 ${c.width?c} ${c.height?c}" xmlns="http://www.w3.org/2000/svg" role="img">
    <#list c.gridlines as g>
    <line x1="${c.plotLeft?c}" x2="${c.plotRight?c}" y1="${g.y?c}" y2="${g.y?c}" class="<#if g?is_first>base<#else>grid</#if>"/>
    <text x="${(c.plotLeft - 6)?c}" y="${g.labelY?c}" class="axis" text-anchor="end">${g.label?html}</text>
    </#list>
    <#list c.bars as b>
    <rect x="${b.x?c}" y="${b.y?c}" width="${b.width?c}" height="${b.height?c}" rx="2" fill="${b.color}"><title>${b.title?html}</title></rect>
    <#if b.valueLabel?has_content>
    <text x="${b.labelX?c}" y="${b.labelY?c}" class="value" text-anchor="middle">${b.valueLabel?html}</text>
    </#if>
    </#list>
    <#list c.axisLabels as l>
    <text x="${l.x?c}" y="${l.y?c}" class="axis<#if l.highlighted> highlighted</#if>" text-anchor="middle">${l.text?html}</text>
    </#list>
  </svg>
  </#if>
</section>
</#macro>
<!DOCTYPE html>
<html lang="${plan.lang}">
<head>
  <meta charset="utf-8">
  <title>${plan.title?html} – ${plan.organizationName?html}</title>
  <style>
    @page { size: A4; margin: 13mm 12mm 14mm 12mm; }
    * { box-sizing: border-box; }
    body { margin: 0; font-family: "Inter", "Helvetica Neue", Arial, sans-serif; font-size: 9.5pt; line-height: 1.45; color: #0f172a; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    h1, h2, h3, p { margin: 0; }
    h2 { font-size: 12pt; margin: 18px 0 6px; color: #1e293b; padding-bottom: 3px; border-bottom: 1px solid #e2e8f0; }
    h3 { font-size: 10pt; color: #1e293b; }
    p { margin: 4px 0; }
    .muted { color: #64748b; font-size: 8.5pt; }
    .good { color: #15803d; }
    .bad { color: #b91c1c; }
    .neutral { color: #475569; }

    header { display: flex; justify-content: space-between; align-items: flex-end; padding-bottom: 8px; border-bottom: 2px solid #2563eb; }
    header .org img { display: block; max-height: 34px; max-width: 170px; margin-bottom: 3px; }
    header .org .name { font-size: 12pt; font-weight: 700; }
    header .title { text-align: right; }
    header .title h1 { font-size: 18pt; margin: 1px 0; }

    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 8px; margin-top: 12px; }
    .kpi { border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px 10px; break-inside: avoid; border-top: 3px solid #cbd5e1; }
    .kpi.good { border-top-color: #16a34a; }
    .kpi.bad { border-top-color: #dc2626; }
    .kpi .label { color: #64748b; font-size: 7.5pt; text-transform: uppercase; letter-spacing: .03em; }
    .kpi .value { font-size: 13pt; font-weight: 700; margin: 3px 0 2px; white-space: nowrap; }
    .kpi .detail { font-size: 7.5pt; color: #64748b; }
    .kpi.good .detail { color: #15803d; }
    .kpi.bad .detail { color: #b91c1c; }
    .summary { margin-top: 10px; }

    .company { display: grid; grid-template-columns: 1fr 1fr; gap: 5px 18px; }
    .label { color: #64748b; font-size: 7.5pt; text-transform: uppercase; letter-spacing: .03em; }
    .activity { white-space: pre-wrap; margin-top: 8px; }

    .card { border: 1px solid #e2e8f0; border-radius: 8px; padding: 7px 12px; margin-top: 8px; break-inside: avoid; }
    .chart-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 4px; }
    .legend span { margin-left: 10px; font-size: 8pt; color: #475569; }
    .legend i { display: inline-block; width: 9px; height: 9px; border-radius: 2px; margin-right: 4px; vertical-align: -1px; }
    svg { width: 100%; height: auto; display: block; }
    svg .grid { stroke: #e2e8f0; stroke-width: 1; }
    svg .base { stroke: #94a3b8; stroke-width: 1; }
    svg .axis { font-size: 10px; fill: #64748b; }
    svg .axis.highlighted { font-weight: 700; fill: #0f172a; }
    svg .value { font-size: 9px; fill: #334155; font-weight: 600; }
    .empty { color: #94a3b8; font-style: italic; padding: 12px 0; }

    table { width: 100%; border-collapse: collapse; margin-top: 6px; font-size: 8.5pt; }
    th { text-align: left; font-size: 7.3pt; color: #64748b; text-transform: uppercase; letter-spacing: .02em; border-bottom: 1px solid #cbd5e1; padding: 3px 6px; vertical-align: bottom; }
    td { padding: 3px 6px; border-bottom: 1px solid #f1f5f9; vertical-align: middle; }
    th.num, td.num { text-align: right; white-space: nowrap; }
    tr.partial td, tr.current td { background: #eff6ff; }
    tr.strong td { font-weight: 700; border-top: 1px solid #cbd5e1; }
    tr.highlight td { background: #f0fdf4; }
    .bar { height: 4px; border-radius: 2px; background: #2563eb; margin-top: 2px; min-width: 1px; }
    .note { font-size: 7.8pt; color: #64748b; margin-top: 5px; }
    ul.method { margin: 4px 0 0; padding-left: 16px; }
    ul.method li { margin: 3px 0; }
    .loan { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px 18px; }
    .loan .figure { font-size: 11pt; font-weight: 600; }
    section { break-inside: avoid; }
    footer { margin-top: 16px; color: #94a3b8; font-size: 7.5pt; text-align: center; }
  </style>
</head>
<body>
<header>
  <div class="org">
    <#if plan.logo?has_content><img src="${plan.logo}" alt=""></#if>
    <div class="name">${plan.organizationName?html}</div>
    <#if plan.vatNumber?has_content><div class="muted">${plan.t.vat?html} ${plan.vatNumber?html}</div></#if>
  </div>
  <div class="title">
    <h1>${plan.title?html}</h1>
    <div class="muted">${plan.subtitle?html}</div>
  </div>
</header>

<section>
  <h2>${plan.t.secSummary?html}</h2>
  <#if plan.kpis?has_content>
  <div class="kpis">
    <#list plan.kpis as k>
    <div class="kpi ${k.tone}">
      <div class="label">${k.label?html}</div>
      <div class="value">${k.value?html}</div>
      <div class="detail">${k.detail?html}</div>
    </div>
    </#list>
  </div>
  </#if>
  <div class="summary"><#list plan.summary as s><p>${s?html}</p></#list></div>
</section>

<section>
  <h2>${plan.t.secCompany?html}</h2>
  <div class="company">
    <div><div class="label">${plan.t.name?html}</div>${plan.organizationName?html}</div>
    <div><div class="label">${plan.t.vat?html}</div>${plan.vatNumber?html}</div>
    <div><div class="label">${plan.t.address?html}</div>${plan.address?html}</div>
    <div><div class="label">${plan.t.activeSince?html}</div>${plan.activeSince?html}</div>
    <#if plan.manager?has_content><div><div class="label">${plan.t.manager?html}</div>${plan.manager?html}</div></#if>
    <#if plan.contact?has_content><div><div class="label">${plan.t.contact?html}</div>${plan.contact?html}</div></#if>
  </div>
  <#if plan.activity?has_content>
  <div class="activity"><div class="label">${plan.t.activity?html}</div>${plan.activity?html}</div>
  </#if>
</section>

<section>
  <h2>${plan.t.secHistory?html}</h2>
  <@chart c=plan.yearsChart/>
  <table>
    <thead>
      <tr>
        <th>${plan.t.year?html}</th>
        <th class="num">${plan.t.revenue?html}</th>
        <th class="num">${plan.t.growth?html}</th>
        <th class="num">${plan.t.normalExpenses?html}</th>
        <th class="num">${plan.t.exceptionalExpenses?html}</th>
        <th class="num">${plan.t.result?html}</th>
        <th class="num">${plan.t.margin?html}</th>
      </tr>
    </thead>
    <tbody>
      <#list plan.years as y>
      <tr<#if y.partial> class="partial"</#if>>
        <td>${y.year?html}</td>
        <td class="num"><b>${y.revenue}</b></td>
        <td class="num ${y.growthTone}">${y.growth}</td>
        <td class="num">${y.normalExpenses}</td>
        <td class="num">${y.exceptionalExpenses}</td>
        <td class="num<#if y.negative> bad</#if>"><b>${y.result}</b></td>
        <td class="num">${y.margin}</td>
      </tr>
      </#list>
    </tbody>
  </table>
  <p class="note">${plan.t.amountsNote?html}</p>
</section>

<#if plan.monthYears?has_content>
<section>
  <h2>${plan.t.secMonths?html}</h2>
  <#if plan.monthsExplain?has_content><p>${plan.monthsExplain?html}</p></#if>
  <@chart c=plan.monthsChart/>
  <table>
    <thead>
      <tr>
        <th>${plan.t.month?html}</th>
        <#list plan.monthYears as y><th class="num">${y}</th></#list>
        <th class="num">${plan.t.cumulated?html} ${plan.monthYears?last}</th>
      </tr>
    </thead>
    <tbody>
      <#list plan.months as m>
      <tr<#if m.current> class="current"</#if>>
        <td>${m.month?html}</td>
        <#list m.cells as v><td class="num">${v}</td></#list>
        <td class="num"><b>${m.cumulated}</b></td>
      </tr>
      </#list>
    </tbody>
  </table>
</section>
</#if>

<section>
  <h2>${plan.t.secExpenses?html}<#if plan.expensesYear?has_content> ${plan.expensesYear}</#if></h2>
  <#if plan.expenses?has_content>
  <p>${plan.expensesExplain?html}</p>
  <table>
    <thead>
      <tr>
        <th>${plan.t.category?html}</th>
        <th class="num">${plan.t.amount?html}</th>
        <th class="num">${plan.t.share?html}</th>
        <th class="num">${plan.t.average?html}</th>
      </tr>
    </thead>
    <tbody>
      <#list plan.expenses as e>
      <tr>
        <td>${e.category?html}<div class="bar" style="width: ${e.bar?c}%"></div></td>
        <td class="num">${e.amount}</td>
        <td class="num">${e.share}</td>
        <td class="num">${e.average}</td>
      </tr>
      </#list>
    </tbody>
  </table>
  <#else>
  <p class="empty">${plan.t.noData?html}</p>
  </#if>
</section>

<section>
  <h2>${plan.t.secExceptional?html}</h2>
  <p>${plan.exceptionalExplain?html}</p>
  <#if plan.exceptional?has_content>
  <table>
    <thead>
      <tr>
        <th>${plan.t.date?html}</th>
        <th>${plan.t.description?html}</th>
        <th>${plan.t.category?html}</th>
        <th class="num">${plan.t.amount?html}</th>
        <th>${plan.t.status?html}</th>
      </tr>
    </thead>
    <tbody>
      <#list plan.exceptional as e>
      <tr>
        <td class="num">${e.date}</td>
        <td>${e.description?html}</td>
        <td>${e.category?html}</td>
        <td class="num">${e.amount}</td>
        <td>${e.status?html}</td>
      </tr>
      </#list>
    </tbody>
  </table>
  <#else>
  <p class="muted">${plan.t.noExceptional?html}</p>
  </#if>
</section>

<section>
  <h2>${plan.t.secForecast?html}</h2>
  <#if plan.hasForecast>
  <p class="muted">${plan.t.realisticNote?html}</p>
  <table>
    <thead>
      <tr><th></th><#list plan.forecastYears as y><th class="num">${y}</th></#list></tr>
    </thead>
    <tbody>
      <#list plan.forecast as r>
      <tr<#if r.strong> class="strong"</#if>>
        <td>${r.label?html}</td>
        <#list r.cells as v><td class="num<#if r.negative[v?index]> bad</#if>">${v}</td></#list>
      </tr>
      </#list>
    </tbody>
  </table>
  <#if plan.exceptionalForecast?has_content><p class="note">${plan.exceptionalForecast?html}</p></#if>

  <h3 style="margin-top: 12px">${plan.t.secScenarios?html}</h3>
  <p>${plan.scenariosExplain?html}</p>
  <table>
    <thead>
      <tr><th>${plan.t.scenario?html}</th><#list plan.forecastYears as y><th class="num">${y}</th></#list></tr>
    </thead>
    <tbody>
      <#list plan.scenarios as r>
      <tr<#if r.strong> class="highlight"</#if>>
        <td>${r.label?html}</td>
        <#list r.cells as v><td class="num<#if r.negative[v?index]> bad</#if>">${v}</td></#list>
      </tr>
      </#list>
    </tbody>
  </table>
  <#else>
  <p class="muted">${plan.t.noForecast?html}</p>
  </#if>
</section>

<#if plan.loan??>
<section>
  <h2>${plan.t.secFinancing?html}</h2>
  <div class="loan">
    <div><div class="label">${plan.t.loanAmount?html}</div><div class="figure">${plan.loan.amount}</div></div>
    <div><div class="label">${plan.t.loanDuration?html}</div><div class="figure">${plan.loan.duration?html}</div></div>
    <div><div class="label">${plan.t.loanRate?html}</div><div class="figure">${plan.loan.rate}</div></div>
    <div><div class="label">${plan.t.monthlyPayment?html}</div><div class="figure">${plan.loan.monthlyPayment}</div></div>
    <div><div class="label">${plan.t.totalInterest?html}</div><div class="figure">${plan.loan.totalInterest}</div></div>
  </div>
  <#if plan.loan.purpose?has_content><p><span class="muted">${plan.t.purpose?html} :</span> ${plan.loan.purpose?html}</p></#if>
</section>
</#if>

<section>
  <h2>${plan.t.secMethod?html}</h2>
  <ul class="method"><#list plan.method as m><li>${m?html}</li></#list></ul>
</section>

<footer>${plan.organizationName?html} · ${plan.subtitle?html}</footer>
</body>
</html>
