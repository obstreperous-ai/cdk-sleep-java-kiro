package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

public class ObservabilityTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testLambdaHasXRayTracingEnabled() {
        template.hasResourceProperties("AWS::Lambda::Function", Match.objectLike(Map.of(
            "Runtime", "python3.12",
            "TracingConfig", Match.objectLike(Map.of(
                "Mode", "Active"
            ))
        )));
    }

    @Test
    public void testStateMachineHasXRayTracingEnabled() {
        template.hasResourceProperties("AWS::StepFunctions::StateMachine", Match.objectLike(Map.of(
            "TracingConfiguration", Match.objectLike(Map.of(
                "Enabled", true
            ))
        )));
    }

    @Test
    public void testCloudWatchAlarmForStateMachineFailures() {
        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
            "MetricName", "ExecutionsFailed",
            "Threshold", 1,
            "EvaluationPeriods", 1,
            "ComparisonOperator", "GreaterThanOrEqualToThreshold"
        )));
    }

    @Test
    public void testCloudWatchAlarmForLambdaErrors() {
        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
            "MetricName", "Errors",
            "Threshold", 1,
            "EvaluationPeriods", 1,
            "ComparisonOperator", "GreaterThanOrEqualToThreshold"
        )));
    }

    @Test
    public void testCloudWatchAlarmCount() {
        int count = template.findResources("AWS::CloudWatch::Alarm").size();
        assertTrue(count >= 2,
            "Stack should have at least 2 CloudWatch Alarms (state machine failures and Lambda errors)");
    }

    @Test
    public void testCloudWatchDashboardExists() {
        int count = template.findResources("AWS::CloudWatch::Dashboard").size();
        assertTrue(count >= 1,
            "Stack should have at least 1 CloudWatch Dashboard");
    }

    @Test
    public void testStateMachineAlarmHasCorrectPeriod() {
        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
            "MetricName", "ExecutionsFailed",
            "Period", 300
        )));
    }

    @Test
    public void testLambdaAlarmHasCorrectPeriod() {
        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
            "MetricName", "Errors",
            "Period", 300
        )));
    }
}
