package ac.grim.grimac.checks.impl.timer;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;

// This works around 1.3 timer, to prevent too high abuse - maybe there's a better solution?
@CheckData(name = "TimerLimit", setback = 10)
public class TimerLimit extends Timer {

    // At what ping should we start to limit the balance advantage? (nanos)
    private long limitAbuseOverPing;
    private long climbingGraceWindow;

    public TimerLimit(GrimPlayer player) {
        super(player);
    }

    @Override
    public void doCheck(final PacketReceiveEvent event) {
        // 1:1 with Timer minus cancelling the packet
        long now = System.nanoTime();
        if (timerBalanceRealTime > now) {
            if (applyClimbGrace(now)) {
                limitFallBehind();
                return;
            }
            // If timer check already flagged, don't flag.
            if (!event.isCancelled()) {
                if (flagAndAlert() && shouldSetback()) {
                    player.getSetbackTeleportUtil().executeNonSimulatingSetback();
                }
            }

            // Reset the violation by 1 movement
            timerBalanceRealTime -= 50e6;
        }

        limitFallBehind();
    }

    private boolean applyClimbGrace(long now) {
        if (climbingGraceWindow <= 0) {
            return false;
        }

        if (!(player.isClimbing || player.pointThreeEstimator.isNearClimbable())) {
            return false;
        }

        long lead = timerBalanceRealTime - now;
        if (lead <= 0 || lead > climbingGraceWindow) {
            return false;
        }

        timerBalanceRealTime = Math.max(timerBalanceRealTime - 50_000_000L, now);
        return true;
    }

    @Override
    protected void limitFallBehind() {
        // Limit using transaction ping if over 1000ms (default)
        long playerClock = lastMovementPlayerClock;
        if (limitAbuseOverPing != -1 && System.nanoTime() - playerClock > limitAbuseOverPing) {
            playerClock = System.nanoTime() - limitAbuseOverPing;
        }
        timerBalanceRealTime = Math.max(timerBalanceRealTime, playerClock - clockDrift);
    }

    @Override
    public void onReload(ConfigManager config) {
        super.onReload(config);
        limitAbuseOverPing = config.getLongElse(getConfigName() + ".ping-abuse-limit-threshold", 1000L);
        if (limitAbuseOverPing != -1) {
            limitAbuseOverPing *= (long) 1e6;
        }
        double climbGraceTicks = config.getDoubleElse(getConfigName() + ".climb-grace-ticks", 3.0);
        climbingGraceWindow = climbGraceTicks <= 0 ? 0 : (long) (climbGraceTicks * 50e6);
    }
}
