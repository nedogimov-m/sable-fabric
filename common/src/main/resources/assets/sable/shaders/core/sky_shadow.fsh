#version 450
layout(location = 0) in vec2 texCoord;
layout(location = 1) in float vertexAlpha;
uniform sampler2D Sampler0;
layout(std140) uniform SableShadowTransforms {
    mat4 ModelViewMat;
    mat4 ProjMat;
    float AlphaCutout;
};
void main() {
    if (AlphaCutout > 0.0 && texture(Sampler0, texCoord).a * vertexAlpha < AlphaCutout) discard;
}
