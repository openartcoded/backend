BEGIN:VCALENDAR
PRODID:-//${fullName}//${companyName}//EN
CALSCALE:GREGORIAN
VERSION:2.0
BEGIN:VEVENT
DTSTAMP:${dtstamp}
DTSTART:${dtstart}
DTEND:${dtend}
SUMMARY:${title}
TZID:${tzid}
DESCRIPTION:${description?no_esc}
LOCATION:${loc}
ATTENDEE;ROLE=REQ-PARTICIPANT;CN=${fullName}:mailto:${email}
END:VEVENT
END:VCALENDAR
