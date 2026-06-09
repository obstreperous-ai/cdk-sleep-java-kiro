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

        Object connectionArnObj = this.getNode().tryGetContext("pipeline:connectionArn");
        if (!(connectionArnObj instanceof String) || ((String) connectionArnObj).isEmpty()) {
            throw new IllegalArgumentException(
                "Context value 'pipeline:connectionArn' is required. " +
                "Set it via -c pipeline:connectionArn=arn:aws:codestar-connections:...");
        }
        String connectionArn = (String) connectionArnObj;

        Object repositoryObj = this.getNode().tryGetContext("pipeline:repository");
        if (!(repositoryObj instanceof String) || ((String) repositoryObj).isEmpty()) {
            throw new IllegalArgumentException(
                "Context value 'pipeline:repository' is required. " +
                "Set it via -c pipeline:repository=owner/repo");
        }
        String repository = (String) repositoryObj;

        CodePipeline pipeline = CodePipeline.Builder.create(this, "SleepAudioPipeline")
                .pipelineName("SleepAudioPipeline")
                .synth(ShellStep.Builder.create("Synth")
                        .input(CodePipelineSource.connection(repository, "main",
                                software.amazon.awscdk.pipelines.ConnectionSourceOptions.builder()
                                        .connectionArn(connectionArn)
                                        .build()))
                        .commands(List.of("mvn compile", "npx cdk synth"))
                        .build())
                .build();
    }
}
