# CDK Sleep Audio Pipeline

This is an AWS CDK Java project that deploys an event-driven sleep audio processing pipeline. The architecture uses S3 for file storage, EventBridge for event routing, Lambda for audio processing, DynamoDB for metadata and analysis results, and SNS for downstream notifications. When a sleep audio file is uploaded to the input bucket, the pipeline automatically processes it and distributes results to the appropriate storage and notification endpoints.

## TDD Rules

This project enforces strict Test-Driven Development:

- **Tests before implementation** - Write a failing CDK assertion test before adding any construct to a stack.
- **Every infrastructure change must have a corresponding test** - No CDK construct is added without an assertion that validates its presence and configuration.
- **Red-Green-Refactor cycle** - Write a failing test (red), implement the minimum code to pass (green), then refactor while keeping tests green.

Run `mvn test` frequently to validate the current state.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for a detailed pipeline description and Mermaid diagram.

## Useful Commands

| Command         | Description                                        |
|-----------------|----------------------------------------------------|
| `mvn compile`   | Compile the project                                |
| `mvn test`      | Run CDK assertion tests                            |
| `mvn package`   | Compile, test, and package                         |
| `cdk synth`     | Synthesize the CloudFormation template             |
| `cdk deploy`    | Deploy the stack to your default AWS account       |
| `cdk diff`      | Compare deployed stack with current state          |
| `cdk ls`        | List all stacks in the app                         |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for commit conventions, TDD workflow, and development setup.
