/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.digest;

import static org.camunda.optimize.dto.optimize.query.processoverview.KpiType.QUALITY;
import static org.camunda.optimize.dto.optimize.query.processoverview.KpiType.TIME;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiResultDto;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiType;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.KpiService;
import org.camunda.optimize.service.db.reader.ProcessOverviewReader;
import org.camunda.optimize.service.db.reader.ReportReader;
import org.camunda.optimize.service.db.writer.ProcessOverviewWriter;
import org.camunda.optimize.service.email.EmailService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.util.DurationFormatterUtil;
import org.camunda.optimize.service.util.RootUrlGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.core.Tuple;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class DigestService implements ConfigurationReloadable {

  private static final String DIGEST_EMAIL_TEMPLATE = "digestEmailTemplate.ftl";
  private static final String HTTP_PREFIX = "http://";
  private static final String HTTPS_PREFIX = "https://";
  private static final String UTM_SOURCE = "digest";
  private static final String UTM_MEDIUM = "email";
  private static final String DEFAULT_LOCALE = "en";
  private final ConfigurationService configurationService;
  private final EmailService emailService;
  private final AbstractIdentityService identityService;
  private final KpiService kpiService;
  private final DefinitionService definitionService;
  private final ProcessOverviewWriter processOverviewWriter;
  private final ProcessOverviewReader processOverviewReader;
  private final ReportReader reportReader;
  private final RootUrlGenerator rootUrlGenerator;
  private final Map<String, ScheduledFuture<?>> scheduledDigestTasks = new HashMap<>();
  private ThreadPoolTaskScheduler digestTaskScheduler;

  @PostConstruct
  public void init() {
    initTaskScheduler();
    initExistingDigests();
  }

  @PreDestroy
  public void destroy() {
    if (digestTaskScheduler != null) {
      digestTaskScheduler.destroy();
      digestTaskScheduler = null;
    }
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    destroy();
    init();
  }

  public void handleDigestTask(final String processDefinitionKey) {
    log.debug("Checking for active digests on process [{}].", processDefinitionKey);
    final ProcessOverviewDto overviewDto =
        processOverviewReader
            .getProcessOverviewByKey(processDefinitionKey)
            .orElseThrow(
                () -> {
                  unscheduleDigest(processDefinitionKey);
                  return new OptimizeRuntimeException(
                      "Overview for process ["
                          + processDefinitionKey
                          + "] no longer exists. Unscheduling"
                          + " respective digest.");
                });

    if (overviewDto.getDigest().isEnabled()) {
      log.info("Creating KPI digest for process [{}].", processDefinitionKey);
      sendDigestAndUpdateLatestKpiResults(overviewDto);
    } else {
      log.info("Digest on process [{}] is disabled.", processDefinitionKey);
    }
  }

  public void handleProcessUpdate(
      final String processDefKey, final ProcessUpdateDto processUpdateDto) {
    if (processUpdateDto.getProcessDigest().isEnabled()) {
      rescheduleDigest(processDefKey, processUpdateDto.getProcessDigest());
    } else {
      unscheduleDigest(processDefKey);
    }
  }

  private void rescheduleDigest(
      final String processDefKey, final ProcessDigestRequestDto digestRequestDto) {
    unscheduleDigest(processDefKey);
    scheduleDigest(processDefKey);
    if (digestRequestDto.isEnabled()) {
      handleDigestTask(processDefKey); // if digest is enabled, send out immediate test email
    }
  }

  private void initTaskScheduler() {
    if (digestTaskScheduler == null) {
      digestTaskScheduler = new ThreadPoolTaskScheduler();
      digestTaskScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
      digestTaskScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
      digestTaskScheduler.setThreadNamePrefix("DigestTaskScheduler");
      digestTaskScheduler.initialize();
    }
  }

  private void initExistingDigests() {
    log.debug("Scheduling digest tasks for all existing enabled process digests.");
    processOverviewReader
        .getAllActiveProcessDigestsByKey()
        .forEach((processDefinitionKey, digest) -> scheduleDigest(processDefinitionKey));
  }

  private void scheduleDigest(final String processDefinitionKey) {
    scheduledDigestTasks.put(
        processDefinitionKey,
        digestTaskScheduler.schedule(
            createDigestTask(processDefinitionKey),
            new CronTrigger(configurationService.getDigestCronTrigger())));
  }

  private void unscheduleDigest(final String processDefinitionKey) {
    Optional.ofNullable(scheduledDigestTasks.remove(processDefinitionKey))
        .ifPresent(task -> task.cancel(true));
  }

  private void sendDigestAndUpdateLatestKpiResults(final ProcessOverviewDto overviewDto) {
    final List<KpiResultDto> mostRecentKpiReportResults =
        kpiService.extractMostRecentKpiResultsForCurrentKpiReportsForProcess(
            overviewDto, DEFAULT_LOCALE);

    try {
      composeAndSendDigestEmail(overviewDto, mostRecentKpiReportResults);
    } catch (final Exception e) {
      log.error("Failed to send digest email", e);
    } finally {
      // The most recent results are then set as the baseline for the digest
      updateBaselineKpiReportResults(
          overviewDto.getProcessDefinitionKey(), mostRecentKpiReportResults);
    }
  }

  private void composeAndSendDigestEmail(
      final ProcessOverviewDto overviewDto, final List<KpiResultDto> currentKpiReportResults) {
    final Optional<UserDto> processOwner = identityService.getUserById(overviewDto.getOwner());
    final String definitionName =
        definitionService
            .getLatestCachedDefinitionOnAnyTenant(
                DefinitionType.PROCESS, overviewDto.getProcessDefinitionKey())
            .map(DefinitionOptimizeResponseDto::getName)
            .orElse(overviewDto.getProcessDefinitionKey());

    emailService.sendTemplatedEmailWithErrorHandling(
        processOwner.map(UserDto::getEmail).orElse(null),
        String.format(
            "[%s - Optimize] Process Digest for Process \"%s\"",
            configurationService.getNotificationEmailCompanyBranding(), definitionName),
        DIGEST_EMAIL_TEMPLATE,
        createInputsForTemplate(
            processOwner.map(UserDto::getName).orElse(overviewDto.getOwner()),
            definitionName,
            currentKpiReportResults,
            overviewDto.getDigest().getKpiReportResults()));
  }

  private Map<String, Object> createInputsForTemplate(
      final String ownerName,
      final String processDefinitionName,
      final List<KpiResultDto> currentKpiReportResults,
      final Map<String, String> previousKpiReportResults) {
    return Map.of(
        "ownerName", ownerName,
        "processName", processDefinitionName,
        "hasTimeKpis",
            currentKpiReportResults.stream()
                .anyMatch(kpiResult -> TIME.equals(kpiResult.getType())),
        "hasQualityKpis",
            currentKpiReportResults.stream()
                .anyMatch(kpiResult -> QUALITY.equals(kpiResult.getType())),
        "successfulTimeKPIPercent", calculateSuccessfulKpiInPercent(TIME, currentKpiReportResults),
        "successfulQualityKPIPercent",
            calculateSuccessfulKpiInPercent(QUALITY, currentKpiReportResults),
        "kpiResults",
            getKpiSummaryDtos(
                processDefinitionName, currentKpiReportResults, previousKpiReportResults),
        "optimizeHomePageLink", getOptimizeHomePageLink());
  }

  private int calculateSuccessfulKpiInPercent(
      final KpiType kpiType, final List<KpiResultDto> kpiResults) {
    final long resultCount =
        kpiResults.stream().filter(kpiResult -> kpiType.equals(kpiResult.getType())).count();
    final long successfulResultCount =
        kpiResults.stream()
            .filter(kpiResult -> kpiType.equals(kpiResult.getType()))
            .filter(KpiResultDto::isTargetMet)
            .count();
    return resultCount == 0 ? 0 : (int) (100.0 * successfulResultCount / resultCount);
  }

  private void updateBaselineKpiReportResults(
      final String processDefinitionKey, final List<KpiResultDto> mostRecentKpiReportResults) {
    // We must use a Map that allows null values, so explicitly create a HashMap here
    final Map<String, String> reportIdsToValues = new HashMap<>();
    mostRecentKpiReportResults.forEach(
        result -> reportIdsToValues.put(result.getReportId(), result.getValue()));
    processOverviewWriter.updateProcessDigestResults(
        processDefinitionKey, new ProcessDigestDto(reportIdsToValues));
  }

  private DigestTask createDigestTask(final String processDefinitionKey) {
    return new DigestTask(this, processDefinitionKey);
  }

  private String getOptimizeHomePageLink() {
    return rootUrlGenerator.getRootUrl() + "/#/";
  }

  private String getReportViewLink(final String reportId, final String collectionId) {
    return rootUrlGenerator.getRootUrl() + getReportViewLinkPath(reportId, collectionId);
  }

  private String getReportViewLinkPath(final String reportId, final String collectionId) {
    return Optional.ofNullable(collectionId)
        .map(
            colId ->
                String.format(
                    "/#/collection/%s/report/%s?utm_medium=%s&utm_source=%s",
                    colId, reportId, UTM_MEDIUM, UTM_SOURCE))
        .orElse(
            String.format(
                "/#/report/%s?utm_medium=%s&utm_source=%s", reportId, UTM_MEDIUM, UTM_SOURCE));
  }

  private List<DigestTemplateKpiSummaryDto> getKpiSummaryDtos(
      final String processDefinitionName,
      final List<KpiResultDto> currentKpiReportResults,
      final Map<String, String> previousKpiReportResults) {
    return currentKpiReportResults.stream()
        .map(
            kpiResult -> {
              final Optional<ReportDefinitionDto> reportDefinition =
                  reportReader.getReport(kpiResult.getReportId());
              if (reportDefinition.isEmpty()) {
                log.error(
                    "Report [{}] could not be retrieved for creation of digest email for process [{}] because report no longer exists. "
                        + "This report will be excluded from the digest.",
                    kpiResult.getReportId(),
                    processDefinitionName);
              }
              return Tuple.tuple(reportDefinition, kpiResult);
            })
        .filter(kpiReportResultTuple -> kpiReportResultTuple.v1().isPresent())
        .map(
            kpiReportResultTuple ->
                new DigestTemplateKpiSummaryDto(
                    kpiReportResultTuple.v1().get().getName(),
                    getReportViewLink(
                        kpiReportResultTuple.v2().getReportId(),
                        kpiReportResultTuple.v1().get().getCollectionId()),
                    kpiReportResultTuple.v2(),
                    Optional.ofNullable(previousKpiReportResults)
                        .orElse(Collections.emptyMap())
                        .get(kpiReportResultTuple.v2().getReportId())))
        .toList();
  }

  public enum KpiChangeType {
    GOOD, // compared to previous report value, new value is closer to KPI target
    NEUTRAL, // no change
    BAD // compared to previous report value, new value is further away from KPI target
  }

  @Data
  public static class DigestTemplateKpiSummaryDto {
    private final String reportName;
    private final String reportLink;
    private final String kpiType;
    private final boolean targetMet;
    private final String target;
    private final String current;
    private final Double changeInPercent;
    private final KpiChangeType changeType;

    public DigestTemplateKpiSummaryDto(
        final String reportName,
        final String reportLink,
        final KpiResultDto kpiResultDto,
        @Nullable final String previousValue) {
      this.reportName = reportName;
      this.reportLink = reportLink;
      kpiType = StringUtils.capitalize(kpiResultDto.getType().getId());
      targetMet = kpiResultDto.isTargetMet();
      target =
          getKpiTargetString(
              kpiResultDto.getTarget(),
              kpiResultDto.getUnit(),
              kpiResultDto.getMeasure(),
              kpiResultDto.isBelow());
      current = getKpiValueString(kpiResultDto.getValue(), kpiResultDto.getMeasure());
      changeInPercent = getKpiChangeInPercent(kpiResultDto, previousValue);
      changeType =
          evaluateChangeType(kpiResultDto, getKpiChangeInPercent(kpiResultDto, previousValue));
    }

    /**
     * @return a string to indicate report target depending on viewProperty and isBelow, eg "< 2h"
     *     or "> 50.55 %"
     */
    private String getKpiTargetString(
        final String target,
        final TargetValueUnit unit,
        final ViewProperty kpiMeasure,
        final boolean isBelow) {
      final String targetString;
      if (ViewProperty.DURATION.equals(kpiMeasure)) {
        targetString = target + " " + StringUtils.capitalize(unit.getId());
      } else if (ViewProperty.PERCENTAGE.equals(kpiMeasure)) {
        targetString = String.format("%.2f %%", Double.parseDouble(target));
      } else {
        targetString = target;
      }
      return String.format("%s %s", isBelow ? "<" : ">", targetString);
    }

    private String getKpiValueString(final String value, final ViewProperty kpiMeasure) {
      if (Optional.ofNullable(value).isEmpty()) {
        return "NA";
      }
      if (ViewProperty.DURATION.equals(kpiMeasure)) {
        return DurationFormatterUtil.formatMilliSecondsToReadableDurationString(
            (long) Double.parseDouble(value));
      } else if (ViewProperty.PERCENTAGE.equals(kpiMeasure)) {
        return String.format("%.2f %%", Double.parseDouble(value));
      } else {
        return value;
      }
    }

    private Double getKpiChangeInPercent(
        final KpiResultDto kpiResult, @Nullable final String previousValue) {
      final double previousValueAsDouble =
          previousValue == null ? Double.NaN : Double.parseDouble(previousValue);
      return previousValue == null || previousValueAsDouble == 0.
          ? 0.
          : 100
              * ((Double.parseDouble(kpiResult.getValue()) - previousValueAsDouble)
                  / previousValueAsDouble);
    }

    private KpiChangeType evaluateChangeType(
        final KpiResultDto kpiResultDto, final Double changeInPercent) {
      if (changeInPercent == 0.) {
        return KpiChangeType.NEUTRAL;
      }
      if (kpiResultDto.isBelow()) {
        return changeInPercent < 0. ? KpiChangeType.GOOD : KpiChangeType.BAD;
      } else {
        return changeInPercent > 0. ? KpiChangeType.GOOD : KpiChangeType.BAD;
      }
    }
  }
}
