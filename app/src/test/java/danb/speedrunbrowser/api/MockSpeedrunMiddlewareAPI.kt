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

    override fun listGames(mode: String, offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Game>> {
        TODO("Not yet implemented")
    }

    override fun listGames(ids: String): Observable<SpeedrunMiddlewareAPI.APIResponse<Game>>
        = splitIdsResponse(ids, sampleGames)

    override fun listGamesByGenre(genreId: String, mode: String, offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Game>> {
        TODO("Not yet implemented")
    }

    override fun listPlayers(ids: String): Observable<SpeedrunMiddlewareAPI.APIResponse<User>>
        = splitIdsResponse(ids, samplePlayers)

    override fun listLeaderboards(categoryId: String): Observable<SpeedrunMiddlewareAPI.APIResponse<Leaderboard>>
        = splitIdsResponse(categoryId, sampleLeaderboards)

    override fun listLatestRuns(offset: Int, verified: Boolean): Observable<SpeedrunMiddlewareAPI.APIResponse<LeaderboardRunEntry>> {
        TODO("Not yet implemented")
    }

    override fun listLatestRunsByGenre(genreId: String, offset: Int, verified: Boolean): Observable<SpeedrunMiddlewareAPI.APIResponse<LeaderboardRunEntry>> {
        TODO("Not yet implemented")
    }

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

    override fun getGameGroupMetrics(ggId: String): Observable<SpeedrunMiddlewareAPI.APIChartResponse> {
        TODO("Not yet implemented")
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

    override fun getGameMetrics(gameId: String): Observable<SpeedrunMiddlewareAPI.APIChartResponse> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getLeaderboardMetrics(gameId: String): Observable<SpeedrunMiddlewareAPI.APIChartResponse> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getUserMetrics(gameId: String): Observable<SpeedrunMiddlewareAPI.APIChartResponse> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getUserGameMetrics(gameId: String, playerId: String): Observable<SpeedrunMiddlewareAPI.APIChartResponse> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listStreamsByGameGroup(ggId: String, offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Stream>> {
        TODO("Not yet implemented")
    }

    override fun listStreamsByGameGroup(ggId: String, lang: String, offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Stream>> {
        TODO("Not yet implemented")
    }

    override fun listStreamsByGame(gameId: String, offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Stream>> {
        TODO("Not yet implemented")
    }

    override fun listStreamsByGame(gameId: String, lang: String, offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Stream>> {
        TODO("Not yet implemented")
    }
}
