package ua.komirka.data;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import ua.komirka.core.*;
import static ua.komirka.core.Models.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Transactional private SQLite storage. Drafts and committed records are separate tables. */
public final class Store extends SQLiteOpenHelper {
    public Store(Context context) { super(context,"komirka.db",null,1); setWriteAheadLoggingEnabled(true); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE datasets (id TEXT PRIMARY KEY, definition TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY, table_id TEXT NOT NULL REFERENCES datasets(id) ON DELETE CASCADE, external_id TEXT, data TEXT NOT NULL, history TEXT NOT NULL, search_text TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, version INTEGER NOT NULL DEFAULT 1, UNIQUE(table_id,external_id))");
        db.execSQL("CREATE INDEX records_by_table ON records(table_id,created_at DESC)");
        db.execSQL("CREATE TABLE drafts (table_id TEXT PRIMARY KEY REFERENCES datasets(id) ON DELETE CASCADE, data TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        throw new IllegalStateException("Непідтримувана версія сховища; дані залишено без змін.");
    }
    public List<Table> tables() {
        List<Table> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT d.definition,(SELECT count(*) FROM records r WHERE r.table_id=d.id) FROM datasets d ORDER BY updated_at DESC",null)) {
            while(c.moveToNext()) { Table t=Codec.table(Codec.object(c.getString(0))); t.rowCount=c.getLong(1); result.add(t); }
        }
        return result;
    }
    public Table table(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT definition,(SELECT count(*) FROM records WHERE table_id=?) FROM datasets WHERE id=?",new String[]{id,id})) {
            if(!c.moveToFirst()) return null;
            Table t=Codec.table(Codec.object(c.getString(0))); t.rowCount=c.getLong(1); return t;
        }
    }
    public void create(Table table) {
        Values.validate(table); ContentValues cv=tableValues(table); getWritableDatabase().insertOrThrow("datasets",null,cv);
    }
    private ContentValues tableValues(Table t) {
        ContentValues cv=new ContentValues(); cv.put("id",t.id); cv.put("definition",Codec.table(t).toString()); cv.put("updated_at",t.updatedAt); return cv;
    }
    public void rename(Table t,String name,String description) {
        t.name=name.trim(); t.description=description.trim(); Values.validate(t); t.updatedAt=System.currentTimeMillis();
        getWritableDatabase().update("datasets",tableValues(t),"id=?",new String[]{t.id});
    }
    public void deleteTable(String id) { getWritableDatabase().delete("datasets","id=?",new String[]{id}); }
    public Draft draft(String tableId) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM drafts WHERE table_id=?",new String[]{tableId})) { return c.moveToFirst()?Codec.draft(c.getString(0)):null; }
    }
    public void saveDraft(Draft d) {
        ContentValues cv=new ContentValues(); cv.put("table_id",d.tableId); cv.put("data",Codec.draft(d));
        getWritableDatabase().insertWithOnConflict("drafts",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void clearDraft(String id) { getWritableDatabase().delete("drafts","table_id=?",new String[]{id}); }
    public Row row(String id) {
        try(Cursor c=getReadableDatabase().query("records",null,"id=?",new String[]{id},null,null,null)) { return c.moveToFirst()?readRow(c):null; }
    }
    private Row readRow(Cursor c) {
        Row r=new Row(); r.id=c.getString(c.getColumnIndexOrThrow("id")); r.tableId=c.getString(c.getColumnIndexOrThrow("table_id"));
        r.externalId=c.getString(c.getColumnIndexOrThrow("external_id"));
        r.values=Codec.values(Codec.object(c.getString(c.getColumnIndexOrThrow("data"))));
        r.history=Codec.history(Codec.object(c.getString(c.getColumnIndexOrThrow("history"))));
        r.createdAt=c.getLong(c.getColumnIndexOrThrow("created_at")); r.updatedAt=c.getLong(c.getColumnIndexOrThrow("updated_at")); r.version=c.getInt(c.getColumnIndexOrThrow("version")); return r;
    }
    public List<Row> rows(String tableId,String query,int limit,int offset) {
        List<Row> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("records",null,"table_id=? AND search_text LIKE ? ESCAPE '\\'",new String[]{tableId,pattern(query)},null,null,"created_at DESC,rowid DESC",offset+","+limit)) { while(c.moveToNext()) result.add(readRow(c)); }
        return result;
    }
    private String pattern(String query) { return "%"+query.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; }
    public long countRows(String tableId,String query) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM records WHERE table_id=? AND search_text LIKE ? ESCAPE '\\'",new String[]{tableId,pattern(query)})) { c.moveToFirst(); return c.getLong(0); }
    }
    public Draft editDraft(Table t,Row row) {
        Draft d=new Draft(); d.tableId=t.id; d.rowId=row.id; d.externalId=Values.raw(row.externalId); d.baseVersion=row.version;
        for(Column c:t.columns) { Object value=row.values.get(c.key); d.raw.put(c.key,Values.raw(value)); d.answered.add(c.key); if(value==null) d.nulls.add(c.key); }
        d.history=Codec.history(Codec.history(row.history)); return d;
    }
    public Row commit(Table passedTable,Draft d) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            Table t=table(passedTable.id);
            if(t==null || !t.id.equals(d.tableId)) throw new IllegalArgumentException("Таблицю не знайдено.");
            Map<String,Object> values=Values.validateDraft(t,d); Row old=d.rowId==null?null:row(d.rowId);
            if(d.rowId!=null && (old==null || old.version!=d.baseVersion || !old.tableId.equals(t.id)))
                throw new IllegalArgumentException("Запис змінився або його видалено. Відкрийте актуальний запис знову.");
            Row r=old==null?new Row():old; r.tableId=t.id;
            r.history=Codec.history(Codec.history(d.history));
            if(old!=null) {
                for(Column c:t.columns) if(!Values.same(old.values.get(c.key),values.get(c.key)))
                    r.history.computeIfAbsent(c.key,k->new ArrayList<>()).add(0,new Revision(old.values.get(c.key),System.currentTimeMillis()));
                if(t.idMode==IdMode.MANUAL && !Objects.equals(old.externalId,d.externalId.trim()))
                    r.history.computeIfAbsent("_id",k->new ArrayList<>()).add(0,new Revision(old.externalId,System.currentTimeMillis()));
            }
            r.values=new LinkedHashMap<>(values);
            if(t.idMode==IdMode.MANUAL) r.externalId=d.externalId.trim();
            else if(old==null) {
                if(t.idMode==IdMode.AUTO) r.externalId=UUID.randomUUID().toString();
                if(t.idMode==IdMode.INCREMENT) {
                    if(t.nextId==Long.MAX_VALUE) throw new IllegalArgumentException("Діапазон автоінкременту вичерпано.");
                    r.externalId=String.valueOf(t.nextId++);
                }
            }
            r.updatedAt=System.currentTimeMillis(); r.version++; writeRow(db,r,old!=null);
            t.updatedAt=r.updatedAt; db.update("datasets",tableValues(t),"id=?",new String[]{t.id});
            clearDraft(t.id); db.setTransactionSuccessful(); return r;
        } catch(SQLiteConstraintException e) { throw new IllegalArgumentException("Такий ID уже існує. Введіть інший унікальний ID.",e); }
        finally { db.endTransaction(); }
    }
    private void writeRow(SQLiteDatabase db,Row r,boolean update) {
        ContentValues v=new ContentValues(); v.put("id",r.id); v.put("table_id",r.tableId); v.put("external_id",r.externalId);
        v.put("data",Codec.values(r.values).toString()); v.put("history",Codec.history(r.history).toString());
        StringBuilder search=new StringBuilder(Values.raw(r.externalId));
        for(Object value:r.values.values()) search.append(' ').append(Values.display(value));
        v.put("search_text",search.toString().toLowerCase(Locale.ROOT)); v.put("created_at",r.createdAt); v.put("updated_at",r.updatedAt); v.put("version",r.version);
        if(update) db.update("records",v,"id=?",new String[]{r.id}); else db.insertOrThrow("records",null,v);
    }
    public void deleteRow(Row r) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("records","id=?",new String[]{r.id}); Draft d=draft(r.tableId);
            if(d!=null && r.id.equals(d.rowId)) clearDraft(r.tableId);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void importCsv(Table t,Csv.Document csv) {
        Values.validate(t); SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            create(t); int index=1;
            for(List<String> cells:csv.rows) {
                index++; Row r=new Row(); r.tableId=t.id; r.version=1;
                for(Column c:t.columns) {
                    try { r.values.put(c.key,c.sourceIndex<0?null:Values.parse(c,cells.get(c.sourceIndex))); }
                    catch(IllegalArgumentException ex) { throw new IllegalArgumentException("CSV-рядок "+index+", «"+c.name+"»: "+ex.getMessage()); }
                }
                if(t.idMode==IdMode.AUTO) r.externalId=UUID.randomUUID().toString();
                if(t.idMode==IdMode.INCREMENT) r.externalId=String.valueOf(t.nextId++);
                if(t.idMode==IdMode.MANUAL) throw new IllegalArgumentException("Під час імпорту залиште ID звичайним стовпцем або оберіть автоматичний ID.");
                writeRow(db,r,false);
            }
            db.update("datasets",tableValues(t),"id=?",new String[]{t.id}); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void export(Table t,Writer writer,boolean json) throws IOException {
        SQLiteDatabase db=getReadableDatabase(); db.beginTransactionNonExclusive();
        try(Cursor c=db.query("records",null,"table_id=?",new String[]{t.id},null,null,"created_at,rowid")) {
            if(!json) {
                writer.write('\uFEFF'); List<String> headers=new ArrayList<>(); if(t.idMode!=IdMode.NONE) headers.add(t.idName);
                for(Column col:t.columns) headers.add(col.name); Csv.writeRow(writer,headers);
                while(c.moveToNext()) { Row r=readRow(c); List<Object> cells=new ArrayList<>(); if(t.idMode!=IdMode.NONE) cells.add(r.externalId); for(Column col:t.columns) cells.add(r.values.get(col.key)); Csv.writeRow(writer,cells); }
            } else {
                String head=Codec.exportSchema(t).toString(); writer.write(head.substring(0,head.length()-1)); writer.write(",\"rows\":["); boolean first=true;
                while(c.moveToNext()) {
                    if(!first) writer.write(','); first=false; Row r=readRow(c); JSONObject out=new JSONObject(), values=new JSONObject(), history=new JSONObject();
                    if(t.idMode!=IdMode.NONE) Codec.put(values,t.idName,t.idMode==IdMode.INCREMENT?Long.parseLong(r.externalId):r.externalId);
                    for(Column col:t.columns) Codec.put(values,col.name,r.values.get(col.key));
                    JSONObject rawHistory=Codec.history(r.history);
                    for(Column col:t.columns) if(rawHistory.has(col.key)) Codec.put(history,col.name,rawHistory.optJSONArray(col.key));
                    if(t.idMode!=IdMode.NONE && rawHistory.has("_id")) Codec.put(history,t.idName,rawHistory.optJSONArray("_id"));
                    Codec.put(out,"values",values); Codec.put(out,"aliases",history); Codec.put(out,"createdAt",java.time.Instant.ofEpochMilli(r.createdAt).toString()); Codec.put(out,"updatedAt",java.time.Instant.ofEpochMilli(r.updatedAt).toString());
                    writer.write(out.toString());
                }
                writer.write("]}");
            }
            writer.flush(); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
