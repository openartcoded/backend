package tech.artcoded.websitev2.pages.dossier.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReportFormatAndChartTest {

    @Test
    void amountsAndPercentages() {
        assertThat(ReportFormat.money(new BigDecimal("-1234.5"))).isEqualTo("-1.234,50 €");
        assertThat(ReportFormat.money(null)).isEqualTo("0,00 €");
        assertThat(ReportFormat.compact(new BigDecimal("950"))).isEqualTo("950");
        assertThat(ReportFormat.compact(new BigDecimal("12345"))).isEqualTo("12,3k");
        assertThat(ReportFormat.compact(new BigDecimal("125000"))).isEqualTo("125k");
        assertThat(ReportFormat.compact(new BigDecimal("1500000"))).isEqualTo("1,5M");
        assertThat(ReportFormat.evolutionLabel(new BigDecimal("110"), new BigDecimal("100"))).isEqualTo("+10 %");
        assertThat(ReportFormat.evolutionLabel(new BigDecimal("90"), new BigDecimal("100"))).isEqualTo("-10 %");
        assertThat(ReportFormat.evolutionLabel(new BigDecimal("5"), BigDecimal.ZERO)).isEqualTo("new");
        assertThat(ReportFormat.evolutionLabel(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualTo("–");
        assertThat(ReportFormat.share(new BigDecimal("1"), new BigDecimal("3"))).isEqualByComparingTo("33.3");
    }

    @Test
    void niceAxisSteps() {
        assertThat(BarChart.niceStep(23)).isEqualTo(50);
        assertThat(BarChart.niceStep(1800)).isEqualTo(2000);
        assertThat(BarChart.niceStep(0.3)).isEqualTo(0.5);
        assertThat(BarChart.niceStep(0)).isEqualTo(1);
    }

    @Test
    void barsAreScaledToTheTopGridline() {
        var chart = BarChart.of("t", "s", List.of("2025", "2026"),
                List.of(new BarChart.Series("Revenue", "#000", List.of(new BigDecimal("50"), new BigDecimal("100")))),
                1, true);
        assertThat(chart.isEmpty()).isFalse();
        // step 50 (25 rounded up), top 100: 0, 50, 100
        assertThat(chart.getGridlines()).extracting(BarChart.Gridline::getLabel).containsExactly("0", "50", "100");
        var plotHeight = BarChart.HEIGHT - BarChart.TOP - BarChart.BOTTOM;
        assertThat(chart.getBars().get(1).getHeight()).isEqualTo(plotHeight);
        assertThat(chart.getBars().get(0).getHeight()).isEqualTo(plotHeight / 2.0);
        assertThat(chart.getBars())
                .allSatisfy(b -> assertThat(b.getY() + b.getHeight()).isEqualTo(BarChart.HEIGHT - BarChart.BOTTOM));
        assertThat(chart.getBars().get(1).getValueLabel()).isEqualTo("100");
    }

    @Test
    void missingAndNegativeValues() {
        var chart = BarChart.of("t", "s", List.of("a", "b", "c"), List
                .of(new BarChart.Series("x", "#000", Arrays.asList(new BigDecimal("10"), null, new BigDecimal("-5")))),
                -1, false);
        // null: no bar, negative: a bar of height 0
        assertThat(chart.getBars()).hasSize(2);
        assertThat(chart.getBars().get(1).getHeight()).isZero();
        assertThat(chart.getBars()).allSatisfy(b -> assertThat(b.getValueLabel()).isEmpty());
        assertThat(chart.getAxisLabels()).noneMatch(BarChart.AxisLabel::isHighlighted);

        assertThat(BarChart.of("t", "s", List.of("a"),
                List.of(new BarChart.Series("x", "#000", List.of(BigDecimal.ZERO))), 0, true).isEmpty()).isTrue();
    }
}
