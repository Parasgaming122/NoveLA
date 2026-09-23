package my.noveldokusha.tooling.sync

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module wiring the Supabase sync engine.
 *
 * Mirrors the conventions of the existing app modules:
 *  * `@Module abstract class XxxModule { companion object { @Provides ... } }`
 *    pattern from LocalDatabaseModule / AppWorkersModule / AppModule.
 *  * Abstract class with companion-object @Providers so we can keep
 *    the @Binds-in-the-abstract-body style consistent with the rest
 *    of the app (no @Binds needed here — every provided class is
 *    concrete).
 *
 * Only the pieces that are NOT already provided elsewhere need a
 * @Provides here:
 *  * [SyncSettings] — @Inject constructor already on the class, but
 *    declaring it here as @Singleton makes it explicit and consistent
 *    with the rest of the app's DI style. (Hilt would auto-construct
 *    it via its @Inject constructor either way; the @Provides here
 *    just makes the Singleton binding obvious.)
 *  * [DynamicSupabaseProvider] — same situation.
 *  * [SyncRepository] — same situation.
 *  * [SyncStarter] — same situation.
 *
 * WorkManager itself is already provided by AppWorkersModule; do NOT
 * re-declare it here.
 */
@InstallIn(SingletonComponent::class)
@Module
abstract class SyncModule {

    companion object {
        @Provides
        @Singleton
        fun provideSyncSettings(
            @ApplicationContext context: Context,
        ): SyncSettings = SyncSettings(context)

        @Provides
        @Singleton
        fun provideDynamicSupabaseProvider(
            syncSettings: SyncSettings,
        ): DynamicSupabaseProvider = DynamicSupabaseProvider(syncSettings)

        @Provides
        @Singleton
        fun provideSyncRepository(
            libraryDao: my.noveldokusha.feature.local_database.DAOs.LibraryDao,
            chapterBodyDao: my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao,
            readingHistoryDao: my.noveldokusha.feature.local_database.DAOs.ReadingHistoryDao,
            dynamicSupabaseProvider: DynamicSupabaseProvider,
            syncSettings: SyncSettings,
        ): SyncRepository = SyncRepository(
            libraryDao,
            chapterBodyDao,
            readingHistoryDao,
            dynamicSupabaseProvider,
            syncSettings,
        )

        @Provides
        @Singleton
        fun provideSyncStarter(
            @ApplicationContext context: Context,
        ): SyncStarter = SyncStarter(context)

        @Provides
        @Singleton
        fun provideSyncPeriodicInitializer(
            @ApplicationContext context: Context,
            syncSettings: SyncSettings,
            appCoroutineScope: my.noveldokusha.core.AppCoroutineScope,
        ): SyncPeriodicInitializer = SyncPeriodicInitializer(context, syncSettings, appCoroutineScope)
    }
}
