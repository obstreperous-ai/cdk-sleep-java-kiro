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

- **Java 17** or later (project compiles at source level 17)
- **Maven 3.9+** (for build and dependency management)
- **Node.js 20+** (required for AWS CDK CLI)
- **AWS CDK CLI** (`npm install -g aws-cdk`)

### Building and Testing

```bash
# Compile the project
mvn compile

# Run all tests (104+ assertions across 15 test files)
mvn test

# Run a specific test class
mvn test -Dtest=EndToEndFlowTest

# Package (compile + test)
mvn package

# Synthesize CloudFormation template (requires CDK CLI)
npx cdk synth

# Synthesize for a specific environment
npx cdk synth -c environment=prod
```

### Important Notes

- If `NODE_OPTIONS` is set in your environment, clear it before running CDK commands: `NODE_OPTIONS="" npx cdk synth`
- The project uses Maven proxy settings; ensure `~/.m2/settings.xml` is configured if behind a corporate proxy
- Tests run entirely locally using CDK assertion APIs; no AWS credentials are needed for testing

### Project Structure

```
src/main/java/com/myorg/     - CDK app and stack definitions
src/main/resources/lambda/   - Python Lambda function source
src/test/java/com/myorg/     - CDK assertion tests (15 files)
pom.xml                      - Maven configuration and dependencies
cdk.json                     - CDK toolkit configuration and context
```

## Architecture Sync

When modifying CDK constructs:

1. Update the corresponding test first (TDD)
2. Implement the change in `CdkBaseStack.java`
3. Update the Mermaid diagram in [ARCHITECTURE.md](ARCHITECTURE.md) to reflect the change
4. Run `mvn test` to verify all tests pass
5. Run `npx cdk synth` to verify synthesis succeeds

The Mermaid diagram must always represent the current state of deployed infrastructure.

## Testing Patterns

This project uses the following CDK assertion patterns:

- **Template.fromStack()** - Synthesize a stack and assert against the CloudFormation template
- **hasResourceProperties()** - Verify a resource exists with specific properties
- **Match.objectLike() / Match.arrayWith()** - Flexible property matching
- **ObjectMapper JSON parsing** - Parse state machine DefinitionString for Step Functions flow validation
- **findResources()** - Count and inspect resources by type

### Example Test

```java
@Test
public void testInputBucketExists() {
    Template template = Template.fromStack(stack);
    template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
        "BucketEncryption", Match.objectLike(Map.of(
            "ServerSideEncryptionConfiguration", Match.anyValue()
        ))
    )));
}
```

## CI Pipeline

The GitHub Actions workflow validates every push and pull request:

1. Runs all Maven tests
2. Synthesizes CloudFormation for the dev environment
3. Synthesizes CloudFormation for the prod environment

Ensure your changes pass all three checks before opening a pull request.

## AI Agent Contributors

If you are an AI agent contributing to this project, review and follow the guidelines in [.github/AGENT_GUIDELINES.md](.github/AGENT_GUIDELINES.md). Key requirements:

- Tests before implementation (no exceptions)
- Keep ARCHITECTURE.md Mermaid diagram in sync with code
- Use conventional commit messages
- Be explicit and verbose in explanations
