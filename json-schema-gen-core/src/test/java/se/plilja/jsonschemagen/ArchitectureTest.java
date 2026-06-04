package se.plilja.jsonschemagen;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "se.plilja.jsonschemagen")
class ArchitectureTest {

    @ArchTest
    static final ArchRule layering = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("api").definedBy("se.plilja.jsonschemagen.api..")
            .layer("parser").definedBy("se.plilja.jsonschemagen.internal.parser..")
            .layer("generator").definedBy("se.plilja.jsonschemagen.internal.generator..")
            .layer("model").definedBy("se.plilja.jsonschemagen.internal.model..")
            .whereLayer("parser").mayOnlyAccessLayers("model")
            .whereLayer("generator").mayOnlyAccessLayers("model")
            .whereLayer("model").mayNotAccessAnyLayer();

    @ArchTest
    static final SliceRule noCycles = slices()
            .matching("se.plilja.jsonschemagen.(**)")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule jacksonOnlyInParserAndModel = noClasses()
            .that().resideInAPackage("se.plilja.jsonschemagen..")
            .and().resideOutsideOfPackages(
                    "se.plilja.jsonschemagen.internal.parser..",
                    "se.plilja.jsonschemagen.internal.model.."
            )
            .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..");

}
