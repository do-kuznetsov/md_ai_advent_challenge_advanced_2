package com.sibgear.weather.feature.weather.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

public class FavoriteCityInteractorTest {

    @Test
    public fun addFavoriteCityReturnsRepositoryResult(): Unit = runTest {
        val repository = RecordingFavoriteCityRepository()
        val entry = createEntry()
        val interactor = AddFavoriteCityInteractor(repository)

        assertEquals(Unit, interactor(entry).getOrThrow())
        assertEquals(entry, repository.addedEntry)
    }

    @Test
    public fun removeFavoriteCityReturnsRepositoryResult(): Unit = runTest {
        val repository = RecordingFavoriteCityRepository()
        val entry = createEntry()
        val interactor = RemoveFavoriteCityInteractor(repository)

        assertEquals(Unit, interactor(entry).getOrThrow())
        assertEquals(entry, repository.removedEntry)
    }

    @Test
    public fun getFavoriteCitiesReturnsRepositoryEntries(): Unit = runTest {
        val entries = listOf(createEntry())
        val interactor = GetFavoriteCitiesInteractor(
            RecordingFavoriteCityRepository(favoriteEntries = Result.success(entries)),
        )

        assertEquals(entries, interactor().getOrThrow())
    }

    @Test
    public fun getFavoriteCitiesReturnsRepositoryFailure(): Unit = runTest {
        val failure = IllegalStateException("favorites unavailable")
        val interactor = GetFavoriteCitiesInteractor(
            RecordingFavoriteCityRepository(favoriteEntries = Result.failure(failure)),
        )

        assertSame(failure, interactor().exceptionOrNull())
    }

    private class RecordingFavoriteCityRepository(
        private val favoriteEntries: Result<List<FavoriteCityEntry>> = Result.success(emptyList()),
    ) : FavoriteCityRepository {

        var addedEntry: FavoriteCityEntry? = null
            private set

        var removedEntry: FavoriteCityEntry? = null
            private set

        override suspend fun addCity(entry: FavoriteCityEntry): Result<Unit> {
            addedEntry = entry
            return Result.success(Unit)
        }

        override suspend fun removeCity(entry: FavoriteCityEntry): Result<Unit> {
            removedEntry = entry
            return Result.success(Unit)
        }

        override suspend fun favoriteCities(): Result<List<FavoriteCityEntry>> =
            favoriteEntries
    }

    private companion object {

        fun createEntry(): FavoriteCityEntry =
            FavoriteCityEntry(
                name = "Москва",
                country = "Россия",
                latitude = 55.7558,
                longitude = 37.6173,
            )
    }
}
