package tech.artcoded.websitev2.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class JinjaMockContextTest {
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @Test
    void readsNestedVariablesWithTypes() {
        var ctx = JinjaMockContext.of("""
                <h1>{{ invoice.billTo.clientName }}</h1>
                <p>{{ invoice.dueDate|dateformat(format="%d/%m/%Y") }}</p>
                <p>{{ invoice.subTotal * 2 }} {{ invoice.taxRate }}%</p>
                {% if invoice.creditNote is defined and personalInfo.isDemo %}demo{% endif %}
                """, NOW);
        var invoice = map(ctx.get("invoice"));
        assertThat(map(invoice.get("billTo"))).containsEntry("clientName", "clientName");
        assertThat(invoice).containsEntry("dueDate", "2026-11-12T10:00:00Z").containsEntry("subTotal", 1234.56)
                .containsEntry("taxRate", 21);
        assertThat(map(ctx.get("personalInfo"))).containsEntry("isDemo", true);
        // tests and filters are not variables
        assertThat(ctx).doesNotContainKeys("defined", "dateformat", "format");
    }

    @Test
    void loopsGiveListsOfTwoItems() {
        var ctx = JinjaMockContext.of("""
                {% for row in invoice.invoiceTable %}{{ row.nature }} {{ row.total|number }}
                  {% for tag in row.tags %}{{ tag }}{% endfor %}{% endfor %}
                {% for message in actionResult.messages or [] %}{{ message }}{% endfor %}
                {% for k, v in settings.items() %}{{ k }}={{ v }}{% endfor %}
                """, NOW);
        var rows = (List<Object>) map(ctx.get("invoice")).get("invoiceTable");
        assertThat(rows).hasSize(2);
        var row = map(rows.get(0));
        assertThat(row).containsEntry("nature", "nature").containsEntry("total", 1234.56);
        assertThat((List<Object>) row.get("tags")).containsExactly("tag 1", "tag 2");
        assertThat((List<Object>) map(ctx.get("actionResult")).get("messages")).containsExactly("message 1",
                "message 2");
        assertThat(map(ctx.get("settings"))).hasSize(2);
        assertThat(ctx).doesNotContainKeys("row", "tag", "message", "k", "v");
    }

    @Test
    void localsAreNotVariables() {
        var ctx = JinjaMockContext.of("""
                {%- set person = cv.person or {} -%}
                {%- set base = "https://example.org/" -%}
                {%- macro chart(c) -%}{{ c.title }}{% for b in c.bars %}{{ b.x }}{% endfor %}{%- endmacro -%}
                {{ person.firstname }} {{ base }} {{ chart(report.chart) }} {{ loop.index }}
                """, NOW);
        assertThat(map(map(ctx.get("cv")).get("person"))).containsKey("firstname");
        assertThat(ctx).containsOnlyKeys("cv", "report");
    }

    @Test
    void emptySource() {
        assertThat(JinjaMockContext.of("", NOW)).isEmpty();
        assertThat(JinjaMockContext.of("no variable here", NOW)).isEmpty();
    }

    @Test
    void guessesTypesFromNames() {
        assertThat(JinjaMockContext.guess("dateOfInvoice")).isEqualTo(JinjaMockContext.Kind.DATE);
        assertThat(JinjaMockContext.guess("birthdate")).isEqualTo(JinjaMockContext.Kind.DATE);
        assertThat(JinjaMockContext.guess("hoursPerDay")).isEqualTo(JinjaMockContext.Kind.NUMBER);
        assertThat(JinjaMockContext.guess("maxDaysToPay")).isEqualTo(JinjaMockContext.Kind.NUMBER);
        assertThat(JinjaMockContext.guess("organizationBankAccount")).isEqualTo(JinjaMockContext.Kind.STRING);
        assertThat(JinjaMockContext.guess("invoiceNumber")).isEqualTo(JinjaMockContext.Kind.STRING);
        assertThat(JinjaMockContext.guess("hasPaymentProof")).isEqualTo(JinjaMockContext.Kind.BOOLEAN);
    }

    @Test
    void bundledTemplatesGetTheirModelRoots() throws Exception {
        var source = new ClassPathResource("templates/v2/invoice-2027.html").getContentAsString(StandardCharsets.UTF_8);
        var ctx = JinjaMockContext.of(source, NOW);
        assertThat(ctx).containsKeys("invoice", "personalInfo");
        assertThat((List<Object>) map(ctx.get("invoice")).get("invoiceTable")).hasSize(2);
    }
}
