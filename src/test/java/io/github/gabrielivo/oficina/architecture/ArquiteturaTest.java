package io.github.gabrielivo.oficina.architecture;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Regras de arquitetura descritas no CLAUDE.md.
 *
 * <p>Toda regra é congelada com {@code FreezingArchRule}: as violações que já existiam quando a regra foi criada
 * estão registradas em {@code src/test/resources/archunit_store} e são toleradas. Qualquer violação NOVA quebra o
 * build. Ao corrigir uma violação antiga, ela sai do store automaticamente na próxima execução. Não edite o store à
 * mão. Ver {@code src/test/resources/archunit.properties}.
 */
@AnalyzeClasses(packages = "io.github.gabrielivo.oficina", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    private static final String DOMAIN = "io.github.gabrielivo.oficina.domain..";
    private static final String APPLICATION = "io.github.gabrielivo.oficina.application..";
    private static final String PRESENTATION = "io.github.gabrielivo.oficina.presentation..";
    private static final String INFRASTRUCTURE = "io.github.gabrielivo.oficina.infrastructure..";
    private static final String CONFIG = "io.github.gabrielivo.oficina.config..";

    // ---------------------------------------------------------------- camadas

    @ArchTest
    static final ArchRule domainNaoDependeDeOutrasCamadas = freeze(noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(APPLICATION, PRESENTATION, INFRASTRUCTURE, CONFIG)
            .as("domain não depende de application, presentation, infrastructure nem config")
            .because("o domínio é o núcleo; jakarta.persistence e Spring Data são permitidos porque as entidades são"
                    + " JPA"));

    @ArchTest
    static final ArchRule applicationNaoDependeDePresentation = freeze(noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PRESENTATION)
            .as("application não depende de presentation"));

    @ArchTest
    static final ArchRule applicationNaoDependeDeInfrastructure = freeze(noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(INFRASTRUCTURE)
            .as("application não depende de infrastructure")
            .because("integrações externas entram no application por interface (porta) declarada em application"
                    + " ou domain; o adapter fica em infrastructure (DIP)"));

    @ArchTest
    static final ArchRule presentationNaoAcessaRepositories = freeze(noClasses()
            .that()
            .resideInAPackage(PRESENTATION)
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .as("presentation não acessa repositories")
            .because("controllers falam apenas com services de application"));

    // ---------------------------------------------------------------- estereótipos Spring

    @ArchTest
    static final ArchRule servicesFicamEmApplication = freeze(classes()
            .that()
            .areAnnotatedWith(Service.class)
            .should()
            .resideInAPackage(APPLICATION)
            .as("classes @Service ficam em application")
            .because("@Service marca casos de uso; componentes técnicos de infrastructure usam @Component"));

    @ArchTest
    static final ArchRule restControllersFicamEmPresentation = freeze(classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .resideInAPackage(PRESENTATION)
            .as("classes @RestController ficam em presentation"));

    @ArchTest
    static final ArchRule semInjecaoPorCampo = freeze(noFields()
            .should()
            .beAnnotatedWith(Autowired.class)
            .orShould()
            .beAnnotatedWith(Value.class)
            .orShould()
            .beAnnotatedWith("jakarta.inject.Inject")
            .as("sem injeção por campo (@Autowired, @Value ou @Inject em atributo)")
            .because("injeção só por construtor; configuração nova via @ConfigurationProperties"));

    @ArchTest
    static final ArchRule transactionalEmClassesSoEmApplication = freeze(noClasses()
            .that()
            .resideOutsideOfPackage(APPLICATION)
            .should()
            .beAnnotatedWith(Transactional.class)
            .orShould()
            .beAnnotatedWith("jakarta.transaction.Transactional")
            .as("@Transactional em classe só em application"));

    @ArchTest
    static final ArchRule transactionalEmMetodosSoEmApplication = freeze(noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackage(APPLICATION)
            .should()
            .beAnnotatedWith(Transactional.class)
            .orShould()
            .beAnnotatedWith("jakarta.transaction.Transactional")
            .as("@Transactional em método só em application"));

    @ArchTest
    static final ArchRule controllersNaoExpoemEntidades = freeze(methods()
            .that()
            .arePublic()
            .and()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(RestController.class)
            .should(naoUsarEntidadesNaAssinatura())
            .as("métodos de @RestController não recebem nem devolvem entidades JPA")
            .because("o contrato HTTP usa Request/Response (records) convertidos por um Mapper"));

    // ---------------------------------------------------------------- módulos de domínio

    /**
     * Dependências entre módulos de domínio aceitas (acíclicas e coerentes com o modelo): veiculo → cliente;
     * ordemServico → cliente, veiculo, peca; pagamento → ordemServico. Qualquer ciclo é proibido.
     */
    @ArchTest
    static final ArchRule modulosDeDominioSemCiclos =
            freeze(slices().matching("io.github.gabrielivo.oficina.domain.(*)..")
                    .should()
                    .beFreeOfCycles()
                    .as("módulos de domínio sem ciclos"));

    private static ArchCondition<JavaMethod> naoUsarEntidadesNaAssinatura() {
        return new ArchCondition<>("não usar entidades JPA na assinatura") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Stream.concat(
                                method.getReturnType().getAllInvolvedRawTypes().stream(),
                                method.getParameters().stream()
                                        .map(JavaParameter::getType)
                                        .flatMap(t -> t.getAllInvolvedRawTypes().stream()))
                        .filter(ArquiteturaTest::isEntidadeJpa)
                        .distinct()
                        .forEach(entidade -> events.add(violated(
                                method,
                                method.getFullName() + " expõe a entidade " + entidade.getName() + " em "
                                        + method.getSourceCodeLocation())));
            }
        };
    }

    private static boolean isEntidadeJpa(JavaClass type) {
        return type.isAnnotatedWith(Entity.class) || type.isAnnotatedWith(Embeddable.class);
    }
}
