package com.example.di;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed metadata for one registered bean class.
 */
final class BeanDefinition {

    private final Class<?> type;
    private final String name;
    private final Scope scope;
    private final Constructor<?> constructor;
    private final List<Dependency> constructorDependencies;
    private final List<Field> injectFields;
    private final List<Dependency> fieldDependencies;
    private final List<Method> postConstructMethods;
    private final List<Method> preDestroyMethods;

    private List<BeanDefinition> resolvedConstructorDependencies;
    private List<BeanDefinition> resolvedFieldDependencies;

    private BeanDefinition(Class<?> type, String name, Scope scope, Constructor<?> constructor,
                           List<Dependency> constructorDependencies,
                           List<Field> injectFields, List<Dependency> fieldDependencies,
                           List<Method> postConstructMethods, List<Method> preDestroyMethods) {
        this.type = type;
        this.name = name;
        this.scope = scope;
        this.constructor = constructor;
        this.constructorDependencies = constructorDependencies;
        this.injectFields = injectFields;
        this.fieldDependencies = fieldDependencies;
        this.postConstructMethods = postConstructMethods;
        this.preDestroyMethods = preDestroyMethods;
    }

    static BeanDefinition of(Class<?> type) {
        Component component = type.getAnnotation(Component.class);
        String name = component != null && !component.value().isEmpty()
                ? component.value()
                : decapitalize(type.getSimpleName());
        Scope scope = component != null ? component.scope() : Scope.SINGLETON;

        Constructor<?> constructor = selectConstructor(type);
        List<Dependency> constructorDependencies = new ArrayList<>();
        for (Parameter parameter : constructor.getParameters()) {
            constructorDependencies.add(Dependency.of(parameter.getType(), parameter.getAnnotation(Named.class)));
        }

        List<Field> injectFields = new ArrayList<>();
        List<Dependency> fieldDependencies = new ArrayList<>();
        List<Method> postConstructMethods = new ArrayList<>();
        List<Method> preDestroyMethods = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    field.setAccessible(true);
                    injectFields.add(field);
                    fieldDependencies.add(Dependency.of(field.getType(), field.getAnnotation(Named.class)));
                }
            }
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PostConstruct.class)) {
                    requireNoArgs(method, "@PostConstruct");
                    method.setAccessible(true);
                    postConstructMethods.add(method);
                }
                if (method.isAnnotationPresent(PreDestroy.class)) {
                    requireNoArgs(method, "@PreDestroy");
                    method.setAccessible(true);
                    preDestroyMethods.add(method);
                }
            }
        }
        return new BeanDefinition(type, name, scope, constructor, constructorDependencies,
                injectFields, fieldDependencies, postConstructMethods, preDestroyMethods);
    }

    private static Constructor<?> selectConstructor(Class<?> type) {
        List<Constructor<?>> injectConstructors = new ArrayList<>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.isAnnotationPresent(Inject.class)) {
                injectConstructors.add(constructor);
            }
        }
        if (injectConstructors.size() > 1) {
            throw new DiException("Class '" + type.getName() + "' has " + injectConstructors.size()
                    + " constructors annotated with @Inject; at most one is allowed");
        }
        Constructor<?> constructor;
        if (injectConstructors.size() == 1) {
            constructor = injectConstructors.get(0);
        } else {
            try {
                constructor = type.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                throw new DiException("Class '" + type.getName() + "' has no @Inject constructor"
                        + " and no no-arg constructor; it cannot be instantiated by the container");
            }
        }
        constructor.setAccessible(true);
        return constructor;
    }

    private static void requireNoArgs(Method method, String annotation) {
        if (method.getParameterCount() != 0) {
            throw new DiException(annotation + " method '" + method.getName() + "' in '"
                    + method.getDeclaringClass().getName() + "' must not declare parameters");
        }
    }

    private static String decapitalize(String simpleName) {
        if (simpleName.isEmpty() || (simpleName.length() > 1 && Character.isUpperCase(simpleName.charAt(1)))) {
            return simpleName;
        }
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    Class<?> type() {
        return type;
    }

    String name() {
        return name;
    }

    Scope scope() {
        return scope;
    }

    Constructor<?> constructor() {
        return constructor;
    }

    List<Dependency> constructorDependencies() {
        return constructorDependencies;
    }

    List<Dependency> fieldDependencies() {
        return fieldDependencies;
    }

    List<Field> injectFields() {
        return injectFields;
    }

    List<Method> postConstructMethods() {
        return postConstructMethods;
    }

    List<Method> preDestroyMethods() {
        return preDestroyMethods;
    }

    List<BeanDefinition> resolvedConstructorDependencies() {
        return resolvedConstructorDependencies;
    }

    List<BeanDefinition> resolvedFieldDependencies() {
        return resolvedFieldDependencies;
    }

    void resolveConstructorDependencies(List<BeanDefinition> resolved) {
        this.resolvedConstructorDependencies = resolved;
    }

    void resolveFieldDependencies(List<BeanDefinition> resolved) {
        this.resolvedFieldDependencies = resolved;
    }
}
