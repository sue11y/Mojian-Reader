package com.mojian.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mojian.reader.data.LibraryRepository
import com.mojian.reader.data.ReaderSettingsRepository
import com.mojian.reader.data.RemoteCacheRepository
import com.mojian.reader.data.RemoteLibraryRepository
import com.mojian.reader.data.SourceRepository
import com.mojian.reader.ui.ReaderApp
import com.mojian.reader.ui.theme.MojianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val libraryRepository = LibraryRepository(applicationContext)
        val settingsRepository = ReaderSettingsRepository(applicationContext)
        val sourceRepository = SourceRepository(applicationContext)
        val remoteLibraryRepository = RemoteLibraryRepository(applicationContext)
        val remoteCacheRepository = RemoteCacheRepository(applicationContext)
        setContent {
            MojianTheme {
                ReaderApp(
                    repository = libraryRepository,
                    settingsRepository = settingsRepository,
                    sourceRepository = sourceRepository,
                    remoteLibraryRepository = remoteLibraryRepository,
                    remoteCacheRepository = remoteCacheRepository
                )
            }
        }
    }
}
