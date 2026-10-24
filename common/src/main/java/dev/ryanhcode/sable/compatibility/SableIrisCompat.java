package dev.ryanhcode.sable.compatibility;

import dev.ryanhcode.sable.mixinterface.compatibility.iris.ExtendedShaderExtension;
import org.joml.Matrix4fc;

public class SableIrisCompat {

    public static void refreshModelMatrices(final Object shader, final Matrix4fc modelView) {
        if (shader instanceof final ExtendedShaderExtension ext) {
            ext.sable$refreshModelMatrices(modelView);
        }
    }

}
