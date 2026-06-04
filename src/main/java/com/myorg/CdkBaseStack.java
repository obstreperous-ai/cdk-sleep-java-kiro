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
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.TableEncryption;
import software.amazon.awscdk.services.stepfunctions.JsonPath;
import software.amazon.awscdk.services.stepfunctions.Chain;
import software.amazon.awscdk.services.stepfunctions.RetryProps;

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

        // DynamoDB Metadata Table - stores audio pipeline metadata
        Table metadataTable = Table.Builder.create(this, "SleepAudioMetadataTable")
                .partitionKey(Attribute.builder()
                        .name("audioId")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .encryption(TableEncryption.DEFAULT)
                .pointInTimeRecovery(true)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        // DynamoDB PutItem task - writes initial metadata record with PROCESSING status
        CallAwsService putItemTask = CallAwsService.Builder.create(this, "PutMetadataRecord")
                .service("dynamodb")
                .action("putItem")
                .parameters(Map.of(
                        "TableName", metadataTable.getTableName(),
                        "Item", Map.of(
                                "audioId", Map.of("S", JsonPath.stringAt("$.object.key")),
                                "status", Map.of("S", "PROCESSING"),
                                "inputBucket", Map.of("S", JsonPath.stringAt("$.bucket.name")),
                                "inputKey", Map.of("S", JsonPath.stringAt("$.object.key")),
                                "createdAt", Map.of("S", JsonPath.stringAt("$$.State.EnteredTime"))
                        )
                ))
                .iamResources(List.of(metadataTable.getTableArn()))
                .resultPath("$.putItemResult")
                .build();

        putItemTask.addRetry(RetryProps.builder()
                .errors(List.of("States.ALL"))
                .maxAttempts(3)
                .backoffRate(2.0)
                .build());

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

        // DynamoDB UpdateItem task - updates status to COMPLETED after Polly processing
        CallAwsService updateItemTask = CallAwsService.Builder.create(this, "UpdateMetadataStatus")
                .service("dynamodb")
                .action("updateItem")
                .parameters(Map.of(
                        "TableName", metadataTable.getTableName(),
                        "Key", Map.of(
                                "audioId", Map.of("S", JsonPath.stringAt("$.object.key"))
                        ),
                        "UpdateExpression", "SET #s = :status, #u = :updatedAt",
                        "ExpressionAttributeNames", Map.of(
                                "#s", "status",
                                "#u", "updatedAt"
                        ),
                        "ExpressionAttributeValues", Map.of(
                                ":status", Map.of("S", "COMPLETED"),
                                ":updatedAt", Map.of("S", JsonPath.stringAt("$$.State.EnteredTime"))
                        )
                ))
                .iamResources(List.of(metadataTable.getTableArn()))
                .resultPath("$.updateItemResult")
                .build();

        updateItemTask.addRetry(RetryProps.builder()
                .errors(List.of("States.ALL"))
                .maxAttempts(3)
                .backoffRate(2.0)
                .build());

        // Chain: PutItem -> Polly -> UpdateItem
        Chain chain = Chain.start(putItemTask)
                .next(pollyTask)
                .next(updateItemTask);

        // Step Functions State Machine with chained tasks and logging
        StateMachine stateMachine = StateMachine.Builder.create(this, "SleepAudioPipelineStateMachine")
                .definitionBody(DefinitionBody.fromChainable(chain))
                .logs(LogOptions.builder()
                        .destination(stateMachineLogGroup)
                        .level(LogLevel.ALL)
                        .build())
                .build();

        // Grant the state machine role permissions to read/write the DynamoDB table
        metadataTable.grantReadWriteData(stateMachine);

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
