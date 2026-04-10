package com.ditadesigner;

import com.ditadesigner.generator.CatalogGenerator;
import com.ditadesigner.generator.DtdGenerator;
import com.ditadesigner.generator.XsdGenerator;
import com.ditadesigner.model.DitaModel;
import com.ditadesigner.repository.ProjectRepository;
import com.ditadesigner.service.ProjectService;

import java.io.File;

/**
 * CLI entry point to generate the phxTask sample specialization artefacts
 * (XSD, DTD, catalog.xml, .ddp) to the sample-output/ directory.
 *
 * Run via: ./gradlew generateSample
 */
public class SampleGenerator {

    public static void main(String[] args) throws Exception {
        ProjectService projectService = new ProjectService();
        DitaModel model = projectService.createSamplePhxTask();
        model.setCopyrightOwner("Phoenix Engineering");
        model.setVersion("1.0");

        File outDir = new File("sample-output");
        outDir.mkdirs();

        System.out.println("Generating phxTask sample to: " + outDir.getAbsolutePath());

        new DtdGenerator().generate(model, outDir);
        System.out.println("  DTD: dtd/phxtask.dtd + .mod + .ent");

        new XsdGenerator().generate(model, outDir);
        System.out.println("  XSD: xsd/phxtask.xsd");

        new CatalogGenerator().generate(model, outDir);
        System.out.println("  Catalog: catalog.xml");

        new ProjectRepository().save(model, new File(outDir, "phxTask.ddp"));
        System.out.println("  Project: phxTask.ddp");

        System.out.println("Done.");
    }
}
