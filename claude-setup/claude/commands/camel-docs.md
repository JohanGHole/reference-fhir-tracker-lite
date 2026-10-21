---
description: Look up a Camel component, EIP, language or data format at the version this repo pins
argument-hint: <component|eip|language|dataformat name> [what you want to do with it]
---
Look up `$ARGUMENTS` in the Camel catalog using the Camel MCP server, at the version pinned in
CLAUDE.md (runtime spring-boot, camelVersion 4.21.0, platformBom
org.apache.camel.springboot:camel-spring-boot-bom:4.21.0).

1. Decide whether the name is a component, EIP, language or data format, and call the matching
   `camel_catalog_*_doc` tool. For components also try `includeOptions: all` if an option you
   expect is missing.
2. Reply with: the URI syntax or YAML shape, only the options relevant to what I described (name,
   type, default), and one YAML DSL snippet written the way this repo writes routes (descriptions,
   `{{placeholders}}` for configuration, quoted constants).
3. If the snippet is a full step or route, validate it with `camel_validate_yaml_dsl` before
   showing it.
