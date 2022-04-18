package tech.artcoded.websitev2.rdf;

import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
public class ModelConverterTest {

  @Test
  public void modelToLang() throws IOException {
    String expectedOutput = """
      {
        "@id" : "http://artcoded.tech/person",
        "artist" : "Nordine Bittich",
        "company" : "Artcoded",
        "country" : "BELGIUM",
        "year" : "1988",
        "@context" : {
          "year" : {
            "@id" : "http://artcoded.tech#year"
          },
          "company" : {
            "@id" : "http://artcoded.tech#company"
          },
          "country" : {
            "@id" : "http://artcoded.tech#country"
          },
          "artist" : {
            "@id" : "http://artcoded.tech#artist"
          },
          "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          "artcoded" : "http://artcoded.tech#"
        }
      }
      """;
    Model expectedModel = ModelFactory.createDefaultModel()
      .read(IOUtils.toInputStream(expectedOutput, StandardCharsets.UTF_8), "", "JSONLD");

    Model model = ModelFactory.createDefaultModel().read(new ClassPathResource("rdf/input.rdf").getInputStream(), "");
    String jsonld = ModelConverter.modelToLang(model, "JSONLD");
    Model actualModel = ModelFactory.createDefaultModel()
      .read(IOUtils.toInputStream(jsonld, StandardCharsets.UTF_8), "", "JSONLD");
    assertTrue(expectedModel.isIsomorphicWith(actualModel));
  }

  @Test
  public void toModel() throws IOException {
    String input = """
      {
        "@id" : "http://artcoded.tech/person",
        "artist" : "Nordine Bittich",
        "company" : "Artcoded",
        "country" : "BELGIUM",
        "year" : "1988",
        "@context" : {
          "year" : {
            "@id" : "http://artcoded.tech#year"
          },
          "company" : {
            "@id" : "http://artcoded.tech#company"
          },
          "country" : {
            "@id" : "http://artcoded.tech#country"
          },
          "artist" : {
            "@id" : "http://artcoded.tech#artist"
          },
          "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          "artcoded" : "http://artcoded.tech#"
        }
      }
      """;
    Model jsonld = ModelConverter.toModel(input, "JSONLD");
    Model model = ModelFactory.createDefaultModel().read(new ClassPathResource("rdf/input.rdf").getInputStream(), "");
    assertTrue(jsonld.isIsomorphicWith(model));

  }

  @Test
  public void testEquals() throws IOException {
    String input = """
      {
        "@id" : "http://artcoded.tech/person",
        "artist" : "Nordine Bittich",
        "company" : "Artcoded",
        "country" : "BELGIUM",
        "year" : "1988",
        "@context" : {
          "year" : {
            "@id" : "http://artcoded.tech#year"
          },
          "company" : {
            "@id" : "http://artcoded.tech#company"
          },
          "country" : {
            "@id" : "http://artcoded.tech#country"
          },
          "artist" : {
            "@id" : "http://artcoded.tech#artist"
          },
          "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          "artcoded" : "http://artcoded.tech#"
        }
      }
      """;
    Model jsonld = ModelConverter.toModel(input, "JSONLD");
    Model model = ModelFactory.createDefaultModel().read(new ClassPathResource("rdf/input.rdf").getInputStream(), "");
    assertTrue(ModelConverter.equals(jsonld, model));
  }

  @Test
  public void difference() throws IOException {
    String input = """
      {
        "@id" : "http://artcoded.tech/person",
        "artist" : "JEAN PIERRE",
        "company" : "Artcoded",
        "country" : "BELGIUM",
        "year" : "2010",
        "@context" : {
          "year" : {
            "@id" : "http://artcoded.tech#year"
          },
          "company" : {
            "@id" : "http://artcoded.tech#company"
          },
          "country" : {
            "@id" : "http://artcoded.tech#country"
          },
          "artist" : {
            "@id" : "http://artcoded.tech#artist"
          },
          "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          "artcoded" : "http://artcoded.tech#"
        }
      }
      """;
    String expectedDiff = """
      {
        "@id" : "http://artcoded.tech/person",
        "artist" : "JEAN PIERRE",
        "year" : "2010",
        "@context" : {
          "artist" : {
            "@id" : "http://artcoded.tech#artist"
          },
          "year" : {
            "@id" : "http://artcoded.tech#year"
          }
        }
      }

      """;
    Model jsonld = ModelConverter.toModel(input, "JSONLD");
    Model expectedDifference = ModelConverter.toModel(expectedDiff, "JSONLD");
    Model model = ModelFactory.createDefaultModel().read(new ClassPathResource("rdf/input.rdf").getInputStream(), "");
    Model difference = ModelConverter.difference(jsonld, model);
    assertTrue(ModelConverter.equals(difference, expectedDifference));
  }


  @Test
  public void inputStreamToLang() {
    String model = ModelConverter.inputStreamToLang("input.rdf", () -> new ClassPathResource("rdf/input.rdf").getInputStream(), "JSONLD");
    String expectedOutput = """
      {
        "@id" : "http://artcoded.tech/person",
        "artist" : "Nordine Bittich",
        "company" : "Artcoded",
        "country" : "BELGIUM",
        "year" : "1988",
        "@context" : {
          "year" : {
            "@id" : "http://artcoded.tech#year"
          },
          "company" : {
            "@id" : "http://artcoded.tech#company"
          },
          "country" : {
            "@id" : "http://artcoded.tech#country"
          },
          "artist" : {
            "@id" : "http://artcoded.tech#artist"
          },
          "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          "artcoded" : "http://artcoded.tech#"
        }
      }
      """;
    Model expectedModel = ModelConverter.toModel(expectedOutput, "JSONLD");
    Model actualModel = ModelConverter.toModel(model, "JSONLD");
    assertTrue(ModelConverter.equals(actualModel, expectedModel));

  }

  @Test
  public void inputStreamToModel() {
    Model model = ModelConverter.inputStreamToModel("input.rdf", () -> new ClassPathResource("rdf/input.rdf").getInputStream());
    assertFalse(model.isEmpty());
  }

}
