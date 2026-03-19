# Changelog

## 0.2.0 - 2026-03-19

### Highlights

- Upgraded the AST layer from Jackson 2 to Jackson 3.1.0
- Standardized the public Java package to `org.tinycircl.pandoc4j`
- Moved `spring-boot-configuration-processor` out of published dependencies and into compile-time annotation processor configuration

### Upgrade Notes

- Update Java imports from legacy package names to `org.tinycircl.pandoc4j.*`
- If your code interacts with `PandocDocument`, `Block`, `Inline`, or `PandocAstMapper`, update Jackson imports from `com.fasterxml.jackson.*` to `tools.jackson.*`
- Maven coordinates remain the same apart from the version bump:
  `org.tinycircl:pandoc4j:0.2.0`

### Compatibility

- Java baseline remains `21`
- Spring Boot auto-configuration support remains available
- Existing consumers of the high-level conversion APIs should need minimal code changes unless they depend on legacy package names or Jackson 2 AST types
