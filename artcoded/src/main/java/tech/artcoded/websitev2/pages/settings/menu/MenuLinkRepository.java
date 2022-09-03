package tech.artcoded.websitev2.pages.settings.menu;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MenuLinkRepository extends MongoRepository<MenuLink, String> {
  List<MenuLink> findByOrderByOrderAsc();
  List<MenuLink> findByOrderByOrderDesc();
}
