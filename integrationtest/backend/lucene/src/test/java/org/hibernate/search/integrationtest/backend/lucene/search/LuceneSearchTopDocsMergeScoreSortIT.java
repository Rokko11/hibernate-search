/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.lucene.search;

import static org.hibernate.search.util.impl.integrationtest.common.assertion.SearchResultAssert.assertThat;
import static org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMapperUtils.referenceProvider;

import org.hibernate.search.backend.lucene.LuceneExtension;
import org.hibernate.search.backend.lucene.search.query.LuceneSearchQuery;
import org.hibernate.search.backend.lucene.search.query.LuceneSearchResult;
import org.hibernate.search.engine.backend.common.DocumentReference;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.work.execution.spi.IndexIndexingPlan;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.engine.search.sort.dsl.SortOrder;
import org.hibernate.search.integrationtest.backend.tck.testsupport.configuration.DefaultAnalysisDefinitions;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.rule.SearchSetupHelper;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.SimpleMappedIndex;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingScope;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.assertj.core.api.Assertions;

/**
 * Test that one can use {@link org.apache.lucene.search.TopDocs#merge(int, TopDocs[])}
 * to merge top docs coming from different Lucene search queries
 * (which could run on different server nodes),
 * when relying on score sort.
 * <p>
 * This is a use case in Infinispan, in particular.
 */
public class LuceneSearchTopDocsMergeScoreSortIT {

	private static final String SEGMENT_0 = "seg0";
	private static final String SEGMENT_1 = "seg1";

	private static final String SEGMENT_0_DOC_0 = "0_0";
	private static final String SEGMENT_0_DOC_1 = "0_1";
	private static final String SEGMENT_0_DOC_NON_MATCHING = "0_nonMatching";
	private static final String SEGMENT_1_DOC_0 = "1_0";
	private static final String SEGMENT_1_DOC_1 = "1_1";
	private static final String SEGMENT_1_DOC_NON_MATCHING = "1_nonMatching";

	@ClassRule
	public static final SearchSetupHelper setupHelper = new SearchSetupHelper();

	private static final SimpleMappedIndex<IndexBinding> index = SimpleMappedIndex.of( "MainIndex", IndexBinding::new );

	@BeforeClass
	public static void setup() {
		setupHelper.start().withIndex( index ).setup();

		initData();
	}

	@Test
	public void desc() {
		LuceneSearchQuery<DocumentReference> segment0Query = matchTextSortedByScoreQuery( SortOrder.DESC, SEGMENT_0 );
		LuceneSearchQuery<DocumentReference> segment1Query = matchTextSortedByScoreQuery( SortOrder.DESC, SEGMENT_1 );
		LuceneSearchResult segment0Result = segment0Query.fetch( 10 );
		LuceneSearchResult segment1Result = segment1Query.fetch( 10 );
		assertThat( segment0Result )
				.hasDocRefHitsExactOrder( index.name(), SEGMENT_0_DOC_0, SEGMENT_0_DOC_1 );
		assertThat( segment1Result )
				.hasDocRefHitsExactOrder( index.name(), SEGMENT_1_DOC_0, SEGMENT_1_DOC_1 );

		TopFieldDocs[] allTopDocs = retrieveTopDocs( segment0Query, segment0Result, segment1Result );
		Assertions.assertThat( TopDocs.merge( segment0Query.getLuceneSort(), 10, allTopDocs ).scoreDocs )
				.containsExactly(
						allTopDocs[1].scoreDocs[0], // SEGMENT_1_DOC_0
						allTopDocs[0].scoreDocs[0], // SEGMENT_0_DOC_0
						allTopDocs[1].scoreDocs[1], // SEGMENT_1_DOC_1
						allTopDocs[0].scoreDocs[1] // SEGMENT_0_DOC_0
				);
	}

	// Also check ascending order, to be sure the above didn't just pass by chance
	@Test
	public void asc() {
		LuceneSearchQuery<DocumentReference> segment0Query = matchTextSortedByScoreQuery( SortOrder.ASC, SEGMENT_0 );
		LuceneSearchQuery<DocumentReference> segment1Query = matchTextSortedByScoreQuery( SortOrder.ASC, SEGMENT_1 );
		LuceneSearchResult segment0Result = segment0Query.fetch( 10 );
		LuceneSearchResult segment1Result = segment1Query.fetch( 10 );
		assertThat( segment0Result )
				.hasDocRefHitsExactOrder( index.name(), SEGMENT_0_DOC_1, SEGMENT_0_DOC_0 );
		assertThat( segment1Result )
				.hasDocRefHitsExactOrder( index.name(), SEGMENT_1_DOC_1, SEGMENT_1_DOC_0 );

		TopFieldDocs[] allTopDocs = retrieveTopDocs( segment0Query, segment0Result, segment1Result );
		Assertions.assertThat( TopDocs.merge( segment0Query.getLuceneSort(), 10, allTopDocs ).scoreDocs )
				.containsExactly(
						allTopDocs[0].scoreDocs[0], // SEGMENT_0_DOC_1
						allTopDocs[1].scoreDocs[0], // SEGMENT_1_DOC_1
						allTopDocs[0].scoreDocs[1], // SEGMENT_0_DOC_0
						allTopDocs[1].scoreDocs[1] // SEGMENT_1_DOC_0
				);
	}

	private LuceneSearchQuery<DocumentReference> matchTextSortedByScoreQuery(SortOrder sortOrder, String routingKey) {
		StubMappingScope scope = index.createScope();
		return scope.query().extension( LuceneExtension.get() )
				.where( f -> f.match().field( "text" ).matching( "hooray" ) )
				.sort( f -> f.score().order( sortOrder ) )
				.routing( routingKey )
				.toQuery();
	}

	private TopFieldDocs[] retrieveTopDocs(LuceneSearchQuery<?> query, LuceneSearchResult ... results) {
		Sort sort = query.getLuceneSort();
		TopFieldDocs[] allTopDocs = new TopFieldDocs[results.length];
		for ( int i = 0; i < results.length; i++ ) {
			TopDocs topDocs = results[i].getTopDocs();
			allTopDocs[i] = new TopFieldDocs( topDocs.totalHits, topDocs.scoreDocs, sort.getSort() );
		}
		return allTopDocs;
	}

	private static void initData() {
		IndexIndexingPlan<?> plan = index.createIndexingPlan();
		// Important: do not index the documents in the expected order after sorts
		plan.add( referenceProvider( SEGMENT_0_DOC_1, SEGMENT_0 ), document -> {
			document.addValue( index.binding().text, "Hooray" );
		} );
		plan.add( referenceProvider( SEGMENT_0_DOC_0, SEGMENT_0 ), document -> {
			document.addValue( index.binding().text, "Hooray Hooray Hooray" );
		} );
		plan.add( referenceProvider( SEGMENT_0_DOC_NON_MATCHING, SEGMENT_0 ), document -> {
			document.addValue( index.binding().text, "No match" );
		} );
		plan.add( referenceProvider( SEGMENT_1_DOC_0, SEGMENT_1 ), document -> {
			document.addValue( index.binding().text, "Hooray Hooray Hooray Hooray" );
		} );
		plan.add( referenceProvider( SEGMENT_1_DOC_1, SEGMENT_1 ), document -> {
			document.addValue( index.binding().text, "Hooray Hooray" );
		} );
		plan.add( referenceProvider( SEGMENT_1_DOC_NON_MATCHING, SEGMENT_1 ), document -> {
			document.addValue( index.binding().text, "No match" );
		} );

		plan.execute().join();

		// Check that all documents are searchable
		StubMappingScope scope = index.createScope();
		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.matchAll() )
				.toQuery();
		assertThat( query ).hasDocRefHitsAnyOrder(
				index.name(),
				SEGMENT_0_DOC_0, SEGMENT_0_DOC_1, SEGMENT_0_DOC_NON_MATCHING,
				SEGMENT_1_DOC_0, SEGMENT_1_DOC_1, SEGMENT_1_DOC_NON_MATCHING
		);
	}

	private static class IndexBinding {
		final IndexFieldReference<String> text;

		IndexBinding(IndexSchemaElement root) {
			text = root.field(
					"text" ,
					f -> f.asString()
							.analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD_ENGLISH.name )
			)
					.toReference();
		}
	}
}