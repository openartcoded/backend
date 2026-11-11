package tech.artcoded.websitev2.pages.businessplan;

import java.util.LinkedHashMap;
import java.util.Map;

import tech.artcoded.websitev2.pages.businessplan.BusinessPlanRequest.Language;

/**
 * The texts of the business plan in english, french and dutch. {name} is replaced by a value. The labels (no
 * placeholder) are given to the template as plan.t.
 */
final class BusinessPlanTexts {
    private static final Map<String, String[]> TEXTS = new LinkedHashMap<>();

    private BusinessPlanTexts() {
    }

    private static void t(String key, String en, String fr, String nl) {
        TEXTS.put(key, new String[] { en, fr, nl });
    }

    static {
        t("title", "Business plan", "Plan financier", "Financieel plan");
        t("subtitle", "Prepared on {date} from the accounting data", "Établi le {date} sur base des données comptables",
                "Opgesteld op {date} op basis van de boekhoudkundige gegevens");

        // sections
        t("secSummary", "Key figures", "Chiffres clés", "Kerncijfers");
        t("secCompany", "The company", "L'entreprise", "De onderneming");
        t("secHistory", "Progress over the years", "Évolution au fil des années", "Evolutie over de jaren");
        t("secMonths", "Progress during the year", "Évolution en cours d'année", "Evolutie in de loop van het jaar");
        t("secExpenses", "Expenses", "Dépenses", "Uitgaven");
        t("secExceptional", "Exceptional expenses", "Dépenses exceptionnelles", "Uitzonderlijke uitgaven");
        t("secForecast", "Projected results", "Résultats prévisionnels", "Verwachte resultaten");
        t("secScenarios", "Scenarios", "Scénarios", "Scenario's");
        t("secFinancing", "Financing plan", "Plan de financement", "Financieringsplan");
        t("secMethod", "Assumptions", "Hypothèses", "Hypothesen");

        // company
        t("name", "Name", "Dénomination", "Benaming");
        t("vat", "VAT number", "Numéro de TVA", "Btw-nummer");
        t("address", "Address", "Adresse", "Adres");
        t("manager", "Manager", "Gérant", "Zaakvoerder");
        t("contact", "Contact", "Contact", "Contact");
        t("activity", "Activity", "Activité", "Activiteit");
        t("activeSince", "Invoicing since", "Facturation depuis", "Facturatie sinds");

        // tables
        t("year", "Year", "Année", "Jaar");
        t("month", "Month", "Mois", "Maand");
        t("soFar", "so far", "en cours", "lopend");
        t("revenue", "Revenue", "Chiffre d'affaires", "Omzet");
        t("growth", "Growth", "Croissance", "Groei");
        t("expenses", "Expenses", "Dépenses", "Uitgaven");
        t("normalExpenses", "Operating expenses", "Charges d'exploitation", "Bedrijfskosten");
        t("exceptionalExpenses", "Exceptional expenses", "Dépenses exceptionnelles", "Uitzonderlijke uitgaven");
        t("result", "Result before tax", "Résultat avant impôts", "Resultaat vóór belastingen");
        t("adjustedResult", "Result excl. exceptional", "Résultat hors exceptionnel", "Resultaat excl. uitzonderlijk");
        t("margin", "Margin", "Marge", "Marge");
        t("cumulated", "Cumulated", "Cumulé", "Gecumuleerd");
        t("category", "Category", "Catégorie", "Categorie");
        t("share", "Share", "Part", "Aandeel");
        t("average", "Yearly average", "Moyenne annuelle", "Jaarlijks gemiddelde");
        t("date", "Date", "Date", "Datum");
        t("description", "Description", "Libellé", "Omschrijving");
        t("amount", "Amount", "Montant", "Bedrag");
        t("status", "Status", "Statut", "Status");
        t("confirmed", "confirmed", "confirmée", "bevestigd");
        t("detected", "detected", "détectée", "gedetecteerd");
        t("noExceptional", "No exceptional expense.", "Aucune dépense exceptionnelle.", "Geen uitzonderlijke uitgaven.");
        t("noData", "Nothing to show yet.", "Rien à afficher pour l'instant.", "Nog niets te tonen.");
        t("amountsNote", "Amounts in euro, excluding VAT. Results before tax.",
                "Montants en euros, hors TVA. Résultats avant impôts.",
                "Bedragen in euro, exclusief btw. Resultaten vóór belastingen.");

        // forecast
        t("scenario", "Scenario", "Scénario", "Scenario");
        t("PRUDENT", "Prudent", "Prudent", "Voorzichtig");
        t("REALISTIC", "Realistic", "Réaliste", "Realistisch");
        t("OPTIMISTIC", "Optimistic", "Optimiste", "Optimistisch");
        t("provision", "Provision exceptional expenses", "Provision dépenses exceptionnelles",
                "Voorziening uitzonderlijke uitgaven");
        t("loanPayments", "Loan payments", "Remboursements du prêt", "Aflossingen lening");
        t("coverage", "Coverage", "Couverture", "Dekking");
        t("noForecast", "There is no complete year yet: no projection.",
                "Il n'y a pas encore d'année complète : pas de prévisions.",
                "Er is nog geen volledig jaar: geen prognose.");
        t("realisticNote", "Realistic scenario, the one the plan is built on.",
                "Scénario réaliste, celui sur lequel le plan se base.",
                "Realistisch scenario, waarop het plan gebaseerd is.");
        t("growthPerYear", "Revenue growth per year", "Croissance du chiffre d'affaires par an",
                "Omzetgroei per jaar");

        // financing
        t("loanAmount", "Amount requested", "Montant demandé", "Gevraagd bedrag");
        t("loanDuration", "Duration", "Durée", "Looptijd");
        t("loanRate", "Interest rate", "Taux d'intérêt", "Rentevoet");
        t("monthlyPayment", "Monthly payment", "Mensualité", "Maandelijkse aflossing");
        t("totalInterest", "Total interest", "Intérêts totaux", "Totale interest");
        t("purpose", "Purpose", "Objet", "Doel");

        // chart titles
        t("chartYears", "Revenue and result per year", "Chiffre d'affaires et résultat par année",
                "Omzet en resultaat per jaar");
        t("chartYearsSubtitle", "Faded bars: projection (realistic scenario)",
                "Barres claires : prévisions (scénario réaliste)",
                "Lichte balken: prognose (realistisch scenario)");
        t("chartMonths", "Revenue per month", "Chiffre d'affaires par mois", "Omzet per maand");

        // ---------- sentences, with values ----------
        t("months", "{n} months", "{n} mois", "{n} maanden");
        t("vsPrevious", "{growth} vs {year}", "{growth} par rapport à {year}", "{growth} t.o.v. {year}");
        t("marginOf", "margin {margin}", "marge de {margin}", "marge van {margin}");
        t("kpiRevenue", "Revenue {year}", "Chiffre d'affaires {year}", "Omzet {year}");
        t("kpiResult", "Result {year}", "Résultat {year}", "Resultaat {year}");
        t("kpiYearToDate", "Revenue {year} so far", "Chiffre d'affaires {year} à ce jour", "Omzet {year} tot nu");
        t("kpiForecast", "Projected revenue {year}", "Chiffre d'affaires prévu {year}", "Verwachte omzet {year}");
        t("kpiForecastDetail", "result {result}", "résultat {result}", "resultaat {result}");
        t("kpiGrowth", "Average growth", "Croissance moyenne", "Gemiddelde groei");
        t("kpiGrowthDetail", "revenue, {years}", "chiffre d'affaires, {years}", "omzet, {years}");

        t("summaryHistory",
                "{org} has been invoicing since {since}. In {year}, the revenue reached {revenue} excluding VAT ({growth} compared with {previous}) for a result before tax of {result}, a margin of {margin}.",
                "{org} facture depuis {since}. En {year}, le chiffre d'affaires a atteint {revenue} hors TVA ({growth} par rapport à {previous}) pour un résultat avant impôts de {result}, soit une marge de {margin}.",
                "{org} factureert sinds {since}. In {year} bedroeg de omzet {revenue} exclusief btw ({growth} ten opzichte van {previous}) voor een resultaat vóór belastingen van {result}, een marge van {margin}.");
        t("summaryHistoryFirst",
                "{org} has been invoicing since {since}. In {year}, the revenue reached {revenue} excluding VAT for a result before tax of {result}, a margin of {margin}.",
                "{org} facture depuis {since}. En {year}, le chiffre d'affaires a atteint {revenue} hors TVA pour un résultat avant impôts de {result}, soit une marge de {margin}.",
                "{org} factureert sinds {since}. In {year} bedroeg de omzet {revenue} exclusief btw voor een resultaat vóór belastingen van {result}, een marge van {margin}.");
        t("summaryGrowth",
                "Over {years}, the revenue grew by {growth} a year on average.",
                "Sur {years}, le chiffre d'affaires a progressé en moyenne de {growth} par an.",
                "Over {years} groeide de omzet gemiddeld met {growth} per jaar.");
        t("summaryYearToDate",
                "In {year}, the revenue until the end of {month} is {ytd}, {growth} compared with the same period of {previous}.",
                "En {year}, le chiffre d'affaires jusqu'à fin {month} s'élève à {ytd}, soit {growth} par rapport à la même période de {previous}.",
                "In {year} bedraagt de omzet tot eind {month} {ytd}, {growth} ten opzichte van dezelfde periode in {previous}.");
        t("summaryForecast",
                "In the realistic scenario, the revenue reaches {revenue} in {year} for a result before tax of {result}.",
                "Dans le scénario réaliste, le chiffre d'affaires atteint {revenue} en {year} pour un résultat avant impôts de {result}.",
                "In het realistische scenario bereikt de omzet {revenue} in {year} voor een resultaat vóór belastingen van {result}.");
        t("summaryLoan",
                "The requested loan of {amount} costs {payment} per month; the realistic result of {year} covers the payments of that year {coverage} times.",
                "Le prêt demandé de {amount} coûte {payment} par mois ; le résultat réaliste de {year} couvre {coverage} fois les remboursements de cette année.",
                "De gevraagde lening van {amount} kost {payment} per maand; het realistische resultaat van {year} dekt de aflossingen van dat jaar {coverage} keer.");

        t("monthsExplain",
                "Revenue invoiced each month: the current year compared with the previous ones. Until the end of {month}, {year} is at {ytd} against {previousYtd} in {previous} ({growth}).",
                "Chiffre d'affaires facturé chaque mois : l'année en cours comparée aux précédentes. Jusqu'à fin {month}, {year} est à {ytd} contre {previousYtd} en {previous} ({growth}).",
                "Gefactureerde omzet per maand: het lopende jaar vergeleken met de vorige. Tot eind {month} staat {year} op {ytd} tegenover {previousYtd} in {previous} ({growth}).");
        t("expensesExplain",
                "The operating expenses of {year} by category, compared with their yearly average.",
                "Les charges d'exploitation de {year} par catégorie, comparées à leur moyenne annuelle.",
                "De bedrijfskosten van {year} per categorie, vergeleken met hun jaarlijks gemiddelde.");
        t("exceptionalExplain",
                "Some expenses are far above the usual ones: taxes and social contributions paid in one go, one-off purchases. They are shown apart so that they don't distort the picture of a normal year. An expense is exceptional when it was confirmed as such, or when it was not reviewed and costs at least {threshold} (VAT included).",
                "Certaines dépenses sont bien au-dessus des dépenses habituelles : impôts et cotisations sociales payés en une fois, achats ponctuels. Elles sont présentées à part pour ne pas fausser l'image d'une année normale. Une dépense est exceptionnelle lorsqu'elle a été confirmée comme telle, ou lorsqu'elle n'a pas été revue et coûte au moins {threshold} (TVA comprise).",
                "Sommige uitgaven liggen ver boven de gebruikelijke: belastingen en sociale bijdragen in één keer betaald, eenmalige aankopen. Ze worden apart getoond zodat ze het beeld van een normaal jaar niet vertekenen. Een uitgave is uitzonderlijk wanneer ze als dusdanig bevestigd werd, of wanneer ze niet nagekeken werd en minstens {threshold} kost (btw inbegrepen).");
        t("exceptionalForecast",
                "In the projections, they are neither ignored nor counted as if they came back every year: each scenario keeps a provision equal to their yearly average ({provision}).",
                "Dans les prévisions, elles ne sont ni ignorées ni comptées comme si elles revenaient chaque année : chaque scénario garde une provision égale à leur moyenne annuelle ({provision}).",
                "In de prognoses worden ze niet genegeerd en ook niet geteld alsof ze elk jaar terugkomen: elk scenario houdt een voorziening aan gelijk aan hun jaarlijks gemiddelde ({provision}).");
        t("scenariosExplain",
                "Result before tax per year in each scenario. Prudent: no growth from the lowest revenue of the reference years and expenses up {prudentIndexation} a year. Realistic: growth of {growth} a year, expenses up {indexation}. Optimistic: growth of {optimisticGrowth} a year, expenses up {indexation}.",
                "Résultat avant impôts par année dans chaque scénario. Prudent : pas de croissance à partir du chiffre d'affaires le plus bas des années de référence et dépenses en hausse de {prudentIndexation} par an. Réaliste : croissance de {growth} par an, dépenses en hausse de {indexation}. Optimiste : croissance de {optimisticGrowth} par an, dépenses en hausse de {indexation}.",
                "Resultaat vóór belastingen per jaar in elk scenario. Voorzichtig: geen groei vanaf de laagste omzet van de referentiejaren en uitgaven die met {prudentIndexation} per jaar stijgen. Realistisch: groei van {growth} per jaar, uitgaven die met {indexation} stijgen. Optimistisch: groei van {optimisticGrowth} per jaar, uitgaven die met {indexation} stijgen.");

        t("methodAmounts",
                "Amounts are in euro, excluding VAT. An invoice counts in the month and year of its date, an expense in the year of its date. Results are before tax.",
                "Les montants sont en euros, hors TVA. Une facture compte dans le mois et l'année de sa date, une dépense dans l'année de sa date. Les résultats sont avant impôts.",
                "Bedragen zijn in euro, exclusief btw. Een factuur telt in de maand en het jaar van haar datum, een uitgave in het jaar van haar datum. De resultaten zijn vóór belastingen.");
        t("methodBase",
                "The projections start from {last} and from the average revenue growth of the reference years {years} ({historical}), capped to {cap} either way.",
                "Les prévisions partent de {last} et de la croissance moyenne du chiffre d'affaires des années de référence {years} ({historical}), limitée à {cap} dans les deux sens.",
                "De prognoses vertrekken van {last} en van de gemiddelde omzetgroei van de referentiejaren {years} ({historical}), begrensd tot {cap} in beide richtingen.");
        t("methodBaseNoGrowth",
                "The projections start from {last}; there is not enough history to measure a growth: none is assumed.",
                "Les prévisions partent de {last} ; l'historique ne permet pas encore de mesurer une croissance : aucune n'est supposée.",
                "De prognoses vertrekken van {last}; de historiek laat nog niet toe een groei te meten: er wordt geen groei verondersteld.");
        t("methodExpenses",
                "Operating expenses start from those of {last} and are indexed by {indexation} a year ({prudentIndexation} in the prudent scenario).",
                "Les charges d'exploitation partent de celles de {last} et sont indexées de {indexation} par an ({prudentIndexation} dans le scénario prudent).",
                "De bedrijfskosten vertrekken van die van {last} en worden met {indexation} per jaar geïndexeerd ({prudentIndexation} in het voorzichtige scenario).");
        t("methodProvision",
                "Exceptional expenses are replaced by a yearly provision equal to their average over the reference years.",
                "Les dépenses exceptionnelles sont remplacées par une provision annuelle égale à leur moyenne sur les années de référence.",
                "Uitzonderlijke uitgaven worden vervangen door een jaarlijkse voorziening gelijk aan hun gemiddelde over de referentiejaren.");
        t("methodTax",
                "The tax depends on the legal form: corporate tax for a company, personal income tax and social contributions for a self-employed person.",
                "L'impôt dépend de la forme juridique : impôt des sociétés pour une société, impôt des personnes physiques et cotisations sociales pour un indépendant.",
                "De belasting hangt af van de rechtsvorm: vennootschapsbelasting voor een vennootschap, personenbelasting en sociale bijdragen voor een zelfstandige.");
        t("methodLoan",
                "The loan is paid back by constant monthly payments. Coverage is the result before tax divided by the payments of the year: above 1.2, the loan is comfortably covered.",
                "Le prêt est remboursé par mensualités constantes. La couverture est le résultat avant impôts divisé par les remboursements de l'année : au-delà de 1,2, le prêt est largement couvert.",
                "De lening wordt terugbetaald met constante maandelijkse aflossingen. De dekking is het resultaat vóór belastingen gedeeld door de aflossingen van het jaar: boven 1,2 is de lening ruim gedekt.");
        t("methodPartial",
                "The current year ({year}) is not complete: it is shown for information and not used in the projections.",
                "L'année en cours ({year}) n'est pas complète : elle est montrée à titre indicatif et n'est pas utilisée dans les prévisions.",
                "Het lopende jaar ({year}) is niet volledig: het wordt ter informatie getoond en niet gebruikt in de prognoses.");
    }

    static String text(Language language, String key, Object... nameValues) {
        var values = TEXTS.get(key);
        if (values == null) {
            throw new IllegalArgumentException("no text " + key);
        }
        var text = values[language.ordinal()];
        for (int i = 0; i + 1 < nameValues.length; i += 2) {
            text = text.replace("{" + nameValues[i] + "}", String.valueOf(nameValues[i + 1]));
        }
        return text;
    }

    /** the labels of the language (the texts without placeholder), for the template */
    static Map<String, String> labels(Language language) {
        var labels = new LinkedHashMap<String, String>();
        TEXTS.forEach((k, v) -> {
            if (!v[0].contains("{")) {
                labels.put(k, v[language.ordinal()]);
            }
        });
        return labels;
    }

    /** every text of every language, for the tests */
    static Map<String, String[]> all() {
        return TEXTS;
    }
}
