package danb.speedrunbrowser.api.objects;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Variable implements Serializable {
    public String id;
    public String name;
    public boolean mandatory;
    public boolean obsoletes;

    public VariableScope scope;

    public String deflt;

    public boolean isSubcategory;

    public Map<String, VariableValue> values;

    public static class VariableValue implements Serializable {
        public String label;
        public String rules;
    }

    public static class VariableScope implements Serializable {
        public String type;
        public String category;
        public String level;
    }

    public static class VariableSelections implements Serializable {
        public static final String FILTER_KEY_PLATFORM = "platform";
        public static final String FILTER_KEY_REGION = "region";

        private Map<String, Set<String>> selections;

        public VariableSelections() {
            selections = new HashMap<>();
        }

        public void setDefaults(List<Variable> vars) {
            for(Variable var : vars) {
                if(!selections.containsKey(var.id) && var.isSubcategory && !var.values.isEmpty()) {
                    select(var.id, var.values.keySet().iterator().next(), true);
                }
            }
        }

        public boolean shouldShowRun(Run run, List<Variable> activeVariables) {

            if(run.values != null) {
                for(Variable selection : activeVariables) {
                    if(!run.values.containsKey(selection.id)) {
                        if(selection.isSubcategory)
                            continue;
                        return false;
                    }

                    if(!selections.containsKey(selection.id))
                        continue;

                    if(!Objects.requireNonNull(selections.get(selection.id)).contains(run.values.get(selection.id)))
                        return false;
                }

                // platforms and regions can also be filtered if special keys are provided
                if((selections.containsKey(FILTER_KEY_PLATFORM) && (run.system == null ||
                        !Objects.requireNonNull(selections.get(FILTER_KEY_PLATFORM)).contains(run.system.platform))) ||
                    selections.containsKey(FILTER_KEY_REGION) && (run.system == null ||
                        !Objects.requireNonNull(selections.get(FILTER_KEY_REGION)).contains(run.system.region))) {
                    return false;
                }
            }

            return true;
        }

        public List<LeaderboardRunEntry> filterLeaderboardRuns(Leaderboard lb, List<Variable> activeVariables) {
            List<LeaderboardRunEntry> shownRuns = new ArrayList<>();

            int lastPlace = 1;
            int curPlace = 0;

            for(LeaderboardRunEntry re : lb.runs) {
                if(shouldShowRun(re.run, activeVariables)) {
                    LeaderboardRunEntry newRunEntry = new LeaderboardRunEntry();
                    newRunEntry.place = re.place != curPlace ? (lastPlace = shownRuns.size() + 1) : lastPlace;
                    curPlace = re.place;
                    newRunEntry.run = re.run;

                    shownRuns.add(newRunEntry);
                }
            }

            return shownRuns;
        }

        public boolean isSelected(String id, String vv) {
            return selections.containsKey(id) && Objects.requireNonNull(selections.get(id)).contains(vv);
        }

        public void select(String variableId, String valueId, boolean isSelected) {
            if(selections.containsKey(variableId)) {
                if(isSelected)
                    Objects.requireNonNull(selections.get(variableId)).add(valueId);
                else {
                    Objects.requireNonNull(selections.get(variableId)).remove(valueId);

                    if(Objects.requireNonNull(selections.get(variableId)).isEmpty())
                        selections.remove(variableId);
                }
            }
            else if(isSelected) {
                HashSet<String> newSet = new HashSet<>();
                newSet.add(valueId);

                selections.put(variableId, newSet);
            }
        }

        public void selectOnly(String variableId, String valueId) {
            selections.remove(variableId);
            select(variableId, valueId, true);
        }

        public Set<String> getSelections(String variableId) {
            return selections.get(variableId);
        }
    }

    // need custom serializer/deserializer due to occasionally the object is just a string
    public static class JsonConverter implements JsonSerializer<Variable>, JsonDeserializer<Variable> {
        public JsonElement serialize(Variable src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();

            obj.addProperty("id", src.id);
            obj.addProperty("name", src.name);
            obj.addProperty("mandatory", src.mandatory);
            obj.addProperty("obsoletes", src.obsoletes);
            obj.addProperty("default", src.deflt);
            obj.addProperty("is-subcategory", src.isSubcategory);

            obj.add("scope", context.serialize(src.scope));
            obj.add("values", context.serialize(src.values));

            return obj;
        }

        public Variable deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Variable v = new Variable();

            JsonObject obj = json.getAsJsonObject();

            v.id = obj.get("id").getAsString();
            v.name = obj.get("name").getAsString();
            v.mandatory = obj.get("mandatory").getAsBoolean();
            v.obsoletes = obj.get("mandatory").getAsBoolean();
            v.isSubcategory = obj.get("is-subcategory").getAsBoolean();

            if(obj.has("default"))
                v.deflt = obj.get("default").getAsString();

            if(obj.has("scope"))
                v.scope = context.deserialize(obj.get("scope"), VariableScope.class);

            JsonObject vObj = obj.getAsJsonObject("values");


            Type hmType = new TypeToken<Map<String, VariableValue>>(){}.getType();

            if(vObj.has("values"))
                v.values = context.deserialize(vObj.get("values"), hmType);
            else
                v.values = context.deserialize(vObj, hmType);

            return v;
        }
    }
}
