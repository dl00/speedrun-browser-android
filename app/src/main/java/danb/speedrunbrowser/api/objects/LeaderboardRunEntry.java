package danb.speedrunbrowser.api.objects;

public class LeaderboardRunEntry {
    public int place;
    public Run run;

    public String getPlaceName() {

        if((place / 10) % 10 == 1)
            return place + "th";

        switch(place % 10) {
            case 1:
                return place + "st";
            case 2:
                return place + "nd";
            case 3:
                return place + "rd";
            default:
                return place + "th";
        }
    }
}
