package ua.komirka.data;

import org.json.*;
import java.util.*;
import static ua.komirka.core.Models.*;

/** Explicit, versioned JSON codecs used by SQLite and the portable JSON export. */
public final class Codec {
    private Codec() {}
    public static JSONObject object(String source) {
        try { return new JSONObject(source); }
        catch(JSONException e) { throw new IllegalArgumentException("Could not read the saved data.",e); }
    }
    public static JSONObject put(JSONObject o,String key,Object value) {
        try { o.put(key,value==null ? JSONObject.NULL:value); return o; }
        catch(JSONException e) { throw new IllegalArgumentException(e); }
    }
    public static JSONObject column(Column c) {
        JSONObject o=new JSONObject(); put(o,"key",c.key); put(o,"name",c.name);
        put(o,"type",c.kind.name()); put(o,"options",new JSONArray(c.options)); put(o,"sourceIndex",c.sourceIndex); return o;
    }
    public static Column column(JSONObject o) {
        Column c=new Column(o.optString("name"),Kind.valueOf(o.optString("type","STRING")));
        c.key=o.optString("key",c.key); c.sourceIndex=o.optInt("sourceIndex",-1);
        JSONArray options=o.optJSONArray("options");
        if(options!=null) for(int i=0;i<options.length();i++) c.options.add(options.optString(i));
        return c;
    }
    public static JSONObject table(Table t) {
        JSONObject o=new JSONObject(); put(o,"id",t.id); put(o,"name",t.name); put(o,"description",t.description);
        put(o,"idMode",t.idMode.name()); put(o,"idName",t.idName); put(o,"nextId",t.nextId);
        put(o,"createdAt",t.createdAt); put(o,"updatedAt",t.updatedAt); put(o,"color",t.color);
        JSONArray a=new JSONArray(); for(Column c:t.columns) a.put(column(c)); put(o,"columns",a); return o;
    }
    public static Table table(JSONObject o) {
        Table t=new Table(); t.id=o.optString("id",t.id); t.name=o.optString("name"); t.description=o.optString("description");
        t.idMode=IdMode.valueOf(o.optString("idMode","NONE")); t.idName=o.optString("idName","id"); t.nextId=o.optLong("nextId",1);
        t.createdAt=o.optLong("createdAt",t.createdAt); t.updatedAt=o.optLong("updatedAt",t.createdAt); t.color=o.optInt("color",0);
        JSONArray a=o.optJSONArray("columns"); if(a!=null) for(int i=0;i<a.length();i++) t.columns.add(column(a.optJSONObject(i))); return t;
    }
    public static JSONObject values(Map<String,Object> values) {
        JSONObject o=new JSONObject(); for(Map.Entry<String,Object> e:values.entrySet()) put(o,e.getKey(),e.getValue()); return o;
    }
    public static LinkedHashMap<String,Object> values(JSONObject o) {
        LinkedHashMap<String,Object> out=new LinkedHashMap<>();
        if(o!=null) { Iterator<String> it=o.keys(); while(it.hasNext()) { String k=it.next(); out.put(k,o.isNull(k)?null:o.opt(k)); } }
        return out;
    }
    public static JSONObject history(Map<String,List<Revision>> history) {
        JSONObject o=new JSONObject();
        for(Map.Entry<String,List<Revision>> e:history.entrySet()) {
            JSONArray a=new JSONArray();
            for(Revision r:e.getValue()) { JSONObject p=new JSONObject(); put(p,"value",r.value); put(p,"at",r.at); a.put(p); }
            put(o,e.getKey(),a);
        }
        return o;
    }
    public static LinkedHashMap<String,List<Revision>> history(JSONObject o) {
        LinkedHashMap<String,List<Revision>> out=new LinkedHashMap<>();
        if(o==null) return out;
        Iterator<String> it=o.keys();
        while(it.hasNext()) {
            String k=it.next(); JSONArray a=o.optJSONArray(k); List<Revision> list=new ArrayList<>();
            if(a!=null) for(int i=0;i<a.length();i++) {
                JSONObject p=a.optJSONObject(i); if(p!=null) list.add(new Revision(p.isNull("value")?null:p.opt("value"),p.optLong("at")));
            }
            out.put(k,list);
        }
        return out;
    }
    public static String draft(Draft d) {
        JSONObject o=new JSONObject(); put(o,"tableId",d.tableId); put(o,"rowId",d.rowId); put(o,"externalId",d.externalId);
        put(o,"step",d.step); put(o,"baseVersion",d.baseVersion); put(o,"raw",new JSONObject(d.raw));
        put(o,"answered",new JSONArray(d.answered)); put(o,"nulls",new JSONArray(d.nulls)); put(o,"history",history(d.history)); return o.toString();
    }
    public static Draft draft(String source) {
        JSONObject o=object(source); Draft d=new Draft(); d.tableId=o.optString("tableId"); d.rowId=o.isNull("rowId")?null:o.optString("rowId",null);
        d.externalId=o.optString("externalId",""); d.step=o.optInt("step"); d.baseVersion=o.optInt("baseVersion");
        JSONObject raw=o.optJSONObject("raw"); if(raw!=null) { Iterator<String> it=raw.keys(); while(it.hasNext()) { String k=it.next(); d.raw.put(k,raw.optString(k)); } }
        JSONArray a=o.optJSONArray("answered"); if(a!=null) for(int i=0;i<a.length();i++) d.answered.add(a.optString(i));
        a=o.optJSONArray("nulls"); if(a!=null) for(int i=0;i<a.length();i++) d.nulls.add(a.optString(i));
        d.history=history(o.optJSONObject("history")); return d;
    }
    public static JSONObject exportSchema(Table t) {
        JSONObject result=new JSONObject(); put(result,"format","komirka"); put(result,"version",1);
        put(result,"exportedAt",java.time.Instant.now().toString()); put(result,"name",t.name); put(result,"description",t.description);
        JSONObject id=new JSONObject(); put(id,"mode",t.idMode.name().toLowerCase(Locale.ROOT)); put(id,"name",t.idName); put(result,"id",id);
        JSONArray columns=new JSONArray();
        for(Column c:t.columns) { JSONObject col=new JSONObject(); put(col,"name",c.name); put(col,"type",c.kind.code); if(c.kind.options()) put(col,"categories",new JSONArray(c.options)); columns.put(col); }
        put(result,"columns",columns); return result;
    }
}

