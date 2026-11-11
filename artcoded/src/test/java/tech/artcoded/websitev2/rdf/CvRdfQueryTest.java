package tech.artcoded.websitev2.rdf;

import org.apache.commons.io.IOUtils;
import org.apache.jena.update.UpdateFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tech.artcoded.websitev2.pages.cv.entity.Curriculum;
import tech.artcoded.websitev2.pages.cv.entity.Experience;
import tech.artcoded.websitev2.pages.cv.entity.Hobby;
import tech.artcoded.websitev2.pages.cv.entity.Person;
import tech.artcoded.websitev2.pages.cv.entity.PersonalProject;
import tech.artcoded.websitev2.pages.cv.entity.ScholarHistory;
import tech.artcoded.websitev2.pages.cv.entity.Skill;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CvRdfQueryTest {

    private static final String GRAPH = "https://bittich.be/graphs/public";

    private static String query(Curriculum cv) throws Exception {
        String template = IOUtils.toString(new ClassPathResource("templates/cv-rdf.ftl").getInputStream(),
                StandardCharsets.UTF_8);
        return SparqlQueryStore.computeQueryWithParameters(template, Map.of("graph", GRAPH, "cv", cv));
    }

    @Test
    void fullCvWithSpecialCharactersIsValidSparql() throws Exception {
        var cv = Curriculum.builder().introduction("I say \"hello\"\nwith a \\ backslash and \"\"\"triple\"\"\"")
                .person(Person.builder().firstname("Nordine").lastname("O\"Bittich").title("Dev").birthdate(new Date())
                        .linkedinUrl("https://linkedin.com/in/me").githubUrl("https://github.com/me")
                        .website("https://bittich.be").build())
                .skills(List.of(Skill.builder().name("Java \"21\"").hardSkill(true).tags(List.of("a\"b", "c")).build()))
                .personalProjects(List.of(PersonalProject.builder().name("file-search").url("https://github.com/x")
                        .description("search \"files\"").build()))
                .scholarHistories(
                        List.of(ScholarHistory.builder().school("ULB").title("Master").from(new Date()).build()))
                .experiences(List.of(Experience.builder().company("Artcoded").title("Owner").from(new Date())
                        .current(true).description(List.of("line \"1\"", "line 2")).build()))
                .build();

        String query = query(cv);

        assertDoesNotThrow(() -> UpdateFactory.create(query), query);
        // personal projects are linked to themselves, and the name is the title
        assertTrue(query.contains("cv:hasOtherInfo <https://bittich.be/cv/personal-project/" + cv.getId() + "-1>"),
                query);
        assertTrue(query.contains("foaf:title \"file-search\""), query);
        assertFalse(query.contains("/cv/hobby/"), query);
    }

    @Test
    void cvWithMissingValuesIsValidSparql() throws Exception {
        // no hobby but a personal project: used to fail on the undefined hobbyId
        var cv = Curriculum.builder().introduction(null).person(Person.builder().firstname("Nordine").build())
                .personalProjects(List.of(PersonalProject.builder().build()))
                .scholarHistories(List.of(ScholarHistory.builder().build()))
                .experiences(List.of(Experience.builder().build())).skills(List.of(Skill.builder().build()))
                .hobbies(List.of(Hobby.builder().build())).build();

        String query = query(cv);

        assertDoesNotThrow(() -> UpdateFactory.create(query), query);
    }

    @Test
    void cvWithoutPersonIsValidSparql() throws Exception {
        String query = query(Curriculum.builder().person(null).build());

        assertDoesNotThrow(() -> UpdateFactory.create(query), query);
    }
}
