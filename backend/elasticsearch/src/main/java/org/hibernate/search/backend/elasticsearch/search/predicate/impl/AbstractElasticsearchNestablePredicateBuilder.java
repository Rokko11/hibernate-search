/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.search.predicate.impl;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;

import org.hibernate.search.backend.elasticsearch.gson.impl.JsonAccessor;
import org.hibernate.search.backend.elasticsearch.logging.impl.Log;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

import com.google.gson.JsonObject;

public abstract class AbstractElasticsearchNestablePredicateBuilder extends AbstractElasticsearchSearchPredicateBuilder {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );
	private static final JsonAccessor<String> PATH_ACCESSOR = JsonAccessor.root().property( "path" ).asString();
	private static final JsonAccessor<JsonObject> QUERY_ACCESSOR = JsonAccessor.root().property( "query" ).asObject();

	@Override
	public void checkNestableWithin(String expectedParentNestedPath) {
		List<String> nestedPathHierarchy = getNestedPathHierarchy();

		if ( expectedParentNestedPath != null && !nestedPathHierarchy.contains( expectedParentNestedPath ) ) {
			throw log.invalidNestedObjectPathForPredicate(
					expectedParentNestedPath,
					getFieldPathsForErrorMessage()
			);
		}
	}

	@Override
	public JsonObject build(ElasticsearchSearchPredicateContext context) {
		checkNestableWithin( context.getNestedPath() );

		List<String> nestedPathHierarchy = getNestedPathHierarchy();
		String expectedNestedPath = nestedPathHierarchy.isEmpty() ? null
				: nestedPathHierarchy.get( nestedPathHierarchy.size() - 1 );

		if ( Objects.equals( context.getNestedPath(), expectedNestedPath ) ) {
			// Implicit nesting is not necessary
			return super.build( context );
		}

		// The context we expect this predicate to be built in.
		// We'll make sure to wrap it in nested predicates as appropriate in the next few lines,
		// so that the predicate is actually executed in this context.
		ElasticsearchSearchPredicateContext contextAfterImplicitNesting =
				context.withNestedPath( expectedNestedPath );

		JsonObject result = super.build( contextAfterImplicitNesting );

		// traversing the nestedPathHierarchy in the inverted order
		for ( int i = 0; i < nestedPathHierarchy.size(); i++ ) {
			String path = nestedPathHierarchy.get( nestedPathHierarchy.size() - 1 - i );
			if ( path.equals( context.getNestedPath() ) ) {
				// skip all from this point
				break;
			}

			JsonObject innerObject = new JsonObject();

			PATH_ACCESSOR.set( innerObject, path );
			QUERY_ACCESSOR.set( innerObject, result );

			JsonObject outerObject = new JsonObject();
			outerObject.add( "nested", innerObject );
			result = outerObject;
		}

		return result;
	}

	protected abstract List<String> getNestedPathHierarchy();

	protected abstract List<String> getFieldPathsForErrorMessage();
}