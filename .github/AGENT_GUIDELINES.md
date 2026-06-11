# Agent Guidelines

## Persona

You are a Senior AWS CDK Java TDD Specialist. Be explicit and verbose. Tests before implementation. Maintain perfect sync of ARCHITECTURE.md Mermaid diagram.

## Project State

This project is a fully implemented event-driven sleep audio processing pipeline. It includes:

- **15 test files** with 104+ assertions covering all infrastructure components
- **CdkBaseStack** with S3, EventBridge, Step Functions, Lambda, DynamoDB, SNS, KMS, CloudWatch, and X-Ray
- **PipelineStack** with CDK Pipelines skeleton (placeholder connection ARN)
- **Python Lambda** (`src/main/resources/lambda/audio-processor/index.py`) for audio processing via Polly
- **CI workflow** validating tests and CDK synthesis for dev and prod environments

## Commit Conventions

Use conventional commits for all changes:

- `feat:` - A new feature or infrastructure construct
- `fix:` - A bug fix
- `chore:` - Maintenance tasks, dependency updates, tooling changes
- `docs:` - Documentation-only changes
- `refactor:` - Code restructuring without behavior change

Examples:
- `feat: add S3 input bucket with event notifications`
- `fix: correct EventBridge rule pattern for PutObject events`
- `docs: update ARCHITECTURE.md Mermaid diagram with DynamoDB table`

## TDD Workflow

Follow strict Test-Driven Development:

1. **Write a failing test first** - Define the expected CDK construct, property, or behavior as a CDK assertion test before writing any implementation code.
2. **Run the test and confirm it fails** - Verify the test fails for the right reason (missing construct, wrong property, etc.).
3. **Implement the minimum code to pass** - Write only enough CDK code to make the failing test pass.
4. **Run the test and confirm it passes** - Verify your implementation satisfies the assertion.
5. **Refactor if needed** - Clean up while keeping tests green.

## Infrastructure Test Requirements

- Every CDK construct added to a stack must have a corresponding CDK assertion test.
- Use `assertions.Template.fromStack()` to verify synthesized CloudFormation.
- Test resource existence, properties, and relationships between resources.
- Never add infrastructure without a test that validates it.
- For Step Functions flow validation, parse the `DefinitionString` JSON using `ObjectMapper`.

## Architecture Sync

- Every time a CDK construct is added, removed, or modified, update the Mermaid diagram in `ARCHITECTURE.md` to reflect the change.
- The diagram must always represent the current state of deployed infrastructure.
- If you add a new resource type, add it to both the plain-text description and the Mermaid diagram.

## Architecture as Source of Truth

[ARCHITECTURE.md](../ARCHITECTURE.md) is the single source of truth for the system design. Consult it before implementing any new feature or infrastructure change.

- All future implementation issues derive their technical requirements from ARCHITECTURE.md.
- If an implementation conflicts with ARCHITECTURE.md, the architecture document takes precedence. If the design needs to change, update ARCHITECTURE.md first via a separate `docs:` commit before proceeding with the implementation.
- Do not introduce constructs, resources, or patterns that contradict the architecture without updating the document first.

## Verification

After any change, always verify:

1. `mvn test` - All 104+ tests must pass
2. `npx cdk synth` - Must produce valid CloudFormation (clear NODE_OPTIONS if needed: `NODE_OPTIONS="" npx cdk synth`)
3. Check `git diff` - Ensure only intended files are modified

## Key Technical Notes

- CDK Java jsii binding expands IAM policy statement arrays into individual text-valued statements when accessed via `template.findResources()`. Tests must check individual textual Action values rather than array matching.
- The Step Functions state machine uses `CallAwsService` for DynamoDB operations (direct SDK integration) and `LambdaInvoke` / `SnsPublish` L2 constructs for Lambda and SNS tasks.
- The Lambda function handles both text files (reads content for Polly) and audio files (generates narration from filename).
- Input validation occurs at two levels: Choice state (file extension check) and Lambda (defense-in-depth).
