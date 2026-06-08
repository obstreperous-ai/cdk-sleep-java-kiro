package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.pipelines.CodePipeline;
import software.amazon.awscdk.pipelines.CodePipelineSource;
import software.amazon.awscdk.pipelines.ShellStep;

import java.util.List;

public class PipelineStack extends Stack {
    public PipelineStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public PipelineStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        CodePipeline pipeline = CodePipeline.Builder.create(this, "SleepAudioPipeline")
                .pipelineName("SleepAudioPipeline")
                .synth(ShellStep.Builder.create("Synth")
                        .input(CodePipelineSource.connection("owner/repo", "main",
                                software.amazon.awscdk.pipelines.ConnectionSourceOptions.builder()
                                        .connectionArn("arn:aws:codestar-connections:us-east-1:123456789012:connection/placeholder-connection-id")
                                        .build()))
                        .commands(List.of("mvn compile", "npx cdk synth"))
                        .build())
                .build();
    }
}
