# One-time setup

Claude's remote file tools are not allowed to write agent configuration (`.claude/`, `.mcp.json`)
or dot-folders like `.mvn/`. Copy them into place yourself from the repo root:

```bash
cp claude-setup/mcp.json .mcp.json
mkdir -p .claude && cp -R claude-setup/claude/. .claude/
mkdir -p iol/.mvn && cp -R claude-setup/mvn/. iol/.mvn/
chmod +x iol/mvnw config/dhis2/bootstrap.sh
```

Then you can delete `claude-setup/`.

| File | Purpose |
|---|---|
| `.mcp.json` | Project-scoped Camel MCP server for Claude Code (`jbang … camel-jbang-mcp:LATEST:runner`) |
| `.claude/settings.json` | Pre-approves the read-only Camel MCP tools, `./mvnw`, `docker compose`, `curl` |
| `.claude/commands/*.md` | `/camel-docs`, `/new-route`, `/verify`, `/diagnose` slash commands used in docs/DEMO.md |
| `iol/.mvn/wrapper/maven-wrapper.properties` | Maven wrapper config (same as the lab-result reference) |
