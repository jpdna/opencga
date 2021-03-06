package org.opencb.opencga.storage.mongodb.variant;

import com.google.common.collect.Lists;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.opencb.biodata.models.variant.VariantSourceEntry;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.commons.utils.CryptoUtils;

/**
 *
 * @author Cristina Yenyxe Gonzalez Garcia <cyenyxe@ebi.ac.uk>
 */
public class DBObjectToVariantConverterTest {
    
    private BasicDBObject mongoVariant;
    private Variant variant;
    
    @Before
    public void setUp() {
        variant = new Variant("1", 1000, 1000, "A", "C");
        variant.setId("rs666");
        
        mongoVariant = new BasicDBObject("_id", "1_1000_A_C")
                .append(DBObjectToVariantConverter.ID_FIELD, variant.getId())
                .append(DBObjectToVariantConverter.TYPE_FIELD, variant.getType().name())
                .append(DBObjectToVariantConverter.CHROMOSOME_FIELD, variant.getChromosome())
                .append(DBObjectToVariantConverter.START_FIELD, variant.getStart())
                .append(DBObjectToVariantConverter.END_FIELD, variant.getStart())
                .append(DBObjectToVariantConverter.LENGTH_FIELD, variant.getLength())
                .append(DBObjectToVariantConverter.REFERENCE_FIELD, variant.getReference())
                .append(DBObjectToVariantConverter.ALTERNATE_FIELD, variant.getAlternate());
        
        BasicDBList chunkIds = new BasicDBList();
        chunkIds.add("1_1_1k");
        chunkIds.add("1_0_10k");
        mongoVariant.append("_at", new BasicDBObject("chunkIds", chunkIds));
        
        BasicDBList hgvs = new BasicDBList();
        hgvs.add(new BasicDBObject("type", "genomic").append("name", "1:g.1000A>C"));
        mongoVariant.append("hgvs", hgvs);
    }
    
    @Test
    public void testConvertToDataModelTypeWithFiles() {
        VariantSourceEntry file = new VariantSourceEntry("f1", "s1");
        file.addAttribute("QUAL", "0.01");
        file.addAttribute("AN", "2");
        file.setFormat("GT:DP");
        
        Map<String, String> na001 = new HashMap<>();
        na001.put("GT", "0/0");
        na001.put("DP", "4");
        file.addSampleData("NA001", na001);
        Map<String, String> na002 = new HashMap<>();
        na002.put("GT", "0/1");
        na002.put("DP", "5");
        file.addSampleData("NA002", na002);
        variant.addSourceEntry(file);
        
        // MongoDB object
        BasicDBObject mongoFile = new BasicDBObject(DBObjectToVariantSourceEntryConverter.FILEID_FIELD, file.getFileId())
                .append(DBObjectToVariantSourceEntryConverter.STUDYID_FIELD, file.getStudyId());
        mongoFile.append(DBObjectToVariantSourceEntryConverter.ATTRIBUTES_FIELD, 
                new BasicDBObject("QUAL", "0.01").append("AN", "2"));
        mongoFile.append(DBObjectToVariantSourceEntryConverter.FORMAT_FIELD, file.getFormat());
        BasicDBObject genotypeCodes = new BasicDBObject();
        genotypeCodes.append("def", "0/0");
        genotypeCodes.append("0/1", Arrays.asList(1));
        mongoFile.append(DBObjectToVariantSourceEntryConverter.SAMPLES_FIELD, genotypeCodes);
        BasicDBList files = new BasicDBList();
        files.add(mongoFile);
        mongoVariant.append("files", files);
        
        List<String> sampleNames = Lists.newArrayList("NA001", "NA002");
        DBObjectToVariantConverter converter = new DBObjectToVariantConverter(
                new DBObjectToVariantSourceEntryConverter(
                        true,
                        new DBObjectToSamplesConverter(sampleNames),
                        new DBObjectToVariantStatsConverter()));
        Variant converted = converter.convertToDataModelType(mongoVariant);
        assertEquals(variant, converted);
    }

    @Test
    public void testConvertToStorageTypeWithFiles() {
        VariantSourceEntry file = new VariantSourceEntry("f1", "s1");
        file.addAttribute("QUAL", "0.01");
        file.addAttribute("AN", "2");
        file.setFormat("GT:DP");
        
        Map<String, String> na001 = new HashMap<>();
        na001.put("GT", "0/0");
        na001.put("DP", "4");
        file.addSampleData("NA001", na001);
        Map<String, String> na002 = new HashMap<>();
        na002.put("GT", "0/1");
        na002.put("DP", "5");
        file.addSampleData("NA002", na002);
        variant.addSourceEntry(file);
        
        // MongoDB object
        BasicDBObject mongoFile = new BasicDBObject(DBObjectToVariantSourceEntryConverter.FILEID_FIELD, file.getFileId())
                .append(DBObjectToVariantSourceEntryConverter.STUDYID_FIELD, file.getStudyId());
        mongoFile.append(DBObjectToVariantSourceEntryConverter.ATTRIBUTES_FIELD, 
                new BasicDBObject("QUAL", "0.01").append("AN", "2"));
        mongoFile.append(DBObjectToVariantSourceEntryConverter.FORMAT_FIELD, file.getFormat());
        BasicDBObject genotypeCodes = new BasicDBObject();
        genotypeCodes.append("def", "0/0");
        genotypeCodes.append("0/1", Arrays.asList(1));
        mongoFile.append(DBObjectToVariantSourceEntryConverter.SAMPLES_FIELD, genotypeCodes);
        BasicDBList files = new BasicDBList();
        files.add(mongoFile);
        mongoVariant.append("files", files);
        
        List<String> sampleNames = Lists.newArrayList("NA001", "NA002");
        DBObjectToVariantConverter converter = new DBObjectToVariantConverter(new DBObjectToVariantSourceEntryConverter(
                true,
                new DBObjectToSamplesConverter(sampleNames),
                new DBObjectToVariantStatsConverter()));
        DBObject converted = converter.convertToStorageType(variant);
        assertEquals(mongoVariant, converted);
    }

    @Test
    public void testConvertToDataModelTypeWithoutFiles() {
        DBObjectToVariantConverter converter = new DBObjectToVariantConverter();
        Variant converted = converter.convertToDataModelType(mongoVariant);
        assertEquals(variant, converted);
    }

    @Test
    public void testConvertToStorageTypeWithoutFiles() {
        DBObjectToVariantConverter converter = new DBObjectToVariantConverter();
        DBObject converted = converter.convertToStorageType(variant);
        assertEquals(mongoVariant, converted);
    }

    @Test
    public void testBuildStorageId() {
        DBObjectToVariantConverter converter = new DBObjectToVariantConverter();
        
        // SNV
        Variant v1 = new Variant("1", 1000, 1000, "A", "C");
        assertEquals("1_1000_A_C", converter.buildStorageId(v1));
        
        // Indel
        Variant v2 = new Variant("1", 1000, 1002, "", "CA");
        assertEquals("1_1000__CA", converter.buildStorageId(v2));
        
        // Structural
        String alt = "ACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTACGT";
        Variant v3 = new Variant("1", 1000, 1002, "TAG", alt);
        assertEquals("1_1000_TAG_" + new String(CryptoUtils.encryptSha1(alt)), converter.buildStorageId(v3));
    }
    
}
