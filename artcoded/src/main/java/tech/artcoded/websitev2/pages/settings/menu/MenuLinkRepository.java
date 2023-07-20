package tech.artcoded.websitev2.pages.settings.menu;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface MenuLinkRepository extends MongoRepository<MenuLink, String> {
  List<MenuLink> findByOrderByOrderAsc();

  List<MenuLink> findByOrderByOrderDesc();

  default void incrementCount(String id) {
    this.findById(id).ifPresent(menu -> {
      this.save(
          menu.toBuilder()
              .updatedDate(new Date())
              .numberOfTimesClicked(menu.getNumberOfTimesClicked() + 1)
              .build());
    });
  }
}
