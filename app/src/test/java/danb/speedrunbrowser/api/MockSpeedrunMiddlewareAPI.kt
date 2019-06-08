package danb.speedrunbrowser.api

import danb.speedrunbrowser.api.objects.*
import io.reactivex.Observable

/**
 * A simple implementation of the middleware api which returns dummy responses
 */
class MockSpeedrunMiddlewareAPI : SpeedrunMiddlewareAPI.Endpoints {

    var simulateError: SpeedrunMiddlewareAPI.Error? = null

    override fun autocomplete(query: String): Observable<SpeedrunMiddlewareAPI.APISearchResponse> {

        return Observable.just(SpeedrunMiddlewareAPI.APISearchResponse(
                search = SpeedrunMiddlewareAPI.APISearchData(
                        games = sampleGames.filterValues { it.resolvedName.contains(query) }.values.toList(),
                        players = samplePlayers.filterValues { it.resolvedName.contains(query) }.values.toList()
                )
        ))
    }

    override fun listGenres(query: String): Observable<SpeedrunMiddlewareAPI.APIResponse<Genre>>
        = response(sampleGenres.filterValues { it.name.contains(query) }.values)

    override fun listGames(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Game>>
        = response(sampleGames.values)

    override fun listGamesByGenre(genreId: String, offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Game>>
        = listGames(0)

    override fun listGames(ids: String): Observable<SpeedrunMiddlewareAPI.APIResponse<Game>>
        = splitIdsResponse(ids, sampleGames)

    override fun listPlayers(ids: String): Observable<SpeedrunMiddlewareAPI.APIResponse<User>>
        = splitIdsResponse(ids, samplePlayers)

    override fun listLeaderboards(categoryId: String): Observable<SpeedrunMiddlewareAPI.APIResponse<Leaderboard>>
        = splitIdsResponse(categoryId, sampleLeaderboards)

    override fun listLatestRuns(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<LeaderboardRunEntry>>
        = response(sampleRuns.values)

    override fun listLatestRunsByGenre(genreId: String, offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<LeaderboardRunEntry>>
        = response(sampleRuns.filterValues { it.run.game?.genres?.contains(sampleGenres.getValue(genreId)) ?: true }.values)

    override fun listRuns(runIds: String): Observable<SpeedrunMiddlewareAPI.APIResponse<LeaderboardRunEntry>>
        = splitIdsResponse(runIds, sampleRuns)

    override fun whatAreThese(thingIds: String): Observable<SpeedrunMiddlewareAPI.APIResponse<WhatIsEntry>> {

        val entries: List<WhatIsEntry> = thingIds.split(',').map {
            when {
                sampleGames.containsKey(it) -> WhatIsEntry("game")
                samplePlayers.containsKey(it) -> WhatIsEntry("player")
                sampleRuns.containsKey(it) -> WhatIsEntry("run")
                else -> WhatIsEntry("unknown")
            }
        }

        return response(entries)
    }

    private fun<T> splitIdsResponse(ids: String, from: Map<String, T>): Observable<SpeedrunMiddlewareAPI.APIResponse<T>> {
        val splitIds = ids.split(',')
        return response(from.filterKeys { splitIds.contains(it) }.values)
    }

    private fun<T> response(result: Collection<T>): Observable<SpeedrunMiddlewareAPI.APIResponse<T>> {
        if (simulateError != null)
            return Observable.just(SpeedrunMiddlewareAPI.APIResponse<T>(
                    data = null,
                    error = simulateError
            ))
        else
            return Observable.just(SpeedrunMiddlewareAPI.APIResponse(
                    data = result.toList(),
                    error = null
            ))
    }
}
