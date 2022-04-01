<!doctype html>
<html lang="en">
<head>
    <!-- Required meta tags -->
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">

    <!-- Bootstrap CSS -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@4.6.0/dist/css/bootstrap.min.css"
          integrity="sha384-B0vP5xmATw1+K9KRQjQERJvTumQW0nPEzvF6L/Z6nronJ3oUOFUFpCjEUQouq2+l" crossorigin="anonymous">

    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.3.0/font/bootstrap-icons.css">
    <style type="text/css">
        .list-group-item {
            padding: 0.2rem 1.25rem !important;
        }

        .card-body {
            padding: .45rem !important;
        }

        @media print {
            div {
                break-inside: avoid;
                page-break-inside: avoid
            }
        }

    </style>
    <title>${cv.person.firstname} ${cv.person.lastname}</title>
</head>
<body>
<div class="container pt-2">
    <h3>About me</h3>
    <ul class="list-group">
        <li class="list-group-item">
            <i class="bi bi-file-person"></i>
            ${cv.person.lastname} ${cv.person.firstname}</li>
        <li class="list-group-item">
            <i class="bi bi-info-square"></i>
            ${cv.person.title}</li>
        <li class="list-group-item">
            <i class="bi bi-geo-alt"></i>
            ${cv.person.address}</li>
        <li class="list-group-item">
            <i class="bi bi-telephone"></i>
            ${cv.person.phoneNumber}
        </li>
        <li class="list-group-item">
            <i class="bi bi-at"></i>
            ${cv.person.emailAddress}
        </li>
        <li class="list-group-item">
            <i class="bi bi-calendar2-day"></i>
            ${cv.person.birthdate?date?iso_utc}
        </li>
        <li class="list-group-item">
            <i class="bi bi-github"></i>
            <a href="${cv.person.githubUrl}">${cv.person.githubUrl}</a>

        </li>
        <li class="list-group-item">
            <i class="bi bi-linkedin"></i>
            <a href="${cv.person.linkedinUrl}">${cv.person.linkedinUrl}</a>
        </li>
        <li class="list-group-item">
            <i class="bi bi-link"></i>
            <a href="${cv.person.website}">${cv.person.website}</a>
        </li>
    </ul>
</div>
<#if cv.personalProjects??>
    <div class="container pt-2">
        <h3>Personal Projects</h3>
        <#list cv.personalProjects as proj>
            <div class="card  mb-2">
                <div class="card-header">
                    <div class="d-flex  justify-content-between">
                        <strong>${proj.name} - <a href="${proj.url}">${proj.url}</a>
                        </strong>
                    </div>
                </div>
                <#if proj.description??>
                    <div class="card-body">
                        ${proj.description}
                    </div>
                </#if>

            </div>
        </#list>
    </div>
</#if>
<div class="container pt-2">
    <h3>Experiences</h3>
    <#list cv.experiences as exp>

        <div class="card  mb-2">
            <div class="card-header">

                <div class="d-flex  justify-content-between">
                    <strong>${exp.title} @ ${exp.company}</strong>
                </div>

                <p class="card-subtitle text-muted">${exp.from?date?iso_utc}
                    &ndash; ${exp.current?then('current',exp.to?date?iso_utc) }</p>

            </div>

            <div class="card-body">
                <ul>
                    <#list exp.description as description>
                        <li>
                            ${description}
                        </li>
                    </#list>
                </ul>
            </div>
        </div>
    </#list>

</div>
<div class="container pt-2">
    <h3>Hard Skills</h3>
    <#list cv.skills as skill>
        <#if skill.hardSkill>
            <div class="d-block mt-2 border border-light">
                <div class="d-flex justify-content-between bg-light border-bottom border-light">
                    <div class="pt-1 ml-1 mr-0 pr-0">
                        <strong>${skill.name}</strong>
                    </div>
                </div>
                <h5 class="mt-1">
                    <#list skill.tags as tag>
                        <span class="badge mr-1 mb-1 badge-secondary">${tag}</span>
                    </#list>

                </h5>
            </div>
        </#if>

    </#list>

</div>
<div class="container pt-2">
    <h3>Soft Skills</h3>
    <#list cv.skills as skill>
        <#if skill.softSkill>
            <div class="d-block mt-2 border border-light">
                <div class="d-flex justify-content-between bg-light border-bottom border-light">
                    <div class="pt-1 ml-1 mr-0 pr-0">
                        <strong class=" text-break">${skill.name}</strong>
                    </div>
                </div>
                <h5 class="mt-1">
                    <#list skill.tags as tag>
                        <span class="badge badge-secondary mr-2 mb-1">${tag}</span>
                    </#list>

                </h5>
            </div>
        </#if>

    </#list>

</div>

<div class="container pt-2">
    <h3>Education</h3>
    <#list cv.scholarHistories as hist>
        <div class="card border-0">
            <div class="card-body  bg-light">
                <strong class="card-title">${hist.title} @ ${hist.school}</strong>
                <p class="card-subtitle text-muted">${hist.from?date?iso_utc}/${hist.to?date?iso_utc}</p>
            </div>
        </div>
    </#list>

</div>
<#assign interests = cv.hobbies?map(hob -> hob.title)>
<div class="container pt-2 pb-2">
    <h3>Interests</h3>
    <p>${interests?join(", ","","")}</p>
</div>
</body>
</html>
