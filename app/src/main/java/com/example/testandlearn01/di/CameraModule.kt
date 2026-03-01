package com.example.testandlearn01.di

import android.content.Context
import com.example.testandlearn01.data.camera.CameraRepositoryImpl
import com.example.testandlearn01.domain.repository.CameraRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.ViewModelComponent
import javax.inject.Singleton

@Module
@InstallIn(ViewModelComponent::class)
object CameraModule {

    @Provides
    @Singleton
    fun provideCameraRepository(
        @ApplicationContext context: Context
    ): CameraRepository {
        return CameraRepositoryImpl(context)
    }
}