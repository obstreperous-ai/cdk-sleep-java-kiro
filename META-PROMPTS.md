# Meta-Prompting Patterns for AI-Driven TDD Infrastructure

This document captures the reusable meta-prompting patterns used to build this project entirely through AI-agent-driven development. These patterns guide AI agents through strict Test-Driven Development for Infrastructure as Code (IaC), ensuring quality, consistency, and architectural integrity at every step.

---

## Table of Contents

- [Introduction](#introduction)
- [Core Principles](#core-principles)
- [Reusable Prompt Templates](#reusable-prompt-templates)
  - [New Infrastructure Construct](#new-infrastructure-construct)
  - [Bug Fix](#bug-fix)
  - [Refactor](#refactor)
  - [Documentation Update](#documentation-update)
- [Agent Configuration](#agent-configuration)
- [Project-Specific Patterns](#project-specific-patterns)
- [Workflow Patterns](#workflow-patterns)
- [Lessons Learned](#lessons-learned)

---

## Introduction

Meta-prompts are structured instructions that define how an AI agent should approach a development task. Rather than providing one-off instructions, meta-prompts encode repeatable workflows, quality gates, and domain-specific constraints that ensure consistent, high-quality output across many tasks.

In this project, meta-prompts drive an **agentic TDD IaC development cycle** where:

1. Every infrastructure change starts with a failing test
2. The architecture document is the single source of truth
3. Verification gates prevent broken code from being committed
4. Conventional commits maintain a clean, navigable history

These patterns are portable. They can be adapted for AWS CDK (any language), Terraform, Pulumi, CloudFormation, or any infrastructure-as-code framework that supports programmatic testing.

---

## Core Principles

### 1. TDD-First

> Write a failing test before writing any implementation code. The test defines the expected behavior. Only then write the minimum code to make it pass.

This principle eliminates speculative infrastructure. Every resource exists because a test demanded it. The test suite becomes living documentation of what the system should look like.

**In practice:**
- CDK assertion tests validate synthesized CloudFormation templates
- Tests run without AWS credentials or deployed resources
- A new construct is never added without a corresponding `hasResourceProperties()` or equivalent assertion

### 2. Architecture as Source of Truth

> ARCHITECTURE.md is the authoritative design document. Implementation follows architecture, never the reverse. If the design needs to change, update the architecture first.

This prevents architectural drift. The architecture document is always current because the workflow enforces synchronization.

**In practice:**
- Before implementing a feature, the agent reads ARCHITECTURE.md to understand the target state
- After implementing a feature, the agent updates the Mermaid diagram if the structure changed
- Conflicts between code and architecture are resolved in favor of the architecture (or the architecture is updated via a separate `docs:` commit first)

### 3. Conventional Commits

> Every commit follows the format `type: short description` where type is one of: feat, fix, chore, docs, refactor.

This produces a machine-readable, human-scannable commit history that can generate changelogs and identify the nature of changes at a glance.

**Types:**

| Type | Purpose | Example |
|------|---------|---------|
| `feat` | New feature or infrastructure construct | `feat: add S3 input bucket with event notifications` |
| `fix` | Bug fix | `fix: correct EventBridge rule event pattern` |
| `chore` | Maintenance, dependencies, tooling | `chore: update CDK dependency version range` |
| `docs` | Documentation-only changes | `docs: update ARCHITECTURE.md Mermaid diagram` |
| `refactor` | Code restructuring without behavior change | `refactor: extract bucket configuration to shared method` |

### 4. Defense-in-Depth Testing

> Validate at multiple levels. Resource existence, resource properties, cross-resource wiring, state machine flow, and end-to-end pipeline integrity.

A single assertion that a resource exists is necessary but insufficient. The test suite must also verify properties, relationships, and behavioral correctness.

**Test layers in this project:**
- Unit: Individual resource properties (encryption mode, billing mode, runtime)
- Integration: Cross-resource wiring (EventBridge targets Step Functions, Lambda has S3 permissions)
- Flow: State machine ordering, Catch routing, retry configuration
- End-to-End: Resource counts, IAM policy correctness, notification payloads
- Regression: Full template snapshots for drift detection

---

## Reusable Prompt Templates

### New Infrastructure Construct

Use this template when adding a new AWS resource to the CDK stack.

```markdown
## Persona

You are a Senior AWS CDK Java TDD Specialist. You write tests before
implementation. You maintain perfect sync of ARCHITECTURE.md. You use
conventional commits.

## Context

- Project: AWS CDK Java stack for sleep audio processing pipeline
- Stack: CdkBaseStack (single stack, all resources)
- Test framework: JUnit 5 + CDK assertions (Template.fromStack)
- Architecture doc: ARCHITECTURE.md (source of truth)

## Task

Add [RESOURCE DESCRIPTION] to the CdkBaseStack.

## Requirements

[List specific configuration requirements: encryption, permissions,
properties, relationships to existing resources]

## Workflow

1. Read ARCHITECTURE.md to understand how this resource fits the design
2. Write a failing CDK assertion test in a new or existing test file:
   - Test resource existence with hasResourceProperties()
   - Test key properties (encryption, billing mode, etc.)
   - Test relationships to other resources if applicable
3. Run `mvn test` and confirm the new test fails for the expected reason
4. Implement the minimum CDK code in CdkBaseStack.java to pass the test
5. Run `mvn test` and confirm ALL tests pass (not just the new one)
6. Run `NODE_OPTIONS="" npx cdk synth` to verify synthesis succeeds
7. Update ARCHITECTURE.md Mermaid diagram to include the new resource
8. Commit with: `feat: add [resource description]`

## Verification Gates

- [ ] New test written and initially failing
- [ ] `mvn test` passes (all tests, not just new ones)
- [ ] `NODE_OPTIONS="" npx cdk synth` succeeds
- [ ] ARCHITECTURE.md updated with new resource
- [ ] Commit uses conventional format
```

### Bug Fix

Use this template when fixing a defect in existing infrastructure or tests.

```markdown
## Persona

You are a Senior AWS CDK Java TDD Specialist. You diagnose issues
methodically and fix them with minimal, targeted changes.

## Context

- Project: AWS CDK Java stack for sleep audio processing pipeline
- Existing test suite: 15 files, 100+ assertions
- All tests should pass before and after the fix

## Task

Fix: [DESCRIPTION OF THE BUG OR INCORRECT BEHAVIOR]

## Workflow

1. Reproduce the issue by running `mvn test` or identifying the failing assertion
2. Read the relevant test file and implementation code
3. Identify the root cause (not just the symptom)
4. If a test is missing for this scenario, write one first (TDD)
5. Apply the minimum fix to resolve the issue
6. Run `mvn test` and confirm ALL tests pass
7. Run `NODE_OPTIONS="" npx cdk synth` to verify synthesis still succeeds
8. Commit with: `fix: [concise description of what was fixed]`

## Verification Gates

- [ ] Root cause identified and documented in commit message
- [ ] Fix is minimal and targeted (no scope creep)
- [ ] `mvn test` passes (all tests)
- [ ] `NODE_OPTIONS="" npx cdk synth` succeeds
- [ ] No unrelated files modified
```

### Refactor

Use this template when restructuring code without changing behavior.

```markdown
## Persona

You are a Senior AWS CDK Java TDD Specialist. You improve code structure
while preserving all existing behavior, verified by the test suite.

## Context

- Project: AWS CDK Java stack for sleep audio processing pipeline
- Existing test suite serves as the behavioral specification
- All tests must continue to pass without modification

## Task

Refactor: [DESCRIPTION OF THE STRUCTURAL IMPROVEMENT]

## Workflow

1. Run `mvn test` to establish baseline (all tests must pass)
2. Read the code to be refactored and understand its current structure
3. Plan the refactoring (extract method, rename, reorganize)
4. Apply the changes incrementally
5. Run `mvn test` after each step to ensure no regressions
6. Run `NODE_OPTIONS="" npx cdk synth` to verify synthesis still succeeds
7. Run `npx cdk synth` and compare output to ensure CloudFormation is unchanged
8. Commit with: `refactor: [concise description of structural change]`

## Constraints

- No new behavior (no new resources, no changed properties)
- All existing tests must pass WITHOUT modification
- If a test needs to change, this is not a pure refactor
- CloudFormation output should be identical before and after

## Verification Gates

- [ ] All tests pass without modification
- [ ] `NODE_OPTIONS="" npx cdk synth` produces identical output
- [ ] No new resources or changed properties in the template
- [ ] Commit uses `refactor:` prefix
```

### Documentation Update

Use this template when updating documentation files.

```markdown
## Persona

You are a Senior AWS CDK Java TDD Specialist. You maintain documentation
that accurately reflects the current state of the system.

## Context

- Project: AWS CDK Java stack for sleep audio processing pipeline
- Key docs: ARCHITECTURE.md, CONTRIBUTING.md, SUMMARY.md, README.md
- ARCHITECTURE.md Mermaid diagram must match deployed infrastructure

## Task

Update documentation: [DESCRIPTION OF WHAT NEEDS UPDATING]

## Workflow

1. Read the current state of the documentation to be updated
2. Read the implementation code to understand the current system state
3. Update the documentation to reflect reality
4. If updating ARCHITECTURE.md, ensure the Mermaid diagram is accurate
5. Verify all internal markdown links resolve correctly
6. Run `mvn test` to confirm no code was accidentally modified
7. Commit with: `docs: [concise description of documentation change]`

## Constraints

- Documentation must describe what IS, not what SHOULD BE
- Internal links must resolve (relative paths between docs)
- Do not modify any code files in a docs-only commit
- Mermaid diagrams must be valid and render correctly

## Verification Gates

- [ ] Documentation accurately reflects current implementation
- [ ] All internal links resolve correctly
- [ ] Mermaid diagrams render without errors
- [ ] No code files modified
- [ ] Commit uses `docs:` prefix
```

---

## Agent Configuration

These meta-prompts can be adapted for different AI agents and tools. The key elements to configure are:

### Persona Definition

The persona establishes the agent's identity and priorities:

```
You are a Senior AWS CDK Java TDD Specialist. Be explicit and verbose.
Tests before implementation. Maintain perfect sync of ARCHITECTURE.md
Mermaid diagram.
```

**Adapt by changing:**
- The technology stack (CDK Java -> Terraform HCL, Pulumi TypeScript, etc.)
- The seniority and verbosity level
- The priority ordering (TDD-first vs. architecture-first vs. speed-first)

### Verification Gates

Verification gates are non-negotiable checkpoints. The agent must not proceed past a gate until the condition is satisfied.

**This project's gates:**
1. `mvn test` - All CDK assertion tests pass
2. `NODE_OPTIONS="" npx cdk synth` - CloudFormation synthesizes without errors
3. `git diff` - Only intended files are modified

**Adapt for other projects:**
- Terraform: `terraform validate` + `terraform plan` + `tflint`
- Pulumi: `pulumi preview` + language-specific tests
- CloudFormation: `cfn-lint` + `taskcat`

### Constraint Encoding

Constraints prevent common failure modes. Encode them explicitly:

```
- Never add infrastructure without a corresponding test
- Never modify ARCHITECTURE.md without also updating the Mermaid diagram
- Never use `git add .` or `git add -A` (stage specific files by name)
- Always clear NODE_OPTIONS before running CDK commands
```

---

## Project-Specific Patterns

### CDK Java Testing with Template.fromStack

The standard pattern for CDK assertion tests in this project:

```java
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

@Test
public void testResourceExists() {
    // Arrange: stack is created in @BeforeEach
    Template template = Template.fromStack(stack);

    // Assert: verify resource with specific properties
    template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
        "BucketEncryption", Match.objectLike(Map.of(
            "ServerSideEncryptionConfiguration", Match.anyValue()
        )),
        "PublicAccessBlockConfiguration", Match.objectLike(Map.of(
            "BlockPublicAcls", true,
            "BlockPublicPolicy", true
        ))
    )));
}
```

### Step Functions Flow Validation with ObjectMapper

State machine flow is validated by parsing the DefinitionString JSON:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Test
public void testStateOrdering() {
    Template template = Template.fromStack(stack);
    Map<String, Map<String, Object>> stateMachines =
        template.findResources("AWS::StepFunctions::StateMachine");

    // Extract and parse the DefinitionString
    String definitionString = /* extract from template */;
    ObjectMapper mapper = new ObjectMapper();
    JsonNode definition = mapper.readTree(definitionString);

    // Navigate the state machine definition
    JsonNode states = definition.get("States");
    String startState = definition.get("StartAt").asText();
    // Assert state transitions, Catch blocks, retry configs...
}
```

### jsii IAM Policy Statement Quirk

CDK Java jsii bindings expand IAM policy statement arrays into individual text-valued statements when accessed via `template.findResources()`. Tests checking IAM actions must verify individual textual Action values rather than using array matching:

```java
// WRONG - jsii expands arrays into individual statements
template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
    "PolicyDocument", Match.objectLike(Map.of(
        "Statement", Match.arrayWith(List.of(
            Match.objectLike(Map.of("Action", List.of("s3:GetObject", "s3:PutObject")))
        ))
    ))
)));

// CORRECT - check for individual action strings in the expanded form
// Use findResources() and manually inspect the statement structure
Map<String, Map<String, Object>> policies = template.findResources("AWS::IAM::Policy");
// Then iterate and check individual Action values
```

### CDK Context for Multi-Environment Testing

```java
@Test
public void testEnvironmentTagging() {
    // Create app with specific context
    App app = new App(AppProps.builder()
        .context(Map.of("environment", "prod"))
        .build());
    CdkBaseStack stack = new CdkBaseStack(app, "TestStack");
    Template template = Template.fromStack(stack);

    // Verify environment tag is applied
    template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
        "Tags", Match.arrayWith(List.of(
            Match.objectLike(Map.of("Key", "Environment", "Value", "prod"))
        ))
    )));
}
```

---

## Workflow Patterns

### Issue-to-PR Cycle

Every feature in this project followed the same cycle:

```
1. GitHub Issue Created
   - Clear title and description
   - Acceptance criteria defined
   - Technical requirements reference ARCHITECTURE.md

2. AI Agent Picks Up Issue
   - Reads ARCHITECTURE.md for context
   - Reads existing test files for patterns
   - Plans the implementation approach

3. TDD Implementation
   - Writes failing test(s) first
   - Implements minimum code to pass
   - Runs full test suite for regression detection
   - Runs CDK synth for synthesis validation

4. Documentation Sync
   - Updates ARCHITECTURE.md if structure changed
   - Updates CONTRIBUTING.md if workflow changed
   - Updates SUMMARY.md with key decisions

5. Pull Request
   - Conventional commit message
   - All tests passing
   - CDK synth succeeding for both dev and prod
   - Architecture diagram current
```

### Red-Green-Refactor for Infrastructure

The classic TDD cycle adapted for CDK:

```
RED:    Write a CDK assertion test for the desired infrastructure.
        Run `mvn test`. The test fails because the resource does not exist.

GREEN:  Add the minimum CDK construct to CdkBaseStack.java.
        Run `mvn test`. All tests pass.

REFACTOR: Clean up the implementation (extract methods, improve naming).
          Run `mvn test` again. Still green.
          Run `NODE_OPTIONS="" npx cdk synth`. Template is valid.
```

### Verification Checklist (Every Commit)

```bash
# 1. All tests pass
mvn test

# 2. CloudFormation synthesizes correctly
NODE_OPTIONS="" npx cdk synth

# 3. Only intended files are modified
git diff --name-only

# 4. Commit with conventional format
git commit -m "feat: add [description]"
```

---

## Lessons Learned

### What Makes TDD Effective for IaC

1. **CDK assertions test the output, not the code** - You are testing the synthesized CloudFormation template, which is what actually gets deployed. This means you are testing the real artifact.

2. **Tests run in milliseconds** - Unlike integration tests that deploy to AWS, CDK assertion tests synthesize locally and run in under a second. This enables true red-green-refactor cycles.

3. **Tests serve as documentation** - Each test file describes what the infrastructure should look like. New team members can read tests to understand the system.

4. **Refactoring is safe** - With 100+ assertions, you can restructure CDK code confidently. If the CloudFormation output changes unexpectedly, a test will catch it.

### What Makes Agentic Development Effective for IaC

1. **Strict constraints produce better output** - The more specific the meta-prompt constraints (TDD-first, architecture sync, conventional commits), the more consistent the AI output.

2. **Verification gates are essential** - Without mandatory `mvn test` + `cdk synth` gates, agents may produce code that compiles but does not synthesize correctly.

3. **Architecture-as-source-of-truth prevents drift** - When the agent must consult ARCHITECTURE.md before implementing, it produces infrastructure that matches the design rather than improvising.

4. **Issue-driven scope prevents creep** - Clear acceptance criteria in issues keep the agent focused. Without them, agents tend to over-engineer or add unrequested features.

5. **Persona definition matters** - "Senior AWS CDK Java TDD Specialist" produces meaningfully different output than a generic developer persona. The specificity channels the agent's behavior.

### CDK Java Specific Insights

1. **jsii is an abstraction layer** - CDK Java runs on jsii, which bridges Java to the underlying TypeScript CDK. This introduces occasional behavioral differences (like IAM policy array expansion) that require awareness.

2. **NODE_OPTIONS interference** - Corporate environments often set `NODE_OPTIONS` for proxy bootstrap modules. This conflicts with jsii's Node.js runtime. Always clear it: `NODE_OPTIONS="" npx cdk synth`.

3. **ObjectMapper for Step Functions** - There is no first-class CDK assertion API for Step Functions flow. Parsing the DefinitionString JSON with Jackson ObjectMapper is the practical approach.

4. **Template snapshots are brittle** - Full template snapshots break on CDK version updates even when no code changes. Use them for regression detection but expect maintenance overhead.

5. **Single stack simplifies testing** - With all resources in one stack, a single `Template.fromStack(stack)` call gives you the full template to assert against. Multi-stack architectures require more complex test setup.

---

## Adapting These Patterns

To use these meta-prompting patterns in your own project:

1. **Define your persona** - Match the technology stack, seniority level, and priorities
2. **Set your verification gates** - What commands must pass before a commit is allowed?
3. **Choose your source of truth** - Which document describes the target architecture?
4. **Encode your constraints** - What are the non-negotiable rules for your project?
5. **Create templates for each workflow type** - New feature, bug fix, refactor, docs update
6. **Document project-specific quirks** - Every framework has edge cases; write them down so agents do not rediscover them painfully

The goal is to encode your team's collective wisdom into structured prompts that produce consistent, high-quality infrastructure code regardless of which agent or developer executes them.
