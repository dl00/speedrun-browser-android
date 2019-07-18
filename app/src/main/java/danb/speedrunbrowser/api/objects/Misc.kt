package danb.speedrunbrowser.api.objects

import com.google.android.material.chip.Chip

fun makeCategoryNameText(game: Game, category: Category, level: Level?): String {
    val fullCategoryName = StringBuilder(category.name)
    if (level?.name != null)
        fullCategoryName.append(" \u2022 ").append(level.name)

    return fullCategoryName.toString()
}