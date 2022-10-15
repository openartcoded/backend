  <!DOCTYPE html>
  <html lang="en">

  <head>
    <meta charset="UTF-8">
    <meta content="text/html; charset=UTF-8" http-equiv="content-type">

    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@4.6.2/dist/css/bootstrap.min.css"
      integrity="sha384-xOolHFLEh07PJGoPkLv1IbcEPTNtaed2xpHsD9ESMhqIYd0nLMwNLD69Npy4HI+N" crossorigin="anonymous">
    <title>Invoice ${invoice.invoiceNumber}</title>

    <style type="text/css">
      .invoice {
        width: 100%;
      }

      .center {
        margin: 0 auto;
      }

      img {
        vertical-align: middle;
        opacity: .25;
        border-style: none
      }
    </style>
  </head>

  <body>
    <div class="invoice">
      <div class="d-block text-center">
        <img class="
        text-center opacity-25" src="${logo}">
        <small class="d-block text-center">IBAN: ${personalInfo.organizationBankAccount} -
          BIC: ${personalInfo.organizationBankBIC}</small>
      </div>
      <hr>
      <div class="text-center mb-3">
        <table class="table table-borderless table-light">
          <thead>
            <tr>
              <th>
                <h4>From</h4>
              </th>
              <th>
                <h4>To</h4>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>
                <strong>${personalInfo.organizationName}</strong>
                <p>${personalInfo.vatNumber}</p>
                <p>${personalInfo.organizationAddress}, ${personalInfo.organizationPostCode}
                  ${personalInfo.organizationCity}
                </p>
                <p>${personalInfo.organizationPhoneNumber}</p>
                <p>${personalInfo.organizationEmailAddress}</p>
              </td>
              <td>
                <strong>${invoice.billTo.clientName}</strong>
                <p>${invoice.billTo.vatNumber}</p>
                <p>${invoice.billTo.address},</p>
                <p>${invoice.billTo.city}</p>
                <p>${invoice.billTo.emailAddress}</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <#setting locale="fr_BE">
      <div class="float-right  mb-3">
        <strong class="d-block">N°${invoice.invoiceNumber}</strong>
        <strong class="d-block">${invoice.dateOfInvoice?date?string.medium}</strong>
      </div>
      <#setting locale="de_DE">
      <table class="table table-light table-borderless mb-3">
        <thead class="thead-dark">
          <tr>
            <th scope="col">Period</th>
            <th scope="col">Description</th>
            <th scope="col">Rate</th>
            <th scope="col">Amount</th>
            <th scope="col">Total</th>
          </tr>
        </thead>
        <tbody class="table-group-divider">
          <#list invoice.invoiceTable as table>
            <tr>
              <th scope="row">${table.period}</th>
              <td>
                <strong class="text-info">${table.nature}</strong>
                <p>${table.projectName}</p>
              </td>
              <td>
                <strong>${table.rate}&euro;/${table.rateType.label}</strong>
              </td>
              <td>
                ${table.amount} ${table.amountType.label}s <small class="text-secondary">(1 day =
                  ${table.hoursPerDay}h)</small>
              </td>
              <td>
                <strong>${table.total?string.currency}</strong>
              </td>
            </tr>
          </#list>
        </tbody>
        <tfoot>
          <tr>
            <td colspan="3"></td>
            <td colspan="1">SUBTOTAL</td>
            <td>${invoice.subTotal?string.currency}</td>
          </tr>
          <tr>
            <td colspan="3"></td>
            <td colspan="1">TAX ${invoice.taxRate}%</td>
            <td>${invoice.taxes?string.currency}</td>
          </tr>
          <tr>
            <td colspan="3"></td>
            <td colspan="1"><strong class="text-info">GRAND TOTAL</strong></td>
            <td><strong class="text-info">${invoice.total?string.currency}</strong></td>
          </tr>
        </tfoot>
      </table>

      <div class="alert alert-light" role="alert">
        <h5>Notice</h5>
        <p>Thank you for your business. Payment is due within ${invoice.maxDaysToPay} days. Please be aware
          that we will charge ${personalInfo.financeCharge}% interest per month on late invoices.</p>
      </div>
      <#if invoice.specialNote?has_content>
        <div class="alert alert-light" role="alert">
          <h5>Remark</h5>
          <p>${invoice.specialNote}</p>
        </div>
      <#else>
        <div class="mb-3"></div>
      </#if>
      <hr>
      <footer class="mt-2 text-center">
        <small class="text-center text-secondary">
          ${personalInfo.note}
        </small>
      </footer>
    </div>

  </body>

  </html>
