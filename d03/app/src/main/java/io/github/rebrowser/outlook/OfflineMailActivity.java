package io.github.rebrowser.outlook;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
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
 * Native offline-mail WINDOW SHELL over one already-filtered page plus a bounded query/page
 * sequence. It never queries the database, decrypts, retries the network, saves/exports
 * attachments or renders mail HTML. Search/next/prev capture the original frame+query+page
 * revision; a late body is accepted ONLY for that exact frame/query/page/row, and busy/unknown/
 * empty states stay distinct. All visible private text and listeners are scrubbed on clear.
 */
public final class OfflineMailActivity extends Activity {
    public static final int MAX_ROWS = 100;
    public static final int MAX_TEXT_CHARS = 4096;
    public static final int MAX_SEARCH_CHARS = 64;
    public static final int MAX_PAGE_NUMBER = 10_000;

    /** Search/paging identity supplied with the page; not an actor/account credential. */
    public static final class Query {
        private final String literal;private final int pageNumber;
        public Query(String literal,int pageNumber){
            String normalized=literal==null?"":literal.trim();
            if(normalized.length()>MAX_SEARCH_CHARS)throw new IllegalArgumentException("Query too long");
            if(pageNumber<0 || pageNumber>MAX_PAGE_NUMBER)throw new IllegalArgumentException("Invalid page number");
            this.literal=normalized;this.pageNumber=pageNumber;
        }
        public String literal(){return literal;}
        public int pageNumber(){return pageNumber;}
        @Override public String toString(){return literal+"#"+pageNumber;}
    }
    public static final class PageNav {
        private final Boolean previousAvailable,nextAvailable;
        public PageNav(Boolean previousAvailable,Boolean nextAvailable){this.previousAvailable=previousAvailable;this.nextAvailable=nextAvailable;}
        public Boolean previousAvailable(){return previousAvailable;}
        public Boolean nextAvailable(){return nextAvailable;}
    }
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
        void open(MailRow row,Query query,long expectedRevision);
        void search(String literal,int maxResults,long expectedRevision);
        void requestPage(int direction,Query query,long expectedRevision);
        void attachment(MailRow row,Query query,long expectedRevision);
    }
    public enum PageDirection { PREVIOUS, NEXT }

    private TextView status;
    private EditText search;
    private ListView list;
    private final Adapter adapter=new Adapter();
    private BrowserToolbar.Binding binding;private Page page;private Actions actions;
    private Query query=new Query("",0);private PageNav pageNav=new PageNav(null,null);
    private Object pageFrame;private PageDirection pendingPage;

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
        LinearLayout paging=new LinearLayout(this);
        paging.setOrientation(LinearLayout.HORIZONTAL);
        Button previous=new Button(this);previous.setText("上一页");previous.setMinimumHeight(dp(48));
        Button next=new Button(this);next.setText("下一页");next.setMinimumHeight(dp(48));
        previous.setOnClickListener(v->requestPage(PageDirection.PREVIOUS));
        next.setOnClickListener(v->requestPage(PageDirection.NEXT));
        paging.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));
        paging.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(paging);
        list=new ListView(this);
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
    }

    public void showPage(BrowserToolbar.Binding binding,Page page,Actions actions){
        showPage(binding,page,new Query("",0),new PageNav(null,null),actions);
    }

    /** ONLY the owning native window; the query/page identity travels with the page. */
    public void showPage(BrowserToolbar.Binding binding,Page page,Query query,PageNav nav,Actions actions){
        ownerThread();
        if(binding==null || page==null || query==null || nav==null || actions==null)throw new IllegalArgumentException("Missing native offline mail binding");
        binding.check();
        scrubVisibleRows();
        this.binding=binding;this.page=page;this.actions=actions;this.query=query;this.pageNav=nav;
        this.pageFrame=new Object();this.pendingPage=null;
        adapter.update(page);
        status.setText(page.rows().isEmpty()?"本页没有离线邮件":"离线邮件为只读展示；正文仅纯文本 · 查询 " + query);
        search.setText(query.literal());
        search.setOnEditorActionListener((view,action,event)->{
            String literal=search.getText()==null?"":search.getText().toString().trim();
            if(literal.isEmpty() || literal.length()>MAX_SEARCH_CHARS){search.setError("搜索内容为空或过长");return true;}
            BrowserToolbar.Binding captured=this.binding;Page currentPage=this.page;Actions owner=this.actions;
            if(captured==null || currentPage==null || owner==null)return true;
            status.setText("已请求检索，等待原生结果");
            BrowserToolbar.invokeNativeIntent(()->{
                captured.check();
                if(captured!=this.binding || currentPage!=this.page || owner!=this.actions)return;
                owner.search(literal,50,currentPage.revision());
            });
            return true;
        });
    }

    /** One bounded paging request per frame; unknown outcome keeps it blocked. */
    private void requestPage(PageDirection direction){
        BrowserToolbar.Binding captured=binding;Actions owner=actions;Page currentPage=page;Object expectedFrame=pageFrame;
        if(captured==null || owner==null || currentPage==null || pageFrame!=expectedFrame)return;
        Boolean available=direction==PageDirection.PREVIOUS?pageNav.previousAvailable():pageNav.nextAvailable();
        if(pendingPage!=null || !Boolean.TRUE.equals(available))return;
        pendingPage=direction;
        status.setText("已请求"+(direction==PageDirection.PREVIOUS?"上一页":"下一页")+"，等待原生结果");
        BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(page!=currentPage || binding!=captured || actions!=owner || pageFrame!=expectedFrame)return;
            owner.requestPage(direction==PageDirection.PREVIOUS?-1:1,query,currentPage.revision());
        });
    }

    public void showUnavailable(String reason){
        ownerThread();
        scrubVisibleRows();
        binding=null;page=null;actions=null;pageFrame=null;pendingPage=null;query=new Query("",0);pageNav=new PageNav(null,null);
        adapter.update(null);
        search.setOnEditorActionListener(null);search.setText("");
        status.setText(reason==null || reason.isEmpty()?"未连接原生离线邮件窗口":"离线邮件不可用：" + bounded(reason));
    }

    /**
     * Late body results are accepted ONLY for the exact still-current frame/query/page/row.
     * A title or URL is never sufficient to reidentify the private account or message.
     */
    public boolean showBodyText(BrowserToolbar.Binding expectedBinding,Page expectedPage,Object exactRowToken,
                                String subject,String plainText){
        return showBodyText(expectedBinding,expectedPage,query,exactRowToken,subject,plainText);
    }
    public boolean showBodyText(BrowserToolbar.Binding expectedBinding,Page expectedPage,Query expectedQuery,Object exactRowToken,
                                String subject,String plainText){
        ownerThread();
        if(expectedBinding==null || expectedPage==null || expectedQuery==null || exactRowToken==null
                || binding!=expectedBinding || page!=expectedPage || actions==null)return false;
        if(!query.toString().equals(expectedQuery.toString()))return false;
        try{expectedBinding.check();}catch(SecurityException | IllegalStateException stale){return false;}
        boolean exactRowPresent=false;
        for(MailRow row:expectedPage.rows())if(row.token()==exactRowToken){exactRowPresent=true;break;}
        if(!exactRowPresent)return false;
        status.setText(text(subject,512)+"\n"+text(plainText,64*1024));
        return true;
    }

    private void scrubVisibleRows(){
        if(list==null)return;
        for(int i=0;i<list.getChildCount();i++){
            View child=list.getChildAt(i);
            Object tag=child.getTag();
            if(!(tag instanceof View[]))continue;
            View[] parts=(View[])tag;
            if(parts.length!=4)continue;
            if(parts[0] instanceof TextView)((TextView)parts[0]).setText("");
            if(parts[1] instanceof TextView)((TextView)parts[1]).setText("");
            for(int k=2;k<4;k++)if(parts[k] instanceof Button){
                Button button=(Button)parts[k];button.setEnabled(false);
                button.setOnClickListener(null);button.setContentDescription(null);
            }
        }
    }

    @Override protected void onDestroy(){
        scrubVisibleRows();
        if(list!=null)list.setAdapter(null);
        adapter.update(null);binding=null;page=null;actions=null;pageFrame=null;pendingPage=null;
        if(search!=null){search.setOnEditorActionListener(null);search.setText("");}
        if(status!=null)status.setText("");
        super.onDestroy();
    }

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
                View[] parts=(View[])recycled.getTag();
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
            Page currentPage=page;Query currentQuery=query;
            title.setText(item.subject());detail.setText(item.sender()+" · "+item.dateText()+" · "+item.preview());
            attach.setEnabled(item.attachmentPresent() && binding!=null);
            attach.setContentDescription("查看附件：" + item.subject());
            attach.setOnClickListener(v->dispatch(item,currentPage,currentQuery,true));
            open.setContentDescription("打开离线邮件：" + item.subject());
            open.setOnClickListener(v->dispatch(item,currentPage,currentQuery,false));
            return row;
        }
        private void dispatch(MailRow item,Page expectedPage,Query expectedQuery,boolean attachment){
            BrowserToolbar.Binding captured=binding;Actions callback=actions;
            if(captured==null || callback==null || expectedPage==null || expectedQuery==null || expectedPage!=page)return;
            BrowserToolbar.invokeNativeIntent(()->{
                captured.check();
                if(expectedPage!=page || !expectedQuery.toString().equals(query.toString()) || captured!=binding || callback!=actions)return;
                if(attachment)callback.attachment(item,expectedQuery,expectedPage.revision());
                else callback.open(item,expectedQuery,expectedPage.revision());
            });
        }
    }

    private static String text(String value,int max){
        if(value==null || value.length()>max)throw new IllegalArgumentException("Unbounded offline mail text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
    private static String bounded(String value){if(value.length()>200)throw new IllegalArgumentException("Unbounded status");return value;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static void ownerThread(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native UI owner thread required");}
}
