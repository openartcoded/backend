package tech.artcoded.websitev2.pages.settings.menu;

import java.util.Date;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MenuLinkRepository extends MongoRepository<MenuLink, String> {
  List<MenuLink> findByOrderByOrderAsc();

  default void reorder() {
    var links = this.findByOrderByOrderAsc();
    var i = links.size();
    while (i > 0) {
      i -= 1;
      var link = links.get(i);
      this.save(link.toBuilder().order(i).build());
    }
  }

  List<MenuLink> findByOrderByOrderDesc();

  List<MenuLink> findTop3ByOrderByNumberOfTimesClickedDesc();

  default void incrementCount(String id) {
    this.findById(id).ifPresent(menu -> {
      this.save(menu.toBuilder()
          .updatedDate(new Date())
          .numberOfTimesClicked(menu.getNumberOfTimesClicked() + 1)
          .build());
    });
  }
}
