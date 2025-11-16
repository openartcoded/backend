package tech.artcoded.websitev2.pages.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.websitev2.pages.personal.User;
import tech.artcoded.websitev2.pages.postit.PostIt;
import tech.artcoded.websitev2.pages.report.Post.PostStatus;
import tech.artcoded.websitev2.pages.report.Post.Priority;
import tech.artcoded.websitev2.rest.util.PdfToolBox;
import tech.artcoded.websitev2.rest.util.RestUtil;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.utils.func.CheckedFunction;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
@Slf4j
public class ReportController {
  private final MongoTemplate mongoTemplate;

  private final PostRepository repository;
  private final ChannelService channelService;
  private final PostService postService;
  private final PostTagRepository postTagRepository;
  private final IFileUploadService fileUploadService;

  @PostMapping("/new-post")
  public Post newPost(Principal principal) {
    var user = User.fromPrincipal(principal);
    var id = IdGenerators.get().replace("-", "");
    var post = Post.builder().status(PostStatus.IN_PROGRESS)
        .id(IdGenerators.get())
        .priority(Priority.MEDIUM)
        .author(user.getEmail())
        .channelId(channelService.createChannel(id).getId())
        .title("Draft").content("Content here").build();
    return repository.save(post);
  }

  public record PostIts(Set<PostIt> todos, Set<PostIt> inProgress, Set<PostIt> done) {
  }

  @PostMapping(value = "/update-post-it")
  public ResponseEntity<Post> updateTodos(@RequestParam(value = "id", required = true) String id,
      @RequestBody PostIts postIts) {
    return repository.findById(id).map(existing -> {

      var builder = existing.toBuilder().updatedDate(new Date());

      builder.todos(postIts.todos());
      builder.inProgress(postIts.inProgress());
      builder.done(postIts.done());

      return repository.save(builder.build());
    }).map(ResponseEntity::ok).orElseGet(ResponseEntity.noContent()::build);

  }

  @GetMapping("/bookmarked")
  public ResponseEntity<Page<Post>> bookmarked(Pageable pageable) {
    return ResponseEntity.ok(postService.getBookmarked(pageable));
  }

  @PostMapping("/toggle-bookmarked")
  public ResponseEntity<Post> toggleBookmarked(@RequestParam("id") String id) {
    return postService.toggleBookmarked(id).map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping(value = "/add-attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Post> addAttachment(@RequestParam("id") String id,
      @RequestPart("files") MultipartFile[] file) {
    return ResponseEntity.ok(this.postService.addAttachment(id, file));
  }

  @PostMapping(value = "/toggle-process-attachment")
  public ResponseEntity<Post> toggleProcessedAttachment(@RequestParam("id") String id,
      @RequestParam("attachmentId") String attachmentId) {
    return ResponseEntity.ok(this.postService.toggleProcessAttachment(id, attachmentId));
  }

  @PostMapping("/remove-attachment")
  public ResponseEntity<Post> removeAttachment(@RequestParam("id") String postId,
      @RequestParam("attachmentId") String attachmentId) {
    return ResponseEntity.ok(this.postService.removeAttachment(postId, attachmentId));
  }

  @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Post> save(@RequestParam(value = "id") String id, @RequestParam("title") String title,
      @RequestParam("description") String description, @RequestParam("tags") Set<String> tags,
      @RequestParam("priority") Priority priority,
      @RequestParam("content") String content, @RequestParam("author") String author,
      @RequestParam(value = "status") PostStatus status,
      @RequestPart(value = "cover", required = false) MultipartFile cover) {

    Post post = Optional.ofNullable(id).filter(StringUtils::isNotEmpty).flatMap(this.repository::findById)
        .orElseGet(() -> Post.builder().build());

    String coverId = post.getCoverId();

    if (cover != null && !cover.isEmpty()) {
      coverId = this.fileUploadService.upload(cover, post.getId(), false);
    }

    if (StringUtils.isNotEmpty(coverId) && !coverId.equals(post.getCoverId())) {
      fileUploadService.delete(post.getCoverId());
    }

    Post save = repository.save(post.toBuilder().tags(tags)
        .priority(priority)
        .author(author).title(title).content(content)
        .description(description).updatedDate(new Date()).status(status).coverId(coverId).build());

    tags.stream().map(PostTag::new).forEach(postTagRepository::save);

    return ResponseEntity.ok(save);
  }

  @DeleteMapping
  public ResponseEntity<Map.Entry<String, String>> delete(@RequestParam("id") String id) {
    return repository.findById(id).map(post -> {
      fileUploadService.deleteByCorrelationId(id);
      repository.delete(post);
      return ResponseEntity.ok(Map.entry("message", "post with id %s deleted".formatted(id)));
    }).orElseGet(ResponseEntity.noContent()::build);
  }

  @GetMapping("/tags")
  public List<String> getTags() {
    return this.postTagRepository.findAll().stream().map(PostTag::getTag).collect(Collectors.toList());
  }

  @PostMapping("/post-by-id")
  public ResponseEntity<Post> getPostById(@RequestParam("id") String id) {
    return this.repository.findById(id).map(ResponseEntity::ok).orElseGet(ResponseEntity.noContent()::build);
  }

  @PostMapping("/channel/subscribe")
  public ResponseEntity<Channel> subscribe(@RequestParam("id") String id, Principal principal) {
    var user = User.fromPrincipal(principal);
    return this.channelService.getChannelByCorrelationId(id)
        .map(ch -> ch.toBuilder()
            .subscribers(
                Stream.concat(ch.getSubscribers().stream(), Stream.of(user.getEmail())).distinct().toList())
            .build())
        .flatMap(ch -> channelService.updateChannel(ch))
        .map(ResponseEntity::ok).orElseGet(ResponseEntity.noContent()::build);
  }

  @PostMapping("/channel/get")
  public ResponseEntity<Channel> getChannelByCorrelationId(@RequestParam("id") String id, Principal principal) {
    return this.channelService.getChannelByCorrelationId(id)
        .map(ResponseEntity::ok).orElseGet(ResponseEntity.noContent()::build);
  }

  @PostMapping("/channel/post")
  public void postMessage(@RequestParam("id") String id,
      @RequestParam("message") String message,
      @RequestPart("files") MultipartFile[] attachments,
      Principal principal) {
    var user = User.fromPrincipal(principal);
    this.channelService.getChannelByCorrelationId(id)
        .ifPresent(ch -> {
          var uploadIds = fileUploadService.uploadAll(Arrays.asList(attachments), ch.getId(), false);
          var msg = new Channel.Message(IdGenerators.get(), new Date(), user.getEmail(), message, uploadIds, false);
          channelService.addMessage(ch.getId(), msg);
        });
  }

  @DeleteMapping("/channel/message")
  public ResponseEntity<Void> deleteMessage(@RequestParam("id") String id,
      @RequestParam("messageId") String messageId,
      Principal principal) {
    var user = User.fromPrincipal(principal);
    var ch = this.channelService.getChannelByCorrelationId(id)
        .orElseThrow(() -> new RuntimeException("channel not found"));
    if (ch.getMessages().stream().noneMatch(m -> m.id().equals(id) && m.emailFrom().equals(user.getEmail()))) {
      return ResponseEntity.badRequest().build();
    }
    channelService.deleteMessage(ch.getId(), messageId);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/latest")
  public ResponseEntity<Page<Post>> getLatest() {
    var pageable = PageRequest.of(0, 3);
    return ResponseEntity
        .ok(this.repository.findByOrderByUpdatedDateDesc(pageable));
  }

  @GetMapping("/generate-pdf")
  public ResponseEntity<ByteArrayResource> generatePdf(@RequestParam("id") String id) {
    return this.repository.findById(id).map(post -> {
      String md = """
          ## %s

          > %s
          <hr>

          %s
          """.formatted(post.getTitle(), post.getDescription(), post.getContent());
      Parser parser = Parser.builder().build();
      Node document = parser.parse(md);
      HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(false).build();
      return renderer.render(document);
    }).map(CheckedFunction.toFunction(PdfToolBox::generatePDFFromHTML))
        .map(bytes -> RestUtil.transformToByteArrayResource("post%s.pdf".formatted(IdGenerators.get()),
            MediaType.APPLICATION_PDF_VALUE, bytes))
        .orElseGet(ResponseEntity.badRequest()::build);

  }

  @PostMapping("/admin-search")
  public Page<Post> adminSearch(@RequestBody PostSearchCriteria searchCriteria, Pageable pageable) {
    return search(searchCriteria, pageable);
  }

  private Page<Post> search(PostSearchCriteria searchCriteria, Pageable pageable) {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria criteria = new Criteria();

    if (StringUtils.isNotEmpty(searchCriteria.getTitle()) || StringUtils.isNotEmpty(searchCriteria.getContent())) {
      criteriaList.add(new Criteria().orOperator(
          Criteria.where("title").regex(".*%s.*".formatted(searchCriteria.getTitle()), "i"),
          Criteria.where("content").regex(".*%s.*".formatted(searchCriteria.getContent()), "i")));
    }
    if (searchCriteria.getDateBefore() != null) {
      criteriaList.add(Criteria.where("updatedDate").lt(searchCriteria.getDateBefore()));
    }

    if (searchCriteria.getDateAfter() != null) {
      criteriaList.add(Criteria.where("updatedDate").gt(searchCriteria.getDateAfter()));
    }

    if (StringUtils.isNotEmpty(searchCriteria.getId())) {
      criteriaList.add(Criteria.where("id").is(searchCriteria.getId()));
    }
    if (searchCriteria.getStatus() != null) {
      criteriaList.add(Criteria.where("status").is(searchCriteria.getStatus()));
    }

    if (searchCriteria.getPriority() != null) {
      criteriaList.add(Criteria.where("priority").is(searchCriteria.getPriority()));
    }

    if (searchCriteria.getBookmarked() != null) {
      criteriaList.add(Criteria.where("bookmarked").is(searchCriteria.getBookmarked()));
    }

    if (searchCriteria.getTag() != null) {
      criteriaList.add(Criteria.where("tags").in(searchCriteria.getTag()));
    }

    if (!criteriaList.isEmpty()) {
      criteria = criteria.andOperator(criteriaList.toArray(new Criteria[0]));
    }

    Query query = Query.query(criteria).with(pageable);
    long count = mongoTemplate.count(Query.query(criteria), Post.class);
    List<Post> posts = mongoTemplate.find(query, Post.class);

    return PageableExecutionUtils.getPage(posts, pageable, () -> count);
  }

}
