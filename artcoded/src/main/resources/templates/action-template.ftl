<p>Started Date: ${actionResult.startedDate?datetime?iso_utc}</p>
<p>Finished Date: ${actionResult.finishedDate?datetime?iso_utc}</p>
<p>Status: ${actionResult.status}</p>
<p>Messages:</p>
<ul>
    <#list actionResult.messages as message>
        <li>${message}</li>
    </#list>
</ul>

