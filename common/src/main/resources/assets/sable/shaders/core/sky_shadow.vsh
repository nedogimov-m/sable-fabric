#version 450
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 0) out vec2 texCoord;
layout(location = 1) out float vertexAlpha;
layout(std140) uniform SableShadowTransforms {
    mat4 ModelViewMat;
    mat4 ProjMat;
    float AlphaCutout;
};
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexAlpha = Color.a;
}
