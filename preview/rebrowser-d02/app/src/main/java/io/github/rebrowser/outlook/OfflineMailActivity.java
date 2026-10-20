package io.github.rebrowser.outlook;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import io.github.rebrowser.ui.BrowserToolbar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native offline-mail WINDOW SHELL over one already-filtered page. It never queries the database,
 * decrypts, retries the network, saves/exports attachments or renders mail HTML: the body is
 * bounded PLAIN display text supplied by the native owner. Search/attachment/open are INTENT.
 */
public final class OfflineMailActivity extends Activity {
    public static final int MAX_ROWS = 100;
    public static final int MAX_TEXT_CHARS = 4096;
    public static final int MAX_SEARCH_CHARS = 64;

    public static final class MailRow {
        private final Object token;private final String subject,sender,dateText,preview;
        private final boolean attachmentPresent;
        public MailRow(Object token,String subject,String sender,String dateText,String preview,boolean attachmentPresent){
            if(token==null)throw new IllegalArgumentException("Missing mail token");
            this.token=token;this.subject=text(subject,512);this.sender=text(sender,256);
            this.dateText=text(dateText,64);this.preview=text(preview,512);this.attachmentPresent=attachmentPresent;
        }
        public Object token(){return token;}
        public String subject(){return subject;}
        public String sender(){return sender;}
        public String dateText(){return dateText;}
        public String preview(){return preview;}
        public boolean attachmentPresent(){return attachmentPresent;}
    }
    public static final class Page {
        private final List<MailRow> rows;private final long revision;
        public Page(List<MailRow> rows,long revision){
            if(rows==null || rows.size()>MAX_ROWS || revision<1)throw new IllegalArgumentException("Invalid offline mail page");
            Set<Object> tokens=Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            List<MailRow> copy=new ArrayList<>();
            for(MailRow row:rows){if(row==null)throw new IllegalArgumentException("Missing mail row");if(!tokens.add(row.token()))throw new IllegalArgumentException("Duplicate mail token");copy.add(row);}
            this.rows=Collections.unmodifiableList(copy);this.revision=revision;
        }
        public List<MailRow> rows(){return rows;}
        public long revision(){return revision;}
    }
    public interface Actions {
        void open(MailRow row,long expectedRevision);
        void search(String literal,int maxResults,long expectedRevision);
        void attachment(MailRow row,long expectedRevision);
        void loadMore(long expectedRevision);
    }

    private TextView status;
    private EditText search;
    private ListView list;
    private final Adapter adapter=new Adapter();
    private BrowserToolbar.Binding binding;private Page page;private Actions actions;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        status=new TextView(this);
        status.setText("未连接原生离线邮件窗口");
        root.addView(status);
        search=new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索本机离线邮件（字面匹配）");
        search.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_SEARCH_CHARS)});
        root.addView(search,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        list=new ListView(this);
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
    }

    public void showPage(BrowserToolbar.Binding binding,Page page,Actions actions){
        ownerThread();
        if(binding==null || page==null || actions==null)throw new IllegalArgumentException("Missing native offline mail binding");
        binding.check();
        this.binding=binding;this.page=page;this.actions=actions;
        adapter.update(page);
        status.setText(page.rows().isEmpty()?"没有离线邮件":"离线邮件为只读展示；正文仅纯文本");
        search.setOnEditorActionListener((view,action,event)->{
            String literal=search.getText().toString().trim();
            if(literal.isEmpty() || literal.length()>MAX_SEARCH_CHARS)return false;
            BrowserToolbar.Binding captured=this.binding;
            if(captured==null)return false;
            BrowserToolbar.invokeNativeIntent(()->{captured.check();actions.search(literal,50,page.revision());});
            return true;
        });
    }

    public void showUnavailable(String reason){
        ownerThread();
        binding=null;page=null;actions=null;adapter.update(null);
        status.setText(reason==null || reason.isEmpty()?"未连接原生离线邮件窗口":"离线邮件不可用：" + reason);
    }

    /** Plain-text body projection; never HTML and never a live account view. */
    public void showBodyText(String subject,String plainText){
        ownerThread();
        if(subject==null || plainText==null)throw new IllegalArgumentException("Missing body text");
        status.setText(text(subject,512)+"\n"+text(plainText,64*1024));
    }

    @Override protected void onDestroy(){adapter.update(null);binding=null;page=null;actions=null;super.onDestroy();}

    private final class Adapter extends BaseAdapter {
        private List<MailRow> rows=Collections.emptyList();
        void update(Page page){rows=page==null?Collections.emptyList():page.rows();notifyDataSetChanged();}
        @Override public int getCount(){return rows.size();}
        @Override public MailRow getItem(int position){return rows.get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            ownerThread();
            LinearLayout row;
            TextView title,detail;
            Button attach,open;
            if(recycled instanceof LinearLayout && recycled.getTag()!=null){
                row=(LinearLayout)recycled;
                @SuppressWarnings("unchecked") View[] parts=(View[])recycled.getTag();
                title=(TextView)parts[0];detail=(TextView)parts[1];attach=(Button)parts[2];open=(Button)parts[3];
            }else{
                row=new LinearLayout(OfflineMailActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                title=new TextView(OfflineMailActivity.this);title.setTextSize(16);
                detail=new TextView(OfflineMailActivity.this);detail.setTextSize(13);
                LinearLayout buttons=new LinearLayout(OfflineMailActivity.this);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                attach=new Button(OfflineMailActivity.this);attach.setText("附件");attach.setMinimumHeight(dp(48));
                open=new Button(OfflineMailActivity.this);open.setText("打开");open.setMinimumHeight(dp(48));
                buttons.addView(attach,new LinearLayout.LayoutParams(0,dp(48),1));
                buttons.addView(open,new LinearLayout.LayoutParams(0,dp(48),1));
                row.addView(title);row.addView(detail);row.addView(buttons);
                row.setTag(new View[]{title,detail,attach,open});
            }
            if(position<0 || position>=rows.size()){
                title.setText("");detail.setText("");attach.setOnClickListener(null);open.setOnClickListener(null);
                attach.setEnabled(false);open.setEnabled(false);
                return row;
            }
            MailRow item=rows.get(position);
            Page currentPage=page;
            title.setText(item.subject());detail.setText(item.sender()+" · "+item.dateText()+" · "+item.preview());
            attach.setEnabled(item.attachmentPresent() && binding!=null);
            attach.setContentDescription("查看附件：" + item.subject());
            attach.setOnClickListener(v->dispatch(item,currentPage,true));
            open.setContentDescription("打开离线邮件：" + item.subject());
            open.setOnClickListener(v->dispatch(item,currentPage,false));
            return row;
        }
        private void dispatch(MailRow item,Page expectedPage,boolean attachment){
            BrowserToolbar.Binding captured=binding;Actions callback=actions;
            if(captured==null || callback==null || expectedPage==null || expectedPage!=page)return;
            BrowserToolbar.invokeNativeIntent(()->{
                captured.check();
                if(expectedPage!=page || captured!=binding || callback!=actions)return;
                if(attachment)callback.attachment(item,expectedPage.revision());else callback.open(item,expectedPage.revision());
            });
        }
    }

    private static String text(String value,int max){
        if(value==null || value.length()>max)throw new IllegalArgumentException("Unbounded offline mail text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static void ownerThread(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native UI owner thread required");}
}
