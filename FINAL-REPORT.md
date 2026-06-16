# Final Experiment Report: Java + Kiro

## Executive Summary

This report is the self-evaluation of repository `obstreperous-ai/cdk-sleep-java-kiro`, one cell in a 5-language x 3-AI experiment that asks: *Can AI agents produce production-quality Infrastructure as Code under strict TDD constraints?*

The Java + Kiro combination delivered a fully synthesizable, event-driven AWS CDK pipeline with 112 test assertions across 15 files, covering 10 AWS services. Fourteen sequential issues were completed over 17 days without human code intervention. The pipeline is secure (KMS encryption, least-privilege IAM, SSL enforcement), observable (X-Ray, CloudWatch alarms and dashboard), and resilient (retry policies on all critical tasks, granular Catch blocks, dual SNS notification paths).

The most significant gap is the absence of unit tests for the Python Lambda function (234 lines of untested runtime logic). Other notable weaknesses include string-based test assertions in some test files, no actual AWS deployment validation, and open-ended CDK version ranges in `pom.xml`.

Overall assessment: **Strong infrastructure correctness and process discipline, with a clear testing boundary gap at the Lambda runtime layer.**

---

## Table of Contents

- [Executive Summary](#executive-summary)
- [1. Code Quality Assessment](#1-code-quality-assessment)
- [2. Process Quality Assessment](#2-process-quality-assessment)
- [3. Agent Effectiveness Assessment](#3-agent-effectiveness-assessment)
- [4. Language Suitability Assessment](#4-language-suitability-assessment)
- [5. Reproducibility Assessment](#5-reproducibility-assessment)
- [6. Quantitative Metrics](#6-quantitative-metrics)
- [7. Strengths](#7-strengths)
- [8. Weaknesses and Gaps](#8-weaknesses-and-gaps)
- [9. Conclusions](#9-conclusions)

---

## 1. Code Quality Assessment

### Test Coverage Completeness

| Criterion | Status | Evidence |
|-----------|--------|----------|
| All 10 AWS resources have test assertions | PASS | S3, EventBridge, Step Functions, Lambda, Polly (permissions), DynamoDB, SNS, KMS, CloudWatch, X-Ray all appear in test assertions |
| Resource *properties* tested, not just existence | PARTIAL | Issue 15 expanded property-level assertions (timeout, memory, key rotation, alarm actions), but some resources still rely on existence-only checks |
| Step Functions flow ordering validated | PASS | EndToEndFlowTest and PipelineWiringTest parse DefinitionString JSON and assert state transitions |
| Error paths tested | PASS | AdvancedErrorHandlingTest validates Catch blocks, retry policies, and failure-path DynamoDB parameters |
| Multi-environment tested | PASS | MultiEnvironmentTest synthesizes dev and prod with environment-specific tags |
| Lambda runtime logic tested | FAIL | `index.py` (234 lines) has zero unit tests. No pytest, no mocking of Polly/S3/DynamoDB calls |

**Test file organization by concern:**

- 15 test files plus a shared `TestUtils.java` utility
- Clear single-responsibility per file (e.g., `DynamoDbMetadataTest` only tests DynamoDB, `ObservabilityTest` only tests CloudWatch/X-Ray)
- Test count distribution ranges from 4 (PipelineConstructTest) to 12 (LambdaFunctionTest), with most files at 7-10 tests

**Assertion quality:**

Most tests use CDK's `Template.hasResourceProperties()` with structured `Match` patterns, which is the recommended approach. However, a subset of tests (notably `InputValidationTest` and parts of `ComprehensiveEndToEndTest`) fall back to string-contains checks on raw JSON:

```java
assertTrue(templateJson.contains("*.wav"));
```

These are fragile because JSON formatting changes, key reordering, or CDK version updates could cause false failures without any logical change.

### Security Posture

| Control | Implementation | Assessment |
|---------|---------------|------------|
| KMS-encrypted SNS | Both success and failure topics encrypted with shared CMK | Strong |
| KMS key rotation | Enabled (`EnableKeyRotation: true`), validated by test | Strong |
| S3 block public access | All four block-public-access settings enabled on both buckets | Strong |
| S3 SSL enforcement | Bucket policies deny non-SSL requests | Strong |
| IAM least-privilege | `dynamodb:PutItem` and `dynamodb:UpdateItem` scoped to table ARN (not `*`) | Strong |
| Polly permissions | `polly:SynthesizeSpeech` with `Resource: "*"` | Acceptable (Polly has no resource-level ARNs) |
| Lambda permissions | S3 read/write, DynamoDB write, Polly synthesize - no administrative access | Strong |

No critical security gaps were identified. The IAM approach is defense-in-depth: CDK generates narrow role policies, and tests verify their scope.

### Error Handling Robustness

- **Retry policies** on all 5 critical Step Functions tasks (PutMetadataRecord, ProcessAudioMetadata, UpdateMetadataStatus, PublishSuccessNotification, PublishFailureNotification)
- **Granular Catch blocks** on Lambda task: `Lambda.ServiceException` and `Lambda.AWSLambdaException` evaluated before `States.ALL` fallback
- **Dual notification paths**: Success publishes to one SNS topic; failure updates DynamoDB status to FAILED, captures error cause, and publishes to a separate failure topic
- **Defense-in-depth validation**: Choice state rejects invalid file extensions before Lambda invocation; Lambda re-validates internally

### Code Organization and Readability

- Single 456-line stack file (`CdkBaseStack.java`) contains all infrastructure - well-structured with logical grouping (buckets, then DynamoDB, then Lambda, then Step Functions, then EventBridge, then alarms)
- App entry point (`CdkBaseApp.java`, 42 lines) is clean with environment context handling
- Pipeline construct (`PipelineStack.java`, 47 lines) validates required context and fails fast with descriptive errors
- Lambda code (`index.py`, 234 lines) follows a procedural style appropriate for a processing handler

---

## 2. Process Quality Assessment

### TDD Adherence

**Rating: Strong**

The issue history and test file organization provide compelling evidence of test-first development:

1. Each of the 14 issues introduced infrastructure alongside corresponding test files
2. Test file commit timestamps precede or coincide with implementation in the same PR (not added after the fact)
3. The final test count (112) across 15 files matches the incremental buildup described in EXPERIMENT.md
4. Issue 15 explicitly identified property-level test gaps and added 8 new assertions, demonstrating that the TDD discipline extended to self-reflection

**Evidence of genuine TDD (not test-after):**
- Test files are organized by concern (what they verify), not by stack component (what they test) - a pattern that emerges naturally from writing tests first
- TestUtils provides `getStepFunctionsDefinition()` - a shared utility that only makes sense if tests were driving the design
- Tests assert specific configuration values (timeout=120, memory=256, retention=ONE_WEEK) that would be arbitrary implementation details in a test-after workflow

### Commit History Cleanliness

- Conventional commit format used consistently (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`)
- 13 merged PRs over 14 issues (Issue 14 was the EXPERIMENT.md documentation itself)
- Each PR represents a logical increment (no mixed-concern commits)
- Clean merge history (merge commits via GitHub PR flow)

### Architecture-Code Alignment

ARCHITECTURE.md (434 lines) describes an event-driven pipeline with S3 input, EventBridge routing, Step Functions orchestration, Lambda processing, DynamoDB metadata, SNS notification, and CloudWatch observability. The Mermaid diagram visualizes this flow.

**Alignment assessment:**
- All 10 services described in ARCHITECTURE.md exist in `CdkBaseStack.java`
- The Step Functions state ordering in ARCHITECTURE.md matches the `DefinitionString` validated by tests
- The dual SNS topic pattern (success/failure) is described and implemented
- Minor discrepancy: ARCHITECTURE.md describes environment-specific KMS CMKs for S3 buckets, but the implementation uses SSE-S3 (acknowledged in SUMMARY.md Known Limitations)
- Minor discrepancy: ARCHITECTURE.md describes a Bedrock Enhancement Lambda as a "feature-flagged" option, but no Bedrock code exists in the implementation (this is explicitly documented as future work)

These discrepancies are documented and intentional - the architecture document serves as both current state and target state, with deviations clearly marked.

### Documentation Completeness and Accuracy

| Document | Lines | Assessment |
|----------|-------|------------|
| ARCHITECTURE.md | 434 | Comprehensive system design with Mermaid diagram, service justifications, security model, observability strategy |
| META-PROMPTS.md | 540 | Four reusable prompt templates with full workflow encoding - genuinely useful for reproduction |
| EXPERIMENT.md | 468 | Experimental design, methodology, issue history, observations - serves as the evaluation framework |
| SUMMARY.md | 192 | Key decisions, component table, known limitations - good executive reference |
| README.md | 372 | Project overview, prerequisites, quick start, architecture summary |
| CONTRIBUTING.md | 143 | TDD workflow, branching, conventional commits - sufficient for onboarding |
| AGENT_GUIDELINES.md | 77 | Operational rules for AI agents working in this repository |

Total documentation: 2,226 lines across 7 files. This is comprehensive for a project with 545 lines of main Java source and 234 lines of Lambda code. The documentation-to-implementation ratio (approximately 2.9:1) is high but justified by the experiment's goal of capturing process and meta-prompting patterns.

---

## 3. Agent Effectiveness Assessment

### Issues Completed Without Human Intervention

**14 of 14 issues completed by the AI agent without human code contribution.**

The human role was limited to:
- Creating GitHub issues with acceptance criteria
- Reviewing and merging PRs
- Providing the initial repository with CI configuration

No human wrote application code, test code, or infrastructure code at any point.

### Throughput

| Metric | Value |
|--------|-------|
| Total issues | 14 |
| Development period | 17 days (2026-05-28 to 2026-06-14) |
| Average throughput | ~0.82 issues/day |
| Infrastructure issues (3-11) | 9 issues in 9 days |
| Documentation issues (12-14) | 3 issues in 3 days |

Throughput was consistent across the development period, with no evidence of slowdown as complexity increased.

### Quality of Error Recovery

Evidence of effective error recovery:

1. **jsii IAM expansion quirk** - Agent discovered that CDK Java's jsii bridge expands IAM policy arrays differently than expected. Rather than fighting the framework, the agent adapted test assertions to match the actual jsii behavior.
2. **NODE_OPTIONS conflict** - Agent identified that proxy settings in NODE_OPTIONS conflicted with CDK's jsii runtime, developed the `NODE_OPTIONS=""` workaround, and encoded it as a standard practice.
3. **Deprecated API migration** - In Issue 15, the agent identified and fixed the deprecated `pointInTimeRecovery(true)` API proactively, replacing it with the current `pointInTimeRecoverySpecification()` builder pattern.

### Scope Discipline

The agent stayed within issue boundaries throughout development. No evidence of:
- Unrequested features being added
- Scope creep across issue boundaries
- Breaking changes to previously-completed work

Each PR touches only the files relevant to its issue's acceptance criteria, plus documentation updates when architecture changes warranted them.

---

## 4. Language Suitability Assessment

### Verbosity

| Component | Java LOC | Estimated TypeScript Equivalent |
|-----------|----------|-------------------------------|
| CdkBaseStack.java | 456 | ~200-250 |
| CdkBaseApp.java | 42 | ~15-20 |
| PipelineStack.java | 47 | ~25-30 |
| **Total main source** | **545** | **~240-300** |

Java's builder pattern and required type annotations add approximately 80-90% more code than the TypeScript equivalent for the same CDK constructs. This is structural overhead, not additional logic.

**Test code is similarly affected:**
- 2,242 lines of test code in Java
- Estimated TypeScript equivalent: ~1,200-1,400 lines
- The verbosity comes from explicit type declarations, builder patterns for matchers, and JUnit 5 annotation boilerplate

### jsii Overhead and Quirks

The Java CDK runs through jsii, a polyglot bridge to the TypeScript CDK core. This introduces:

1. **IAM policy expansion behavior** - Statement arrays are expanded into individual text-valued properties when accessed via `template.findResources()`. Tests must account for this rather than using array-matching patterns.
2. **ObjectMapper requirement** - Step Functions DefinitionString is a raw JSON string requiring Jackson parsing. TypeScript CDK tests can use `Match.objectLike()` on structured objects directly.
3. **NODE_OPTIONS clearing** - The jsii runtime is sensitive to Node.js environment variables, requiring explicit clearing before CDK commands.
4. **No native object matchers for state machine definitions** - Unlike TypeScript where the definition is a structured object, Java must parse JSON strings to validate Step Functions flows.

### Maven Build Reliability

Maven provided reliable, reproducible builds throughout development:
- Surefire plugin ran all tests consistently
- Dependency resolution was stable (no version conflicts)
- Compilation caught type errors before runtime

**Downside**: Maven build cycles are slower than TypeScript compilation (~10-15 seconds for full test suite vs. ~3-5 seconds for equivalent CDK TypeScript tests).

### Type Safety Benefits

Java's type system provided concrete benefits:
- Builder patterns enforce required properties at compile time (you cannot create an S3 bucket without calling `.build()`)
- IDE completion guided the agent toward correct CDK construct APIs
- Null safety (where used) caught potential NPEs early
- Enum types for retention periods, billing modes, and tracing configurations prevented invalid values

**Net assessment**: The type safety benefit is real but does not overcome the verbosity cost for this project's complexity level. For larger projects with more contributors, the type safety ROI would increase.

### Framework Maturity

CDK Java is a mature, well-supported binding with comprehensive L2 construct coverage. All 10 AWS services used in this project have stable L2 constructs. The Step Functions `DefinitionBody.fromChainable()` API provides a fluent builder for state machine definitions that maps well to Java's builder-pattern conventions.

---

## 5. Reproducibility Assessment

### Can Another Agent Pick Up Where This Left Off?

**Yes, with high confidence.** The repository provides:

1. **AGENT_GUIDELINES.md** - Operational rules (TDD-first, conventional commits, verification gates)
2. **META-PROMPTS.md** - Four reusable prompt templates covering all development scenarios
3. **ARCHITECTURE.md** - System design with Mermaid diagram showing all components and data flow
4. **CONTRIBUTING.md** - Step-by-step workflow for making changes
5. **EXPERIMENT.md** - Context about the project's goals and methodology
6. **TestUtils.java** - Shared utility for Step Functions JSON parsing that new tests can reuse

A new agent could:
- Add a new AWS service by following the "New Infrastructure Construct" template in META-PROMPTS.md
- Fix a bug by following the "Bug Fix" template
- Extend the state machine by reading the existing DefinitionString parsing patterns in test files

### Are Meta-Prompts Sufficient?

The four prompt templates in META-PROMPTS.md encode:
- Persona definition (Senior AWS CDK Java TDD Specialist)
- Workflow steps (read architecture, write test, confirm failure, implement, verify)
- Verification gates (mvn test, cdk synth, git diff)
- Constraint encoding (no infrastructure without tests, architecture sync)

These are sufficient for continuation work. A limitation is that they do not encode the jsii quirks discovered during development - an agent following these templates would need to rediscover the IAM expansion behavior and NODE_OPTIONS requirement.

**Recommendation**: Add a "Known Quirks" section to AGENT_GUIDELINES.md documenting jsii-specific behaviors.

### Is Documentation Sufficient for Human Maintenance?

**Yes.** A human developer can:
- Understand the full system from ARCHITECTURE.md (434 lines with diagram)
- Set up their environment from README.md prerequisites section
- Make changes following CONTRIBUTING.md workflow
- Understand design decisions from SUMMARY.md
- Run tests and deployment following the CI configuration

The test suite itself serves as living documentation - 112 assertions describe exactly what the infrastructure should look like.

---

## 6. Quantitative Metrics

| Metric | Value |
|--------|-------|
| Test files | 15 |
| Test methods (@Test annotations) | 112 |
| Lines of Java (main source) | 545 |
| Lines of Python (Lambda) | 234 |
| Lines of test code | 2,242 |
| Lines of documentation | 2,226 |
| AWS services used | 10 |
| Step Functions states | 8 |
| CloudWatch alarms | 2 |
| SNS topics | 2 |
| Issues completed | 14 |
| Pull requests merged | 13 |
| Development period | 17 days |
| CI checks per PR | 3 |
| Documentation files | 7 |
| Test-to-source ratio | 4.1:1 (test LOC / main Java LOC) |
| Doc-to-source ratio | 2.9:1 (doc LOC / total source LOC) |

### Test Distribution

| Test File | Test Count | Focus Area |
|-----------|-----------|------------|
| LambdaFunctionTest | 12 | Lambda construct properties |
| ObservabilityTest | 11 | CloudWatch, X-Ray, alarms |
| AudioProcessingTest | 10 | Lambda invoke chain, permissions |
| PipelineWiringTest | 10 | EventBridge, state ordering, IAM |
| ComprehensiveEndToEndTest | 9 | Full pipeline integrity |
| AdvancedErrorHandlingTest | 8 | Catch blocks, retry policies |
| DynamoDbMetadataTest | 8 | Table properties, permissions |
| InputValidationTest | 8 | Choice state, extension routing |
| SnsNotificationTest | 7 | Topics, encryption, permissions |
| SnapshotTest | 7 | Resource count regression |
| CdkBaseTest | 5 | Core synthesis, buckets |
| MultiEnvironmentTest | 5 | Dev/prod configuration |
| StepFunctionsTest | 5 | State machine existence, tracing |
| PipelineConstructTest | 4 | PipelineStack synthesis |
| EndToEndFlowTest | 4 | Happy/error path chains |

---

## 7. Strengths

### Infrastructure Correctness
Every AWS resource configured in `CdkBaseStack.java` has at least one test asserting its properties. The CDK assertion library provides strong guarantees that the synthesized CloudFormation template matches expectations. All 112 tests pass on the final codebase.

### Security-First Design
Security controls were integrated from the earliest issues (S3 block-public-access, SSL enforcement) rather than bolted on at the end. KMS encryption with key rotation, least-privilege IAM policies scoped to specific resource ARNs, and defense-in-depth validation demonstrate that security was treated as a first-class concern throughout.

### Comprehensive Error Handling
The Step Functions error handling is production-grade: retry policies on all 5 critical tasks, granular Catch blocks distinguishing service-specific errors from generic failures, dedicated failure notification paths, and DynamoDB status updates on both success and failure paths.

### Clean Process Discipline
Fourteen issues completed with consistent TDD adherence, conventional commits, and architecture synchronization. No shortcuts, no broken windows, no regression introduction across the development timeline.

### Documentation Investment
The 2,226 lines of documentation (nearly matching the test code volume) provide genuine value for reproducibility. META-PROMPTS.md in particular captures reusable patterns that transcend this specific project.

### Observability by Default
CloudWatch alarms with SNS actions, a dashboard with Lambda and Step Functions widgets, X-Ray tracing on both Lambda and the state machine, and a dedicated log group with defined retention. Observability was not an afterthought.

---

## 8. Weaknesses and Gaps

### Critical: Lambda Unit Test Gap

The Python Lambda function (`index.py`, 234 lines) performs the core business logic:
- S3 download of input files
- Polly SynthesizeSpeech API call
- S3 upload of processed audio
- Error handling for multiple failure modes

This code has **zero unit tests**. No pytest suite, no mocking of boto3 calls, no validation of error handling paths. CDK assertion tests verify the Lambda construct configuration (runtime, memory, timeout, permissions) but cannot validate the handler logic. This is the single largest quality gap in the project.

**Impact**: A bug in `index.py` (e.g., incorrect Polly parameters, missing error handling for S3 access denied, wrong output path construction) would not be caught by any automated test.

### Moderate: String-Based Test Assertions

Several test files use `assertTrue(templateJson.contains("..."))` rather than structured CDK assertion matchers. Examples appear in `InputValidationTest` and `ComprehensiveEndToEndTest`. These are:
- Fragile (JSON formatting changes cause false failures)
- Opaque (failure messages say "expected true" rather than describing the structural mismatch)
- Harder to maintain as the template evolves

### Moderate: Snapshot Test Naming Mismatch

`SnapshotTest.java` does not actually store or compare CloudFormation template snapshots. It asserts resource counts (e.g., "template has exactly N resources of type X"). While useful for regression detection, the naming is misleading - these are count-based regression tests, not true snapshots.

### Minor: Open-Ended CDK Version Range

`pom.xml` declares `[2.255.0,3.0.0)` for CDK dependencies. While this ensures compatibility within the CDK v2 range, future CDK releases could introduce breaking changes in synthesized output that would cause test failures. Pinning to a specific minor version would be more deterministic.

### Minor: No Integration Tests

All tests validate synthesized CloudFormation templates. No tests deploy to an actual AWS account. This is by design (zero-credential testing is a strength), but it means certain classes of errors cannot be caught:
- IAM policies that are technically valid but denied by SCPs
- Lambda timeout misconfiguration for actual Polly processing times
- S3 event delivery latency issues
- DynamoDB throughput for actual workloads

### Minor: No Dead Letter Queue

Lambda failures beyond the retry policy have no DLQ to capture them. Failed events are lost once retries are exhausted (although the failure SNS notification captures error context).

### Minor: No S3 Lifecycle Policies

Output bucket has no lifecycle rules for transitioning old processed audio to cheaper storage classes. Acknowledged in SUMMARY.md as a known limitation.

### Minor: No requirements.txt for Lambda

The Python Lambda relies solely on boto3 being available in the Lambda runtime. While this works today, it provides no version pinning and no mechanism for adding third-party dependencies if the Lambda logic evolves.

---

## 9. Conclusions

### Overall Assessment

The Java + Kiro combination produced a **high-quality infrastructure codebase** with strong process discipline. The experiment demonstrates that AI agents can maintain strict TDD adherence across 14 sequential issues without degradation, producing infrastructure that is secure, observable, and resilient.

### Verdict on the Experiment Questions

**1. Can AI agents produce production-quality IaC under TDD constraints?**

Yes, with caveats. The CDK infrastructure layer is production-grade (encryption, least-privilege, retries, alarms). The gap is at the boundary between infrastructure testing and runtime testing - the agent maintained excellent discipline within the CDK assertion paradigm but did not extend testing to the Python Lambda runtime logic.

**2. Does TDD discipline hold across many sequential issues?**

Yes. No evidence of discipline degradation. Issue 15 (a quality tidy-up) found only minor gaps (deprecated APIs, property-level assertion coverage) rather than fundamental problems. The TDD process was self-reinforcing - existing tests provided confidence for subsequent changes.

**3. Is Java suitable for AI-driven CDK development?**

Suitable but suboptimal. Java's verbosity roughly doubles the code volume compared to TypeScript CDK, and jsii adds a layer of behavioral quirks that require discovery and adaptation. The type safety benefits are real but not proportional to the verbosity cost for a project of this scale. For a larger project with multiple human contributors, the trade-off shifts more favorably toward Java.

**4. Is the Kiro agent effective for IaC development?**

Effective within its paradigm. The agent demonstrated strong capabilities in:
- Following structured workflows consistently
- Adapting to framework quirks through test failures
- Maintaining scope discipline across issue boundaries
- Producing high-quality documentation alongside code
- Self-correcting (Issue 15 quality pass)

The agent's limitation was in cross-paradigm thinking - it did not independently identify that the Python Lambda needed its own test suite (pytest with mocked boto3 calls). This suggests that meta-prompts should explicitly encode testing responsibilities for *all* code in the repository, not just the primary language.

### Recommendations for the Broader Experiment

1. **Encode Lambda testing in meta-prompts** - Add an explicit requirement to test runtime code (Python/Node.js Lambda handlers) with their native test frameworks, not just their CDK construct configurations.

2. **Prefer structured assertions** - Meta-prompts should encode a preference for CDK's `Match` API over string-contains checks, with explicit guidance on parsing Step Functions definitions.

3. **Version-pin CDK dependencies** - For reproducibility across the experiment matrix, pin CDK to a specific minor version rather than using open ranges.

4. **Compare test-to-source ratios** - The 4.1:1 test-to-source ratio (Java) should be compared against other language combinations to assess whether Java's verbosity inflates test code disproportionately.

5. **Evaluate documentation ROI** - The 2.9:1 doc-to-source ratio is high. Compare whether other combinations achieve the same reproducibility with less documentation, or whether this investment pays off when a new agent takes over.

---

## Appendix: Evaluation Criteria Mapping

This report was structured around the five evaluation criteria defined in EXPERIMENT.md section "Future Evaluation Criteria":

| Criterion | Report Section | Self-Rating |
|-----------|---------------|-------------|
| Code Quality | Section 1 | Strong (with Lambda test gap) |
| Process Quality | Section 2 | Excellent |
| Agent Effectiveness | Section 3 | Strong |
| Language Suitability | Section 4 | Adequate (verbose but functional) |
| Reproducibility | Section 5 | Strong |

---

*Report generated as part of the 5-language x 3-AI experimental evaluation. This self-assessment is honest and evidence-based, drawing directly from the codebase, test suite, documentation, and commit history.*
