package tech.artcoded.websitev2.migrations;

import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.mongodb.MigrationTargetSystem;
import tech.artcoded.websitev2.pages.settings.menu.MenuLink;
import tech.artcoded.websitev2.pages.settings.menu.RouterLinkOption;
import tech.artcoded.websitev2.security.oauth.Role;

/**
 * The administration page: to do, post-its, toolbox, script console, system info and logs, previously tabs of the
 * home page. Added at the end of the menu, for both roles (accountants only see the toolbox there).
 */
@Slf4j
@TargetSystem(id = MigrationTargetSystem.ID)
@Change(id = "add-administration-menu", author = "Nordine Bittich")
public class _0008__AddAdministrationMenu {
    static final String ROUTE = "administration";

    @Apply
    public void apply(MongoTemplate mongoTemplate) {
        if (mongoTemplate.exists(administrationLinks(), MenuLink.class)) {
            log.info("the administration menu link already exists");
            return;
        }
        var last = mongoTemplate.findOne(new Query().with(Sort.by(Sort.Direction.DESC, "order")), MenuLink.class);
        int order = last == null ? 0 : last.getOrder() + 1;
        mongoTemplate.save(MenuLink.builder().title("Administration").description("Administration")
                .icon(new String[] { "fas", "cog" }).routerLink(new String[] { ROUTE }).order(order)
                .updatedDate(new Date())
                // not exact: one url per tab (/administration/logs, ...)
                .routerLinkActiveOptions(RouterLinkOption.builder().exact(false).build())
                .roles(List.of(Role.ADMIN.getAuthority(), Role.REGULATOR_OR_ACCOUNTANT.getAuthority())).build());
        log.info("administration menu link added (order {})", order);
    }

    @Rollback
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.remove(administrationLinks(), MenuLink.class);
    }

    static Query administrationLinks() {
        // routerLink is an array: matches when it contains the route
        return Query.query(Criteria.where("routerLink").is(ROUTE));
    }
}
