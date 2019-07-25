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

        if (rules != null)
            rulesText.append(rules.trim { it <= ' ' }).append("\n\n")

        // add variable rules as necessary
        if (variables != null) {
            for ((id, _, _, _, _, _, isSubcategory, values) in variables) {
                if (!isSubcategory)
                    break

                val selections = filter.getSelections(id)
                if (selections?.isEmpty() != false)
                    continue

                val moreRules = values[selections.iterator().next()]?.rules

                if (moreRules != null)
                    rulesText.append("\n\n").append(moreRules)
            }
        }

        return rulesText.toString().trim { it <= ' ' }
    }
}
