package com.example.expensetracker.core.di

import android.content.Context
import com.example.expensetracker.core.preferences.DataStorePreferencesRepository
import com.example.expensetracker.core.preferences.PreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideDataStorePreferencesRepository(
        @ApplicationContext context: Context
    ): DataStorePreferencesRepository {
        return DataStorePreferencesRepository(context)
    }

    @Provides
    @Singleton
    fun providePreferencesRepository(
        impl: DataStorePreferencesRepository
    ): PreferencesRepository = impl
}
