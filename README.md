# CDK Sleep Audio Pipeline

An event-driven sleep audio processing pipeline built with AWS CDK (Java). When audio files are uploaded to an S3 input bucket, the system automatically processes them through a Step Functions orchestration pipeline that validates inputs, synthesizes speech via Amazon Polly, stores processed audio in an output bucket, tracks metadata in DynamoDB, and delivers notifications through SNS topics.

## Architecture Summary

The pipeline leverages the following AWS services:

| Service | Role |
|---------|------|
| **Amazon S3** | Input bucket (raw uploads) and output bucket (processed audio, versioned) |
| **Amazon EventBridge** | Detects S3 PutObject events and routes them to the state machine |
| **AWS Step Functions** | Orchestrates the processing pipeline with built-in retries and error handling |
| **AWS Lambda** (Python 3.12) | Performs audio processing: downloads from S3, calls Polly for TTS, uploads results |
| **Amazon Polly** | Neural text-to-speech synthesis (Joanna voice, MP3 output) |
| **Amazon DynamoDB** | Stores processing metadata (audioId, status, timestamps, output location) |
| **Amazon SNS** | KMS-encrypted topics for success and failure notifications |
| **AWS KMS** | Manages encryption keys for SNS topics (with automatic key rotation) |
| **Amazon CloudWatch** | Alarms (execution failures, Lambda errors), dashboard, and logging |
| **AWS X-Ray** | Distributed tracing across Lambda and Step Functions |
| **CDK Pipelines** | CI/CD skeleton for automated deployments |

The Step Functions state machine follows this flow:

```
Upload -> EventBridge -> PutMetadataRecord (DynamoDB) -> ValidateFileExtension (Choice)
  -> [valid] -> ProcessAudioMetadata (Lambda) -> UpdateMetadataStatus (COMPLETED) -> PublishSuccessNotification
  -> [invalid] -> ValidationError -> UpdateMetadataStatusFailed (FAILED) -> PublishFailureNotification
```

All critical tasks have retry policies for transient errors, and Catch blocks route failures to the error notification path.

For the complete architecture with Mermaid diagram, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Prerequisites

- **Java 17** or later (project compiled at source level 17)
- **Maven 3.9+** (build and dependency management)
- **Node.js 20+** (required for AWS CDK CLI)
- **AWS CDK CLI** (`npm install -g aws-cdk`)
- **AWS account and credentials** (for deployment only; tests run without AWS access)

## Getting Started

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd cdk-sleep-java-kiro
   ```

2. **Compile the project:**
   ```bash
   mvn compile
   ```

3. **Run tests:**
   ```bash
   mvn test
   ```

4. **Synthesize the CloudFormation template:**
   ```bash
   npx cdk synth
   ```

## Running Tests

The project includes 15 test files with 100+ assertions covering all infrastructure components:

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=EndToEndFlowTest

# Run with verbose output
mvn test -X
```

### Test Coverage

| Test File | Coverage Area |
|-----------|--------------|
| `CdkBaseTest.java` | Core stack synthesis and basic resource validation |
| `StepFunctionsTest.java` | State machine definition, state transitions |
| `DynamoDbMetadataTest.java` | DynamoDB table configuration and properties |
| `LambdaFunctionTest.java` | Lambda function configuration and permissions |
| `SnsNotificationTest.java` | SNS topics, KMS encryption, publish permissions |
| `PipelineWiringTest.java` | EventBridge rule, state machine trigger integration |
| `InputValidationTest.java` | Choice state validation logic, error paths |
| `AudioProcessingTest.java` | Lambda invoke task, processing chain |
| `AdvancedErrorHandlingTest.java` | Catch blocks, retry policies, failure paths |
| `ObservabilityTest.java` | CloudWatch alarms, dashboard, X-Ray tracing |
| `EndToEndFlowTest.java` | Pipeline flow ordering, state chain validation |
| `ComprehensiveEndToEndTest.java` | Cross-cutting E2E: resource counts, IAM, integration |
| `MultiEnvironmentTest.java` | Environment tagging via CDK context |
| `PipelineConstructTest.java` | CDK Pipelines stack synthesis |
| `SnapshotTest.java` | Full template snapshot for regression detection |

## Deployment

### Deploy to dev (default)

```bash
npx cdk deploy
```

### Deploy to a specific environment

```bash
npx cdk deploy -c environment=prod
npx cdk deploy -c environment=stage
```

### Compare changes before deploying

```bash
npx cdk diff
```

### List all stacks

```bash
npx cdk ls
```

## Environment Configuration

The project uses CDK context values to drive multi-environment configuration. The default environment is `dev`, configured in `cdk.json`.

Override the environment at synth/deploy time:

```bash
npx cdk synth -c environment=prod
```

The `environment` context value controls:

- Resource tagging (all resources receive an `Environment` tag)
- Stack naming conventions
- Future environment-specific settings (log retention, alarm thresholds)

### CDK Context Defaults

Defined in `cdk.json`:
- `environment`: `"dev"` (default)
- AWS CDK feature flags for best-practice defaults

## Project Structure

```
cdk-sleep-java-kiro/
├── src/
│   ├── main/
│   │   ├── java/com/myorg/
│   │   │   ├── CdkBaseApp.java          # CDK app entry point
│   │   │   ├── CdkBaseStack.java        # Main infrastructure stack
│   │   │   └── PipelineStack.java        # CI/CD pipeline stack
│   │   └── resources/
│   │       └── lambda/audio-processor/
│   │           └── index.py              # Python Lambda function
│   └── test/
│       └── java/com/myorg/              # 15 test files (100+ tests)
├── .github/
│   ├── workflows/ci.yml                  # CI: tests + cdk synth (dev + prod)
│   └── AGENT_GUIDELINES.md              # Guidelines for AI contributors
├── ARCHITECTURE.md                       # Detailed architecture and Mermaid diagram
├── CONTRIBUTING.md                       # Contribution guidelines and TDD workflow
├── SUMMARY.md                            # Project summary and experiment notes
├── cdk.json                              # CDK configuration and context
├── pom.xml                               # Maven build configuration
└── mise.toml                             # Tool version management
```

## CI/CD

The GitHub Actions workflow (`.github/workflows/ci.yml`) runs on every push to `main` and on pull requests:

1. Sets up Java 17 and Node.js 20
2. Installs the AWS CDK CLI
3. Runs all tests (`mvn test`)
4. Synthesizes CloudFormation for the default (dev) environment (`cdk synth`)
5. Synthesizes CloudFormation for the production environment (`cdk synth -c environment=prod`)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for commit conventions, TDD workflow, and development setup.

## Further Reading

- [ARCHITECTURE.md](ARCHITECTURE.md) - Detailed architecture, data flow, and Mermaid diagram
- [SUMMARY.md](SUMMARY.md) - Key decisions, what was built, and experiment notes
- [.github/AGENT_GUIDELINES.md](.github/AGENT_GUIDELINES.md) - Guidelines for AI agent contributors

## License

See [LICENSE](LICENSE) for details.
