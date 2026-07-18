package io.github.gjuton;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceRule;

@AnalyzeClasses(packages = "io.github.gjuton", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

        @ArchTest
        static final ArchRule layering = layeredArchitecture()
                        .consideringOnlyDependenciesInLayers()
                        .layer("api").definedBy("io.github.gjuton.api..")
                        .layer("parser").definedBy("io.github.gjuton.internal.parser..")
                        .layer("generator").definedBy("io.github.gjuton.internal.generator..")
                        .layer("model").definedBy("io.github.gjuton.internal.model..")
                        .layer("util").definedBy("io.github.gjuton.internal.util..")
                        .layer("errors").definedBy("io.github.gjuton.errors..")
                        .whereLayer("parser").mayOnlyAccessLayers("model", "errors")
                        .whereLayer("generator").mayOnlyAccessLayers("model", "errors", "util")
                        .whereLayer("model").mayNotAccessAnyLayer()
                        .whereLayer("util").mayNotAccessAnyLayer()
                        .whereLayer("errors").mayNotAccessAnyLayer();

        @ArchTest
        static final SliceRule noCycles = slices()
                        .matching("io.github.gjuton.internal.(*)..")
                        .should().beFreeOfCycles();

        // TODO: unit tests for individual generators currently construct GeneratorContext directly
        // and reach into SchemaParser. Revisit whether there is a cleaner test seam — e.g. exposing
        // a minimal test-only factory — so the generator package boundary stays tight.
        @ArchTest
        static final ArchRule jacksonOnlyInParserAndModel = noClasses()
                        .that().resideInAPackage("io.github.gjuton..")
                        .and().resideOutsideOfPackages(
                                        "io.github.gjuton.internal.parser..",
                                        "io.github.gjuton.internal.model.."
                        )
                        .and().haveSimpleNameNotEndingWith("Test")
                        .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..");

        @ArchTest
        static final ArchRule rgxgenOnlyInGenerator = noClasses()
                        .that().resideInAPackage("io.github.gjuton..")
                        .and().resideOutsideOfPackage("io.github.gjuton.internal.generator..")
                        .should().dependOnClassesThat().resideInAPackage("com.github.curiousoddman.rgxgen..");

}
