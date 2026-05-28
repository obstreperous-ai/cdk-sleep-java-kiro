# Contributing

## Commit Message Format

This project uses conventional commits. Every commit message must follow this format:

```
<type>: <short description>
```

### Types

| Type       | Purpose                                      |
|------------|----------------------------------------------|
| `feat`     | New feature or infrastructure construct      |
| `fix`      | Bug fix                                      |
| `chore`    | Maintenance, dependency updates, tooling     |
| `docs`     | Documentation-only changes                   |
| `refactor` | Code restructuring without behavior change   |

### Examples

```
feat: add S3 input bucket for sleep audio uploads
fix: correct EventBridge rule event pattern
chore: update CDK dependency version range
docs: add DynamoDB table to ARCHITECTURE.md diagram
refactor: extract bucket configuration to shared method
```

## TDD-First Workflow

All changes must follow strict Test-Driven Development:

1. **Write a failing test** - Create a CDK assertion test that defines the expected behavior or resource before writing implementation code.
2. **Run tests and see it fail** - Execute `mvn test` and confirm the new test fails for the expected reason (e.g., resource not found in template).
3. **Implement** - Write the minimum CDK code needed to make the test pass.
4. **Run tests and see it pass** - Execute `mvn test` and confirm all tests pass, including the new one.
5. **Refactor** - Clean up code if needed while keeping all tests green.

Never submit code without a corresponding test. Every infrastructure construct must have at least one CDK assertion test validating its presence and key properties.

## Development Setup

### Prerequisites

- **Java 17** or later
- **Maven 3.9+**
- **AWS CDK CLI** (for synth/deploy commands, not required for tests)

### Building and Testing

```bash
# Compile the project
mvn compile

# Run all tests
mvn test

# Package (compile + test)
mvn package

# Synthesize CloudFormation template (requires CDK CLI)
cdk synth
```

### Project Structure

- `src/main/java/com/myorg/` - CDK app and stack definitions
- `src/test/java/com/myorg/` - CDK assertion tests
- `pom.xml` - Maven configuration and dependencies
- `cdk.json` - CDK toolkit configuration

## AI Agent Contributors

If you are an AI agent contributing to this project, review and follow the guidelines in [.github/AGENT_GUIDELINES.md](.github/AGENT_GUIDELINES.md). Key requirements:

- Tests before implementation (no exceptions)
- Keep ARCHITECTURE.md Mermaid diagram in sync with code
- Use conventional commit messages
- Be explicit and verbose in explanations
