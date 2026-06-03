package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.EventPattern;
import software.amazon.awscdk.services.events.RuleTargetInput;
import software.amazon.awscdk.services.events.targets.SfnStateMachine;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.stepfunctions.DefinitionBody;
import software.amazon.awscdk.services.stepfunctions.LogLevel;
import software.amazon.awscdk.services.stepfunctions.LogOptions;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.tasks.CallAwsService;

import java.util.List;
import java.util.Map;

public class CdkBaseStack extends Stack {
    public CdkBaseStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CdkBaseStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // S3 Input Bucket - receives sleep audio files
        Bucket inputBucket = Bucket.Builder.create(this, "SleepAudioInputBucket")
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(true)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .enforceSsl(true)
                .eventBridgeEnabled(true)
                .build();

        // S3 Output Bucket - stores processed audio results
        Bucket outputBucket = Bucket.Builder.create(this, "SleepAudioOutputBucket")
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(true)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .enforceSsl(true)
                .build();

        // CloudWatch LogGroup for Step Functions state machine logs
        LogGroup stateMachineLogGroup = LogGroup.Builder.create(this, "SleepAudioPipelineLogGroup")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // Polly SynthesizeSpeech task using CallAwsService
        CallAwsService pollyTask = CallAwsService.Builder.create(this, "SynthesizeSpeech")
                .service("polly")
                .action("synthesizeSpeech")
                .parameters(Map.of(
                        "OutputFormat", "mp3",
                        "Text", "placeholder text for sleep audio",
                        "VoiceId", "Joanna"
                ))
                .iamResources(List.of("*"))
                .resultPath("$.pollyResult")
                .build();

        // Step Functions State Machine with Polly task and logging
        StateMachine stateMachine = StateMachine.Builder.create(this, "SleepAudioPipelineStateMachine")
                .definitionBody(DefinitionBody.fromChainable(pollyTask))
                .logs(LogOptions.builder()
                        .destination(stateMachineLogGroup)
                        .level(LogLevel.ALL)
                        .build())
                .build();

        // EventBridge Rule - triggers on S3 Object Created events from the input bucket
        Rule rule = Rule.Builder.create(this, "SleepAudioInputRule")
                .eventPattern(EventPattern.builder()
                        .source(List.of("aws.s3"))
                        .detailType(List.of("Object Created"))
                        .detail(Map.of(
                                "bucket", Map.of(
                                        "name", List.of(inputBucket.getBucketName())
                                )
                        ))
                        .build())
                .enabled(true)
                .targets(List.of(SfnStateMachine.Builder.create(stateMachine)
                        .input(RuleTargetInput.fromEventPath("$.detail"))
                        .build()))
                .build();
    }
}
