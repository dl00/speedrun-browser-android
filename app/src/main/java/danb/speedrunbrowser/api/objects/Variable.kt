package danb.speedrunbrowser.api.objects

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken

import java.io.Serializable
import java.lang.reflect.Type
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.Objects

data class Variable(
    val id: String,
    val name: String,
    val mandatory: Boolean = false,
    val obsoletes: Boolean = false,

    val scope: VariableScope?,

    val deflt: String?,

    val isSubcategory: Boolean = false,

    val values: Map<String, VariableValue>

) : Serializable {
    data class VariableValue(
        var label: String,
        var rules: String? = null
    ) : Serializable

    class VariableScope(
        val type: String? = null,
        val category: String? = null,
        val level: String? = null
    ) : Serializable

    class VariableSelections(run: Run? = null) : Serializable {

        private val selections: MutableMap<String, MutableSet<String>> = mutableMapOf()

        init {
            if(run?.values != null) {
                for((key, value) in run.values)
                    selections[key] = mutableSetOf(value)
            }
        }

        fun setDefaults(vars: List<Variable>) {
            for (`var` in vars) {
                if (!selections.containsKey(`var`.id) && `var`.isSubcategory && !`var`.values.isEmpty()) {
                    select(`var`.id, `var`.values.keys.iterator().next(), true)
                }
            }
        }

        fun shouldShowRun(run: Run, activeVariables: List<Variable>): Boolean {

            if (run.values != null) {
                for (selection in activeVariables) {
                    if (!run.values.containsKey(selection.id)) {
                        return false
                    }

                    if (!selections.containsKey(selection.id))
                        continue

                    if (!selections.getValue(selection.id).contains(run.values[selection.id]))
                        return false
                }

                // platforms and regions can also be filtered if special keys are provided
                if (selections.containsKey(FILTER_KEY_PLATFORM) && (run.system == null || !Objects.requireNonNull<Set<String>>(selections[FILTER_KEY_PLATFORM]).contains(run.system.platform)) || selections.containsKey(FILTER_KEY_REGION) && (run.system == null || !Objects.requireNonNull<Set<String>>(selections[FILTER_KEY_REGION]).contains(run.system.region))) {
                    return false
                }
            }

            return true
        }

        fun filterLeaderboardRuns(lb: Leaderboard, activeVariables: List<Variable>): List<LeaderboardRunEntry> {
            val shownRuns = ArrayList<LeaderboardRunEntry>()

            var lastPlace = 1
            var lastTime = 0

            for (re in lb.runs!!) {
                if (shouldShowRun(re.run, activeVariables)) {
                    val newRunEntry = LeaderboardRunEntry(
                            place = if (re.run.times!!.primary_t != lastTime) shownRuns.size + 1 else lastPlace,
                            run = re.run
                    )

                    lastPlace = newRunEntry.place!!
                    lastTime = newRunEntry.run.times!!.primary_t

                    shownRuns.add(newRunEntry)
                }
            }

            return shownRuns
        }

        fun isSelected(id: String, vv: String): Boolean {
            return selections.containsKey(id) && Objects.requireNonNull<Set<String>>(selections[id]).contains(vv)
        }

        fun select(variableId: String, valueId: String, isSelected: Boolean) {
            if (isSelected)
                selections.getOrPut(variableId, { mutableSetOf() }).add(valueId)
            else if (selections.containsKey(variableId)) {
                selections[variableId]!!.remove(valueId)

                if (selections[variableId]!!.isEmpty())
                    selections.remove(variableId)
            }
        }

        fun selectOnly(variableId: String, valueId: String) {
            selections.remove(variableId)
            select(variableId, valueId, true)
        }

        fun getSelections(variableId: String): Set<String>? {
            return selections[variableId]
        }

        companion object {
            val FILTER_KEY_PLATFORM = "platform"
            val FILTER_KEY_REGION = "region"
        }
    }

    // need custom serializer/deserializer due to occasionally the object is just a string
    class JsonConverter : JsonSerializer<Variable>, JsonDeserializer<Variable> {
        override fun serialize(src: Variable, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()

            obj.addProperty("id", src.id)
            obj.addProperty("name", src.name)
            obj.addProperty("mandatory", src.mandatory)
            obj.addProperty("obsoletes", src.obsoletes)
            obj.addProperty("default", src.deflt)
            obj.addProperty("is-subcategory", src.isSubcategory)

            obj.add("scope", context.serialize(src.scope))
            obj.add("values", context.serialize(src.values))

            return obj
        }

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Variable {

            val obj = json.asJsonObject

            val valsObj = obj.getAsJsonObject("values")

            val hmType = object : TypeToken<Map<String, VariableValue>>() {}.type

            return Variable(
                    id = obj.get("id").asString,

                    name = obj.get("name").asString,
                    mandatory = obj.get("mandatory").asBoolean,
                    obsoletes = obj.get("mandatory").asBoolean,
                    isSubcategory = obj.get("is-subcategory").asBoolean,

                    deflt = if (obj.has("default")) obj.get("default").asString else null,

                    scope = if (obj.has("scope")) context.deserialize<VariableScope>(obj.get("scope"), VariableScope::class.java) else null,

                    values = if (valsObj.has("values"))
                        context.deserialize<Map<String, VariableValue>>(valsObj.get("values"), hmType)
                    else
                        context.deserialize<Map<String, VariableValue>>(valsObj, hmType)
            )
        }
    }
}
