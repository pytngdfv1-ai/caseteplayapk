package com.mixcasete.app.di

import android.content.Context
import com.mixcasete.app.data.db.AppDb
import com.mixcasete.app.data.db.TrackDao
import com.mixcasete.app.util.SettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDb = AppDb.get(context)

    @Provides
    @Singleton
    fun provideTrackDao(db: AppDb): TrackDao = db.trackDao()

    @Provides
    @Singleton
    fun provideSettings(@ApplicationContext context: Context) = SettingsStore(context)
}
