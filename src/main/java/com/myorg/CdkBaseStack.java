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
import software.amazon.awscdk.services.stepfunctions.tasks.SnsPublish;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.TableEncryption;
import software.amazon.awscdk.services.stepfunctions.JsonPath;
import software.amazon.awscdk.services.stepfunctions.Chain;
import software.amazon.awscdk.services.stepfunctions.Choice;
import software.amazon.awscdk.services.stepfunctions.Condition;
import software.amazon.awscdk.services.stepfunctions.Pass;
import software.amazon.awscdk.services.stepfunctions.Result;
import software.amazon.awscdk.services.stepfunctions.RetryProps;
import software.amazon.awscdk.services.stepfunctions.CatchProps;
import software.amazon.awscdk.services.stepfunctions.TaskInput;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;

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

        // Lambda Function - SleepAudioProcessor
        software.amazon.awscdk.services.lambda.Function audioProcessorFunction =
                software.amazon.awscdk.services.lambda.Function.Builder.create(this, "SleepAudioProcessor")
                        .runtime(Runtime.PYTHON_3_12)
                        .handler("index.handler")
                        .code(Code.fromAsset("src/main/resources/lambda/audio-processor"))
                        .environment(Map.of(
                                "TABLE_NAME", metadataTable.getTableName()
                        ))
                        .build();

        // Grant Lambda read access to DynamoDB metadata table
        metadataTable.grantReadData(audioProcessorFunction);

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
                .errors(List.of("DynamoDB.ProvisionedThroughputExceededException", "DynamoDB.InternalServerError", "States.Timeout"))
                .maxAttempts(3)
                .backoffRate(2.0)
                .build());

        // LambdaInvoke task - processes audio metadata between PutItem and Polly
        LambdaInvoke lambdaInvokeTask = LambdaInvoke.Builder.create(this, "ProcessAudioMetadata")
                .lambdaFunction(audioProcessorFunction)
                .payloadResponseOnly(true)
                .resultPath("$.lambdaResult")
                .build();

        lambdaInvokeTask.addRetry(RetryProps.builder()
                .errors(List.of("Lambda.ServiceException", "Lambda.TooManyRequestsException", "States.Timeout"))
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
                .errors(List.of("DynamoDB.ProvisionedThroughputExceededException", "DynamoDB.InternalServerError", "States.Timeout"))
                .maxAttempts(3)
                .backoffRate(2.0)
                .build());

        // KMS Key for SNS topic encryption
        Key snsEncryptionKey = Key.Builder.create(this, "SnsEncryptionKey")
                .description("KMS key for SNS topic encryption")
                .enableKeyRotation(true)
                .build();

        // SNS Topic - Pipeline Completed notifications
        Topic completedTopic = Topic.Builder.create(this, "SleepAudioPipelineCompleted")
                .masterKey(snsEncryptionKey)
                .build();

        // SNS Topic - Pipeline Failed notifications
        Topic failedTopic = Topic.Builder.create(this, "SleepAudioPipelineFailed")
                .masterKey(snsEncryptionKey)
                .build();

        // SnsPublish task - publishes success notification after UpdateItem(COMPLETED)
        SnsPublish successPublishTask = SnsPublish.Builder.create(this, "PublishSuccessNotification")
                .topic(completedTopic)
                .message(TaskInput.fromObject(Map.of(
                        "audioId", JsonPath.stringAt("$.object.key"),
                        "status", "COMPLETED"
                )))
                .resultPath("$.snsResult")
                .build();

        successPublishTask.addRetry(RetryProps.builder()
                .errors(List.of("States.TaskFailed", "States.Timeout"))
                .maxAttempts(3)
                .backoffRate(2.0)
                .build());

        // DynamoDB UpdateItem task for failure path - sets status=FAILED with error info
        CallAwsService failureUpdateTask = CallAwsService.Builder.create(this, "UpdateMetadataStatusFailed")
                .service("dynamodb")
                .action("updateItem")
                .parameters(Map.of(
                        "TableName", metadataTable.getTableName(),
                        "Key", Map.of(
                                "audioId", Map.of("S", JsonPath.stringAt("$.object.key"))
                        ),
                        "UpdateExpression", "SET #s = :status, #u = :updatedAt, #e = :errorInfo",
                        "ExpressionAttributeNames", Map.of(
                                "#s", "status",
                                "#u", "updatedAt",
                                "#e", "errorInfo"
                        ),
                        "ExpressionAttributeValues", Map.of(
                                ":status", Map.of("S", "FAILED"),
                                ":updatedAt", Map.of("S", JsonPath.stringAt("$$.State.EnteredTime")),
                                ":errorInfo", Map.of("S", JsonPath.stringAt("$.error.Cause"))
                        )
                ))
                .iamResources(List.of(metadataTable.getTableArn()))
                .resultPath("$.failureUpdateResult")
                .build();

        failureUpdateTask.addRetry(RetryProps.builder()
                .errors(List.of("DynamoDB.ProvisionedThroughputExceededException", "DynamoDB.InternalServerError", "States.Timeout"))
                .maxAttempts(3)
                .backoffRate(2.0)
                .build());

        // SnsPublish task - publishes failure notification
        SnsPublish failurePublishTask = SnsPublish.Builder.create(this, "PublishFailureNotification")
                .topic(failedTopic)
                .message(TaskInput.fromObject(Map.of(
                        "audioId", JsonPath.stringAt("$.object.key"),
                        "status", "FAILED",
                        "error", JsonPath.stringAt("$.error.Error"),
                        "cause", JsonPath.stringAt("$.error.Cause")
                )))
                .resultPath("$.snsFailureResult")
                .build();

        failurePublishTask.addRetry(RetryProps.builder()
                .errors(List.of("States.TaskFailed", "States.Timeout"))
                .maxAttempts(3)
                .backoffRate(2.0)
                .build());

        // Wire failure path: UpdateItem(FAILED) -> SnsPublish(Failed)
        failureUpdateTask.next(failurePublishTask);

        // Chain: PutItem -> Lambda -> Polly -> UpdateItem(COMPLETED) -> SnsPublish(Completed)
        // with Catch on lambdaInvokeTask, pollyTask, updateItemTask, and successPublishTask routing to failure path
        lambdaInvokeTask.addCatch(failureUpdateTask, CatchProps.builder()
                .errors(List.of("States.ALL"))
                .resultPath("$.error")
                .build());

        pollyTask.addCatch(failureUpdateTask, CatchProps.builder()
                .errors(List.of("States.ALL"))
                .resultPath("$.error")
                .build());

        updateItemTask.addCatch(failureUpdateTask, CatchProps.builder()
                .errors(List.of("States.ALL"))
                .resultPath("$.error")
                .build());

        successPublishTask.addCatch(failureUpdateTask, CatchProps.builder()
                .errors(List.of("States.ALL"))
                .resultPath("$.error")
                .build());

        // Input validation Choice state - validates file extension before processing
        Choice validateFileExtension = Choice.Builder.create(this, "ValidateFileExtension")
                .build();

        // Valid extensions route to ProcessAudioMetadata
        Condition isWav = Condition.stringMatches("$.object.key", "*.wav");
        Condition isMp3 = Condition.stringMatches("$.object.key", "*.mp3");
        Condition isOgg = Condition.stringMatches("$.object.key", "*.ogg");

        // Pass state injects synthetic error context for the validation failure path.
        // The failure tasks reference $.error.Error and $.error.Cause which are normally
        // populated by Catch blocks. Without this, the Choice otherwise path would fail
        // at runtime because those fields would not exist in the state input.
        Pass validationErrorState = Pass.Builder.create(this, "ValidationError")
                .result(Result.fromObject(Map.of(
                        "Error", "ValidationError",
                        "Cause", "Unsupported file extension"
                )))
                .resultPath("$.error")
                .build();

        // Wire: ValidationError Pass -> UpdateMetadataStatusFailed -> PublishFailureNotification
        validationErrorState.next(failureUpdateTask);

        // Processing chain: Lambda -> Polly -> UpdateItem(COMPLETED) -> SnsPublish(Completed)
        Chain processingChain = Chain.start(lambdaInvokeTask)
                .next(pollyTask)
                .next(updateItemTask)
                .next(successPublishTask);

        validateFileExtension
                .when(isWav, processingChain)
                .when(isMp3, processingChain)
                .when(isOgg, processingChain)
                .otherwise(validationErrorState);

        // Main chain: PutItem -> ValidateFileExtension (Choice)
        Chain chain = Chain.start(putItemTask)
                .next(validateFileExtension);

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

        // Grant SNS publish permissions to the state machine
        completedTopic.grantPublish(stateMachine);
        failedTopic.grantPublish(stateMachine);

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
