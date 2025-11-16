
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.report.PostRepository;
import tech.artcoded.websitev2.pages.report.ChannelService;

import java.io.IOException;

@ChangeUnit(id = "add-channel-to-post", order = "59", author = "Nordine Bittich")
@Slf4j
public class CHANGE_LOG_59_AddChannelToExistingPost {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(PostRepository postRepository, ChannelService channelService) throws IOException {

    postRepository.findAll().stream().map(f -> f.toBuilder()
        .channelId(channelService.createChannel(f.getId()).getId())
        .bookmarked(false).build()).forEach(postRepository::save);

  }

}
