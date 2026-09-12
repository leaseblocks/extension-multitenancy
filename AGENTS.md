# Multitenancy extension agent instructions

- This is the LeaseBlocks fork; inspect the local source and [pom.xml](pom.xml) before applying upstream examples or sibling extension versions.
- Keep core tenant components in [multitenancy](multitenancy/) and Spring Boot wiring in [multitenancy-spring-boot-autoconfigure](multitenancy-spring-boot-autoconfigure/).
- Honor each module's compiler settings: the parent targets Java 8, while the Boot integration modules have their own Java settings. Running Maven on JDK 17 does not change the core language level.
- Use `TenantContext` scopes or its callback helpers to restore the prior tenant after work, including exceptions. Close scopes on the same thread that opened them.
- When changing tenant registration or removal, check subscriptions, per-tenant component segments, and their cleanup paths together.
- Validate Spring Boot wiring in the relevant Boot 3 and Boot 4 integration modules; JDK 17+ activates both through `java17-modules`.
- Keep upstream license notices and attribution. Build commands and module links are in [README.md](README.md).
