package ua.komirka;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import static ua.komirka.core.Models.*;
import static org.junit.Assert.*;

/** Drives the real Activity and native widgets in a JVM Android runtime. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="uk-rUA-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class UiFlowTest {
    private ActivityController<MainActivity> controller;
    private MainActivity a;
    @Before public void setup() {
        Context context=RuntimeEnvironment.getApplication();context.getSharedPreferences("ui",Context.MODE_PRIVATE).edit().clear().commit();context.deleteDatabase("komirka.db");
        controller=Robolectric.buildActivity(MainActivity.class).setup().visible();a=controller.get();idle();
    }
    @After public void cleanup(){if(controller!=null)controller.pause().stop().destroy();idle();}
    private void idle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));}
    private View root(){return a.findViewById(android.R.id.content);}
    private View find(View root,java.util.function.Predicate<View> match) {
        if(match.test(root))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),match);if(found!=null)return found;}}
        return null;
    }
    private View text(View root,String contains){View v=find(root,x->x instanceof TextView&&((TextView)x).getText().toString().contains(contains));assertNotNull("Missing text: "+contains,v);return v;}
    private void click(String value){View v=find(root(),x->x instanceof Button&&((Button)x).getText().toString().contains(value));assertNotNull("Missing button: "+value,v);assertTrue(v.performClick());idle();}
    private EditText field(String hint){View v=find(root(),x->x instanceof EditText&&String.valueOf(((EditText)x).getHint()).contains(hint));assertNotNull("Missing input: "+hint,v);return(EditText)v;}
    private void input(String hint,String value){field(hint).setText(value);idle();}
    private void waitUntil(BooleanSupplier condition) throws Exception {long deadline=System.currentTimeMillis()+8000;while(!condition.getAsBoolean()&&System.currentTimeMillis()<deadline){Thread.sleep(25);idle();}assertTrue("Background task did not complete",condition.getAsBoolean());}
    private Table example() {
        Table t=new Table();t.name="Польові спостереження";t.description="Щоденник дослідження · осінь 2026";t.idMode=IdMode.INCREMENT;
        Column title=new Column("Місце спостереження",Kind.STRING),count=new Column("Кількість зразків",Kind.INT),temp=new Column("Температура, °C",Kind.FLOAT),site=new Column("Середовище",Kind.CATEGORY),rank=new Column("Якість зразка",Kind.ORDINAL),done=new Column("Перевірено",Kind.BOOL);
        site.options.addAll(Arrays.asList("Ліс","Лука","Біля водойми"));rank.options.addAll(Arrays.asList("Низька","Середня","Висока"));t.columns.addAll(Arrays.asList(title,count,temp,site,rank,done));a.store.create(t);return t;
    }
    @Test public void firstLaunchHasClearEmptyStateAndNoFakeRecords() throws Exception {
        assertTrue(a.store.tables().isEmpty());text(root(),"Місце для ваших ідей");text(root(),"Імпортувати CSV");screenshot("01-home-empty");
    }
    @Test public void createsOrdinalSchemaThroughTheActualScreens() throws Exception {
        click("Створити таблицю");input("Польові спостереження","Щоденник самопочуття");text(root(),"Автоінкремент").performClick();idle();screenshot("02-table-settings");click("Далі: стовпці");click("Додати стовпець");input("Температура","Самопочуття");Spinner spinner=(Spinner)find(root(),v->v instanceof Spinner);spinner.setSelection(Kind.ORDINAL.ordinal());idle();
        for(String category:Arrays.asList("Погано","Звичайно","Чудово")){input("Нова категорія",category);View add=find(root(),v->"Додати категорію".contentEquals(v.getContentDescription()==null?"":v.getContentDescription()));assertNotNull(add);add.performClick();idle();}
        screenshot("03-ordinal-builder");click("Далі: зберегти стовпець");assertEquals(Arrays.asList("Погано","Звичайно","Чудово"),a.schema.table.columns.get(0).options);
        assertTrue(a.store.tables().isEmpty());click("Створити таблицю");waitUntil(()->a.screen.equals("table"));assertEquals(1,a.store.tables().size());assertEquals(IdMode.INCREMENT,a.active.idMode);
    }
    @Test public void fullEntryIsNotCommittedUntilFinalConfirmationAndEditsKeepAliases() throws Exception {
        Table t=example();a.showTable(t.id);a.newRecord();idle();input("Введіть значення","Озеро, північний берег");screenshot("04-text-entry");click("Далі →");input("Наприклад, 42","12");assertEquals(android.text.InputType.TYPE_CLASS_NUMBER,field("Наприклад, 42").getInputType()&android.text.InputType.TYPE_MASK_CLASS);click("Далі →");input("Наприклад, 3,14","18,7");assertEquals(android.text.InputType.TYPE_CLASS_NUMBER,field("Наприклад, 3,14").getInputType()&android.text.InputType.TYPE_MASK_CLASS);click("Далі →");click("Біля водойми");screenshot("05-category");click("Далі →");click("Висока");click("Далі →");click("Ні");screenshot("06-boolean");click("Перевірити →");screenshot("07-review");assertEquals(0,a.store.table(t.id).rowCount);
        click("Закінчити та додати");assertEquals(1,a.store.table(t.id).rowCount);Row row=a.store.rows(t.id,"",10,0).get(0);assertEquals(false,row.values.get(t.columns.get(5).key));assertEquals(18.7,row.values.get(t.columns.get(2).key));
        Draft d=a.store.editDraft(t,row);d.step=0;a.store.saveDraft(d);a.openDraft();input("Введіть значення","Озеро, східний берег");a.entry.draft.step=t.columns.size();a.entry.render();click("Закінчити та зберегти");row=a.store.row(row.id);assertEquals("Озеро, північний берег",row.history.get(t.columns.get(0).key).get(0).value);
        d=a.store.editDraft(t,row);d.step=t.columns.size();a.store.saveDraft(d);a.openDraft();click("Історія / aliases");AlertDialog history=ShadowAlertDialog.getLatestAlertDialog();View restore=text(history.getWindow().getDecorView(),"Відновити значення");restore.performClick();idle();assertEquals("Озеро, північний берег",a.entry.draft.raw.get(t.columns.get(0).key));click("Закінчити та зберегти");screenshot("08-records");
        a.showHome();idle();screenshot("09-home-with-table");
    }
    @Test public void draftSurvivesActivityRecreationWithoutCreatingARow() {
        Table t=example();a.showTable(t.id);a.newRecord();input("Введіть значення","Незавершений запис");controller.pause().stop().destroy();
        controller=Robolectric.buildActivity(MainActivity.class).setup().visible();a=controller.get();idle();assertEquals("entry",a.screen);assertEquals("Незавершений запис",field("Введіть значення").getText().toString());assertEquals(0,a.store.table(t.id).rowCount);
    }
    @Test public void nullAndBackNavigationRetainEarlierAnswers() {
        Table t=example();a.showTable(t.id);a.newRecord();input("Введіть значення","Річка");click("Далі →");click("Немає значення");click("Далі →");a.entry.previous();assertTrue(a.entry.draft.nulls.contains(t.columns.get(1).key));a.entry.previous();assertEquals("Річка",field("Введіть значення").getText().toString());assertEquals(0,a.store.table(t.id).rowCount);
    }
    @Test public void fileImportPreviewAndSystemExportResultWorkEndToEnd() throws Exception {
        File source=new File(a.getCacheDir(),"Спостереження.csv");
        try(Writer writer=new OutputStreamWriter(new FileOutputStream(source),java.nio.charset.StandardCharsets.UTF_8)){writer.write("\uFEFFКод;Вага;Перевірено\r\n001;1,25;так\r\n002;;ні\r\n");}
        a.onActivityResult(11,android.app.Activity.RESULT_OK,new Intent().setData(Uri.fromFile(source)));
        waitUntil(()->a.screen.equals("setup_basic"));assertEquals(Kind.STRING,a.schema.table.columns.get(0).kind);assertEquals(Kind.FLOAT,a.schema.table.columns.get(1).kind);assertEquals(Kind.BOOL,a.schema.table.columns.get(2).kind);
        click("Далі: стовпці");screenshot("10-import-preview");click("Імпортувати таблицю");waitUntil(()->a.screen.equals("table"));assertEquals(2,a.active.rowCount);
        click("Експорт");AlertDialog export=ShadowAlertDialog.getLatestAlertDialog();ListView choices=export.getListView();choices.performItemClick(choices.getAdapter().getView(0,null,choices),0,0);idle();
        Intent saveIntent=Shadows.shadowOf(a).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_CREATE_DOCUMENT,saveIntent.getAction());assertEquals("text/csv",saveIntent.getType());
        File target=new File(a.getCacheDir(),"export.csv");a.onActivityResult(12,android.app.Activity.RESULT_OK,new Intent().setData(Uri.fromFile(target)));
        waitUntil(()->"Файл успішно збережено".equals(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()));
        ua.komirka.core.Csv.Document result=ua.komirka.core.Csv.read(new String(java.nio.file.Files.readAllBytes(target.toPath()),java.nio.charset.StandardCharsets.UTF_8));assertEquals("001",result.rows.get(0).get(0));assertEquals("1.25",result.rows.get(0).get(1));assertEquals("",result.rows.get(1).get(1));assertEquals("false",result.rows.get(1).get(2));
    }
    private void screenshot(String name) throws Exception {
        View view=root();int width=786,height=1704;
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);idle();
        settleDrawables(view);Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(DesignColor());view.draw(canvas);
        File directory=new File(System.getProperty("komirka.screenshots"));assertTrue(directory.isDirectory()||directory.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
    private int DesignColor(){return 0xFFF6F8F7;}
    private void settleDrawables(View view){view.jumpDrawablesToCurrentState();if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)settleDrawables(group.getChildAt(i));}}
}
