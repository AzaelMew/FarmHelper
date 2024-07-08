package com.jelly.farmhelperv2.failsafe.impl;

import com.jelly.farmhelperv2.config.FarmHelperConfig;
import com.jelly.farmhelperv2.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv2.failsafe.Failsafe;
import com.jelly.farmhelperv2.failsafe.FailsafeManager;
import com.jelly.farmhelperv2.feature.FeatureManager;
import com.jelly.farmhelperv2.feature.impl.BPSTracker;
import com.jelly.farmhelperv2.handler.GameStateHandler;
import com.jelly.farmhelperv2.handler.MacroHandler;
import com.jelly.farmhelperv2.handler.RotationHandler;
import com.jelly.farmhelperv2.util.KeyBindUtils;
import com.jelly.farmhelperv2.util.LogUtils;
import com.jelly.farmhelperv2.util.helper.Clock;
import com.jelly.farmhelperv2.util.helper.Rotation;
import com.jelly.farmhelperv2.util.helper.RotationConfiguration;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class LowerAvgBpsFailsafe extends Failsafe {
    private static LowerAvgBpsFailsafe instance;
    private float lastBPS;

    private final Clock clock = new Clock();

    public static LowerAvgBpsFailsafe getInstance() {
        if (instance == null) {
            instance = new LowerAvgBpsFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 9;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.LOWER_AVERAGE_BPS;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnLowerAverageBPS;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnLowerAverageBPS;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnLowerAverageBPS;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnLowerAverageBPS;
    }

    @Override
    public void onTickDetection(TickEvent.ClientTickEvent event) {
        if (MacroHandler.getInstance().isCurrentMacroPaused()
                || FeatureManager.getInstance().shouldPauseMacroExecution()
                || !MacroHandler.getInstance().getMacro().checkForBPS()
                || !FarmHelperConfig.averageBPSDropCheck
                || event.phase != TickEvent.Phase.START) {
            return;
        }

        if (BPSTracker.getInstance().getBPSFloat() - lastBPS < 0) {
            if (!clock.isScheduled())
                clock.schedule(FarmHelperConfig.BPSDropThreshold * 1000L);
        } else if (BPSTracker.getInstance().getBPSFloat() - lastBPS > 0) {
            clock.reset();
        }
        lastBPS = BPSTracker.getInstance().getBPSFloat();

        if (!clock.isScheduled())
            return;

        if (clock.passed()) {
            FailsafeManager.getInstance().possibleDetection(this);
        }

    }

    @Override
    public void duringFailsafeTrigger() {
        switch (lowerBPSState) {
            case NONE:
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                lowerBPSState = LowerBPSState.WAIT_BEFORE_START;
                break;
            case WAIT_BEFORE_START:
                KeyBindUtils.stopMovement();
                MacroHandler.getInstance().pauseMacro();
                FailsafeManager.getInstance().scheduleRandomDelay(500, 500);
                lowerBPSState = LowerBPSState.WARP_BACK;
                break;
            case WARP_BACK:
                if (GameStateHandler.getInstance().inGarden()) {
                    MacroHandler.getInstance().triggerWarpGarden(true, false);
                    FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                    lowerBPSState = LowerBPSState.END;
                } else {
                    if (GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.HUB) {
                        MacroHandler.getInstance().triggerWarpGarden(true, false);
                        FailsafeManager.getInstance().scheduleRandomDelay(2500, 2000);
                    } else if (GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.LIMBO) {
                        mc.thePlayer.sendChatMessage("/l");
                        FailsafeManager.getInstance().scheduleRandomDelay(2500, 2000);
                    } else {
                        mc.thePlayer.sendChatMessage("/skyblock");
                        FailsafeManager.getInstance().scheduleRandomDelay(5500, 4000);
                    }
                }
                break;
            case END:
                float randomTime = FarmHelperConfig.getRandomRotationTime();
                this.rotation.easeTo(
                        new RotationConfiguration(
                                new Rotation(
                                        (float) (mc.thePlayer.rotationYaw + Math.random() * 60 - 30),
                                        (float) (30 + Math.random() * 20 - 10))
                                , (long) randomTime, null));
                FailsafeManager.getInstance().stopFailsafes();
                FailsafeManager.getInstance().restartMacroAfterDelay();
                break;
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
    }

    @Override
    public void resetStates() {
        LogUtils.sendDebug("RESET");
        clock.reset();
        lastBPS = 0;
        lowerBPSState = LowerBPSState.NONE;
    }

    enum LowerBPSState {
        NONE,
        WAIT_BEFORE_START,
        WARP_BACK,
        END
    }

    private LowerBPSState lowerBPSState = LowerBPSState.NONE;
    private final RotationHandler rotation = RotationHandler.getInstance();
}
