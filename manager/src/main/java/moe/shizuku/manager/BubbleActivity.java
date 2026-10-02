package moe.shizuku.manager;

import moe.shizuku.manager.home.HomeActivity;

public class BubbleActivity extends HomeActivity {
    // No finish() override — finishAndRemoveTask() was destroying the bubble's
    // virtual display container, causing the activity to appear as a ghost window
    // on the main screen instead of inside the bubble.
}
