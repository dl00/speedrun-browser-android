package danb.speedrunbrowser.stats

class TabbedSwitcherOptions(
        name: String,
        description: String,
        identifier: String,
        val subcharts: List<ChartOptions>
) : ChartOptions(name, description, identifier)