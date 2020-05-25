package fr.quentin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ArgumentsSources;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.util.StringUtils;

/**
 * Junit5ParamTest
 */
class Junit5ParamTest {

    @Nested
    class AAA {

        @ParameterizedTest
        @EnumSource(value = TimeUnit.class, names = { "NANOSECONDS", "MICROSECONDS" })
        public void withSomeEnumValues(TimeUnit unit) {
            System.err.println(unit);
        }

    }

    final List<String> fruits = Arrays.asList("apple", "banana", "lemon");

    @TestTemplate
    @ExtendWith(MyTestTemplateInvocationContextProvider.class)
    void testTemplate(String fruit) {
        assertTrue(fruits.contains(fruit));
    }
    
    public static class MyTestTemplateInvocationContextProvider
            implements TestTemplateInvocationContextProvider {
    
        @Override
        public boolean supportsTestTemplate(ExtensionContext context) {
            return true;
        }
    
        @Override
        public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
                ExtensionContext context) {
    
            return Stream.of(invocationContext("apple"), invocationContext("banana"));
        }
    
        private TestTemplateInvocationContext invocationContext(String parameter) {
            return new TestTemplateInvocationContext() {
                @Override
                public String getDisplayName(int invocationIndex) {
                    return parameter;
                }
    
                @Override
                public List<Extension> getAdditionalExtensions() {
                    return Collections.singletonList(new ParameterResolver(){
                        @Override
                        public boolean supportsParameter(ParameterContext parameterContext,
                                ExtensionContext extensionContext) {
                            return parameterContext.getParameter().getType().equals(String.class);
                        }
    
                        @Override
                        public Object resolveParameter(ParameterContext parameterContext,
                                ExtensionContext extensionContext) {
                            return parameter;
                        }
                    });
                }
            };
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "racecar", "radar", "able was I ere I saw elba" })
    public void palindromesTest(String candidate) {
        assertTrue(false);
    }

    // @DisplayName("Display name of container")
    @ParameterizedTest(name = "{index} ==> the rank of ''{0}'' is {1}")
    @CsvSource({ "apple, 1", "banana, 2", "'lemon, lime', 3" })
    public void testWithCustomDisplayNames(String fruit, int rank) {
    }

    @ParameterizedTest
    @MethodSource("createWordsWithLength")
    public void withMethodSource(String word, int length) {
        System.err.println(word);
    }

    private static Stream<Arguments> createWordsWithLength() {
        return Stream.of(Arguments.of("Hello", 5), Arguments.of("JUnit 5", 7));
    }

    @ParameterizedTest(name = "{index}:{0},{1}")
    @ArgumentsSource(AP.class)
    public void withArgumentsSource(String word, int length) {
        System.err.println(word);
    }

    private static class AP implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            return Stream.of(Arguments.of("Hello", 5), Arguments.of("Bye", 3));
        }

    }
}