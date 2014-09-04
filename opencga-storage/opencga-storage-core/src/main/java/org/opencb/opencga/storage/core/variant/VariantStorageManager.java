package org.opencb.opencga.storage.core.variant;

import org.opencb.biodata.formats.pedigree.io.PedigreePedReader;
import org.opencb.biodata.formats.pedigree.io.PedigreeReader;
import org.opencb.biodata.formats.variant.io.VariantReader;
import org.opencb.biodata.formats.variant.io.VariantWriter;
import org.opencb.biodata.formats.variant.vcf4.io.VariantVcfReader;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantAggregatedVcfFactory;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.biodata.models.variant.VariantVcfEVSFactory;
import org.opencb.commons.containers.list.SortedList;
import org.opencb.commons.run.Task;
import org.opencb.opencga.storage.core.StorageManager;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;
import org.opencb.opencga.storage.core.variant.io.json.VariantJsonReader;
import org.opencb.opencga.storage.core.variant.io.json.VariantJsonWriter;
import org.opencb.variant.lib.runners.VariantRunner;
import org.opencb.variant.lib.runners.tasks.VariantEffectTask;
import org.opencb.variant.lib.runners.tasks.VariantStatsTask;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;

/**
 * Created by imedina on 13/08/14.
 */
public abstract class VariantStorageManager /*implements StorageManager<VariantReader, VariantWriter, VariantDBAdaptor>*/ {


    protected Properties properties;

    public VariantStorageManager() {
        this.properties = new Properties();
    }

    //@Override
    public void addPropertiesPath(Path propertiesPath){
        if(propertiesPath != null && propertiesPath.toFile().exists()) {
            try {
                properties.load(new InputStreamReader(new FileInputStream(propertiesPath.toString())));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //@Override
    public VariantWriter getDBWriter(Path credentials, String fileId) {
        return null;
    }

    //@Override
    abstract public VariantDBAdaptor getDBAdaptor(Path credentials);

    //@Override
    public VariantReader getDBSchemaReader(Path input) {
        return null;
    }

    //@Override
    public VariantWriter getDBSchemaWriter(Path output) {
        return null;
    }

    //TODO: Remove VariantSource
    abstract public VariantWriter getDBWriter(Path credentials, VariantSource source);


    /**
     * Transform raw variant files into Biodata model.
     *
     * @param input         Input file. Accepted formats: *.vcf, *.vcf.gz
     * @param pedigree      Pedigree input file. Accepted formats: *.ped
     * @param output
     * @param params
     * @throws IOException
     */
    final public void transform(Path input, Path pedigree, Path output, Map<String, Object> params) throws IOException {
        // input: VcfReader
        // output: JsonWriter

        boolean includeSamples = Boolean.parseBoolean(params.get("includeSamples").toString());
        boolean includeEffect = Boolean.parseBoolean(params.get("includeEffect").toString());
        boolean includeStats = Boolean.parseBoolean(params.get("includeStats").toString());
        String aggregated = (String) params.get("aggregated");
        VariantSource source = new VariantSource(input.getFileName().toString(), params.get("fileId").toString(), params.get("studyId").toString(), params.get("study").toString());

        //Reader
        VariantReader reader;
        PedigreeReader pedReader = null;
        if(pedigree != null && pedigree.toFile().exists()) {    //FIXME Add "endsWith(".ped") ??
            pedReader = new PedigreePedReader(pedigree.toString());
        }

        // TODO Create a utility to determine which extensions are variants files
        if (source.getFileName().endsWith(".vcf") || source.getFileName().endsWith(".vcf.gz")) {
            if (aggregated != null) {
                includeStats = false;
                switch (aggregated.toLowerCase()) {
                    case "basic":
                        reader = new VariantVcfReader(source, input.toAbsolutePath().toString(), new VariantAggregatedVcfFactory());
                        break;
                    case "evs":
                        reader = new VariantVcfReader(source, input.toAbsolutePath().toString(), new VariantVcfEVSFactory());
                        break;
                    default:
                        reader = new VariantVcfReader(source, input.toAbsolutePath().toString());
                        break;

                }
            } else {
                reader = new VariantVcfReader(source, input.toAbsolutePath().toString());
            }
        } else {
            throw new IOException("Variants input file format not supported");
        }

        //Tasks
        List<Task<Variant>> taskList = new SortedList<>();
        if (includeEffect) {
            taskList.add(new VariantEffectTask());
        }
        if (includeStats) {
            taskList.add(new VariantStatsTask(reader, source));
        }

        //Writers
        List<VariantWriter> writers = new ArrayList<>();
        writers.add(new VariantJsonWriter(source, output));

        for (VariantWriter variantWriter : writers) {
            variantWriter.includeSamples(includeSamples);
            variantWriter.includeEffect(includeEffect);
            variantWriter.includeStats(includeStats);
        }

        //Runner
        VariantRunner vr = new VariantRunner(source, reader, pedReader, writers, taskList);

        System.out.println("Indexing variants...");
        vr.run();
        System.out.println("Variants indexed!");
    }


    public void preLoad(Path input, Path output, Map<String, Object> params) throws IOException {
        // input: JsonVariatnReader
        // output: getDBSchemaWriter

        //Writers
        List<VariantWriter> writers = Arrays.asList(this.getDBSchemaWriter(output));
        if(writers.isEmpty()){
            System.out.println("[ALERT] preLoad method not supported in this plugin");
            return;
        }

        VariantSource source = new VariantSource(input.getFileName().toString(), params.get("fileId").toString(), params.get("studyId").toString(), params.get("study").toString());

        //Reader
        String sourceFile = input.toAbsolutePath().toString().replace("variant.json", "file.json");
        VariantReader jsonReader = new VariantJsonReader(source, input.toAbsolutePath().toString() , sourceFile);


        //Tasks
        List<Task<Variant>> taskList = new SortedList<>();


        //Runner
        VariantRunner vr = new VariantRunner(source, jsonReader, null, writers, taskList);

        System.out.println("Indexing variants...");
        vr.run();
        System.out.println("Variants indexed!");

    }


    public void load(Path input, Path credentials, Map<String, Object> params) throws IOException {
        // input: getDBSchemaReader
        // output: getDBWriter()

        boolean includeSamples = Boolean.parseBoolean(params.get("includeSamples").toString());
        boolean includeEffect = Boolean.parseBoolean(params.get("includeEffect").toString());
        boolean includeStats = Boolean.parseBoolean(params.get("includeStats").toString());
        VariantSource source = new VariantSource(input.getFileName().toString(), params.get("fileId").toString(), params.get("studyId").toString(), params.get("study").toString());

        //Reader
        VariantReader variantDBSchemaReader = this.getDBSchemaReader(input);
        if (variantDBSchemaReader == null) {
            if (source.getFileName().endsWith(".json") || source.getFileName().endsWith(".json.gz")) {
                String sourceFile = input.toAbsolutePath().toString().replace("variant.json", "file.json");
                variantDBSchemaReader = new VariantJsonReader(source, input.toAbsolutePath().toString(), sourceFile);
            } else {
                throw new IOException("Variants input file format not supported");
            }
        }

        //Tasks
        List<Task<Variant>> taskList = new SortedList<>();


        //Writers
        VariantWriter variantDBWriter = this.getDBWriter(credentials, source);
        List<VariantWriter> writers = new ArrayList<>();
        writers.add(variantDBWriter);

        for (VariantWriter variantWriter : writers) {
            variantWriter.includeSamples(includeSamples);
            variantWriter.includeEffect(includeEffect);
            variantWriter.includeStats(includeStats);
        }

        //Runner
        VariantRunner vr = new VariantRunner(source, variantDBSchemaReader, null, writers, taskList);

        System.out.println("Indexing variants...");
        vr.run();
        System.out.println("Variants indexed!");
    }


}
