package org.opencb.opencga.catalog;

import junit.framework.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opencb.biodata.models.pedigree.Individual;
import org.opencb.biodata.models.pedigree.Pedigree;
import org.opencb.commons.test.GenericTest;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.catalog.*;
import org.opencb.opencga.catalog.beans.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class CatalogSampleAnnotationsLoaderTest extends GenericTest {

    private static CatalogSampleAnnotationsLoader loader;
    private static final List<String> populations = Arrays.asList("ACB", "ASW", "BEB", "CDX", "CEU", "CHB", "CHS", "CLM", "ESN", "FIN", "GBR", "GIH", "GWD", "IBS", "ITU", "JPT", "KHV", "LWK", "MSL", "MXL", "PEL", "PJL", "PUR", "STU", "TSI", "YRI");
    private static Pedigree pedigree;
    private static String sessionId;
    private static File pedFile;
    private static CatalogManager catalogManager;
    private static String userId;
    private static int studyId;

    @BeforeClass
    public static void beforeClass() throws IOException, CatalogException, URISyntaxException {
        Properties catalogProperties = new Properties();
        catalogProperties.load(CatalogSampleAnnotationsLoader.class.getClassLoader().getResourceAsStream("catalog.properties"));
        catalogManager = new CatalogManager(catalogProperties);
        loader = new CatalogSampleAnnotationsLoader(catalogManager);


        String pedFileName = "20130606_g1k.ped";
        URL pedFileURL = CatalogSampleAnnotationsLoader.class.getClassLoader().getResource(pedFileName);
        pedigree = loader.readPedigree(pedFileURL.getPath());

        ObjectMap session = catalogManager.loginAsAnonymous("localHost").getResult().get(0);
        sessionId = session.getString("sessionId");
        userId = session.getString("userId");
        Project project = catalogManager.createProject(userId, "default", "def", "", "ACME", null, sessionId).getResult().get(0);
        Study study = catalogManager.createStudy(project.getId(), "default", "def", Study.Type.FAMILY, "", sessionId).getResult().get(0);
        studyId = study.getId();
        pedFile = catalogManager.createFile(studyId, File.Format.PLAIN, File.Bioformat.OTHER_PED, "data/" + pedFileName, "", false, -1, sessionId).getResult().get(0);
        new CatalogFileManager(catalogManager).upload(pedFileURL.toURI(), pedFile, null, sessionId, false, false, false, true, 10000000);
        pedFile = catalogManager.getFile(pedFile.getId(), sessionId).getResult().get(0);
    }

    @AfterClass
    public static void afterClass() throws CatalogException {
//        catalogManager.logout(userId, sessionId);
    }

    @Test
    public void testLoadPedigree_GeneratedVariableSet() throws Exception {
        URL pedFile = this.getClass().getClassLoader().getResource("20130606_g1k.ped");

        Pedigree pedigree = loader.readPedigree(pedFile.getPath());
        VariableSet variableSet = loader.getVariableSetFromPedFile(pedigree);

        validate(pedigree, variableSet);
    }

    @Test
    public void testLoadPedigree_GivenVariableSet() throws Exception {
        HashSet<Variable> variables = new HashSet<>();
        variables.add(new Variable("id", "", Variable.VariableType.NUMERIC, null, true, Collections.<String>emptyList(), 0, null, "", null));
        variables.add(new Variable("name", "", Variable.VariableType.TEXT, null, true, Collections.<String>emptyList(), 0, null, "", null));
        variables.add(new Variable("fatherId", "", Variable.VariableType.NUMERIC, null, true, Collections.<String>emptyList(), 0, null, "", null));
        variables.add(new Variable("Population", "", Variable.VariableType.CATEGORICAL, null, true, populations, 0, null, "", null));

        VariableSet variableSet = new VariableSet(5, "", false, "", variables, null);

        validate(pedigree, variableSet);
    }

    @Test
    public void testLoadPedigreeCatalog() throws Exception {
        QueryResult<Sample> sampleQueryResult = loader.loadSampleAnnotations(pedFile, null, sessionId);
        int variableSetId = sampleQueryResult.getResult().get(0).getAnnotationSets().get(0).getVariableSetId();

//        int variableSetId ;//= sampleQueryResult.getResult().get(0).getAnnotationSets().get(0).getVariableSetId();
//        sessionId = "nIXANk1L8EmLCRhOwiZQ";
//        studyId = 2;
//        variableSetId = 7;

        QueryOptions options = new QueryOptions("variableSetId", variableSetId);

        options.put("annotation", "family:GB84");
        options.put("limit", 2);
        System.out.println(catalogManager.getAllSamples(studyId, options, sessionId));

        options.put("annotation", "sex:2,Population:ITU");
        QueryResult<Sample> femaleIta = catalogManager.getAllSamples(studyId, options, sessionId);
        System.out.println(femaleIta);

        options.put("annotation", "sex:1,Population:ITU");
        QueryResult<Sample> maleIta = catalogManager.getAllSamples(studyId, options, sessionId);
        System.out.println(maleIta);

        options.put("annotation", "Population:ITU");
        QueryResult<Sample> ita = catalogManager.getAllSamples(studyId, options, sessionId);
        System.out.println(ita);

        Assert.assertEquals("Fail sample query", ita.getNumTotalResults(), maleIta.getNumTotalResults() + femaleIta.getNumTotalResults());
    }



    private void validate(Pedigree pedigree, VariableSet variableSet) throws CatalogException {
        for (Map.Entry<String, Individual> entry : pedigree.getIndividuals().entrySet()) {
            Map<String, Object> annotation = loader.getAnnotation(entry.getValue(), null, variableSet, pedigree.getFields());
            HashSet<Annotation> annotationSet = new HashSet<>(annotation.size());
            for (Map.Entry<String, Object> annotationEntry : annotation.entrySet()) {
                annotationSet.add(new Annotation(annotationEntry.getKey(), annotationEntry.getValue()));
            }
            CatalogSampleAnnotationsValidator.checkAnnotationSet(variableSet, new AnnotationSet("", variableSet.getId(), annotationSet, "", null), null);
        }
    }
}