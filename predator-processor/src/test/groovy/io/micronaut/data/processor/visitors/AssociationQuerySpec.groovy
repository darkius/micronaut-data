package io.micronaut.data.processor.visitors

import groovy.transform.CompileStatic
import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.query.encoder.entities.Book
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Unroll

import javax.annotation.processing.SupportedAnnotationTypes
import javax.persistence.Entity

class AssociationQuerySpec extends AbstractTypeElementSpec {
    @Unroll
    void "test build repository with association queries for #method"() {
        given:
        BeanDefinition beanDefinition = compileListRepository(resultType, method, arguments)
        def parameterTypes = arguments.values() as Class[]

        expect: "The finder is valid"
        !beanDefinition.isAbstract()
        beanDefinition != null

        def executableMethod = beanDefinition.getRequiredMethod(method, parameterTypes)
        def ann = executableMethod.synthesize(PredatorMethod)
        ann.interceptor() == interceptor
        ann.rootEntity() == rootEntity
        ann.resultType() == resultType
        executableMethod.getValue(PredatorMethod, "interceptor", Class).get() == interceptor
        executableMethod.getValue(Query, String).orElse(null) == query

        where:
        rootEntity | resultType | method             | arguments      | query                                                                 | interceptor
        Book       | Book       | 'findByAuthorName' | [name: String] | "SELECT book FROM $rootEntity.name AS book LEFT JOIN book.author author WHERE (author.name = :p1)" | FindAllInterceptor
    }

    @CompileStatic
    BeanDefinition compileListRepository(Class returnType, String method, Map<String, Class> arguments) {
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.Repository;
${returnType.isAnnotationPresent(Entity) ? 'import ' + returnType.getName() + ';' : ''}
import io.micronaut.data.model.query.encoder.entities.Person;
import java.util.List;
import io.micronaut.data.annotation.JoinSpec;

@Repository
interface MyInterface extends io.micronaut.data.repository.Repository<Person, Long>{
    @JoinSpec(value="author", type=JoinSpec.Type.LEFT)
    List<$returnType.simpleName> $method(${arguments.entrySet().collect { "$it.value.name $it.key" }.join(',')});    
}


""")
        return beanDefinition
    }

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new MyTypeElementVisitorProcessor()
            }
        }
    }

    @SupportedAnnotationTypes("*")
    static class MyTypeElementVisitorProcessor extends TypeElementVisitorProcessor {
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new IntrospectedTypeElementVisitor(), new RepositoryTypeElementVisitor()]
        }
    }
}
