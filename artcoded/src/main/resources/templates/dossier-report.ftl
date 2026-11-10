<#--
  Report of a dossier (type DOSSIER_REPORT), rendered to a pdf: one month (the monthly report, report.singleMonth)
  or every month of the dossier. Bundled FreeMarker version of
  templates/v2/dossier-report.html (the default once installed): used when no default DOSSIER_REPORT template exists.
  Model: report (tech.artcoded.websitev2.pages.dossier.report.DossierReport), every value formatted by the backend.
-->
<#macro chart c>
<section class="card chart">
  <div class="chart-head">
    <div>
      <h3>${c.title?html}</h3>
      <p class="muted">${c.subtitle?html}</p>
    </div>
    <div class="legend"><#list c.legend as l><span><i style="background: ${l.color}"></i>${l.name?html}</span></#list></div>
  </div>
  <#if c.empty>
  <p class="empty">Nothing to show yet.</p>
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
<#macro shareTable title subtitle rows emptyText>
<section class="card">
  <h3>${title?html}</h3>
  <p class="muted">${subtitle?html}</p>
  <#if rows?has_content>
  <table class="share">
    <thead>
      <tr><th></th><th class="num">${report.currentColumn}</th><th class="num">${report.previousColumn}</th><th class="num">Evolution</th><th class="num">Share</th></tr>
    </thead>
    <tbody>
      <#list rows as row>
      <tr>
        <td class="name">
          ${row.name?html}
          <div class="bars">
            <div class="bar current" style="width: ${row.currentBar?c}%"></div>
            <div class="bar previous" style="width: ${row.previousBar?c}%"></div>
          </div>
        </td>
        <td class="num strong">${row.current}</td>
        <td class="num muted">${row.previous}</td>
        <td class="num ${row.evolutionTone}">${row.evolution}</td>
        <td class="num">${row.share}</td>
      </tr>
      </#list>
    </tbody>
  </table>
  <#else>
  <p class="empty">${emptyText?html}</p>
  </#if>
</section>
</#macro>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>${report.dossier.name?html} – ${report.periodLabel}</title>
  <style>
    @page { size: A4; margin: 12mm 11mm 14mm 11mm; }
    * { box-sizing: border-box; }
    body { margin: 0; font-family: "Inter", "Helvetica Neue", Arial, sans-serif; font-size: 9.5pt; color: #0f172a; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    h1, h2, h3, p { margin: 0; }
    h2 { font-size: 11pt; margin: 14px 0 6px; color: #1e293b; }
    h3 { font-size: 10pt; color: #1e293b; }
    .muted { color: #64748b; font-size: 8.5pt; }
    .strong { font-weight: 600; }
    .good { color: #15803d; }
    .bad { color: #b91c1c; }
    .neutral { color: #475569; }

    header { display: flex; justify-content: space-between; align-items: flex-end; padding-bottom: 8px; border-bottom: 2px solid #2563eb; }
    header .org img { display: block; max-height: 30px; max-width: 160px; }
    header .org .name { font-size: 12pt; font-weight: 700; }
    header .title { text-align: right; }
    header .title .kind { font-size: 8pt; font-weight: 700; color: #2563eb; text-transform: uppercase; letter-spacing: .08em; }
    header .title h1 { font-size: 16pt; color: #0f172a; margin: 1px 0; }

    .kpis { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 12px; }
    .kpi { border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px 10px; break-inside: avoid; border-top: 3px solid #cbd5e1; }
    .kpi.good { border-top-color: #16a34a; }
    .kpi.bad { border-top-color: #dc2626; }
    .kpi .label { color: #64748b; font-size: 8pt; text-transform: uppercase; letter-spacing: .03em; }
    .kpi .value { font-size: 13pt; font-weight: 700; color: #0f172a; margin: 3px 0 2px; }
    .kpi .evolution { font-weight: 700; font-size: 9pt; }
    .kpi .detail { color: #64748b; font-size: 7.5pt; }

    .card { border: 1px solid #e2e8f0; border-radius: 8px; padding: 7px 12px; margin-top: 7px; break-inside: avoid; }
    .insights { background: #f8fafc; }
    .insights ul { margin: 6px 0 0; padding: 0; list-style: none; }
    .insights li { padding: 3px 0 3px 16px; position: relative; color: #1e293b; }
    .insights li::before { content: ""; position: absolute; left: 2px; top: 8px; width: 7px; height: 7px; border-radius: 50%; background: #94a3b8; }
    .insights li.good::before { background: #16a34a; }
    .insights li.bad::before { background: #dc2626; }

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

    table { width: 100%; border-collapse: collapse; margin-top: 6px; }
    th { text-align: left; font-size: 7.5pt; color: #64748b; text-transform: uppercase; letter-spacing: .03em; border-bottom: 1px solid #cbd5e1; padding: 3px 6px; }
    td { padding: 3px 6px; border-bottom: 1px solid #f1f5f9; vertical-align: middle; }
    .num { text-align: right; white-space: nowrap; }
    table.share td.name { width: 38%; }
    .bars { margin-top: 2px; }
    .bar { height: 4px; border-radius: 2px; margin-top: 1px; min-width: 1px; }
    .bar.current { background: #2563eb; }
    .bar.previous { background: #cbd5e1; }
    tr.partial td { background: #eff6ff; }

    .dossier { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 8px; }
    .dossier div .label { color: #64748b; font-size: 7.5pt; text-transform: uppercase; }
    .dossier div .figure { font-size: 10.5pt; font-weight: 600; }
    footer { margin-top: 14px; color: #94a3b8; font-size: 7.5pt; text-align: center; }
  </style>
</head>
<body>
  <header>
    <div class="org">
      <#-- the logo carries the name of the company: the name only without logo -->
      <#if report.logo?has_content>
      <img src="${report.logo}" alt="${report.organizationName?html}">
      <#else>
      <div class="name">${report.organizationName?html}</div>
      <#if report.organizationVatNumber?has_content><div class="muted">${report.organizationVatNumber?html}</div></#if>
      </#if>
    </div>
    <div class="title">
      <div class="kind"><#if report.singleMonth>Monthly report<#else>Dossier report</#if></div>
      <h1>${report.periodLabel}</h1>
      <div class="muted">Dossier ${report.dossier.name?html} · ${report.dossier.status}</div>
    </div>
  </header>

  <div class="kpis">
    <#list report.kpis as k>
    <div class="kpi ${k.tone}">
      <div class="label">${k.label?html}</div>
      <div class="value">${k.value?html}</div>
      <#if k.evolution?has_content><div class="evolution ${k.tone}">${k.evolution}</div></#if>
      <div class="detail">${k.detail?html}</div>
    </div>
    </#list>
  </div>

  <#if report.insights?has_content>
  <section class="card insights">
    <h3>Highlights</h3>
    <ul>
      <#list report.insights as i>
      <li class="${i.tone}">${i.text?html}</li>
      </#list>
    </ul>
  </section>
  </#if>

  <@chart c=report.sameMonthChart/>
  <@chart c=report.ytdChart/>

  <@chart c=report.monthlyChart/>

  <#if !report.singleMonth>
  <section class="card">
    <h3>Month by month</h3>
    <p class="muted">Excluding VAT, every month compared to the same month a year before.</p>
    <table class="share">
      <thead>
        <tr><th>Month</th><th class="num">Revenue</th><th class="num">A year before</th><th class="num">Evolution</th><th class="num">Expenses</th><th class="num">Result</th><th class="num">Invoices</th></tr>
      </thead>
      <tbody>
        <#list report.months as m>
        <tr>
          <td class="name">
            ${m.label}
            <div class="bars">
              <div class="bar current" style="width: ${m.revenueBar?c}%"></div>
              <div class="bar previous" style="width: ${m.previousBar?c}%"></div>
            </div>
          </td>
          <td class="num strong">${m.revenue}</td>
          <td class="num muted">${m.previousRevenue}</td>
          <td class="num ${m.evolutionTone}">${m.evolution}</td>
          <td class="num">${m.expenses}</td>
          <td class="num">${m.result}</td>
          <td class="num<#if m.invoices == 0> bad</#if>">${m.invoices?c}</td>
        </tr>
        </#list>
      </tbody>
    </table>
  </section>
  </#if>

  <@shareTable title="Expenses per tag" subtitle=report.breakdownLabel + ", excluding VAT, compared to the same period a year before" rows=report.expensesPerTag emptyText="No expense processed in this period."/>
  <@shareTable title="Revenue per client" subtitle=report.breakdownLabel + ", excluding VAT, compared to the same period a year before" rows=report.topClients emptyText="No invoice processed in this period."/>

  <section class="card">
    <h3>Dossier ${report.dossier.name?html}</h3>
    <p class="muted">${report.dossier.status}<#if report.dossier.period?has_content> · invoices from ${report.dossier.period}</#if><#if report.dossier.description?has_content> · ${report.dossier.description?html}</#if></p>
    <div class="dossier">
      <div><div class="label">Invoices</div><div class="figure">${report.dossier.invoices?c} · ${report.dossier.revenue}</div></div>
      <div><div class="label">Expenses</div><div class="figure">${report.dossier.expenses?c} · ${report.dossier.expensesTotal}</div></div>
      <div><div class="label">Result</div><div class="figure">${report.dossier.result}</div></div>
      <div><div class="label">Documents</div><div class="figure">${report.dossier.documents?c}</div></div>
      <#if report.dossier.vatKnown>
      <div><div class="label">VAT due</div><div class="figure">${report.dossier.vatDue}</div></div>
      <div><div class="label">Advance payments</div><div class="figure">${report.dossier.advancePayments}</div></div>
      <div><div class="label">${report.dossier.vatBalanceLabel} the dossier</div><div class="figure ${report.dossier.vatBalanceTone}">${report.dossier.vatBalance}</div></div>
      <#else>
      <div><div class="label">Advance payments</div><div class="figure">${report.dossier.advancePayments}</div></div>
      </#if>
      <div><div class="label">Not processed yet</div><div class="figure">${report.pending.invoices?c} inv. · ${report.pending.expenses?c} exp.</div></div>
    </div>
  </section>

  <section class="card">
    <h3>Year by year</h3>
    <p class="muted">Excluding VAT. ${report.year?c}: ${report.ytdLabel} only, its evolution compared to the same period of ${report.previousYear?c}.</p>
    <table>
      <thead>
        <tr><th>Year</th><th class="num">Revenue</th><th class="num">Evolution</th><th class="num">Expenses</th><th class="num">Result</th><th class="num">Result %</th><th class="num">${report.periodName}</th><th class="num">Invoices</th></tr>
      </thead>
      <tbody>
        <#list report.history as y>
        <tr class="<#if y.partial>partial</#if>">
          <td class="strong">${y.year?c}<#if y.partial>*</#if></td>
          <td class="num strong">${y.revenue}</td>
          <td class="num ${y.revenueEvolutionTone}">${y.revenueEvolution}</td>
          <td class="num">${y.expenses}</td>
          <td class="num">${y.margin}</td>
          <td class="num">${y.marginRate}</td>
          <td class="num">${y.monthRevenue}</td>
          <td class="num">${y.invoices?c}</td>
        </tr>
        </#list>
      </tbody>
    </table>
  </section>

  <footer>Generated on ${report.generatedDate} · processed invoices and expenses only (the ones in a dossier) · amounts excluding VAT</footer>
</body>
</html>
