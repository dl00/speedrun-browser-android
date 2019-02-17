package danb.speedrunbrowser.utils;

public class Constants {
    public static final String YOUTUBE_DEVELOPER_KEY = "AIzaSyBeFCTcJNgYuockeJoDreJtyufc4mTniqs";

    public static final String TWITCH_EMBED_SNIPPET = "<html><body style=\"margin: 0; padding: 0; background-color: black;\">\n" +
            "<script src= \"https://player.twitch.tv/js/embed/v1.js\"></script>\n" +
            "<div style=\"margin: 0; padding: 0;\" id=\"player\"></div>\n" +
            "<script type=\"text/javascript\">\n" +
            "  var options = {\n" +
            "    width: 980,\n" +
            "    height: 552,\n" +
            "    video: \"%2$s\",\n" +
            "  };\n" +
            "  console.log(window.innerWidth);\n" +
            "  console.log(window.innerHeight);\n" +
            "  var player = new Twitch.Player(\"player\", options);\n" +
            "  player.setVolume(0.5);\n" +
            "  player.setMuted(false);\n" +
            "</script></body></html>";
}
