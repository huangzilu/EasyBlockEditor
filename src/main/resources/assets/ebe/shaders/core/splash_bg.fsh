#version 150

in vec2 texCoord;
in vec4 vertexColor;

uniform vec4 ColorModulator;

uniform float STime;
uniform float IntroFade;
uniform float ScanProgress;
uniform float TearProgress;
uniform float DissolveProgress;
uniform vec2  ScreenSize;

out vec4 fragColor;

const vec3 ACCENT = vec3(0.22, 0.78, 1.0);
const vec3 ACCENT2 = vec3(0.55, 0.35, 1.0);

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

void main() {
    vec2 uv = texCoord;
    vec3 col = vec3(0.0);

    vec2 centered = uv - 0.5;
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    centered.x *= aspect;
    float dist = length(centered);
    float baseGlow = smoothstep(0.9, 0.0, dist) * 0.06;
    col += vec3(0.02, 0.03, 0.05) + ACCENT * baseGlow;

    float borderReveal = ScanProgress;
    float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float borderBand = smoothstep(0.018, 0.0, edge);
    float borderInner = smoothstep(0.045, 0.018, edge) * 0.4;
    float pulse = 0.6 + 0.4 * sin(STime * 4.0);
    vec3 borderCol = mix(ACCENT, ACCENT2, 0.5 + 0.5 * sin(STime * 1.5 + uv.x * 6.0));
    col += borderCol * (borderBand + borderInner) * borderReveal * pulse * 1.3;

    if (ScanProgress > 0.0 && ScanProgress < 1.0) {
        const int LINES = 6;
        for (int i = 0; i < LINES; i++) {
            float fi = float(i);
            float seed = hash21(vec2(fi * 7.13, 3.27));
            float dir = (hash21(vec2(fi * 2.91, 9.41)) < 0.5) ? -1.0 : 1.0;
            float speed = 1.4 + seed * 1.8;
            float pos = fract(seed + dir * (STime * speed * 0.35));
            float line = smoothstep(0.006, 0.0, abs(uv.y - pos));
            col += ACCENT * line * 0.7;
            col += ACCENT * line * 0.35 * step(0.5, fract(uv.x * 160.0 + fi));
        }
    }

    float scan = 0.94 + 0.06 * sin(uv.y * ScreenSize.y * 2.0);
    col *= scan;

    if (TearProgress > 0.0) {
        float band = floor(uv.y * 18.0);
        float jitter = (hash21(vec2(band, floor(STime * 24.0))) - 0.5);
        float tearMask = step(0.55, hash21(vec2(band, floor(STime * 12.0))));
        col += ACCENT * abs(jitter) * tearMask * TearProgress * 0.5;
        float retrace = smoothstep(0.0, 0.5, abs(jitter)) * tearMask;
        col *= mix(1.0, 0.6, retrace * TearProgress);
    }

    float alpha = 1.0;

    if (DissolveProgress > 0.0) {
        float maxd = 0.5 * sqrt(1.0 + aspect * aspect);
        float nd = dist / maxd;
        float front = 1.0 - DissolveProgress * 1.2;
        float grain = noise(uv * 40.0) * 0.12;
        float dustEdge = smoothstep(front, front + 0.12 + grain, nd);
        float frontGlow = smoothstep(0.18, 0.0, abs(nd - front));
        col += ACCENT * frontGlow * 1.2;
        float spark = step(0.92, hash21(floor(uv * ScreenSize / 6.0) + floor(STime * 30.0)));
        col += ACCENT2 * spark * frontGlow * 2.0;
        alpha *= (1.0 - dustEdge);
    }

    alpha *= clamp(IntroFade, 0.0, 1.0);

    fragColor = vec4(col, alpha) * ColorModulator;
}
