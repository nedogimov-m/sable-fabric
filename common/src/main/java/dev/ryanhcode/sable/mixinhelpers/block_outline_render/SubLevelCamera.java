package dev.ryanhcode.sable.mixinhelpers.block_outline_render;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@ApiStatus.Internal
public class SubLevelCamera extends Camera {

    private Camera renderCamera;
    private final Quaterniond inverseOrientation = new Quaterniond();
    private final Quaternionf inverseOrientationf = new Quaternionf();
    private final Vector3f rotationYXZ = new Vector3f();
    private final Vector3f forwards = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f left = new Vector3f();
    private final Quaternionf inverseViewRotation = new Quaternionf();

    private final BlockPos.MutableBlockPos blockPosition = new BlockPos.MutableBlockPos();
    private Vec3 pos = Vec3.ZERO;

    public void setCamera(final Camera renderCamera) {
        this.renderCamera = renderCamera;
    }

    public void setPose(@Nullable final Pose3dc pose) {
        if (pose != null) {
            final Vec3 pos = pose.transformPositionInverse(this.renderCamera.position());

            final Quaternionf rotation = this.rotation();
            rotation.set(this.inverseOrientationf.set(pose.orientation().invert(this.inverseOrientation))).mul(this.renderCamera.rotation());

            this.blockPosition.set(pos.x, pos.y, pos.z);
            this.pos = pos;

            rotation.getEulerAnglesYXZ(this.rotationYXZ);

            this.forwards.set(0.0F, 0.0F, -1.0F).rotate(rotation);
            this.up.set(0.0F, 1.0F, 0.0F).rotate(rotation);
            this.left.set(-1.0F, 0.0F, 0.0F).rotate(rotation);
        } else {
            this.pos = this.renderCamera.position();
            this.blockPosition.set(this.pos.x, this.pos.y, this.pos.z);
            final Quaternionf rotation = this.rotation();
            rotation.set(this.renderCamera.rotation());
            rotation.getEulerAnglesYXZ(this.rotationYXZ);

            this.forwards.set(0.0F, 0.0F, -1.0F).rotate(rotation);
            this.up.set(0.0F, 1.0F, 0.0F).rotate(rotation);
            this.left.set(-1.0F, 0.0F, 0.0F).rotate(rotation);
        }
    }

    public void clear() {
        this.renderCamera = null;
        this.pos = Vec3.ZERO;
    }

    @Override
    public @NotNull Vec3 position() {
        return this.pos;
    }

    @Override
    public @NotNull BlockPos blockPosition() {
        return this.blockPosition;
    }

    @Override
    public float xRot() {
        return (float) (180.0 / Math.PI * -this.rotationYXZ.x);
    }

    @Override
    public float yRot() {
        return (float) (180.0 / Math.PI * -this.rotationYXZ.y + 180.0);
    }

    @Override
    public @NotNull Entity entity() {
        return this.renderCamera.entity();
    }

    @Override
    public boolean isInitialized() {
        return this.renderCamera.isInitialized();
    }

    @Override
    public boolean isDetached() {
        return this.renderCamera.isDetached();
    }

    @Override
    public @NotNull NearPlane getNearPlane(final float fov) {
        return this.renderCamera.getNearPlane(fov);
    }

    @Override
    public @NotNull FogType getFluidInCamera() {
        return this.renderCamera.getFluidInCamera();
    }

    @Override
    public void reset() {
        this.renderCamera.reset();
    }

    @Override
    public @NotNull Vector3f forwardVector() {
        return this.forwards;
    }

    @Override
    public @NotNull Vector3f upVector() {
        return this.up;
    }

    @Override
    public @NotNull Vector3f leftVector() {
        return this.left;
    }

    @Override
    public Matrix4f getViewRotationMatrix(final Matrix4f target) {
        return target.rotation(this.rotation().conjugate(this.inverseViewRotation));
    }

    @Override
    public Matrix4f getViewRotationProjectionMatrix(final Matrix4f target) {
        this.renderCamera.getViewRotationProjectionMatrix(target);
        return target.rotate(this.renderCamera.rotation()).rotate(this.rotation().conjugate(this.inverseViewRotation));
    }

    public Camera getRenderCamera() {
        return this.renderCamera;
    }
}
