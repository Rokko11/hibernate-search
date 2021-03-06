/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.engine.backend.metamodel;

/**
 * A "value" field in the index, i.e. a field that holds a string, integer, etc.
 * <p>
 * "value", in this context, is opposed to "object", as in {@link IndexObjectFieldDescriptor object field}.
 */
public interface IndexValueFieldDescriptor extends IndexFieldDescriptor {

	/**
	 * @return The type of this field, exposing its various capabilities and accepted Java types.
	 * @see IndexValueFieldTypeDescriptor
	 */
	IndexValueFieldTypeDescriptor type();

}
