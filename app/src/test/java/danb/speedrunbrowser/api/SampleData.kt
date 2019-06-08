package danb.speedrunbrowser.api

import danb.speedrunbrowser.api.objects.*
import java.net.URL
import java.util.*

val sampleGenres = listOf(
        Genre("fight", "Fighting"),
        Genre("adventure", "Adventure")
).associateBy { it.id }

val sampleGames = listOf(
        Game(
                id = "game1",
                names = mapOf("international" to "Game 1"),
                abbreviation = "g1",
                weblink = "https://speedrun.com/game1",

                released = 2019,
                releaseDate = "05/05/2019",
                platforms = listOf(Platform("switch", "Switch", 2017)),
                regions = listOf(Region("us", "USA")),
                genres = listOf(sampleGenres.getValue("fight")),

                categories = listOf(),
                levels = null
        )
).associateBy { it.id }

val sampleRuns = listOf(
        LeaderboardRunEntry(
                place = 2,
                run = Run(
                        id = "superrun",
                        videos = RunVideos(),
                        comment = "This was a speedrun."
                )
        )
).associateBy { it.run.id }

val samplePlayers = listOf(
        User(
                id = "johnny95",
                name = "Johnny Appleseed",

                nameStyle = UserNameStyle("solid", color = UserNameStyleColor("#135", "#135")),
                role = "player",
                signup = Date("2019-05-05"),
                location = UserLocation("us", names = mapOf("international" to "USA")),
                region = UserLocation("ca", names = mapOf("international" to "California")),

                twitch = MediaLink(URL("https://twitch.tv/v123412345")),
                youtube = MediaLink(URL("https://youtube.com/channel/johnny95")),
                twitter = MediaLink(URL("https://twitter.com/johnny95")),

                bests = mapOf("game1" to User.UserGameBests(
                        "game1",
                        sampleGames.getValue("game1").names,
                        assets = sampleGames.getValue("game1").assets,
                        categories = mapOf("any" to User.UserCategoryBest(id = "any", name = "Any%", type = "per-game", run = sampleRuns.getValue("superrun")))
                ))
        ),
        User(
                id = "phil",
                name = "Phil Wick",

                nameStyle = UserNameStyle("solid", color = UserNameStyleColor("#999", "#999")),
                role = "player",
                signup = Date("2019-05-05"),
                location = UserLocation("us", names = mapOf("international" to "USA")),
                region = UserLocation("ca", names = mapOf("international" to "California")),

                twitch = MediaLink(URL("https://twitch.tv/v12341?some=extra")),
                youtube = MediaLink(URL("https://youtube.com/channel/philly")),
                twitter = MediaLink(URL("https://twitter.com/philly")),

                bests = mapOf("game1" to User.UserGameBests(
                        "game1",
                        sampleGames.getValue("game1").names,
                        assets = sampleGames.getValue("game1").assets,
                        categories = mapOf("any" to User.UserCategoryBest(id = "any", name = "Any%", type = "per-game", run = sampleRuns.getValue("superrun")))
                ))


        )
).associateBy { it.id }

val sampleLeaderboards = listOf(
        Leaderboard(
                game = "game1",
                category = "any",
                level = "level",

                weblink = "https://speedrun.com/game1#any",

                platform = "switch",
                region = "us",

                runs = listOf(sampleRuns.getValue("superrun")),
                players = null
        )
).associateBy { it.game + '_' + it.category }