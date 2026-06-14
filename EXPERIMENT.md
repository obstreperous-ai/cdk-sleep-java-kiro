# Experiment Design: AI-Driven TDD Infrastructure as Code

This document captures the experimental design, methodology, actors, prompting strategy, and preliminary observations from building a production-grade AWS CDK pipeline entirely through AI-agent-driven development with strict Test-Driven Development.

---

## Table of Contents

- [Overview and Goals](#overview-and-goals)
- [Methodology](#methodology)
  - [TDD-First Development](#tdd-first-development)
  - [Issue-Driven Workflow](#issue-driven-workflow)
  - [Architecture as Code](#architecture-as-code)
- [Actors and Setup](#actors-and-setup)
  - [Experiment Matrix](#experiment-matrix)
  - [This Repository](#this-repository)
  - [Agent Configuration](#agent-configuration)
- [Prompting Patterns and Meta-Prompts](#prompting-patterns-and-meta-prompts)
  - [Persona Definition](#persona-definition)
  - [Verification Gates](#verification-gates)
  - [Constraint Encoding](#constraint-encoding)
  - [Template-Based Workflows](#template-based-workflows)
- [Issue History Summary](#issue-history-summary)
- [Key Decisions and Trade-offs](#key-decisions-and-trade-offs)
- [Preliminary Observations](#preliminary-observations)
  - [Strengths](#strengths)
  - [Challenges](#challenges)
  - [Comparative Notes](#comparative-notes)
- [Metrics](#metrics)
- [Future Evaluation Criteria](#future-evaluation-criteria)

---

## Overview and Goals

### Experiment Purpose

This project is one cell in a larger experimental matrix designed to evaluate how different AI agents perform when building Infrastructure as Code (IaC) under strict TDD discipline. The overarching question:

> Can AI agents produce production-quality infrastructure code when given structured meta-prompts, strict TDD constraints, and architecture-as-source-of-truth workflows?

### Specific Goals

1. **Evaluate AI-driven IaC quality** - Measure whether AI agents can consistently produce correct, secure, observable infrastructure when guided by meta-prompts
2. **Test TDD discipline adherence** - Determine whether agents can maintain strict test-first development across 14 sequential issues without degradation
3. **Assess language suitability** - Compare how well CDK Java (with jsii bindings) works for AI-driven development versus other language/framework combinations
4. **Capture reusable patterns** - Extract meta-prompting patterns that generalize beyond this specific project
5. **Document challenges and failure modes** - Identify where AI agents struggle with infrastructure development and what mitigations help

### Scope

The experiment covers the full lifecycle of building an event-driven sleep audio processing pipeline:
- From empty repository to production-ready infrastructure
- 10 AWS services orchestrated through Step Functions
- 100+ CDK assertion tests validating every component
- Multi-environment deployment support (dev/stage/prod)
- CI/CD pipeline with automated validation

---

## Methodology

### TDD-First Development

Every infrastructure component in this project was built using strict Test-Driven Development:

```
1. Write a failing CDK assertion test
2. Run `mvn test` - confirm it fails for the expected reason
3. Implement the minimum CDK code to pass the test
4. Run `mvn test` - confirm ALL tests pass
5. Run `NODE_OPTIONS="" npx cdk synth` - confirm synthesis succeeds
6. Refactor if needed while keeping tests green
```

This approach ensures:
- Every resource exists because a test demanded it (no speculative infrastructure)
- The test suite serves as living documentation of expected behavior
- Regressions are caught immediately during development
- Refactoring is safe because 100+ assertions validate the output

### Issue-Driven Workflow

Development proceeded through 14 sequential GitHub issues, each representing a logical increment of functionality:

```
Issue Created -> AI Agent Picks Up -> TDD Implementation -> PR Submitted -> Merged
```

Each issue contained:
- Clear description of the feature or documentation goal
- Acceptance criteria defining what "done" looks like
- Technical requirements referencing ARCHITECTURE.md
- Scope boundaries to prevent feature creep

This structure gave the AI agent clear, bounded tasks rather than open-ended instructions.

### Architecture as Code

ARCHITECTURE.md served as the single source of truth for the system design:

- **Before implementation**: The agent reads ARCHITECTURE.md to understand how a new component fits the overall design
- **After implementation**: The agent updates the Mermaid diagram if the structure changed
- **On conflict**: The architecture document takes precedence over implementation; if the design needs to evolve, ARCHITECTURE.md is updated first via a separate commit

This prevents architectural drift and ensures the codebase stays aligned with the documented design intent.

### Conventional Commits

All commits follow the conventional commits specification:

| Type | Purpose |
|------|---------|
| `feat:` | New feature or infrastructure construct |
| `fix:` | Bug fix |
| `chore:` | Maintenance, dependencies, tooling |
| `docs:` | Documentation-only changes |
| `refactor:` | Code restructuring without behavior change |

This produces a machine-readable history that supports automated changelog generation and makes the project timeline navigable.

---

## Actors and Setup

### Experiment Matrix

The broader experiment spans **5 languages x 3 AIs**, producing 15 total repositories. Each repository implements the same sleep audio processing pipeline concept, allowing cross-comparison of:

| Dimension | Variants |
|-----------|----------|
| **Languages** | Java (CDK), TypeScript (CDK), Python (CDK), Go (CDK), HCL (Terraform) |
| **AI Agents** | Kiro, Claude (via other tooling), additional AI agents |

Each combination produces an independent repository built from scratch, following the same experimental protocol but with language-specific and agent-specific adaptations.

### This Repository

| Attribute | Value |
|-----------|-------|
| **AI Agent** | Kiro |
| **Language** | Java 17 (AWS CDK) |
| **Framework** | AWS CDK 2.255.0+ |
| **Test Framework** | JUnit 5 + CDK Assertions |
| **Build System** | Maven 3.9+ |
| **Repository** | `obstreperous-ai/cdk-sleep-java-kiro` |
| **Issues Completed** | 14 (sequential, issue-driven) |
| **Development Period** | 2026-05-28 to 2026-06-13 |

### Agent Configuration

The AI agent operated under the following configuration:

- **Persona**: "Senior AWS CDK Java TDD Specialist"
- **Behavioral constraints**: Tests before implementation, architecture sync, conventional commits
- **Verification gates**: `mvn test` + `NODE_OPTIONS="" npx cdk synth` before every commit
- **Knowledge sources**: ARCHITECTURE.md (design), AGENT_GUIDELINES.md (operational rules), existing test files (patterns)
- **Scope control**: Issue acceptance criteria define boundaries; no unrequested features

The agent was not given direct access to AWS. All validation occurred locally through CDK assertion tests and template synthesis.

---

## Prompting Patterns and Meta-Prompts

### Persona Definition

The persona establishes the agent's identity, priorities, and communication style:

```
You are a Senior AWS CDK Java TDD Specialist. Be explicit and verbose.
Tests before implementation. Maintain perfect sync of ARCHITECTURE.md
Mermaid diagram.
```

Key elements:
- **Technology specificity** ("AWS CDK Java") channels knowledge toward the relevant domain
- **Seniority level** ("Senior") implies awareness of edge cases, security, and best practices
- **Priority ordering** ("Tests before implementation") makes TDD non-negotiable
- **Behavioral instruction** ("Be explicit and verbose") improves traceability

### Verification Gates

Non-negotiable checkpoints that the agent must satisfy before completing any task:

1. **`mvn test`** - All CDK assertion tests pass (100+ assertions)
2. **`NODE_OPTIONS="" npx cdk synth`** - CloudFormation template synthesizes without errors
3. **`git diff`** - Only intended files are modified (no accidental changes)
4. **Conventional commit** - Message follows `type: description` format

These gates catch errors at multiple levels:
- Test failures catch logical errors in infrastructure configuration
- Synthesis failures catch CDK construct compatibility issues
- Diff review catches scope creep or unintended modifications

### Constraint Encoding

Explicit constraints prevent known failure modes:

- Never add infrastructure without a corresponding test
- Never modify ARCHITECTURE.md without also updating the Mermaid diagram
- Never use `git add .` or `git add -A` (stage specific files by name)
- Always clear NODE_OPTIONS before running CDK commands
- If an implementation conflicts with ARCHITECTURE.md, update the architecture first

### Template-Based Workflows

Four reusable prompt templates cover the primary workflow types:

1. **New Infrastructure Construct** - TDD cycle for adding AWS resources
2. **Bug Fix** - Minimal, targeted fix with root cause identification
3. **Refactor** - Structural improvement verified by unchanged CloudFormation output
4. **Documentation Update** - Accuracy-focused documentation maintenance

Each template encodes the persona, context, task structure, workflow steps, constraints, and verification gates. See [META-PROMPTS.md](META-PROMPTS.md) for the complete collection.

---

## Issue History Summary

The project was built through 14 sequential issues, progressing from bootstrap to documentation:

| # | Issue | PR | Date | Category |
|---|-------|----|------|----------|
| 1 | Bootstrap: Java CDK + Strict TDD + Agent Configuration | #2 | 2026-05-28 | Setup |
| 2 | Initial Architecture Design: Event-Driven Sleep Audio Pipeline | #4 | 2026-06-02 | Design |
| 3 | TDD: Core S3 Buckets + EventBridge Rule | #6 | 2026-06-03 | Infrastructure |
| 4 | TDD: Step Functions State Machine Skeleton + Polly Integration | #8 | 2026-06-04 | Infrastructure |
| 5 | TDD: DynamoDB Metadata Table + Basic State Machine I/O | #10 | 2026-06-05 | Infrastructure |
| 6 | TDD: SNS Notifications + Basic Error Handling & Status Updates | #12 | 2026-06-06 | Infrastructure |
| 7 | TDD: Basic Lambda Function Skeleton + State Machine Integration | #14 | 2026-06-07 | Infrastructure |
| 8 | TDD: Complete Pipeline Wiring, Input Validation & E2E Flow | #16 | 2026-06-08 | Integration |
| 9 | TDD: Pipeline Testing, Refinement & Deployment Prep | #18 | 2026-06-09 | Testing |
| 10 | TDD: Advanced Error Handling, Retries & Observability | #20 | 2026-06-10 | Reliability |
| 11 | TDD: Full Audio Processing Implementation & Output Handling | #22 | 2026-06-11 | Feature |
| 12 | TDD: End-to-End Validation, Documentation Polish & Project Completion | #24 | 2026-06-12 | Validation |
| 13 | Documentation: Review & Enrich README + Meta-Prompting Patterns | #26 | 2026-06-13 | Documentation |
| 14 | Documentation: Capture Experimental Design & Meta-Prompting Process | -- | 2026-06-14 | Documentation |

### Development Phases

**Phase 1: Foundation (Issues 1-2)** - Repository bootstrap, CI configuration, architecture design with Mermaid diagrams. Established the TDD workflow and agent guidelines.

**Phase 2: Core Infrastructure (Issues 3-7)** - Individual AWS resources added one at a time via TDD. Each issue introduced one or two services with corresponding assertion tests.

**Phase 3: Integration and Wiring (Issues 8-9)** - Connected individual components into a working pipeline. End-to-end flow validation. Deployment preparation.

**Phase 4: Production Hardening (Issues 10-11)** - Error handling, retry policies, observability (CloudWatch alarms, dashboards, X-Ray tracing), and full audio processing implementation.

**Phase 5: Validation and Documentation (Issues 12-14)** - Comprehensive end-to-end testing, documentation enrichment, meta-prompting pattern extraction, and experiment design capture.

---

## Key Decisions and Trade-offs

### 1. Single Stack vs. Multi-Stack

**Decision**: All resources live in a single `CdkBaseStack`.

**Trade-off**: Simplifies testing (one `Template.fromStack()` call) and deployment (atomic), but limits independent scaling of components. Acceptable for this pipeline's scope.

### 2. Lambda-Encapsulated Polly vs. Direct SDK Integration

**Decision**: Audio processing (S3 download, Polly synthesis, S3 upload, DynamoDB update) is encapsulated in a single Python Lambda rather than using Step Functions SDK integrations.

**Trade-off**: Increases Lambda cost per invocation but provides a single retry/catch boundary for multi-step logic. Easier to test and debug atomically.

### 3. Choice-Based Validation vs. Lambda Validation Only

**Decision**: File extension validation uses a Step Functions Choice state before invoking Lambda. Lambda also re-validates as defense-in-depth.

**Trade-off**: Dual validation adds complexity but prevents unnecessary Lambda invocations for clearly invalid files (cost optimization) while maintaining safety in the Lambda itself.

### 4. Dual SNS Topics vs. Single Topic with Filtering

**Decision**: Separate KMS-encrypted SNS topics for success and failure notifications.

**Trade-off**: Slightly more infrastructure to manage, but subscribers can opt into only the events they care about without message filtering complexity.

### 5. CDK Pipelines Skeleton vs. No Pipeline

**Decision**: Include a `PipelineStack` with a placeholder CodeStar connection ARN.

**Trade-off**: Validates the pipeline construct without requiring AWS account setup. Demonstrates the pattern without being deployable until a real connection is provisioned.

### 6. Python Lambda vs. Java Lambda

**Decision**: Lambda function written in Python 3.12 despite the CDK stacks being Java.

**Trade-off**: Python is more natural for scripting-style audio processing and has excellent Polly/S3 SDK ergonomics. Avoids Java cold start overhead for a Lambda that primarily orchestrates API calls.

### 7. Test Granularity

**Decision**: 15 test files organized by concern (resource type, integration point, flow, end-to-end) rather than a single test file.

**Trade-off**: More files to maintain, but each file has clear responsibility and tests can be run individually. Failure messages are more actionable because test names indicate what broke.

---

## Preliminary Observations

### Strengths

1. **Consistent TDD adherence** - The agent maintained test-first discipline across all 14 issues without degradation. No infrastructure was added without a corresponding test.

2. **Architecture alignment** - ARCHITECTURE.md stayed synchronized with the implementation throughout development. The Mermaid diagram accurately reflects the deployed system.

3. **Conventional commit discipline** - All commits follow the conventional format, producing a clean, navigable history.

4. **Incremental complexity management** - Starting with simple resources (S3 buckets) and building toward complex orchestration (Step Functions with error handling) allowed the test suite to grow gradually.

5. **Zero-credential testing** - The entire 100+ assertion test suite runs without AWS credentials, enabling fast feedback loops and CI validation.

6. **Multi-environment support** - CDK context-driven configuration was integrated naturally through the TDD process.

7. **Security by default** - KMS encryption, SSL enforcement, block public access, and least-privilege IAM were built in from the beginning, not bolted on later.

8. **Documentation quality** - The agent produced comprehensive, well-structured documentation (ARCHITECTURE.md at 435 lines, META-PROMPTS.md at 540 lines) that serves as genuine reference material.

### Challenges

1. **CDK Java jsii quirks** - The jsii bridge between Java and TypeScript CDK introduces behavioral differences (e.g., IAM policy array expansion) that are not well-documented. The agent had to discover and adapt to these through test failures.

2. **Step Functions JSON parsing** - No first-class CDK assertion API exists for validating state machine flow. Parsing DefinitionString JSON with Jackson ObjectMapper works but adds test complexity and fragility.

3. **Node.js environment interference** - Corporate proxy settings via `NODE_OPTIONS` conflict with CDK's jsii runtime. Requiring `NODE_OPTIONS=""` before CDK commands is a non-obvious workaround.

4. **Template snapshot brittleness** - Full CloudFormation template snapshots break on CDK version updates even when no code changes. They serve as regression detection but require periodic maintenance.

5. **Issue scope calibration** - Some issues (particularly integration-focused ones like #8) had broader scope than single-resource issues. Balancing granularity vs. issue overhead requires judgment.

6. **Lambda testing boundary** - CDK assertion tests validate the Lambda construct configuration (runtime, memory, timeout) but cannot validate the Lambda code logic. The Python function itself needs separate testing outside CDK assertions.

7. **Cross-issue context maintenance** - Each issue builds on previous work. The agent must understand the accumulated state of the codebase, not just the current issue's requirements.

### Comparative Notes

While this document focuses on the Java/Kiro combination, some observations are relevant for cross-comparison:

- **Java verbosity** - CDK Java requires more boilerplate than TypeScript or Python CDK equivalents. The agent handled this well, but test files tend to be longer.
- **jsii overhead** - The Java CDK runs through a JavaScript interop layer. This adds an abstraction that can produce unexpected behaviors (unlike TypeScript CDK which is native).
- **Maven build system** - Reliable and well-understood, but slower than direct TypeScript compilation. Test feedback loops are slightly longer.
- **Strong typing** - Java's type system catches errors at compile time that would be runtime errors in Python/TypeScript CDK. This may reduce certain categories of agent errors.

---

## Metrics

### Quantitative Summary

| Metric | Value |
|--------|-------|
| Total issues | 14 |
| Total pull requests | 13 (merged) |
| Development period | 17 days (2026-05-28 to 2026-06-14) |
| Test files | 15 |
| Test assertions | 100+ |
| AWS services | 10 |
| Lines of Java (main) | ~300 (CdkBaseStack + CdkBaseApp + PipelineStack) |
| Lines of Python (Lambda) | ~200 |
| State machine states | 8 |
| CloudWatch alarms | 2 |
| SNS topics | 2 |
| Documentation files | 7 (README, ARCHITECTURE, CONTRIBUTING, SUMMARY, META-PROMPTS, AGENT_GUIDELINES, EXPERIMENT) |
| CI checks per PR | 3 (tests, synth-dev, synth-prod) |

### Issue Cadence

Issues were completed at approximately one per day, demonstrating consistent throughput:

- Infrastructure issues (3-7): One service per day
- Integration issues (8-9): Wiring and validation, slightly more complex
- Hardening issues (10-11): Error handling and observability
- Documentation issues (12-14): Final polish and experiment capture

---

## Future Evaluation Criteria

This document serves as the foundation for a final evaluation of the experiment. When comparing across the 15 repositories (5 languages x 3 AIs), the following criteria should be assessed:

### Code Quality

- Test coverage completeness (are all resources tested?)
- Security posture (encryption, least-privilege, public access blocks)
- Error handling robustness (retries, catch blocks, failure notifications)
- Code organization and readability

### Process Quality

- TDD adherence (were tests truly written first?)
- Commit history cleanliness (conventional commits, logical increments)
- Architecture-code alignment (does the diagram match reality?)
- Documentation completeness and accuracy

### Agent Effectiveness

- Issues completed without human intervention
- Time per issue (throughput)
- Quality of error recovery (how did the agent handle unexpected failures?)
- Scope discipline (did the agent stay within issue boundaries?)

### Language Suitability

- Lines of code for equivalent functionality
- Build/test cycle speed
- Type safety benefits vs. verbosity costs
- Framework maturity and documentation availability
- Edge cases and quirks encountered

### Reproducibility

- Can another agent pick up where this one left off?
- Are the meta-prompts sufficient to guide a new agent through similar work?
- Is the documentation sufficient for a human to understand and maintain the system?

---

## References

- [README.md](README.md) - Project overview and quick start
- [ARCHITECTURE.md](ARCHITECTURE.md) - Full architecture with Mermaid diagrams
- [META-PROMPTS.md](META-PROMPTS.md) - Reusable meta-prompting patterns
- [SUMMARY.md](SUMMARY.md) - Key decisions and project metrics
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines and TDD workflow
- [.github/AGENT_GUIDELINES.md](.github/AGENT_GUIDELINES.md) - AI agent operational guidelines
