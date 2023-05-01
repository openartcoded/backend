Action: ${actionKey}
Started Date: ${actionResult.startedDate?datetime?iso_utc}
Finished Date: ${actionResult.finishedDate?datetime?iso_utc}
Status: ${actionResult.status}
Messages:

<#list actionResult.messages as message>
  ${message}
</#list>


