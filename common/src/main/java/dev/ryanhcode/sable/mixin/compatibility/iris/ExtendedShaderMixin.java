package dev.ryanhcode.sable.mixin.compatibility.iris;

import dev.ryanhcode.sable.mixinterface.compatibility.iris.ExtendedShaderExtension;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The 26.3 Iris program owns raw matrix uniform locations on its OpenGL backend. */
@Mixin(ExtendedShader.class)
public class ExtendedShaderMixin implements ExtendedShaderExtension {
    @Shadow @Final private int modelViewInverse;
    @Shadow @Final private int normalMat;
    @Shadow @Final private Matrix4f tempMatrix4f;
    @Shadow @Final private Matrix3f tempMatrix3f;
    @Shadow @Final private float[] tempFloats;
    @Shadow @Final private float[] tempFloats2;

    @Override
    public void sable$refreshModelMatrices(final Matrix4fc modelView) {
        if (this.modelViewInverse != -1) IrisRenderSystem.uniformMatrix4fv(this.modelViewInverse, false,
                this.tempMatrix4f.set(modelView).invert().get(this.tempFloats));
        if (this.normalMat != -1) IrisRenderSystem.uniformMatrix3fv(this.normalMat, false,
                this.tempMatrix3f.set(this.tempMatrix4f.set(modelView)).invert().transpose().get(this.tempFloats2));
    }
}
