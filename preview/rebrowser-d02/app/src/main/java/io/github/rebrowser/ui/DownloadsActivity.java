package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;

/**
 * Native download WINDOW SHELL over an already-computed TaskSnapshot page. It never captures a
 * WebResponse, downloads bytes, publishes a file or sends an installer/open Intent; approval,
 * cancellation and handoff are user INTENTS for the native download controller. A full progress
 * bar or a Java callback return never means the transfer ended or the file is ready.
 */
public final class DownloadsActivity extends Activity {
    public interface Actions {
        void approveDownload(Object requestToken,long expectedRevision);
        void requestCancel(DownloadsAdapter.Row row,long expectedRevision);
        void requestHandoff(DownloadsAdapter.Row row,long expectedRevision);
    }
    /** A pending native approval request; the token is opaque and is not a file grant. */
    public static final class ApprovalRequest {
        private final Object token;private final String displayName;private final long reportedBytes;
        public ApprovalRequest(Object token,String displayName,long reportedBytes){
            if(token==null || displayName==null || displayName.isEmpty() || displayName.length()>256)throw new IllegalArgumentException("Invalid approval request");
            if(reportedBytes< -1 || reportedBytes>1L<<40)throw new IllegalArgumentException("Invalid reported size");
            this.token=token;this.displayName=displayName;this.reportedBytes=reportedBytes;
        }
        public Object token(){return token;}
        public String displayName(){return displayName;}
        /** -1 = unknown; never coerced to zero or a fake 100%. */
        public long reportedBytes(){return reportedBytes;}
    }

    private TextView status;
    private ListView list;
    private DownloadsAdapter adapter;
    private BrowserToolbar.Binding binding;
    private long pageRevision;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView header=new TextView(this);
        header.setText("下载");
        header.setTextSize(20);
        root.addView(header,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        status=new TextView(this);
        status.setText("未连接原生下载控制器");
        root.addView(status,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        list=new ListView(this);
        adapter=new DownloadsAdapter(this);
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
    }

    /** Called ONLY by the native download owner with a current binding and one page. */
    public void showPage(BrowserToolbar.Binding binding,long pageRevision,List<DownloadsAdapter.Row> rows,Actions actions){
        BrowserToolbar.main();
        if(binding==null || actions==null)throw new IllegalArgumentException("Missing native download binding");
        binding.check();
        this.binding=binding;this.pageRevision=pageRevision;
        adapter.bind(binding,pageRevision,rows,new DownloadsAdapter.Actions(){
            public void requestApproval(DownloadsAdapter.Row row,long revision){actions.approveDownload(row.itemRef(),revision);}
            public void requestCancel(DownloadsAdapter.Row row,long revision){actions.requestCancel(row,revision);}
            public void requestHandoff(DownloadsAdapter.Row row,long revision){actions.requestHandoff(row,revision);}
        });
        status.setText(rows.isEmpty()?"没有下载任务":"下载状态以原生任务轴为准，不推断完成或文件可打开");
    }

    /** Approval UI shows the exact native request; it does not start or reserve a transfer. */
    public void showApprovalRequest(ApprovalRequest request,Actions actions){
        BrowserToolbar.main();
        BrowserToolbar.Binding captured=binding;
        if(captured==null || actions==null)throw new IllegalStateException("Download window is not connected");
        captured.check();
        if(request==null)throw new IllegalArgumentException("Missing approval request");
        status.setText("待批准：" + request.displayName() + (request.reportedBytes()<0?"（大小未知）":"（" + request.reportedBytes() + " 字节）") + " · 批准仅提交意图");
        final Object frame=adapter;
        list.setOnItemClickListener((parent,view,position,id)->{
            BrowserToolbar.Binding current=binding;
            if(current==null || frame!=adapter)return;
            BrowserToolbar.invokeNativeIntent(()->{current.check();if(frame!=adapter)return;actions.approveDownload(request.token(),pageRevision);});
        });
    }

    public void showUnavailable(String reason){
        BrowserToolbar.main();
        adapter.unbind();binding=null;
        status.setText(reason==null || reason.isEmpty()?"未连接原生下载控制器":"下载不可用：" + reason);
    }

    @Override protected void onDestroy(){
        if(adapter!=null)adapter.retire();
        binding=null;
        super.onDestroy();
    }
}
