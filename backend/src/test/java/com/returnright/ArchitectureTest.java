package com.returnright;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.returnright.returns.ReturnItem;
import com.returnright.returns.ReturnRequest;
import com.returnright.returns.ReturnStateMachine;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** CLAUDE.md coding conventions: module layering, no cycles, and only the state machine writes status. */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.returnright");
    }

    @Test
    void modulesRespectTheLayering() {
        ArchRule rule = layeredArchitecture().consideringOnlyDependenciesInAnyPackage("com.returnright..")
                .layer("Core").definedBy("com.returnright.core..")
                .layer("Domain").definedBy("com.returnright.orders..", "com.returnright.eligibility..",
                        "com.returnright.labels..", "com.returnright.audit..", "com.returnright.notifications..",
                        "com.returnright.ledger..", "com.returnright.gatewayclient..", "com.returnright.auth..",
                        "com.returnright.analytics..")
                .layer("Returns").definedBy("com.returnright.returns..")
                .layer("Refunds").definedBy("com.returnright.refunds..")
                .layer("Workflows").definedBy("com.returnright.inspection..", "com.returnright.finance..")
                .layer("Entry").definedBy("com.returnright.web..", "com.returnright.jobs..", "com.returnright.seed..")
                .whereLayer("Entry").mayNotBeAccessedByAnyLayer()
                .whereLayer("Workflows").mayOnlyBeAccessedByLayers("Entry")
                .whereLayer("Refunds").mayOnlyBeAccessedByLayers("Workflows", "Entry")
                .whereLayer("Returns").mayOnlyBeAccessedByLayers("Refunds", "Workflows", "Entry")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Returns", "Refunds", "Workflows", "Entry");
        rule.check(classes);
    }

    @Test
    void coreDependsOnNoOtherModule() {
        noClasses().that().resideInAPackage("com.returnright.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.returnright.orders..", "com.returnright.eligibility..", "com.returnright.labels..",
                        "com.returnright.audit..", "com.returnright.notifications..", "com.returnright.ledger..",
                        "com.returnright.gatewayclient..", "com.returnright.auth..", "com.returnright.analytics..",
                        "com.returnright.returns..", "com.returnright.refunds..", "com.returnright.inspection..",
                        "com.returnright.finance..", "com.returnright.web..", "com.returnright.jobs..",
                        "com.returnright.seed..")
                .check(classes);
    }

    @Test
    void eligibilityIsPureJava() {
        noClasses().that().resideInAPackage("com.returnright.eligibility..")
                .should().dependOnClassesThat().resideOutsideOfPackages("java..", "com.returnright.eligibility..")
                .check(classes);
    }

    @Test
    void noCyclesBetweenModules() {
        slices().matching("com.returnright.(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    void onlyTheStateMachineChangesReturnStatus() {
        noClasses().that().doNotHaveFullyQualifiedName(ReturnStateMachine.class.getName())
                .should().callMethod(ReturnRequest.class, "setStatus", com.returnright.core.enums.ReturnStatus.class)
                .orShould().callMethod(ReturnItem.class, "setStatus", com.returnright.core.enums.ItemStatus.class)
                .check(classes);
    }
}
