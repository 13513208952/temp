package io.github.rebrowser.system;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.rebrowser.ui.BrowserToolbar;

/**
 * Native APK handoff CONFIRMATION window. It displays only a native-verified descriptor and its
 * waiting/unknown state, ignores external Intent extras, never stages/parses an APK, never sends
 * an installer Intent or grants URI permission and never reports that installation finished.
 */
public final class ApkHandoffActivity extends Activity {
    public enum HandoffState { WAITING_FOR_OWNER, VERIFIED_READY, HANDED_OFF_TO_SYSTEM, UNKNOWN }
    public interface Actions {
        void confirmHandoff(Verified descriptor);
        void cancel();
    }
    /** Only fields the native verifier already established; names are display text. */
    public static final class Verified {
        private final String displayName,versionText;private final long byteCount;private final String sha256Hex;
        public Verified(String displayName,String versionText,long byteCount,String sha256Hex){
            if(displayName==null || displayName.isEmpty() || displayName.length()>256)throw new IllegalArgumentException("Invalid APK display name");
            if(versionText==null || versionText.length()>64)throw new IllegalArgumentException("Invalid version text");
            if(byteCount<1 || byteCount>1L<<33)throw new IllegalArgumentException("Invalid APK length");
            if(sha256Hex==null || !sha256Hex.matches("^[0-9a-f]{64}$"))throw new IllegalArgumentException("Invalid APK digest");
            this.displayName=displayName;this.versionText=versionText;this.byteCount=byteCount;this.sha256Hex=sha256Hex;
        }
        public String displayName(){return displayName;}
        public String versionText(){return versionText;}
        public long byteCount(){return byteCount;}
        public String sha256Hex(){return sha256Hex;}
    }

    private TextView stateText,detailText;
    private Button confirm,cancel;
    private BrowserToolbar.Binding binding;private Verified verified;private Actions actions;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this);
        title.setText("APK 交接确认");
        title.setTextSize(20);
        root.addView(title);
        stateText=new TextView(this);
        stateText.setText(stateLabel(HandoffState.WAITING_FOR_OWNER));
        root.addView(stateText);
        detailText=new TextView(this);
        detailText.setText("等待原生校验器提供文件描述");
        root.addView(detailText);
        confirm=new Button(this);confirm.setText("交给系统安装器");confirm.setMinimumHeight(dp(48));confirm.setEnabled(false);
        cancel=new Button(this);cancel.setText("取消");cancel.setMinimumHeight(dp(48));cancel.setEnabled(false);
        root.addView(confirm,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        root.addView(cancel,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        setContentView(root);
        // External Intent extras are intentionally NOT read as administrator/file/path authority.
    }

    public void showVerified(BrowserToolbar.Binding binding,Verified descriptor,Actions actions){
        ownerThread();
        if(binding==null || descriptor==null || actions==null)throw new IllegalArgumentException("Missing native APK handoff binding");
        binding.check();
        this.binding=binding;this.verified=descriptor;this.actions=actions;
        stateText.setText(stateLabel(HandoffState.VERIFIED_READY));
        detailText.setText(descriptor.displayName()+" · "+descriptor.versionText()+" · "+descriptor.byteCount()+" 字节 · SHA-256 "+descriptor.sha256Hex().substring(0,16)+"…");
        confirm.setEnabled(true);
        cancel.setEnabled(true);
        confirm.setContentDescription("请求将已验证APK交给系统安装器：" + descriptor.displayName());
        cancel.setContentDescription("取消APK交接");
        confirm.setOnClickListener(v->dispatch(true));
        cancel.setOnClickListener(v->dispatch(false));
    }

    /** Called by the owner after the system handoff intent actually returned/started. */
    public void showHandoffState(HandoffState state){
        ownerThread();
        if(state==null)throw new IllegalArgumentException("Missing handoff state");
        stateText.setText(stateLabel(state));
    }

    public void showUnavailable(String reason){
        ownerThread();
        binding=null;verified=null;actions=null;
        stateText.setText(stateLabel(HandoffState.UNKNOWN));
        detailText.setText(reason==null || reason.isEmpty()?"交接不可用":"交接不可用：" + reason);
        confirm.setEnabled(false);cancel.setEnabled(false);
    }

    public static String stateLabel(HandoffState state){
        if(state==null)throw new IllegalArgumentException("Missing handoff state");
        switch(state){
            case WAITING_FOR_OWNER:return "等待原生校验器";
            case VERIFIED_READY:return "已验证，等待你的确认";
            case HANDED_OFF_TO_SYSTEM:return "已交给系统安装器（安装结果未确认）";
            default:return "交接状态未知";
        }
    }

    private void dispatch(boolean confirmHandoff){
        BrowserToolbar.Binding captured=binding;Verified descriptor=verified;Actions callback=actions;
        if(captured==null || descriptor==null || callback==null)return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(captured!=binding || descriptor!=verified || callback!=actions)return;
            if(confirmHandoff)callback.confirmHandoff(descriptor);else callback.cancel();
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN)showHandoffState(HandoffState.UNKNOWN);
    }

    @Override protected void onDestroy(){binding=null;verified=null;actions=null;super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static void ownerThread(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native UI owner thread required");}
}
