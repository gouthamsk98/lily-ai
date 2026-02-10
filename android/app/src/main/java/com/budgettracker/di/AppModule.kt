package com.budgettracker.di

import android.content.Context
import androidx.room.Room
import com.budgettracker.BuildConfig
import com.budgettracker.data.local.AppDatabase
import com.budgettracker.data.local.ExpenseDao
import com.budgettracker.data.remote.ApiService
import com.budgettracker.data.repository.AuthRepositoryImpl
import com.budgettracker.data.repository.ExpenseRepositoryImpl
import com.budgettracker.domain.repository.AuthRepository
import com.budgettracker.domain.repository.ExpenseRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor { chain ->
                // Token will be added by AuthInterceptor
                chain.proceed(chain.request())
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(client: OkHttpClient): ApiService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context, AppDatabase::class.java, "budget_tracker_db"
        ).build()
    }

    @Provides
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()

    @Provides
    @Singleton
    fun provideAuthRepository(apiService: ApiService): AuthRepository =
        AuthRepositoryImpl(apiService)

    @Provides
    @Singleton
    fun provideExpenseRepository(apiService: ApiService, dao: ExpenseDao): ExpenseRepository =
        ExpenseRepositoryImpl(apiService, dao)
}
