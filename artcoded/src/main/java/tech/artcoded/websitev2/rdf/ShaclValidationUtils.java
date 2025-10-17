package tech.artcoded.websitev2.rdf;

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.sparql.graph.GraphFactory;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.Optional;

public interface ShaclValidationUtils {

    static Optional<String> validate(InputStream dataModel, Lang modelLang, InputStream shapesModel, Lang shapesLang) {
        StringWriter writer = new StringWriter();
        Graph shapesGraph = GraphFactory.createDefaultGraph();
        RDFParser.source(shapesModel).base("").lang(shapesLang).build().parse(shapesGraph);
        Graph dataGraph = GraphFactory.createDefaultGraph();
        RDFParser.source(dataModel).base("").lang(modelLang).build().parse(dataGraph);

        Shapes shapes = Shapes.parse(shapesGraph);

        ValidationReport report = ShaclValidator.get().validate(shapes, dataGraph);

        if (report.conforms()) {
            return Optional.empty();
        }
        RDFDataMgr.write(writer, report.getModel(), Lang.JSONLD);
        return Optional.of(writer.toString());
    }
}
