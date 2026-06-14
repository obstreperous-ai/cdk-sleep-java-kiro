package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    public void testCloudWatchAlarmsHaveAlarmActions() {
        // Verify StateMachine alarm has at least one alarm action
        Map<String, Map<String, Object>> alarms = template.findResources("AWS::CloudWatch::Alarm",
            Match.objectLike(Map.of(
                "Properties", Match.objectLike(Map.of(
                    "MetricName", "ExecutionsFailed"
                ))
            )));
        assertTrue(alarms.size() >= 1, "Should have at least one ExecutionsFailed alarm");
        for (Map<String, Object> alarm : alarms.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) alarm.get("Properties");
            assertNotNull(props.get("AlarmActions"), "ExecutionsFailed alarm should have AlarmActions");
            assertTrue(props.get("AlarmActions") instanceof java.util.List,
                "AlarmActions should be a list");
            @SuppressWarnings("unchecked")
            java.util.List<Object> actions = (java.util.List<Object>) props.get("AlarmActions");
            assertTrue(actions.size() >= 1,
                "ExecutionsFailed alarm should have at least one alarm action");
        }
    }

    @Test
    public void testLambdaAlarmHasAlarmActions() {
        // Verify Lambda alarm has at least one alarm action
        Map<String, Map<String, Object>> alarms = template.findResources("AWS::CloudWatch::Alarm",
            Match.objectLike(Map.of(
                "Properties", Match.objectLike(Map.of(
                    "MetricName", "Errors"
                ))
            )));
        assertTrue(alarms.size() >= 1, "Should have at least one Errors alarm");
        for (Map<String, Object> alarm : alarms.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) alarm.get("Properties");
            assertNotNull(props.get("AlarmActions"), "Errors alarm should have AlarmActions");
            assertTrue(props.get("AlarmActions") instanceof java.util.List,
                "AlarmActions should be a list");
            @SuppressWarnings("unchecked")
            java.util.List<Object> actions = (java.util.List<Object>) props.get("AlarmActions");
            assertTrue(actions.size() >= 1,
                "Errors alarm should have at least one alarm action");
        }
    }

    @Test
    public void testLogGroupHasOneWeekRetention() {
        template.hasResourceProperties("AWS::Logs::LogGroup", Match.objectLike(Map.of(
            "RetentionInDays", 7
        )));
    }
}
