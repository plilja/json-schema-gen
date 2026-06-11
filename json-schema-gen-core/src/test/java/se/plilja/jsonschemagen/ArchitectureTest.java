package se.plilja.jsonschemagen;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceRule;

@AnalyzeClasses(packages = "se.plilja.jsonschemagen", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

        @ArchTest
        static final ArchRule layering = layeredArchitecture()
                        .consideringOnlyDependenciesInLayers()
                        .layer("api").definedBy("se.plilja.jsonschemagen.api..")
                        .layer("parser").definedBy("se.plilja.jsonschemagen.internal.parser..")
                        .layer("generator").definedBy("se.plilja.jsonschemagen.internal.generator..")
                        .layer("model").definedBy("se.plilja.jsonschemagen.internal.model..")
                        .layer("errors").definedBy("se.plilja.jsonschemagen.errors..")
                        .whereLayer("parser").mayOnlyAccessLayers("model", "errors")
                        .whereLayer("generator").mayOnlyAccessLayers("model", "errors")
                        .whereLayer("model").mayNotAccessAnyLayer()
                        .whereLayer("errors").mayNotAccessAnyLayer();

        @ArchTest
        static final SliceRule noCycles = slices()
                        .matching("se.plilja.jsonschemagen.internal.(*)..")
                        .should().beFreeOfCycles();

        // TODO: unit tests for individual generators currently construct GeneratorContext directly
        // and reach into SchemaParser. Revisit whether there is a cleaner test seam — e.g. exposing
        // a minimal test-only factory — so the generator package boundary stays tight.
        @ArchTest
        static final ArchRule jacksonOnlyInParserAndModel = noClasses()
                        .that().resideInAPackage("se.plilja.jsonschemagen..")
                        .and().resideOutsideOfPackages(
                                        "se.plilja.jsonschemagen.internal.parser..",
                                        "se.plilja.jsonschemagen.internal.model.."
                        )
                        .and().haveSimpleNameNotEndingWith("Test")
                        .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..");

        @ArchTest
        static final ArchRule rgxgenOnlyInGenerator = noClasses()
                        .that().resideInAPackage("se.plilja.jsonschemagen..")
                        .and().resideOutsideOfPackage("se.plilja.jsonschemagen.internal.generator..")
                        .should().dependOnClassesThat().resideInAPackage("com.github.curiousoddman.rgxgen..");

}
