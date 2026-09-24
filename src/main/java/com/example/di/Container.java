package com.example.di;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A lightweight dependency-injection container.
 *
 * <p>Usage: {@code register} component classes, {@link #start()} the container
 * (which validates the wiring and eagerly creates all singletons), look beans up
 * with {@link #getBean}, and {@link #close()} the container to destroy singletons
 * in reverse dependency order.
 */
public final class Container implements AutoCloseable {

    private final List<Class<?>> registeredClasses = new ArrayList<>();
    private final Map<Class<?>, BeanDefinition> definitions = new LinkedHashMap<>();
    private final Map<BeanDefinition, Object> singletons = new LinkedHashMap<>();
    private final List<DestructionTask> destructionTasks = new ArrayList<>();
    private boolean started;
    private boolean closed;

    /** Registers component classes. Must be called before {@link #start()}. */
    public Container register(Class<?>... classes) {
        if (started) {
            throw new ContainerException("Cannot register classes after the container has started");
        }
        for (Class<?> clazz : classes) {
            if (clazz == null) {
                throw new ContainerException("Cannot register a null class");
            }
            registeredClasses.add(clazz);
        }
        return this;
    }

    /**
     * Validates the whole object graph and eagerly creates all singleton beans.
     * Fails fast on missing dependencies, ambiguous dependencies and circular
     * constructor dependencies.
     */
    public void start() {
        if (started) {
            throw new ContainerException("Container has already been started");
        }
        buildDefinitions();
        validateDependencies();
        detectConstructorCycles();
        instantiateSingletons();
        started = true;
    }

    /** Looks up a bean by type. Fails if zero or several candidates match. */
    public <T> T getBean(Class<T> type) {
        requireStarted();
        return type.cast(beanFor(resolveDefinition(new Dependency(type, null))));
    }

    /** Looks up a bean by name, checked against the expected type. */
    public <T> T getBean(String name, Class<T> type) {
        requireStarted();
        return type.cast(beanFor(resolveDefinition(new Dependency(type, name))));
    }

    /**
     * Destroys all singleton beans that have {@link PreDestroy} methods, in
     * reverse creation order (dependents are destroyed before their dependencies).
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        ContainerException failure = null;
        for (int i = destructionTasks.size() - 1; i >= 0; i--) {
            DestructionTask task = destructionTasks.get(i);
            for (Method method : task.preDestroyMethods) {
                try {
                    method.invoke(task.bean);
                } catch (ReflectiveOperationException e) {
                    ContainerException error = new ContainerException(
                            "Failed to invoke @PreDestroy method " + method.getName()
                                    + " on bean " + task.bean.getClass().getSimpleName(), e);
                    if (failure == null) {
                        failure = error;
                    } else {
                        failure.addSuppressed(error);
                    }
                }
            }
        }
        singletons.clear();
        destructionTasks.clear();
        if (failure != null) {
            throw failure;
        }
    }

    // ---- definition building -------------------------------------------------

    private void buildDefinitions() {
        Map<String, Class<?>> names = new LinkedHashMap<>();
        for (Class<?> clazz : registeredClasses) {
            if (definitions.containsKey(clazz)) {
                throw new ContainerException("Class registered twice: " + clazz.getName());
            }
            BeanDefinition definition = BeanDefinition.of(clazz);
            Class<?> previous = names.putIfAbsent(definition.name, clazz);
            if (previous != null) {
                throw new ContainerException("Duplicate bean name '" + definition.name + "' used by "
                        + previous.getName() + " and " + clazz.getName());
            }
            definitions.put(clazz, definition);
        }
    }

    // ---- validation ------------------------------------------------------------

    private void validateDependencies() {
        for (BeanDefinition definition : definitions.values()) {
            for (Dependency dependency : definition.allDependencies()) {
                resolveDefinition(dependency);
            }
        }
    }

    private void detectConstructorCycles() {
        Set<BeanDefinition> visited = new HashSet<>();
        Deque<BeanDefinition> stack = new ArrayDeque<>();
        for (BeanDefinition definition : definitions.values()) {
            visit(definition, visited, stack);
        }
    }

    private void visit(BeanDefinition definition, Set<BeanDefinition> visited, Deque<BeanDefinition> stack) {
        if (visited.contains(definition)) {
            return;
        }
        if (stack.contains(definition)) {
            List<BeanDefinition> path = new ArrayList<>(stack);
            List<BeanDefinition> cycle = new ArrayList<>(path.subList(path.indexOf(definition), path.size()));
            cycle.add(definition);
            throw new CircularDependencyException("Circular constructor dependency detected: "
                    + cycle.stream().map(d -> d.beanClass.getSimpleName()).collect(Collectors.joining(" -> ")));
        }
        stack.addLast(definition);
        for (Dependency dependency : definition.constructorDependencies) {
            visit(resolveDefinition(dependency), visited, stack);
        }
        stack.removeLast();
        visited.add(definition);
    }

    // ---- bean creation ---------------------------------------------------------

    private void instantiateSingletons() {
        for (BeanDefinition definition : definitions.values()) {
            if (definition.scope == Scope.SINGLETON) {
                getOrCreateSingleton(definition, new ArrayDeque<>());
            }
        }
    }

    private Object beanFor(BeanDefinition definition) {
        if (definition.scope == Scope.SINGLETON) {
            Object bean = singletons.get(definition);
            if (bean == null) {
                throw new ContainerException("Singleton not initialized: " + definition.beanClass.getName());
            }
            return bean;
        }
        return createBean(definition, new ArrayDeque<>());
    }

    private Object beanForDependency(Dependency dependency, Deque<BeanDefinition> creationStack) {
        BeanDefinition target = resolveDefinition(dependency);
        if (target.scope == Scope.SINGLETON) {
            return getOrCreateSingleton(target, creationStack);
        }
        return createBean(target, creationStack);
    }

    private Object getOrCreateSingleton(BeanDefinition definition, Deque<BeanDefinition> creationStack) {
        Object existing = singletons.get(definition);
        return existing != null ? existing : createBean(definition, creationStack);
    }

    private Object createBean(BeanDefinition definition, Deque<BeanDefinition> creationStack) {
        if (creationStack.contains(definition)) {
            List<BeanDefinition> path = new ArrayList<>(creationStack);
            path.add(definition);
            throw new CircularDependencyException("Circular dependency detected while creating beans: "
                    + path.stream().map(d -> d.beanClass.getSimpleName()).collect(Collectors.joining(" -> ")));
        }
        creationStack.addLast(definition);
        try {
            Object[] args = new Object[definition.constructorDependencies.size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = beanForDependency(definition.constructorDependencies.get(i), creationStack);
            }
            Object bean = definition.constructor.newInstance(args);
            for (FieldInjection injection : definition.fieldInjections) {
                injection.field.set(bean, beanForDependency(injection.dependency, creationStack));
            }
            for (Method method : definition.postConstructMethods) {
                method.invoke(bean);
            }
            if (definition.scope == Scope.SINGLETON) {
                singletons.put(definition, bean);
                if (!definition.preDestroyMethods.isEmpty()) {
                    destructionTasks.add(new DestructionTask(bean, definition.preDestroyMethods));
                }
            }
            return bean;
        } catch (ContainerException e) {
            throw e;
        } catch (ReflectiveOperationException e) {
            throw new ContainerException("Failed to create bean of type " + definition.beanClass.getName(), e);
        } finally {
            creationStack.removeLast();
        }
    }

    // ---- resolution --------------------------------------------------------------

    private BeanDefinition resolveDefinition(Dependency dependency) {
        List<BeanDefinition> candidates = new ArrayList<>();
        for (BeanDefinition definition : definitions.values()) {
            if (dependency.type.isAssignableFrom(definition.beanClass)
                    && (dependency.qualifier == null || definition.name.equals(dependency.qualifier))) {
                candidates.add(definition);
            }
        }
        if (candidates.isEmpty()) {
            if (dependency.qualifier != null) {
                throw new ContainerException("No bean named '" + dependency.qualifier
                        + "' assignable to " + dependency.type.getName() + " is registered");
            }
            throw new ContainerException("No bean assignable to " + dependency.type.getName() + " is registered");
        }
        if (candidates.size() > 1) {
            String names = candidates.stream()
                    .map(d -> "'" + d.name + "' (" + d.beanClass.getSimpleName() + ")")
                    .collect(Collectors.joining(", "));
            throw new ContainerException("Multiple beans assignable to " + dependency.type.getName()
                    + ": " + names + ". Disambiguate with @Qualifier.");
        }
        return candidates.get(0);
    }

    private void requireStarted() {
        if (!started) {
            throw new ContainerException("Container has not been started yet");
        }
    }

    // ---- model -------------------------------------------------------------------

    private record Dependency(Class<?> type, String qualifier) {
    }

    private record FieldInjection(Field field, Dependency dependency) {
    }

    private record DestructionTask(Object bean, List<Method> preDestroyMethods) {
    }

    private static final class BeanDefinition {
        final Class<?> beanClass;
        final String name;
        final Scope scope;
        final Constructor<?> constructor;
        final List<Dependency> constructorDependencies;
        final List<FieldInjection> fieldInjections;
        final List<Method> postConstructMethods;
        final List<Method> preDestroyMethods;

        private BeanDefinition(Class<?> beanClass, String name, Scope scope, Constructor<?> constructor,
                               List<Dependency> constructorDependencies, List<FieldInjection> fieldInjections,
                               List<Method> postConstructMethods, List<Method> preDestroyMethods) {
            this.beanClass = beanClass;
            this.name = name;
            this.scope = scope;
            this.constructor = constructor;
            this.constructorDependencies = constructorDependencies;
            this.fieldInjections = fieldInjections;
            this.postConstructMethods = postConstructMethods;
            this.preDestroyMethods = preDestroyMethods;
        }

        List<Dependency> allDependencies() {
            List<Dependency> all = new ArrayList<>(constructorDependencies);
            for (FieldInjection injection : fieldInjections) {
                all.add(injection.dependency);
            }
            return all;
        }

        static BeanDefinition of(Class<?> beanClass) {
            Component annotation = beanClass.getAnnotation(Component.class);
            String name = annotation != null && !annotation.name().isEmpty()
                    ? annotation.name()
                    : decapitalize(beanClass.getSimpleName());
            Scope scope = annotation != null ? annotation.scope() : Scope.SINGLETON;

            Constructor<?> constructor = selectConstructor(beanClass);
            constructor.setAccessible(true);
            List<Dependency> constructorDependencies = new ArrayList<>();
            for (Parameter parameter : constructor.getParameters()) {
                constructorDependencies.add(new Dependency(parameter.getType(), qualifierOf(parameter)));
            }

            List<FieldInjection> fieldInjections = new ArrayList<>();
            for (Field field : hierarchyFields(beanClass)) {
                if (!field.isAnnotationPresent(Inject.class)) {
                    continue;
                }
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    throw new ContainerException("@Inject field must not be static or final: "
                            + beanClass.getSimpleName() + "." + field.getName());
                }
                field.setAccessible(true);
                fieldInjections.add(new FieldInjection(field,
                        new Dependency(field.getType(), qualifierOf(field))));
            }

            return new BeanDefinition(beanClass, name, scope, constructor,
                    List.copyOf(constructorDependencies), List.copyOf(fieldInjections),
                    lifecycleMethods(beanClass, PostConstruct.class), lifecycleMethods(beanClass, PreDestroy.class));
        }

        private static Constructor<?> selectConstructor(Class<?> beanClass) {
            Constructor<?>[] constructors = beanClass.getDeclaredConstructors();
            List<Constructor<?>> annotated = new ArrayList<>();
            for (Constructor<?> constructor : constructors) {
                if (constructor.isAnnotationPresent(Inject.class)) {
                    annotated.add(constructor);
                }
            }
            if (annotated.size() > 1) {
                throw new ContainerException("Multiple @Inject constructors on " + beanClass.getName());
            }
            if (annotated.size() == 1) {
                return annotated.get(0);
            }
            if (constructors.length == 1) {
                return constructors[0];
            }
            throw new ContainerException("Multiple constructors on " + beanClass.getName()
                    + "; annotate exactly one with @Inject");
        }

        private static List<Field> hierarchyFields(Class<?> beanClass) {
            List<Field> fields = new ArrayList<>();
            List<Class<?>> hierarchy = new ArrayList<>();
            for (Class<?> current = beanClass; current != null && current != Object.class;
                 current = current.getSuperclass()) {
                hierarchy.add(0, current);
            }
            for (Class<?> level : hierarchy) {
                fields.addAll(List.of(level.getDeclaredFields()));
            }
            return fields;
        }

        private static List<Method> lifecycleMethods(Class<?> beanClass,
                                                     Class<? extends java.lang.annotation.Annotation> annotation) {
            List<Method> methods = new ArrayList<>();
            List<Class<?>> hierarchy = new ArrayList<>();
            for (Class<?> current = beanClass; current != null && current != Object.class;
                 current = current.getSuperclass()) {
                hierarchy.add(0, current);
            }
            for (Class<?> level : hierarchy) {
                for (Method method : level.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(annotation)) {
                        continue;
                    }
                    if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())) {
                        throw new ContainerException("@" + annotation.getSimpleName()
                                + " method must be a no-arg instance method: "
                                + beanClass.getSimpleName() + "." + method.getName());
                    }
                    method.setAccessible(true);
                    methods.add(method);
                }
            }
            return List.copyOf(methods);
        }

        private static String qualifierOf(java.lang.reflect.AnnotatedElement element) {
            Qualifier qualifier = element.getAnnotation(Qualifier.class);
            return qualifier == null ? null : qualifier.value();
        }

        private static String decapitalize(String value) {
            if (value.isEmpty() || (value.length() > 1 && Character.isUpperCase(value.charAt(1)))) {
                return value;
            }
            return Character.toLowerCase(value.charAt(0)) + value.substring(1);
        }
    }
}
