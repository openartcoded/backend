package tech.artcoded.websitev2.pages.report;

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

import jakarta.servlet.http.HttpServletRequest;
import tech.artcoded.websitev2.pages.postit.PostIt;
import tech.artcoded.websitev2.rest.util.PdfToolBox;
import tech.artcoded.websitev2.rest.util.RestUtil;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.utils.func.CheckedFunction;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/report")
@Slf4j
public class ReportController {
  private final MongoTemplate mongoTemplate;

  private final PostRepository repository;
  private final PostService postService;
  private final PostTagRepository postTagRepository;
  private final IFileUploadService fileUploadService;

  @Inject
  public ReportController(MongoTemplate mongoTemplate, PostRepository repository, PostTagRepository postTagRepository,
      IFileUploadService fileUploadService, PostService postService) {
    this.mongoTemplate = mongoTemplate;
    this.repository = repository;
    this.postService = postService;
    this.postTagRepository = postTagRepository;
    this.fileUploadService = fileUploadService;
  }

  @PostMapping("/new-post")
  public Post newPost() {
    return repository.save(Post.builder().draft(true).title("Draft").content("Content here").build());
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

  @PostMapping(value = "/add-attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Post> addAttachment(@RequestParam("id") String id,
      @RequestPart("files") MultipartFile[] file) {
    return ResponseEntity.ok(this.postService.addAttachment(id, file));
  }

  @PostMapping(value = "/toggle-process-attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
      @RequestParam("content") String content, @RequestParam("author") String author,
      @RequestParam(value = "draft", defaultValue = "false") boolean draft,
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

    Post save = repository.save(post.toBuilder().tags(tags).author(author).title(title).content(content)
        .description(description).updatedDate(new Date()).draft(draft).coverId(coverId).build());

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

  @GetMapping("/post/{title}/{id}")
  public ResponseEntity<Post> getPublicPostById(@PathVariable("title") String title, @PathVariable("id") String id,
      HttpServletRequest request) {
    return repository.findById(id).filter(post -> !post.isDraft()).map(ResponseEntity::ok)
        .orElseGet(ResponseEntity.noContent()::build);
  }

  @GetMapping("/latest")
  public ResponseEntity<Page<Post>> getLatest() {
    var pageable = PageRequest.of(0, 3);
    return ResponseEntity
        .ok(this.repository.findByDraftIsAndCoverIdIsNotNullOrderByUpdatedDateDesc(false, pageable));
  }

  @GetMapping("/generate-pdf")
  public ResponseEntity<ByteArrayResource> generatePdf(@RequestParam("id") String id) {
    return this.repository.findById(id).map(Post::getContent).map(md -> {
      Parser parser = Parser.builder().build();
      Node document = parser.parse(md);
      HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(false).build();
      return renderer.render(document);
    }).map(CheckedFunction.toFunction(PdfToolBox::generatePDFFromHTML))
        .map(bytes -> RestUtil.transformToByteArrayResource("post%s.pdf".formatted(IdGenerators.get()),
            MediaType.APPLICATION_PDF_VALUE, bytes))
        .orElseGet(ResponseEntity.badRequest()::build);

  }

  @PostMapping("/public-search")
  public Page<Post> publicSearch(@RequestBody PostSearchCriteria searchCriteria, Pageable pageable) {
    return search(searchCriteria, false, pageable);
  }

  @PostMapping("/admin-search")
  public Page<Post> adminSearch(@RequestBody PostSearchCriteria searchCriteria, Pageable pageable) {
    return search(searchCriteria, null, pageable);
  }

  private Page<Post> search(PostSearchCriteria searchCriteria, Boolean draft, Pageable pageable) {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria criteria = new Criteria();

    if (StringUtils.isNotEmpty(searchCriteria.getTitle())) {
      criteriaList.add(Criteria.where("title").regex(".*%s.*".formatted(searchCriteria.getTitle()), "i"));
    }

    if (StringUtils.isNotEmpty(searchCriteria.getContent())) {
      criteriaList.add(new Criteria().orOperator(
          Criteria.where("title").regex(".*%s.*".formatted(searchCriteria.getContent()), "i"),
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

    if (searchCriteria.getTag() != null) {
      criteriaList.add(Criteria.where("tags").in(searchCriteria.getTag()));
    }

    if (!criteriaList.isEmpty()) {
      criteria = criteria.orOperator(criteriaList.toArray(new Criteria[0]));
    }

    if (draft != null) {
      criteria = criteria.andOperator(Criteria.where("draft").is(draft));
    }
    Query query = Query.query(criteria).with(pageable);
    long count = mongoTemplate.count(Query.query(criteria), Post.class);
    List<Post> posts = mongoTemplate.find(query, Post.class);

    return PageableExecutionUtils.getPage(posts, pageable, () -> count);
  }

}
