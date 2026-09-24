package com.example.di;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConstructorInjectionTest {

    interface Engine {
        String label();
    }

    @Component
    static class PetrolEngine implements Engine {
        @Override
        public String label() {
            return "petrol";
        }
    }

    @Component
    static class Car {
        private final Engine engine;

        @Inject
        Car(Engine engine) {
            this.engine = engine;
        }
    }

    @Component(name = "dieselEngine")
    static class DieselEngine implements Engine {
        @Override
        public String label() {
            return "diesel";
        }
    }

    @Component
    static class Truck {
        final Engine engine;

        @Inject
        Truck(@Qualifier("dieselEngine") Engine engine) {
            this.engine = engine;
        }
    }

    @Test
    void injectsConstructorDependencyByType() {
        try (Container container = new Container()) {
            container.register(PetrolEngine.class, Car.class);
            container.start();

            Car car = container.getBean(Car.class);
            assertThat(car.engine).isInstanceOf(PetrolEngine.class);
            assertThat(car.engine.label()).isEqualTo("petrol");
        }
    }

    @Test
    void injectsConstructorDependencyByNameWhenSeveralCandidatesExist() {
        try (Container container = new Container()) {
            container.register(PetrolEngine.class, DieselEngine.class, Truck.class);
            container.start();

            Truck truck = container.getBean(Truck.class);
            assertThat(truck.engine).isInstanceOf(DieselEngine.class);
            assertThat(container.getBean("dieselEngine", Engine.class).label()).isEqualTo("diesel");
        }
    }
}
