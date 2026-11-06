package com.rassvet.essential.di

import android.content.Context
import com.rassvet.essential.data.chat.ChatEngine
import com.rassvet.essential.data.index.IndexRepository
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.llm.HybridLocalLlmEngine
import com.rassvet.essential.data.network.NetworkMonitor
import com.rassvet.essential.data.network.OnlineChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideVaultPreferences(@ApplicationContext context: Context): VaultPreferencesRepository =
        VaultPreferencesRepository(context)

    @Provides
    @Singleton
    fun provideIndexRepository(db: com.rassvet.essential.data.local.AppDatabase): IndexRepository =
        IndexRepository(db)

    @Provides
    @Singleton
    fun provideHybridLocalLlmEngine(
        @ApplicationContext context: Context,
        prefs: VaultPreferencesRepository,
    ): HybridLocalLlmEngine = HybridLocalLlmEngine(context, prefs)

    @Provides
    @Singleton
    fun provideChatEngine(
        @ApplicationContext context: Context,
        prefs: VaultPreferencesRepository,
        index: IndexRepository,
        localEngine: HybridLocalLlmEngine,
        networkMonitor: NetworkMonitor,
    ): ChatEngine = ChatEngine(context, prefs, index, localEngine, networkMonitor)
}
