package moe.shizuku.manager;

import android.app.Activity;
import android.os.Bundle;

import moe.shizuku.manager.utils.BubbleHelper;

public class BubbleLauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // We are now in the foreground thanks to the direct widget click.
        // We can safely summon and auto-expand the bubble without Android
        // blocking it as a background activity launch (which causes ghost windows).
        BubbleHelper.INSTANCE.displayBubble(this, "ShizElite", "Active");
        
        // Immediately finish so this invisible activity disappears
        finish();
    }
}
