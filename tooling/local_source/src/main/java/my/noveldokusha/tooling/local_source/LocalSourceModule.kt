package my.noveldokusha.tooling.local_source

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import my.noveldokusha.scraper.sources.LocalSource
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class LocalSourceModule {

    @Binds
    @Singleton
    @IntoSet
    internal abstract fun bindAppLocalSources(v: AppLocalSources): LocalSource

    @Binds
    @Singleton
    @IntoSet
    internal abstract fun bindAppLocalMangaSource(v: AppLocalMangaSource): LocalSource

}
