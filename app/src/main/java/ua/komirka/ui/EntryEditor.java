package ua.komirka.ui;

import android.app.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.time.LocalDate;
import java.util.*;
import ua.komirka.MainActivity;
import ua.komirka.core.Values;
import static ua.komirka.core.Models.*;
import static ua.komirka.ui.Design.*;

public final class EntryEditor {
    private final MainActivity a;
    private final Design u;
    private final Table table;
    public final Draft draft;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable save=this::flush;
    private boolean dirty=false, detached=false;
    private TextView error,status;
    private EditText input;
    private final int shift;
    public EntryEditor(MainActivity activity,Draft draft) {
        a=activity;u=a.ui;table=a.active;this.draft=draft;shift=table.idMode==IdMode.MANUAL?1:0;
    }
    public void render() {
        draft.step=Math.max(0,Math.min(draft.step,total()));
        a.page("entry",draft.rowId==null?"Новий запис":"Редагування запису",a::leaveEntry);
        input=null;error=null;status=null;
        if(draft.step==total()) {review();return;}
        LinearLayout meta=u.row();u.weight(meta,u.text("ПОЛЕ "+(draft.step+1)+" ІЗ "+total(),12,GREEN,true));meta.addView(u.chip("Чернетка",MUTED,BORDER));u.add(a.body,meta);
        ProgressBar progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(total()+1);progress.setProgress(draft.step+1);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));u.add(a.body,progress,14);
        boolean manual=shift==1&&draft.step==0;
        if(manual) {
            u.add(a.body,u.title(table.idName),26);u.add(a.body,u.muted("Унікальний ID · ваш текстовий код"),10);
            input=u.input("Наприклад, OBS-001",draft.externalId,InputType.TYPE_CLASS_TEXT);input.setSingleLine(true);u.watch(input,s->{draft.externalId=s;changed();});u.add(a.body,input,28);
            u.info(a.body,"ID обов’язковий і має бути унікальним у межах таблиці.");
            historyLink(a.body,"_id",null);
        } else {
            Column c=table.columns.get(draft.step-shift);
            u.add(a.body,u.title(c.name),26);u.add(a.body,u.muted(c.kind.title+" · "+c.kind.code),10);
            if(c.kind==Kind.BOOL) boolField(c);else if(c.kind.options()) categoryField(c);else textField(c);
            boolean isNull=draft.nulls.contains(c.key);
            Button skip=u.button(isNull?"✓  Немає значення (null)":"∅  Немає значення",false,()->{draft.answered.add(c.key);draft.nulls.add(c.key);draft.raw.remove(c.key);changed();render();});
            if(isNull)u.background(skip,PALE,16,GREEN);u.add(a.body,skip,18);
            u.add(a.body,u.muted("У CSV це буде порожня клітинка, у JSON — null."),8);
            historyLink(a.body,c.key,c);
        }
        error=u.error(a.body);status=u.muted("Чернетка зберігається на пристрої");u.add(a.body,status,14);
        a.footer.setVisibility(View.VISIBLE);LinearLayout actions=u.row();
        Button previous=u.button("← Назад",false,this::previous);u.weight(actions,previous);u.gap(actions,12);
        u.weight(actions,u.button(draft.step==total()-1?"Перевірити →":"Далі →",true,this::next));u.add(a.footer,actions);
        u.add(a.footer,u.quiet("Зберегти чернетку та вийти",()->{dirty=true;if(flush())a.leaveEntry();}),6);
        if(input!=null) {
            input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_NEXT||action==EditorInfo.IME_ACTION_DONE){next();return true;}return false;});
            if(manual || (table.columns.get(draft.step-shift).kind!=Kind.DATE && !draft.nulls.contains(table.columns.get(draft.step-shift).key))) a.keyboard(input);
        }
    }
    private int total() {return table.columns.size()+shift;}
    private void textField(Column c) {
        int type=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        String hint="Введіть значення…";
        if(c.kind==Kind.INT) {type=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED;hint="Наприклад, 42";}
        if(c.kind==Kind.FLOAT) {type=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL;hint="Наприклад, 3,14";}
        if(c.kind==Kind.DATE) {type=InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_DATE;hint="РРРР-ММ-ДД";}
        input=u.input(hint,draft.nulls.contains(c.key)?"":draft.raw.getOrDefault(c.key,""),type);
        if(c.kind==Kind.STRING) {input.setMinLines(3);input.setGravity(Gravity.TOP|Gravity.START);}
        else input.setSingleLine(true);
        if(c.kind==Kind.INT)input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789+-"));
        if(c.kind==Kind.FLOAT)input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789+-,.eE"));
        if(c.kind==Kind.INT||c.kind==Kind.FLOAT)input.setRawInputType(type);
        u.watch(input,s->{draft.raw.put(c.key,s);draft.nulls.remove(c.key);draft.answered.add(c.key);if(error!=null)error.setText("");changed();});u.add(a.body,input,26);
        if(c.kind==Kind.DATE) u.add(a.body,u.button("▦  Обрати в календарі",false,()->{
            LocalDate initial=LocalDate.now();try{if(!draft.raw.getOrDefault(c.key,"").isEmpty())initial=LocalDate.parse(draft.raw.get(c.key));}catch(Exception ignored){}
            new DatePickerDialog(a,(view,year,month,day)->input.setText(LocalDate.of(year,month+1,day).toString()),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();
        }),10);
    }
    private void boolField(Column c) {
        u.add(a.body,u.muted("Оберіть один варіант"),26);
        String selected=draft.nulls.contains(c.key)?"":draft.raw.getOrDefault(c.key,"");
        Button yes=u.button("✓  Так",false,()->select(c,"true"));yes.setTextSize(21);yes.setMinHeight(u.dp(84));yes.setTextColor("true".equals(selected)?WHITE:GREEN);u.background(yes,"true".equals(selected)?GREEN:PALE,18,"true".equals(selected)?GREEN:BORDER);yes.setContentDescription("Так"+("true".equals(selected)?", обрано":""));
        Button no=u.button("✕  Ні",false,()->select(c,"false"));no.setTextSize(21);no.setMinHeight(u.dp(84));no.setTextColor("false".equals(selected)?WHITE:RED);u.background(no,"false".equals(selected)?RED:RED_PALE,18,"false".equals(selected)?RED:BORDER);no.setContentDescription("Ні"+("false".equals(selected)?", обрано":""));
        LinearLayout buttons=u.row();u.weight(buttons,yes);u.gap(buttons,14);u.weight(buttons,no);u.add(a.body,buttons,14);
    }
    private void select(Column c,String value) {draft.raw.put(c.key,value);draft.nulls.remove(c.key);draft.answered.add(c.key);changed();render();}
    private void categoryField(Column c) {
        u.add(a.body,u.muted(c.kind==Kind.ORDINAL?"Від найнижчого рівня до найвищого":"Оберіть категорію"),24);
        LinearLayout options=u.column();
        if(c.options.size()>8) {EditText query=u.input("Знайти варіант…","",InputType.TYPE_CLASS_TEXT);u.add(a.body,query,12);u.watch(query,s->fillCategory(options,c,s));}
        u.add(a.body,options,12);fillCategory(options,c,"");
    }
    private void fillCategory(LinearLayout parent,Column c,String query) {
        parent.removeAllViews();int matching=0,shown=0;String selected=draft.nulls.contains(c.key)?null:draft.raw.get(c.key);
        for(int i=0;i<c.options.size();i++) {
            String option=c.options.get(i);if(!option.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))continue;matching++;if(shown++>=100)continue;
            boolean checked=option.equals(selected);String label=(checked?"✓  ":c.kind==Kind.ORDINAL?(i+1)+".  ":"")+option;
            Button button=u.button(label,checked,()->select(c,option));button.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);button.setContentDescription(option+(checked?", обрано":""));u.add(parent,button,8);
        }
        if(matching==0)u.add(parent,u.muted("Немає збігів. Спробуйте інший запит."),8);
        if(matching>100)u.add(parent,u.muted("Показано 100 із "+matching+". Уточніть пошук."),12);
    }
    public void previous() {
        if(draft.step==0) {a.leaveEntry();return;}
        draft.step--;dirty=true;flush();render();
    }
    private boolean validateCurrent() {
        if(shift==1&&draft.step==0) {
            if(draft.externalId.trim().isEmpty()){error.setText("Введіть унікальний ID.");return false;}return true;
        }
        if(draft.step>=total())return true;Column c=table.columns.get(draft.step-shift);
        if(!draft.answered.contains(c.key)){error.setText("Введіть значення або натисніть «Немає значення».");return false;}
        try{if(!draft.nulls.contains(c.key))Values.parse(c,draft.raw.get(c.key));return true;}
        catch(IllegalArgumentException ex){error.setText(ex.getMessage());if(input!=null)input.setError(ex.getMessage());return false;}
    }
    private void next() {if(!validateCurrent())return;draft.step++;dirty=true;flush();render();}
    private void review() {
        u.add(a.body,u.chip("ОСТАННІЙ КРОК · ЗБЕРЕЖЕННЯ",GREEN,PALE));u.add(a.body,u.title("Усе правильно?"),18);
        u.add(a.body,u.muted("Натисніть поле, щоб змінити значення. Запис буде збережено лише після підтвердження внизу."),12);
        if(table.idMode!=IdMode.NONE) {
            LinearLayout id=u.card();u.add(id,u.muted(table.idName+" · "+table.idMode.title));
            String value=table.idMode==IdMode.MANUAL?draft.externalId:draft.rowId!=null?Objects.requireNonNull(a.store.row(draft.rowId)).externalId:table.idMode==IdMode.INCREMENT?String.valueOf(table.nextId):"UUID буде створено після збереження";
            u.add(id,u.label(value),8);
            if(table.idMode==IdMode.MANUAL){u.add(id,u.quiet("Змінити ID",()->jump(0)),10);historyLink(id,"_id",null);}u.add(a.body,id,18);
        }
        for(int i=0;i<table.columns.size();i++) {
            final int step=i+shift;Column c=table.columns.get(i);LinearLayout card=u.card();LinearLayout header=u.row();u.weight(header,u.muted(c.name));header.addView(u.chip(c.kind.code,GREEN,PALE));u.add(card,header);
            Object value=null;boolean valid=true;String display;
            if(!draft.answered.contains(c.key)){display="Ще не заповнено";valid=false;}
            else {try{value=draft.nulls.contains(c.key)?null:Values.parse(c,draft.raw.get(c.key));display=Values.display(value);}catch(IllegalArgumentException ex){display="Перевірте: "+draft.raw.getOrDefault(c.key,"");valid=false;}}
            TextView text=u.text(display,18,valid?(value==null?MUTED:INK):RED,true);u.add(card,text,12);
            u.add(card,u.quiet("Змінити →",()->jump(step)),10);historyLink(card,c.key,c);u.clickable(card,()->jump(step));u.add(a.body,card,12);
        }
        if(draft.rowId!=null)u.info(a.body,"Коли збережете зміни, попередні значення залишаться в історії / aliases відповідних полів.");
        status=u.muted("Чернетка зберігається на пристрої");u.add(a.body,status,18);
        u.add(a.body,u.quiet("Зберегти чернетку та вийти",()->{dirty=true;if(flush())a.leaveEntry();}),12);
        if(draft.rowId!=null)u.add(a.body,u.danger("Видалити запис",()->a.confirm("Видалити запис?","Запис і вся його історія будуть видалені без можливості відновлення.","Видалити",()->{
            Row row=a.store.row(draft.rowId);if(row!=null)a.store.deleteRow(row);else a.store.clearDraft(table.id);detach();a.entry=null;a.showTable(table.id);a.toast("Запис видалено");
        })),12);
        a.bottom(draft.rowId==null?"Закінчити та додати до таблиці":"Закінчити та зберегти зміни",this::commit);
    }
    private void jump(int step) {draft.step=step;dirty=true;flush();render();}
    private void commit() {
        if(detached)return;
        try {
            Values.validateDraft(table,draft);a.store.commit(table,draft);detach();a.entry=null;a.showTable(table.id);a.toast(draft.rowId==null?"Запис додано до таблиці":"Зміни та історію збережено");
        }catch(Exception ex){a.error(ex.getMessage()==null?"Не вдалося зберегти запис. Чернетку залишено для повторної спроби.":ex.getMessage());}
    }
    private void historyLink(LinearLayout parent,String key,Column c) {
        List<Revision> values=draft.history.get(key);if(values==null||values.isEmpty())return;
        u.add(parent,u.quiet("Історія / aliases · "+values.size(),()->showHistory(key,c)),8);
    }
    private void showHistory(String key,Column c) {
        a.hideKeyboard();ScrollView scroll=new ScrollView(a);LinearLayout content=u.column();content.setPadding(u.dp(20),u.dp(12),u.dp(20),u.dp(16));scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Історія: "+(c==null?table.idName:c.name)).setView(scroll).setNegativeButton("Закрити",null).create();
        u.add(content,u.muted("Зміни історії буде застосовано разом із записом після фінального підтвердження."));
        List<Revision> revisions=draft.history.computeIfAbsent(key,k->new ArrayList<>());
        for(int i=0;i<revisions.size();i++) {
            Revision revision=revisions.get(i);LinearLayout card=u.card();u.add(card,u.label(Values.display(revision.value)));u.add(card,u.muted(a.date(revision.at)),8);
            u.add(card,u.button("Відновити значення",false,()->{
                if(c==null)draft.externalId=Values.raw(revision.value);else{draft.answered.add(key);draft.raw.put(key,Values.raw(revision.value));if(revision.value==null)draft.nulls.add(key);else draft.nulls.remove(key);}
                changed();dialog.dismiss();render();a.toast("Значення відновлено в чернетці");
            }),12);
            LinearLayout actions=u.row();u.weight(actions,u.quiet("Змінити alias",()->{dialog.dismiss();editAlias(key,c,revision);}));u.gap(actions,8);
            u.weight(actions,u.quiet("Видалити",()->{revisions.remove(revision);changed();dialog.dismiss();render();showHistory(key,c);}));u.add(card,actions,6);u.add(content,card,12);
        }
        if(revisions.isEmpty())u.add(content,u.muted("Історія порожня."),20);
        else u.add(content,u.danger("Очистити історію поля",()->a.confirm("Очистити історію?","Поточне значення залишиться. Видалення aliases застосовується після збереження запису.","Очистити",()->{revisions.clear();changed();dialog.dismiss();render();})),16);
        dialog.show();
    }
    private void editAlias(String key,Column c,Revision revision) {
        Column validator=c==null?new Column(table.idName,Kind.STRING):c;
        if(validator.kind.options()||validator.kind==Kind.BOOL) {
            List<String> labels=new ArrayList<>(),raw=new ArrayList<>();
            if(validator.kind==Kind.BOOL){labels.addAll(Arrays.asList("Так","Ні"));raw.addAll(Arrays.asList("true","false"));}
            else{labels.addAll(validator.options);raw.addAll(validator.options);}
            labels.add("Немає значення (null)");raw.add(null);
            new AlertDialog.Builder(a).setTitle("Змінити alias").setItems(labels.toArray(new String[0]),(dialog,which)->{
                revision.value=Values.parse(validator,raw.get(which));changed();render();showHistory(key,c);
            }).setNegativeButton("Скасувати",null).show();return;
        }
        LinearLayout box=u.column();box.setPadding(u.dp(20),u.dp(12),u.dp(20),0);
        EditText field=u.input("Значення alias",Values.raw(revision.value),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);u.add(box,field);TextView error=u.error(box);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Змінити alias").setView(box).setPositiveButton("Зберегти",null).setNegativeButton("Скасувати",null).setNeutralButton("null",(d,w)->{revision.value=null;changed();render();showHistory(key,c);}).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{revision.value=Values.parse(validator,field.getText().toString());changed();dialog.dismiss();render();showHistory(key,c);}catch(IllegalArgumentException ex){error.setText(ex.getMessage());}}));dialog.show();
    }
    private void changed() {dirty=true;if(status!=null)status.setText("Зберігаємо чернетку…");handler.removeCallbacks(save);handler.postDelayed(save,450);}
    public boolean flush() {
        handler.removeCallbacks(save);if(detached||!dirty)return true;
        try{a.store.saveDraft(draft);dirty=false;if(status!=null)status.setText("✓ Чернетку збережено");return true;}
        catch(Exception ex){if(status!=null)status.setText("Не вдалося зберегти чернетку");a.toast("Не вдалося зберегти чернетку. Перевірте вільне місце на пристрої.");return false;}
    }
    public void detach() {detached=true;handler.removeCallbacksAndMessages(null);}
}
