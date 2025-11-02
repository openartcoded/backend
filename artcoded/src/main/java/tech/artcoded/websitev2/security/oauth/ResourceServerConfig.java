package tech.artcoded.websitev2.security.oauth;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.OPTIONS;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static tech.artcoded.websitev2.security.oauth.Role.*;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var mvc = PathPatternRequestMatcher.withDefaults();
        // accountants or regulators bits

        // label endpoints
        var labelFindAllMatcher = mvc.matcher(POST, "/api/label/find-all");
        var labelFindByNameMatcher = mvc.matcher(POST, "/api/label/find-by-name");

        // expense endpoints
        var feeFindAllMatcher = mvc.matcher(POST, "/api/fee/find-all");

        var feeFindByIdMatcher = mvc.matcher(POST, "/api/fee/find-by-id");

        var feeFindByIdsMatcher = mvc.matcher(POST, "/api/fee/find-by-ids");

        var feeSearchMatcher = mvc.matcher(POST, "/api/fee/search");

        var feeSummariesMatcher = mvc.matcher(POST, "/api/fee/summaries");

        // billable clients
        var billableClientFindByContractStatusMatcher = mvc.matcher(GET,
                "/api/billable-client/find-by-contract-status");

        var billableClientFindAllMatcher = mvc.matcher(GET, "/api/billable-client/find-all");

        // administrative documents
        var adminDocFindAllMatcher = mvc.matcher(GET, "/api/administrative-document/find-all");

        var adminDocFindByIdMatcher = mvc.matcher(POST, "/api/administrative-document/find-by-id");

        var adminDocFindByIdsMatcher = mvc.matcher(POST, "/api/administrative-document/find-by-ids");

        var adminDocSearchMatcher = mvc.matcher(POST, "/api/administrative-document/search");

        // dossiers
        var dossierFindAllMatcher = mvc.matcher(POST, "/api/dossier/find-all");

        var dossierFindAllPagedMatcher = mvc.matcher(POST, "/api/dossier/find-all-paged");

        var dossierSummaryMatcher = mvc.matcher(POST, "/api/dossier/summary");

        var dossierSummariesMatcher = mvc.matcher(POST, "/api/dossier/summaries");

        var dossierFindAllSummariesMatcher = mvc.matcher(POST, "/api/dossier/find-all-summaries");

        var dossierFindByIdMatcher = mvc.matcher(POST, "/api/dossier/find-by-id");

        var dossierSizeMatcher = mvc.matcher(POST, "/api/dossier/size");
        var dossierBookmarkedMatcher = mvc.matcher("/api/dossier/bookmarked");

        var dossierGenerateSummaryMatcher = mvc.matcher(GET, "/api/dossier/generate-summary");

        var dossierFindByFeeIdMatcher = mvc.matcher(POST, "/api/dossier/find-by-fee-id");

        var dossierActiveMatcher = mvc.matcher(POST, "/api/dossier/active-dossier");

        // invoices
        var invoicePageMatcher = mvc.matcher(POST, "/api/invoice/page");
        var invoiceBookmarkedMatcher = mvc.matcher("/api/invoice/bookmarked");

        var invoiceFindAllSummariesMatcher = mvc.matcher(POST, "/api/invoice/find-all-summaries");

        var invoiceFindByIdMatcher = mvc.matcher(POST, "/api/invoice/find-by-id");

        var invoiceFindByIdsMatcher = mvc.matcher(POST, "/api/invoice/find-by-ids");

        var invoiceListTemplatesMatcher = mvc.matcher(GET, "/api/invoice/list-templates");

        // personal infos
        var personalInfoMeMatcher = mvc.matcher(GET, "/api/personal-info/@me");
        var personalInfoMatcher = mvc.matcher(GET, "/api/personal-info");

        // menu links
        var menuLinkClickedMatcher = mvc.matcher(POST, "/api/settings/menu-link/clicked");

        var menuLinkTop3Matcher = mvc.matcher(GET, "/api/settings/menu-link/top-3");

        var menuLinkFindAllMatcher = mvc.matcher(GET, "/api/settings/menu-link");

        // uploads
        var resourceFindByIdMatcher = mvc.matcher(GET, "/api/resource/find-by-id");

        var resourceCorrelationLinksMatcher = mvc.matcher("/api/resource/correlation-links");
        var resourceFindByIdsMatcher = mvc.matcher(GET, "/api/resource/find-by-ids");

        var resourceFindAllMatcher = mvc.matcher(POST, "/api/resource/find-all");

        var resourcePrivateDownloadMatcher = mvc.matcher(GET, "/api/resource/download");

        var resourceFindByCorrelationIdMatcher = mvc.matcher(GET, "/api/resource/find-by-correlation-id");
        // end

        var swaggerMatcher = mvc.matcher(GET, "/swagger-ui.html");
        var openApiMatcher = mvc.matcher(GET, "/v3/api-docs/**");

        var prometheusMatcher = mvc.matcher("/api/actuator/prometheus/**");

        var memzagramPublicMatcher = mvc.matcher(GET, "/api/memzagram/public/**");
        var memzagramStatMatcher = mvc.matcher(POST, "/api/memzagram/_stat/**");

        var formContactSubmitMatcher = mvc.matcher(POST, "/api/form-contact/submit/**");

        var toolboxGetMatcher = mvc.matcher(GET, "/api/toolbox/public/**");
        var toolboxPostMatcher = mvc.matcher(POST, "/api/toolbox/public/**");

        var deleteMatcher = mvc.matcher(DELETE, "/**");

        var optionMatcher = mvc.matcher(OPTIONS, "/**");

        var resourcePublicMatcher = mvc.matcher(GET, "/api/resource/public/**");

        var mainPageGetMatcher = mvc.matcher(GET, "/api/main-page/**");

        var blogPublicSearchMatcher = mvc.matcher(POST, "/api/blog/public-search/**");

        var scriptMatcher = mvc.matcher("/api/script/**");

        var apiPutMatcher = mvc.matcher(PUT, "/api/**");
        var apiPostMatcher = mvc.matcher(POST, "/api/**");
        var apiGetMatcher = mvc.matcher(GET, "/api/**");

        var cvAdminDownloadMatcher = mvc.matcher(GET, "/api/cv/admin-download/**");
        var cvMatcher = mvc.matcher(GET, "/api/cv/**");
        var cvPublicDownloadMatcher = mvc.matcher(POST, "/api/cv/download/**");

        http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.requestMatchers(prometheusMatcher)
                .hasAnyRole(PROMETHEUS.getAuthority()).requestMatchers(deleteMatcher).hasAnyRole(ADMIN.getAuthority())
                .requestMatchers(memzagramPublicMatcher).permitAll().requestMatchers(memzagramStatMatcher).permitAll()

                .requestMatchers(formContactSubmitMatcher).permitAll().requestMatchers(toolboxGetMatcher).permitAll()
                .requestMatchers(toolboxPostMatcher).permitAll()

                .requestMatchers(resourcePublicMatcher).permitAll()

                // accountant or regulator or admin bits
                .requestMatchers(resourcePrivateDownloadMatcher).hasAnyRole(ADMIN.getAuthority(),
                        /* SERVICE_ACCOUNT_DOWNLOAD.getAuthority(), */
                        REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(labelFindByNameMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(labelFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(feeFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority()).requestMatchers(scriptMatcher)
                .hasAnyRole(ADMIN.getAuthority()).requestMatchers(feeFindByIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(feeFindByIdsMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(feeSearchMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(feeSummariesMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(billableClientFindByContractStatusMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(billableClientFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(adminDocFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(adminDocFindByIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(adminDocFindByIdsMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(adminDocSearchMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierFindAllPagedMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierSummaryMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierSummariesMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierFindAllSummariesMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierFindByIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierSizeMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierBookmarkedMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierGenerateSummaryMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierFindByFeeIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierActiveMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(invoicePageMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(invoiceBookmarkedMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(invoiceFindAllSummariesMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(invoiceFindByIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(invoiceFindByIdsMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(invoiceListTemplatesMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(personalInfoMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(personalInfoMeMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(menuLinkClickedMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(menuLinkTop3Matcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(menuLinkFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(resourceFindByIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(resourceFindByIdsMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(resourceCorrelationLinksMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(resourceFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(resourceFindByCorrelationIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(), REGULATOR_OR_ACCOUNTANT.getAuthority())

                // end accountant or regulator bits

                .requestMatchers(mainPageGetMatcher).permitAll()

                .requestMatchers(cvAdminDownloadMatcher).hasAnyRole(ADMIN.getAuthority()).requestMatchers(cvMatcher)
                .permitAll().requestMatchers(cvPublicDownloadMatcher).permitAll()

                .requestMatchers(apiPostMatcher).hasAnyRole(ADMIN.getAuthority()).requestMatchers(apiPutMatcher)
                .hasAnyRole(ADMIN.getAuthority()).requestMatchers(apiGetMatcher)
                .hasAnyRole(ADMIN.getAuthority(), USER.getAuthority()).requestMatchers(optionMatcher).permitAll()
                .requestMatchers(swaggerMatcher).permitAll().requestMatchers(openApiMatcher).permitAll() // FIXME limit
                                                                                                         // to
                                                                                                         // authenticated
                .anyRequest().permitAll())

                .oauth2ResourceServer(e -> e.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new RealmRoleConverter());
        return jwtConverter;
    }
}
