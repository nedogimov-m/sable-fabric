package dev.ryanhcode.sable.render.dynamic_shade;

import foundry.veil.Veil;
import foundry.veil.api.client.render.shader.processor.ShaderPreProcessor;
import io.github.ocelot.glslprocessor.api.GlslInjectionPoint;
import io.github.ocelot.glslprocessor.api.GlslParser;
import io.github.ocelot.glslprocessor.api.GlslSyntaxException;
import io.github.ocelot.glslprocessor.api.node.GlslNode;
import io.github.ocelot.glslprocessor.api.node.GlslNodeList;
import io.github.ocelot.glslprocessor.api.node.GlslTree;
import io.github.ocelot.glslprocessor.api.node.expression.GlslAssignmentNode;
import io.github.ocelot.glslprocessor.api.node.expression.GlslOperationNode;
import io.github.ocelot.glslprocessor.api.node.function.GlslInvokeFunctionNode;
import io.github.ocelot.glslprocessor.api.node.variable.GlslVariableNode;
import io.github.ocelot.glslprocessor.lib.anarres.cpp.LexerException;

import java.io.IOException;
import java.util.List;

public class SableDynamicDirectionalShadingPreProcessor implements ShaderPreProcessor {

    @Override
    public void modify(final Context ctx, final GlslTree tree) throws GlslSyntaxException, IOException, LexerException {
        if (!SableDynamicDirectionalShading.isEnabled()) {
            return;
        }

        if (ctx instanceof final MinecraftContext minecraftContext) {
            final String shader = minecraftContext.shaderInstance();
            if (!ctx.isVertex() || !java.util.Set.of("terrain", "block", "core/terrain", "core/block",
                    "minecraft:core/terrain", "minecraft:core/block").contains(shader)) return;
        } else {
            return;
        }

        // Native BLOCK omits normals; the primary plot backend evaluates this shading on the CPU.
        // Only a pipeline that actually supplies Normal can use the optional shader injection.
        if (tree.field("Normal").isEmpty()) return;

        ctx.include(tree, net.minecraft.resources.Identifier.fromNamespaceAndPath("veil", "light"), IncludeOverloadStrategy.SOURCE);

        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform float SableEnableNormalLighting;"));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform float SableSkyLightScale;"));

        // Add NormalMat if we're lacking it
        if (tree.field("NormalMat").isEmpty()) {
            tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform mat3 NormalMat;"));
        }

        final List<GlslNode> body = tree.mainFunction().orElseThrow().getBody();
        body.add(GlslParser.parseExpression("vertexColor.rgb *= mix(vec3(1.0), vec3(block_brightness(inverse(NormalMat) * (ModelViewMat * vec4(Normal, 0.0)).xyz)), SableEnableNormalLighting);"));

        final GlslNodeList mainFunctionBody = tree.mainFunction().orElseThrow().getBody();
        assert mainFunctionBody != null;
        for (int i = 0; i < mainFunctionBody.size(); i++) {
            final GlslNode node = mainFunctionBody.get(i);

            if (node instanceof final GlslAssignmentNode assignmentNode && assignmentNode.getOperand() == GlslAssignmentNode.Operand.EQUAL) {
                // We can be for sure it's an assignment. Now let's check that it's calling
                // minecraft_sample_lightmap
                final GlslNode second = assignmentNode.getSecond();

                if (second instanceof final GlslOperationNode operationNode && operationNode.getOperand() == GlslOperationNode.Operand.MULTIPLY) {
                    if (operationNode.getSecond() instanceof final GlslInvokeFunctionNode invokeNode && invokeNode.getHeader() instanceof final GlslVariableNode variableNode && variableNode.getName().equals("minecraft_sample_lightmap")) {
                        final List<GlslNode> replacementNodes = GlslParser.parseExpressionList("vertexColor = Color * minecraft_sample_lightmap(Sampler2, ivec2(UV2 * vec2(1.0, SableSkyLightScale)));");

                        mainFunctionBody.set(i, replacementNodes.getFirst());

                        for (int j = 1; j < replacementNodes.size(); j++) {
                            mainFunctionBody.add(i + j, replacementNodes.get(j));
                        }

                        break;
                    }
                }
            }
        }
    }
}
