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
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
@EnableWebSecurity
public class ResourceServerConfig {
  private final HandlerMappingIntrospector introspector;

  public ResourceServerConfig(HandlerMappingIntrospector introspector) {
    this.introspector = introspector;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // accountants or regulators bits

    // label endpoints
    var labelFindAllMatcher = new MvcRequestMatcher(introspector, "/api/label/find-all");
    labelFindAllMatcher.setMethod(POST);

    var labelFindByNameMatcher = new MvcRequestMatcher(introspector, "/api/label/find-by-name");
    labelFindByNameMatcher.setMethod(POST);

    // expense endpoints
    var feeFindAllMatcher = new MvcRequestMatcher(introspector, "/api/fee/find-all");
    feeFindAllMatcher.setMethod(POST);

    var feeFindByIdMatcher = new MvcRequestMatcher(introspector, "/api/fee/find-by-id");
    feeFindByIdMatcher.setMethod(POST);

    var feeFindByIdsMatcher = new MvcRequestMatcher(introspector, "/api/fee/find-by-ids");
    feeFindByIdsMatcher.setMethod(POST);

    var feeSearchMatcher = new MvcRequestMatcher(introspector, "/api/fee/search");
    feeSearchMatcher.setMethod(POST);

    var feeSummariesMatcher = new MvcRequestMatcher(introspector, "/api/fee/summaries");
    feeSummariesMatcher.setMethod(POST);

    // billable clients
    var billableClientFindByContractStatusMatcher = new MvcRequestMatcher(
        introspector, "/api/billable-client/find-by-contract-status");
    billableClientFindByContractStatusMatcher.setMethod(GET);

    var billableClientFindAllMatcher = new MvcRequestMatcher(introspector, "/api/billable-client/find-all");
    billableClientFindAllMatcher.setMethod(GET);

    // administrative documents
    var adminDocFindAllMatcher = new MvcRequestMatcher(
        introspector, "/api/administrative-document/find-all");
    adminDocFindAllMatcher.setMethod(POST);

    var adminDocFindByIdMatcher = new MvcRequestMatcher(
        introspector, "/api/administrative-document/find-by-id");
    adminDocFindByIdMatcher.setMethod(POST);

    var adminDocFindByIdsMatcher = new MvcRequestMatcher(
        introspector, "/api/administrative-document/find-by-ids");
    adminDocFindByIdsMatcher.setMethod(POST);

    var adminDocSearchMatcher = new MvcRequestMatcher(
        introspector, "/api/administrative-document/search");
    adminDocSearchMatcher.setMethod(POST);

    // dossiers
    var dossierFindAllMatcher = new MvcRequestMatcher(introspector, "/api/dossier/find-all");
    dossierFindAllMatcher.setMethod(POST);

    var dossierFindAllPagedMatcher = new MvcRequestMatcher(introspector, "/api/dossier/find-all-paged");
    dossierFindAllPagedMatcher.setMethod(POST);

    var dossierSummaryMatcher = new MvcRequestMatcher(introspector, "/api/dossier/summary");
    dossierSummaryMatcher.setMethod(POST);

    var dossierSummariesMatcher = new MvcRequestMatcher(introspector, "/api/dossier/summaries");
    dossierSummariesMatcher.setMethod(POST);

    var dossierFindAllSummariesMatcher = new MvcRequestMatcher(introspector, "/api/dossier/find-all-summaries");
    dossierFindAllSummariesMatcher.setMethod(POST);

    var dossierFindByIdMatcher = new MvcRequestMatcher(introspector, "/api/dossier/find-by-id");
    dossierFindByIdMatcher.setMethod(POST);

    var dossierSizeMatcher = new MvcRequestMatcher(introspector, "/api/dossier/size");
    dossierSizeMatcher.setMethod(POST);

    var dossierGenerateSummaryMatcher = new MvcRequestMatcher(introspector, "/api/dossier/generate-summary");
    dossierGenerateSummaryMatcher.setMethod(GET);

    var dossierFindByFeeIdMatcher = new MvcRequestMatcher(introspector, "/api/dossier/find-by-fee-id");
    dossierFindByFeeIdMatcher.setMethod(POST);

    var dossierActiveMatcher = new MvcRequestMatcher(introspector, "/api/dossier/active-dossier");
    dossierActiveMatcher.setMethod(POST);

    // invoices
    var invoicePageMatcher = new MvcRequestMatcher(introspector, "/api/invoice/page");
    invoicePageMatcher.setMethod(POST);

    var invoiceFindAllSummariesMatcher = new MvcRequestMatcher(introspector, "/api/invoice/find-all-summaries");
    invoiceFindAllSummariesMatcher.setMethod(POST);

    var invoiceFindByIdMatcher = new MvcRequestMatcher(introspector, "/api/invoice/find-by-id");
    invoiceFindByIdMatcher.setMethod(POST);

    var invoiceFindByIdsMatcher = new MvcRequestMatcher(introspector, "/api/invoice/find-by-ids");
    invoiceFindByIdsMatcher.setMethod(POST);

    var invoiceListTemplatesMatcher = new MvcRequestMatcher(introspector, "/api/invoice/list-templates");
    invoiceListTemplatesMatcher.setMethod(GET);

    // personal infos
    var personalInfoMeMatcher = new MvcRequestMatcher(introspector, "/api/personal-info/@me");
    personalInfoMeMatcher.setMethod(GET);
    var personalInfoMatcher = new MvcRequestMatcher(introspector, "/api/personal-info");
    personalInfoMatcher.setMethod(GET);

    // menu links
    var menuLinkClickedMatcher = new MvcRequestMatcher(introspector, "/api/settings/menu-link/clicked");
    menuLinkClickedMatcher.setMethod(POST);

    var menuLinkTop3Matcher = new MvcRequestMatcher(introspector, "/api/settings/menu-link/top-3");
    menuLinkTop3Matcher.setMethod(GET);

    var menuLinkFindAllMatcher = new MvcRequestMatcher(introspector, "/api/settings/menu-link");
    menuLinkFindAllMatcher.setMethod(GET);

    // uploads
    var resourceFindByIdMatcher = new MvcRequestMatcher(introspector, "/api/resource/find-by-id");
    resourceFindByIdMatcher.setMethod(GET);

    var resourceFindByIdsMatcher = new MvcRequestMatcher(introspector, "/api/resource/find-by-ids");
    resourceFindByIdsMatcher.setMethod(GET);

    var resourceFindAllMatcher = new MvcRequestMatcher(introspector, "/api/resource/find-all");
    resourceFindAllMatcher.setMethod(POST);

    var resourcePrivateDownloadMatcher = new MvcRequestMatcher(introspector, "/api/resource/download");
    resourcePrivateDownloadMatcher.setMethod(GET);

    var resourceFindByCorrelationIdMatcher = new MvcRequestMatcher(
        introspector, "/api/resource/find-by-correlation-id");
    resourceFindByCorrelationIdMatcher.setMethod(GET);
    // end

    var prometheusMatcher = new MvcRequestMatcher(introspector, "/api/actuator/prometheus/**");

    var memzagramPublicMatcher = new MvcRequestMatcher(introspector, "/api/memzagram/public/**");
    memzagramPublicMatcher.setMethod(GET);
    var memzagramStatMatcher = new MvcRequestMatcher(introspector, "/api/memzagram/_stat/**");
    memzagramStatMatcher.setMethod(POST);

    var formContactSubmitMatcher = new MvcRequestMatcher(introspector, "/api/form-contact/submit/**");
    formContactSubmitMatcher.setMethod(POST);

    var toolboxGetMatcher = new MvcRequestMatcher(introspector, "/api/toolbox/public/**");
    toolboxGetMatcher.setMethod(GET);
    var toolboxPostMatcher = new MvcRequestMatcher(introspector, "/api/toolbox/public/**");
    toolboxPostMatcher.setMethod(POST);

    var deleteMatcher = new MvcRequestMatcher(introspector, "/**");
    deleteMatcher.setMethod(DELETE);

    var optionMatcher = new MvcRequestMatcher(introspector, "/**");
    optionMatcher.setMethod(OPTIONS);

    var resourcePublicMatcher = new MvcRequestMatcher(introspector, "/api/resource/public/**");
    resourcePublicMatcher.setMethod(GET);

    var blogGetMatcher = new MvcRequestMatcher(introspector, "/api/blog/**");
    blogGetMatcher.setMethod(GET);

    var mainPageGetMatcher = new MvcRequestMatcher(introspector, "/api/main-page/**");
    mainPageGetMatcher.setMethod(GET);

    var blogPublicSearchMatcher = new MvcRequestMatcher(introspector, "/api/blog/public-search/**");
    blogPublicSearchMatcher.setMethod(POST);

    var apiPutMatcher = new MvcRequestMatcher(introspector, "/api/**");
    apiPutMatcher.setMethod(PUT);
    var apiPostMatcher = new MvcRequestMatcher(introspector, "/api/**");
    apiPostMatcher.setMethod(POST);
    var apiGetMatcher = new MvcRequestMatcher(introspector, "/api/**");
    apiGetMatcher.setMethod(GET);

    var cvAdminDownloadMatcher = new MvcRequestMatcher(introspector, "/api/cv/admin-download/**");
    cvAdminDownloadMatcher.setMethod(GET);
    var cvMatcher = new MvcRequestMatcher(introspector, "/api/cv/**");
    cvMatcher.setMethod(GET);
    var cvPublicDownloadMatcher = new MvcRequestMatcher(introspector, "/api/cv/download/**");
    cvPublicDownloadMatcher.setMethod(POST);

    http.csrf(c -> c.disable())
        .authorizeHttpRequests(
            a -> a.requestMatchers(prometheusMatcher)
                .hasAnyRole(PROMETHEUS.getAuthority())
                .requestMatchers(deleteMatcher)
                .hasAnyRole(ADMIN.getAuthority())
                .requestMatchers(memzagramPublicMatcher)
                .permitAll()
                .requestMatchers(memzagramStatMatcher)
                .permitAll()

                .requestMatchers(formContactSubmitMatcher)
                .permitAll()
                .requestMatchers(toolboxGetMatcher)
                .permitAll()
                .requestMatchers(toolboxPostMatcher)
                .permitAll()

                .requestMatchers(resourcePublicMatcher)
                .permitAll()

                // accountant or regulator or admin bits
                .requestMatchers(resourcePrivateDownloadMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    /* SERVICE_ACCOUNT_DOWNLOAD.getAuthority(), */
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(labelFindByNameMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(labelFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(feeFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(feeFindByIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(feeFindByIdsMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(feeSearchMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(feeSummariesMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(billableClientFindByContractStatusMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(billableClientFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(adminDocFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(adminDocFindByIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(adminDocFindByIdsMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(adminDocSearchMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierFindAllPagedMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierSummaryMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierSummariesMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierFindAllSummariesMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierFindByIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierSizeMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierGenerateSummaryMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierFindByFeeIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(dossierActiveMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(invoicePageMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(invoiceFindAllSummariesMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(invoiceFindByIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(invoiceFindByIdsMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(invoiceListTemplatesMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(personalInfoMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(personalInfoMeMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())

                .requestMatchers(menuLinkClickedMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(menuLinkTop3Matcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(menuLinkFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(resourceFindByIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(resourceFindByIdsMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(resourceFindAllMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())
                .requestMatchers(resourceFindByCorrelationIdMatcher)
                .hasAnyRole(ADMIN.getAuthority(),
                    REGULATOR_OR_ACCOUNTANT.getAuthority())

                // end accountant or regulator bits

                .requestMatchers(blogGetMatcher)
                .permitAll()
                .requestMatchers(blogPublicSearchMatcher)
                .permitAll()

                .requestMatchers(mainPageGetMatcher)
                .permitAll()

                .requestMatchers(cvAdminDownloadMatcher)
                .hasAnyRole(ADMIN.getAuthority())
                .requestMatchers(cvMatcher)
                .permitAll()
                .requestMatchers(cvPublicDownloadMatcher)
                .permitAll()

                .requestMatchers(apiPostMatcher)
                .hasAnyRole(ADMIN.getAuthority())
                .requestMatchers(apiPutMatcher)
                .hasAnyRole(ADMIN.getAuthority())
                .requestMatchers(apiGetMatcher)
                .hasAnyRole(ADMIN.getAuthority(), USER.getAuthority())
                .requestMatchers(optionMatcher)
                .permitAll()
                .anyRequest()
                .permitAll())

        .oauth2ResourceServer(e -> e.jwt(jwt -> jwt.jwtAuthenticationConverter(
            jwtAuthenticationConverter())));

    return http.build();
  }

  private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
    jwtConverter.setJwtGrantedAuthoritiesConverter(new RealmRoleConverter());
    return jwtConverter;
  }
}
