#version 450

layout(std140) uniform SableRegionTransforms {
    mat4 ModelViewMat;
    mat4 ProjMat;
    float FaceMode;
};

void main() {
    if ((FaceMode > 0.0 && !gl_FrontFacing) || (FaceMode < 0.0 && gl_FrontFacing)) {
        discard;
    }
}
