package com.example.di;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A lightweight dependency injection container.
 *
 * <p>Usage:
 * <pre>{@code
 * DiContainer container = new DiContainer();
 * container.register(App.class, Repository.class);
 * container.start();              // validates the whole graph eagerly
 * App app = container.getBean(App.class);
 * container.close();              // runs @PreDestroy on singletons
 * }</pre>
 *
 * <p>The container is not thread-safe.
 */
public final class DiContainer implements AutoCloseable {

    private final Map<Class<?>, BeanDefinition> definitionsByType = new LinkedHashMap<>();
    private final Map<String, BeanDefinition> definitionsByName = new LinkedHashMap<>();
    private final Map<BeanDefinition, Object> singletons = new IdentityHashMap<>();
    private final Map<BeanDefinition, Object> earlySingletons = new IdentityHashMap<>();
    private final List<SingletonRecord> destructionOrder = new ArrayList<>();
    private final Deque<BeanDefinition> creationStack = new ArrayDeque<>();
    private boolean started;
    private boolean closed;

    private record SingletonRecord(BeanDefinition definition, Object instance) {
    }

    /**
     * Registers bean classes. Must be called before {@link #start()}.
     */
    public DiContainer register(Class<?>... types) {
        if (started) {
            throw new IllegalStateException("Cannot register beans after the container has started");
        }
        for (Class<?> type : types) {
            if (type == null) {
                throw new IllegalArgumentException("Cannot register a null class");
            }
            if (definitionsByType.containsKey(type)) {
                throw new DiException("Class '" + type.getName() + "' is already registered");
            }
            BeanDefinition definition = BeanDefinition.of(type);
            BeanDefinition clash = definitionsByName.get(definition.name());
            if (clash != null) {
                throw new DiException("Bean name '" + definition.name() + "' is used by both '"
                        + clash.type().getName() + "' and '" + type.getName() + "'");
            }
            definitionsByType.put(type, definition);
            definitionsByName.put(definition.name(), definition);
        }
        return this;
    }

    /**
     * Validates the whole bean graph: resolves every injection point and
     * detects constructor-injection cycles. Idempotent.
     */
    public void start() {
        if (started) {
            return;
        }
        if (closed) {
            throw new IllegalStateException("Container is already closed");
        }
        resolveAllDependencies();
        detectConstructorCycles();
        started = true;
    }

    /**
     * Looks up a bean by type. Fails if zero or more than one candidate exists.
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        ensureStarted();
        return (T) getInstance(resolve(new Dependency(type, null)));
    }

    /**
     * Looks up a bean by its name.
     */
    public Object getBean(String name) {
        ensureStarted();
        BeanDefinition definition = definitionsByName.get(name);
        if (definition == null) {
            throw new NoSuchBeanException("No bean named '" + name + "' is registered");
        }
        return getInstance(definition);
    }

    /**
     * Looks up a bean by name, additionally checking the expected type.
     */
    public <T> T getBean(String name, Class<T> type) {
        Object bean = getBean(name);
        if (!type.isInstance(bean)) {
            throw new NoSuchBeanException("Bean named '" + name + "' is of type '"
                    + bean.getClass().getName() + "', not assignable to '" + type.getName() + "'");
        }
        return type.cast(bean);
    }

    /**
     * Destroys all singleton beans in reverse dependency order by invoking
     * their {@link PreDestroy} methods. Prototype beans are not tracked.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        DiException failure = null;
        for (int i = destructionOrder.size() - 1; i >= 0; i--) {
            SingletonRecord record = destructionOrder.get(i);
            for (Method method : record.definition().preDestroyMethods()) {
                try {
                    method.invoke(record.instance());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    DiException error = new DiException("Failed to destroy bean '"
                            + record.definition().name() + "'", e);
                    if (failure == null) {
                        failure = error;
                    } else {
                        failure.addSuppressed(error);
                    }
                }
            }
        }
        destructionOrder.clear();
        singletons.clear();
        earlySingletons.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private void ensureStarted() {
        if (closed) {
            throw new IllegalStateException("Container is already closed");
        }
        if (!started) {
            start();
        }
    }

    private void resolveAllDependencies() {
        for (BeanDefinition definition : definitionsByType.values()) {
            List<BeanDefinition> constructorDeps = new ArrayList<>();
            for (Dependency dependency : definition.constructorDependencies()) {
                constructorDeps.add(resolve(dependency));
            }
            definition.resolveConstructorDependencies(constructorDeps);

            List<BeanDefinition> fieldDeps = new ArrayList<>();
            for (Dependency dependency : definition.fieldDependencies()) {
                fieldDeps.add(resolve(dependency));
            }
            definition.resolveFieldDependencies(fieldDeps);
        }
    }

    private BeanDefinition resolve(Dependency dependency) {
        List<BeanDefinition> candidates = new ArrayList<>();
        for (BeanDefinition definition : definitionsByType.values()) {
            if (dependency.type().isAssignableFrom(definition.type())) {
                candidates.add(definition);
            }
        }
        if (dependency.name() != null) {
            candidates.removeIf(definition -> !definition.name().equals(dependency.name()));
            if (candidates.isEmpty()) {
                throw new NoSuchBeanException("No bean matches " + dependency.describe());
            }
            return candidates.get(0);
        }
        if (candidates.isEmpty()) {
            throw new NoSuchBeanException("No bean matches " + dependency.describe());
        }
        if (candidates.size() > 1) {
            List<String> names = new ArrayList<>();
            for (BeanDefinition candidate : candidates) {
                names.add(candidate.name() + " (" + candidate.type().getName() + ")");
            }
            throw new AmbiguousBeanException("Multiple beans match " + dependency.describe()
                    + ": " + names + ". Add a @Named qualifier to select one.");
        }
        return candidates.get(0);
    }

    private void detectConstructorCycles() {
        Set<BeanDefinition> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<BeanDefinition> onStack = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<BeanDefinition> path = new ArrayDeque<>();
        for (BeanDefinition definition : definitionsByType.values()) {
            visit(definition, visited, onStack, path);
        }
    }

    private void visit(BeanDefinition definition, Set<BeanDefinition> visited,
                       Set<BeanDefinition> onStack, Deque<BeanDefinition> path) {
        if (visited.contains(definition)) {
            return;
        }
        if (onStack.contains(definition)) {
            List<String> cycle = new ArrayList<>();
            boolean inCycle = false;
            for (BeanDefinition entry : path) {
                if (entry == definition) {
                    inCycle = true;
                }
                if (inCycle) {
                    cycle.add(entry.type().getSimpleName());
                }
            }
            cycle.add(definition.type().getSimpleName());
            throw new CircularDependencyException(
                    "Circular dependency detected in constructor injection: " + String.join(" -> ", cycle));
        }
        onStack.add(definition);
        path.addLast(definition);
        for (BeanDefinition dependency : definition.resolvedConstructorDependencies()) {
            visit(dependency, visited, onStack, path);
        }
        path.removeLast();
        onStack.remove(definition);
        visited.add(definition);
    }

    private Object getInstance(BeanDefinition definition) {
        if (definition.scope() == Scope.SINGLETON) {
            Object existing = singletons.get(definition);
            if (existing != null) {
                return existing;
            }
            Object early = earlySingletons.get(definition);
            if (early != null) {
                return early;
            }
        }
        if (creationStack.contains(definition)) {
            List<String> chain = new ArrayList<>();
            for (BeanDefinition entry : creationStack) {
                chain.add(entry.type().getSimpleName());
            }
            chain.add(definition.type().getSimpleName());
            throw new BeanCreationException("Circular dependency through field injection on prototype-scoped bean '"
                    + definition.name() + "': " + String.join(" -> ", chain));
        }
        creationStack.push(definition);
        try {
            Object[] args = new Object[definition.resolvedConstructorDependencies().size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = getInstance(definition.resolvedConstructorDependencies().get(i));
            }
            Object instance;
            try {
                instance = definition.constructor().newInstance(args);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new BeanCreationException("Failed to construct bean '" + definition.name() + "'", e);
            }
            if (definition.scope() == Scope.SINGLETON) {
                earlySingletons.put(definition, instance);
            }
            try {
                injectFields(definition, instance);
                invokePostConstruct(definition, instance);
            } catch (RuntimeException e) {
                earlySingletons.remove(definition);
                throw e;
            }
            if (definition.scope() == Scope.SINGLETON) {
                earlySingletons.remove(definition);
                singletons.put(definition, instance);
                destructionOrder.add(new SingletonRecord(definition, instance));
            }
            return instance;
        } finally {
            creationStack.pop();
        }
    }

    private void injectFields(BeanDefinition definition, Object instance) {
        List<Field> fields = definition.injectFields();
        List<BeanDefinition> dependencies = definition.resolvedFieldDependencies();
        for (int i = 0; i < fields.size(); i++) {
            try {
                fields.get(i).set(instance, getInstance(dependencies.get(i)));
            } catch (IllegalAccessException e) {
                throw new BeanCreationException("Failed to inject field '" + fields.get(i).getName()
                        + "' of bean '" + definition.name() + "'", e);
            }
        }
    }

    private void invokePostConstruct(BeanDefinition definition, Object instance) {
        for (Method method : definition.postConstructMethods()) {
            try {
                method.invoke(instance);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new BeanCreationException("@PostConstruct method '" + method.getName()
                        + "' of bean '" + definition.name() + "' failed", e);
            }
        }
    }
}
