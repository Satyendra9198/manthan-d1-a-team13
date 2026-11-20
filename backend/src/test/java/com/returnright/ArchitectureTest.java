package com.returnright;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.returnright.returns.entity.ReturnItem;
import com.returnright.returns.entity.ReturnRequest;
import com.returnright.returns.service.ReturnStateMachine;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * CLAUDE.md coding conventions, enforced:
 * <ul>
 *   <li>module layering and no cycles (core ← domain ← returns ← refunds ← workflows ← api/jobs/seed)</li>
 *   <li>inside a module: controller → service → repository/entity (controllers only live in {@code api})</li>
 *   <li>only the state machine mutates a return or a return item</li>
 * </ul>
 */
class ArchitectureTest {

    private static final String BASE = "com.returnright";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void modulesRespectTheLayering() {
        ArchRule rule = layeredArchitecture().consideringOnlyDependenciesInAnyPackage(BASE + "..")
                .layer("Core").definedBy(BASE + ".core..")
                .layer("Domain").definedBy(BASE + ".orders..", BASE + ".eligibility..", BASE + ".labels..",
                        BASE + ".audit..", BASE + ".notifications..", BASE + ".ledger..", BASE + ".gatewayclient..",
                        BASE + ".auth..", BASE + ".analytics..")
                .layer("Returns").definedBy(BASE + ".returns..")
                .layer("Refunds").definedBy(BASE + ".refunds..")
                .layer("Workflows").definedBy(BASE + ".inspection..", BASE + ".finance..")
                .layer("Entry").definedBy(BASE + ".api..", BASE + ".jobs..", BASE + ".seed..")
                .whereLayer("Entry").mayNotBeAccessedByAnyLayer()
                .whereLayer("Workflows").mayOnlyBeAccessedByLayers("Entry")
                .whereLayer("Refunds").mayOnlyBeAccessedByLayers("Workflows", "Entry")
                .whereLayer("Returns").mayOnlyBeAccessedByLayers("Refunds", "Workflows", "Entry")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Returns", "Refunds", "Workflows", "Entry");
        rule.check(classes);
    }

    @Test
    void coreDependsOnNoOtherModule() {
        noClasses().that().resideInAPackage(BASE + ".core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".orders..", BASE + ".eligibility..", BASE + ".labels..", BASE + ".audit..",
                        BASE + ".notifications..", BASE + ".ledger..", BASE + ".gatewayclient..", BASE + ".auth..",
                        BASE + ".analytics..", BASE + ".returns..", BASE + ".refunds..", BASE + ".inspection..",
                        BASE + ".finance..", BASE + ".api..", BASE + ".jobs..", BASE + ".seed..")
                .check(classes);
    }

    @Test
    void eligibilityIsPureJava() {
        noClasses().that().resideInAPackage(BASE + ".eligibility..")
                .should().dependOnClassesThat().resideOutsideOfPackages("java..", BASE + ".eligibility..")
                .check(classes);
    }

    @Test
    void noCyclesBetweenModules() {
        slices().matching(BASE + ".(*)..").should().beFreeOfCycles().check(classes);
    }

    // ------------------------------------------------------------------ controller / service / repository layering

    @Test
    void controllersLiveOnlyInTheApiLayer() {
        classes().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().resideInAPackage(BASE + ".api.controller..")
                .check(classes);
    }

    @Test
    void controllersNeverUseRepositories() {
        noClasses().that().resideInAPackage(BASE + ".api.controller..")
                .should().dependOnClassesThat().resideInAPackage(BASE + "..repository..")
                .check(classes);
    }

    @Test
    void repositoriesAreOnlyUsedByServicesMappersJobsAndTheSeeder() {
        classes().that().resideInAPackage(BASE + "..repository..")
                .should().onlyBeAccessed().byClassesThat().resideInAnyPackage(
                        BASE + "..service..", BASE + "..repository..", BASE + ".jobs..", BASE + ".seed..",
                        BASE + ".api.mapper..")
                .check(classes);
    }

    @Test
    void entitiesDoNotDependOnServicesOrTheApi() {
        noClasses().that().resideInAPackage(BASE + "..entity..")
                .should().dependOnClassesThat().resideInAnyPackage(BASE + "..service..", BASE + ".api..")
                .check(classes);
    }

    @Test
    void dtosDoNotExposeEntities() {
        noClasses().that().resideInAPackage(BASE + ".api.dto..")
                .should().dependOnClassesThat().resideInAPackage(BASE + "..entity..")
                .check(classes);
    }

    // ------------------------------------------------------------------ state machine ownership

    /** Every state-changing method of a return / return item; getters and snapshots stay free to call. */
    private static final Set<String> RETURN_MUTATORS = Set.of("setStatus", "bumpVersion", "setClosedAt",
            "setApprovedRefundPaise", "setOutcome", "setFinanceReview", "setCancelReason", "setRefundMethod", "inspect");

    @Test
    void onlyTheStateMachineMutatesReturnsAndReturnItems() {
        DescribedPredicate<JavaCall<?>> mutatesAReturn =
                new DescribedPredicate<>("call a ReturnRequest/ReturnItem mutator") {
                    @Override
                    public boolean test(JavaCall<?> call) {
                        String owner = call.getTargetOwner().getName();
                        return (owner.equals(ReturnRequest.class.getName()) || owner.equals(ReturnItem.class.getName()))
                                && RETURN_MUTATORS.contains(call.getName());
                    }
                };
        noClasses().that().doNotHaveFullyQualifiedName(ReturnStateMachine.class.getName())
                .and().doNotHaveFullyQualifiedName(ReturnRequest.class.getName())
                .and().doNotHaveFullyQualifiedName(ReturnItem.class.getName())
                .should().callMethodWhere(mutatesAReturn)
                .because("only ReturnStateMachine.apply(...) may change a return (CLAUDE.md state machine rules)")
                .check(classes);
    }
}
