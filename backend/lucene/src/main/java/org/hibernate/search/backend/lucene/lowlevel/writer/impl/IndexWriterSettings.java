/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.lucene.lowlevel.writer.impl;

import static org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings.WriterRadicals.INFOSTREAM;
import static org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings.WriterRadicals.MAX_BUFFERED_DOCS;
import static org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings.WriterRadicals.MAX_MERGE_DOCS;
import static org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings.WriterRadicals.MERGE_CALIBRATE_BY_DELETES;
import static org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings.WriterRadicals.MERGE_FACTOR;
import static org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings.WriterRadicals.MERGE_MAX_FORCED_SIZE;
import static org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings.WriterRadicals.MERGE_MAX_SIZE;
import static org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings.WriterRadicals.MERGE_MIN_SIZE;
import static org.hibernate.search.backend.lucene.cfg.LuceneIndexSettings.WriterRadicals.RAM_BUFFER_SIZE;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.hibernate.search.backend.lucene.logging.impl.Log;
import org.hibernate.search.engine.cfg.spi.ConfigurationProperty;
import org.hibernate.search.engine.cfg.spi.ConfigurationPropertySource;
import org.hibernate.search.engine.cfg.spi.OptionalConfigurationProperty;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogByteSizeMergePolicy;

/**
 * Represents possible options to be applied to an
 * {@code org.apache.lucene.index.IndexWriter}.
 *
 * @author Sanne Grinovero
 */
public final class IndexWriterSettings implements Serializable {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private IndexWriterSettings() {
	}

	public static List<IndexWriterSettingValue<?>> extractAll(ConfigurationPropertySource propertySource) {
		List<IndexWriterSettingValue<?>> result = new ArrayList<>();
		for ( Extractor<?, ?> extractor : EXTRACTORS ) {
			IndexWriterSettingValue<?> extracted = extractor.extractOrNull( propertySource );
			if ( extracted != null ) {
				result.add( extracted );
			}
		}
		return result;
	}

	private static final List<Extractor<?, ?>> EXTRACTORS = new ArrayList<>();

	static {
		registerIntegerWriterSetting( MAX_BUFFERED_DOCS, IndexWriterConfig::setMaxBufferedDocs );
		registerIntegerWriterSetting( RAM_BUFFER_SIZE, IndexWriterConfig::setRAMBufferSizeMB );

		registerSetting( Extractor.fromBoolean( INFOSTREAM, enabled -> enabled ? new LoggerInfoStream() : null,
				IndexWriterConfig::setInfoStream, (logByteSizeMergePolicy, integer) -> { } ) );

		registerIntegerMergePolicySetting( MAX_MERGE_DOCS, LogByteSizeMergePolicy::setMaxMergeDocs );
		registerIntegerMergePolicySetting( MERGE_FACTOR, LogByteSizeMergePolicy::setMergeFactor );
		registerIntegerMergePolicySetting( MERGE_MIN_SIZE, LogByteSizeMergePolicy::setMinMergeMB );
		registerIntegerMergePolicySetting( MERGE_MAX_SIZE, LogByteSizeMergePolicy::setMaxMergeMB );
		registerIntegerMergePolicySetting( MERGE_MAX_FORCED_SIZE, LogByteSizeMergePolicy::setMaxMergeMBForForcedMerge );
		registerBooleanMergePolicySetting( MERGE_CALIBRATE_BY_DELETES, LogByteSizeMergePolicy::setCalibrateSizeByDeletes );
	}

	private static void registerIntegerWriterSetting(String propertyKey,
			BiConsumer<IndexWriterConfig, Integer> writerSettingApplier) {
		EXTRACTORS.add( Extractor.fromInteger( propertyKey, passThrough -> passThrough,
				writerSettingApplier, (logByteSizeMergePolicy, integer) -> { } ) );
	}

	private static void registerIntegerMergePolicySetting(String propertyKey,
			BiConsumer<LogByteSizeMergePolicy, Integer> mergePolicySettingApplier) {
		EXTRACTORS.add( Extractor.fromInteger( propertyKey, passThrough -> passThrough,
				(writer, integer) -> { }, mergePolicySettingApplier ) );
	}

	private static void registerBooleanMergePolicySetting(String propertyKey,
			BiConsumer<LogByteSizeMergePolicy, Boolean> mergePolicySettingApplier) {
		EXTRACTORS.add( Extractor.fromBoolean( propertyKey, passThrough -> passThrough,
				(writer, integer) -> { }, mergePolicySettingApplier ) );
	}

	private static void registerSetting(Extractor<?, ?> extractor) {
		EXTRACTORS.add( extractor );
	}

	private static final class Extractor<T, R> {

		static <T> Extractor fromInteger(String propertyKey,
				Function<Integer, T> processor,
				BiConsumer<IndexWriterConfig, T> writerSettingApplier,
				BiConsumer<LogByteSizeMergePolicy, T> mergePolicySettingApplier) {
			OptionalConfigurationProperty<Integer> property = ConfigurationProperty.forKey( propertyKey )
					.asInteger().build();
			return new Extractor<>( propertyKey, property, processor, writerSettingApplier, mergePolicySettingApplier );
		}

		static <T> Extractor fromBoolean(String propertyKey,
				Function<Boolean, T> processor,
				BiConsumer<IndexWriterConfig, T> writerSettingApplier,
				BiConsumer<LogByteSizeMergePolicy, T> mergePolicySettingApplier) {
			OptionalConfigurationProperty<Boolean> property = ConfigurationProperty.forKey( propertyKey )
					.asBoolean().build();
			return new Extractor<>( propertyKey, property, processor, writerSettingApplier, mergePolicySettingApplier );
		}

		private final String settingName;
		private final OptionalConfigurationProperty<T> property;
		private final Function<T, R> processor;
		private final BiConsumer<IndexWriterConfig, R> writerSettingApplier;
		private final BiConsumer<LogByteSizeMergePolicy, R> mergePolicySettingApplier;

		private Extractor(String settingName, OptionalConfigurationProperty<T> property, Function<T, R> processor,
				BiConsumer<IndexWriterConfig, R> writerSettingApplier,
				BiConsumer<LogByteSizeMergePolicy, R> mergePolicySettingApplier) {
			this.settingName = settingName;
			this.property = property;
			this.processor = processor;
			this.writerSettingApplier = writerSettingApplier;
			this.mergePolicySettingApplier = mergePolicySettingApplier;
		}

		IndexWriterSettingValue<R> extractOrNull(ConfigurationPropertySource source) {
			return property.getAndMap( source, this::createValueOrNull ).orElse( null );
		}

		private IndexWriterSettingValue<R> createValueOrNull(T value) {
			if ( value == null ) {
				return null;
			}
			if ( log.isDebugEnabled() ) {
				//TODO add DirectoryProvider name when available to log message
				log.debugf( "Set index writer parameter %s to value : %s", settingName, value );
			}
			R processedValue = processor.apply( value );
			return new IndexWriterSettingValue<>( settingName, processedValue,
					writerSettingApplier, mergePolicySettingApplier );
		}

	}

}