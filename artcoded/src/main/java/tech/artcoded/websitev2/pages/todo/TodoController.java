package tech.artcoded.websitev2.pages.todo;


import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/todo")
@Slf4j
public class TodoController {
  private final TodoRepository todoRepository;

  public TodoController(TodoRepository todoRepository) {
    this.todoRepository = todoRepository;
  }

  @GetMapping
  public List<Todo> findAll() {
    return todoRepository.findAll();
  }

  @PostMapping
  public Todo saveOrUpdate(@RequestBody Todo todo) {
    return todoRepository.save(Optional.ofNullable(todo.getId()).flatMap(todoRepository::findById)
                                       .map(Todo::toBuilder)
                                       .orElseGet(Todo::builder)
                                       .title(todo.getTitle())
                                       .updatedDate(new Date())
                                       .done(todo.isDone())
                                       .build());
  }

  @DeleteMapping
  public void delete(@RequestParam("id") String id) {
    todoRepository.deleteById(id);
  }

}
