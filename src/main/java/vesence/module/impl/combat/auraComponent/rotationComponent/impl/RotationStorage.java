package vesence.module.impl.combat.auraComponent.rotationComponent.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.impl.combat.auraComponent.GCDUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.Component;
import vesence.renderengine.utils.MathHelper;

@Environment(EnvType.CLIENT)
public class RotationStorage extends Component {
    
    public static RotationStorage instance;
    
    private RotationTask currentTask = RotationTask.IDLE;
    private float currentYawSpeed;
    private float currentPitchSpeed;
    private float currentYawReturnSpeed;
    private float currentPitchReturnSpeed;
    private int currentPriority;
    private int currentTimeout;
    private int idleTicks;
    private Rotation targetRotation;
    
    public RotationStorage() {
        instance = this;
    }
    
    public RotationTask currentTask() {
        return currentTask;
    }
    
    public float currentYawSpeed() {
        return currentYawSpeed;
    }
    
    public float currentPitchSpeed() {
        return currentPitchSpeed;
    }
    
    public float currentYawReturnSpeed() {
        return currentYawReturnSpeed;
    }
    
    public float currentPitchReturnSpeed() {
        return currentPitchReturnSpeed;
    }
    
    public int currentPriority() {
        return currentPriority;
    }
    
    public int currentTimeout() {
        return currentTimeout;
    }
    
    public int idleTicks() {
        return idleTicks;
    }
    
    public Rotation targetRotation() {
        return targetRotation;
    }
    
    public RotationStorage currentTask(RotationTask task) {
        this.currentTask = task;
        return this;
    }
    
    public RotationStorage currentYawSpeed(float speed) {
        this.currentYawSpeed = speed;
        return this;
    }
    
    public RotationStorage currentPitchSpeed(float speed) {
        this.currentPitchSpeed = speed;
        return this;
    }
    
    public RotationStorage currentYawReturnSpeed(float speed) {
        this.currentYawReturnSpeed = speed;
        return this;
    }
    
    public RotationStorage currentPitchReturnSpeed(float speed) {
        this.currentPitchReturnSpeed = speed;
        return this;
    }
    
    public RotationStorage currentPriority(int priority) {
        this.currentPriority = priority;
        return this;
    }
    
    public RotationStorage currentTimeout(int timeout) {
        this.currentTimeout = timeout;
        return this;
    }
    
    public RotationStorage idleTicks(int ticks) {
        this.idleTicks = ticks;
        return this;
    }
    
    public RotationStorage targetRotation(Rotation rotation) {
        this.targetRotation = rotation;
        return this;
    }
    
    private void resetRotation() {
        Rotation targetRotation = new Rotation(FreeLookUtil.freeYaw, FreeLookUtil.freePitch);
        if (updateRotation(targetRotation, currentYawReturnSpeed(), currentPitchReturnSpeed())) {
            stopRotation();
        }
    }
    
    @EventInit
    public void onEventTick(EventUpdate event) {
        if (currentTask().equals(RotationTask.AIM) && idleTicks() > currentTimeout()) {
            currentTask(RotationTask.RESET);
        }
        
        if (currentTask().equals(RotationTask.RESET)) {
            resetRotation();
        }
        idleTicks++;
    }
    
    public static void update(Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed, float pitchReturnSpeed, int timeout, int priority, boolean clientRotation) {
        final RotationStorage instance = RotationStorage.instance;
        if (mc.player == null) return;
        if (instance.currentPriority() > priority) {
            return;
        }
        
        if (instance.currentTask().equals(RotationTask.IDLE) && !clientRotation) {
            FreeLookUtil.active = true;
        }
        
        instance.currentYawSpeed(yawSpeed);
        instance.currentPitchSpeed(pitchSpeed);
        instance.currentYawReturnSpeed(yawReturnSpeed);
        instance.currentPitchReturnSpeed(pitchReturnSpeed);
        instance.currentTimeout(timeout);
        instance.currentPriority(priority);
        instance.currentTask(RotationTask.AIM);
        instance.targetRotation(target);
        
        instance.updateRotation(target, yawSpeed, pitchSpeed);
    }
    
    public static void update(Rotation targetRotation, float turnSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, turnSpeed, turnSpeed, returnSpeed, returnSpeed, timeout, priority, false);
    }
    
    public static void update(Rotation targetRotation, float yawSpeed, float pitchSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, yawSpeed, pitchSpeed, returnSpeed, returnSpeed, timeout, priority, false);
    }
    
    private boolean updateRotation(Rotation targetRotation, float yawSpeed, float pitchSpeed) {
        if (mc.player == null) return false;
        
        Rotation currentRotation = new Rotation(mc.player);
        float yawDelta = MathHelper.wrapDegrees(targetRotation.yaw - currentRotation.yaw);
        float pitchDelta = targetRotation.pitch - currentRotation.pitch;
        
        float clampedYaw = Math.min(Math.abs(yawDelta), yawSpeed);
        float clampedPitch = Math.min(Math.abs(pitchDelta), pitchSpeed);
        
        float yaw = mc.player.getYaw();
        yaw += GCDUtil.getSensitivity(MathHelper.clamp(yawDelta, -clampedYaw, clampedYaw));
        mc.player.setYaw(yaw);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + GCDUtil.getSensitivity(MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch)), -90F, 90F));
        
        idleTicks(0);
        return new Rotation(mc.player).getDelta(targetRotation) < 1F;
    }
    
    public void stopRotation() {
        currentTask(RotationTask.IDLE);
        currentPriority(0);
        FreeLookUtil.active = false;
    }
    
    public boolean isRotating() {
        return !currentTask.equals(RotationTask.IDLE);
    }
    
    @Environment(EnvType.CLIENT)
    public enum RotationTask {
        AIM,
        RESET,
        IDLE
    }
}
