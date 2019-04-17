/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.es.report.command.decision.RawDecisionDataCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByEvaluationDateTimeCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByInputVariableCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByMatchedRuleCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.decision.frequency.CountDecisionFrequencyGroupByOutputVariableCommand;
import org.camunda.optimize.service.es.report.command.process.RawProcessDataCommand;
import org.camunda.optimize.service.es.report.command.process.flownode.duration.FlowNodeDurationByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.process.flownode.frequency.CountFlowNodeFrequencyByFlowNodeCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.ProcessInstanceDurationGroupByStartDateCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.ProcessInstanceDurationGroupByStartDateWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.ProcessInstanceDurationGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.ProcessInstanceDurationGroupByNoneWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.ProcessInstanceDurationByVariableCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.ProcessInstanceDurationGroupByVariableWithProcessPartCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.frequency.CountProcessInstanceFrequencyByStartDateCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.frequency.CountProcessInstanceFrequencyByVariableCommand;
import org.camunda.optimize.service.es.report.command.process.processinstance.frequency.CountProcessInstanceFrequencyGroupByNoneCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.UserTaskIdleDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.UserTaskTotalDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.process.user_task.duration.UserTaskWorkDurationByUserTaskCommand;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByEvaluationDateTimeReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByInputVariableReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByMatchedRuleReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createCountFrequencyGroupByOutputVariableReport;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionReportDataCreator.createRawDecisionDataReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountFlowNodeFrequencyGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createCountProcessInstanceFrequencyGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createFlowNodeDurationGroupByFlowNodeReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByNoneReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByNoneWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByVariableReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createProcessInstanceDurationGroupByVariableWithProcessPartReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createRawDataReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createUserTaskIdleDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createUserTaskTotalDurationGroupByUserTaskReport;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessReportDataCreator.createUserTaskWorkDurationGroupByUserTaskReport;

@Component
public class SingleReportEvaluator {

  private static Map<String, Supplier<? extends Command>> commandSuppliers = new HashMap<>();

  static {
    // process reports
    commandSuppliers.put(createRawDataReport().createCommandKey(), RawProcessDataCommand::new);

    addCountProcessInstanceFrequencyReports();
    addCountFlowNodeFrequencyReports();

    addProcessInstanceDurationReports();
    addFlowNodeDurationReports();
    addUserTaskDurationReports();

    // decision reports
    commandSuppliers.put(createRawDecisionDataReport().createCommandKey(), RawDecisionDataCommand::new);

    addDecisionCountFrequencyReports();
  }

  protected final ConfigurationService configurationService;
  protected final ObjectMapper objectMapper;
  protected final ProcessQueryFilterEnhancer processQueryFilterEnhancer;
  protected final DecisionQueryFilterEnhancer decisionQueryFilterEnhancer;
  protected final RestHighLevelClient esClient;
  protected final IntervalAggregationService intervalAggregationService;
  protected final ProcessDefinitionReader processDefinitionReader;

  @Autowired
  public SingleReportEvaluator(final ConfigurationService configurationService,
                               final ObjectMapper objectMapper,
                               final ProcessQueryFilterEnhancer processQueryFilterEnhancer,
                               final DecisionQueryFilterEnhancer decisionQueryFilterEnhancer,
                               final RestHighLevelClient esClient,
                               final IntervalAggregationService intervalAggregationService,
                               final ProcessDefinitionReader processDefinitionReader) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.processQueryFilterEnhancer = processQueryFilterEnhancer;
    this.decisionQueryFilterEnhancer = decisionQueryFilterEnhancer;
    this.esClient = esClient;
    this.intervalAggregationService = intervalAggregationService;
    this.processDefinitionReader = processDefinitionReader;
  }

  private static void addCountProcessInstanceFrequencyReports() {
    commandSuppliers.put(
      createCountProcessInstanceFrequencyGroupByNoneReport().createCommandKey(),
      CountProcessInstanceFrequencyGroupByNoneCommand::new
    );
    commandSuppliers.put(
      createCountProcessInstanceFrequencyGroupByStartDateReport().createCommandKey(),
      CountProcessInstanceFrequencyByStartDateCommand::new
    );
    commandSuppliers.put(
      createCountProcessInstanceFrequencyGroupByVariableReport().createCommandKey(),
      CountProcessInstanceFrequencyByVariableCommand::new
    );
  }

  private static void addCountFlowNodeFrequencyReports() {
    commandSuppliers.put(
      createCountFlowNodeFrequencyGroupByFlowNodeReport().createCommandKey(),
      CountFlowNodeFrequencyByFlowNodeCommand::new
    );
  }

  private static void addFlowNodeDurationReports() {
    commandSuppliers.put(
      createFlowNodeDurationGroupByFlowNodeReport().createCommandKey(),
      FlowNodeDurationByFlowNodeCommand::new
    );
  }

  private static void addUserTaskDurationReports() {
    commandSuppliers.put(
      createUserTaskIdleDurationGroupByUserTaskReport().createCommandKey(),
      UserTaskIdleDurationByUserTaskCommand::new
    );
    commandSuppliers.put(
      createUserTaskTotalDurationGroupByUserTaskReport().createCommandKey(),
      UserTaskTotalDurationByUserTaskCommand::new
    );
    commandSuppliers.put(
      createUserTaskWorkDurationGroupByUserTaskReport().createCommandKey(),
      UserTaskWorkDurationByUserTaskCommand::new
    );
  }

  private static void addProcessInstanceDurationReports() {
    commandSuppliers.put(
      createProcessInstanceDurationGroupByNoneReport().createCommandKey(),
      ProcessInstanceDurationGroupByNoneCommand::new
    );
    commandSuppliers.put(
      createProcessInstanceDurationGroupByNoneWithProcessPartReport().createCommandKey(),
      ProcessInstanceDurationGroupByNoneWithProcessPartCommand::new
    );
    commandSuppliers.put(
      createProcessInstanceDurationGroupByStartDateReport().createCommandKey(),
      ProcessInstanceDurationGroupByStartDateCommand::new
    );
    commandSuppliers.put(
      createProcessInstanceDurationGroupByStartDateWithProcessPartReport().createCommandKey(),
      ProcessInstanceDurationGroupByStartDateWithProcessPartCommand::new
    );
    commandSuppliers.put(
      createProcessInstanceDurationGroupByVariableReport().createCommandKey(),
      ProcessInstanceDurationByVariableCommand::new
    );
    commandSuppliers.put(
      createProcessInstanceDurationGroupByVariableWithProcessPartReport().createCommandKey(),
      ProcessInstanceDurationGroupByVariableWithProcessPartCommand::new
    );
  }

  private static void addDecisionCountFrequencyReports() {
    commandSuppliers.put(
      createCountFrequencyGroupByNoneReport().createCommandKey(),
      CountDecisionFrequencyGroupByNoneCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByEvaluationDateTimeReport().createCommandKey(),
      CountDecisionFrequencyGroupByEvaluationDateTimeCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByInputVariableReport().createCommandKey(),
      CountDecisionFrequencyGroupByInputVariableCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByOutputVariableReport().createCommandKey(),
      CountDecisionFrequencyGroupByOutputVariableCommand::new
    );
    commandSuppliers.put(
      createCountFrequencyGroupByMatchedRuleReport().createCommandKey(),
      CountDecisionFrequencyGroupByMatchedRuleCommand::new
    );
  }

  <T extends ReportDefinitionDto> ReportEvaluationResult<?, T> evaluate(T reportDefinition) throws OptimizeException {
    CommandContext<T> commandContext = createCommandContext(reportDefinition);
    Command<T> evaluationCommand = extractCommandWithValidation(reportDefinition);
    return evaluationCommand.evaluate(commandContext);
  }

  protected <T extends ReportDefinitionDto> CommandContext<T> createCommandContext(T reportDefinition) {
    CommandContext<T> commandContext = new CommandContext<>();
    commandContext.setConfigurationService(configurationService);
    commandContext.setEsClient(esClient);
    commandContext.setObjectMapper(objectMapper);
    commandContext.setIntervalAggregationService(intervalAggregationService);
    if (reportDefinition instanceof SingleProcessReportDefinitionDto) {
      commandContext.setQueryFilterEnhancer(processQueryFilterEnhancer);
    } else if (reportDefinition instanceof SingleDecisionReportDefinitionDto) {
      commandContext.setQueryFilterEnhancer(decisionQueryFilterEnhancer);
    }
    commandContext.setProcessDefinitionReader(processDefinitionReader);
    commandContext.setReportDefinition(reportDefinition);
    return commandContext;
  }

  private <T extends ReportDefinitionDto> Command<T> extractCommandWithValidation(T reportDefinition) {
    ValidationHelper.validate(reportDefinition.getData());
    return extractCommand(reportDefinition);
  }

  @SuppressWarnings(value = "unchecked")
  <T extends ReportDefinitionDto> Command<T> extractCommand(T reportDefinition) {
    return commandSuppliers.getOrDefault(reportDefinition.getData().createCommandKey(), NotSupportedCommand::new).get();
  }
}
