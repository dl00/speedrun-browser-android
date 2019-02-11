package danb.speedrunbrowser.utils;

public class Constants {
    public static final String YOUTUBE_DEVELOPER_KEY = "AIzaSyBeFCTcJNgYuockeJoDreJtyufc4mTniqs";

    public static final String TWITCH_EMBED_SNIPPET = "<html><body style=\"margin: 0; padding: 0\">\n" +
            "<script src= \"https://player.twitch.tv/js/embed/v1.js\"></script>\n" +
            "<div style=\"margin: 0; padding: 0;\" id=\"player\"></div>\n" +
            "<script type=\"text/javascript\">\n" +
            "  var options = {\n" +
            "    width: window.innerWidth,\n" +
            "    height: window.innerHeight,\n" +
            "    video: \"%s\",\n" +
            "  };\n" +
            "  var player = new Twitch.Player(\"player\", options);\n" +
            "  player.setVolume(0.5);\n" +
            "  player.setMuted(false);\n" +
            "</script></body></html>";
}
