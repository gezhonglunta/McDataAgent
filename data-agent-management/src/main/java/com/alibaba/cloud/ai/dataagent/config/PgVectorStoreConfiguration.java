/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.properties.VectorStoreDataSourceProperties;
import javax.sql.DataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties({ VectorStoreDataSourceProperties.class, PgVectorStoreProperties.class })
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "pgvector")
public class PgVectorStoreConfiguration {

	@Bean
	@Primary
	@ConditionalOnMissingBean(DataSourceProperties.class)
	@ConfigurationProperties(prefix = "spring.datasource")
	public DataSourceProperties dataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean(name = "dataSource")
	@Primary
	@ConditionalOnMissingBean(name = "dataSource")
	public DataSource dataSource(DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().build();
	}

	@Bean(name = "vectorStoreDataSource")
	@ConditionalOnMissingBean(name = "vectorStoreDataSource")
	public DataSource vectorStoreDataSource(VectorStoreDataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().build();
	}

	@Bean(name = "vectorStoreJdbcTemplate")
	@ConditionalOnMissingBean(name = "vectorStoreJdbcTemplate")
	public JdbcTemplate vectorStoreJdbcTemplate(@Qualifier("vectorStoreDataSource") DataSource vectorStoreDataSource) {
		return new JdbcTemplate(vectorStoreDataSource);
	}

	@Bean
	@ConditionalOnMissingBean(PgVectorStore.class)
	public PgVectorStore pgVectorStore(@Qualifier("vectorStoreJdbcTemplate") JdbcTemplate vectorStoreJdbcTemplate,
			EmbeddingModel embeddingModel,
			PgVectorStoreProperties properties) {
		initializePgVectorSchema(vectorStoreJdbcTemplate, embeddingModel, properties);

		return PgVectorStore.builder(vectorStoreJdbcTemplate, embeddingModel)
			.schemaName(properties.getSchemaName())
			.vectorTableName(properties.getTableName())
			.idType(properties.getIdType())
			.vectorTableValidationsEnabled(properties.isSchemaValidation())
			.dimensions(properties.getDimensions())
			.distanceType(properties.getDistanceType())
			.removeExistingVectorStoreTable(properties.isRemoveExistingVectorStoreTable())
			.indexType(properties.getIndexType())
			.initializeSchema(properties.isInitializeSchema())
			.maxDocumentBatchSize(properties.getMaxDocumentBatchSize())
			.build();
	}

	private void initializePgVectorSchema(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			PgVectorStoreProperties properties) {
		if (!properties.isInitializeSchema()) {
			return;
		}

		String schemaName = properties.getSchemaName();
		String tableName = properties.getTableName();
		String qualifiedTableName = schemaName + "." + tableName;
		int dimensions = resolveDimensions(properties, embeddingModel);
		String indexName = PgVectorStore.DEFAULT_TABLE_NAME.equals(tableName) ? PgVectorStore.DEFAULT_VECTOR_INDEX_NAME
				: tableName + "_index";

		jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
		jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS hstore");
		if (properties.getIdType() == PgVectorStore.PgIdType.UUID) {
			jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
		}

		jdbcTemplate.execute(String.format("CREATE SCHEMA IF NOT EXISTS %s", schemaName));

		if (properties.isRemoveExistingVectorStoreTable()) {
			jdbcTemplate.execute(String.format("DROP TABLE IF EXISTS %s", qualifiedTableName));
		}

		jdbcTemplate.execute(String.format("""
				CREATE TABLE IF NOT EXISTS %s (
					id %s PRIMARY KEY,
					content text,
					metadata json,
					embedding vector(%d)
				)
				""", qualifiedTableName, columnType(properties.getIdType()), dimensions));

		if (properties.getIndexType() != PgVectorStore.PgIndexType.NONE) {
			jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS %s ON %s USING %s (embedding %s)",
					indexName, qualifiedTableName, properties.getIndexType(), properties.getDistanceType().index));
		}
	}

	private int resolveDimensions(PgVectorStoreProperties properties, EmbeddingModel embeddingModel) {
		if (properties.getDimensions() > 0) {
			return properties.getDimensions();
		}
		int dimensions = embeddingModel.dimensions();
		return dimensions > 0 ? dimensions : PgVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE;
	}

	private String columnType(PgVectorStore.PgIdType idType) {
		return switch (idType) {
			case UUID -> "uuid DEFAULT uuid_generate_v4()";
			case TEXT -> "text";
			case INTEGER -> "integer";
			case SERIAL -> "serial";
			case BIGSERIAL -> "bigserial";
		};
	}

}
