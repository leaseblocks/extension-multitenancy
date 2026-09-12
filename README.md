# Axon Framework — Multitenancy Extension

Tenant-aware Axon messaging, event processing, and supporting components.

This is the LeaseBlocks fork of [AxonFramework/extension-multitenancy](https://github.com/AxonFramework/extension-multitenancy), included as a submodule in the LeaseBlocks workspace. [pom.xml](pom.xml) defines this checkout's artifact and dependency versions.

## Build and test

Use JDK 17 or later and the checked-in Maven wrapper from this directory:

```sh
./mvnw clean verify
./mvnw -Dcoverage clean verify
```

On JDK 17+, the `java17-modules` profile automatically includes the Spring Boot 3 and Spring Boot 4 integration-test modules. The `coverage` property adds the aggregate coverage module. Dependency and plugin versions are maintained in the parent and module POMs.

Integration tests use an Axon Server Testcontainer and require Docker.

## Modules

- [multitenancy](multitenancy/): core extension.
- [multitenancy-spring-boot-autoconfigure](multitenancy-spring-boot-autoconfigure/): Spring Boot configuration.
- [multitenancy-spring-boot-starter](multitenancy-spring-boot-starter/): starter dependency bundle.
- [multitenancy-spring-boot-3-integrationtests](multitenancy-spring-boot-3-integrationtests/) and [multitenancy-spring-boot-4-integrationtests](multitenancy-spring-boot-4-integrationtests/): framework integration checks.
- [coverage-report](coverage-report/): aggregate coverage reports.

## Documentation and license

See the [local documentation](docs/README.md) and [upstream reference guide](https://docs.axoniq.io/multitenancy-extension-reference/latest/). The upstream guide follows its own release; check this checkout's source and POMs when behavior differs.

Upstream support: [AxonIQ forum](https://discuss.axoniq.io/) and [issue tracker](https://github.com/AxonFramework/extension-multitenancy/issues).

Licensed under [Apache 2.0](LICENSE).
