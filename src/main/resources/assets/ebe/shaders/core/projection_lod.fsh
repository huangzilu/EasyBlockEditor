#version 150

in vec4 vertexColor;
in vec3 modelPos;
in float viewDepth;

uniform vec4 ColorModulator;
uniform float LodGridScale;
uniform float LodGridStrength;
uniform float LodDepthFadeDistance;
uniform float LodAlphaMultiplier;

out vec4 fragColor;

float gridLine(float coord, float scale, float width) {
    float v = abs(fract(coord / scale) - 0.5);
    return smoothstep(width, 0.0, v);
}

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a <= 0.0) {
        discard;
    }

    float gridScale = max(1.0, LodGridScale);
    float grid = max(max(
        gridLine(modelPos.x, gridScale, 0.030),
        gridLine(modelPos.y, gridScale, 0.030)),
        gridLine(modelPos.z, gridScale, 0.030));
    float depthFade = clamp(1.0 - viewDepth / max(1.0, LodDepthFadeDistance), 0.55, 1.0);
    float gridStrength = clamp(LodGridStrength, 0.0, 1.0);
    vec3 coolLight = vec3(0.18, 0.42, 0.70);
    vec3 lit = mix(color.rgb * depthFade, min(vec3(1.0), color.rgb + coolLight * 0.35), grid * gridStrength);

    float alpha = color.a * LodAlphaMultiplier * mix(0.92, 1.12, grid * gridStrength);
    fragColor = vec4(lit, clamp(alpha, 0.0, 1.0));
}
