/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;
import static io.camunda.optimize.service.export.CSVUtils.extractAllPrefixedCountKeys;
import static io.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import com.google.common.collect.Sets;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizationType;
import io.camunda.optimize.dto.optimize.rest.ProcessRawDataCsvExportRequestDto;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import io.camunda.optimize.service.entities.EntityExportService;
import io.camunda.optimize.service.export.CsvExportService;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(REST_API_PATH + "/export")
public class ExportRestService {
  private final CsvExportService csvExportService;
  private final EntityExportService entityExportService;
  private final SessionService sessionService;
  private final AbstractIdentityService identityService;

  public ExportRestService(
      final CsvExportService csvExportService,
      final EntityExportService entityExportService,
      final SessionService sessionService,
      final AbstractIdentityService identityService) {
    this.csvExportService = csvExportService;
    this.entityExportService = entityExportService;
    this.sessionService = sessionService;
    this.identityService = identityService;
  }

  @GetMapping("report/json/{reportId}/{fileName}")
  public Response getJsonReport(
      @PathVariable("reportId") final String reportId,
      @PathVariable("fileName") final String fileName,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);

    final List<ReportDefinitionExportDto> jsonReports =
        entityExportService.getReportExportDtosAsUser(userId, Sets.newHashSet(reportId));

    return createJsonResponse(fileName, jsonReports);
  }

  @GetMapping("dashboard/json/{dashboardId}/{fileName}")
  public Response getJsonDashboard(
      @PathVariable("dashboardId") final String dashboardId,
      @PathVariable("fileName") final String fileName,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);

    final List<OptimizeEntityExportDto> jsonDashboards =
        entityExportService.getCompleteDashboardExportAsUser(userId, dashboardId);

    return createJsonResponse(fileName, jsonDashboards);
  }

  @GetMapping("csv/{reportId}/{fileName}")
  // Produces octet stream on success, json on potential error
  public Response getCsvReport(
      @PathVariable("reportId") final String reportId,
      @PathVariable("fileName") final String fileName,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    validateAuthorization();
    final ZoneId timezone = extractTimezone(request);

    final Optional<byte[]> csvForReport =
        csvExportService.getCsvBytesForEvaluatedReportResult(userId, reportId, timezone);

    return csvForReport
        .map(csvBytes -> createOctetStreamResponse(fileName, csvBytes))
        .orElse(Response.status(Response.Status.NOT_FOUND).build());
  }

  /**
   * This endpoint returns only the columns specified in the includedColumns list in the request.
   * All other columns (dto fields, new and existing variables not in includedColumns) are to be
   * excluded. It is used for example to return process instance Ids in the branch analysis export.
   */
  // Produces octet stream on success, json on potential error
  @PostMapping("csv/process/rawData/{fileName}")
  public Response getRawDataCsv(
      @PathVariable("fileName") final String fileName,
      @Valid @RequestBody final ProcessRawDataCsvExportRequestDto request,
      final HttpServletRequest servletRequest) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(servletRequest);
    validateAuthorization();
    final ZoneId timezone = extractTimezone(servletRequest);

    final SingleProcessReportDefinitionRequestDto reportDefinitionDto =
        SingleProcessReportDefinitionRequestDto.builder()
            .reportType(ReportType.PROCESS)
            .combined(false)
            .data(
                ProcessReportDataDto.builder()
                    .definitions(
                        List.of(
                            new ReportDataDefinitionDto(
                                request.getProcessDefinitionKey(),
                                request.getProcessDefinitionVersions(),
                                request.getTenantIds())))
                    .filter(request.getFilter())
                    .configuration(
                        SingleReportConfigurationDto.builder()
                            .tableColumns(
                                TableColumnDto.builder()
                                    .includeNewVariables(false)
                                    .excludedColumns(getAllExcludedFields(request))
                                    .includedColumns(request.getIncludedColumns())
                                    .build())
                            .build())
                    .view(new ProcessViewDto(ViewProperty.RAW_DATA))
                    .groupBy(new NoneGroupByDto())
                    .visualization(ProcessVisualization.TABLE)
                    .build())
            .build();

    return createOctetStreamResponse(
        fileName,
        csvExportService.getCsvBytesForEvaluatedReportResult(
            userId, reportDefinitionDto, timezone));
  }

  private void validateAuthorization() {
    if (!identityService.getEnabledAuthorizations().contains(AuthorizationType.CSV_EXPORT)) {
      throw new ForbiddenException("User not authorized to export CSVs");
    }
  }

  private List<String> getAllExcludedFields(final ProcessRawDataCsvExportRequestDto request) {
    final List<String> excludedFields =
        Stream.concat(
                extractAllProcessInstanceDtoFieldKeys().stream(),
                extractAllPrefixedCountKeys().stream())
            .collect(Collectors.toList());
    excludedFields.removeAll(request.getIncludedColumns());
    return excludedFields;
  }

  private Response createOctetStreamResponse(
      final String fileName, final byte[] csvBytesForEvaluatedReportResult) {
    return Response.ok(csvBytesForEvaluatedReportResult, MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=" + createFileName(fileName, ".csv"))
        .build();
  }

  private Response createJsonResponse(
      final String fileName, final List<? extends OptimizeEntityExportDto> jsonEntities) {
    return Response.ok(jsonEntities, MediaType.APPLICATION_JSON)
        .header("Content-Disposition", "attachment; filename=" + createFileName(fileName, ".json"))
        .build();
  }

  private String createFileName(final String fileName, final String extension) {
    return fileName == null ? System.currentTimeMillis() + extension : fileName;
  }
}
