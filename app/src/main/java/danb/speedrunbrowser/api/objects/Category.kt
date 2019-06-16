package danb.speedrunbrowser.api.objects

import java.io.Serializable

data class Category(
    val id: String,
    val name: String,
    val weblink: String? = null,
    val type: String? = null,
    val rules: String? = null,

    val variables: List<Variable>? = null,

    val miscellaneous: Boolean = false

) : Serializable {
    fun getRulesText(filter: Variable.VariableSelections): String {
        val rulesText = StringBuilder()

        // add variable rules as necessary
        if (variables != null) {
            for ((id, _, _, _, _, _, isSubcategory, values) in variables) {
                if (!isSubcategory)
                    break

                val selections = filter.getSelections(id)

                if (selections!!.isEmpty())
                    continue

                val moreRules = values.getValue(selections.iterator().next()).rules

                if (moreRules != null)
                    rulesText.append("\n\n").append(moreRules)
            }
        }

        if (rules != null)
            rulesText.append(rules.trim { it <= ' ' })

        return rulesText.toString().trim { it <= ' ' }
    }
}
