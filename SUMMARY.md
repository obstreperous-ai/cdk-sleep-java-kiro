# Project Summary

## Project Overview

This project implements a production-grade, event-driven sleep audio processing pipeline using AWS CDK with Java. The system automatically processes audio files uploaded to S3, orchestrates them through a Step Functions pipeline, synthesizes speech via Amazon Polly, stores results in S3 and DynamoDB, and delivers notifications via SNS.

The project was built iteratively using strict Test-Driven Development (TDD), with each infrastructure component added only after a corresponding assertion test was written and confirmed to fail.

## Key Decisions Made

### 1. TDD-First Development

Every infrastructure construct was preceded by a failing CDK assertion test. This ensured that:
- Requirements were codified before implementation
- Regressions were caught immediately
- The test suite serves as living documentation of expected behavior

### 2. Single Stack Architecture (CdkBaseStack)

All pipeline resources live in a single `CdkBaseStack` rather than being split across multiple stacks. This simplifies:
- Cross-resource references (no export/import complexity)
- Deployment ordering (single atomic deployment)
- Testing (one `Template.fromStack()` call covers everything)

### 3. Lambda-Encapsulated Polly Integration

Rather than calling Polly directly from Step Functions via SDK integration, the audio processing logic (S3 download, Polly synthesis, S3 upload, DynamoDB metadata update) is encapsulated in a single Python Lambda function. This provides:
- A single retry/catch boundary for the entire processing step
- Ability to perform multi-step logic (download, transform, upload) atomically
- Easier local testing and debugging of the processing logic
- Defense-in-depth input validation within the Lambda

### 4. Choice-Based Input Validation

File extension validation uses a Step Functions Choice state before invoking the Lambda. Invalid files are rejected immediately without incurring Lambda execution costs. The Lambda also re-validates as a defense-in-depth measure.

### 5. Dual SNS Topics (Success/Failure)

Separate KMS-encrypted SNS topics for success and failure notifications allow subscribers to opt into only the events they care about. This is cleaner than a single topic with message filtering.

### 6. CDK Pipelines Skeleton

A `PipelineStack` with a placeholder CodeStar connection ARN provides the foundation for CI/CD. It synthesizes successfully but is not deployed until a real connection is provisioned. This validates the pipeline construct without requiring AWS account setup.

### 7. Direct SDK Integration for DynamoDB

Step Functions `CallAwsService` is used for DynamoDB PutItem and UpdateItem operations rather than Lambda functions. This reduces Lambda invocations (and cost) for simple data operations while keeping the state machine definition self-contained.

### 8. Granular Error Catching

The Lambda task has service-specific Catch blocks (`Lambda.ServiceException`, `Lambda.AWSLambdaException`) evaluated before a `States.ALL` fallback. This provides a foundation for future differentiated error handling per service error type.

## What Was Built

### Infrastructure Components (CdkBaseStack)

| Component | CDK ID | Key Configuration |
|-----------|--------|-------------------|
| S3 Input Bucket | `SleepAudioInputBucket` | SSE-S3, versioning, block public access, SSL enforcement, EventBridge enabled |
| S3 Output Bucket | `SleepAudioOutputBucket` | SSE-S3, versioning, block public access, SSL enforcement |
| DynamoDB Table | `SleepAudioMetadataTable` | PAY_PER_REQUEST, audioId partition key, PITR, RETAIN removal policy |
| Lambda Function | `SleepAudioProcessor` | Python 3.12, 256MB, 120s timeout, X-Ray active tracing |
| Step Functions | `SleepAudioPipelineStateMachine` | 7 states, CloudWatch logging (ALL), X-Ray tracing |
| EventBridge Rule | `SleepAudioInputRule` | Triggers on Object Created from input bucket |
| SNS (Success) | `SleepAudioPipelineCompleted` | KMS-encrypted |
| SNS (Failure) | `SleepAudioPipelineFailed` | KMS-encrypted |
| KMS Key | `SnsEncryptionKey` | Key rotation enabled |
| CloudWatch Alarm | `StateMachineExecutionFailedAlarm` | Threshold >= 1, period 300s |
| CloudWatch Alarm | `LambdaErrorAlarm` | Threshold >= 1, period 300s |
| CloudWatch Dashboard | `SleepAudioPipelineDashboard` | 2 widgets: state machine and Lambda metrics |
| CloudWatch LogGroup | `SleepAudioPipelineLogGroup` | 1-week retention, DESTROY removal policy |

### Step Functions States

1. **PutMetadataRecord** - DynamoDB PutItem (status=PROCESSING)
2. **ValidateFileExtension** - Choice state (.wav, .mp3, .ogg)
3. **ProcessAudioMetadata** - LambdaInvoke (download, Polly, upload, DB update)
4. **UpdateMetadataStatus** - DynamoDB UpdateItem (status=COMPLETED + output metadata)
5. **PublishSuccessNotification** - SNS Publish to completed topic
6. **UpdateMetadataStatusFailed** - DynamoDB UpdateItem (status=FAILED + error info)
7. **PublishFailureNotification** - SNS Publish to failed topic
8. **ValidationError** - Pass state injecting error context for invalid extensions

### Lambda Function (Python)

- Downloads input from S3
- Text files: reads content for Polly synthesis (up to 3000 chars)
- Audio files: generates relaxation narration from filename
- Calls Polly SynthesizeSpeech (Neural engine, Joanna voice, MP3 output)
- Uploads processed audio to output bucket
- Returns structured metadata for DynamoDB update

### CI/CD Pipeline

- `PipelineStack`: CDK Pipelines with CodePipeline, placeholder GitHub connection
- `.github/workflows/ci.yml`: Tests + CDK synth for dev and prod on every push/PR

## Architecture Highlights

- **Event-driven**: No polling; S3 events flow through EventBridge to Step Functions
- **Serverless**: All compute is Lambda-based with pay-per-use pricing
- **Observable**: X-Ray tracing, CloudWatch alarms and dashboard, structured JSON logs
- **Secure**: KMS-encrypted SNS, SSL enforcement on S3, block public access, least-privilege IAM
- **Resilient**: Retry policies on all critical tasks, granular Catch blocks for error isolation
- **Multi-environment**: CDK context-driven configuration (dev/stage/prod)
- **Testable**: 104+ assertions validate every component without AWS credentials

## Testing Strategy

### Coverage Summary

- **15 test files** with **104+ test methods**
- Tests validate synthesized CloudFormation templates using CDK assertions
- Step Functions flow is validated by parsing the DefinitionString JSON with Jackson ObjectMapper
- No AWS credentials or deployed resources needed for testing

### Test Categories

1. **Unit/Resource Tests**: Validate individual resource properties (encryption, billing mode, runtime, etc.)
2. **Integration Tests**: Validate cross-resource wiring (EventBridge to Step Functions, Lambda permissions, SNS grants)
3. **Flow Tests**: Validate state machine ordering, Catch routing, retry configuration
4. **End-to-End Tests**: Validate complete pipeline integrity (resource counts, IAM permissions, notification payloads)
5. **Multi-Environment Tests**: Validate environment tagging via CDK context
6. **Snapshot Tests**: Full template regression detection

### Key Testing Insight

CDK Java jsii bindings expand IAM policy statement arrays into individual text-valued statements when accessed via `template.findResources()`. Tests checking IAM actions must verify individual textual Action values rather than using array matching.

## Environment Setup Notes

- **Java 25 runtime** with source level 17 (via `maven-compiler-plugin` release=17)
- **Maven 3.9.10** with proxy settings in `~/.m2/settings.xml` for corporate environments
- **Node.js 22** via nvm; `NODE_OPTIONS` must be cleared for CDK commands (`NODE_OPTIONS="" npx cdk synth`)
- **Tool versions** managed via `mise.toml`
- **No Docker required** for building or testing

## Known Limitations / Future Work

| Item | Description |
|------|-------------|
| Bedrock Enhancement | Feature-flagged Lambda for AI-generated sleep sounds (not yet implemented) |
| S3 KMS CMK | Buckets use SSE-S3; migration to customer-managed KMS keys per environment is planned |
| Pipeline Activation | PipelineStack uses a placeholder connection ARN; needs real CodeStar connection |
| Alarm Actions | Alarms notify via SNS but no subscriber endpoints are configured |
| Log Retention | Currently fixed at 1 week; should be environment-specific (7/30/90 days) |
| S3 Lifecycle | No lifecycle policies on output bucket; should transition to Intelligent-Tiering |
| Cognito Integration | No user authentication; user_id derived from S3 metadata headers |
| API Gateway | No REST API for presigned upload URLs |
| CloudFront | No CDN distribution for processed audio |

## Experiment Notes

### Development Approach

This project was built incrementally using AI-assisted development with strict TDD discipline. Each feature was implemented in isolated tasks, with clear acceptance criteria and verification steps.

### Build Process

The build validates two things:
1. `mvn test` - All CDK assertion tests pass (verifies infrastructure correctness)
2. `npx cdk synth` - CloudFormation template synthesizes without errors (verifies CDK construct compatibility)

Both checks run in CI for dev and prod environments.

### What Worked Well

- **TDD provided confidence**: Every change was immediately validated against 104+ assertions
- **CDK assertions are powerful**: Template-level testing catches misconfiguration before deployment
- **Single stack simplicity**: Avoided cross-stack reference complexity
- **Step Functions for orchestration**: Built-in retries and catches eliminated custom error handling code
- **Separation of validation levels**: Choice state prevents unnecessary Lambda invocations for invalid files

### Challenges Encountered

- **CDK Java jsii quirks**: IAM policy statements are expanded differently than expected; tests needed adaptation
- **Step Functions JSON parsing**: Validating state machine flow requires parsing the DefinitionString JSON, which adds test complexity
- **Node.js proxy interference**: `NODE_OPTIONS` bootstrap module conflicts required clearing the variable for CDK commands
- **Template snapshot drift**: Full template snapshots are brittle and break on CDK version updates

### Metrics

| Metric | Value |
|--------|-------|
| Total test files | 15 |
| Total test methods | 104+ |
| Lines of Java (main) | ~300 (CdkBaseStack + CdkBaseApp + PipelineStack) |
| Lines of Python (Lambda) | ~200 |
| AWS services used | 10 (S3, EventBridge, Step Functions, Lambda, Polly, DynamoDB, SNS, KMS, CloudWatch, X-Ray) |
| State machine states | 8 |
| CloudWatch alarms | 2 |
| SNS topics | 2 (success + failure) |
