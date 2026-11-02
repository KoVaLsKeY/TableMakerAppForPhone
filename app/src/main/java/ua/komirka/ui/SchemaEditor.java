package ua.komirka.ui;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;
import ua.komirka.MainActivity;
import ua.komirka.core.*;
import ua.komirka.data.Codec;
import static ua.komirka.core.Models.*;
import static ua.komirka.ui.Design.*;

public final class SchemaEditor {
    private final MainActivity a;
    private final Design u;
    public Table table;
    public Csv.Document imported;
    public boolean isImport=false;
    private String stage="basic";
    private Column editing;
    private int editingIndex=-1;
    private LinearLayout optionsPanel, optionsList;
    private EditText optionInput;
    private TextView columnError;
    private int optionsPage;
    public SchemaEditor(MainActivity activity) { a=activity;u=a.ui; }
    public void start() { clear();table=new Table();table.color=a.store.tables().size()%4;basics(); }
    public void clear() { table=null;imported=null;isImport=false;editing=null;stage="basic"; }
    public String snapshot() {
        if(table==null) return null;
        JSONObject o=new JSONObject(); Codec.put(o,"table",Codec.table(table));Codec.put(o,"import",isImport);Codec.put(o,"stage",stage);
        Codec.put(o,"editing",editing==null?null:Codec.column(editing));Codec.put(o,"index",editingIndex);return o.toString();
    }
    public void restore(String text) {
        JSONObject o=Codec.object(text);table=Codec.table(o.optJSONObject("table"));isImport=o.optBoolean("import");stage=o.optString("stage","basic");
        editing=o.isNull("editing")?null:Codec.column(o.optJSONObject("editing"));editingIndex=o.optInt("index",-1);
    }
    public void render() {
        if(table==null) {start();return;}
        if(isImport&&imported==null){a.resumeImportSchema();return;}
        if(stage.equals("column")&&editing!=null) column();else if(stage.equals("columns"))columns();else basics();
    }
    public void fromCsv(Csv.Document doc,String filename) {
        clear();isImport=true;imported=doc;table=new Table();table.color=a.store.tables().size()%4;
        table.name=filename.replaceFirst("(?i)\\.csv$",""); if(table.name.length()>120)table.name=table.name.substring(0,120);
        for(int i=0;i<doc.headers.size();i++) {
            List<String> values=new ArrayList<>();for(List<String> row:doc.rows) values.add(row.get(i));
            Column c=new Column(doc.headers.get(i),Values.infer(values));c.sourceIndex=i;table.columns.add(c);
        }
        basics();
    }
    public void basics() {
        stage="basic";a.page("setup_basic",isImport?"Import CSV":"New table",a::showHome);
        u.add(a.body,u.chip("STEP 1 OF 2 · DETAILS",GREEN,PALE));
        u.add(a.body,u.title(isImport?"Set up your\nimported table":"Give your table a name"),18);
        u.add(a.body,u.muted(isImport?"Check the table name. You can review the column types in the next step.":"What will you collect? You can change the name and description later."),12);
        u.section(a.body,"Table name",null);
        EditText name=u.input("For example, Field observations",table.name,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setMaxLines(2);u.watch(name,s->table.name=s);u.add(a.body,name,10);
        u.section(a.body,"Description",null);EditText description=u.input("Optional",table.description,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        description.setMinLines(2);u.watch(description,s->table.description=s);u.add(a.body,description,10);
        if(isImport && imported!=null) u.info(a.body,"Found "+Values.records(imported.rows.size())+" and "+Values.columns(imported.headers.size())+".\nThe first row supplies the column names. Empty cells are treated as missing values.");
        u.section(a.body,"Record IDs","Choose how to identify each record.");
        RadioGroup modes=new RadioGroup(a);modes.setOrientation(RadioGroup.VERTICAL);
        LinearLayout idField=u.column();EditText idName=u.input("ID column name",table.idName,InputType.TYPE_CLASS_TEXT);u.watch(idName,s->table.idName=s);u.add(idField,idName);
        for(IdMode mode:IdMode.values()) {
            if(isImport&&mode==IdMode.MANUAL)continue;
            RadioButton radio=new RadioButton(a);radio.setId(View.generateViewId());radio.setText(mode.title+"\n"+mode.description);radio.setTextSize(15);radio.setTextColor(INK);radio.setPadding(u.dp(8),u.dp(12),u.dp(12),u.dp(12));radio.setMinHeight(u.dp(64));radio.setTag(mode);
            modes.addView(radio,new RadioGroup.LayoutParams(-1,-2));radio.setChecked(table.idMode==mode);
        }
        modes.setOnCheckedChangeListener((group,id)->{RadioButton radio=group.findViewById(id);if(radio!=null) {table.idMode=(IdMode)radio.getTag();idField.setVisibility(table.idMode==IdMode.NONE?View.GONE:View.VISIBLE);}});
        u.add(a.body,modes,10);u.add(a.body,idField,10);idField.setVisibility(table.idMode==IdMode.NONE?View.GONE:View.VISIBLE);
        if(isImport)u.info(a.body,"An existing CSV ID stays a regular column. An automatic ID adds a separate column and needs a different name.");
        TextView error=u.error(a.body);
        a.bottom("Next: columns →",()->{
            table.name=table.name.trim();table.description=table.description.trim();table.idName=table.idName.trim();
            if(table.name.isEmpty()||table.name.length()>120) {error.setText("Enter a table name with 1 to 120 characters.");name.requestFocus();return;}
            try {if(table.idMode!=IdMode.NONE)Values.validateName(table.idName);columns();}catch(IllegalArgumentException ex){error.setText(ex.getMessage());}
        });
    }
    public void columns() {
        stage="columns";editing=null;a.page("setup_columns",isImport?"Review import":"New table",this::basics);
        u.add(a.body,u.chip("STEP 2 OF 2 · COLUMNS",GREEN,PALE));u.add(a.body,u.title("Choose your columns"),18);
        u.add(a.body,u.muted(isImport?"Types were detected automatically. Tap a column to check or change its type.":"Add the fields you need. You will fill them in using this order."),12);
        if(table.idMode!=IdMode.NONE)u.info(a.body,table.idName+" · "+table.idMode.title);
        if(table.columns.isEmpty()) {
            LinearLayout empty=u.card();u.add(empty,u.label("One column for each field"));u.add(empty,u.muted("For example: Name as text, Quantity as an integer, and Status as a category."),10);u.add(a.body,empty,22);
        }
        for(int index=0;index<table.columns.size();index++) {
            final int position=index;Column c=table.columns.get(index);LinearLayout card=u.card(),heading=u.row();
            TextView badge=u.badge(c.kind.mark,index);heading.addView(badge,new LinearLayout.LayoutParams(u.dp(44),u.dp(44)));u.gap(heading,12);
            LinearLayout labels=u.column();u.add(labels,u.label(c.name));u.add(labels,u.muted(c.kind.title+" · "+c.kind.code),5);u.weight(heading,labels);
            heading.addView(u.icon("✎","Edit “"+c.name+"”",()->edit(position)),new LinearLayout.LayoutParams(u.dp(48),-2));u.add(card,heading);
            if(c.kind.options())u.add(card,u.muted(String.join(c.kind==Kind.ORDINAL?" → ":" · ",c.options)),12);
            if(isImport&&imported!=null&&c.sourceIndex>=0) {
                List<String> preview=new ArrayList<>();for(int r=0;r<Math.min(3,imported.rows.size());r++) {String value=imported.rows.get(r).get(c.sourceIndex);preview.add(value.isEmpty()?"null":value);}
                u.add(card,u.muted("Preview: "+String.join(" · ",preview)),10);
            }
            LinearLayout tools=u.row();u.weight(tools,u.muted("Field "+(index+1)));
            Button up=u.icon("↑","Move “"+c.name+"” up",()->{Collections.swap(table.columns,position,position-1);columns();});up.setEnabled(index>0);tools.addView(up,new LinearLayout.LayoutParams(u.dp(48),-2));
            Button down=u.icon("↓","Move “"+c.name+"” down",()->{Collections.swap(table.columns,position,position+1);columns();});down.setEnabled(index<table.columns.size()-1);tools.addView(down,new LinearLayout.LayoutParams(u.dp(48),-2));
            tools.addView(u.icon("×","Remove “"+c.name+"”",()->a.confirm("Remove this column?",c.name+(isImport?" will not be imported.":" will be removed from the table setup."),"Remove",()->{table.columns.remove(position);columns();})),new LinearLayout.LayoutParams(u.dp(48),-2));
            u.add(card,tools,10);u.clickable(card,()->edit(position));u.add(a.body,card,14);
        }
        u.add(a.body,u.button("＋  Add column",false,()->{editingIndex=-1;editing=new Column();column();}),18);
        if(isImport)u.info(a.body,"Set categories in the column settings. Arrange ordered categories from lowest to highest. New columns start with missing values in imported records.");
        else u.info(a.body,"Start with the fields you need. You can leave a value missing when entering a record.");
        a.bottom(isImport?"Import table":"Create table",this::create);
    }
    private void edit(int index) { editingIndex=index;editing=table.columns.get(index).copy();column(); }
    private void column() {
        stage="column";a.page("setup_column",editingIndex<0?"New column":"Column settings",this::columns);
        u.add(a.body,u.title("Define your field"));
        u.section(a.body,"Column name",null);EditText name=u.input("For example, Temperature",editing.name,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);u.watch(name,s->editing.name=s);u.add(a.body,name,10);
        u.section(a.body,"Data type","Choose the format that fits this field.");
        Spinner type=new Spinner(a);List<String> names=new ArrayList<>();for(Kind kind:Kind.values())names.add(kind.title+"  ·  "+kind.code);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);type.setAdapter(adapter);type.setSelection(editing.kind.ordinal());type.setMinimumHeight(u.dp(56));u.background(type,WHITE,14,BORDER);type.setContentDescription("Column data type");u.add(a.body,type,10);
        TextView hint=u.muted(editing.kind.description);u.add(a.body,hint,10);
        optionsPanel=u.column();u.add(a.body,optionsPanel,16);
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long id) {
                editing.kind=Kind.values()[position];hint.setText(editing.kind.description);
                if(editing.kind.options() && editing.options.isEmpty() && imported!=null && editing.sourceIndex>=0) {
                    LinkedHashSet<String> unique=new LinkedHashSet<>();for(List<String> row:imported.rows) {String s=row.get(editing.sourceIndex);if(!s.trim().isEmpty())unique.add(s);}
                    editing.options.addAll(unique);
                }
                renderOptions();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        renderOptions();columnError=u.error(a.body);
        a.bottom("Save column",()->{
            try {
                if(editing.kind.options() && optionInput!=null && !optionInput.getText().toString().trim().isEmpty()) if(!addOption()) return;
                editing.name=editing.name.trim();Values.validateName(editing.name);
                if(table.idMode!=IdMode.NONE && editing.name.equalsIgnoreCase(table.idName))throw new IllegalArgumentException("The ID column already uses this name.");
                for(int i=0;i<table.columns.size();i++)if(i!=editingIndex && table.columns.get(i).name.equalsIgnoreCase(editing.name))throw new IllegalArgumentException("A column with this name already exists.");
                if(editing.kind.options()&&editing.options.isEmpty())throw new IllegalArgumentException("Add at least one category.");
                if(editingIndex<0) {if(table.columns.size()>=100)throw new IllegalArgumentException("You can add up to 100 columns.");table.columns.add(editing.copy());}
                else table.columns.set(editingIndex,editing.copy());columns();
            }catch(IllegalArgumentException ex){columnError.setText(ex.getMessage());}
        });
    }
    private void renderOptions() {
        optionsPanel.removeAllViews();optionInput=null;optionsPage=0;
        if(!editing.kind.options()) {
            String info=editing.kind==Kind.BOOL?"Choose between two buttons when entering a record: Yes and No.":editing.kind==Kind.STRING?"The keyboard opens automatically. Use No value to leave a field empty.":editing.kind==Kind.DATE?"Choose a date from the calendar or enter it as YYYY-MM-DD.":"A number keyboard opens automatically. The value is checked before you continue.";
            u.info(optionsPanel,info);return;
        }
        u.add(optionsPanel,u.label(editing.kind==Kind.ORDINAL?"Categories from lowest to highest":"Your categories"));
        u.add(optionsPanel,u.muted(editing.kind==Kind.ORDINAL?"The first option is the lowest level. Use the arrows to change the order.":"Add one option at a time, then tap Save column."),8);
        LinearLayout add=u.row();optionInput=u.input("New category","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);optionInput.setSingleLine(true);u.weight(add,optionInput);u.gap(add,8);add.addView(u.icon("＋","Add category",this::addOption),new LinearLayout.LayoutParams(u.dp(54),-2));u.add(optionsPanel,add,14);
        optionInput.setOnEditorActionListener((v,id,event)->{addOption();return true;});
        optionsList=u.column();u.add(optionsPanel,optionsList,12);fillOptions();
    }
    private boolean addOption() {
        String value=optionInput.getText().toString().trim();
        if(value.isEmpty()) {optionInput.setError("Enter a category name");return false;}
        if(editing.options.contains(value)) {optionInput.setError("This category already exists");return false;}
        editing.options.add(value);optionsPage=(editing.options.size()-1)/40;optionInput.setText("");fillOptions();return true;
    }
    private void fillOptions() {
        optionsList.removeAllViews();
        optionsPage=Math.min(optionsPage,Math.max(0,(editing.options.size()-1)/40));
        int from=optionsPage*40,to=Math.min(from+40,editing.options.size());
        if(editing.options.size()>40)u.add(optionsList,u.muted("Categories "+(from+1)+"–"+to+" of "+editing.options.size()),8);
        for(int i=from;i<to;i++) {
            final int index=i;LinearLayout row=u.row();u.background(row,WHITE,12,BORDER);row.setPadding(u.dp(12),u.dp(4),u.dp(4),u.dp(4));
            u.weight(row,u.text((editing.kind==Kind.ORDINAL?(i+1)+".  ":"")+editing.options.get(i),15,INK,false));
            if(editing.kind==Kind.ORDINAL) {
                Button up=u.icon("↑","Move category up",()->{Collections.swap(editing.options,index,index-1);fillOptions();});up.setEnabled(i>0);row.addView(up,new LinearLayout.LayoutParams(u.dp(48),-2));
                Button down=u.icon("↓","Move category down",()->{Collections.swap(editing.options,index,index+1);fillOptions();});down.setEnabled(i<editing.options.size()-1);row.addView(down,new LinearLayout.LayoutParams(u.dp(48),-2));
            }
            row.addView(u.icon("×","Remove category “"+editing.options.get(i)+"”",()->{editing.options.remove(index);fillOptions();}),new LinearLayout.LayoutParams(u.dp(48),-2));u.add(optionsList,row,6);
        }
        if(editing.options.size()>40) {
            LinearLayout pages=u.row();Button prev=u.button("← Previous",false,()->{optionsPage--;fillOptions();});prev.setEnabled(optionsPage>0);u.weight(pages,prev);u.gap(pages,8);
            Button next=u.button("Next →",false,()->{optionsPage++;fillOptions();});next.setEnabled(to<editing.options.size());u.weight(pages,next);u.add(optionsList,pages,12);
        }
    }
    private void create() {
        try {
            Values.validate(table);if(isImport&&imported==null)throw new IllegalArgumentException("Choose the CSV file again.");
            Table copy=Codec.table(Codec.table(table));Csv.Document doc=imported;
            a.startJob(isImport?"Importing records…":"Creating table…","create",()->{if(isImport)a.store.importCsv(copy,doc);else a.store.create(copy);return copy;});
        }catch(IllegalArgumentException ex){a.error(ex.getMessage());}
    }
}
