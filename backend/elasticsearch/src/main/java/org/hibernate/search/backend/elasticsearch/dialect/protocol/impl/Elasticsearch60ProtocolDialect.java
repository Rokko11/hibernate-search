/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.dialect.protocol.impl;

import org.hibernate.search.backend.elasticsearch.gson.spi.GsonProvider;
import org.hibernate.search.backend.elasticsearch.work.builder.factory.impl.Elasticsearch60WorkBuilderFactory;
import org.hibernate.search.backend.elasticsearch.work.builder.factory.impl.ElasticsearchWorkBuilderFactory;

/**
 * The protocol dialect for Elasticsearch 6.0 to 6.2.
 */
public class Elasticsearch60ProtocolDialect extends Elasticsearch67ProtocolDialect
		implements ElasticsearchProtocolDialect {

	@Override
	public ElasticsearchWorkBuilderFactory createWorkBuilderFactory(GsonProvider gsonProvider) {
		// Necessary because of the "allow_partial_search_results" flag for search APIs introduced in ES6.3
		return new Elasticsearch60WorkBuilderFactory( gsonProvider );
	}
}