/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import static io.camunda.tasklist.util.CollectionUtil.toArrayOfStrings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.DateUtil;
import io.camunda.tasklist.views.TaskSearchView;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public final class TaskDTO {

  private String id;
  private String processInstanceId;

  /** Field is used to resolve task name. */
  private String flowNodeBpmnId;

  private String flowNodeInstanceId;

  /** Field is used to resolve process name. */
  private String processDefinitionId;

  /** Fallback value for process name. */
  private String bpmnProcessId;

  private String creationTime;
  private String completionTime;
  private String assignee;
  private String[] candidateGroups;
  private String[] candidateUsers;
  private TaskState taskState;
  private String[] sortValues;
  private boolean isFirst = false;
  private String formKey;
  private String formId;
  private Long formVersion;
  private Boolean isFormEmbedded;
  private String tenantId;
  private OffsetDateTime dueDate;
  private OffsetDateTime followUpDate;
  private VariableDTO[] variables;
  private TaskImplementation implementation;

  public String getId() {
    return id;
  }

  public TaskDTO setId(String id) {
    this.id = id;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskDTO setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskDTO setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskDTO setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public TaskDTO setFlowNodeBpmnId(String flowNodeBpmnId) {
    this.flowNodeBpmnId = flowNodeBpmnId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public TaskDTO setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskDTO setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskDTO setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getCreationTime() {
    return creationTime;
  }

  public TaskDTO setCreationTime(String creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public String getCompletionTime() {
    return completionTime;
  }

  public TaskDTO setCompletionTime(String completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public TaskState getTaskState() {
    return taskState;
  }

  public TaskDTO setTaskState(TaskState taskState) {
    this.taskState = taskState;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public TaskDTO setSortValues(final String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public boolean getIsFirst() {
    return isFirst;
  }

  public TaskDTO setIsFirst(final boolean first) {
    isFirst = first;
    return this;
  }

  public String[] candidateUsers() {
    return candidateUsers;
  }

  public TaskDTO setCandidateUsers(String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskDTO setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskDTO setFormId(String formId) {
    this.formId = formId;
    return this;
  }

  public Long getFormVersion() {
    return formVersion;
  }

  public TaskDTO setFormVersion(Long formVersion) {
    this.formVersion = formVersion;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public TaskDTO setIsFormEmbedded(Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public TaskDTO setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskDTO setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskDTO setFollowUpDate(OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public VariableDTO[] getVariables() {
    return variables;
  }

  public TaskDTO setVariables(VariableDTO[] variables) {
    this.variables = variables;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskDTO setImplementation(TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public static TaskDTO createFrom(TaskEntity taskEntity, ObjectMapper objectMapper) {
    return createFrom(taskEntity, null, objectMapper);
  }

  public static TaskDTO createFrom(
      TaskEntity taskEntity, Object[] sortValues, ObjectMapper objectMapper) {
    final TaskDTO taskDTO =
        new TaskDTO()
            .setCreationTime(objectMapper.convertValue(taskEntity.getCreationTime(), String.class))
            .setCompletionTime(
                objectMapper.convertValue(taskEntity.getCompletionTime(), String.class))
            .setId(taskEntity.getId())
            .setProcessInstanceId(taskEntity.getProcessInstanceId())
            .setTaskState(taskEntity.getState())
            .setAssignee(taskEntity.getAssignee())
            .setBpmnProcessId(taskEntity.getBpmnProcessId())
            .setProcessDefinitionId(taskEntity.getProcessDefinitionId())
            .setFlowNodeBpmnId(taskEntity.getFlowNodeBpmnId())
            .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
            .setFormKey(taskEntity.getFormKey())
            .setFormId(taskEntity.getFormId())
            .setFormVersion(taskEntity.getFormVersion())
            .setIsFormEmbedded(taskEntity.getIsFormEmbedded())
            .setTenantId(taskEntity.getTenantId())
            .setFollowUpDate(taskEntity.getFollowUpDate())
            .setDueDate(taskEntity.getDueDate())
            .setCandidateGroups(taskEntity.getCandidateGroups())
            .setCandidateUsers(taskEntity.getCandidateUsers())
            .setImplementation(taskEntity.getImplementation());
    if (sortValues != null) {
      taskDTO.setSortValues(toArrayOfStrings(sortValues));
    }
    return taskDTO;
  }

  public static TaskDTO createFrom(
      TaskSearchView taskSearchView, VariableDTO[] variables, ObjectMapper objectMapper) {
    return new TaskDTO()
        .setCreationTime(objectMapper.convertValue(taskSearchView.getCreationTime(), String.class))
        .setCompletionTime(
            objectMapper.convertValue(taskSearchView.getCompletionTime(), String.class))
        .setId(taskSearchView.getId())
        .setProcessInstanceId(taskSearchView.getProcessInstanceId())
        .setTaskState(taskSearchView.getState())
        .setAssignee(taskSearchView.getAssignee())
        .setBpmnProcessId(taskSearchView.getBpmnProcessId())
        .setProcessDefinitionId(taskSearchView.getProcessDefinitionId())
        .setFlowNodeBpmnId(taskSearchView.getFlowNodeBpmnId())
        .setFlowNodeInstanceId(taskSearchView.getFlowNodeInstanceId())
        .setFormKey(taskSearchView.getFormKey())
        .setFormId(taskSearchView.getFormId())
        .setFormVersion(taskSearchView.getFormVersion())
        .setIsFormEmbedded(taskSearchView.getIsFormEmbedded())
        .setTenantId(taskSearchView.getTenantId())
        .setFollowUpDate(taskSearchView.getFollowUpDate())
        .setDueDate(taskSearchView.getDueDate())
        .setCandidateGroups(taskSearchView.getCandidateGroups())
        .setCandidateUsers(taskSearchView.getCandidateUsers())
        .setSortValues(taskSearchView.getSortValues())
        .setIsFirst(taskSearchView.isFirst())
        .setVariables(variables)
        .setImplementation(taskSearchView.getImplementation());
  }

  public static TaskEntity toTaskEntity(TaskDTO taskDTO) {

    final TaskEntity taskEntity;
    try {
      taskEntity =
          new TaskEntity()
              .setCreationTime(
                  DateUtil.toOffsetDateTime(
                      DateUtil.SIMPLE_DATE_FORMAT.parse(taskDTO.getCreationTime()).toInstant()))
              .setId(taskDTO.getId())
              .setProcessInstanceId(taskDTO.getProcessInstanceId())
              .setState(taskDTO.getTaskState())
              .setAssignee(taskDTO.getAssignee())
              .setBpmnProcessId(taskDTO.getBpmnProcessId())
              .setProcessDefinitionId(taskDTO.getProcessDefinitionId())
              .setFlowNodeBpmnId(taskDTO.getFlowNodeBpmnId())
              .setFlowNodeInstanceId(taskDTO.getFlowNodeInstanceId())
              .setFormKey(taskDTO.getFormKey())
              .setFormId(taskDTO.getFormId())
              .setFormVersion(taskDTO.getFormVersion())
              .setIsFormEmbedded(taskDTO.getIsFormEmbedded())
              .setTenantId(taskDTO.getTenantId())
              .setFollowUpDate(taskDTO.getFollowUpDate())
              .setDueDate(taskDTO.getDueDate())
              .setCandidateGroups(taskDTO.getCandidateGroups())
              .setCandidateUsers(taskDTO.getCandidateUsers())
              .setImplementation(taskDTO.getImplementation());

      if (taskDTO.getCompletionTime() != null) {
        taskEntity.setCompletionTime(
            DateUtil.toOffsetDateTime(
                DateUtil.SIMPLE_DATE_FORMAT.parse(taskDTO.getCompletionTime()).toInstant()));
      }
    } catch (ParseException e) {
      throw new TasklistRuntimeException(e);
    }

    return taskEntity;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TaskDTO taskDTO = (TaskDTO) o;
    return isFirst == taskDTO.isFirst
        && implementation == taskDTO.implementation
        && Objects.equals(id, taskDTO.id)
        && Objects.equals(processInstanceId, taskDTO.processInstanceId)
        && Objects.equals(flowNodeBpmnId, taskDTO.flowNodeBpmnId)
        && Objects.equals(flowNodeInstanceId, taskDTO.flowNodeInstanceId)
        && Objects.equals(processDefinitionId, taskDTO.processDefinitionId)
        && Objects.equals(bpmnProcessId, taskDTO.bpmnProcessId)
        && Objects.equals(creationTime, taskDTO.creationTime)
        && Objects.equals(completionTime, taskDTO.completionTime)
        && Objects.equals(assignee, taskDTO.assignee)
        && Arrays.equals(candidateGroups, taskDTO.candidateGroups)
        && Arrays.equals(candidateUsers, taskDTO.candidateUsers)
        && taskState == taskDTO.taskState
        && Arrays.equals(sortValues, taskDTO.sortValues)
        && Objects.equals(formKey, taskDTO.formKey)
        && Objects.equals(formId, taskDTO.formId)
        && Objects.equals(formVersion, taskDTO.formVersion)
        && Objects.equals(isFormEmbedded, taskDTO.isFormEmbedded)
        && Objects.equals(tenantId, taskDTO.tenantId)
        && Objects.equals(dueDate, taskDTO.dueDate)
        && Objects.equals(followUpDate, taskDTO.followUpDate)
        && Arrays.equals(variables, taskDTO.variables);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            processInstanceId,
            flowNodeBpmnId,
            flowNodeInstanceId,
            processDefinitionId,
            bpmnProcessId,
            creationTime,
            completionTime,
            assignee,
            taskState,
            isFirst,
            formKey,
            formId,
            formVersion,
            isFormEmbedded,
            tenantId,
            dueDate,
            followUpDate,
            implementation);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    result = 31 * result + Arrays.hashCode(sortValues);
    result = 31 * result + Arrays.hashCode(variables);
    return result;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskDTO.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("processInstanceId='" + processInstanceId + "'")
        .add("flowNodeBpmnId='" + flowNodeBpmnId + "'")
        .add("flowNodeInstanceId='" + flowNodeInstanceId + "'")
        .add("processDefinitionId='" + processDefinitionId + "'")
        .add("bpmnProcessId='" + bpmnProcessId + "'")
        .add("creationTime='" + creationTime + "'")
        .add("completionTime='" + completionTime + "'")
        .add("assignee='" + assignee + "'")
        .add("candidateGroups=" + Arrays.toString(candidateGroups))
        .add("candidateUsers=" + Arrays.toString(candidateUsers))
        .add("taskState=" + taskState)
        .add("sortValues=" + Arrays.toString(sortValues))
        .add("isFirst=" + isFirst)
        .add("formKey='" + formKey + "'")
        .add("formId='" + formId + "'")
        .add("formVersion='" + formVersion + "'")
        .add("isFormEmbedded='" + isFormEmbedded + "'")
        .add("tenantId='" + tenantId + "'")
        .add("dueDate=" + dueDate)
        .add("followUpDate=" + followUpDate)
        .add("variables=" + Arrays.toString(variables))
        .add("implementation=" + implementation)
        .toString();
  }
}
