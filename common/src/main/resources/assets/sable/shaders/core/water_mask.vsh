#version 450

layout(location = 0) in vec3 Position;
layout(std140) uniform SableRegionTransforms {
    mat4 ModelViewMat;
    mat4 ProjMat;
    float FaceMode;
};

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
