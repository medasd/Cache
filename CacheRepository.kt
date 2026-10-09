package com.example.cacheclean.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Всё, что обычное приложение МОЖЕТ делать с кэшем, без root и скрытых API:
 *  - список приложений (PackageManager + QUERY_ALL_PACKAGES);
 *  - размер кэша других приложений (StorageStatsManager + «Доступ к данным об использовании»);
 *  - удаление СВОЕГО кэша.
 * Очистить кэш чужого приложения Android не позволяет — для этого есть экран настроек (см. [Intents]).
 */
class CacheRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager
    private val storageStats: StorageStatsManager =
        appContext.getSystemService(StorageStatsManager::class.java)

    /** Выдал ли пользователь «Доступ к данным об использовании» этому приложению. */
    fun hasUsageAccess(): Boolean {
        val appOps = appContext.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName,
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            appContext.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Размер кэша приложения в байтах или -1, если узнать не удалось. */
    fun cacheOf(packageName: String): Long = try {
        storageStats
            .queryStatsForPackage(StorageManager.UUID_DEFAULT, packageName, Process.myUserHandle())
            .cacheBytes
    } catch (e: PackageManager.NameNotFoundException) {
        -1L
    } catch (e: SecurityException) {
        -1L
    } catch (e: IOException) {
        -1L
    }

    /**
     * Список приложений. С доступом к статистике — только приложения с кэшем, по убыванию размера.
     * Без доступа — все приложения по алфавиту, размер = -1.
     */
    suspend fun scan(): List<AppCache> = withContext(Dispatchers.IO) {
        val access = hasUsageAccess()
        val self = appContext.packageName
        val installed = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            .filter { it.packageName != self }

        val items = coroutineScope {
            installed.map { info ->
                async {
                    AppCache(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        cacheBytes = if (access) cacheOf(info.packageName) else -1L,
                    )
                }
            }.awaitAll()
        }

        if (access) {
            items.filter { it.cacheBytes > 0 }.sortedByDescending { it.cacheBytes }
        } else {
            items.sortedBy { it.label.lowercase() }
        }
    }

    /** Удаляет кэш самого приложения (внутренний и внешний). Возвращает освобождённые байты. */
    suspend fun clearOwnCache(): Long = withContext(Dispatchers.IO) {
        val dirs = buildList<File> {
            add(appContext.cacheDir)
            addAll(appContext.externalCacheDirs.filterNotNull())
        }
        var freed = 0L
        for (dir in dirs) freed += clearDirectoryContents(dir)
        freed
    }

    private fun clearDirectoryContents(dir: File): Long {
        var freed = 0L
        dir.listFiles()?.forEach { child ->
            val size = child.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (child.deleteRecursively()) freed += size
        }
        return freed
    }
}
