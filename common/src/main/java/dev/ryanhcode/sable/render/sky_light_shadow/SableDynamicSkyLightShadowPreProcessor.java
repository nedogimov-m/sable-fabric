package dev.ryanhcode.sable.render.sky_light_shadow;

import foundry.veil.api.client.render.shader.processor.ShaderPreProcessor;
import dev.ryanhcode.sable.render.SableNativeWorldShaders;
import io.github.ocelot.glslprocessor.api.GlslInjectionPoint;
import io.github.ocelot.glslprocessor.api.GlslParser;
import io.github.ocelot.glslprocessor.api.GlslSyntaxException;
import io.github.ocelot.glslprocessor.api.node.GlslNode;
import io.github.ocelot.glslprocessor.api.node.GlslTree;
import io.github.ocelot.glslprocessor.api.node.expression.GlslAssignmentNode;
import io.github.ocelot.glslprocessor.api.node.expression.GlslOperationNode;
import io.github.ocelot.glslprocessor.api.node.function.GlslInvokeFunctionNode;
import io.github.ocelot.glslprocessor.api.node.variable.GlslVariableNode;

/** Uses native sample_lightmap and a typed reverse-depth shadow resource block. */
public class SableDynamicSkyLightShadowPreProcessor implements ShaderPreProcessor {
    public static final String SAMPLER_NAME = "SableShadowSampler";
    public static final String SHADOW_VOLUME_SIZE_UNIFORM = "SableShadowVolumeSize";
    public static final String ENABLE_UNIFORM = "SableShadowsEnabled";
    public static final String SHADOW_ORIGIN_UNIFORM = "SableShadowOrigin";
    @Override public void modify(final Context ctx, final GlslTree tree) throws GlslSyntaxException {
        if (!ctx.isSourceFile() || !ctx.isVertex()
                || !(ctx instanceof final MinecraftContext minecraft)
                || !SableNativeWorldShaders.isTerrainSource(ctx.name())) return;
        final var body = tree.mainFunction().orElseThrow().getBody();
        if (body == null) return;
        for (int i = 0; i < body.size(); i++) {
            if (!(body.get(i) instanceof final GlslAssignmentNode assignment) || assignment.getOperand() != GlslAssignmentNode.Operand.EQUAL
                    || !(assignment.getSecond() instanceof final GlslOperationNode multiply) || multiply.getOperand() != GlslOperationNode.Operand.MULTIPLY
                    || !(multiply.getSecond() instanceof final GlslInvokeFunctionNode call)
                    || !(call.getHeader() instanceof final GlslVariableNode function) || !function.getName().equals("sample_lightmap")) continue;
            tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform sampler2D SableShadowSampler;"));
            tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("""
                    layout(std140) uniform SableSkyLightShadow {
                        vec3 SableShadowOrigin;
                        float SableShadowVolumeSize;
                        vec2 SableShadowTexelSize;
                        float SableShadowsEnabled;
                    };
                    """));
            final var replacement = GlslParser.parseExpressionList("""
                    float sableSkyScale = 1.0;
                    if (SableShadowsEnabled > 0.0) {
                        vec2 sableShadowUv = ((pos.xz - SableShadowOrigin.xz) * vec2(1.0, -1.0) + SableShadowVolumeSize) / (SableShadowVolumeSize * 2.0);
                        if (all(greaterThanEqual(sableShadowUv, vec2(0.0))) && all(lessThanEqual(sableShadowUv, vec2(1.0)))) {
                            float sableOcclusion = 0.0;
                            for (int x = -3; x <= 3; x++) {
                                for (int y = -3; y <= 3; y++) {
                                    float sableDepth = texture(SableShadowSampler, sableShadowUv + vec2(x, y) * SableShadowTexelSize).r;
                                    float sableHeight = SableShadowOrigin.y - (0.5 + (1.0 - sableDepth) * (SableShadowVolumeSize - 0.5));
                                    if (sableDepth > 0.0 && sableHeight >= pos.y) {
                                        sableOcclusion += 0.6 * clamp((sableHeight - pos.y - 2.0) / 15.0, 0.0, 1.0);
                                    }
                                }
                            }
                            sableSkyScale = smoothstep(0.0, 1.0, 1.0 - sableOcclusion / 49.0);
                        }
                    }
                    vertexColor = Color * sample_lightmap(Sampler2, ivec2(UV2.x, int(float(UV2.y) * sableSkyScale)));
                    """);
            body.set(i, replacement.get(0));
            for (int j = 1; j < replacement.size(); j++) body.add(i + j, replacement.get(j));
            return;
        }
    }
}
