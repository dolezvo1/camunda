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
package io.camunda.operate.schema.opensearch;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.*;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component("schemaManager")
@Profile("!test")
@Conditional(OpensearchCondition.class)
public class OpensearchSchemaManager implements SchemaManager {

  public static final String SCHEMA_OPENSEARCH_CREATE_TEMPLATE_JSON =
      "/schema/opensearch/create/template/operate-%s.json";
  public static final String SCHEMA_OPENSEARCH_CREATE_INDEX_JSON =
      "/schema/opensearch/create/index/operate-%s.json";
  public static final String SCHEMA_OPENSEARCH_CREATE_POLICY_JSON =
      "/schema/opensearch/create/policy/%s.json";
  public static final String SETTINGS = "settings";
  public static final String MAPPINGS = "mappings";
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchSchemaManager.class);
  protected final OperateProperties operateProperties;

  protected final RichOpenSearchClient richOpenSearchClient;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JsonbJsonpMapper jsonpMapper = new JsonbJsonpMapper();

  private final List<TemplateDescriptor> templateDescriptors;

  private final List<IndexDescriptor> indexDescriptors;

  @Autowired
  public OpensearchSchemaManager(
      final OperateProperties operateProperties,
      final RichOpenSearchClient richOpenSearchClient,
      final List<TemplateDescriptor> templateDescriptors,
      final List<IndexDescriptor> indexDescriptors) {
    super();
    this.operateProperties = operateProperties;
    this.richOpenSearchClient = richOpenSearchClient;
    this.templateDescriptors = templateDescriptors;
    this.indexDescriptors =
        indexDescriptors.stream()
            .filter(indexDescriptor -> !(indexDescriptor instanceof TemplateDescriptor))
            .toList();
  }

  @Override
  public void createSchema() {
    if (operateProperties.getArchiver().isIlmEnabled()) {
      createIsmPolicy();
    }
    createDefaults();
    createTemplates();
    createIndices();
  }

  @Override
  public void checkAndUpdateIndices() {
    LOGGER.info("Updating Indices with currently-configured number of replicas...");
    final String currentConfigNumberOfReplicas =
        String.valueOf(operateProperties.getOpensearch().getNumberOfReplicas());
    indexDescriptors.forEach(
        indexDescriptor -> {
          final String index = indexDescriptor.getIndexName();
          final Map<String, String> indexSettings =
              getIndexSettingsFor(index, NUMBERS_OF_REPLICA, REFRESH_INTERVAL);
          final String currentIndexNumberOfReplicas = indexSettings.get(NUMBERS_OF_REPLICA);
          if (currentIndexNumberOfReplicas == null
              || !currentIndexNumberOfReplicas.equals(currentConfigNumberOfReplicas)) {
            indexSettings.put(NUMBERS_OF_REPLICA, currentConfigNumberOfReplicas);
            final boolean success = setIndexSettingsFor(indexSettings, index);
            if (success) {
              LOGGER.debug("Successfully updated number of replicas for index {}", index);
            } else {
              LOGGER.warn("Failed to update number of replicas for index {}", index);
            }
          }
        });
  }

  @Override
  public boolean setIndexSettingsFor(final Map<String, ?> settings, final String indexPattern) {
    final IndexSettings indexSettings =
        new IndexSettings.Builder()
            .refreshInterval(ri -> ri.time(((String) settings.get(REFRESH_INTERVAL))))
            .numberOfReplicas(String.valueOf(settings.get(NUMBERS_OF_REPLICA)))
            .build();
    return richOpenSearchClient.index().setIndexSettingsFor(indexSettings, indexPattern);
  }

  @Override
  public String getOrDefaultRefreshInterval(final String indexName, final String defaultValue) {
    return richOpenSearchClient.index().getOrDefaultRefreshInterval(indexName, defaultValue);
  }

  @Override
  public String getOrDefaultNumbersOfReplica(final String indexName, final String defaultValue) {
    return richOpenSearchClient.index().getOrDefaultNumbersOfReplica(indexName, defaultValue);
  }

  @Override
  public void refresh(final String indexPattern) {
    richOpenSearchClient.index().refreshWithRetries(indexPattern);
  }

  @Override
  public boolean isHealthy() {
    return richOpenSearchClient.cluster().isHealthy();
  }

  @Override
  public Set<String> getIndexNames(final String indexPattern) {
    return richOpenSearchClient.index().getIndexNamesWithRetries(indexPattern);
  }

  @Override
  public Set<String> getAliasesNames(final String indexPattern) {
    return richOpenSearchClient.index().getAliasesNamesWithRetries(indexPattern);
  }

  @Override
  public long getNumberOfDocumentsFor(final String... indexPatterns) {
    return richOpenSearchClient.index().getNumberOfDocumentsWithRetries(indexPatterns);
  }

  @Override
  public boolean deleteIndicesFor(final String indexPattern) {
    return richOpenSearchClient.index().deleteIndicesWithRetries(indexPattern);
  }

  @Override
  public boolean deleteTemplatesFor(final String deleteTemplatePattern) {
    return richOpenSearchClient.template().deleteTemplatesWithRetries(deleteTemplatePattern);
  }

  @Override
  public void removePipeline(final String pipelineName) {
    richOpenSearchClient.pipeline().removePipelineWithRetries(pipelineName);
  }

  @Override
  public boolean addPipeline(final String name, final String pipelineDefinition) {
    return richOpenSearchClient.pipeline().addPipelineWithRetries(name, pipelineDefinition);
  }

  @Override
  public Map<String, String> getIndexSettingsFor(final String indexName, final String... fields) {
    final IndexSettings indexSettings =
        richOpenSearchClient.index().getIndexSettingsWithRetries(indexName);
    final var result = new HashMap<String, String>();
    for (final String field : fields) {
      if (field.equals(REFRESH_INTERVAL)) {
        final var refreshInterval = indexSettings.refreshInterval();
        result.put(REFRESH_INTERVAL, refreshInterval != null ? refreshInterval.time() : null);
      }
      if (field.equals(NUMBERS_OF_REPLICA)) {
        result.put(NUMBERS_OF_REPLICA, indexSettings.numberOfReplicas());
      }
    }
    return result;
  }

  @Override
  public String getIndexPrefix() {
    return operateProperties.getOpensearch().getIndexPrefix();
  }

  private void createDefaults() {
    final OperateOpensearchProperties osConfig = operateProperties.getOpensearch();

    final String settingsTemplateName = settingsTemplateName();
    LOGGER.info(
        "Create default settings '{}' with {} shards and {} replicas per index.",
        settingsTemplateName,
        osConfig.getNumberOfShards(),
        osConfig.getNumberOfReplicas());

    final IndexSettings settings = getDefaultIndexSettings();
    richOpenSearchClient
        .template()
        .createComponentTemplateWithRetries(
            new PutComponentTemplateRequest.Builder()
                .name(settingsTemplateName)
                .template(t -> t.settings(settings))
                .build());
  }

  private IndexSettings getDefaultIndexSettings() {
    final OperateOpensearchProperties osConfig = operateProperties.getOpensearch();
    return new IndexSettings.Builder()
        .numberOfShards(String.valueOf(osConfig.getNumberOfShards()))
        .numberOfReplicas(String.valueOf(osConfig.getNumberOfReplicas()))
        .build();
  }

  private IndexSettings getIndexSettings(final String indexName) {
    final OperateOpensearchProperties osConfig = operateProperties.getOpensearch();
    final var shards =
        osConfig
            .getNumberOfShardsForIndices()
            .getOrDefault(indexName, osConfig.getNumberOfShards());
    final var replicas =
        osConfig
            .getNumberOfReplicasForIndices()
            .getOrDefault(indexName, osConfig.getNumberOfReplicas());

    return new IndexSettings.Builder()
        .numberOfShards(String.valueOf(shards))
        .numberOfReplicas(String.valueOf(replicas))
        .build();
  }

  private String settingsTemplateName() {
    final OperateOpensearchProperties osConfig = operateProperties.getOpensearch();
    return format("%s_template", osConfig.getIndexPrefix());
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private IndexSettings templateSettings(final TemplateDescriptor templateDescriptor) {
    final var shards =
        operateProperties
            .getOpensearch()
            .getNumberOfShardsForIndices()
            .get(templateDescriptor.getIndexName());

    final var replicas =
        operateProperties
            .getOpensearch()
            .getNumberOfReplicasForIndices()
            .get(templateDescriptor.getIndexName());

    if (shards != null || replicas != null) {
      final var indexSettingsBuilder = new IndexSettings.Builder();

      if (shards != null) {
        indexSettingsBuilder.numberOfShards(shards.toString());
      }

      if (replicas != null) {
        indexSettingsBuilder.numberOfReplicas(replicas.toString());
      }

      return indexSettingsBuilder.build();
    }

    return null;
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    final var templateSettings = templateSettings(templateDescriptor);
    final var templateBuilder =
        new IndexTemplateMapping.Builder()
            .aliases(templateDescriptor.getAlias(), new Alias.Builder().build());

    if (templateSettings != null) {
      templateBuilder.settings(templateSettings);
    }

    final IndexTemplateMapping template = templateBuilder.build();

    putIndexTemplate(
        new PutIndexTemplateRequest.Builder()
            .name(templateDescriptor.getTemplateName())
            .indexPatterns(templateDescriptor.getIndexPattern())
            .template(template)
            .composedOf(settingsTemplateName())
            .build());

    // This is necessary, otherwise operate won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();
    final String templateFileName =
        format(SCHEMA_OPENSEARCH_CREATE_TEMPLATE_JSON, templateDescriptor.getIndexName());
    try {
      final InputStream description =
          OpensearchSchemaManager.class.getResourceAsStream(templateFileName);
      final var request =
          createIndexFromJson(
              StreamUtils.copyToString(description, Charset.defaultCharset()),
              templateDescriptor.getFullQualifiedName(),
              Map.of(
                  templateDescriptor.getAlias(), new Alias.Builder().isWriteIndex(false).build()),
              getIndexSettings(templateDescriptor.getIndexName()));
      createIndex(request, indexName);
    } catch (final Exception e) {
      throw new OperateRuntimeException(e);
    }
  }

  private void putIndexTemplate(final PutIndexTemplateRequest request) {
    final boolean created = richOpenSearchClient.template().createTemplateWithRetries(request);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, final String indexName) {
    final boolean created = richOpenSearchClient.index().createIndexWithRetries(createIndexRequest);
    if (created) {
      LOGGER.debug("Index [{}] was successfully created", indexName);
    } else {
      LOGGER.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void createIndex(final IndexDescriptor indexDescriptor) {
    try {
      final String indexFilename =
          format(SCHEMA_OPENSEARCH_CREATE_INDEX_JSON, indexDescriptor.getIndexName());
      final InputStream description =
          OpensearchSchemaManager.class.getResourceAsStream(indexFilename);
      final var request =
          createIndexFromJson(
              StreamUtils.copyToString(description, Charset.defaultCharset()),
              indexDescriptor.getFullQualifiedName(),
              Map.of(indexDescriptor.getAlias(), new Alias.Builder().isWriteIndex(false).build()),
              getIndexSettings(indexDescriptor.getIndexName()));
      createIndex(request, indexDescriptor.getFullQualifiedName());
    } catch (final Exception e) {
      throw new OperateRuntimeException(
          "Could not create index " + indexDescriptor.getIndexName(), e);
    }
  }

  /** Reads mappings and optionally settings from json file */
  private CreateIndexRequest createIndexFromJson(
      final String json,
      final String indexName,
      final Map<String, Alias> aliases,
      final IndexSettings settings) {
    try {
      final var indexAsJSONNode = objectMapper.readTree(new StringReader(json));

      final var customSettings = getCustomSettings(settings, indexAsJSONNode);
      final var mappings = getMappings(indexAsJSONNode.get(MAPPINGS));

      return new CreateIndexRequest.Builder()
          .index(indexName)
          .aliases(aliases)
          .settings(customSettings)
          .mappings(mappings)
          .build();
    } catch (final Exception e) {
      throw new OperateRuntimeException("Could not load schema for " + indexName, e);
    }
  }

  private TypeMapping getMappings(final JsonNode mappingsAsJSON) {
    final JsonParser jsonParser =
        JsonProvider.provider().createParser(new StringReader(mappingsAsJSON.toPrettyString()));
    return TypeMapping._DESERIALIZER.deserialize(jsonParser, jsonpMapper);
  }

  private IndexSettings getCustomSettings(
      final IndexSettings defaultSettings, final JsonNode indexAsJSONNode) {
    if (indexAsJSONNode.has(SETTINGS)) {
      final var settingsJSON = indexAsJSONNode.get(SETTINGS);
      final JsonParser jsonParser =
          JsonProvider.provider().createParser(new StringReader(settingsJSON.toPrettyString()));
      final var updatedSettings = IndexSettings._DESERIALIZER.deserialize(jsonParser, jsonpMapper);
      return new IndexSettings.Builder()
          .index(defaultSettings)
          .analysis(updatedSettings.analysis())
          .build();
    }
    return defaultSettings;
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private Optional<Map<String, Object>> fetchIsmPolicy() {
    try {
      return Optional.ofNullable(
          richOpenSearchClient.ism().getPolicy(OPERATE_DELETE_ARCHIVED_INDICES));
    } catch (final OpenSearchException e) {
      if (e.status() != 404) {
        LOGGER.error(format("Failed to get policy %s", OPERATE_DELETE_ARCHIVED_INDICES), e);
      }
      return Optional.empty();
    }
  }

  private String loadIsmPolicy() throws IOException {
    final var policyFilename =
        format(SCHEMA_OPENSEARCH_CREATE_POLICY_JSON, OPERATE_DELETE_ARCHIVED_INDICES);
    final var inputStream = OpensearchSchemaManager.class.getResourceAsStream(policyFilename);
    final var policyContent = StreamUtils.copyToString(inputStream, Charset.defaultCharset());
    return policyContent.replace(
        "$MIN_INDEX_AGE", operateProperties.getArchiver().getIlmMinAgeForDeleteArchivedIndices());
  }

  private void createIsmPolicy() {
    fetchIsmPolicy()
        .ifPresentOrElse(
            ismPolicy ->
                LOGGER.warn(
                    "ISM policy {} already exists: {}.",
                    OPERATE_DELETE_ARCHIVED_INDICES,
                    ismPolicy),
            () -> {
              try {
                richOpenSearchClient
                    .ism()
                    .createPolicy(OPERATE_DELETE_ARCHIVED_INDICES, loadIsmPolicy());
                LOGGER.info(
                    "Created ISM policy {} for min age of {}.",
                    OPERATE_DELETE_ARCHIVED_INDICES,
                    operateProperties.getArchiver().getIlmMinAgeForDeleteArchivedIndices());
              } catch (final Exception e) {
                throw new OperateRuntimeException(
                    "Failed to create ISM policy " + OPERATE_DELETE_ARCHIVED_INDICES, e);
              }
            });
  }
}
