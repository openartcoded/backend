package tech.artcoded.websitev2.changelogs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import tech.artcoded.websitev2.pages.cv.entity.*;
import tech.artcoded.websitev2.pages.cv.repository.CurriculumRepository;

import static java.util.Arrays.asList;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@ChangeUnit(id = "create-cv-if-not-exist", order = "1", author = "Nordine Bittich")
public class CHANGE_LOG_01_CreateCvIfNotExistChangeLog {

    @Execution
    public void execute(CurriculumRepository repository, Environment environment) {
        var fixtureCv = environment.getProperty("application.fixture.cv", Boolean.class, Boolean.FALSE);
        var experience = new ClassPathResource("cv/experience.json");
        var hobby = new ClassPathResource("cv/hobby.json");
        var person = new ClassPathResource("cv/person.json");
        var skills = new ClassPathResource("cv/skills.json");
        var personalProject = new ClassPathResource("cv/personal-project.json");
        var scholarHistory = new ClassPathResource("cv/scholar-history.json");
        var mapper = new ObjectMapper();
        if (fixtureCv || repository.count() == 0) {
            repository.deleteAll();
            Curriculum cv = toSupplier(() -> Curriculum.builder()
                    .experiences(asList(mapper.readValue(experience.getInputStream(), Experience[].class)))
                    .hobbies(asList(mapper.readValue(hobby.getInputStream(), Hobby[].class)))
                    .personalProjects(
                            asList(mapper.readValue(personalProject.getInputStream(), PersonalProject[].class)))
                    .scholarHistories(asList(mapper.readValue(scholarHistory.getInputStream(), ScholarHistory[].class)))
                    .skills(asList(mapper.readValue(skills.getInputStream(), Skill[].class)))
                    .person(mapper.readValue(person.getInputStream(), Person.class)).build()).get();
            repository.save(cv);
        }
    }

    @RollbackExecution
    public void rollbackExecution(CurriculumRepository repository) {

    }
}
